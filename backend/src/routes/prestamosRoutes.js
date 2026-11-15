const express = require('express');
const { getPool } = require('../config/db');
const { verifyToken, requireRole } = require('../middlewares/authMiddleware');
const { uploadEquipoImage, handleMulterError } = require('../middlewares/uploadMiddleware');
const { registrarAuditoria } = require('../services/auditService');
const { emitStockUpdate, emitPrestamoUpdate, emitChatMensaje } = require('../socket/socketServer');

const router = express.Router();

function generarCodigoCertificado(id) {
  const year = new Date().getFullYear();
  const padded = String(id).padStart(5, '0');
  const randomSuffix = Math.random().toString(36).substring(2, 6).toUpperCase();
  return `CTMA-${year}-${padded}-${randomSuffix}`;
}

function mapPrestamoRow(row) {
  return {
    id: row.id,
    codigoCertificado: row.codigo_certificado,
    usuarioId: row.usuario_id,
    usuarioCorreo: row.usuario_correo || '',
    equipoId: row.equipo_id || 0,
    equipoNombre: row.equipo_nombre || row.equipo_nombre_snapshot || 'Equipo',
    equipoCategoria: row.equipo_categoria || row.equipo_categoria_snapshot || 'OTRO',
    equipoImagenUrl: row.equipo_imagen_url || null,
    cantidad: Number(row.cantidad || 1),
    nombreSolicitante: row.nombre_solicitante,
    documentoSolicitante: row.documento_solicitante || '',
    ambienteDestino: row.ambiente_destino,
    proposito: row.motivo,
    motivo: row.motivo,
    fechaPrestamo: row.fecha_prestamo ? new Date(row.fecha_prestamo).toISOString() : new Date().toISOString(),
    fechaDevolucionEstimada: row.fecha_devolucion_estimada
      ? new Date(row.fecha_devolucion_estimada).toISOString()
      : new Date().toISOString(),
    fechaCierre: row.fecha_cierre ? new Date(row.fecha_cierre).toISOString() : null,
    estado: row.estado,
    photoPath: row.evidencia_url || null,
    evidenciaUrl: row.evidencia_url || null,
    gestionadoPor: row.gestionado_por || null,
    gestionadoPorNombre: row.gestionado_por_nombre || null,
    chatId: row.chat_id || null,
    chatSoloLectura: row.estado !== 'ACTIVO' || Boolean(row.chat_solo_lectura),
    unreadCount: Number(row.unread_count || 0)
  };
}

async function fetchPrestamoDetallado(executor, prestamoId, lectorUsuarioId) {
  const [rows] = await executor.query(
    `SELECT p.*,
            u.correo AS usuario_correo,
            e.nombre AS equipo_nombre,
            e.categoria AS equipo_categoria,
            e.imagen_url AS equipo_imagen_url,
            g.nombre AS gestionado_por_nombre,
            c.id AS chat_id,
            c.solo_lectura AS chat_solo_lectura,
            (
              SELECT COUNT(*)
              FROM chat_mensajes m
              LEFT JOIN chat_lecturas cl ON cl.chat_id = c.id AND cl.usuario_id = ?
              WHERE m.chat_id = c.id
                AND m.remitente_id != ?
                AND m.id > COALESCE(cl.ultimo_mensaje_leido_id, 0)
            ) AS unread_count
     FROM prestamos p
     JOIN usuarios u ON u.id = p.usuario_id
     LEFT JOIN equipos e ON e.id = p.equipo_id
     LEFT JOIN usuarios g ON g.id = p.gestionado_por
     LEFT JOIN chats c ON c.prestamo_id = p.id
     WHERE p.id = ?
     LIMIT 1`,
    [lectorUsuarioId, lectorUsuarioId, prestamoId]
  );
  return rows.length > 0 ? mapPrestamoRow(rows[0]) : null;
}

/**
 * POST /api/prestamos
 * Crear préstamo inmediato en estado ACTIVO (sin aprobación previa).
 * Usa SELECT ... FOR UPDATE para garantizar atomicidad frente a concurrencia.
 */
