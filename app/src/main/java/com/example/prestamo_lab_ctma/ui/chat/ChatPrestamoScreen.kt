package com.example.prestamo_lab_ctma.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.example.prestamo_lab_ctma.model.ChatMensaje
import com.example.prestamo_lab_ctma.model.RolUsuario
import com.example.prestamo_lab_ctma.model.SolicitudPrestamo
import com.example.prestamo_lab_ctma.model.TipoMensajeChat
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPrestamoScreen(
    prestamo: SolicitudPrestamo?,
    mensajes: List<ChatMensaje>,
    usuarioActualId: Int,
    onEnviarTexto: (String) -> Unit,
    onEnviarAdjunto: (
        tipo: TipoMensajeChat,
        descripcion: String,
        archivo: File,
        mimeType: String,
        duracionSeg: Int?
    ) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var texto by remember { mutableStateOf("") }
    var grabandoAudio by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var archivoAudioTemp by remember { mutableStateOf<File?>(null) }
    var inicioGrabacionMs by remember { mutableStateOf(0L) }

    val soloLectura = prestamo == null || prestamo.chatSoloLectura || !prestamo.estado.esActivo()

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                recorder?.stop()
                recorder?.release()
            }
        }
    }

    // Selector de imágenes
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            copiarUriACacheChat(context, uri, "imagen_chat.jpg")?.let { (file, mime) ->
                onEnviarAdjunto(TipoMensajeChat.IMAGEN, "📷 Imagen adjunta", file, mime, null)
            }
        }
    }

    // Selector de documentos / archivos (PDF, DOC, DOCX, TXT)
    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            copiarUriACacheChat(context, uri, "documento_adjunto.pdf")?.let { (file, mime) ->
                onEnviarAdjunto(
                    TipoMensajeChat.ARCHIVO,
                    "📎 Archivo: ${file.name}",
                    file,
                    mime,
                    null
                )
            }
        }
    }

    // Permiso de micrófono para grabar audio
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            iniciarGrabacionAudio(context) { rec, file ->
                recorder = rec
                archivoAudioTemp = file
                inicioGrabacionMs = System.currentTimeMillis()
                grabandoAudio = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Chat Préstamo #${prestamo?.id ?: ""}")
                        Text(
                            text = if (soloLectura) {
                                "🔒 Modo solo lectura (${prestamo?.estado?.etiquetaEspanol() ?: "Cerrado"})"
                            } else {
                                "En línea • ${prestamo?.equipoNombre ?: "Soporte con Administradores"}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (soloLectura) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
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
        ) {
            if (soloLectura) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🔒 Este préstamo ya fue cerrado (${prestamo?.estado?.etiquetaEspanol()}). El historial del chat se conserva en modo solo lectura.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (mensajes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Inicia la conversación con el administrador del laboratorio.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(mensajes, key = { it.id }) { msg ->
                        ChatBubbleItem(
                            mensaje = msg,
                            esMio = msg.remitenteId == usuarioActualId
                        )
                    }
                }
            }

            if (!soloLectura) {
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Barra de adjuntos multimedia (Imagen, Archivo, Grabadora de Audio)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = {
                                imagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            label = { Text("Imagen") },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )

                        AssistChip(
                            onClick = {
                                documentPicker.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "text/plain",
                                        "application/msword",
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                    )
                                )
                            },
                            label = { Text("Archivo") },
                            leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )

                        if (!grabandoAudio) {
                            AssistChip(
                                onClick = {
                                    val perm = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    )
                                    if (perm == PackageManager.PERMISSION_GRANTED) {
                                        iniciarGrabacionAudio(context) { rec, file ->
                                            recorder = rec
                                            archivoAudioTemp = file
                                            inicioGrabacionMs = System.currentTimeMillis()
                                            grabandoAudio = true
                                        }
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                label = { Text("Grabar Audio") },
                                leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        } else {
                            Button(
                                onClick = {
                                    runCatching {
                                        recorder?.stop()
                                        recorder?.release()
                                    }
                                    recorder = null
                                    grabandoAudio = false
                                    val durSeg = ((System.currentTimeMillis() - inicioGrabacionMs) / 1000L)
                                        .toInt()
                                        .coerceAtLeast(1)
                                    archivoAudioTemp?.takeIf { it.exists() }?.let { file ->
                                        onEnviarAdjunto(
                                            TipoMensajeChat.AUDIO,
                                            "🎤 Nota de voz (${durSeg}s)",
                                            file,
                                            "audio/mp4",
                                            durSeg
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Enviar Audio")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = texto,
                            onValueChange = { texto = it },
                            placeholder = { Text("Escribe un mensaje...") },
                            modifier = Modifier.weight(1f),
                            maxLines = 3
                        )
                        IconButton(
                            onClick = {
                                if (texto.isNotBlank()) {
                                    onEnviarTexto(texto)
                                    texto = ""
                                }
                            },
                            enabled = texto.isNotBlank()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Enviar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(
    mensaje: ChatMensaje,
    esMio: Boolean
) {
    var reproduciendoAudio by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (esMio) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (esMio) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                val etiquetaRol = if (mensaje.remitenteRol == RolUsuario.ADMIN_EQUIPOS) "ADMIN" else "USUARIO"
                Text(
                    text = "${mensaje.remitenteNombre} ($etiquetaRol)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                when (mensaje.tipo) {
                    TipoMensajeChat.IMAGEN -> {
                        if (!mensaje.archivoUrl.isNullOrBlank()) {
                            val model = if (mensaje.archivoUrl.startsWith("http")) {
                                mensaje.archivoUrl
                            } else {
                                File(mensaje.archivoUrl)
                            }
                            Image(
                                painter = rememberAsyncImagePainter(model),
                                contentDescription = "Imagen del chat",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Text(text = mensaje.contenido, style = MaterialTheme.typography.bodyMedium)
                    }

                    TipoMensajeChat.AUDIO -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    val url = mensaje.archivoUrl ?: return@FilledTonalIconButton
                                    reproducirAudioUrl(url) { playing ->
                                        reproduciendoAudio = playing
                                    }
                                }
                            ) {
                                Icon(
                                    if (reproduciendoAudio) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                                    contentDescription = "Reproducir audio"
                                )
                            }
                            Column {
                                Text(
                                    text = mensaje.contenido.ifBlank { "🎤 Nota de voz" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                mensaje.duracionSeg?.let {
                                    Text("Duración: ${it}s", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    TipoMensajeChat.ARCHIVO -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null)
                            Column {
                                Text(
                                    text = mensaje.archivoNombre ?: mensaje.contenido,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                mensaje.archivoTamano?.let { bytes ->
                                    Text(
                                        text = "${(bytes / 1024).coerceAtLeast(1)} KB",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }

                    TipoMensajeChat.TEXTO -> {
                        Text(text = mensaje.contenido, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (mensaje.createdAt.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = mensaje.createdAt.takeLast(8).trim(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

private fun iniciarGrabacionAudio(
    context: Context,
    onStarted: (MediaRecorder, File) -> Unit
) {
    runCatching {
        val dir = File(context.cacheDir, "chat_audios").apply { mkdirs() }
        val file = File(dir, "nota_voz_${System.currentTimeMillis()}.m4a")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        rec.setOutputFile(file.absolutePath)
        rec.prepare()
        rec.start()
        onStarted(rec, file)
    }
}

private fun reproducirAudioUrl(pathOrUrl: String, onStateChange: (Boolean) -> Unit) {
    runCatching {
        val mp = MediaPlayer()
        mp.setDataSource(pathOrUrl)
        mp.setOnPreparedListener {
            onStateChange(true)
            it.start()
        }
        mp.setOnCompletionListener {
            onStateChange(false)
            it.release()
        }
        mp.setOnErrorListener { player, _, _ ->
            onStateChange(false)
            player.release()
            true
        }
        mp.prepareAsync()
    }
}

private fun copiarUriACacheChat(
    context: Context,
    uri: Uri,
    defaultName: String
): Pair<File, String>? {
    return try {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        var fileName = defaultName
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                fileName = cursor.getString(idx) ?: defaultName
            }
        }
        val dir = File(context.cacheDir, "chat_uploads").apply { mkdirs() }
        val target = File(dir, "${System.currentTimeMillis()}_$fileName")
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { out -> input.copyTo(out) }
        }
        Pair(target, mime)
    } catch (_: Exception) {
        null
    }
}
