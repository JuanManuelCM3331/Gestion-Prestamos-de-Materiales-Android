const { Server } = require('socket.io');
const jwt = require('jsonwebtoken');
const { getPool } = require('../config/db');
const { JWT_SECRET } = require('../middlewares/authMiddleware');

let ioInstance = null;

function initSocketServer(httpServer) {
  const io = new Server(httpServer, {
    cors: {
      origin: '*',
      methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']
    }
  });

  // Autenticación opcional o con token JWT en el handshake
  io.use(async (socket, next) => {
    try {
      const token =
        socket.handshake.auth?.token ||
        (socket.handshake.headers?.authorization || '').replace(/^Bearer\s+/i, '').trim() ||
        socket.handshake.query?.token;

      if (!token) {
        socket.user = null;
        return next();
      }

      const decoded = jwt.verify(token, JWT_SECRET);
      const pool = getPool();
      const [rows] = await pool.query(
        'SELECT id, nombre, correo, rol FROM usuarios WHERE id = ? LIMIT 1',
        [decoded.id]
      );

      socket.user = rows.length > 0 ? rows[0] : null;
      next();
    } catch (_err) {
      socket.user = null;
      next();
    }
  });

  io.on('connection', (socket) => {
    // Todos los clientes reciben eventos públicos del catálogo y stock
    socket.join('catalogo');

    if (socket.user) {
      socket.join(`usuario:${socket.user.id}`);
      if (socket.user.rol === 'ADMIN_EQUIPOS') {
        socket.join('admins');
      }
    }

    // Suscribirse a un equipo específico (para detectar si es editado o eliminado mientras lo ve)
    socket.on('join:equipo', ({ equipoId } = {}) => {
      if (equipoId) {
        socket.join(`equipo:${Number(equipoId)}`);
      }
    });

    socket.on('leave:equipo', ({ equipoId } = {}) => {
      if (equipoId) {
        socket.leave(`equipo:${Number(equipoId)}`);
      }
    });

    // Suscribirse al chat y eventos de un préstamo (validando en BD que sea participante)
    socket.on('join:prestamo', async ({ prestamoId } = {}, callback) => {
      try {
        if (!socket.user || !prestamoId) {
          if (typeof callback === 'function') callback({ ok: false, error: 'No autorizado' });
          return;
        }
        const pool = getPool();
        const [rows] = await pool.query(
          'SELECT id, usuario_id FROM prestamos WHERE id = ? LIMIT 1',
          [Number(prestamoId)]
        );
        if (rows.length === 0) {
          if (typeof callback === 'function') callback({ ok: false, error: 'Préstamo no encontrado' });
          return;
        }

        const esParticipante =
          socket.user.rol === 'ADMIN_EQUIPOS' || rows[0].usuario_id === socket.user.id;

        if (!esParticipante) {
          if (typeof callback === 'function') callback({ ok: false, error: 'Acceso denegado al chat' });
          return;
        }

        socket.join(`prestamo:${Number(prestamoId)}`);
        if (typeof callback === 'function') callback({ ok: true });
      } catch (err) {
        if (typeof callback === 'function') callback({ ok: false, error: err.message });
      }
    });

    socket.on('leave:prestamo', ({ prestamoId } = {}) => {
      if (prestamoId) {
        socket.leave(`prestamo:${Number(prestamoId)}`);
      }
    });
  });

  ioInstance = io;
  return io;
}

function getIO() {
  return ioInstance;
}

function emitCatalogoUpdate(payload) {
  if (!ioInstance) return;
  ioInstance.to('catalogo').to('admins').emit('catalogo:actualizado', payload);
  if (payload?.equipo?.id) {
    ioInstance.to(`equipo:${payload.equipo.id}`).emit('equipo:detalle_actualizado', payload);
  }
}

function emitStockUpdate({ equipoId, stockTotal, stockDisponible }) {
  if (!ioInstance) return;
  const data = {
    equipoId: Number(equipoId),
    stockTotal: Number(stockTotal),
    stockDisponible: Number(stockDisponible),
    timestamp: Date.now()
  };
  ioInstance.to('catalogo').to('admins').to(`equipo:${Number(equipoId)}`).emit('stock:actualizado', data);
}

function emitPrestamoUpdate({ accion, prestamo }) {
  if (!ioInstance || !prestamo) return;
  const payload = { accion, prestamo, timestamp: Date.now() };
  ioInstance
    .to('admins')
    .to(`usuario:${prestamo.usuarioId}`)
    .to(`prestamo:${prestamo.id}`)
    .emit('prestamo:actualizado', payload);
}

function emitChatMensaje({ prestamoId, usuarioDueñoId, mensaje }) {
  if (!ioInstance) return;
  const payload = { prestamoId: Number(prestamoId), mensaje, timestamp: Date.now() };
  ioInstance
    .to(`prestamo:${Number(prestamoId)}`)
    .to(`usuario:${Number(usuarioDueñoId)}`)
    .to('admins')
    .emit('chat:mensaje_nuevo', payload);
}

function emitChatLeido({ prestamoId, lectorId, ultimoMensajeId }) {
  if (!ioInstance) return;
  ioInstance.to(`prestamo:${Number(prestamoId)}`).emit('chat:mensajes_leidos', {
    prestamoId: Number(prestamoId),
    lectorId: Number(lectorId),
    ultimoMensajeId: Number(ultimoMensajeId),
    timestamp: Date.now()
  });
}

module.exports = {
  initSocketServer,
  getIO,
  emitCatalogoUpdate,
  emitStockUpdate,
  emitPrestamoUpdate,
  emitChatMensaje,
  emitChatLeido
};
