package com.example.prestamo_lab_ctma.ui.solicitud

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.prestamo_lab_ctma.model.Equipo
import com.example.prestamo_lab_ctma.model.SolicitudPrestamo
import com.example.prestamo_lab_ctma.ui.util.CertificadoGenerator
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolicitudDetalleScreen(
    solicitud: SolicitudPrestamo?,
    equipo: Equipo?,
    onCancelar: (Int) -> Unit,
    onBack: () -> Unit,
    esAdmin: Boolean = false,
    onMarcarEntregado: (Int) -> Unit = {},
    onAbrirChat: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var rutaCertificadoExportado by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle y Certificado de Préstamo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (solicitud == null) {
                Text("Error: Solicitud no encontrada.")
            } else {
                val nombreEquipo = solicitud.equipoNombre.ifBlank { equipo?.nombre ?: "Equipo #${solicitud.equipoId}" }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Préstamo #${solicitud.id} • ${solicitud.codigoCertificado}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Equipo: $nombreEquipo (${solicitud.cantidad} unidad/es)",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "Estado actual:", style = MaterialTheme.typography.bodyMedium)
                            AssistChip(
                                onClick = {},
                                label = { Text(solicitud.estado.etiquetaEspanol()) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = when {
                                        solicitud.estado.esActivo() -> MaterialTheme.colorScheme.primaryContainer
                                        solicitud.estado.esCancelado() -> MaterialTheme.colorScheme.errorContainer
                                        else -> MaterialTheme.colorScheme.secondaryContainer
                                    }
                                )
                            )
                        }
                    }
                }

                // Acciones rápidas: Chat y Exportación de Certificado PNG
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onAbrirChat(solicitud.id) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        val textoChat = if (solicitud.unreadCount > 0) {
                            "Chat (${solicitud.unreadCount})"
                        } else if (solicitud.chatSoloLectura) {
                            "Ver Chat (Lectura)"
                        } else {
                            "Abrir Chat"
                        }
                        Text(textoChat)
                    }

                    OutlinedButton(
                        onClick = {
                            val res = CertificadoGenerator.exportarCertificadoPng(context, solicitud, equipo)
                            res.onSuccess { path ->
                                rutaCertificadoExportado = path
                                Toast.makeText(
                                    context,
                                    "Certificado PNG guardado en $path",
                                    Toast.LENGTH_LONG
                                ).show()
                            }.onFailure { err ->
                                Toast.makeText(
                                    context,
                                    "Error al exportar PNG: ${err.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exportar PNG")
                    }
                }

                rutaCertificadoExportado?.let { ruta ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✅ Certificado exportado como imagen PNG:\n$ruta",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Información Detallada del Préstamo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (solicitud.nombreSolicitante.isNotBlank()) {
                    Text(
                        text = "• Solicitante: ${solicitud.nombreSolicitante}" +
                            (if (solicitud.documentoSolicitante.isNotBlank()) " — Doc: ${solicitud.documentoSolicitante}" else "")
                    )
                }
                Text(text = "• Cantidad prestada: ${solicitud.cantidad} unidad(es)")
                Text(text = "• Ambiente / Destino: ${solicitud.ambienteDestino}")
                Text(text = "• Motivo / Observaciones: ${solicitud.proposito}")
                if (solicitud.fechaPrestamo.isNotBlank()) {
                    Text(text = "• Fecha de registro: ${solicitud.fechaPrestamo.take(19).replace("T", " ")}")
                }
                if (solicitud.fechaDevolucionEstimada.isNotBlank()) {
                    Text(text = "• Fecha estimada de devolución: ${solicitud.fechaDevolucionEstimada.take(19).replace("T", " ")}")
                }
                if (!solicitud.fechaCierre.isNullOrBlank()) {
                    Text(text = "• Fecha de cierre: ${solicitud.fechaCierre.take(19).replace("T", " ")}")
                }

                // Vista previa visual del Certificado PNG generado
                HorizontalDivider()
                Text(
                    text = "Vista Previa del Certificado Oficial (PNG):",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val certBitmap = remember(solicitud, equipo) {
                    CertificadoGenerator.generarBitmapCertificado(solicitud, equipo)
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Image(
                        bitmap = certBitmap.asImageBitmap(),
                        contentDescription = "Certificado de préstamo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                if (!solicitud.photoPath.isNullOrBlank()) {
                    HorizontalDivider()
                    Text(text = "Evidencia Fotográfica Adjunta:", style = MaterialTheme.typography.titleMedium)
                    val imgModel = if (solicitud.photoPath.startsWith("http")) solicitud.photoPath else File(solicitud.photoPath)
                    Image(
                        painter = rememberAsyncImagePainter(imgModel),
                        contentDescription = "Foto de evidencia del equipo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Acciones de cambio de estado (ACTIVO -> ENTREGADO o CANCELADO)
                if (solicitud.estado.esActivo()) {
                    if (esAdmin) {
                        Button(
                            onClick = { onMarcarEntregado(solicitud.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Marcar Préstamo como ENTREGADO (Devolver al Stock)")
                        }
                    }

                    Button(
                        onClick = { onCancelar(solicitud.id) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Cancelar Préstamo (Devolver Unidades a Existencias)")
                    }
                } else {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Préstamo finalizado en estado ${solicitud.estado.etiquetaEspanol()}")
                    }
                }
            }
        }
    }
}
