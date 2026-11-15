require('dotenv').config();
const http = require('http');
const path = require('path');
const express = require('express');
const cors = require('cors');
const { initDatabase } = require('./config/db');
const { initSocketServer } = require('./socket/socketServer');

const authRoutes = require('./routes/authRoutes');
const equiposRoutes = require('./routes/equiposRoutes');
const prestamosRoutes = require('./routes/prestamosRoutes');
const chatRoutes = require('./routes/chatRoutes');

const app = express();
const server = http.createServer(app);

app.use(cors());
app.use(express.json({ limit: '2mb' }));
app.use(express.urlencoded({ extended: true }));

// Servir archivos subidos (imágenes de catálogo, adjuntos y audios del chat)
app.use('/uploads', express.static(path.join(__dirname, '../uploads')));

// Healthcheck
app.get('/api/health', (_req, res) => {
  res.json({ status: 'ok', service: 'PrestamoLab CTMA Backend', timestamp: new Date().toISOString() });
});

// Rutas de la API
app.use('/api', authRoutes);
app.use('/api', equiposRoutes);
app.use('/api', prestamosRoutes);
app.use('/api', chatRoutes);

// Manejador global de errores
app.use((err, _req, res, _next) => {
  console.error('[Unhandled Error]', err);
  res.status(500).json({ error: err.message || 'Error interno del servidor' });
});

initSocketServer(server);

const PORT = Number(process.env.PORT || 3000);

async function startServer(port = PORT) {
  await initDatabase();
  return new Promise((resolve) => {
    server.listen(port, '0.0.0.0', () => {
      console.log(`[Backend] PréstamoLab CTMA escuchando en http://localhost:${port}`);
      resolve(server);
    });
  });
}

if (require.main === module) {
  startServer().catch((err) => {
    console.error('[Backend] Fallo al iniciar el servidor:', err);
    process.exit(1);
  });
}

module.exports = { app, server, startServer };
