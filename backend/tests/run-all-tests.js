const assert = require('assert');
const { startServer, server } = require('../src/server');
const { getPool } = require('../src/config/db');

const TEST_PORT = 3098;
const BASE_URL = `http://127.0.0.1:${TEST_PORT}/api`;

async function request(path, { method = 'GET', token = null, body = null } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;

  const opts = { method, headers };
  if (body !== null && !(body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(body);
  } else if (body instanceof FormData) {
    opts.body = body;
  }

  const res = await fetch(`${BASE_URL}${path}`, opts);
  const data = await res.json().catch(() => ({}));
  return { status: res.status, data };
}

async function runAllTests() {
  console.log('=== EJECUTANDO SUITE E2E DEL BACKEND PRÉSTAMOLAB CTMA ===');
  await startServer(TEST_PORT);

  try {
    const ts = Date.now();

    // 1. Fase 1: Roles y Autenticación
    console.log('1) Probando Fase 1: Roles y Autenticación...');
    const badReg = await request('/auth/register', {
      method: 'POST',
      body: {
        nombre: 'Hacker Admin',
        correo: `hacker_${ts}@ctma.edu.co`,
        password: 'Password123*',
        rol: 'ADMIN_EQUIPOS'
      }
    });
    assert.strictEqual(badReg.status, 403, 'No debe permitir registrarse públicamente como ADMIN_EQUIPOS');

    const userReg = await request('/auth/register', {
      method: 'POST',
      body: {
        nombre: 'Aprendiz Prueba',
        correo: `usuario_${ts}@ctma.edu.co`,
        password: 'Password123*'
      }
    });
    assert.strictEqual(userReg.status, 201, 'Debe registrar USUARIO públicamente');
    const userToken = userReg.data.token;

    const adminLogin = await request('/auth/login', {
      method: 'POST',
      body: {
        correo: process.env.ADMIN_SEED_EMAIL || 'admin@ctma.sena.edu.co',
        password: process.env.ADMIN_SEED_PASSWORD || 'Admin123*'
      }
    });
    assert.strictEqual(adminLogin.status, 200, 'El admin inicial (seed) debe iniciar sesión');
    const adminToken = adminLogin.data.token;

    const testUserLogin = await request('/auth/login', {
      method: 'POST',
      body: {
        correo: process.env.USER_SEED_EMAIL || 'usuario@ctma.sena.edu.co',
        password: process.env.USER_SEED_PASSWORD || 'Usuario123*'
      }
    });
    assert.strictEqual(testUserLogin.status, 200, 'El usuario de prueba (seed) debe iniciar sesión');
    assert.strictEqual(testUserLogin.data.usuario.rol, 'USUARIO');

    const userTryCreateAdmin = await request('/admin/admins', {
      method: 'POST',
      token: userToken,
      body: {
        nombre: 'Otro Admin',
        correo: `admin2_${ts}@ctma.edu.co`,
        passwordTemporal: 'Temp1234*'
      }
    });
    assert.strictEqual(userTryCreateAdmin.status, 403, 'Un USUARIO no puede acceder a rutas de ADMIN_EQUIPOS');

    const adminCreateAdmin = await request('/admin/admins', {
      method: 'POST',
      token: adminToken,
      body: {
        nombre: 'Segundo Administrador',
        correo: `admin2_${ts}@ctma.edu.co`,
        passwordTemporal: 'Temp1234*'
      }
    });
    assert.strictEqual(adminCreateAdmin.status, 201, 'Un ADMIN_EQUIPOS puede crear otro ADMIN_EQUIPOS');

    // 2. Fase 2: Catálogo de Equipos (Crear, Editar, Eliminar Lógico, Restaurar)
    console.log('2) Probando Fase 2: Catálogo de Equipos...');
    const createEq = await request('/equipos', {
      method: 'POST',
      token: adminToken,
      body: {
        nombre: `Analizador Lógico ${ts}`,
        categoria: 'ELECTRONICA',
        stockTotal: 4,
        descripcion: 'Analizador lógico de 8 canales 24MHz con cables de prueba.'
      }
    });
    assert.strictEqual(createEq.status, 201);
    const equipoId = createEq.data.equipo.id;

    const editEq = await request(`/equipos/${equipoId}`, {
      method: 'PUT',
      token: adminToken,
      body: {
        nombre: `Analizador Lógico Pro ${ts}`,
        categoria: 'ELECTRONICA',
        stockTotal: 5,
        descripcion: 'Analizador lógico de 16 canales actualizado.'
      }
    });
    assert.strictEqual(editEq.status, 200);
    assert.strictEqual(editEq.data.equipo.stockTotal, 5);

    // Eliminación lógica y restauración
    const softDel = await request(`/equipos/${equipoId}`, { method: 'DELETE', token: adminToken });
    assert.strictEqual(softDel.status, 200);
    assert.strictEqual(softDel.data.equipo.eliminado, true);

    const listDeletedUser = await request('/equipos?eliminados=true', { token: userToken });
    assert.strictEqual(listDeletedUser.status, 403, 'Usuario no puede ver pestaña Eliminados');

    const listDeletedAdmin = await request('/equipos?eliminados=true', { token: adminToken });
    assert.strictEqual(listDeletedAdmin.status, 200);
    assert.ok(listDeletedAdmin.data.equipos.some((e) => e.id === equipoId));

    const restoreEq = await request(`/equipos/${equipoId}/restaurar`, { method: 'PATCH', token: adminToken });
    assert.strictEqual(restoreEq.status, 200);
    assert.strictEqual(restoreEq.data.equipo.eliminado, false);

    // 3. Fase 3: Préstamos, Stock y Regla de no eliminar equipo con préstamo activo
    console.log('3) Probando Fase 3: Préstamos, Descuento y Devolución de Stock...');
    const crearPrestamo = await request('/prestamos', {
      method: 'POST',
      token: userToken,
      body: {
        equipoId,
        cantidad: 2,
        nombreSolicitante: 'Aprendiz Prueba',
        documentoSolicitante: '1020304050',
        ambienteDestino: 'Laboratorio 204',
        motivo: 'Análisis de protocolo I2C y SPI en microcontroladores',
        fechaDevolucionEstimada: new Date(Date.now() + 5 * 3600 * 1000).toISOString()
      }
    });
    assert.strictEqual(crearPrestamo.status, 201);
    assert.strictEqual(crearPrestamo.data.prestamo.estado, 'ACTIVO');
    assert.strictEqual(crearPrestamo.data.stockDisponible, 3, 'El stock debe bajar de 5 a 3 al instante');
    const prestamoId = crearPrestamo.data.prestamo.id;

    // Intentar borrar permanentemente un equipo con préstamo ACTIVO -> debe rechazar (409)
    const permDelActive = await request(`/equipos/${equipoId}/permanente`, {
      method: 'DELETE',
      token: adminToken
    });
    assert.strictEqual(permDelActive.status, 409, 'No debe permitir eliminar permanentemente equipo con préstamo activo');

    // 4. Fase 4: Chat en tiempo real y adjuntos
    console.log('4) Probando Fase 4: Chat (Texto, Audio/Archivo, Permisos y Modo Lectura)...');
    const sendMsgUser = await request(`/prestamos/${prestamoId}/chat/mensajes`, {
      method: 'POST',
      token: userToken,
      body: { contenido: 'Hola administrador, ya recogí los 2 analizadores.' }
    });
    assert.strictEqual(sendMsgUser.status, 201);

    // Enviar nota de voz simulada vía FormData
    const formAudio = new FormData();
    const audioBlob = new Blob([Buffer.from('fake-audio-aac-stream')], { type: 'audio/mp4' });
    formAudio.append('archivo', audioBlob, 'nota_voz.m4a');
    formAudio.append('tipo', 'AUDIO');
    formAudio.append('duracionSeg', '6');

    const sendAudioAdmin = await request(`/prestamos/${prestamoId}/chat/mensajes`, {
      method: 'POST',
      token: adminToken,
      body: formAudio
    });
    assert.strictEqual(sendAudioAdmin.status, 201);
    assert.strictEqual(sendAudioAdmin.data.chatMensaje.tipo, 'AUDIO');

    // Cancelar préstamo -> stock sube a 5 y chat pasa a solo lectura
    const cancelPrestamo = await request(`/prestamos/${prestamoId}/cancelar`, {
      method: 'PATCH',
      token: userToken
    });
    assert.strictEqual(cancelPrestamo.status, 200);
    assert.strictEqual(cancelPrestamo.data.stock.stockDisponible, 5, 'Al cancelar, las 2 unidades vuelven al stock (5)');

    // Intentar enviar mensaje en préstamo cerrado -> 403
    const sendAfterClose = await request(`/prestamos/${prestamoId}/chat/mensajes`, {
      method: 'POST',
      token: userToken,
      body: { contenido: 'Mensaje tras cierre' }
    });
    assert.strictEqual(sendAfterClose.status, 403, 'El chat debe quedar en modo lectura tras cerrarse el préstamo');

    // Crear un segundo préstamo y que el ADMIN lo marque como ENTREGADO
    const p2 = await request('/prestamos', {
      method: 'POST',
      token: userToken,
      body: {
        equipoId,
        cantidad: 3,
        nombreSolicitante: 'Aprendiz Prueba',
        documentoSolicitante: '1020304050',
        ambienteDestino: 'Laboratorio 204',
        motivo: 'Segunda práctica de depuración de buses digitales',
        fechaDevolucionEstimada: new Date(Date.now() + 3 * 3600 * 1000).toISOString()
      }
    });
    assert.strictEqual(p2.status, 201);
    assert.strictEqual(p2.data.stockDisponible, 2);

    const entregarP2 = await request(`/prestamos/${p2.data.prestamo.id}/entregar`, {
      method: 'PATCH',
      token: adminToken
    });
    assert.strictEqual(entregarP2.status, 200);
    assert.strictEqual(entregarP2.data.prestamo.estado, 'ENTREGADO');
    assert.strictEqual(entregarP2.data.stock.stockDisponible, 5, 'Al entregar, el stock regresa a 5');

    // Ahora que no tiene préstamos activos, sí se puede eliminar permanentemente
    const permDelOk = await request(`/equipos/${equipoId}/permanente`, {
      method: 'DELETE',
      token: adminToken
    });
    assert.strictEqual(permDelOk.status, 200);

    console.log('✅ TODAS LAS PRUEBAS E2E DEL BACKEND PASARON EXITOSAMENTE.');
  } finally {
    server.close();
    const pool = getPool();
    if (pool) await pool.end();
  }
}

if (require.main === module) {
  runAllTests().catch((err) => {
    console.error('❌ Falló la suite de pruebas:', err);
    process.exit(1);
  });
}

module.exports = { runAllTests };