router.post(
  '/prestamos',
  verifyToken,
  uploadEquipoImage.single('evidencia'),
  handleMulterError,
  async (req, res) => {
    const pool = getPool();
    const conn = await pool.getConnection();
    try {
      const {
        equipoId,
        cantidad = 1,
        fechaDevolucionEstimada,
        motivo,
        proposito,
        ambienteDestino,
        nombreSolicitante,
        documentoSolicitante,
        duracionHoras
      } = req.body || {};

      const eqId = Number(equipoId);
      const cantNum = Number(cantidad);

      if (!Number.isInteger(eqId) || eqId <= 0) {
        return res.status(400).json({ error: 'Debe seleccionar un equipo válido.' });
      }

      if (!Number.isInteger(cantNum) || cantNum < 1) {
        return res.status(400).json({ error: 'La cantidad solicitada debe ser al menos 1 unidad.' });
      }

      const textoMotivo = String(motivo || proposito || '').trim();
      if (textoMotivo.length < 10 || textoMotivo.length > 500) {
        return res.status(400).json({
          error: 'El motivo u observaciones debe tener entre 10 y 500 caracteres.'
        });
      }

      const ambienteTrim = String(ambienteDestino || 'Ambiente General CTMA').trim();
      if (!ambienteTrim) {
        return res.status(400).json({ error: 'El ambiente o destino es obligatorio.' });
      }

      const solicitanteTrim = String(nombreSolicitante || req.user.nombre || '').trim();
      if (solicitanteTrim.length < 3) {
        return res.status(400).json({ error: 'El nombre del solicitante es obligatorio.' });
      }

      const docTrim = String(documentoSolicitante || '').trim();

      let fechaDev = null;
      if (fechaDevolucionEstimada) {
        const parsed = new Date(fechaDevolucionEstimada);
        if (!isNaN(parsed.getTime())) {
          fechaDev = parsed;
        }
      }
      if (!fechaDev) {
        const horas = Number(duracionHoras) > 0 ? Number(duracionHoras) : 4;
        fechaDev = new Date(Date.now() + horas * 3600 * 1000);
      }

      await conn.beginTransaction();

      // Bloqueo pesimista sobre la fila del equipo para evitar condiciones de carrera
      const [eqRows] = await conn.query(
        'SELECT * FROM equipos WHERE id = ? FOR UPDATE',
        [eqId]
      );

      if (eqRows.length === 0) {
        await conn.rollback();
        return res.status(404).json({ error: 'El equipo solicitado no existe.' });
      }

      const equipoRow = eqRows[0];
      if (equipoRow.eliminado) {
        await conn.rollback();
        return res.status(409).json({
          error: 'Este equipo ya no se encuentra disponible porque fue retirado del catálogo.'
        });
      }

      const disponibleActual = Number(equipoRow.stock_disponible);
      if (disponibleActual < cantNum) {
        await conn.rollback();
        return res.status(409).json({
          error:
            disponibleActual === 0
              ? `No quedan unidades disponibles de "${equipoRow.nombre}". Otra solicitud acaba de tomar la última unidad.`
              : `Stock insuficiente para "${equipoRow.nombre}". Disponibles: ${disponibleActual}, solicitadas: ${cantNum}.`
        });
      }

      const nuevoStockDisponible = disponibleActual - cantNum;
      await conn.query(
        'UPDATE equipos SET stock_disponible = ?, version = version + 1 WHERE id = ?',
        [nuevoStockDisponible, eqId]
      );

      const tempCertCode = `TEMP-${Date.now()}-${Math.round(Math.random() * 1e6)}`;
      const evidenciaUrl = req.file ? `/uploads/equipos/${req.file.filename}` : null;

      const [insertRes] = await conn.query(
        `INSERT INTO prestamos (
          codigo_certificado, usuario_id, equipo_id, equipo_nombre_snapshot, equipo_categoria_snapshot,
          cantidad, nombre_solicitante, documento_solicitante, ambiente_destino, motivo,
          fecha_prestamo, fecha_devolucion_estimada, estado, evidencia_url
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, 'ACTIVO', ?)`,
        [
          tempCertCode,
          req.user.id,
          eqId,
          equipoRow.nombre,
          equipoRow.categoria,
          cantNum,
          solicitanteTrim,
          docTrim,
          ambienteTrim,
          textoMotivo,
          fechaDev,
          evidenciaUrl
        ]
      );

      const prestamoId = insertRes.insertId;
      const codigoCertificado = generarCodigoCertificado(prestamoId);
      await conn.query(
        'UPDATE prestamos SET codigo_certificado = ? WHERE id = ?',
        [codigoCertificado, prestamoId]
      );

      // Crear automáticamente la sala de chat asociada al préstamo
      const [chatRes] = await conn.query(
        'INSERT INTO chats (prestamo_id, solo_lectura) VALUES (?, 0)',
        [prestamoId]
      );
      const chatId = chatRes.insertId;

      // Mensaje inicial del sistema/usuario abriendo el canal de soporte
      const msgInicial = `Préstamo #${prestamoId} (${codigoCertificado}) iniciado por ${solicitanteTrim} para ${cantNum} unidad(es) de "${equipoRow.nombre}".`;
      const [msgRes] = await conn.query(
        `INSERT INTO chat_mensajes (chat_id, remitente_id, remitente_nombre, remitente_rol, tipo, contenido)
         VALUES (?, ?, ?, ?, 'TEXTO', ?)`,
        [chatId, req.user.id, req.user.nombre, req.user.rol, msgInicial]
      );

      // Marcar leído para quien lo creó
      await conn.query(
        `INSERT INTO chat_lecturas (chat_id, usuario_id, ultimo_mensaje_leido_id)
         VALUES (?, ?, ?)`,
        [chatId, req.user.id, msgRes.insertId]
      );

      await registrarAuditoria(conn, {
        usuario: req.user,
        accion: 'CREAR_PRESTAMO',
        entidad: 'PRESTAMO',
        entidadId: prestamoId,
        detalle: {
          codigoCertificado,
          equipoId: eqId,
          equipoNombre: equipoRow.nombre,
          cantidad: cantNum,
          stockRestante: nuevoStockDisponible
        }
      });

      const prestamoCreado = await fetchPrestamoDetallado(conn, prestamoId, req.user.id);
      await conn.commit();

      emitStockUpdate({
        equipoId: eqId,
        stockTotal: Number(equipoRow.stock_total),
        stockDisponible: nuevoStockDisponible
      });

      emitPrestamoUpdate({
        accion: 'CREADO',
        prestamo: prestamoCreado
      });

      emitChatMensaje({
        prestamoId,
        usuarioDueñoId: req.user.id,
        mensaje: {
          id: msgRes.insertId,
          chatId,
          prestamoId,
          remitenteId: req.user.id,
          remitenteNombre: req.user.nombre,
          remitenteRol: req.user.rol,
          tipo: 'TEXTO',
          contenido: msgInicial,
          archivoUrl: null,
          createdAt: new Date().toISOString()
        }
      });

      return res.status(201).json({
        mensaje: 'Préstamo registrado exitosamente',
        prestamo: prestamoCreado,
        stockDisponible: nuevoStockDisponible
      });
    } catch (err) {
      await conn.rollback();
      console.error('[POST /prestamos]', err);
      return res.status(500).json({ error: 'Error interno al procesar el préstamo.' });
    } finally {
      conn.release();
    }
  }
);

