/**
 * Servicio de Auditoría Básica
 * Registra quién creó, editó, eliminó, restauró, canceló o devolvió cada recurso.
 */
async function registrarAuditoria(executor, { usuario, accion, entidad, entidadId = null, detalle = null }) {
  try {
    const usuarioId = usuario?.id || null;
    const usuarioNombre = usuario?.nombre || 'Sistema';
    const usuarioRol = usuario?.rol || 'SISTEMA';
    const detalleStr = typeof detalle === 'string' ? detalle : detalle ? JSON.stringify(detalle) : null;

    await executor.query(
      `INSERT INTO auditoria_logs (usuario_id, usuario_nombre, usuario_rol, accion, entidad, entidad_id, detalle)
       VALUES (?, ?, ?, ?, ?, ?, ?)`,
      [usuarioId, usuarioNombre, usuarioRol, accion, entidad, entidadId, detalleStr]
    );
  } catch (err) {
    console.error('[Auditoría] Error al guardar log:', err.message);
  }
}

module.exports = {
  registrarAuditoria
};
