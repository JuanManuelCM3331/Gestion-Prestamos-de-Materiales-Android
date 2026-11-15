package com.example.prestamo_lab_ctma.ui.catalogo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.prestamo_lab_ctma.model.CategoriaEquipo
import com.example.prestamo_lab_ctma.model.Equipo
import com.example.prestamo_lab_ctma.model.EstadoEquipo
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogoScreen(
    equipos: List<Equipo>,
    isLoading: Boolean,
    error: String?,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    onEquipoClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    equiposEliminados: List<Equipo> = emptyList(),
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    esAdmin: Boolean = false,
    usuarioNombre: String = "",
    onCrearEquipo: () -> Unit = {},
    onEditarEquipo: (Int) -> Unit = {},
    onEliminarLogico: (Int) -> Unit = {},
    onRestaurarEquipo: (Int) -> Unit = {},
    onEliminarPermanente: (Int) -> Unit = {},
    onRefrescar: () -> Unit = {},
    onCerrarSesion: () -> Unit = {}
) {
    var tabIndex by remember { mutableStateOf(0) }
    var equipoConfirmarBorradoPermanente by remember { mutableStateOf<Equipo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Catálogo de Equipos CTMA")
                        if (usuarioNombre.isNotBlank()) {
                            Text(
                                text = "$usuarioNombre (${if (esAdmin) "ADMIN_EQUIPOS" else "USUARIO"})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onRefrescar) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sincronizar")
                    }
                    IconButton(onClick = onCerrarSesion) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Cerrar Sesión")
                    }
                }
            )
        },
        floatingActionButton = {
            if (esAdmin && tabIndex == 0) {
                ExtendedFloatingActionButton(
                    onClick = onCrearEquipo,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Nuevo Equipo") }
                )
            }
        }
    ) { padding ->
        Column(modifier = modifier.padding(padding)) {
            if (esAdmin) {
                TabRow(selectedTabIndex = tabIndex) {
                    Tab(
                        selected = tabIndex == 0,
                        onClick = { tabIndex = 0 },
                        text = { Text("Activos (${equipos.size})") }
                    )
                    Tab(
                        selected = tabIndex == 1,
                        onClick = { tabIndex = 1 },
                        text = { Text("Eliminados (${equiposEliminados.size})") }
                    )
                }
            }

            if (tabIndex == 0) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text("Buscar equipo por nombre o descripción...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )

                CategoryFilterRow(
                    selectedCategory = selectedCategory,
                    onCategorySelected = onCategorySelected
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (error != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = error, color = MaterialTheme.colorScheme.error)
                    }
                } else if (equipos.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No se encontraron equipos con los filtros seleccionados.")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(equipos, key = { it.id }) { equipo ->
                            EquipoItem(
                                equipo = equipo,
                                esAdmin = esAdmin,
                                onClick = { onEquipoClick(equipo.id) },
                                onEditar = { onEditarEquipo(equipo.id) },
                                onEliminarLogico = { onEliminarLogico(equipo.id) }
                            )
                        }
                    }
                }
            } else {
                // Pestaña "Eliminados" (Solo ADMIN_EQUIPOS)
                if (equiposEliminados.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No hay equipos en la papelera de Eliminados.")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(equiposEliminados, key = { it.id }) { equipo ->
                            EquipoEliminadoItem(
                                equipo = equipo,
                                onRestaurar = { onRestaurarEquipo(equipo.id) },
                                onSolicitarBorradoPermanente = { equipoConfirmarBorradoPermanente = equipo }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal de confirmación para eliminación permanente
    equipoConfirmarBorradoPermanente?.let { eq ->
        AlertDialog(
            onDismissRequest = { equipoConfirmarBorradoPermanente = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Confirmar Eliminación Permanente") },
            text = {
                Text(
                    "¿Estás seguro de que deseas eliminar permanentemente \"${eq.nombre}\" del sistema?\n\n" +
                        "Esta acción no se puede deshacer. Nota: Si el equipo tiene préstamos en estado ACTIVO, el sistema bloqueará el borrado."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEliminarPermanente(eq.id)
                        equipoConfirmarBorradoPermanente = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar Permanentemente")
                }
            },
            dismissButton = {
                TextButton(onClick = { equipoConfirmarBorradoPermanente = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun CategoryFilterRow(
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onCategorySelected(null) },
                label = { Text("Todos") }
            )
        }
        items(CategoriaEquipo.values()) { category ->
            FilterChip(
                selected = selectedCategory == category.name,
                onClick = { onCategorySelected(category.name) },
                label = { Text(category.name) }
            )
        }
    }
}

@Composable
fun EquipoItem(
    equipo: Equipo,
    esAdmin: Boolean = false,
    onClick: () -> Unit,
    onEditar: () -> Unit = {},
    onEliminarLogico: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!equipo.imagenUrl.isNullOrBlank()) {
                    val imgModel = if (equipo.imagenUrl.startsWith("http")) equipo.imagenUrl else File(equipo.imagenUrl)
                    Image(
                        painter = rememberAsyncImagePainter(imgModel),
                        contentDescription = equipo.nombre,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = equipo.nombre,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Categoría: ${equipo.categoria}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (equipo.descripcion.isNotBlank()) {
                        Text(
                            text = equipo.descripcion,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val disponible = equipo.stockDisponible > 0 && equipo.estado == EstadoEquipo.DISPONIBLE
            val colorEstado = if (disponible) Color(0xFF2E7D32) else Color(0xFFC62828)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = colorEstado.copy(alpha = 0.12f),
                    contentColor = colorEstado,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (disponible) {
                            "DISPONIBLE • Stock: ${equipo.stockDisponible} / ${equipo.stockTotal}"
                        } else {
                            "AGOTADO (0 / ${equipo.stockTotal})"
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (equipo.unidadesEnPrestamoActivo > 0) {
                    Text(
                        text = "En préstamo: ${equipo.unidadesEnPrestamoActivo}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClick) {
                    Text("Ver detalle / Solicitar")
                }

                if (esAdmin) {
                    Row {
                        IconButton(onClick = onEditar) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar equipo")
                        }
                        IconButton(onClick = onEliminarLogico) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Enviar a Eliminados",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EquipoEliminadoItem(
    equipo: Equipo,
    onRestaurar: () -> Unit,
    onSolicitarBorradoPermanente: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${equipo.nombre} (Eliminado)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Categoría: ${equipo.categoria} • Stock total: ${equipo.stockTotal} (En préstamo activo: ${equipo.unidadesEnPrestamoActivo})",
                style = MaterialTheme.typography.bodySmall
            )
            if (equipo.descripcion.isNotBlank()) {
                Text(
                    text = equipo.descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRestaurar,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restaurar")
                }

                OutlinedButton(
                    onClick = onSolicitarBorradoPermanente,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Borrar Definitivo")
                }
            }
        }
    }
}