/**
 * GET /api/prestamos/mios
 * Lista los préstamos del usuario autenticado (con filtro opcional ?estado=ACTIVO|CANCELADO|ENTREGADO).
 */
router.get('/prestamos/mios', verifyToken, async (req, res) => {
  try {
    const estado = String(req.query.estado || '').trim().toUpperCase();
    const conditions = ['p.usuario_id = ?'];
    const params = [req.user.id, req.user.id, req.user.id];

    if (['ACTIVO', 'CANCELADO', 'ENTREGADO'].includes(estado)) {
      conditions.push('p.estado = ?');
      params.push(estado);
    }

    const pool = getPool();
    const [rows] = await pool.query(
      `SELECT p.*,
              u.correo AS usuario_correo,
              e.nombre AS equipo_nombre,
              e.categoria AS equipo_categoria,
              e.imagen_url AS equipo_imagen_url,
              g.nombre AS gestionado_por_nombre,
              c.id AS chat_id,
              c.solo_lectura AS chat_solo_lectura,
              (
                SELECT COUNT(*)
                FROM chat_mensajes m
                LEFT JOIN chat_lecturas cl ON cl.chat_id = c.id AND cl.usuario_id = ?
                WHERE m.chat_id = c.id
                  AND m.remitente_id != ?
                  AND m.id > COALESCE(cl.ultimo_mensaje_leido_id, 0)
              ) AS unread_count
       FROM prestamos p
       JOIN usuarios u ON u.id = p.usuario_id
       LEFT JOIN equipos e ON e.id = p.equipo_id
       LEFT JOIN usuarios g ON g.id = p.gestionado_por
       LEFT JOIN chats c ON c.prestamo_id = p.id
       WHERE ${conditions.join(' AND ')}
       ORDER BY p.id DESC`,
      params
    );

    return res.json({
      prestamos: rows.map(mapPrestamoRow)
    });
  } catch (err) {
    console.error('[GET /prestamos/mios]', err);
    return res.status(500).json({ error: 'Error al consultar tus préstamos.' });
  }
});

