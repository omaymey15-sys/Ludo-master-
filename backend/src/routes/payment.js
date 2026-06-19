const express = require('express');
const { body, validationResult } = require('express-validator');
const router  = express.Router();

const wonyapay   = require('../services/wonyapay');
const User        = require('../models/User');
const Transaction = require('../models/Transaction');
const { auth }    = require('../middleware/auth');

// ── Validation ────────────────────────────────────────────────
const depositRules = [
  body('amount').isFloat({ min: Number(process.env.MIN_DEPOSIT||500) })
    .withMessage(`Montant minimum ${process.env.MIN_DEPOSIT||500} CDF`),
  body('phone').notEmpty().withMessage('Numéro requis'),
  body('operator').isIn(['mpesa','orange_money','airtel_money']).withMessage('Opérateur invalide'),
];
const withdrawRules = [
  body('amount').isFloat({ min: 200 }).withMessage('Minimum 200 CDF'),
  body('phone').notEmpty(),
  body('operator').isIn(['mpesa','orange_money','airtel_money']),
];

// ══════════════════════════════════════════════════════════════
//  POST /api/payment/deposit — Initier un dépôt
// ══════════════════════════════════════════════════════════════
router.post('/deposit', auth, depositRules, async (req, res) => {
  let tx; // déclaré ici pour être accessible dans le catch global

  try {
    const errors = validationResult(req);
    if (!errors.isEmpty()) return res.status(400).json({ errors: errors.array() });

    const { amount, phone, operator } = req.body;
    const user = await User.findById(req.user.id);

    if (!user) return res.status(404).json({ message: 'Utilisateur non trouvé' });
    if (amount > Number(process.env.MAX_DEPOSIT || 1000000))
      return res.status(400).json({ message: 'Montant trop élevé' });

    // Créer la transaction en attente
    tx = await Transaction.create({
      user:          user._id,
      type:          'deposit',
      amount,
      operator,
      momoPhone:     wonyapay.normalizePhone(phone),
      status:        'pending',
      balanceBefore: user.wallet.balance,
      description:   `Dépôt via ${operator}`,
    });

    const result = await wonyapay.initiateDeposit({
      userId:      user._id.toString(),
      phone,
      amount,
      operator,
      description: `Dépôt Ludo Master #${tx._id}`,
    });

    tx.wonyaRef     = result.wonyaRef;
    tx.wonyaOrderId = result.orderId;
    await tx.save();

    res.json({
      message:    'Demande de paiement envoyée',
      txId:       tx._id,
      wonyaRef:   result.wonyaRef,
      orderId:    result.orderId,
      status:     result.status,
      instruction:'Confirmez le paiement sur votre téléphone',
    });

  } catch (err) {
    // Marquer la transaction comme échouée si elle a été créée
    if (tx) {
      try { tx.status = 'failed'; tx.failReason = err.message; await tx.save(); }
      catch (saveErr) { console.error('Impossible de marquer la tx en failed:', saveErr.message); }
    }

    console.error('[POST /payment/deposit] Erreur:', err.message);

    // 503 = service WonyaPay indisponible/non configuré (erreur claire)
    // 502 = erreur réseau imprévue
    const status = err.status || 502;
    res.status(status).json({
      message: status === 503
        ? err.message
        : `Erreur paiement : ${err.message}`,
    });
  }
});

// ══════════════════════════════════════════════════════════════
//  POST /api/payment/withdraw — Initier un retrait
// ══════════════════════════════════════════════════════════════
router.post('/withdraw', auth, withdrawRules, async (req, res) => {
  let tx, user, before;

  try {
    const errors = validationResult(req);
    if (!errors.isEmpty()) return res.status(400).json({ errors: errors.array() });

    // FIX : vérifier la config WonyaPay AVANT de toucher au solde,
    // pour ne jamais débiter un compte si le service est indisponible.
    wonyapay.assertConfigured?.();

    const { amount, phone, operator } = req.body;
    user = await User.findById(req.user.id);
    if (!user) return res.status(404).json({ message: 'Utilisateur non trouvé' });

    if (user.wallet.balance < amount)
      return res.status(400).json({ message: 'Solde insuffisant' });

    // Bloquer le solde immédiatement
    before = user.wallet.balance;
    user.wallet.balance -= amount;
    user.wallet.lastWithdraw = new Date();
    await user.save();

    tx = await Transaction.create({
      user:          user._id,
      type:          'withdraw',
      amount,
      operator,
      momoPhone:     wonyapay.normalizePhone(phone),
      status:        'pending',
      balanceBefore: before,
      balanceAfter:  user.wallet.balance,
      description:   `Retrait via ${operator}`,
    });

    const result = await wonyapay.initiateWithdraw({
      userId: user._id.toString(),
      phone, amount, operator,
      description: `Gains Ludo Master #${tx._id}`,
    });

    tx.wonyaRef     = result.wonyaRef;
    tx.wonyaOrderId = result.orderId;
    tx.status       = 'pending';
    await tx.save();

    res.json({
      message:    'Retrait en cours',
      txId:       tx._id,
      wonyaRef:   result.wonyaRef,
      amount,
      newBalance: user.wallet.balance,
    });

  } catch (err) {
    // Rembourser uniquement si le solde a effectivement été débité
    if (user && before !== undefined) {
      try {
        user.wallet.balance = before;
        await user.save();
      } catch (saveErr) {
        console.error('Échec remboursement après erreur withdraw:', saveErr.message);
      }
    }
    if (tx) {
      try { tx.status = 'failed'; tx.failReason = err.message; await tx.save(); }
      catch (saveErr) { console.error('Impossible de marquer la tx en failed:', saveErr.message); }
    }

    console.error('[POST /payment/withdraw] Erreur:', err.message);

    const status = err.status || 502;
    res.status(status).json({
      message: status === 503
        ? err.message
        : `Erreur retrait : ${err.message}`,
    });
  }
});

