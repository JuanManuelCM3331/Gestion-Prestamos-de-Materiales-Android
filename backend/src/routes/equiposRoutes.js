const express = require('express');
const { getPool } = require('../config/db');
const { verifyToken, requireRole } = require('../middlewares/authMiddleware');
const { uploadEquipoImage, handleMulterError } = require('../middlewares/uploadMiddleware');
const { registrarAuditoria } = require('../services/auditService');
const { emitCatalogoUpdate, emitStockUpdate } = require('../socket/socketServer');

const router = express.Router();

const CATEGORIAS_VALIDAS = new Set([
  'ELECTRONICA',
  'MULTIMEDIA',
  'INFORMATICA',
  'HERRAMIENTAS',
  'OTRO'
]);

function mapEquipoRow(row) {
  const stockDisponible = Number(row.stock_disponible);
  const stockTotal = Number(row.stock_total);
  const estado = stockDisponible > 0 ? 'DISPONIBLE' : 'PRESTADO';
  return {
    id: row.id,
    nombre: row.nombre,
    categoria: row.categoria,
    descripcion: row.descripcion || '',
    imagenUrl: row.imagen_url || null,
    stockTotal,
    stockDisponible,
    unidadesEnPrestamoActivo: Math.max(0, stockTotal - stockDisponible),
    estado,
    eliminado: Boolean(row.eliminado),
    eliminadoAt: row.eliminado_at || null,
    version: Number(row.version || 1),
    updatedAt: row.updated_at ? new Date(row.updated_at).getTime() : Date.now()
  };
}

/**
 * GET /api/equipos
 * Soporta filtros: ?search=texto&categoria=ELECTRONICA&eliminados=true|false
 * Solo ADMIN_EQUIPOS puede consultar eliminados=true.
 */
router.get('/equipos', verifyToken, async (req, res) => {
  try {
    const search = String(req.query.search || '').trim();
    const categoria = String(req.query.categoria || '').trim().toUpperCase();
    const eliminadosParam = String(req.query.eliminados || 'false').toLowerCase() === 'true';

    if (eliminadosParam && req.user.rol !== 'ADMIN_EQUIPOS') {
      return res.status(403).json({
        error: 'Solo los administradores de equipos pueden acceder a la pestaña de equipos eliminados.'
      });
    }

    const conditions = ['eliminado = ?'];
    const params = [eliminadosParam ? 1 : 0];

    if (search) {
      conditions.push('(nombre LIKE ? OR descripcion LIKE ?)');
      params.push(`%${search}%`, `%${search}%`);
    }

    if (categoria && CATEGORIAS_VALIDAS.has(categoria)) {
      conditions.push('categoria = ?');
      params.push(categoria);
    }

    const pool = getPool();
    const [rows] = await pool.query(
      `SELECT * FROM equipos WHERE ${conditions.join(' AND ')} ORDER BY id DESC`,
      params
    );

    return res.json({
      equipos: rows.map(mapEquipoRow)
    });
  } catch (err) {
    console.error('[GET /equipos]', err);
    return res.status(500).json({ error: 'Error al consultar el catálogo de equipos.' });
  }
});

/**
 * GET /api/equipos/:id
 */
router.get('/equipos/:id', verifyToken, async (req, res) => {
  try {
    const equipoId = Number(req.params.id);
    if (!Number.isInteger(equipoId) || equipoId <= 0) {
      return res.status(400).json({ error: 'ID de equipo inválido.' });
    }

    const pool = getPool();
    const [rows] = await pool.query('SELECT * FROM equipos WHERE id = ? LIMIT 1', [equipoId]);
    if (rows.length === 0) {
      return res.status(404).json({ error: 'El equipo solicitado no existe.' });
    }

    const equipo = mapEquipoRow(rows[0]);
    if (equipo.eliminado && req.user.rol !== 'ADMIN_EQUIPOS') {
      return res.status(404).json({ error: 'Este equipo fue retirado del catálogo.', eliminado: true });
    }

    return res.json({ equipo });
  } catch (err) {
    console.error('[GET /equipos/:id]', err);
    return res.status(500).json({ error: 'Error al obtener detalle del equipo.' });
  }
});

/**
 * POST /api/equipos
 * Crear equipo en el catálogo (Solo ADMIN_EQUIPOS).
 * Soporta multipart/form-data (campo 'imagen') o JSON con 'imagenUrl'.
 */