/**
 * GET /api/prestamos
 * Si el usuario es ADMIN_EQUIPOS devuelve todos los préstamos del sistema;
 * si es USUARIO devuelve únicamente sus propios préstamos.
 */
router.get('/prestamos', verifyToken, async (req, res) => {
  try {
    const estado = String(req.query.estado || '').trim().toUpperCase();
    const conditions = ['1 = 1'];
    const params = [req.user.id, req.user.id];

    if (req.user.rol !== 'ADMIN_EQUIPOS') {
      conditions.push('p.usuario_id = ?');
      params.push(req.user.id);
    }

    if (['ACTIVO', 'CANCELADO', 'ENTREGADO'].includes(estado)) {
      conditions.push('p.estado = ?');
      params.push(estado);
    }

    const pool = getPool();
    const [rows] = await pool.query(
      `SELECT p.*,
              u.correo AS usuario_correo,
              e.nombre AS equipo_nombre,
              e.categoria AS equipo_categoria,
              e.imagen_url AS equipo_imagen_url,
              g.nombre AS gestionado_por_nombre,
              c.id AS chat_id,
              c.solo_lectura AS chat_solo_lectura,
              (
                SELECT COUNT(*)
                FROM chat_mensajes m
                LEFT JOIN chat_lecturas cl ON cl.chat_id = c.id AND cl.usuario_id = ?
                WHERE m.chat_id = c.id
                  AND m.remitente_id != ?
                  AND m.id > COALESCE(cl.ultimo_mensaje_leido_id, 0)
              ) AS unread_count
       FROM prestamos p
       JOIN usuarios u ON u.id = p.usuario_id
       LEFT JOIN equipos e ON e.id = p.equipo_id
       LEFT JOIN usuarios g ON g.id = p.gestionado_por
       LEFT JOIN chats c ON c.prestamo_id = p.id
       WHERE ${conditions.join(' AND ')}
       ORDER BY p.id DESC`,
      params
    );

    return res.json({
      prestamos: rows.map(mapPrestamoRow)
    });
  } catch (err) {
    console.error('[GET /prestamos]', err);
    return res.status(500).json({ error: 'Error al consultar préstamos.' });
  }
});

/**
 * GET /api/prestamos/:id
 */
