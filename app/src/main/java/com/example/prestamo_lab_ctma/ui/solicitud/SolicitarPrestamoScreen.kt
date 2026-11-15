package com.example.prestamo_lab_ctma.ui.solicitud

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.example.prestamo_lab_ctma.model.Equipo
import com.example.prestamo_lab_ctma.ui.viewmodel.FormularioSolicitudState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolicitarPrestamoScreen(
    equipo: Equipo?,
    formState: FormularioSolicitudState,
    guardando: Boolean,
    onAmbienteChange: (String) -> Unit,
    onPropositoChange: (String) -> Unit,
    onDuracionChange: (String) -> Unit,
    onCapturePhoto: () -> Unit,
    onPhotoSelected: (String) -> Unit,
    onGuardar: () -> Unit,
    onBack: () -> Unit,
    onCantidadChange: (String) -> Unit = {},
    onFechaDevolucionChange: (String) -> Unit = {},
    onNombreSolicitanteChange: (String) -> Unit = {},
    onDocumentoSolicitanteChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onCapturePhoto()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val cachedPath = copyUriToStorage(context, uri)
            if (cachedPath != null) {
                onPhotoSelected(cachedPath)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Formulario de Préstamo Inmediato") },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (equipo == null) {
                Text("Error: Equipo no encontrado.")
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Equipo: ${equipo.nombre}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Existencias disponibles ahora mismo: ${equipo.stockDisponible} de ${equipo.stockTotal} unidades",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Al enviar se activa inmediatamente el préstamo, se descuenta el stock, se abre el chat y se genera el Certificado PNG.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                OutlinedTextField(
                    value = formState.cantidad,
                    onValueChange = onCantidadChange,
                    label = { Text("Cantidad de unidades a solicitar (máx. ${equipo.stockDisponible}) *") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = formState.errorCantidad != null,
                    supportingText = { formState.errorCantidad?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                OutlinedTextField(
                    value = formState.fechaDevolucionEstimada,
                    onValueChange = onFechaDevolucionChange,
                    label = { Text("Fecha y hora de devolución estimada (YYYY-MM-DD HH:mm) *") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = formState.errorFecha != null,
                    supportingText = { formState.errorFecha?.let { Text(it) } },
                    singleLine = true
                )

                OutlinedTextField(
                    value = formState.nombreSolicitante,
                    onValueChange = onNombreSolicitanteChange,
                    label = { Text("Nombre completo del solicitante *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = formState.documentoSolicitante,
                    onValueChange = onDocumentoSolicitanteChange,
                    label = { Text("Documento / Ficha del solicitante") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = formState.ambienteDestino,
                    onValueChange = onAmbienteChange,
                    label = { Text("Ambiente o laboratorio destino *") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = formState.errorAmbiente != null,
                    supportingText = { formState.errorAmbiente?.let { Text(it) } },
                    singleLine = true
                )

                OutlinedTextField(
                    value = formState.proposito,
                    onValueChange = onPropositoChange,
                    label = { Text("Motivo / Observaciones (10 a 180 caracteres) *") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = formState.errorProposito != null,
                    supportingText = { formState.errorProposito?.let { Text(it) } },
                    minLines = 3
                )

                Text("Evidencia Fotográfica (Opcional):", style = MaterialTheme.typography.titleSmall)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val permissionCheckResult =
                                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                            if (permissionCheckResult == PackageManager.PERMISSION_GRANTED) {
                                onCapturePhoto()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("📷 Tomar Foto")
                    }

                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🖼️ Galería")
                    }
                }

                if (!formState.photoPath.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(File(formState.photoPath)),
                                contentDescription = "Vista previa de evidencia",
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Evidencia Seleccionada ✓",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Foto adjuntada correctamente",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { onPhotoSelected("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Eliminar evidencia")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onGuardar,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = formState.puedeGuardar && !guardando && equipo.stockDisponible > 0 && !equipo.eliminado
                ) {
                    if (guardando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Confirmar Préstamo y Generar Certificado")
                    }
                }
            }
        }
    }
}

private fun copyUriToStorage(context: Context, uri: Uri): String? {
    return try {
        if (uri.scheme == "file" && uri.path != null) {
            return uri.path
        }
        val dir = File(context.filesDir, "evidencias").apply { mkdirs() }
        val file = File(dir, "evidencia_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { out -> input.copyTo(out) }
        }
        file.absolutePath
    } catch (_: Exception) {
        null
    }
}