router.post(
  '/equipos',
  verifyToken,
  requireRole('ADMIN_EQUIPOS'),
  uploadEquipoImage.single('imagen'),
  handleMulterError,
  async (req, res) => {
    try {
      const { nombre, categoria, stockTotal, descripcion, imagenUrl } = req.body || {};

      const nombreTrim = String(nombre || '').trim();
      if (nombreTrim.length < 3) {
        return res.status(400).json({ error: 'El nombre del equipo es obligatorio (mínimo 3 caracteres).' });
      }

      const catUpper = String(categoria || '').trim().toUpperCase();
      if (!CATEGORIAS_VALIDAS.has(catUpper)) {
        return res.status(400).json({ error: 'Categoría inválida.' });
      }

      const stockNum = Number(stockTotal);
      if (!Number.isInteger(stockNum) || stockNum < 0) {
        return res.status(400).json({ error: 'La cantidad de existencias debe ser un número entero mayor o igual a 0.' });
      }

      const descTrim = String(descripcion || '').trim();
      if (descTrim.length < 5) {
        return res.status(400).json({ error: 'La descripción o aclaración es obligatoria (mínimo 5 caracteres).' });
      }

      const finalImageUrl = req.file
        ? `/uploads/equipos/${req.file.filename}`
        : imagenUrl
          ? String(imagenUrl).trim()
          : null;

      const pool = getPool();
      const [result] = await pool.query(
        `INSERT INTO equipos (nombre, categoria, descripcion, imagen_url, stock_total, stock_disponible, eliminado)
         VALUES (?, ?, ?, ?, ?, ?, 0)`,
        [nombreTrim, catUpper, descTrim, finalImageUrl, stockNum, stockNum]
      );

      const [createdRows] = await pool.query('SELECT * FROM equipos WHERE id = ? LIMIT 1', [result.insertId]);
      const equipo = mapEquipoRow(createdRows[0]);

      await registrarAuditoria(pool, {
        usuario: req.user,
        accion: 'CREAR_EQUIPO',
        entidad: 'EQUIPO',
        entidadId: equipo.id,
        detalle: { nombre: equipo.nombre, categoria: equipo.categoria, stockTotal: equipo.stockTotal }
      });

      emitCatalogoUpdate({ accion: 'CREADO', equipo });
      emitStockUpdate({
        equipoId: equipo.id,
        stockTotal: equipo.stockTotal,
        stockDisponible: equipo.stockDisponible
      });

      return res.status(201).json({
        mensaje: 'Equipo creado exitosamente',
        equipo
      });
    } catch (err) {
      console.error('[POST /equipos]', err);
      return res.status(500).json({ error: 'Error interno al crear el equipo.' });
    }
  }
);

/**
 * PUT /api/equipos/:id
 * Editar equipo (todos los campos) garantizando la invariante:
 * stock_disponible = stock_total - unidades_en_prestamo_activo
 */
