const mongoose = require('mongoose');
const bcrypt   = require('bcryptjs');

const userSchema = new mongoose.Schema({

  // ── Identité ───────────────────────────────────────────────
  username: {
    type      : String,
    required  : true,
    unique    : true,
    trim      : true,
    minlength : 3,
    maxlength : 20,
  },
  email: {
    type      : String,
    required  : true,
    unique    : true,
    lowercase : true,
    trim      : true,
  },
  // select:false → jamais retourné par défaut.
  // Pour l'auth, utilisez : User.findOne({ email }).select('+password')
  password: {
    type      : String,
    required  : true,
    minlength : 8,
    select    : false,
  },
  phone: {
    type     : String,
    required : true,
    unique   : true,
    trim     : true,
  },
  avatar : { type: String, default: '😊' },
  role   : { type: String, enum: ['player', 'admin'], default: 'player' },

  // ── Portefeuille ───────────────────────────────────────────
  wallet: {
    // FIX #4 : min:0 supprimé — géré côté code métier pour éviter
    // les échecs silencieux en cas de race condition
    balance     : { type: Number, default: 0 },
    currency    : { type: String, default: 'CDF' },
    lastDeposit : { type: Date },
    lastWithdraw: { type: Date },
  },

  // ── Statistiques ──────────────────────────────────────────
  stats: {
    gamesPlayed : { type: Number, default: 0 },
    gamesWon    : { type: Number, default: 0 },
    totalEarned : { type: Number, default: 0 },
    totalSpent  : { type: Number, default: 0 },
    winRate     : { type: Number, default: 0 },
  },

  // ── KYC minimal ───────────────────────────────────────────
  kyc: {
    verified  : { type: Boolean, default: false },
    // FIX #1 : '' retiré de l'enum — causait une erreur de validation
    // Mongoose quand l'opérateur n'était pas encore défini.
    // default: null → le champ est absent/null jusqu'à configuration.
    operator  : {
      type : String,
      enum : ['mpesa', 'orange_money', 'airtel_money', null],
      default: null,
    },
    momoNumber: { type: String, default: '' },
  },

  isActive  : { type: Boolean, default: true },
  lastLogin : { type: Date },

  // FIX #2 : createdAt supprimé ici — { timestamps: true } ci-dessous
  // gère automatiquement createdAt ET updatedAt.
  // Définir les deux causait un conflit et des erreurs de duplication.

}, {
  timestamps: true,  // génère automatiquement createdAt + updatedAt
});

// ── Index pour les requêtes admin ─────────────────────────────
// FIX #5 : index ajoutés pour les filtres fréquents
userSchema.index({ role: 1, createdAt: -1 });
userSchema.index({ isActive: 1 });
userSchema.index({ 'wallet.balance': -1 });

// ── Hash du mot de passe avant save ──────────────────────────
userSchema.pre('save', async function(next) {
  if (!this.isModified('password')) return next();
  try {
    this.password = await bcrypt.hash(this.password, 12);
    next();
  } catch (err) {
    next(err);
  }
});

// ── Vérification du mot de passe ─────────────────────────────
// FIX #3 : vérification défensive — si this.password est undefined
// (oubli de .select('+password')), renvoie false au lieu de crasher.
userSchema.methods.checkPassword = function(plain) {
  if (!this.password) {
    console.error('[User.checkPassword] password non chargé — utilisez .select("+password")');
    return Promise.resolve(false);
  }
  return bcrypt.compare(plain, this.password);
};

// ── Recalcul du winRate ───────────────────────────────────────
userSchema.methods.updateWinRate = function() {
  this.stats.winRate = this.stats.gamesPlayed > 0
    ? Math.round((this.stats.gamesWon / this.stats.gamesPlayed) * 100)
    : 0;
};

// ── Sécurité : ne jamais sérialiser le password ───────────────
userSchema.methods.toJSON = function() {
  const obj = this.toObject();
  delete obj.password;
  return obj;
};

module.exports = mongoose.model('User', userSchema);
