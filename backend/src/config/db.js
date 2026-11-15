const mysql = require('mysql2/promise');
const fs = require('fs');
const path = require('path');
const bcrypt = require('bcryptjs');
const { SqliteFallbackPool } = require('./sqliteFallbackPool');
require('dotenv').config();

const dbConfig = {
  host: process.env.DB_HOST || '127.0.0.1',
  port: Number(process.env.DB_PORT || 3306),
  user: process.env.DB_USER || 'root',
  password: process.env.DB_PASSWORD || '',
  waitForConnections: true,
  connectionLimit: 20,
  queueLimit: 0,
  multipleStatements: true,
  connectTimeout: 3000
};

const DB_NAME = process.env.DB_NAME || 'prestamo_lab_ctma';

let pool = null;
let activeEngine = 'mysql';

function getPool() {
  return pool;
}

function getActiveEngine() {
  return activeEngine;
}

async function initDatabase() {
  if (!pool) {
    try {
      // 1. Intentar conectar al servidor MySQL configurado en .env
      const bootstrapConn = await mysql.createConnection(dbConfig);
      try {
        const schemaPath = path.join(__dirname, '../../sql/schema.sql');
        const schemaSql = fs.readFileSync(schemaPath, 'utf8');
        await bootstrapConn.query(schemaSql);
      } finally {
        await bootstrapConn.end();
      }

      pool = mysql.createPool({
        ...dbConfig,
        database: DB_NAME
      });
      // Probar una consulta simple para verificar estabilidad de InnoDB
      await pool.query('SELECT 1');
      activeEngine = 'mysql';
      console.log(`[DB] Conectado exitosamente a MySQL (${dbConfig.host}:${dbConfig.port}/${DB_NAME}).`);
    } catch (mysqlErr) {
      console.warn(
        `[DB] Servidor MySQL en ${dbConfig.host}:${dbConfig.port} no disponible (${mysqlErr.code || mysqlErr.message}). ` +
          'Activando motor relacional SQL local (backend/data/prestamo_lab_ctma.sqlite) compatible con MySQL.'
      );
      const sqlitePath = path.join(__dirname, '../../data', `${DB_NAME}.sqlite`);
      pool = new SqliteFallbackPool(sqlitePath);
      activeEngine = 'sqlite-compat';
    }
  }

  const dbPool = getPool();

  // 2. Seed del primer ADMIN_EQUIPOS si no existe ningún administrador
  const [admins] = await dbPool.query(
    "SELECT id FROM usuarios WHERE rol = 'ADMIN_EQUIPOS' LIMIT 1"
  );

  let seedAdminId = admins.length > 0 ? admins[0].id : null;

  if (!seedAdminId) {
    const adminName = process.env.ADMIN_SEED_NAME || 'Administrador Principal CTMA';
    const adminEmail = (process.env.ADMIN_SEED_EMAIL || 'admin@ctma.sena.edu.co').toLowerCase();
    const adminPass = process.env.ADMIN_SEED_PASSWORD || 'Admin123*';
    const hash = await bcrypt.hash(adminPass, 10);

    const [res] = await dbPool.query(
      `INSERT INTO usuarios (nombre, correo, password_hash, rol, password_temporal)
       VALUES (?, ?, ?, 'ADMIN_EQUIPOS', 0)`,
      [adminName, adminEmail, hash]
    );
    seedAdminId = res.insertId;

    await dbPool.query(
      `INSERT INTO auditoria_logs (usuario_id, usuario_nombre, usuario_rol, accion, entidad, entidad_id, detalle)
       VALUES (?, ?, 'ADMIN_EQUIPOS', 'SEED_ADMIN_INICIAL', 'USUARIO', ?, ?)`,
      [seedAdminId, adminName, seedAdminId, JSON.stringify({ correo: adminEmail })]
    );
    console.log(`[Seed] Primer ADMIN_EQUIPOS creado: ${adminEmail}`);
  }

  // 2b. Seed de un USUARIO de prueba si aún no existe ese correo
  const testUserEmail = (process.env.USER_SEED_EMAIL || 'usuario@ctma.sena.edu.co').toLowerCase();
  const [existingTestUsers] = await dbPool.query(
    'SELECT id FROM usuarios WHERE correo = ? LIMIT 1',
    [testUserEmail]
  );

  if (existingTestUsers.length === 0) {
    const testUserName = process.env.USER_SEED_NAME || 'Aprendiz Prueba CTMA';
    const testUserPass = process.env.USER_SEED_PASSWORD || 'Usuario123*';
    const testUserHash = await bcrypt.hash(testUserPass, 10);

    const [uRes] = await dbPool.query(
      `INSERT INTO usuarios (nombre, correo, password_hash, rol, password_temporal)
       VALUES (?, ?, ?, 'USUARIO', 0)`,
      [testUserName, testUserEmail, testUserHash]
    );

    await dbPool.query(
      `INSERT INTO auditoria_logs (usuario_id, usuario_nombre, usuario_rol, accion, entidad, entidad_id, detalle)
       VALUES (?, ?, 'USUARIO', 'SEED_USUARIO_PRUEBA', 'USUARIO', ?, ?)`,
      [uRes.insertId, testUserName, uRes.insertId, JSON.stringify({ correo: testUserEmail })]
    );
    console.log(`[Seed] Usuario de prueba creado: ${testUserEmail}`);
  }

  // 3. Seed de los 8 equipos iniciales del repositorio si la tabla está vacía
  const [countRows] = await dbPool.query('SELECT COUNT(*) AS total FROM equipos');
  if (Number(countRows[0].total) === 0) {
    const equiposIniciales = [
      ['Multímetro digital', 'ELECTRONICA', 'Multímetro digital de precisión True RMS con puntas de prueba para laboratorio de electrónica.', null, 5, 5],
      ['Kit Arduino', 'ELECTRONICA', 'Kit Arduino UNO R3 con sensores, jumpers, resistencias y cable USB para prototipado.', null, 8, 8],
      ['Cámara digital', 'MULTIMEDIA', 'Cámara réflex digital Full HD con lente 18-55mm, batería y tarjeta SD de 64GB.', null, 3, 3],
      ['Tablet educativa', 'INFORMATICA', 'Tablet de 10 pulgadas para diseño, lectura de planos y control de aplicaciones móviles.', null, 6, 6],
      ['Taladro inalámbrico', 'HERRAMIENTAS', 'Taladro percutor inalámbrico de 18V con dos baterías de litio y maletín de brocas.', null, 4, 4],
      ['Fuente de alimentación', 'ELECTRONICA', 'Fuente de poder DC regulable de laboratorio (0-30V, 0-5A) con protección contra cortocircuito.', null, 5, 5],
      ['Protoboard', 'ELECTRONICA', 'Placa de pruebas de 830 puntos sin soldadura con rieles de alimentación dobles.', null, 15, 15],
      ['Cámara web', 'MULTIMEDIA', 'Cámara web 1080p con micrófono estéreo integrado y soporte universal para monitor.', null, 6, 6]
    ];

    for (const eq of equiposIniciales) {
      await dbPool.query(
        `INSERT INTO equipos (nombre, categoria, descripcion, imagen_url, stock_total, stock_disponible, eliminado)
         VALUES (?, ?, ?, ?, ?, ?, 0)`,
        eq
      );
    }
    console.log('[Seed] 8 equipos iniciales insertados en la base de datos.');
  }
}

async function closePool() {
  if (pool) {
    await pool.end();
    pool = null;
  }
}

module.exports = {
  getPool,
  getActiveEngine,
  initDatabase,
  closePool
};