router.put(
  '/equipos/:id',
  verifyToken,
  requireRole('ADMIN_EQUIPOS'),
  uploadEquipoImage.single('imagen'),
  handleMulterError,
  async (req, res) => {
    const pool = getPool();
    const conn = await pool.getConnection();
    try {
      const equipoId = Number(req.params.id);
      if (!Number.isInteger(equipoId) || equipoId <= 0) {
        return res.status(400).json({ error: 'ID de equipo inválido.' });
      }

      await conn.beginTransaction();

      const [existingRows] = await conn.query(
        'SELECT * FROM equipos WHERE id = ? FOR UPDATE',
        [equipoId]
      );
      if (existingRows.length === 0) {
        await conn.rollback();
        return res.status(404).json({ error: 'Equipo no encontrado.' });
      }

      const actual = existingRows[0];
      const nombreTrim = req.body.nombre !== undefined ? String(req.body.nombre).trim() : actual.nombre;
      if (nombreTrim.length < 3) {
        await conn.rollback();
        return res.status(400).json({ error: 'El nombre del equipo debe tener al menos 3 caracteres.' });
      }

      const catUpper = req.body.categoria !== undefined
        ? String(req.body.categoria).trim().toUpperCase()
        : actual.categoria;
      if (!CATEGORIAS_VALIDAS.has(catUpper)) {
        await conn.rollback();
        return res.status(400).json({ error: 'Categoría inválida.' });
      }

      const descTrim = req.body.descripcion !== undefined ? String(req.body.descripcion).trim() : actual.descripcion;
      if (descTrim.length < 5) {
        await conn.rollback();
        return res.status(400).json({ error: 'La descripción debe tener al menos 5 caracteres.' });
      }

      // Calcular unidades en préstamos activos reales desde la tabla prestamos
      const [prestadosRows] = await conn.query(
        "SELECT COALESCE(SUM(cantidad), 0) AS prestadas FROM prestamos WHERE equipo_id = ? AND estado = 'ACTIVO'",
        [equipoId]
      );
      const unidadesPrestadas = Number(prestadosRows[0].prestadas || 0);

      const nuevoStockTotal = req.body.stockTotal !== undefined
        ? Number(req.body.stockTotal)
        : Number(actual.stock_total);

      if (!Number.isInteger(nuevoStockTotal) || nuevoStockTotal < 0) {
        await conn.rollback();
        return res.status(400).json({ error: 'La cantidad de existencias debe ser un entero no negativo.' });
      }

      if (nuevoStockTotal < unidadesPrestadas) {
        await conn.rollback();
        return res.status(409).json({
          error: `No puedes reducir el stock total a ${nuevoStockTotal} porque actualmente hay ${unidadesPrestadas} unidad(es) en préstamo ACTIVO.`
        });
      }

      const nuevoStockDisponible = nuevoStockTotal - unidadesPrestadas;

      let nuevaImagenUrl = actual.imagen_url;
      if (req.file) {
        nuevaImagenUrl = `/uploads/equipos/${req.file.filename}`;
      } else if (req.body.imagenUrl !== undefined) {
        nuevaImagenUrl = req.body.imagenUrl ? String(req.body.imagenUrl).trim() : null;
      }

      await conn.query(
        `UPDATE equipos
         SET nombre = ?, categoria = ?, descripcion = ?, imagen_url = ?,
             stock_total = ?, stock_disponible = ?, version = version + 1
         WHERE id = ?`,
        [nombreTrim, catUpper, descTrim, nuevaImagenUrl, nuevoStockTotal, nuevoStockDisponible, equipoId]
      );

      // Actualizar snapshot en préstamos activos si cambió el nombre
      if (nombreTrim !== actual.nombre || catUpper !== actual.categoria) {
        await conn.query(
          'UPDATE prestamos SET equipo_nombre_snapshot = ?, equipo_categoria_snapshot = ? WHERE equipo_id = ?',
          [nombreTrim, catUpper, equipoId]
        );
      }

      const [updatedRows] = await conn.query('SELECT * FROM equipos WHERE id = ? LIMIT 1', [equipoId]);
      const equipo = mapEquipoRow(updatedRows[0]);

      await registrarAuditoria(conn, {
        usuario: req.user,
        accion: 'EDITAR_EQUIPO',
        entidad: 'EQUIPO',
        entidadId: equipoId,
        detalle: {
          antes: { nombre: actual.nombre, stockTotal: actual.stock_total },
          despues: { nombre: equipo.nombre, stockTotal: equipo.stockTotal, stockDisponible: equipo.stockDisponible }
        }
      });

      await conn.commit();

      emitCatalogoUpdate({ accion: 'EDITADO', equipo });
      emitStockUpdate({
        equipoId: equipo.id,
        stockTotal: equipo.stockTotal,
        stockDisponible: equipo.stockDisponible
      });

      return res.json({
        mensaje: 'Equipo actualizado exitosamente',
        equipo
      });
    } catch (err) {
      await conn.rollback();
      console.error('[PUT /equipos/:id]', err);
      return res.status(500).json({ error: 'Error interno al editar el equipo.' });
    } finally {
      conn.release();
    }
  }
);

/**
 * DELETE /api/equipos/:id
 * Eliminación lógica: envía el equipo a la pestaña "Eliminados".
 */
