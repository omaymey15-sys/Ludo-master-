const mongoose = require('mongoose');
const bcrypt   = require('bcryptjs');

const userSchema = new mongoose.Schema({
  username: { type: String, required: true, unique: true, trim: true },
  email:    { type: String, required: true, unique: true, lowercase: true },
  password: { type: String, required: true, minlength: 8, select: false },
  phone:    { type: String, required: true, unique: true },
  avatar:   { type: String, default: '😊' },
  role:     { type: String, enum: ['player', 'admin'], default: 'player' },

  wallet: {
    balance:  { type: Number, default: 0 },
    currency: { type: String, default: 'CDF' }
  },

  stats: {
    gamesPlayed: { type: Number, default: 0 },
    gamesWon:    { type: Number, default: 0 },
    winRate:     { type: Number, default: 0 }
  },

  kyc: {
    verified:   { type: Boolean, default: false },
    operator:   { type: String, enum: ['mpesa','orange_money','airtel_money',''] , default: '' },
    momoNumber: { type: String, default: '' }
  },

  isActive:  { type: Boolean, default: true },
  lastLogin: Date
}, { timestamps: true });

// HASH PASSWORD
userSchema.pre('save', async function(next) {
  if (!this.isModified('password')) return next();
  this.password = await bcrypt.hash(this.password, 10);
  next();
});

// WINRATE AUTO
userSchema.pre('save', function(next) {
  if (this.stats.gamesPlayed > 0) {
    this.stats.winRate = Math.round(
      (this.stats.gamesWon / this.stats.gamesPlayed) * 100
    );
  }
  next();
});

// PASSWORD CHECK
userSchema.methods.checkPassword = async function(plain) {
  if (!this.password) return false;
  return bcrypt.compare(plain, this.password);
};

module.exports = mongoose.model('User', userSchema);
