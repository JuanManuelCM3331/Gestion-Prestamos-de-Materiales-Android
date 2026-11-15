package com.example.prestamo_lab_ctma.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.prestamo_lab_ctma.model.AuditoriaLog
import com.example.prestamo_lab_ctma.model.Usuario

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    guardando: Boolean,
    apiBaseUrl: String,
    onLogin: (String, String) -> Unit,
    onIrARegistro: () -> Unit,
    onCambiarUrlServidor: (String) -> Unit
) {
    var correo by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var mostrarConfigUrl by remember { mutableStateOf(false) }
    var nuevaUrl by remember(apiBaseUrl) { mutableStateOf(apiBaseUrl) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PréstamoLab CTMA — Ingreso") },
                actions = {
                    IconButton(onClick = { mostrarConfigUrl = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurar Servidor")
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
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "SENA CTMA",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Sistema en Tiempo Real de Préstamo de Materiales",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = correo,
                onValueChange = { correo = it },
                label = { Text("Correo electrónico") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { onLogin(correo, password) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !guardando && correo.isNotBlank() && password.isNotBlank()
            ) {
                if (guardando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Iniciar Sesión")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onIrARegistro) {
                Text("¿Eres aprendiz o instructor (USUARIO)? Regístrate aquí")
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Acceso rápido de prueba (Seed Inicial):",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                correo = "admin@ctma.sena.edu.co"
                                password = "Admin123*"
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Text("Rellenar ADMIN", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = {
                                correo = "usuario@ctma.sena.edu.co"
                                password = "Usuario123*"
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Text("Rellenar USUARIO", maxLines = 1)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Admin: admin@ctma.sena.edu.co • Usuario: usuario@ctma.sena.edu.co",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (mostrarConfigUrl) {
        AlertDialog(
            onDismissRequest = { mostrarConfigUrl = false },
            title = { Text("URL del Servidor Backend (API + Socket.IO)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "En emulador Android usa http://10.0.2.2:3000/. En dispositivo físico usa la IP LAN de tu PC.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = nuevaUrl,
                        onValueChange = { nuevaUrl = it },
                        label = { Text("URL Base") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onCambiarUrlServidor(nuevaUrl)
                    mostrarConfigUrl = false
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfigUrl = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistroScreen(
    guardando: Boolean,
    onRegistrar: (String, String, String) -> Unit,
    onVolverALogin: () -> Unit
) {
    var nombre by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registro Público (Rol USUARIO)") },
                navigationIcon = {
                    IconButton(onClick = onVolverALogin) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Nota de seguridad: El registro público crea únicamente cuentas con rol USUARIO. Los administradores de equipos (ADMIN_EQUIPOS) solo pueden ser creados por otro administrador desde el panel interno.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre completo") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = correo,
                onValueChange = { correo = it },
                label = { Text("Correo institucional o personal") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña (mínimo 6 caracteres)") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onRegistrar(nombre, correo, password) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !guardando && nombre.trim().length >= 3 && correo.contains("@") && password.length >= 6
            ) {
                if (guardando) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Crear Cuenta de Usuario")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    usuarioActual: Usuario?,
    admins: List<Usuario>,
    auditoriaLogs: List<AuditoriaLog>,
    guardando: Boolean,
    onCargarDatos: () -> Unit,
    onCrearAdmin: (String, String, String) -> Unit
) {
    LaunchedEffect(Unit) {
        onCargarDatos()
    }

    var tabIndex by remember { mutableStateOf(0) }
    var nombreAdmin by remember { mutableStateOf("") }
    var correoAdmin by remember { mutableStateOf("") }
    var passTemporal by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Panel de Administración CTMA") },
                actions = {
                    IconButton(onClick = onCargarDatos) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                    }
                }
            )
        }
    ) { padding ->
        if (usuarioActual?.esAdmin != true) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Acceso restringido exclusivamente a ADMIN_EQUIPOS.")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    text = { Text("Administradores (${admins.size})") }
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text("Auditoría (${auditoriaLogs.size})") }
                )
            }

            if (tabIndex == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Registrar nuevo ADMIN_EQUIPOS",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedTextField(
                                    value = nombreAdmin,
                                    onValueChange = { nombreAdmin = it },
                                    label = { Text("Nombre completo") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = correoAdmin,
                                    onValueChange = { correoAdmin = it },
                                    label = { Text("Correo electrónico") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = passTemporal,
                                    onValueChange = { passTemporal = it },
                                    label = { Text("Contraseña temporal (mín. 6 caracteres)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        onCrearAdmin(nombreAdmin, correoAdmin, passTemporal)
                                        nombreAdmin = ""
                                        correoAdmin = ""
                                        passTemporal = ""
                                    },
                                    enabled = !guardando &&
                                        nombreAdmin.trim().length >= 3 &&
                                        correoAdmin.contains("@") &&
                                        passTemporal.length >= 6,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Crear Administrador")
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Administradores activos en el sistema:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(admins) { adm ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(text = adm.nombre, style = MaterialTheme.typography.titleMedium)
                                Text(text = adm.correo, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "Rol: ${adm.rol}" +
                                        (if (adm.creadoPorNombre != null) " • Creado por: ${adm.creadoPorNombre}" else " • Admin Inicial (Seed)"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            } else {
                if (auditoriaLogs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No hay registros de auditoría disponibles.")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(auditoriaLogs) { log ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = log.accion,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = log.createdAt.take(19).replace("T", " "),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Text(
                                        text = "Por: ${log.usuarioNombre} (${log.usuarioRol}) • ${log.entidad} #${log.entidadId ?: "-"}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (!log.detalle.isNullOrBlank()) {
                                        Text(
                                            text = log.detalle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
