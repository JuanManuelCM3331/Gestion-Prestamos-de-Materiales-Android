# Backend PréstamoLab CTMA (Node.js + Express + MySQL + Socket.IO)

Backend RESTful y servidor en tiempo real para la aplicación móvil **PréstamoLab CTMA**.

## 1. Requisitos e Instalación

```bash
cd backend
npm install
```

## 2. Configuración de Base de Datos (`.env`)

Archivo `backend/.env`:
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

- Al iniciar el servidor (`npm start` o `npm run dev`), se ejecuta automáticamente `sql/schema.sql`, se crean los dos usuarios iniciales de prueba (`ADMIN_EQUIPOS`: `admin@ctma.sena.edu.co` / `Admin123*` y `USUARIO`: `usuario@ctma.sena.edu.co` / `Usuario123*`) y se insertan los 8 equipos base del laboratorio si el catálogo está vacío.
- Si el servicio externo de MySQL (`3306`) se encuentra apagado o inaccesible en el equipo local, el backend activa automáticamente su motor relacional SQL embebido (`backend/data/prestamo_lab_ctma.sqlite`) con soporte transaccional completo.

## 3. Ejecución y Pruebas Automatizadas

- **Iniciar el servidor:**
  ```bash
  npm start
  ```
- **Ejecutar suite completa E2E (Auth, Roles, Catálogo, Préstamos, Chat y Auditoría):**
  ```bash
  npm test
  ```
- **Ejecutar prueba de concurrencia (dos usuarios pidiendo la última unidad al mismo tiempo):**
  ```bash
  npm run test:concurrency
  ```
