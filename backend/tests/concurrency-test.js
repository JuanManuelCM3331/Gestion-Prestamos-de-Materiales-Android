const assert = require('assert');
const { startServer, server } = require('../src/server');
const { getPool } = require('../src/config/db');

const TEST_PORT = 3099;
const BASE_URL = `http://127.0.0.1:${TEST_PORT}/api`;

async function request(path, { method = 'GET', token = null, body = null, headers = {} } = {}) {
  const opts = {
    method,
    headers: { ...headers }
  };
  if (token) {
    opts.headers.Authorization = `Bearer ${token}`;
  }
  if (body !== null && !(body instanceof FormData)) {
    opts.headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(body);
  } else if (body instanceof FormData) {
    opts.body = body;
  }

  const res = await fetch(`${BASE_URL}${path}`, opts);
  const data = await res.json().catch(() => ({}));
  return { status: res.status, data };
}

async function runConcurrencyTest() {
  console.log('=== PRUEBA DE CONCURRENCIA: DOS USUARIOS PIDIENDO LA ÚLTIMA UNIDAD ===');
  await startServer(TEST_PORT);

  try {
    // 1. Login con el admin semilla
    const adminLogin = await request('/auth/login', {
      method: 'POST',
      body: {
        correo: process.env.ADMIN_SEED_EMAIL || 'admin@ctma.sena.edu.co',
        password: process.env.ADMIN_SEED_PASSWORD || 'Admin123*'
      }
    });
    assert.strictEqual(adminLogin.status, 200, 'Login admin semilla debe responder 200');
    const adminToken = adminLogin.data.token;

    // 2. Crear un equipo con stock_total = 1 (la última unidad disponible)
    const crearEq = await request('/equipos', {
      method: 'POST',
      token: adminToken,
      body: {
        nombre: `Osciloscopio Concurrencia ${Date.now()}`,
        categoria: 'ELECTRONICA',
        stockTotal: 1,
        descripcion: 'Equipo con una única unidad para validar concurrencia atómica.'
      }
    });
    assert.strictEqual(crearEq.status, 201, 'Admin debe crear equipo con 1 unidad');
    const equipoId = crearEq.data.equipo.id;

    // 3. Registrar dos usuarios distintos
    const ts = Date.now();
    const u1Reg = await request('/auth/register', {
      method: 'POST',
      body: {
        nombre: 'Aprendiz Uno',
        correo: `aprendiz1_${ts}@ctma.edu.co`,
        password: 'Password123*'
      }
    });
    const u2Reg = await request('/auth/register', {
      method: 'POST',
      body: {
        nombre: 'Aprendiz Dos',
        correo: `aprendiz2_${ts}@ctma.edu.co`,
        password: 'Password123*'
      }
    });
    assert.strictEqual(u1Reg.status, 201);
    assert.strictEqual(u2Reg.status, 201);

    const tokenU1 = u1Reg.data.token;
    const tokenU2 = u2Reg.data.token;

    // 4. Disparar ambas peticiones de préstamo AL MISMO TIEMPO con Promise.all
    const payloadPrestamo = {
      equipoId,
      cantidad: 1,
      ambienteDestino: 'Ambiente 301 Electrónica',
      motivo: 'Práctica de medición de señales simultánea en laboratorio',
      fechaDevolucionEstimada: new Date(Date.now() + 4 * 3600 * 1000).toISOString()
    };

    const [res1, res2] = await Promise.all([
      request('/prestamos', { method: 'POST', token: tokenU1, body: payloadPrestamo }),
      request('/prestamos', { method: 'POST', token: tokenU2, body: payloadPrestamo })
    ]);

    const statuses = [res1.status, res2.status].sort();
    console.log('Resultados simultáneos HTTP:', statuses);
    assert.deepStrictEqual(
      statuses,
      [201, 409],
      `Exactamente uno debe lograrlo (201) y el otro fallar con conflicto (409). Recibido: ${statuses}`
    );

    const ganador = res1.status === 201 ? res1 : res2;
    const perdedor = res1.status === 409 ? res1 : res2;

    console.log('Mensaje ganador:', ganador.data.mensaje, '-> Certificado:', ganador.data.prestamo.codigoCertificado);
    console.log('Mensaje perdedor:', perdedor.data.error);

    // 5. Verificar en BD que el stock_disponible quedó en 0 (nunca negativo)
    const eqCheck = await request(`/equipos/${equipoId}`, { token: adminToken });
    assert.strictEqual(eqCheck.data.equipo.stockDisponible, 0, 'El stock disponible debe ser exactamente 0');

    // Limpieza: cancelar el préstamo ganador y borrar equipo de prueba
    await request(`/prestamos/${ganador.data.prestamo.id}/cancelar`, {
      method: 'PATCH',
      token: adminToken
    });
    await request(`/equipos/${equipoId}/permanente`, {
      method: 'DELETE',
      token: adminToken
    });

    console.log('✅ PRUEBA DE CONCURRENCIA SUPERADA CON ÉXITO.');
  } finally {
    server.close();
    const pool = getPool();
    if (pool) await pool.end();
  }
}

if (require.main === module) {
  runConcurrencyTest().catch((err) => {
    console.error('❌ Falló la prueba de concurrencia:', err);
    process.exit(1);
  });
}

module.exports = { runConcurrencyTest };
