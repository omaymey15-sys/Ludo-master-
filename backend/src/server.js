require('dotenv').config();

const express    = require('express');
const mongoose   = require('mongoose');
const cors       = require('cors');
const helmet     = require('helmet');
const morgan     = require('morgan');
const rateLimit  = require('express-rate-limit');
const http       = require('http');
const { Server } = require('socket.io');

const { startKeepAlive } = require('./utils/keepAlive');

// Routes
const authRouter        = require('./routes/auth');
const paymentRouter     = require('./routes/payment');
const competitionRouter = require('./routes/competition');
const adminRouter       = require('./routes/admin');

const app = express();
const server = http.createServer(app);

const io = new Server(server, {
  cors: { origin: '*', methods: ['GET', 'POST'] }
});

// ─────────────────────────────────────────────
// TRUST PROXY (Render obligatoire)
app.set('trust proxy', 1);

// ─────────────────────────────────────────────
// MIDDLEWARES
app.use(helmet({ contentSecurityPolicy: false }));

app.use(cors({
  origin: process.env.ALLOWED_ORIGINS
    ? process.env.ALLOWED_ORIGINS.split(',')
    : '*'
}));

app.use(morgan(process.env.NODE_ENV === 'production' ? 'combined' : 'dev'));

app.use(express.json({ limit: '10kb' }));
app.use(express.urlencoded({ extended: true }));

// Rate limit global API
app.use('/api/', rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 200
}));

app.use('/api/auth/', rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 15
}));

// ─────────────────────────────────────────────
// ROOT ROUTE (IMPORTANT)
app.get('/', (req, res) => {
  res.status(200).json({
    name: "Ludo Master API",
    status: "running",
    version: "1.0.0"
  });
});

// HEALTH CHECK
app.get('/health', (req, res) => {
  res.status(200).json({
    status: 'ok',
    uptime: process.uptime(),
    mongodb: mongoose.connection.readyState === 1 ? 'connected' : 'disconnected',
    time: new Date().toISOString()
  });
});

// ─────────────────────────────────────────────
// API ROUTES
app.use('/api/auth', authRouter);
app.use('/api/payment', paymentRouter);
app.use('/api/competitions', competitionRouter);
app.use('/api/admin', adminRouter);

// ─────────────────────────────────────────────
// SOCKET.IO (MATCHMAKING)
const rooms = new Map();

io.on('connection', (socket) => {
  console.log('🔌 connecté:', socket.id);

  socket.on('join_room', (data) => {
    const { competitionId, userId, username, color } = data;

    socket.join(competitionId);

    if (!rooms.has(competitionId)) {
      rooms.set(competitionId, { players: [], ready: 0 });
    }

    const room = rooms.get(competitionId);

    if (!room.players.find(p => p.userId === userId)) {
      room.players.push({ socketId: socket.id, userId, username, color });
    }

    io.to(competitionId).emit('room_update', room.players);
  });

  socket.on('player_ready', ({ competitionId }) => {
    const room = rooms.get(competitionId);
    if (!room) return;

    room.ready++;

    if (room.ready >= room.players.length && room.players.length >= 2) {
      io.to(competitionId).emit('game_start', {
        players: room.players
      });
      room.ready = 0;
    }
  });

  socket.on('dice_roll', (data) => {
    socket.to(data.competitionId).emit('opponent_dice', data);
  });

  socket.on('piece_move', (data) => {
    socket.to(data.competitionId).emit('opponent_move', data);
  });

  socket.on('game_over', (data) => {
    io.to(data.competitionId).emit('game_result', data);
    rooms.delete(data.competitionId);
  });

  socket.on('chat_msg', (data) => {
    io.to(data.competitionId).emit('chat_msg', {
      ...data,
      ts: Date.now()
    });
  });

  socket.on('disconnect', () => {
    console.log('❌ déconnecté:', socket.id);

    rooms.forEach((room, id) => {
      room.players = room.players.filter(p => p.socketId !== socket.id);
      io.to(id).emit('room_update', room.players);
    });
  });
});

// ─────────────────────────────────────────────
// 404 HANDLER
app.use((req, res) => {
  res.status(404).json({
    error: "NOT_FOUND",
    message: `Route ${req.method} ${req.path} non trouvée`
  });
});

// ─────────────────────────────────────────────
// ERROR HANDLER
app.use((err, req, res, next) => {
  console.error(err);

  res.status(err.status || 500).json({
    message: process.env.NODE_ENV === 'production'
      ? 'Erreur serveur'
      : err.message
  });
});

// ─────────────────────────────────────────────
// START SERVER
const PORT = process.env.PORT || 3000;

mongoose.connect(process.env.MONGO_URI, {
  serverSelectionTimeoutMS: 10000,
  socketTimeoutMS: 45000
})
.then(() => {
  console.log('✅ MongoDB connecté');

  server.listen(PORT, '0.0.0.0', () => {
    console.log(`🚀 API running on port ${PORT}`);

    const url = process.env.RENDER_EXTERNAL_URL
      || `http://localhost:${PORT}`;

    startKeepAlive(url);
  });
})
.catch(err => {
  console.error('❌ MongoDB error:', err.message);
  process.exit(1);
});

module.exports = { app, io };
