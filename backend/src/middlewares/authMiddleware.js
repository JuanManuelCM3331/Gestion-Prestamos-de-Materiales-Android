const jwt = require('jsonwebtoken');
const { getPool } = require('../config/db');

const JWT_SECRET = process.env.JWT_SECRET || 'ctma_sena_super_secret_jwt_key_2026';

/**
 * Verifica el token JWT y consulta el usuario en MySQL para garantizar
 * que el rol y el estado del usuario sean válidos en cada petición.
 */
async function verifyToken(req, res, next) {
  try {
    const authHeader = req.headers.authorization || '';
    if (!authHeader.startsWith('Bearer ')) {
      return res.status(401).json({ error: 'No autenticado. Token Bearer requerido.' });
    }

    const token = authHeader.slice(7).trim();
    if (!token) {
      return res.status(401).json({ error: 'Token vacío.' });
    }

    const decoded = jwt.verify(token, JWT_SECRET);
    const pool = getPool();
    const [rows] = await pool.query(
      'SELECT id, nombre, correo, rol, password_temporal, created_at FROM usuarios WHERE id = ? LIMIT 1',
      [decoded.id]
    );

    if (rows.length === 0) {
      return res.status(401).json({ error: 'El usuario asociado al token ya no existe.' });
    }

    req.user = rows[0];
    next();
  } catch (err) {
    return res.status(401).json({ error: 'Token inválido o expirado.' });
  }
}

/**
 * Middleware de autorización por rol (RBAC estricto en el backend).
 */
function requireRole(...allowedRoles) {
  return (req, res, next) => {
    if (!req.user) {
      return res.status(401).json({ error: 'No autenticado.' });
    }
    if (!allowedRoles.includes(req.user.rol)) {
      return res.status(403).json({
        error: `Acceso denegado. Se requiere rol: ${allowedRoles.join(' o ')}. Tu rol actual es: ${req.user.rol}.`
      });
    }
    next();
  };
}

module.exports = {
  verifyToken,
  requireRole,
  JWT_SECRET
};
