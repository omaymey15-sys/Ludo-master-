const mongoose = require('mongoose');

const txSchema = new mongoose.Schema({
  user:          { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
  type:          { type: String, enum: ['deposit','withdraw','bet','prize','fee','refund'], required: true },
  amount:        { type: Number, required: true, min: 0 },
  currency:      { type: String, default: 'CDF' },
  status:        { type: String, enum: ['pending','success','failed','cancelled'], default: 'pending' },

  // WonyaPay
  wonyaRef:      String,   // référence transaction WonyaPay
  wonyaOrderId:  String,   // order_id interne WonyaPay
  operator:      { type: String, enum: ['mpesa','orange_money','airtel_money',''] },
  momoPhone:     String,   // numéro mobile money utilisé

  // Lié à une compétition
  competition:   { type: mongoose.Schema.Types.ObjectId, ref: 'Competition' },

  // Soldes avant/après
  balanceBefore: Number,
  balanceAfter:  Number,

  description:   String,
  failReason:    String,
  callbackData:  mongoose.Schema.Types.Mixed,   // payload brut WonyaPay
  createdAt:     { type: Date, default: Date.now },
}, { timestamps: true });

txSchema.index({ user: 1, createdAt: -1 });
txSchema.index({ wonyaRef: 1 });
txSchema.index({ status: 1 });

module.exports = mongoose.model('Transaction', txSchema);
