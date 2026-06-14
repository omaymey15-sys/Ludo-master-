const mongoose = require('mongoose');

const competitionSchema = new mongoose.Schema({
  // ── Config ────────────────────────────────────────────────
  title:       { type: String, required: true },
  description: String,
  createdBy:   { type: mongoose.Schema.Types.ObjectId, ref: 'User' },  // admin

  // ── Mise ──────────────────────────────────────────────────
  entryFee:    { type: Number, required: true, min: 0 },   // CDF par joueur
  maxPlayers:  { type: Number, default: 4, min: 2, max: 4 },
  prizePool:   { type: Number, default: 0 },   // calculé automatiquement
  platformFee: { type: Number, default: 0 },   // prélevé par la plateforme

  // ── Distribution des gains ────────────────────────────────
  // Ex: [60, 30, 10] = 1er 60%, 2e 30%, 3e 10%
  prizeDistribution: { type: [Number], default: [100] },

  // ── Joueurs inscrits ──────────────────────────────────────
  players: [{
    user:       { type: mongoose.Schema.Types.ObjectId, ref: 'User' },
    username:   String,
    avatar:     String,
    color:      { type: String, enum: ['RED','BLUE','GREEN','YELLOW'] },
    joinedAt:   { type: Date, default: Date.now },
    txDeposit:  { type: mongoose.Schema.Types.ObjectId, ref: 'Transaction' },
    rank:       Number,
    prize:      { type: Number, default: 0 },
    txPrize:    { type: mongoose.Schema.Types.ObjectId, ref: 'Transaction' },
  }],

  // ── Statut ────────────────────────────────────────────────
  status: {
    type: String,
    enum: ['draft','open','full','playing','finished','cancelled'],
    default: 'draft'
  },

  // ── Timing ────────────────────────────────────────────────
  openAt:      Date,
  startAt:     Date,
  endAt:       Date,

  // ── Résultat de la partie ─────────────────────────────────
  gameResult: {
    totalTurns: Number,
    ranking:    [{ username: String, color: String, rank: Number }],
  },

  // ── Options avancées ──────────────────────────────────────
  isPublic:    { type: Boolean, default: true },
  inviteCode:  String,   // pour tournois privés
  aiAllowed:   { type: Boolean, default: false },

}, { timestamps: true });

// Calculer le prize pool automatiquement
competitionSchema.pre('save', function(next) {
  if (this.isModified('players') || this.isModified('entryFee')) {
    const gross = this.entryFee * this.players.length;
    const feePct = parseFloat(process.env.PLATFORM_FEE_PERCENT || 10) / 100;
    this.platformFee = Math.floor(gross * feePct);
    this.prizePool   = gross - this.platformFee;
  }
  next();
});

competitionSchema.index({ status: 1, openAt: -1 });
competitionSchema.index({ 'players.user': 1 });

module.exports = mongoose.model('Competition', competitionSchema);