router.get('/prestamos/:id', verifyToken, async (req, res) => {
  try {
    const prestamoId = Number(req.params.id);
    const pool = getPool();
    const prestamo = await fetchPrestamoDetallado(pool, prestamoId, req.user.id);
    if (!prestamo) {
      return res.status(404).json({ error: 'Préstamo no encontrado.' });
    }

    if (req.user.rol !== 'ADMIN_EQUIPOS' && prestamo.usuarioId !== req.user.id) {
      return res.status(403).json({ error: 'No tienes permiso para ver este préstamo.' });
    }

    return res.json({ prestamo });
  } catch (err) {
    console.error('[GET /prestamos/:id]', err);
    return res.status(500).json({ error: 'Error al obtener detalle del préstamo.' });
  }
});

/**
 * PATCH /api/prestamos/:id/cancelar
 * El usuario dueño (o un ADMIN_EQUIPOS) puede cancelar un préstamo en estado ACTIVO.
 * De forma atómica devuelve las unidades al stock del equipo y pasa el chat a modo lectura.
 */
router.patch('/prestamos/:id/cancelar', verifyToken, async (req, res) => {
  const pool = getPool();
  const conn = await pool.getConnection();
  try {
    const prestamoId = Number(req.params.id);
    await conn.beginTransaction();

    const [pRows] = await conn.query(
      'SELECT * FROM prestamos WHERE id = ? FOR UPDATE',
      [prestamoId]
    );

    if (pRows.length === 0) {
      await conn.rollback();
      return res.status(404).json({ error: 'Préstamo no encontrado.' });
    }

    const prestamoRow = pRows[0];
    if (req.user.rol !== 'ADMIN_EQUIPOS' && prestamoRow.usuario_id !== req.user.id) {
      await conn.rollback();
      return res.status(403).json({ error: 'Solo el titular del préstamo o un administrador puede cancelarlo.' });
    }

    if (prestamoRow.estado !== 'ACTIVO') {
      await conn.rollback();
      return res.status(409).json({
        error: `No se puede cancelar un préstamo que ya se encuentra en estado ${prestamoRow.estado}.`
      });
    }

    await conn.query(
      "UPDATE prestamos SET estado = 'CANCELADO', fecha_cierre = NOW() WHERE id = ?",
      [prestamoId]
    );

    // Cerrar el chat en modo lectura
    await conn.query(
      'UPDATE chats SET solo_lectura = 1 WHERE prestamo_id = ?',
      [prestamoId]
    );

    // Devolver las unidades al stock del equipo de forma atómica
    let stockInfo = null;
    if (prestamoRow.equipo_id) {
      const [eqRows] = await conn.query(
        'SELECT id, stock_total, stock_disponible FROM equipos WHERE id = ? FOR UPDATE',
        [prestamoRow.equipo_id]
      );
      if (eqRows.length > 0) {
        const eq = eqRows[0];
        const nuevoDisponible = Math.min(
          Number(eq.stock_total),
          Number(eq.stock_disponible) + Number(prestamoRow.cantidad)
        );
        await conn.query(
          'UPDATE equipos SET stock_disponible = ?, version = version + 1 WHERE id = ?',
          [nuevoDisponible, eq.id]
        );
        stockInfo = {
          equipoId: eq.id,
          stockTotal: Number(eq.stock_total),
          stockDisponible: nuevoDisponible
        };
      }
    }

    await registrarAuditoria(conn, {
      usuario: req.user,
      accion: 'CANCELAR_PRESTAMO',
      entidad: 'PRESTAMO',
      entidadId: prestamoId,
      detalle: {
        codigoCertificado: prestamoRow.codigo_certificado,
        cantidadDevuelta: prestamoRow.cantidad,
        equipoId: prestamoRow.equipo_id
      }
    });

    const prestamoActualizado = await fetchPrestamoDetallado(conn, prestamoId, req.user.id);
    await conn.commit();

    if (stockInfo) {
      emitStockUpdate(stockInfo);
    }

    emitPrestamoUpdate({
      accion: 'CANCELADO',
      prestamo: prestamoActualizado
    });

    return res.json({
      mensaje: 'Préstamo cancelado y unidades reintegradas al stock.',
      prestamo: prestamoActualizado,
      stock: stockInfo
    });
  } catch (err) {
    await conn.rollback();
    console.error('[PATCH /prestamos/:id/cancelar]', err);
    return res.status(500).json({ error: 'Error interno al cancelar el préstamo.' });
  } finally {
    conn.release();
  }
});

