require('dotenv').config();
const express    = require('express');
const mongoose   = require('mongoose');
const cors       = require('cors');
const helmet     = require('helmet');
const morgan     = require('morgan');
const rateLimit  = require('express-rate-limit');
const { Server } = require('socket.io');
const http       = require('http');
const { startKeepAlive } = require('./utils/keepAlive');

const authRouter        = require('./routes/auth');
const paymentRouter     = require('./routes/payment');
const competitionRouter = require('./routes/competition');
const adminRouter       = require('./routes/admin');

const app    = express();
const server = http.createServer(app);
const io     = new Server(server, {
  cors: { origin: '*', methods: ['GET', 'POST'] }
});

// ── Trust proxy (requis sur Render) ──────────────────────────
app.set('trust proxy', 1);

// ── Middlewares ───────────────────────────────────────────────
app.use(helmet({ contentSecurityPolicy: false }));
app.use(cors({
  origin: process.env.ALLOWED_ORIGINS
    ? process.env.ALLOWED_ORIGINS.split(',')
    : '*'
}));
app.use(morgan(process.env.NODE_ENV === 'production' ? 'combined' : 'dev'));
app.use(express.json({ limit: '10kb' }));
app.use(express.urlencoded({ extended: true }));

// Rate limiting
app.use('/api/', rateLimit({
  windowMs : 15 * 60 * 1000,
  max      : 200,
  standardHeaders: true,
  legacyHeaders  : false,
  message  : { message: 'Trop de requêtes. Réessayez dans 15 min.' }
}));
app.use('/api/auth/', rateLimit({
  windowMs : 15 * 60 * 1000,
  max      : 15,
  message  : { message: 'Trop de tentatives de connexion.' }
}));

// ── Health check (utilisé par Render + keep-alive) ────────────
app.get('/health', (_, res) => res.json({
  status  : 'ok',
  env     : process.env.NODE_ENV,
  uptime  : Math.floor(process.uptime()),
  ts      : new Date().toISOString()
}));

// ── Routes API ────────────────────────────────────────────────
app.use('/api/auth',         authRouter);
app.use('/api/payment',      paymentRouter);
app.use('/api/competitions', competitionRouter);
app.use('/api/admin',        adminRouter);

// 404
app.use((req, res) =>
  res.status(404).json({ message: `Route ${req.path} non trouvée` })
);

// Gestionnaire d'erreurs global
app.use((err, req, res, _next) => {
  console.error('[ERROR]', err.stack);
  res.status(err.status || 500).json({
    message: process.env.NODE_ENV === 'production'
      ? 'Erreur interne du serveur'
      : err.message
  });
});

// ── Socket.IO — Matchmaking temps-réel ───────────────────────
const rooms = new Map();

io.on('connection', (socket) => {
  console.log(`🔌 ${socket.id} connecté`);

  socket.on('join_room', ({ competitionId, userId, username, color }) => {
    socket.join(competitionId);
    if (!rooms.has(competitionId)) rooms.set(competitionId, { players: [], ready: 0 });
    const room = rooms.get(competitionId);
    if (!room.players.find(p => p.userId === userId)) {
      room.players.push({ socketId: socket.id, userId, username, color });
    }
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

  socket.on('dice_roll', ({ competitionId, userId, dice }) =>
    socket.to(competitionId).emit('opponent_dice', { userId, dice }));

  socket.on('piece_move', ({ competitionId, userId, pieceId, finalPos }) =>
    socket.to(competitionId).emit('opponent_move', { userId, pieceId, finalPos }));

  socket.on('game_over', ({ competitionId, ranking, totalTurns }) => {
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

// ── Démarrage ─────────────────────────────────────────────────
const PORT = process.env.PORT || 3000;

mongoose
  .connect(process.env.MONGO_URI, {
    serverSelectionTimeoutMS: 10000,
    socketTimeoutMS         : 45000,
  })
  .then(() => {
    console.log('✅ MongoDB connecté');
    server.listen(PORT, '0.0.0.0', () => {
      console.log(`🚀 Ludo Master API — port ${PORT} [${process.env.NODE_ENV}]`);
      // Démarrer le keep-alive (évite le sleep sur Render Free)
      const selfUrl = process.env.RENDER_EXTERNAL_URL
                   || `http://localhost:${PORT}`;
      startKeepAlive(selfUrl);
    });
  })
  .catch(err => {
    console.error('❌ MongoDB erreur:', err.message);
    process.exit(1);
  });

// Gestion propre des arrêts
process.on('SIGTERM', () => {
  console.log('⚠️  SIGTERM reçu — arrêt propre');
  server.close(() => { mongoose.connection.close(); process.exit(0); });
});

module.exports = { app, io };
