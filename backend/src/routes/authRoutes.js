const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const { getPool } = require('../config/db');
const { verifyToken, requireRole, JWT_SECRET } = require('../middlewares/authMiddleware');
const { registrarAuditoria } = require('../services/auditService');

const router = express.Router();

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function signUserToken(user) {
  return jwt.sign(
    { id: user.id, correo: user.correo, rol: user.rol, nombre: user.nombre },
    JWT_SECRET,
    { expiresIn: process.env.JWT_EXPIRES_IN || '24h' }
  );
}

/**
 * POST /api/auth/register
 * Registro público EXCLUSIVO para rol USUARIO.
 * Aunque envíen rol = 'ADMIN_EQUIPOS' en el body, se ignora o rechaza.
 */
router.post('/auth/register', async (req, res) => {
  try {
    const { nombre, correo, password, rol } = req.body || {};

    if (rol && rol !== 'USUARIO') {
      return res.status(403).json({
        error: 'El registro público solo está permitido para el rol USUARIO. Los administradores solo pueden ser creados por otro ADMIN_EQUIPOS.'
      });
    }

    if (!nombre || String(nombre).trim().length < 3) {
      return res.status(400).json({ error: 'El nombre completo debe tener al menos 3 caracteres.' });
    }

    const emailNormalized = String(correo || '').trim().toLowerCase();
    if (!EMAIL_REGEX.test(emailNormalized)) {
      return res.status(400).json({ error: 'Debe ingresar un correo electrónico válido.' });
    }

    if (!password || String(password).length < 6) {
      return res.status(400).json({ error: 'La contraseña debe tener al menos 6 caracteres.' });
    }

    const pool = getPool();
    const [existing] = await pool.query('SELECT id FROM usuarios WHERE correo = ? LIMIT 1', [emailNormalized]);
    if (existing.length > 0) {
      return res.status(409).json({ error: 'Ya existe una cuenta registrada con este correo electrónico.' });
    }

    const passwordHash = await bcrypt.hash(String(password), 10);
    const [result] = await pool.query(
      `INSERT INTO usuarios (nombre, correo, password_hash, rol, password_temporal)
       VALUES (?, ?, ?, 'USUARIO', 0)`,
      [String(nombre).trim(), emailNormalized, passwordHash]
    );

    const nuevoUsuario = {
      id: result.insertId,
      nombre: String(nombre).trim(),
      correo: emailNormalized,
      rol: 'USUARIO',
      passwordTemporal: false
    };

    await registrarAuditoria(pool, {
      usuario: nuevoUsuario,
      accion: 'REGISTRO_USUARIO',
      entidad: 'USUARIO',
      entidadId: nuevoUsuario.id,
      detalle: { correo: nuevoUsuario.correo }
    });

    const token = signUserToken(nuevoUsuario);
    return res.status(201).json({
      mensaje: 'Usuario registrado exitosamente',
      token,
      usuario: nuevoUsuario
    });
  } catch (err) {
    console.error('[Auth Register]', err);
    return res.status(500).json({ error: 'Error interno al registrar usuario.' });
  }
});

/**
 * POST /api/auth/login
 * Inicio de sesión para ADMIN_EQUIPOS y USUARIO.
 */
router.post('/auth/login', async (req, res) => {
  try {
    const { correo, password } = req.body || {};
    const emailNormalized = String(correo || '').trim().toLowerCase();

    if (!emailNormalized || !password) {
      return res.status(400).json({ error: 'Debe ingresar correo y contraseña.' });
    }

    const pool = getPool();
    const [rows] = await pool.query(
      'SELECT id, nombre, correo, password_hash, rol, password_temporal FROM usuarios WHERE correo = ? LIMIT 1',
      [emailNormalized]
    );

    if (rows.length === 0) {
      return res.status(401).json({ error: 'Credenciales inválidas.' });
    }

    const userRow = rows[0];
    const match = await bcrypt.compare(String(password), userRow.password_hash);
    if (!match) {
      return res.status(401).json({ error: 'Credenciales inválidas.' });
    }

    const usuario = {
      id: userRow.id,
      nombre: userRow.nombre,
      correo: userRow.correo,
      rol: userRow.rol,
      passwordTemporal: Boolean(userRow.password_temporal)
    };

    const token = signUserToken(usuario);
    return res.json({
      mensaje: 'Inicio de sesión exitoso',
      token,
      usuario
    });
  } catch (err) {
    console.error('[Auth Login]', err);
    return res.status(500).json({ error: 'Error interno al iniciar sesión.' });
  }
});

/**
 * GET /api/auth/me
 */
