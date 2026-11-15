const express = require('express');
const path = require('path');
const { getPool } = require('../config/db');
const { verifyToken } = require('../middlewares/authMiddleware');
const { uploadChatAttachment, handleMulterError } = require('../middlewares/uploadMiddleware');
const { emitChatMensaje, emitChatLeido } = require('../socket/socketServer');

const router = express.Router();

function mapMensajeRow(row, prestamoId) {
  return {
    id: row.id,
    chatId: row.chat_id,
    prestamoId: Number(prestamoId),
    remitenteId: row.remitente_id,
    remitenteNombre: row.remitente_nombre,
    remitenteRol: row.remitente_rol,
    tipo: row.tipo,
    contenido: row.contenido || '',
    archivoUrl: row.archivo_url || null,
    archivoNombre: row.archivo_nombre || null,
    archivoMime: row.archivo_mime || null,
    archivoTamano: row.archivo_tamano ? Number(row.archivo_tamano) : null,
    duracionSeg: row.duracion_seg ? Number(row.duracion_seg) : null,
    createdAt: row.created_at ? new Date(row.created_at).toISOString() : new Date().toISOString()
  };
}

function detectarTipoAdjunto(file, tipoSolicitado) {
  const solicitado = String(tipoSolicitado || '').trim().toUpperCase();
  if (['IMAGEN', 'ARCHIVO', 'AUDIO'].includes(solicitado)) {
    return solicitado;
  }
  const mime = (file.mimetype || '').toLowerCase();
  const ext = path.extname(file.originalname || '').toLowerCase();
  if (mime.startsWith('image/') || ['.jpg', '.jpeg', '.png', '.webp'].includes(ext)) {
    return 'IMAGEN';
  }
  if (
    mime.startsWith('audio/') ||
    ['.m4a', '.mp3', '.aac', '.wav', '.ogg', '.3gp'].includes(ext)
  ) {
    return 'AUDIO';
  }
  return 'ARCHIVO';
}

async function verificarAccesoChat(pool, prestamoId, user) {
  const [rows] = await pool.query(
    `SELECT p.id AS prestamo_id, p.usuario_id, p.estado AS prestamo_estado,
            c.id AS chat_id, c.solo_lectura
     FROM prestamos p
     LEFT JOIN chats c ON c.prestamo_id = p.id
     WHERE p.id = ?
     LIMIT 1`,
    [prestamoId]
  );

  if (rows.length === 0) {
    return { errorStatus: 404, error: 'El préstamo asociado al chat no existe.' };
  }

  const info = rows[0];
  const esParticipante = user.rol === 'ADMIN_EQUIPOS' || info.usuario_id === user.id;
  if (!esParticipante) {
    return {
      errorStatus: 403,
      error: 'Acceso denegado: Solo los participantes del préstamo pueden ver o interactuar con este chat.'
    };
  }

  // Asegurar que exista el registro en chats
  let chatId = info.chat_id;
  const soloLectura = info.prestamo_estado !== 'ACTIVO' || Boolean(info.solo_lectura);
  if (!chatId) {
    const [ins] = await pool.query(
      'INSERT INTO chats (prestamo_id, solo_lectura) VALUES (?, ?)',
      [prestamoId, soloLectura ? 1 : 0]
    );
    chatId = ins.insertId;
  }

  return {
    prestamoId: info.prestamo_id,
    usuarioDueñoId: info.usuario_id,
    prestamoEstado: info.prestamo_estado,
    chatId,
    soloLectura
  };
}

/**
 * GET /api/prestamos/:id/chat
 * Obtiene el historial persistente del chat del préstamo.
 */
router.get('/prestamos/:id/chat', verifyToken, async (req, res) => {
  try {
    const prestamoId = Number(req.params.id);
    const pool = getPool();

    const acceso = await verificarAccesoChat(pool, prestamoId, req.user);
    if (acceso.error) {
      return res.status(acceso.errorStatus).json({ error: acceso.error });
    }

    const [mensajesRows] = await pool.query(
      'SELECT * FROM chat_mensajes WHERE chat_id = ? ORDER BY id ASC',
      [acceso.chatId]
    );

    const [lecturaRows] = await pool.query(
      'SELECT ultimo_mensaje_leido_id FROM chat_lecturas WHERE chat_id = ? AND usuario_id = ? LIMIT 1',
      [acceso.chatId, req.user.id]
    );

    const ultimoLeidoId = lecturaRows.length > 0 ? Number(lecturaRows[0].ultimo_mensaje_leido_id) : 0;
    const mensajes = mensajesRows.map((r) => mapMensajeRow(r, prestamoId));
    const unreadCount = mensajes.filter(
      (m) => m.remitenteId !== req.user.id && m.id > ultimoLeidoId
    ).length;

    return res.json({
      chatId: acceso.chatId,
      prestamoId,
      prestamoEstado: acceso.prestamoEstado,
      soloLectura: acceso.soloLectura,
      ultimoMensajeLeidoId: ultimoLeidoId,
      unreadCount,
      mensajes
    });
  } catch (err) {
    console.error('[GET /prestamos/:id/chat]', err);
    return res.status(500).json({ error: 'Error al consultar los mensajes del chat.' });
  }
});

