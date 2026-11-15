package com.example.prestamo_lab_ctma.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.prestamo_lab_ctma.ui.auth.AdminPanelScreen
import com.example.prestamo_lab_ctma.ui.auth.LoginScreen
import com.example.prestamo_lab_ctma.ui.auth.RegistroScreen
import com.example.prestamo_lab_ctma.ui.catalogo.CatalogoScreen
import com.example.prestamo_lab_ctma.ui.chat.ChatPrestamoScreen
import com.example.prestamo_lab_ctma.ui.equipo.EquipoDetalleScreen
import com.example.prestamo_lab_ctma.ui.equipo.EquipoFormScreen
import com.example.prestamo_lab_ctma.ui.misprestamos.MisSolicitudesScreen
import com.example.prestamo_lab_ctma.ui.solicitud.CameraCapture
import com.example.prestamo_lab_ctma.ui.solicitud.SolicitarPrestamoScreen
import com.example.prestamo_lab_ctma.ui.solicitud.SolicitudDetalleScreen
import com.example.prestamo_lab_ctma.ui.util.HardwareManager
import com.example.prestamo_lab_ctma.ui.viewmodel.PrestamoViewModel

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Registro : Screen("registro")
    object Catalogo : Screen("catalogo")
    object EquipoDetalle : Screen("equipo/{equipoId}") {
        fun createRoute(id: Int) = "equipo/$id"
    }
    object EquipoForm : Screen("equipo_form?equipoId={equipoId}") {
        fun createRoute(id: Int? = null) = if (id == null) "equipo_form?equipoId=-1" else "equipo_form?equipoId=$id"
    }
    object Solicitar : Screen("solicitar/{equipoId}") {
        fun createRoute(id: Int) = "solicitar/$id"
    }
    object MisSolicitudes : Screen("mis_solicitudes")
    object SolicitudDetalle : Screen("solicitud/{solicitudId}") {
        fun createRoute(id: Int) = "solicitud/$id"
    }
    object ChatPrestamo : Screen("chat/{prestamoId}") {
        fun createRoute(id: Int) = "chat/$id"
    }
    object AdminPanel : Screen("admin_panel")
    object Camera : Screen("camera")
}

