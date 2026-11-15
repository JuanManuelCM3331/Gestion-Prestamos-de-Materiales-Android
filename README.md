# PréstamoLab CTMA

**PréstamoLab CTMA** es una plataforma cliente-servidor (App Android nativa + API REST + Socket.IO + MySQL) para la gestión integral, control de existencias atómico, certificación en imagen y comunicación en tiempo real de préstamos de equipos y herramientas en el Centro de Tecnología de la Manufactura Avanzada (**SENA CTMA**).

---

## Tabla de Contenidos

1. [Arquitectura del Sistema](#arquitectura-del-sistema)
2. [Funcionalidades por Rol](#funcionalidades-por-rol)
3. [Usuarios de Prueba y Autocompletado en Login](#usuarios-de-prueba-y-autocompletado-en-login)
4. [Cómo Ejecutar el Proyecto](#cómo-ejecutar-el-proyecto)
5. [Cómo Ejecutar las Pruebas](#cómo-ejecutar-las-pruebas)
6. [API REST y Eventos en Tiempo Real](#api-rest-y-eventos-en-tiempo-real)
7. [Modelo de Datos y Reglas Clave](#modelo-de-datos-y-reglas-clave)
8. [Stack Tecnológico y Permisos](#stack-tecnológico-y-permisos)

---

## Arquitectura del Sistema

El repositorio está dividido en dos módulos principales:

```text
Prestamos-Materiales-CTMA/
├── backend/                  # API REST (Node.js + Express) + Socket.IO + MySQL / fallback SQLite
│   ├── sql/schema.sql        # Esquema relacional InnoDB (7 tablas)
│   ├── src/config/           # Conexión MySQL/SQLite, seed inicial y configuración
│   ├── src/middlewares/      # Autenticación JWT, RBAC por rol y subida segura Multer (10 MB)
│   ├── src/routes/           # Rutas de auth/admin, equipos, préstamos y chat
│   ├── src/socket/           # Servidor Socket.IO (salas catálogo, equipo, admins, préstamo)
│   ├── src/services/         # Registro de auditoría
│   └── tests/                # Suite E2E y prueba de concurrencia atómica
├── app/                      # App Android (Kotlin + Jetpack Compose + MVVM + Hilt + Room + Socket.IO)
│   └── src/main/java/com/example/prestamo_lab_ctma/
│       ├── di/               # Módulo Dagger Hilt (Retrofit, OkHttp, Room, SocketManager)
│       ├── model/            # Modelos de dominio y estados
│       ├── data/local/       # Caché offline Room v2 (MIGRATION_1_2) + DataStore preferencias/URL
│       ├── data/remote/      # ApiService (Retrofit), DTOs y cliente Socket.IO
│       ├── data/repository/  # PrestamoRepository + sincronización híbrida local/remota
│       └── ui/               # Pantallas Compose, Auth, Catálogo, Préstamos, Chat y Certificado PNG
└── docs/                     # Historias de usuario, riesgos y plan de pruebas (HU, RIESGOS, PLAN_PRUEBAS)
```

---

## Funcionalidades por Rol

### 1. Autenticación y Administración (`ADMIN_EQUIPOS` y `USUARIO`)
- **Login con JWT** para ambos roles (`POST /api/auth/login`) y validación estricta de permisos en el backend (`verifyToken` + `requireRole`).
- **Registro público exclusivo para `USUARIO`** (`POST /api/auth/register`): si alguien intenta registrarse enviando `rol = "ADMIN_EQUIPOS"`, el servidor lo rechaza con HTTP `403`.
- **Panel de Administración** (solo `ADMIN_EQUIPOS`): permite crear nuevos administradores (nombre, correo y contraseña temporal), consultar el listado de administradores y revisar el **registro de auditoría** (`auditoria_logs`).

### 2. Catálogo de Equipos
- **Búsqueda y filtros** en vivo por nombre y categoría (`ELECTRONICA`, `MULTIMEDIA`, `INFORMATICA`, `HERRAMIENTAS`, `OTRO`).
- **Gestión (`ADMIN_EQUIPOS`)**:
  - Crear y editar equipos (nombre, categoría, existencias totales, descripción e imagen del producto desde galería/cámara).
  - **Eliminación lógica**: envía el equipo a la pestaña **Eliminados** (`eliminado = true`).
  - **Pestaña Eliminados**: permite **restaurar** el equipo al catálogo activo o **eliminarlo permanentemente** (con modal de confirmación). No se permite eliminación permanente mientras tenga préstamos en estado `ACTIVO` (HTTP `409`).

### 3. Préstamos y Certificado PNG
- **Flujo `USUARIO`**:
  - Solicitar préstamo sin aprobación previa indicando cantidad, fecha estimada de devolución, ambiente de destino, motivo/observaciones y datos del solicitante.
  - Al confirmar, el préstamo nace en estado **`ACTIVO`**, descuenta unidades de `stock_disponible` de forma atómica en BD y genera un **certificado exportable en imagen (PNG)** (`CertificadoGenerator`, guardado en *Pictures/PrestamoLab_CTMA*).
  - En **Mis Préstamos** (pestañas *Activos*, *Entregados*, *Cancelados*), el usuario puede **cancelar** un préstamo `ACTIVO`, devolviendo automáticamente las unidades al stock en tiempo real.
- **Vista `ADMIN_EQUIPOS`**:
  - Listado completo de préstamos con detalle del solicitante, cantidad, fechas, estado y exportación del certificado PNG.
  - Acción **Marcar como ENTREGADO**, cerrando el préstamo y restaurando las unidades a `stock_disponible`.

### 4. Chat en Tiempo Real por Préstamo
- Al crearse un préstamo se habilita automáticamente una sala de chat asociada entre el **`USUARIO` dueño del préstamo** y los **`ADMIN_EQUIPOS`** (el backend valida participación tanto en HTTP como en el handshake/salas de Socket.IO).
- Soporte para mensajes de **texto**, **imágenes**, **documentos** (PDF, DOC, DOCX, TXT) y **audios de voz** (grabación y reproducción directa desde la app).
- Contadores de mensajes **no leídos**, notificaciones en vivo al recibir mensajes nuevos y cambio automático a **modo solo lectura** cuando el préstamo pasa a `ENTREGADO` o `CANCELADO`.

---

## Usuarios de Prueba y Autocompletado en Login

Al iniciar el backend por primera vez (o ejecutar `npm run seed`), se crean automáticamente **dos cuentas de prueba**:

| Rol | Nombre | Correo | Contraseña |
| :--- | :--- | :--- | :--- |
| **`ADMIN_EQUIPOS`** | Administrador Principal CTMA | `admin@ctma.sena.edu.co` | `Admin123*` |
| **`USUARIO`** | Aprendiz Prueba CTMA | `usuario@ctma.sena.edu.co` | `Usuario123*` |

En la pantalla de **Inicio de Sesión** de la app (`AuthScreens.kt`) encontrarás dos botones de autocompletado rápido dentro de la tarjeta *"Acceso rápido de prueba (Seed Inicial)"*:
- **`Rellenar ADMIN`**: autocompleta `admin@ctma.sena.edu.co` / `Admin123*`.
- **`Rellenar USUARIO`**: autocompleta `usuario@ctma.sena.edu.co` / `Usuario123*`.

---

## Cómo Ejecutar el Proyecto

### Requisitos Previos
- **Node.js** 20+ (o 22+) y `npm`
- **Android Studio** (JDK / JetBrains Runtime incluido en `Android Studio/jbr`) y Android SDK (`compileSdk = 35`, `minSdk = 24`)
- **MySQL / MariaDB** (opcional, por ejemplo XAMPP en `127.0.0.1:3306`). *Si el servicio MySQL está apagado, el backend activa automáticamente un motor relacional SQL embebido local (`backend/data/prestamo_lab_ctma.sqlite`) compatible con las mismas tablas y transacciones.*

### Paso 1: Levantar el Backend

1. Ve a la carpeta `backend` e instala dependencias:
   ```bash
   cd backend
   npm install
   ```
   *(En Windows PowerShell, si la política de ejecución bloquea `npm`, usa `npm.cmd install` y `npm.cmd start`.)*

2. *(Opcional)* Copia la plantilla de variables de entorno si deseas personalizar puerto, credenciales MySQL o seed:
   ```bash
   # En PowerShell / Windows:
   Copy-Item .env.example .env
   ```
   Valores por defecto incluidos en `backend/.env.example`:
   ```env
   PORT=3000
   DB_HOST=127.0.0.1
   DB_PORT=3306
   DB_USER=root
   DB_PASSWORD=
   DB_NAME=prestamo_lab_ctma
   JWT_SECRET=ctma_sena_super_secret_jwt_key_2026
   JWT_EXPIRES_IN=24h
   MAX_FILE_SIZE_MB=10
   ADMIN_SEED_NAME=Administrador Principal CTMA
   ADMIN_SEED_EMAIL=admin@ctma.sena.edu.co
   ADMIN_SEED_PASSWORD=Admin123*
   USER_SEED_NAME=Aprendiz Prueba CTMA
   USER_SEED_EMAIL=usuario@ctma.sena.edu.co
   USER_SEED_PASSWORD=Usuario123*
   ```

3. Inicia el servidor (ejecuta automáticamente `sql/schema.sql`, crea las dos cuentas semilla e inserta 8 equipos iniciales si el catálogo está vacío):
   ```bash
   npm start
   ```
   Para modo desarrollo con recarga automática:
   ```bash
   npm run dev
   ```
   Verifica que el servidor responde en `http://localhost:3000/api/health`.

### Paso 2: Compilar y Ejecutar la App Android

1. Abre la raíz del proyecto (`Prestamos-Materiales-CTMA`) en **Android Studio**.
2. Ejecuta **File → Sync Project with Gradle Files** (ícono del elefante) antes de compilar.
3. Configura la URL del servidor si es necesario:
   - **En Emulador Android**: viene preconfigurado con `http://10.0.2.2:3000/` (`BuildConfig.API_BASE_URL`), que apunta al `localhost:3000` de tu PC.
   - **En dispositivo físico**: asegúrate de estar en la misma red Wi-Fi, abre la pantalla de Login en la app, toca el ícono de configuración (**⚙**) arriba a la derecha e ingresa la IP local de tu PC (ejemplo: `http://192.168.1.50:3000/`).
4. Pulsa **Run 'app' (▶)** para instalar y abrir la app en el emulador o dispositivo, o compila por consola:
   ```powershell
   .\gradlew.bat assembleDebug
   ```

> **Nota para equipos Windows con 8 GB de RAM:**  
> `gradle.properties` está optimizado con `-Xmx896m -XX:MaxMetaspaceSize=320m -XX:+UseSerialGC` y `org.gradle.workers.max=2` para evitar errores de memoria virtual al tener Android Studio y el emulador abiertos simultáneamente. Si quedan procesos Gradle en segundo plano consumiendo memoria, puedes detenerlos con `.\gradlew.bat --stop`.

---

## Cómo Ejecutar las Pruebas

### Pruebas del Backend (`backend/`)
Con dependencias instaladas en `backend/`:
```bash
cd backend

# 1. Suite E2E completa (Auth/RBAC, Catálogo, Eliminación lógica/permanente, Préstamos, Chat y Auditoría)
npm test

# 2. Prueba de concurrencia atómica (2 usuarios solicitando la última unidad al mismo tiempo -> [201, 409])
npm run test:concurrency
```
*(En PowerShell usa `npm.cmd test` y `npm.cmd run test:concurrency`.)*

### Pruebas Unitarias Android (`app/`)
Desde la raíz del repositorio:
```powershell
.\gradlew.bat testDebugUnitTest
```
Ejecuta `ExampleUnitTest` y `UserStoriesValidationTest` (reglas de negocio, estados, repositorio local y caché Room).

---

## API REST y Eventos en Tiempo Real

### Endpoints Principales (`/api`)
- **Salud**: `GET /api/health`
- **Auth y Admin**:
  - `POST /api/auth/register` *(solo `USUARIO`)*
  - `POST /api/auth/login`
  - `GET /api/auth/me`
  - `POST /api/admin/admins` *(solo `ADMIN_EQUIPOS`)*
  - `GET /api/admin/admins` *(solo `ADMIN_EQUIPOS`)*
  - `GET /api/admin/auditoria` *(solo `ADMIN_EQUIPOS`)*
- **Equipos**:
  - `GET /api/equipos` *(filtros por nombre, categoría, `eliminados=true` solo admin)*
  - `GET /api/equipos/:id`
  - `POST /api/equipos` *(solo `ADMIN_EQUIPOS`, multipart hasta 10 MB)*
  - `PUT /api/equipos/:id` *(solo `ADMIN_EQUIPOS`)*
  - `DELETE /api/equipos/:id` *(eliminación lógica, solo `ADMIN_EQUIPOS`)*
  - `PATCH /api/equipos/:id/restaurar` *(solo `ADMIN_EQUIPOS`)*
  - `DELETE /api/equipos/:id/permanente` *(solo `ADMIN_EQUIPOS`, bloqueado si hay préstamos `ACTIVO`)*
- **Préstamos**:
  - `POST /api/prestamos` *(descuento atómico con `SELECT ... FOR UPDATE`, crea chat)*
  - `GET /api/prestamos/mios`
  - `GET /api/prestamos` *(solo `ADMIN_EQUIPOS`)*
  - `GET /api/prestamos/:id` *(dueño o `ADMIN_EQUIPOS`)*
  - `PATCH /api/prestamos/:id/cancelar` *(restaura stock y cierra chat a solo lectura)*
  - `PATCH /api/prestamos/:id/entregar` *(solo `ADMIN_EQUIPOS`, restaura stock y cierra chat)*
- **Chat**:
  - `GET /api/prestamos/:id/chat`
  - `POST /api/prestamos/:id/chat/mensajes` *(texto o multipart: imagen, archivo o audio)*
  - `POST /api/prestamos/:id/chat/leido`

### Eventos Socket.IO
- `catalogo:actualizado` — creación, edición, borrado lógico/permanente o restauración de equipos.
- `equipo:detalle_actualizado` — notificación en la sala `equipo:<id>` si un equipo abierto se edita o elimina.
- `stock:actualizado` — actualización inmediata de `stockTotal` y `stockDisponible`.
- `prestamo:actualizado` — creación, cancelación o entrega de un préstamo.
- `chat:mensaje_nuevo` y `chat:mensajes_leidos` — mensajes y confirmaciones de lectura en tiempo real.

---

## Modelo de Datos y Reglas Clave

- **Tablas SQL (`backend/sql/schema.sql`)**: `usuarios`, `equipos`, `prestamos`, `chats`, `chat_mensajes`, `chat_lecturas`, `auditoria_logs`.
- **Caché offline Android (`AppDatabase.kt`)**: Room v2 con `MIGRATION_1_2` preservando datos locales previos.
- **Control de existencias y concurrencia**: cada préstamo y devolución se ejecuta en transacción SQL atómica (`SELECT ... FOR UPDATE`), evitando que dos solicitudes simultáneas sobre la última unidad produzcan stock negativo (una obtiene `201` y la otra `409 Conflict`).
- **Límites de carga de archivos (`uploadMiddleware.js`)**: máximo **10 MB** (`MAX_FILE_SIZE_MB`). Formatos permitidos: imágenes (`JPG`, `PNG`, `WEBP`), documentos (`PDF`, `DOC`, `DOCX`, `TXT`) y audio (`M4A`, `MP3`, `AAC`, `WAV`, `OGG`, `3GP`, `MP4`).

---

## Stack Tecnológico y Permisos

- **Backend**: Node.js, Express `4.21.2`, MySQL (`mysql2`), Socket.IO `4.8.1`, JWT (`jsonwebtoken`), `bcryptjs`, `multer`.
- **Android**: Kotlin `2.1.0`, AGP `8.7.3`, Jetpack Compose (BOM `2025.02.00`) + Material 3, Dagger Hilt `2.53.1`, Navigation Compose `2.8.7`, Room `2.6.1`, DataStore `1.1.1`, Retrofit `2.9.0` + OkHttp `4.12.0`, Socket.IO Client `2.1.0`, CameraX `1.4.0`, Coil `2.6.0`.
- **Permisos Android (`AndroidManifest.xml`)**:
  - `INTERNET` y `ACCESS_NETWORK_STATE`: comunicación REST y Socket.IO.
  - `CAMERA` *(opcional)*: captura de evidencia o foto de equipo/chat.
  - `RECORD_AUDIO` *(opcional)*: grabación de notas de voz en el chat.
  - `VIBRATE`: confirmación háptica al completar acciones clave.

---
*Proyecto desarrollado para el Centro de Tecnología de la Manufactura Avanzada — SENA CTMA.*