/**
 * POST /api/prestamos/:id/chat/mensajes
 * Envía un mensaje (TEXTO, IMAGEN, ARCHIVO o AUDIO).
 * Rechaza el envío si el préstamo está cerrado (modo solo lectura).
 */
router.post(
  '/prestamos/:id/chat/mensajes',
  verifyToken,
  uploadChatAttachment.single('archivo'),
  handleMulterError,
  async (req, res) => {
    try {
      const prestamoId = Number(req.params.id);
      const pool = getPool();

      const acceso = await verificarAccesoChat(pool, prestamoId, req.user);
      if (acceso.error) {
        return res.status(acceso.errorStatus).json({ error: acceso.error });
      }

      if (acceso.soloLectura) {
        return res.status(403).json({
          error: `El préstamo se encuentra en estado ${acceso.prestamoEstado}. El chat está en modo solo lectura y no admite nuevos mensajes.`
        });
      }

      const { contenido, tipo, duracionSeg } = req.body || {};
      const texto = String(contenido || '').trim();

      if (!req.file && !texto) {
        return res.status(400).json({ error: 'Debe enviar un mensaje de texto o adjuntar un archivo.' });
      }

      const tipoFinal = req.file ? detectarTipoAdjunto(req.file, tipo) : 'TEXTO';
      const archivoUrl = req.file ? `/uploads/chat/${req.file.filename}` : null;
      const archivoNombre = req.file ? req.file.originalname : null;
      const archivoMime = req.file ? req.file.mimetype : null;
      const archivoTamano = req.file ? req.file.size : null;
      const duracion = duracionSeg ? Number(duracionSeg) : null;

      const contenidoFinal =
        texto ||
        (tipoFinal === 'IMAGEN'
          ? '📷 Imagen adjunta'
          : tipoFinal === 'AUDIO'
            ? '🎤 Nota de voz'
            : `📎 Archivo: ${archivoNombre}`);

      const [insertRes] = await pool.query(
        `INSERT INTO chat_mensajes (
          chat_id, remitente_id, remitente_nombre, remitente_rol,
          tipo, contenido, archivo_url, archivo_nombre, archivo_mime, archivo_tamano, duracion_seg
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          acceso.chatId,
          req.user.id,
          req.user.nombre,
          req.user.rol,
          tipoFinal,
          contenidoFinal,
          archivoUrl,
          archivoNombre,
          archivoMime,
          archivoTamano,
          duracion
        ]
      );

      const nuevoMsgId = insertRes.insertId;

      // Actualizar lectura del propio remitente
      await pool.query(
        `INSERT INTO chat_lecturas (chat_id, usuario_id, ultimo_mensaje_leido_id)
         VALUES (?, ?, ?)
         ON DUPLICATE KEY UPDATE ultimo_mensaje_leido_id = GREATEST(ultimo_mensaje_leido_id, VALUES(ultimo_mensaje_leido_id))`,
        [acceso.chatId, req.user.id, nuevoMsgId]
      );

      const [rows] = await pool.query('SELECT * FROM chat_mensajes WHERE id = ? LIMIT 1', [nuevoMsgId]);
      const mensaje = mapMensajeRow(rows[0], prestamoId);

      emitChatMensaje({
        prestamoId,
        usuarioDueñoId: acceso.usuarioDueñoId,
        mensaje
      });

      return res.status(201).json({
        mensaje: 'Mensaje enviado',
        chatMensaje: mensaje
      });
    } catch (err) {
      console.error('[POST /prestamos/:id/chat/mensajes]', err);
      return res.status(500).json({ error: 'Error al enviar el mensaje.' });
    }
  }
);

/**
 * POST /api/prestamos/:id/chat/leido
 * Marca todos los mensajes actuales del chat como leídos por el usuario autenticado.
 */
router.post('/prestamos/:id/chat/leido', verifyToken, async (req, res) => {
  try {
    const prestamoId = Number(req.params.id);
    const pool = getPool();

    const acceso = await verificarAccesoChat(pool, prestamoId, req.user);
    if (acceso.error) {
      return res.status(acceso.errorStatus).json({ error: acceso.error });
    }

    const [maxRows] = await pool.query(
      'SELECT COALESCE(MAX(id), 0) AS maxId FROM chat_mensajes WHERE chat_id = ?',
      [acceso.chatId]
    );
    const ultimoId = Number(maxRows[0].maxId || 0);

    await pool.query(
      `INSERT INTO chat_lecturas (chat_id, usuario_id, ultimo_mensaje_leido_id)
       VALUES (?, ?, ?)
       ON DUPLICATE KEY UPDATE ultimo_mensaje_leido_id = GREATEST(ultimo_mensaje_leido_id, VALUES(ultimo_mensaje_leido_id))`,
      [acceso.chatId, req.user.id, ultimoId]
    );

    emitChatLeido({
      prestamoId,
      lectorId: req.user.id,
      ultimoMensajeId: ultimoId
    });

    return res.json({
      ok: true,
      prestamoId,
      ultimoMensajeLeidoId: ultimoId
    });
  } catch (err) {
    console.error('[POST /prestamos/:id/chat/leido]', err);
    return res.status(500).json({ error: 'Error al actualizar estado de lectura del chat.' });
  }
});

module.exports = router;
