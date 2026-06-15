require('dotenv').config();

const express    = require('express');
const mongoose   = require('mongoose');
const cors       = require('cors');
const helmet     = require('helmet');
const morgan     = require('morgan');
const rateLimit  = require('express-rate-limit');
const { Server } = require('socket.io');
const http       = require('http');

// ── Routes ────────────────────────────────────────────────────
const authRouter        = require('./routes/auth');
const paymentRouter     = require('./routes/payment');
const competitionRouter = require('./routes/competition');
const adminRouter       = require('./routes/admin');

const app    = express();
const server = http.createServer(app);
const io     = new Server(server, {
  cors: { origin: '*', methods: ['GET','POST'] }
});

// ── Trust proxy (requis sur Render, Railway, Heroku) ─────────
app.set('trust proxy', 1);

// ── Sécurité & logging ────────────────────────────────────────
app.use(helmet({ contentSecurityPolicy: false }));
app.use(cors({ origin: '*' }));
app.use(morgan(process.env.NODE_ENV === 'production' ? 'combined' : 'dev'));
app.use(express.json({ limit: '10kb' }));
app.use(express.urlencoded({ extended: true }));

// ── Rate limiting ─────────────────────────────────────────────
const globalLimiter = rateLimit({
  windowMs       : 15 * 60 * 1000,
  max            : 300,
  standardHeaders: true,
  legacyHeaders  : false,
  message        : { message: 'Trop de requêtes. Réessayez dans 15 min.' },
});
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max     : 20,
  message : { message: 'Trop de tentatives de connexion.' },
});
app.use('/api/', globalLimiter);
app.use('/api/auth/login',         authLimiter);
app.use('/api/auth/register',      authLimiter);
app.use('/api/auth/setup-admin',   authLimiter);

// ════════════════════════════════════════════════════════════════
//  RACINE — identifiant du serveur (pratique pour vérifier)
// ════════════════════════════════════════════════════════════════
app.get('/', (_, res) => res.json({
  name    : 'Ludo Master API',
  status  : 'running',
  version : '2.0.0',
  uptime  : Math.floor(process.uptime()),
  env     : process.env.NODE_ENV || 'development',
  db      : mongoose.connection.readyState === 1 ? 'connected' : 'disconnected',
  routes  : [
    'GET  /health',
    'POST /api/auth/register',
    'POST /api/auth/login',
    'GET  /api/auth/me',
    'GET  /api/auth/setup-status',
    'POST /api/auth/setup-admin',
    'POST /api/payment/deposit',
    'POST /api/payment/withdraw',
    'GET  /api/payment/history',
    'GET  /api/competitions',
    'POST /api/competitions/:id/join',
    'GET  /api/admin/dashboard',
    'GET  /api/admin/users',
    'POST /api/admin/competitions',
  ],
}));

// ════════════════════════════════════════════════════════════════
//  HEALTH CHECK (utilisé par Render pour le monitoring)
// ════════════════════════════════════════════════════════════════
app.get('/health', (_, res) => res.json({
  status : 'ok',
  db     : mongoose.connection.readyState === 1 ? 'connected' : 'disconnected',
  uptime : Math.floor(process.uptime()),
  ts     : new Date().toISOString(),
}));

// ════════════════════════════════════════════════════════════════
//  DEBUG — uniquement en développement
// ════════════════════════════════════════════════════════════════
if (process.env.NODE_ENV !== 'production') {
  app.get('/debug', (_, res) => res.json({
    env          : process.env,
    mongoState   : mongoose.connection.readyState,
    nodeVersion  : process.version,
    memoryMB     : Math.round(process.memoryUsage().heapUsed / 1024 / 1024),
  }));
}

// ════════════════════════════════════════════════════════════════
//  ROUTES API
// ════════════════════════════════════════════════════════════════
app.use('/api/auth',         authRouter);
app.use('/api/payment',      paymentRouter);
app.use('/api/competitions', competitionRouter);
app.use('/api/admin',        adminRouter);

// ════════════════════════════════════════════════════════════════
//  404
// ════════════════════════════════════════════════════════════════
app.use((req, res) => {
  res.status(404).json({
    message: `Route non trouvée : ${req.method} ${req.path}`,
    hint   : 'Consultez GET / pour la liste des routes disponibles',
  });
});

// ════════════════════════════════════════════════════════════════
//  GESTIONNAIRE D'ERREURS GLOBAL
// ════════════════════════════════════════════════════════════════
app.use((err, req, res, _next) => {
  console.error('[ERROR]', err.stack);
  res.status(err.status || 500).json({
    message: process.env.NODE_ENV === 'production'
      ? 'Erreur interne du serveur'
      : err.message,
    ...(process.env.NODE_ENV !== 'production' && { stack: err.stack }),
  });
});

