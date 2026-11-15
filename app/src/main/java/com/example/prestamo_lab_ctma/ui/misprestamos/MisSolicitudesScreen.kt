package com.example.prestamo_lab_ctma.ui.misprestamos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.prestamo_lab_ctma.model.Equipo
import com.example.prestamo_lab_ctma.model.SolicitudPrestamo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MisSolicitudesScreen(
    solicitudes: List<SolicitudPrestamo>,
    equipos: List<Equipo>,
    onSolicitudClick: (Int) -> Unit,
    onBack: () -> Unit,
    esAdmin: Boolean = false,
    onAbrirChat: (Int) -> Unit = {},
    onMarcarEntregado: (Int) -> Unit = {},
    onCancelar: (Int) -> Unit = {}
) {
    // Pestañas: Activos (0), Cancelados (1), Entregados (2)
    var tabIndex by remember { mutableStateOf(0) }

    val activos = remember(solicitudes) { solicitudes.filter { it.estado.esActivo() } }
    val cancelados = remember(solicitudes) { solicitudes.filter { it.estado.esCancelado() } }
    val entregados = remember(solicitudes) { solicitudes.filter { it.estado.esEntregado() } }

    val listaActual = when (tabIndex) {
        0 -> activos
        1 -> cancelados
        else -> entregados
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (esAdmin) "Gestión Global de Préstamos (ADMIN)" else "Mis Préstamos")
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    text = { Text("Activos (${activos.size})") }
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text("Cancelados (${cancelados.size})") }
                )
                Tab(
                    selected = tabIndex == 2,
                    onClick = { tabIndex = 2 },
                    text = { Text("Entregados (${entregados.size})") }
                )
            }

            if (listaActual.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val nombreTab = when (tabIndex) {
                        0 -> "ACTIVOS"
                        1 -> "CANCELADOS"
                        else -> "ENTREGADOS"
                    }
                    Text("No hay préstamos en la pestaña $nombreTab.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(listaActual, key = { it.id }) { solicitud ->
                        val equipo = equipos.find { it.id == solicitud.equipoId }
                        val nombreEquipo = solicitud.equipoNombre.ifBlank {
                            equipo?.nombre ?: "Equipo #${solicitud.equipoId}"
                        }
                        SolicitudItem(
                            solicitud = solicitud,
                            equipoNombre = nombreEquipo,
                            esAdmin = esAdmin,
                            onClick = { onSolicitudClick(solicitud.id) },
                            onAbrirChat = { onAbrirChat(solicitud.id) },
                            onMarcarEntregado = { onMarcarEntregado(solicitud.id) },
                            onCancelar = { onCancelar(solicitud.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SolicitudItem(
    solicitud: SolicitudPrestamo,
    equipoNombre: String,
    esAdmin: Boolean = false,
    onClick: () -> Unit,
    onAbrirChat: () -> Unit = {},
    onMarcarEntregado: () -> Unit = {},
    onCancelar: () -> Unit = {}
) {
    val colorBadge = when {
        solicitud.estado.esActivo() -> Color(0xFF2E7D32)
        solicitud.estado.esEntregado() -> Color(0xFF1565C0)
        else -> Color(0xFFC62828)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Préstamo #${solicitud.id} • ${solicitud.codigoCertificado}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = colorBadge.copy(alpha = 0.14f),
                    contentColor = colorBadge,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = solicitud.estado.etiquetaEspanol(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Equipo: $equipoNombre (Cantidad: ${solicitud.cantidad} u.)",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (solicitud.nombreSolicitante.isNotBlank()) {
                Text(
                    text = "Solicitante: ${solicitud.nombreSolicitante}" +
                        (if (solicitud.documentoSolicitante.isNotBlank()) " (${solicitud.documentoSolicitante})" else ""),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = "Destino: ${solicitud.ambienteDestino}",
                style = MaterialTheme.typography.bodySmall
            )
            if (solicitud.fechaDevolucionEstimada.isNotBlank()) {
                Text(
                    text = "Devolución estimada: ${solicitud.fechaDevolucionEstimada.take(16).replace("T", " ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onAbrirChat,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    val labelChat = if (solicitud.unreadCount > 0) {
                        "Chat (${solicitud.unreadCount} nuevos)"
                    } else {
                        "Chat"
                    }
                    Text(labelChat)
                }

                if (esAdmin && solicitud.estado.esActivo()) {
                    Button(
                        onClick = onMarcarEntregado,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Entregado")
                    }
                } else if (!esAdmin && solicitud.estado.esActivo()) {
                    OutlinedButton(
                        onClick = onCancelar,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Cancelar")
                    }
                }
            }
        }
    }
}
