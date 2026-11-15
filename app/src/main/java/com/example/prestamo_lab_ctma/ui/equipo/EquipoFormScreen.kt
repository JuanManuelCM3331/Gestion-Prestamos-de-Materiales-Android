package com.example.prestamo_lab_ctma.ui.equipo

import android.content.Context
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.prestamo_lab_ctma.model.CategoriaEquipo
import com.example.prestamo_lab_ctma.model.Equipo
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipoFormScreen(
    equipoExistente: Equipo?,
    guardando: Boolean,
    onGuardar: (
        idEdicion: Int?,
        nombre: String,
        categoria: CategoriaEquipo,
        stockTotal: String,
        descripcion: String,
        imagenPath: String?
    ) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var nombre by remember(equipoExistente) { mutableStateOf(equipoExistente?.nombre.orEmpty()) }
    var categoria by remember(equipoExistente) {
        mutableStateOf(equipoExistente?.categoria ?: CategoriaEquipo.ELECTRONICA)
    }
    var stockTotal by remember(equipoExistente) {
        mutableStateOf((equipoExistente?.stockTotal ?: 5).toString())
    }
    var descripcion by remember(equipoExistente) {
        mutableStateOf(equipoExistente?.descripcion.orEmpty())
    }
    var imagenPath by remember(equipoExistente) {
        mutableStateOf(equipoExistente?.imagenUrl)
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            copiarImagenEquipo(context, uri)?.let { imagenPath = it }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (equipoExistente == null) "Crear Equipo en Catálogo" else "Editar Equipo #${equipoExistente.id}")
                },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre del equipo *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text("Categoría:", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CategoriaEquipo.values().take(3).forEach { cat ->
                    FilterChip(
                        selected = categoria == cat,
                        onClick = { categoria = cat },
                        label = { Text(cat.name) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CategoriaEquipo.values().drop(3).forEach { cat ->
                    FilterChip(
                        selected = categoria == cat,
                        onClick = { categoria = cat },
                        label = { Text(cat.name) }
                    )
                }
            }

            OutlinedTextField(
                value = stockTotal,
                onValueChange = { stockTotal = it },
                label = { Text("Cantidad total de existencias (stock) *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    if (equipoExistente != null && equipoExistente.unidadesEnPrestamoActivo > 0) {
                        Text("Actualmente hay ${equipoExistente.unidadesEnPrestamoActivo} unidad(es) en préstamo ACTIVO.")
                    }
                }
            )

            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                label = { Text("Descripción / Aclaración técnica *") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Imagen del producto:", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Text("🖼️ Seleccionar Imagen")
                }
                if (!imagenPath.isNullOrBlank()) {
                    TextButton(onClick = { imagenPath = null }) {
                        Text("Quitar imagen")
                    }
                }
            }

            if (!imagenPath.isNullOrBlank()) {
                val model = if (imagenPath!!.startsWith("http")) imagenPath else File(imagenPath!!)
                Image(
                    painter = rememberAsyncImagePainter(model),
                    contentDescription = "Imagen del equipo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    onGuardar(
                        equipoExistente?.id,
                        nombre,
                        categoria,
                        stockTotal,
                        descripcion,
                        imagenPath
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !guardando && nombre.trim().length >= 3 && descripcion.trim().length >= 5
            ) {
                if (guardando) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (equipoExistente == null) "Crear Equipo" else "Guardar Cambios")
                }
            }
        }
    }
}

private fun copiarImagenEquipo(context: Context, uri: Uri): String? {
    return try {
        val dir = File(context.filesDir, "equipos_media").apply { mkdirs() }
        val target = File(dir, "equipo_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { out -> input.copyTo(out) }
        }
        target.absolutePath
    } catch (_: Exception) {
        null
    }
}