// ════════════════════════════════════════════════════════════════
//  SOCKET.IO — Matchmaking temps-réel
// ════════════════════════════════════════════════════════════════
const rooms = new Map();

io.on('connection', (socket) => {
  console.log(`🔌 ${socket.id} connecté`);

  socket.on('join_room', ({ competitionId, userId, username, color }) => {
    socket.join(competitionId);
    if (!rooms.has(competitionId)) rooms.set(competitionId, { players: [], ready: 0 });
    const room = rooms.get(competitionId);
    if (!room.players.find(p => p.userId === userId))
      room.players.push({ socketId: socket.id, userId, username, color });
    io.to(competitionId).emit('room_update', { players: room.players });
  });

  socket.on('player_ready', ({ competitionId }) => {
    const room = rooms.get(competitionId);
    if (!room) return;
    room.ready = (room.ready || 0) + 1;
    if (room.ready >= room.players.length && room.players.length >= 2) {
      io.to(competitionId).emit('game_start', { players: room.players });
      room.ready = 0;
    }
  });

  socket.on('dice_roll',  ({ competitionId, userId, dice }) =>
    socket.to(competitionId).emit('opponent_dice', { userId, dice }));

  socket.on('piece_move', ({ competitionId, userId, pieceId, finalPos }) =>
    socket.to(competitionId).emit('opponent_move', { userId, pieceId, finalPos }));

  socket.on('game_over',  ({ competitionId, ranking, totalTurns }) => {
    io.to(competitionId).emit('game_result', { ranking, totalTurns });
    rooms.delete(competitionId);
  });

  socket.on('chat_msg', ({ competitionId, username, text }) =>
    io.to(competitionId).emit('chat_msg', { username, text, ts: Date.now() }));

  socket.on('disconnect', () => {
    console.log(`🔌 ${socket.id} déconnecté`);
    rooms.forEach((room, compId) => {
      room.players = room.players.filter(p => p.socketId !== socket.id);
      io.to(compId).emit('room_update', { players: room.players });
    });
  });
});

// ════════════════════════════════════════════════════════════════
//  KEEP-ALIVE pour Render Free Tier (évite le sleep après 15 min)
//  Ping toutes les 10 minutes
// ════════════════════════════════════════════════════════════════
function startKeepAlive(url) {
  const https = require('https');
  const http2 = require('http');
  setInterval(() => {
    const lib = url.startsWith('https') ? https : http2;
    lib.get(`${url}/health`, (res) => {
      console.log(`💓 Keep-alive ping → ${res.statusCode}`);
    }).on('error', (e) => {
      console.error('💔 Keep-alive erreur:', e.message);
    });
  }, 10 * 60 * 1000); // 10 minutes
}

// ════════════════════════════════════════════════════════════════
//  DÉMARRAGE
// ════════════════════════════════════════════════════════════════
const PORT = process.env.PORT || 3000;

if (!process.env.MONGO_URI) {
  console.error('❌ MONGO_URI manquant dans les variables d'environnement !');
  console.error('   → Allez dans Render Dashboard → Environment → ajoutez MONGO_URI');
  process.exit(1);
}

mongoose
  .connect(process.env.MONGO_URI, {
    serverSelectionTimeoutMS : 15000,
    socketTimeoutMS          : 45000,
    maxPoolSize              : 10,
  })
  .then(() => {
    console.log('✅ MongoDB connecté');
    server.listen(PORT, '0.0.0.0', () => {
      console.log(`🚀 Ludo Master API v2.0.0 — port ${PORT} [${process.env.NODE_ENV || 'dev'}]`);
      console.log(`   → Routes disponibles sur http://localhost:${PORT}/`);

      // Keep-alive uniquement en production
      if (process.env.NODE_ENV === 'production') {
        const selfUrl = process.env.RENDER_EXTERNAL_URL
                     || process.env.RAILWAY_STATIC_URL
                     || `https://ludo-master-apii.onrender.com`;
        startKeepAlive(selfUrl);
        console.log(`💓 Keep-alive actif → ${selfUrl}/health`);
      }
    });
  })
  .catch(err => {
    console.error('❌ MongoDB connexion échouée:', err.message);
    console.error('   Vérifiez MONGO_URI dans Render Dashboard → Environment');
    process.exit(1);
  });

// Gestion propre des arrêts
process.on('SIGTERM', () => {
  console.log('⚠️  SIGTERM — arrêt propre');
  server.close(() => { mongoose.connection.close(); process.exit(0); });
});
process.on('SIGINT', () => {
  server.close(() => { mongoose.connection.close(); process.exit(0); });
});

module.exports = { app, io };