// ══════════════════════════════════════════════════════════════
//  POST /api/payment/callback/deposit — Webhook WonyaPay dépôt
// ══════════════════════════════════════════════════════════════
router.post('/callback/deposit', async (req, res) => {
  const body = req.body;
  const sig  = req.headers['x-wonyapay-signature'] || body.signature;

  // Vérifier la signature
  if (!wonyapay.verifyCallback(body, sig)) {
    return res.status(401).json({ message: 'Signature invalide' });
  }

  const tx = await Transaction.findOne({ wonyaRef: body.reference });
  if (!tx) return res.status(404).json({ message: 'Transaction inconnue' });
  if (tx.status !== 'pending') return res.json({ message: 'Déjà traité' });

  tx.callbackData = body;

  if (body.status === 'success') {
    const user = await User.findById(tx.user);
    if (user) {
      tx.balanceBefore    = user.wallet.balance;
      user.wallet.balance += tx.amount;
      user.wallet.lastDeposit = new Date();
      user.stats.totalSpent  += tx.amount;
      await user.save();
      tx.balanceAfter = user.wallet.balance;
    }
    tx.status = 'success';
  } else {
    tx.status    = 'failed';
    tx.failReason = body.reason || 'Échec côté opérateur';
  }

  await tx.save();
  res.json({ message: 'OK' });
});

// ══════════════════════════════════════════════════════════════
//  POST /api/payment/callback/withdraw — Webhook WonyaPay retrait
// ══════════════════════════════════════════════════════════════
router.post('/callback/withdraw', async (req, res) => {
  const body = req.body;
  const sig  = req.headers['x-wonyapay-signature'] || body.signature;

  if (!wonyapay.verifyCallback(body, sig))
    return res.status(401).json({ message: 'Signature invalide' });

  const tx = await Transaction.findOne({ wonyaRef: body.reference });
  if (!tx) return res.status(404).json({ message: 'Transaction inconnue' });
  if (tx.status !== 'pending') return res.json({ message: 'Déjà traité' });

  tx.callbackData = body;
  if (body.status === 'success') {
    tx.status = 'success';
    const user = await User.findById(tx.user);
    if (user) { user.stats.totalEarned += tx.amount; await user.save(); }
  } else {
    // Échec du retrait → rembourser le solde
    tx.status = 'failed'; tx.failReason = body.reason;
    const user = await User.findById(tx.user);
    if (user) { user.wallet.balance += tx.amount; await user.save(); }
  }

  await tx.save();
  res.json({ message: 'OK' });
});

// ══════════════════════════════════════════════════════════════
//  GET /api/payment/status/:wonyaRef — Vérifier le statut
// ══════════════════════════════════════════════════════════════
router.get('/status/:ref', auth, async (req, res) => {
  const tx = await Transaction.findOne({ wonyaRef: req.params.ref, user: req.user.id });
  if (!tx) return res.status(404).json({ message: 'Transaction non trouvée' });
  res.json({ status: tx.status, amount: tx.amount, type: tx.type, createdAt: tx.createdAt });
});

// GET /api/payment/history — Historique des transactions
router.get('/history', auth, async (req, res) => {
  const page  = parseInt(req.query.page)  || 1;
  const limit = parseInt(req.query.limit) || 20;
  const txs   = await Transaction
    .find({ user: req.user.id })
    .sort({ createdAt: -1 })
    .skip((page-1)*limit)
    .limit(limit)
    .select('-callbackData -__v');
  const total = await Transaction.countDocuments({ user: req.user.id });
  res.json({ transactions: txs, page, total, pages: Math.ceil(total/limit) });
});

module.exports = router;
      
