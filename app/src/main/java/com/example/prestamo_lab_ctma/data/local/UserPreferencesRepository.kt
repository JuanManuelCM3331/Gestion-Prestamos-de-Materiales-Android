package com.example.prestamo_lab_ctma.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.prestamo_lab_ctma.BuildConfig
import com.example.prestamo_lab_ctma.model.RolUsuario
import com.example.prestamo_lab_ctma.model.Usuario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class SesionState(
    val token: String? = null,
    val usuario: Usuario? = null,
    val apiBaseUrl: String = BuildConfig.API_BASE_URL
) {
    val estaAutenticado: Boolean
        get() = !token.isNullOrBlank() && usuario != null
}

open class UserPreferencesRepository(private val context: Context) {
    private val CATEGORY_FILTER = stringPreferencesKey("category_filter")
    private val AUTH_TOKEN = stringPreferencesKey("auth_token")
    private val USER_ID = intPreferencesKey("user_id")
    private val USER_NAME = stringPreferencesKey("user_name")
    private val USER_EMAIL = stringPreferencesKey("user_email")
    private val USER_ROLE = stringPreferencesKey("user_role")
    private val API_BASE_URL = stringPreferencesKey("api_base_url")

    open val categoryFilter: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[CATEGORY_FILTER]
    }

    open val sesionFlow: Flow<SesionState> = context.dataStore.data.map { prefs ->
        val token = prefs[AUTH_TOKEN]
        val id = prefs[USER_ID]
        val nombre = prefs[USER_NAME]
        val correo = prefs[USER_EMAIL]
        val rolStr = prefs[USER_ROLE]
        val baseUrl = prefs[API_BASE_URL] ?: BuildConfig.API_BASE_URL

        val usuario = if (id != null && !nombre.isNullOrBlank() && !correo.isNullOrBlank() && !rolStr.isNullOrBlank()) {
            val rol = runCatching { RolUsuario.valueOf(rolStr) }.getOrDefault(RolUsuario.USUARIO)
            Usuario(id = id, nombre = nombre, correo = correo, rol = rol)
        } else {
            null
        }
        SesionState(token = token, usuario = usuario, apiBaseUrl = baseUrl)
    }

    open suspend fun saveCategoryFilter(category: String?) {
        context.dataStore.edit { preferences ->
            if (category == null) {
                preferences.remove(CATEGORY_FILTER)
            } else {
                preferences[CATEGORY_FILTER] = category
            }
        }
    }

    open suspend fun guardarSesion(token: String, usuario: Usuario) {
        context.dataStore.edit { prefs ->
            prefs[AUTH_TOKEN] = token
            prefs[USER_ID] = usuario.id
            prefs[USER_NAME] = usuario.nombre
            prefs[USER_EMAIL] = usuario.correo
            prefs[USER_ROLE] = usuario.rol.name
        }
    }

    open suspend fun cerrarSesion() {
        context.dataStore.edit { prefs ->
            prefs.remove(AUTH_TOKEN)
            prefs.remove(USER_ID)
            prefs.remove(USER_NAME)
            prefs.remove(USER_EMAIL)
            prefs.remove(USER_ROLE)
        }
    }

    open suspend fun guardarApiBaseUrl(url: String) {
        val normalizada = if (url.endsWith("/")) url else "$url/"
        context.dataStore.edit { prefs ->
            prefs[API_BASE_URL] = normalizada
        }
    }

    open suspend fun obtenerTokenActual(): String? {
        return context.dataStore.data.first()[AUTH_TOKEN]
    }

    open suspend fun obtenerBaseUrlActual(): String {
        return context.dataStore.data.first()[API_BASE_URL] ?: BuildConfig.API_BASE_URL
    }
}
