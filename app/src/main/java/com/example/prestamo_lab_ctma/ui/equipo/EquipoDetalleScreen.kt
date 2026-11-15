package com.example.prestamo_lab_ctma.ui.equipo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.prestamo_lab_ctma.model.Equipo
import com.example.prestamo_lab_ctma.model.EstadoEquipo
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipoDetalleScreen(
    equipo: Equipo?,
    onBack: () -> Unit,
    onSolicitar: (Int) -> Unit,
    esAdmin: Boolean = false,
    onEditar: (Int) -> Unit = {},
    onEliminarLogico: (Int) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle del Equipo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (esAdmin && equipo != null) {
                        IconButton(onClick = { onEditar(equipo.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar equipo")
                        }
                        if (!equipo.eliminado) {
                            IconButton(onClick = { onEliminarLogico(equipo.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Eliminar equipo",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (equipo == null) {
                Text(
                    text = "El equipo solicitado ya no existe o fue eliminado del catálogo.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = onBack) { Text("Volver al catálogo") }
            } else {
                if (equipo.eliminado) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ Este equipo acaba de ser retirado del catálogo activo y no admite nuevos préstamos.",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (!equipo.imagenUrl.isNullOrBlank()) {
                    val imgModel = if (equipo.imagenUrl.startsWith("http")) equipo.imagenUrl else File(equipo.imagenUrl)
                    Image(
                        painter = rememberAsyncImagePainter(imgModel),
                        contentDescription = equipo.nombre,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Text(
                    text = equipo.nombre,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(text = "Categoría: ${equipo.categoria}", style = MaterialTheme.typography.bodyLarge)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Control de Existencias en Tiempo Real",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "• Disponibles para préstamo inmediato: ${equipo.stockDisponible} unidad(es)",
                            color = if (equipo.tieneExistencias) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Text("• Existencias totales en inventario: ${equipo.stockTotal} unidad(es)")
                        Text("• Unidades actualmente en préstamo ACTIVO: ${equipo.unidadesEnPrestamoActivo}")
                    }
                }

                if (equipo.descripcion.isNotBlank()) {
                    Text(
                        text = "Descripción / Aclaración Técnica:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = equipo.descripcion,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val puedePedir = !equipo.eliminado &&
                    equipo.estado == EstadoEquipo.DISPONIBLE &&
                    equipo.stockDisponible > 0

                Button(
                    onClick = { onSolicitar(equipo.id) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = puedePedir
                ) {
                    Text(
                        if (puedePedir) {
                            "Solicitar préstamo (${equipo.stockDisponible} disponibles)"
                        } else {
                            "Sin existencias disponibles"
                        }
                    )
                }
            }
        }
    }
}
