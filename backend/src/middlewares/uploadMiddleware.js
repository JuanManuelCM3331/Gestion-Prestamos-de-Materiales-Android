const multer = require('multer');
const path = require('path');
const fs = require('fs');

const MAX_MB = Number(process.env.MAX_FILE_SIZE_MB || 10);
const MAX_BYTES = MAX_MB * 1024 * 1024;

const ALLOWED_IMAGE_MIMES = new Set([
  'image/jpeg',
  'image/jpg',
  'image/png',
  'image/webp'
]);

const ALLOWED_FILE_MIMES = new Set([
  'application/pdf',
  'text/plain',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
]);

const ALLOWED_AUDIO_MIMES = new Set([
  'audio/mp4',
  'audio/mpeg',
  'audio/mp3',
  'audio/aac',
  'audio/x-m4a',
  'audio/m4a',
  'audio/ogg',
  'audio/wav',
  'audio/x-wav',
  'audio/3gpp',
  'audio/webm',
  'video/3gpp',
  'video/mp4' // Android MediaRecorder MPEG_4 a veces reporta video/mp4 o audio/mp4
]);

function ensureDir(dirPath) {
  if (!fs.existsSync(dirPath)) {
    fs.mkdirSync(dirPath, { recursive: true });
  }
}

function sanitizeFilename(name) {
  return name.replace(/[^a-zA-Z0-9._-]/g, '_');
}

function createStorage(subfolder) {
  const targetDir = path.join(__dirname, '../../uploads', subfolder);
  ensureDir(targetDir);

  return multer.diskStorage({
    destination: (_req, _file, cb) => cb(null, targetDir),
    filename: (_req, file, cb) => {
      const ext = path.extname(file.originalname || '').toLowerCase() || '.bin';
      const base = path.basename(file.originalname || 'archivo', ext);
      const unique = `${Date.now()}_${Math.round(Math.random() * 1e6)}_${sanitizeFilename(base)}${ext}`;
      cb(null, unique);
    }
  });
}

const uploadEquipoImage = multer({
  storage: createStorage('equipos'),
  limits: { fileSize: MAX_BYTES },
  fileFilter: (_req, file, cb) => {
    if (ALLOWED_IMAGE_MIMES.has(file.mimetype.toLowerCase())) {
      cb(null, true);
    } else {
      cb(new Error(`Tipo de imagen no permitido (${file.mimetype}). Permitidos: JPG, PNG, WEBP.`));
    }
  }
});

const uploadChatAttachment = multer({
  storage: createStorage('chat'),
  limits: { fileSize: MAX_BYTES },
  fileFilter: (_req, file, cb) => {
    const mime = (file.mimetype || '').toLowerCase();
    const ext = path.extname(file.originalname || '').toLowerCase();
    const validAudioExt = ['.m4a', '.mp3', '.aac', '.wav', '.ogg', '.3gp', '.mp4'].includes(ext);
    const validDocExt = ['.pdf', '.doc', '.docx', '.txt'].includes(ext);
    const validImgExt = ['.jpg', '.jpeg', '.png', '.webp'].includes(ext);

    if (
      ALLOWED_IMAGE_MIMES.has(mime) ||
      ALLOWED_FILE_MIMES.has(mime) ||
      ALLOWED_AUDIO_MIMES.has(mime) ||
      (mime === 'application/octet-stream' && (validAudioExt || validDocExt || validImgExt))
    ) {
      cb(null, true);
    } else {
      cb(
        new Error(
          `Tipo de archivo no permitido (${file.mimetype}). Máximo ${MAX_MB}MB. Permitidos: Imágenes (JPG, PNG, WEBP), Documentos (PDF, DOC, DOCX, TXT) y Audios (M4A, MP3, AAC, WAV, OGG, 3GP).`
        )
      );
    }
  }
});

function handleMulterError(err, _req, res, next) {
  if (!err) return next();
  if (err instanceof multer.MulterError) {
    if (err.code === 'LIMIT_FILE_SIZE') {
      return res.status(400).json({ error: `El archivo excede el tamaño máximo permitido de ${MAX_MB} MB.` });
    }
    return res.status(400).json({ error: `Error al subir archivo: ${err.message}` });
  }
  return res.status(400).json({ error: err.message || 'Error en la subida del archivo.' });
}

module.exports = {
  uploadEquipoImage,
  uploadChatAttachment,
  handleMulterError,
  MAX_MB
};
