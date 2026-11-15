require('dotenv').config();
const { initDatabase, getPool } = require('./db');

(async () => {
  try {
    await initDatabase();
    console.log('[Seed] Base de datos inicializada y datos semilla verificados correctamente.');
  } catch (err) {
    console.error('[Seed] Error al ejecutar seed:', err);
    process.exitCode = 1;
  } finally {
    const pool = getPool();
    if (pool) await pool.end();
  }
})();
