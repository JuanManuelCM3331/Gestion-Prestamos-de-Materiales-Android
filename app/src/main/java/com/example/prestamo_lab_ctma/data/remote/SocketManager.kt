package com.example.prestamo_lab_ctma.data.remote

import android.util.Log
import com.example.prestamo_lab_ctma.data.remote.dto.ChatMensajeDto
import com.example.prestamo_lab_ctma.data.remote.dto.EquipoDto
import com.example.prestamo_lab_ctma.data.remote.dto.PrestamoDto
import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class CatalogoSocketEvent(
    val accion: String,
    val equipo: EquipoDto?
)

data class StockSocketEvent(
    val equipoId: Int,
    val stockTotal: Int,
    val stockDisponible: Int
)

data class PrestamoSocketEvent(
    val accion: String,
    val prestamo: PrestamoDto
)

data class ChatSocketEvent(
    val prestamoId: Int,
    val mensaje: ChatMensajeDto
)

@Singleton
class SocketManager @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private var socket: Socket? = null
    private var currentToken: String? = null
    private var currentBaseUrl: String? = null

    private val _catalogoEvents = MutableSharedFlow<CatalogoSocketEvent>(extraBufferCapacity = 32)
    val catalogoEvents: SharedFlow<CatalogoSocketEvent> = _catalogoEvents.asSharedFlow()

    private val _stockEvents = MutableSharedFlow<StockSocketEvent>(extraBufferCapacity = 32)
    val stockEvents: SharedFlow<StockSocketEvent> = _stockEvents.asSharedFlow()

    private val _prestamoEvents = MutableSharedFlow<PrestamoSocketEvent>(extraBufferCapacity = 32)
    val prestamoEvents: SharedFlow<PrestamoSocketEvent> = _prestamoEvents.asSharedFlow()

    private val _chatEvents = MutableSharedFlow<ChatSocketEvent>(extraBufferCapacity = 64)
    val chatEvents: SharedFlow<ChatSocketEvent> = _chatEvents.asSharedFlow()

    fun connect(baseUrl: String, token: String?) {
        val cleanUrl = baseUrl.trimEnd('/')
        if (socket?.connected() == true && currentBaseUrl == cleanUrl && currentToken == token) {
            return
        }

        disconnect()
        currentBaseUrl = cleanUrl
        currentToken = token

        try {
            val options = IO.Options.builder()
                .setReconnection(true)
                .setReconnectionAttempts(Int.MAX_VALUE)
                .setReconnectionDelay(1500)
                .apply {
                    if (!token.isNullOrBlank()) {
                        setAuth(mapOf("token" to token))
                        setQuery("token=$token")
                    }
                }
                .build()

            val newSocket = IO.socket(cleanUrl, options)

            newSocket.on(Socket.EVENT_CONNECT) {
                Log.d("SocketManager", "Conectado a Socket.IO en $cleanUrl")
            }

            newSocket.on("catalogo:actualizado") { args ->
                val json = args.firstOrNull() as? JSONObject ?: return@on
                runCatching {
                    val accion = json.optString("accion", "EDITADO")
                    val eqObj = json.optJSONObject("equipo")
                    val dto = eqObj?.let { gson.fromJson(it.toString(), EquipoDto::class.java) }
                    scope.launch {
                        _catalogoEvents.emit(CatalogoSocketEvent(accion, dto))
                    }
                }
            }

            newSocket.on("stock:actualizado") { args ->
                val json = args.firstOrNull() as? JSONObject ?: return@on
                runCatching {
                    val event = StockSocketEvent(
                        equipoId = json.getInt("equipoId"),
                        stockTotal = json.getInt("stockTotal"),
                        stockDisponible = json.getInt("stockDisponible")
                    )
                    scope.launch {
                        _stockEvents.emit(event)
                    }
                }
            }

            newSocket.on("prestamo:actualizado") { args ->
                val json = args.firstOrNull() as? JSONObject ?: return@on
                runCatching {
                    val accion = json.optString("accion", "ACTUALIZADO")
                    val pObj = json.optJSONObject("prestamo") ?: return@runCatching
                    val dto = gson.fromJson(pObj.toString(), PrestamoDto::class.java)
                    scope.launch {
                        _prestamoEvents.emit(PrestamoSocketEvent(accion, dto))
                    }
                }
            }

            newSocket.on("chat:mensaje_nuevo") { args ->
                val json = args.firstOrNull() as? JSONObject ?: return@on
                runCatching {
                    val prestamoId = json.getInt("prestamoId")
                    val mObj = json.optJSONObject("mensaje") ?: return@runCatching
                    val dto = gson.fromJson(mObj.toString(), ChatMensajeDto::class.java)
                    scope.launch {
                        _chatEvents.emit(ChatSocketEvent(prestamoId, dto))
                    }
                }
            }

            newSocket.connect()
            socket = newSocket
        } catch (e: Exception) {
            Log.e("SocketManager", "Error conectando Socket.IO", e)
        }
    }

    fun joinEquipoRoom(equipoId: Int) {
        val payload = JSONObject().put("equipoId", equipoId)
        socket?.emit("join:equipo", payload)
    }

    fun leaveEquipoRoom(equipoId: Int) {
        val payload = JSONObject().put("equipoId", equipoId)
        socket?.emit("leave:equipo", payload)
    }

    fun joinPrestamoRoom(prestamoId: Int) {
        val payload = JSONObject().put("prestamoId", prestamoId)
        socket?.emit("join:prestamo", payload)
    }

    fun leavePrestamoRoom(prestamoId: Int) {
        val payload = JSONObject().put("prestamoId", prestamoId)
        socket?.emit("leave:prestamo", payload)
    }

    fun disconnect() {
        runCatching {
            socket?.off()
            socket?.disconnect()
            socket = null
        }
    }
}
