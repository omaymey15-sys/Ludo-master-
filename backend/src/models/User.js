const mongoose = require('mongoose');
const bcrypt   = require('bcryptjs');

const userSchema = new mongoose.Schema({
  // ── Identité ───────────────────────────────────────────────
  username:    { type: String, required: true, unique: true, trim: true, minlength: 3, maxlength: 20 },
  email:       { type: String, required: true, unique: true, lowercase: true },
  password:    { type: String, required: true, minlength: 8, select: false },
  phone:       { type: String, required: true, unique: true }, // ex: +243812345678
  avatar:      { type: String, default: '😊' },
  role:        { type: String, enum: ['player', 'admin'], default: 'player' },

  // ── Portefeuille ───────────────────────────────────────────
  wallet: {
    balance:     { type: Number, default: 0, min: 0 },  // CDF
    currency:    { type: String, default: 'CDF' },
    lastDeposit: Date,
    lastWithdraw:Date,
  },

  // ── Statistiques ──────────────────────────────────────────
  stats: {
    gamesPlayed:  { type: Number, default: 0 },
    gamesWon:     { type: Number, default: 0 },
    totalEarned:  { type: Number, default: 0 },
    totalSpent:   { type: Number, default: 0 },
    winRate:      { type: Number, default: 0 },
  },

  // ── KYC minimal ───────────────────────────────────────────
  kyc: {
    verified:  { type: Boolean, default: false },
    operator:  { type: String, enum: ['mpesa','orange_money','airtel_money',''] , default: '' },
    momoNumber:{ type: String, default: '' },  // numéro mobile money vérifié
  },

  isActive:    { type: Boolean, default: true },
  createdAt:   { type: Date,    default: Date.now },
  lastLogin:   Date,
}, { timestamps: true });

// Hash password avant save
userSchema.pre('save', async function(next) {
  if (!this.isModified('password')) return next();
  this.password = await bcrypt.hash(this.password, 12);
  next();
});

userSchema.methods.checkPassword = function(plain) {
  return bcrypt.compare(plain, this.password);
};

// Recalcul winRate
userSchema.methods.updateWinRate = function() {
  this.stats.winRate = this.stats.gamesPlayed > 0
    ? Math.round((this.stats.gamesWon / this.stats.gamesPlayed) * 100)
    : 0;
};

module.exports = mongoose.model('User', userSchema);
