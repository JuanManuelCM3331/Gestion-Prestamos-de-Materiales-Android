-- ============================================================================
-- Esquema Relacional MySQL: PréstamoLab CTMA
-- Motor: InnoDB (Transaccional con soporte para SELECT ... FOR UPDATE)
-- ============================================================================

CREATE DATABASE IF NOT EXISTS prestamo_lab_ctma
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE prestamo_lab_ctma;

-- 1. Tabla de Usuarios y Roles (ADMIN_EQUIPOS | USUARIO)
CREATE TABLE IF NOT EXISTS usuarios (
  id INT AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(120) NOT NULL,
  correo VARCHAR(150) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  rol ENUM('ADMIN_EQUIPOS', 'USUARIO') NOT NULL DEFAULT 'USUARIO',
  password_temporal TINYINT(1) NOT NULL DEFAULT 0,
  creado_por INT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_usuarios_creado_por FOREIGN KEY (creado_por) REFERENCES usuarios(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- 2. Tabla de Catálogo de Equipos (con control de existencias y eliminación lógica)
CREATE TABLE IF NOT EXISTS equipos (
  id INT AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(150) NOT NULL,
  categoria ENUM('ELECTRONICA', 'MULTIMEDIA', 'INFORMATICA', 'HERRAMIENTAS', 'OTRO') NOT NULL,
  descripcion TEXT NOT NULL,
  imagen_url VARCHAR(500) NULL,
  stock_total INT NOT NULL DEFAULT 1,
  stock_disponible INT NOT NULL DEFAULT 1,
  eliminado TINYINT(1) NOT NULL DEFAULT 0,
  eliminado_at DATETIME NULL,
  version INT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_equipos_eliminado_categoria (eliminado, categoria),
  INDEX idx_equipos_nombre (nombre)
) ENGINE=InnoDB;

-- 3. Tabla de Préstamos
CREATE TABLE IF NOT EXISTS prestamos (
  id INT AUTO_INCREMENT PRIMARY KEY,
  codigo_certificado VARCHAR(40) NOT NULL UNIQUE,
  usuario_id INT NOT NULL,
  equipo_id INT NULL,
  equipo_nombre_snapshot VARCHAR(150) NOT NULL,
  equipo_categoria_snapshot VARCHAR(50) NOT NULL DEFAULT 'OTRO',
  cantidad INT NOT NULL DEFAULT 1,
  nombre_solicitante VARCHAR(150) NOT NULL,
  documento_solicitante VARCHAR(60) NOT NULL DEFAULT '',
  ambiente_destino VARCHAR(150) NOT NULL,
  motivo VARCHAR(500) NOT NULL,
  fecha_prestamo DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_devolucion_estimada DATETIME NOT NULL,
  fecha_cierre DATETIME NULL,
  estado ENUM('ACTIVO', 'ENTREGADO', 'CANCELADO') NOT NULL DEFAULT 'ACTIVO',
  evidencia_url VARCHAR(500) NULL,
  gestionado_por INT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_prestamos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
  CONSTRAINT fk_prestamos_equipo FOREIGN KEY (equipo_id) REFERENCES equipos(id) ON DELETE SET NULL,
  CONSTRAINT fk_prestamos_admin FOREIGN KEY (gestionado_por) REFERENCES usuarios(id) ON DELETE SET NULL,
  INDEX idx_prestamos_usuario_estado (usuario_id, estado),
  INDEX idx_prestamos_equipo_estado (equipo_id, estado),
  INDEX idx_prestamos_estado (estado)
) ENGINE=InnoDB;

-- 4. Tabla de Salas de Chat por Préstamo
CREATE TABLE IF NOT EXISTS chats (
  id INT AUTO_INCREMENT PRIMARY KEY,
  prestamo_id INT NOT NULL UNIQUE,
  solo_lectura TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_chats_prestamo FOREIGN KEY (prestamo_id) REFERENCES prestamos(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 5. Tabla de Mensajes del Chat (TEXTO, IMAGEN, ARCHIVO, AUDIO)
CREATE TABLE IF NOT EXISTS chat_mensajes (
  id INT AUTO_INCREMENT PRIMARY KEY,
  chat_id INT NOT NULL,
  remitente_id INT NOT NULL,
  remitente_nombre VARCHAR(120) NOT NULL,
  remitente_rol ENUM('ADMIN_EQUIPOS', 'USUARIO') NOT NULL,
  tipo ENUM('TEXTO', 'IMAGEN', 'ARCHIVO', 'AUDIO') NOT NULL DEFAULT 'TEXTO',
  contenido TEXT NOT NULL,
  archivo_url VARCHAR(500) NULL,
  archivo_nombre VARCHAR(255) NULL,
  archivo_mime VARCHAR(120) NULL,
  archivo_tamano INT NULL,
  duracion_seg INT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_mensajes_chat FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE,
  CONSTRAINT fk_mensajes_remitente FOREIGN KEY (remitente_id) REFERENCES usuarios(id) ON DELETE CASCADE,
  INDEX idx_mensajes_chat_id (chat_id, id)
) ENGINE=InnoDB;

-- 6. Tabla de Control de Lectura de Mensajes por Participante
CREATE TABLE IF NOT EXISTS chat_lecturas (
  chat_id INT NOT NULL,
  usuario_id INT NOT NULL,
  ultimo_mensaje_leido_id INT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (chat_id, usuario_id),
  CONSTRAINT fk_lecturas_chat FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE,
  CONSTRAINT fk_lecturas_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 7. Tabla de Registro de Auditoría
CREATE TABLE IF NOT EXISTS auditoria_logs (
  id INT AUTO_INCREMENT PRIMARY KEY,
  usuario_id INT NULL,
  usuario_nombre VARCHAR(120) NOT NULL,
  usuario_rol VARCHAR(40) NOT NULL,
  accion VARCHAR(80) NOT NULL,
  entidad VARCHAR(60) NOT NULL,
  entidad_id INT NULL,
  detalle TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_auditoria_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE SET NULL,
  INDEX idx_auditoria_fecha (created_at DESC)
) ENGINE=InnoDB;
