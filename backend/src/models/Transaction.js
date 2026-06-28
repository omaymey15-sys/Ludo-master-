const mongoose = require('mongoose');

const txSchema = new mongoose.Schema({
  user:          { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
  type:          { type: String, enum: ['deposit','withdraw','bet','prize','fee','refund'], required: true },
  amount:        { type: Number, required: true, min: 0 },
  currency:      { type: String, default: 'CDF' },
  status:        { type: String, enum: ['pending','success','failed','cancelled'], default: 'pending' },

  // Unipay Congo
  unipayRef    : String,   // transaction_id retourné par Unipay
  operator     : {
    type: String,
    enum: ['orange','airtel','afrimoney','vodacash',''],
    default: ''
  },
  momoPhone    : String,   // numéro mobile money de destination (format E.164)
  meta         : {
    fee      : Number,     // frais Unipay (4%)
    netAmount: Number,     // montant net crédité après frais
    unipayOp : String,     // code opérateur exact envoyé à Unipay
  },

  // Lié à une compétition
  competition:   { type: mongoose.Schema.Types.ObjectId, ref: 'Competition' },

  // Soldes avant/après
  balanceBefore: Number,
  balanceAfter:  Number,

  description:   String,
  failReason:    String,
  callbackData:  mongoose.Schema.Types.Mixed,   // payload brut Unipay
  createdAt:     { type: Date, default: Date.now },
}, { timestamps: true });

txSchema.index({ user: 1, createdAt: -1 });
txSchema.index({ unipayRef: 1 });
txSchema.index({ status: 1 });

module.exports = mongoose.model('Transaction', txSchema);