/**
 * PATCH /api/prestamos/:id/entregar
 * Exclusivo ADMIN_EQUIPOS: marca un préstamo ACTIVO como ENTREGADO (devolución),
 * reintegra las unidades al stock y pasa el chat a modo lectura.
 */
router.patch('/prestamos/:id/entregar', verifyToken, requireRole('ADMIN_EQUIPOS'), async (req, res) => {
  const pool = getPool();
  const conn = await pool.getConnection();
  try {
    const prestamoId = Number(req.params.id);
    await conn.beginTransaction();

    const [pRows] = await conn.query(
      'SELECT * FROM prestamos WHERE id = ? FOR UPDATE',
      [prestamoId]
    );

    if (pRows.length === 0) {
      await conn.rollback();
      return res.status(404).json({ error: 'Préstamo no encontrado.' });
    }

    const prestamoRow = pRows[0];
    if (prestamoRow.estado !== 'ACTIVO') {
      await conn.rollback();
      return res.status(409).json({
        error: `Solo se pueden marcar como ENTREGADOS los préstamos en estado ACTIVO (actual: ${prestamoRow.estado}).`
      });
    }

    await conn.query(
      "UPDATE prestamos SET estado = 'ENTREGADO', fecha_cierre = NOW(), gestionado_por = ? WHERE id = ?",
      [req.user.id, prestamoId]
    );

    await conn.query(
      'UPDATE chats SET solo_lectura = 1 WHERE prestamo_id = ?',
      [prestamoId]
    );

    let stockInfo = null;
    if (prestamoRow.equipo_id) {
      const [eqRows] = await conn.query(
        'SELECT id, stock_total, stock_disponible FROM equipos WHERE id = ? FOR UPDATE',
        [prestamoRow.equipo_id]
      );
      if (eqRows.length > 0) {
        const eq = eqRows[0];
        const nuevoDisponible = Math.min(
          Number(eq.stock_total),
          Number(eq.stock_disponible) + Number(prestamoRow.cantidad)
        );
        await conn.query(
          'UPDATE equipos SET stock_disponible = ?, version = version + 1 WHERE id = ?',
          [nuevoDisponible, eq.id]
        );
        stockInfo = {
          equipoId: eq.id,
          stockTotal: Number(eq.stock_total),
          stockDisponible: nuevoDisponible
        };
      }
    }

    await registrarAuditoria(conn, {
      usuario: req.user,
      accion: 'ENTREGAR_PRESTAMO',
      entidad: 'PRESTAMO',
      entidadId: prestamoId,
      detalle: {
        codigoCertificado: prestamoRow.codigo_certificado,
        cantidadDevuelta: prestamoRow.cantidad,
        equipoId: prestamoRow.equipo_id
      }
    });

    const prestamoActualizado = await fetchPrestamoDetallado(conn, prestamoId, req.user.id);
    await conn.commit();

    if (stockInfo) {
      emitStockUpdate(stockInfo);
    }

    emitPrestamoUpdate({
      accion: 'ENTREGADO',
      prestamo: prestamoActualizado
    });

    return res.json({
      mensaje: 'Préstamo marcado como ENTREGADO y existencias actualizadas.',
      prestamo: prestamoActualizado,
      stock: stockInfo
    });
  } catch (err) {
    await conn.rollback();
    console.error('[PATCH /prestamos/:id/entregar]', err);
    return res.status(500).json({ error: 'Error interno al registrar la devolución del préstamo.' });
  } finally {
    conn.release();
  }
});

module.exports = router;