router.delete('/equipos/:id', verifyToken, requireRole('ADMIN_EQUIPOS'), async (req, res) => {
  try {
    const equipoId = Number(req.params.id);
    const pool = getPool();

    const [rows] = await pool.query('SELECT * FROM equipos WHERE id = ? LIMIT 1', [equipoId]);
    if (rows.length === 0) {
      return res.status(404).json({ error: 'Equipo no encontrado.' });
    }

    await pool.query(
      'UPDATE equipos SET eliminado = 1, eliminado_at = NOW(), version = version + 1 WHERE id = ?',
      [equipoId]
    );

    const [updatedRows] = await pool.query('SELECT * FROM equipos WHERE id = ? LIMIT 1', [equipoId]);
    const equipo = mapEquipoRow(updatedRows[0]);

    await registrarAuditoria(pool, {
      usuario: req.user,
      accion: 'ELIMINAR_LOGICO_EQUIPO',
      entidad: 'EQUIPO',
      entidadId: equipoId,
      detalle: { nombre: equipo.nombre }
    });

    emitCatalogoUpdate({ accion: 'ELIMINADO', equipo });

    return res.json({
      mensaje: 'Equipo movido a la sección de Eliminados',
      equipo
    });
  } catch (err) {
    console.error('[DELETE /equipos/:id]', err);
    return res.status(500).json({ error: 'Error al eliminar lógicamente el equipo.' });
  }
});

/**
 * PATCH /api/equipos/:id/restaurar
 * Restaura un equipo eliminado lógicamente al catálogo activo.
 */
router.patch('/equipos/:id/restaurar', verifyToken, requireRole('ADMIN_EQUIPOS'), async (req, res) => {
  try {
    const equipoId = Number(req.params.id);
    const pool = getPool();

    const [rows] = await pool.query('SELECT * FROM equipos WHERE id = ? LIMIT 1', [equipoId]);
    if (rows.length === 0) {
      return res.status(404).json({ error: 'Equipo no encontrado.' });
    }

    await pool.query(
      'UPDATE equipos SET eliminado = 0, eliminado_at = NULL, version = version + 1 WHERE id = ?',
      [equipoId]
    );

    const [updatedRows] = await pool.query('SELECT * FROM equipos WHERE id = ? LIMIT 1', [equipoId]);
    const equipo = mapEquipoRow(updatedRows[0]);

    await registrarAuditoria(pool, {
      usuario: req.user,
      accion: 'RESTAURAR_EQUIPO',
      entidad: 'EQUIPO',
      entidadId: equipoId,
      detalle: { nombre: equipo.nombre }
    });

    emitCatalogoUpdate({ accion: 'RESTAURADO', equipo });

    return res.json({
      mensaje: 'Equipo restaurado al catálogo activo',
      equipo
    });
  } catch (err) {
    console.error('[PATCH /equipos/:id/restaurar]', err);
    return res.status(500).json({ error: 'Error al restaurar el equipo.' });
  }
});

/**
 * DELETE /api/equipos/:id/permanente
 * Eliminación permanente: Bloqueada si el equipo tiene préstamos en estado ACTIVO.
 */
router.delete('/equipos/:id/permanente', verifyToken, requireRole('ADMIN_EQUIPOS'), async (req, res) => {
  const pool = getPool();
  const conn = await pool.getConnection();
  try {
    const equipoId = Number(req.params.id);
    await conn.beginTransaction();

    const [rows] = await conn.query('SELECT * FROM equipos WHERE id = ? FOR UPDATE', [equipoId]);
    if (rows.length === 0) {
      await conn.rollback();
      return res.status(404).json({ error: 'Equipo no encontrado.' });
    }

    const equipo = mapEquipoRow(rows[0]);

    const [activosRows] = await conn.query(
      "SELECT COUNT(*) AS totalActivos FROM prestamos WHERE equipo_id = ? AND estado = 'ACTIVO'",
      [equipoId]
    );

    if (Number(activosRows[0].totalActivos) > 0) {
      await conn.rollback();
      return res.status(409).json({
        error: `No se puede eliminar permanentemente "${equipo.nombre}" porque tiene ${activosRows[0].totalActivos} préstamo(s) ACTIVO(S).`
      });
    }

    await conn.query('DELETE FROM equipos WHERE id = ?', [equipoId]);

    await registrarAuditoria(conn, {
      usuario: req.user,
      accion: 'ELIMINAR_PERMANENTE_EQUIPO',
      entidad: 'EQUIPO',
      entidadId: equipoId,
      detalle: { nombre: equipo.nombre, categoria: equipo.categoria }
    });

    await conn.commit();

    emitCatalogoUpdate({ accion: 'BORRADO_PERMANENTE', equipo: { ...equipo, id: equipoId } });

    return res.json({
      mensaje: 'Equipo eliminado permanentemente del sistema',
      equipoId
    });
  } catch (err) {
    await conn.rollback();
    console.error('[DELETE /equipos/:id/permanente]', err);
    return res.status(500).json({ error: 'Error al eliminar permanentemente el equipo.' });
  } finally {
    conn.release();
  }
});

module.exports = router;