router.get('/auth/me', verifyToken, (req, res) => {
  return res.json({
    usuario: {
      id: req.user.id,
      nombre: req.user.nombre,
      correo: req.user.correo,
      rol: req.user.rol,
      passwordTemporal: Boolean(req.user.password_temporal)
    }
  });
});

/**
 * POST /api/admin/admins
 * Protegido: Solo ADMIN_EQUIPOS puede crear otro ADMIN_EQUIPOS.
 */
router.post('/admin/admins', verifyToken, requireRole('ADMIN_EQUIPOS'), async (req, res) => {
  try {
    const { nombre, correo, passwordTemporal } = req.body || {};

    if (!nombre || String(nombre).trim().length < 3) {
      return res.status(400).json({ error: 'El nombre del administrador debe tener al menos 3 caracteres.' });
    }

    const emailNormalized = String(correo || '').trim().toLowerCase();
    if (!EMAIL_REGEX.test(emailNormalized)) {
      return res.status(400).json({ error: 'Debe ingresar un correo válido para el administrador.' });
    }

    if (!passwordTemporal || String(passwordTemporal).length < 6) {
      return res.status(400).json({ error: 'La contraseña temporal debe tener al menos 6 caracteres.' });
    }

    const pool = getPool();
    const [existing] = await pool.query('SELECT id FROM usuarios WHERE correo = ? LIMIT 1', [emailNormalized]);
    if (existing.length > 0) {
      return res.status(409).json({ error: 'Ya existe un usuario registrado con ese correo.' });
    }

    const hash = await bcrypt.hash(String(passwordTemporal), 10);
    const [result] = await pool.query(
      `INSERT INTO usuarios (nombre, correo, password_hash, rol, password_temporal, creado_por)
       VALUES (?, ?, ?, 'ADMIN_EQUIPOS', 1, ?)`,
      [String(nombre).trim(), emailNormalized, hash, req.user.id]
    );

    const nuevoAdmin = {
      id: result.insertId,
      nombre: String(nombre).trim(),
      correo: emailNormalized,
      rol: 'ADMIN_EQUIPOS',
      passwordTemporal: true,
      creadoPor: req.user.id
    };

    await registrarAuditoria(pool, {
      usuario: req.user,
      accion: 'CREAR_ADMIN',
      entidad: 'USUARIO',
      entidadId: nuevoAdmin.id,
      detalle: { nuevoAdminCorreo: emailNormalized, nombre: nuevoAdmin.nombre }
    });

    return res.status(201).json({
      mensaje: 'Administrador de equipos creado exitosamente',
      admin: nuevoAdmin
    });
  } catch (err) {
    console.error('[Admin Create]', err);
    return res.status(500).json({ error: 'Error interno al crear administrador.' });
  }
});

/**
 * GET /api/admin/admins
 * Lista todos los administradores registrados (Solo ADMIN_EQUIPOS).
 */
router.get('/admin/admins', verifyToken, requireRole('ADMIN_EQUIPOS'), async (_req, res) => {
  try {
    const pool = getPool();
    const [rows] = await pool.query(
      `SELECT u.id, u.nombre, u.correo, u.rol, u.password_temporal AS passwordTemporal,
              u.creado_por AS creadoPor, c.nombre AS creadoPorNombre, u.created_at AS createdAt
       FROM usuarios u
       LEFT JOIN usuarios c ON u.creado_por = c.id
       WHERE u.rol = 'ADMIN_EQUIPOS'
       ORDER BY u.id ASC`
    );
    return res.json({ admins: rows });
  } catch (err) {
    console.error('[Admin List]', err);
    return res.status(500).json({ error: 'Error al consultar administradores.' });
  }
});

/**
 * GET /api/admin/auditoria
 * Consulta los registros de auditoría del sistema (Solo ADMIN_EQUIPOS).
 */
router.get('/admin/auditoria', verifyToken, requireRole('ADMIN_EQUIPOS'), async (req, res) => {
  try {
    const limit = Math.min(Number(req.query.limit || 100), 500);
    const pool = getPool();
    const [rows] = await pool.query(
      `SELECT id, usuario_id AS usuarioId, usuario_nombre AS usuarioNombre, usuario_rol AS usuarioRol,
              accion, entidad, entidad_id AS entidadId, detalle, created_at AS createdAt
       FROM auditoria_logs
       ORDER BY id DESC
       LIMIT ?`,
      [limit]
    );
    return res.json({ logs: rows });
  } catch (err) {
    console.error('[Auditoria List]', err);
    return res.status(500).json({ error: 'Error al consultar el historial de auditoría.' });
  }
});

module.exports = router;