@Composable
fun AppNavigation(
    viewModel: PrestamoViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val hardwareManager = remember { HardwareManager(context) }
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formularioState.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Vibración háptica en operaciones exitosas
    LaunchedEffect(uiState.operacionExitosa) {
        if (uiState.operacionExitosa) {
            hardwareManager.vibrate()
        }
    }

    // Mostrar mensajes de operaciones o errores en Snackbar
    LaunchedEffect(uiState.mensaje) {
        val msg = uiState.mensaje
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.limpiarMensaje()
        }
    }

    // Mostrar notificaciones en tiempo real (Socket.IO: cambios de stock/equipo/chat)
    LaunchedEffect(uiState.avisoTiempoReal) {
        val aviso = uiState.avisoTiempoReal
        if (!aviso.isNullOrBlank()) {
            snackbarHostState.showSnackbar(aviso)
            viewModel.limpiarAvisoTiempoReal()
        }
    }

    // Redirección reactiva según estado de autenticación
    LaunchedEffect(uiState.estaAutenticado, currentRoute) {
        if (!uiState.estaAutenticado && currentRoute != null &&
            currentRoute != Screen.Login.route && currentRoute != Screen.Registro.route
        ) {
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        } else if (uiState.estaAutenticado &&
            (currentRoute == Screen.Login.route || currentRoute == Screen.Registro.route)
        ) {
            navController.navigate(Screen.Catalogo.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val esAdmin = uiState.usuarioActual?.esAdmin == true

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (uiState.estaAutenticado) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.List, contentDescription = null) },
                        label = { Text("Catálogo") },
                        selected = currentRoute == Screen.Catalogo.route,
                        onClick = {
                            navController.navigate(Screen.Catalogo.route) {
                                popUpTo(Screen.Catalogo.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (uiState.totalMensajesNoLeidos > 0) {
                                        Badge { Text("${uiState.totalMensajesNoLeidos}") }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null)
                            }
                        },
                        label = { Text(if (esAdmin) "Préstamos" else "Mis Préstamos") },
                        selected = currentRoute == Screen.MisSolicitudes.route,
                        onClick = {
                            navController.navigate(Screen.MisSolicitudes.route) {
                                popUpTo(Screen.Catalogo.route)
                                launchSingleTop = true
                            }
                        }
                    )
                    if (esAdmin) {
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                            label = { Text("Admin / Logs") },
                            selected = currentRoute == Screen.AdminPanel.route,
                            onClick = {
                                navController.navigate(Screen.AdminPanel.route) {
                                    popUpTo(Screen.Catalogo.route)
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (uiState.estaAutenticado) Screen.Catalogo.route else Screen.Login.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    guardando = uiState.guardando,
                    apiBaseUrl = uiState.apiBaseUrl,
                    onLogin = viewModel::login,
                    onIrARegistro = { navController.navigate(Screen.Registro.route) },
                    onCambiarUrlServidor = viewModel::actualizarUrlServidor
                )
            }

            composable(Screen.Registro.route) {
                RegistroScreen(
                    guardando = uiState.guardando,
                    onRegistrar = viewModel::registrarUsuario,
                    onVolverALogin = { navController.popBackStack() }
                )
            }

            composable(Screen.Catalogo.route) {
                CatalogoScreen(
                    equipos = uiState.equipos,
                    equiposEliminados = uiState.equiposEliminados,
                    isLoading = uiState.isLoading,
                    error = uiState.error,
                    selectedCategory = uiState.filterCategory,
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onCategorySelected = viewModel::updateFilter,
                    onEquipoClick = { id ->
                        navController.navigate(Screen.EquipoDetalle.createRoute(id))
                    },
                    esAdmin = esAdmin,
                    usuarioNombre = uiState.usuarioActual?.nombre.orEmpty(),
                    onCrearEquipo = {
                        navController.navigate(Screen.EquipoForm.createRoute(null))
                    },
                    onEditarEquipo = { id ->
                        navController.navigate(Screen.EquipoForm.createRoute(id))
                    },
                    onEliminarLogico = { id ->
                        viewModel.eliminarEquipoLogico(id)
                    },
                    onRestaurarEquipo = viewModel::restaurarEquipo,
                    onEliminarPermanente = viewModel::eliminarEquipoPermanente,
                    onRefrescar = viewModel::refrescarDatos,
                    onCerrarSesion = viewModel::cerrarSesion
                )
            }

            composable(
                route = Screen.EquipoForm.route,
                arguments = listOf(navArgument("equipoId") { type = NavType.IntType; defaultValue = -1 })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getInt("equipoId") ?: -1
                val equipoExistente = if (id > 0) viewModel.obtenerEquipoPorId(id) else null

                EquipoFormScreen(
                    equipoExistente = equipoExistente,
                    guardando = uiState.guardando,
                    onGuardar = { idEd, nombre, cat, stock, desc, imgPath ->
                        viewModel.guardarEquipoCatalogo(idEd, nombre, cat, stock, desc, imgPath) {
                            navController.popBackStack()
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.EquipoDetalle.route,
                arguments = listOf(navArgument("equipoId") { type = NavType.IntType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getInt("equipoId") ?: -1

                DisposableEffect(id) {
                    viewModel.setEquipoVistoActualmente(id)
                    onDispose {
                        viewModel.setEquipoVistoActualmente(null)
                    }
                }

                EquipoDetalleScreen(
                    equipo = viewModel.obtenerEquipoPorId(id),
                    esAdmin = esAdmin,
                    onBack = { navController.popBackStack() },
                    onSolicitar = { equipoId ->
                        navController.navigate(Screen.Solicitar.createRoute(equipoId))
                    },
                    onEditar = { equipoId ->
                        navController.navigate(Screen.EquipoForm.createRoute(equipoId))
                    },
                    onEliminarLogico = { equipoId ->
                        viewModel.eliminarEquipoLogico(equipoId) {
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable(
                route = Screen.Solicitar.route,
                arguments = listOf(navArgument("equipoId") { type = NavType.IntType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getInt("equipoId") ?: -1
                val equipo = viewModel.obtenerEquipoPorId(id)

                LaunchedEffect(uiState.operacionExitosa) {
                    if (uiState.operacionExitosa) {
                        navController.navigate(Screen.MisSolicitudes.route) {
                            popUpTo(Screen.Catalogo.route)
                        }
                        viewModel.limpiarMensaje()
                    }
                }

                SolicitarPrestamoScreen(
                    equipo = equipo,
                    formState = formState,
                    guardando = uiState.guardando,
                    onCantidadChange = { cant ->
                        viewModel.onCantidadChange(cant, equipo?.stockDisponible ?: 999)
                    },
                    onFechaDevolucionChange = viewModel::onFechaDevolucionChange,
                    onNombreSolicitanteChange = viewModel::onNombreSolicitanteChange,
                    onDocumentoSolicitanteChange = viewModel::onDocumentoSolicitanteChange,
                    onAmbienteChange = viewModel::onAmbienteChange,
                    onPropositoChange = viewModel::onPropositoChange,
                    onDuracionChange = viewModel::onDuracionChange,
                    onCapturePhoto = { navController.navigate(Screen.Camera.route) },
                    onPhotoSelected = viewModel::onPhotoCaptured,
                    onGuardar = { viewModel.guardarSolicitud(id) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Camera.route) {
                CameraCapture(
                    onImageCaptured = { path ->
                        viewModel.onPhotoCaptured(path)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.MisSolicitudes.route) {
                MisSolicitudesScreen(
                    solicitudes = uiState.solicitudes,
                    equipos = uiState.todosLosEquipos,
                    esAdmin = esAdmin,
                    onSolicitudClick = { id ->
                        navController.navigate(Screen.SolicitudDetalle.createRoute(id))
                    },
                    onAbrirChat = { id ->
                        navController.navigate(Screen.ChatPrestamo.createRoute(id))
                    },
                    onMarcarEntregado = viewModel::marcarPrestamoEntregado,
                    onCancelar = viewModel::cancelarSolicitud,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.SolicitudDetalle.route,
                arguments = listOf(navArgument("solicitudId") { type = NavType.IntType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getInt("solicitudId") ?: -1
                val solicitud = viewModel.obtenerSolicitudPorId(id)
                SolicitudDetalleScreen(
                    solicitud = solicitud,
                    equipo = solicitud?.let { viewModel.obtenerEquipoPorId(it.equipoId) },
                    esAdmin = esAdmin,
                    onCancelar = { viewModel.cancelarSolicitud(id) },
                    onMarcarEntregado = { viewModel.marcarPrestamoEntregado(id) },
                    onAbrirChat = { prestamoId ->
                        navController.navigate(Screen.ChatPrestamo.createRoute(prestamoId))
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.ChatPrestamo.route,
                arguments = listOf(navArgument("prestamoId") { type = NavType.IntType })
            ) { backStackEntry ->
                val prestamoId = backStackEntry.arguments?.getInt("prestamoId") ?: -1
                val prestamo = viewModel.obtenerSolicitudPorId(prestamoId)
                val mensajes by remember(prestamoId) {
                    viewModel.observarMensajesDePrestamo(prestamoId)
                }.collectAsStateWithLifecycle(initialValue = emptyList())

                DisposableEffect(prestamoId) {
                    viewModel.abrirChatPrestamo(prestamoId)
                    onDispose {
                        viewModel.cerrarChatPrestamo(prestamoId)
                    }
                }

                ChatPrestamoScreen(
                    prestamo = prestamo,
                    mensajes = mensajes,
                    usuarioActualId = uiState.usuarioActual?.id ?: 1,
                    onEnviarTexto = { texto ->
                        viewModel.enviarMensajeTextoChat(prestamoId, texto)
                    },
                    onEnviarAdjunto = { tipo, desc, file, mime, dur ->
                        viewModel.enviarAdjuntoChat(prestamoId, tipo, desc, file, mime, dur)
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.AdminPanel.route) {
                AdminPanelScreen(
                    usuarioActual = uiState.usuarioActual,
                    admins = uiState.admins,
                    auditoriaLogs = uiState.auditoriaLogs,
                    guardando = uiState.guardando,
                    onCargarDatos = viewModel::cargarPanelAdmin,
                    onCrearAdmin = { nombre, correo, pass ->
                        viewModel.crearNuevoAdmin(nombre, correo, pass)
                    }
                )
            }
        }
    }
}
