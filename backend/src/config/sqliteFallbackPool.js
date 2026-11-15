const { DatabaseSync } = require('node:sqlite');
const fs = require('fs');
const path = require('path');

/**
 * Pool compatible con la interfaz de `mysql2/promise` respaldado por `node:sqlite`
 * con soporte real para transacciones y bloqueo exclusivo equivalente a `SELECT ... FOR UPDATE`.
 * Se activa automáticamente cuando el demonio externo de MySQL (3306) no está disponible.
 */
class SqliteFallbackPool {
  constructor(dbFilePath) {
    const dir = path.dirname(dbFilePath);
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
    this.db = new DatabaseSync(dbFilePath);
    this.db.exec('PRAGMA foreign_keys = ON;');
    this.db.exec('PRAGMA journal_mode = WAL;');
    this._txQueue = Promise.resolve();
    this._initSchema();
  }

  _initSchema() {
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS usuarios (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        nombre TEXT NOT NULL,
        correo TEXT NOT NULL UNIQUE,
        password_hash TEXT NOT NULL,
        rol TEXT NOT NULL DEFAULT 'USUARIO',
        password_temporal INTEGER NOT NULL DEFAULT 0,
        creado_por INTEGER NULL,
        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
      );

      CREATE TABLE IF NOT EXISTS equipos (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        nombre TEXT NOT NULL,
        categoria TEXT NOT NULL,
        descripcion TEXT NOT NULL,
        imagen_url TEXT NULL,
        stock_total INTEGER NOT NULL DEFAULT 1,
        stock_disponible INTEGER NOT NULL DEFAULT 1,
        eliminado INTEGER NOT NULL DEFAULT 0,
        eliminado_at TEXT NULL,
        version INTEGER NOT NULL DEFAULT 1,
        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
      );

      CREATE TABLE IF NOT EXISTS prestamos (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        codigo_certificado TEXT NOT NULL UNIQUE,
        usuario_id INTEGER NOT NULL,
        equipo_id INTEGER NULL,
        equipo_nombre_snapshot TEXT NOT NULL,
        equipo_categoria_snapshot TEXT NOT NULL DEFAULT 'OTRO',
        cantidad INTEGER NOT NULL DEFAULT 1,
        nombre_solicitante TEXT NOT NULL,
        documento_solicitante TEXT NOT NULL DEFAULT '',
        ambiente_destino TEXT NOT NULL,
        motivo TEXT NOT NULL,
        fecha_prestamo TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
        fecha_devolucion_estimada TEXT NOT NULL,
        fecha_cierre TEXT NULL,
        estado TEXT NOT NULL DEFAULT 'ACTIVO',
        evidencia_url TEXT NULL,
        gestionado_por INTEGER NULL,
        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
        FOREIGN KEY (equipo_id) REFERENCES equipos(id) ON DELETE SET NULL
      );

      CREATE TABLE IF NOT EXISTS chats (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        prestamo_id INTEGER NOT NULL UNIQUE,
        solo_lectura INTEGER NOT NULL DEFAULT 0,
        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (prestamo_id) REFERENCES prestamos(id) ON DELETE CASCADE
      );

      CREATE TABLE IF NOT EXISTS chat_mensajes (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        chat_id INTEGER NOT NULL,
        remitente_id INTEGER NOT NULL,
        remitente_nombre TEXT NOT NULL,
        remitente_rol TEXT NOT NULL,
        tipo TEXT NOT NULL DEFAULT 'TEXTO',
        contenido TEXT NOT NULL,
        archivo_url TEXT NULL,
        archivo_nombre TEXT NULL,
        archivo_mime TEXT NULL,
        archivo_tamano INTEGER NULL,
        duracion_seg INTEGER NULL,
        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
      );

      CREATE TABLE IF NOT EXISTS chat_lecturas (
        chat_id INTEGER NOT NULL,
        usuario_id INTEGER NOT NULL,
        ultimo_mensaje_leido_id INTEGER NOT NULL DEFAULT 0,
        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (chat_id, usuario_id),
        FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
      );

      CREATE TABLE IF NOT EXISTS auditoria_logs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        usuario_id INTEGER NULL,
        usuario_nombre TEXT NOT NULL,
        usuario_rol TEXT NOT NULL,
        accion TEXT NOT NULL,
        entidad TEXT NOT NULL,
        entidad_id INTEGER NULL,
        detalle TEXT NULL,
        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
      );
    `);
  }

  _normalizeSql(sql) {
    let s = sql.trim();
    // Quitar FOR UPDATE (el bloqueo se maneja por mutex transaccional)
    s = s.replace(/\s+FOR\s+UPDATE\b/gi, '');
    // Reemplazar NOW() por CURRENT_TIMESTAMP
    s = s.replace(/\bNOW\(\)/gi, 'CURRENT_TIMESTAMP');
    // Reemplazar ON DUPLICATE KEY UPDATE de chat_lecturas por sintaxis SQLite ON CONFLICT
    if (/ON\s+DUPLICATE\s+KEY\s+UPDATE/i.test(s)) {
      s = `
        INSERT INTO chat_lecturas (chat_id, usuario_id, ultimo_mensaje_leido_id)
        VALUES (?, ?, ?)
        ON CONFLICT(chat_id, usuario_id)
        DO UPDATE SET
          ultimo_mensaje_leido_id = MAX(chat_lecturas.ultimo_mensaje_leido_id, excluded.ultimo_mensaje_leido_id),
          updated_at = CURRENT_TIMESTAMP
      `;
    }
    return s;
  }

  _normalizeParams(params = []) {
    return params.map((p) => {
      if (p instanceof Date) return p.toISOString();
      if (typeof p === 'boolean') return p ? 1 : 0;
      if (p === undefined) return null;
      return p;
    });
  }

  _executeSync(sql, params = []) {
    const normSql = this._normalizeSql(sql);
    const normParams = this._normalizeParams(params);
    const stmt = this.db.prepare(normSql);

    if (/^\s*(SELECT|PRAGMA)/i.test(normSql)) {
      const rows = stmt.all(...normParams);
      return [rows, []];
    } else {
      const info = stmt.run(...normParams);
      return [
        {
          insertId: Number(info.lastInsertRowid || 0),
          affectedRows: Number(info.changes || 0)
        },
        []
      ];
    }
  }

  async query(sql, params = []) {
    // Pequeño yield al event loop para simular I/O de red real en llamadas concurrentes
    await new Promise((r) => setImmediate(r));
    return this._executeSync(sql, params);
  }

  async getConnection() {
    let releaseMutex = null;
    const waitForTurn = new Promise((resolve) => {
      this._txQueue = this._txQueue.then(() => {
        return new Promise((rel) => {
          releaseMutex = rel;
          resolve();
        });
      });
    });

    await waitForTurn;
    let inTx = false;
    let released = false;

    const unlock = () => {
      if (!released && releaseMutex) {
        released = true;
        releaseMutex();
      }
    };

    return {
      beginTransaction: async () => {
        await new Promise((r) => setImmediate(r));
        if (!inTx) {
          this.db.exec('BEGIN IMMEDIATE TRANSACTION;');
          inTx = true;
        }
      },
      query: async (sql, params = []) => {
        await new Promise((r) => setImmediate(r));
        return this._executeSync(sql, params);
      },
      commit: async () => {
        if (inTx) {
          this.db.exec('COMMIT;');
          inTx = false;
        }
      },
      rollback: async () => {
        if (inTx) {
          try {
            this.db.exec('ROLLBACK;');
          } catch (_e) {
            // ignore
          }
          inTx = false;
        }
      },
      release: () => {
        if (inTx) {
          try {
            this.db.exec('ROLLBACK;');
          } catch (_e) {
            // ignore
          }
          inTx = false;
        }
        unlock();
      }
    };
  }

  async end() {
    try {
      this.db.close();
    } catch (_e) {
      // ignore
    }
  }
}

module.exports = { SqliteFallbackPool };
