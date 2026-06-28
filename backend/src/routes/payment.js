const express = require('express');
const { body, validationResult } = require('express-validator');
const router  = express.Router();

const unipay      = require('../services/unipay');
const User        = require('../models/User');
const Transaction = require('../models/Transaction');
const { auth, adminOnly } = require('../middleware/auth');

// ── Validation dépôt ──────────────────────────────────────────
const depositRules = [
  body('amount')
    .isFloat({ min: 500 })
    .withMessage('Montant minimum 500 CDF'),
  body('phone')
    .notEmpty().withMessage('Numéro Mobile Money requis'),
  body('operator')
    .isIn(['orange','airtel','afrimoney','vodacash',
           'orange_money','airtel_money','mpesa'])
    .withMessage('Opérateur invalide'),
];

// ── Validation retrait ────────────────────────────────────────
const withdrawRules = [
  body('amount')
    .isFloat({ min: 200 })
    .withMessage('Montant minimum 200 CDF'),
  body('phone')
    .notEmpty().withMessage('Numéro de réception requis'),
  body('operator')
    .isIn(['orange','airtel','afrimoney','vodacash',
           'orange_money','airtel_money','mpesa'])
    .withMessage('Opérateur invalide'),
];

// ══════════════════════════════════════════════════════════════
//  POST /api/payment/deposit
//  Initie un dépôt via Unipay Congo (C2B collect)
//  → L'utilisateur reçoit une demande sur son téléphone
//  → Il confirme → on poll le statut → solde crédité
// ══════════════════════════════════════════════════════════════
router.post('/deposit', auth, depositRules, async (req, res) => {
  let tx;
  try {
    const errors = validationResult(req);
    if (!errors.isEmpty())
      return res.status(400).json({ errors: errors.array() });

    const { phone, operator } = req.body;
    const amount = Math.floor(Number(req.body.amount));

    const user = await User.findById(req.user.id);
    if (!user) return res.status(404).json({ message: 'Utilisateur non trouvé' });

    // Mapper l'opérateur vers le code Unipay
    const unipayOp = unipay.mapOperator(operator);
    const { fee, netAmount } = unipay.calcFee(amount);

    // Créer la transaction en attente dans notre base
    tx = await Transaction.create({
      user         : user._id,
      type         : 'deposit',
      amount,                          // montant que l'utilisateur envoie
      operator     : unipayOp,
      momoPhone    : unipay.normalizePhone(phone),
      status       : 'pending',
      balanceBefore: user.wallet.balance,
      description  : `Dépôt ${unipayOp} ${amount} CDF`,
      meta         : { fee, netAmount, unipayOp },
    });

    // Appel Unipay Congo
    const result = await unipay.initiateDeposit({
      phone,
      amount,
      operator : unipayOp,
      reference: `LMP-${tx._id.toString().slice(-8).toUpperCase()}`,
    });

    // Sauvegarder l'ID de transaction Unipay
    tx.unipayRef = result.transactionId;
    await tx.save();

    res.status(201).json({
      success    : true,
      txId       : tx._id,
      unipayId   : result.transactionId,
      message    : `Demande envoyée — confirmez le paiement sur votre téléphone`,
      amount,
      fee        : result.fee,
      netAmount  : result.netAmount,
      operator   : unipayOp,
      phone      : unipay.normalizePhone(phone),
      info       : `Après confirmation, votre solde sera crédité de ${result.netAmount} CDF (montant - frais 4%)`,
    });

  } catch (err) {
    if (tx) {
      try { tx.status = 'failed'; tx.failReason = err.message; await tx.save(); }
      catch (_) {}
    }
    console.error('[POST /payment/deposit]', err.message);
    res.status(err.status || 502).json({
      message: err.status === 503
        ? err.message
        : `Erreur dépôt : ${err.message}`,
    });
  }
});

// ══════════════════════════════════════════════════════════════
//  GET /api/payment/deposit/:txId/check
//  L'app mobile poll ce endpoint pour savoir si le dépôt est
//  confirmé. On demande à Unipay le statut réel et on met
//  à jour le solde si "success".
// ══════════════════════════════════════════════════════════════
router.get('/deposit/:txId/check', auth, async (req, res) => {
  try {
    const tx = await Transaction.findOne({
      _id  : req.params.txId,
      user : req.user.id,
      type : 'deposit',
    });
    if (!tx) return res.status(404).json({ message: 'Transaction non trouvée' });

    // Déjà traité
    if (tx.status !== 'pending') {
      return res.json({
        status    : tx.status,
        txId      : tx._id,
        amount    : tx.amount,
        newBalance: tx.balanceAfter,
      });
    }

    if (!tx.unipayRef) {
      return res.json({ status: 'pending', message: 'Référence Unipay non encore disponible' });
    }

    // Interroger Unipay
    const result = await unipay.checkStatus(tx.unipayRef);

    if (result.status === 'success') {
      const user = await User.findById(tx.user);
      if (user && tx.status === 'pending') {
        tx.balanceBefore    = user.wallet.balance;
        // On crédite le net_amount (après frais 4%)
        user.wallet.balance += result.netAmount || tx.amount;
        user.wallet.lastDeposit = new Date();
        await user.save();
        tx.balanceAfter = user.wallet.balance;
        tx.status       = 'success';
        await tx.save();
        console.log(`💰 Dépôt confirmé : ${user.username} +${result.netAmount} CDF (net)`);
      }
      return res.json({
        status    : 'success',
        txId      : tx._id,
        amount    : result.amount,
        netAmount : result.netAmount,
        fee       : result.fee,
        newBalance: tx.balanceAfter,
        message   : `✅ Dépôt confirmé ! +${result.netAmount} CDF sur votre solde.`,
      });
    }

    if (['failed','cancelled'].includes(result.status)) {
      tx.status     = result.status;
      tx.failReason = 'Refusé ou annulé par l\'opérateur';
      await tx.save();
    }

    res.json({
      status : result.status,
      txId   : tx._id,
      message: result.status === 'processing'
        ? 'Paiement en cours de traitement…'
        : result.status === 'failed'
        ? '❌ Paiement refusé par l\'opérateur'
        : 'En attente de confirmation…',
    });

  } catch (err) {
    console.error('[GET /deposit/:txId/check]', err.message);
    res.status(err.status || 500).json({ message: err.message });
  }
});

// ══════════════════════════════════════════════════════════════
//  POST /api/payment/withdraw
//  FLUX MANUEL — Le solde est débité immédiatement.
//  L'admin reçoit : Nom / Numéro / Montant / Opérateur
//  et envoie l'argent manuellement depuis son compte Unipay.
// ══════════════════════════════════════════════════════════════
router.post('/withdraw', auth, withdrawRules, async (req, res) => {
  let tx, originalBalance;

  try {
    const errors = validationResult(req);
    if (!errors.isEmpty())
      return res.status(400).json({ errors: errors.array() });

    const { phone, operator } = req.body;
    const amount    = Math.floor(Number(req.body.amount));
    const unipayOp  = unipay.mapOperator(operator);
    const phoneFmt  = unipay.normalizePhone(phone);

    const user = await User.findById(req.user.id);
    if (!user) return res.status(404).json({ message: 'Utilisateur non trouvé' });

    if (user.wallet.balance < amount)
      return res.status(400).json({
        message   : 'Solde insuffisant',
        balance   : user.wallet.balance,
        requested : amount,
      });

    // Débiter immédiatement
    originalBalance          = user.wallet.balance;
    user.wallet.balance     -= amount;
    user.wallet.lastWithdraw = new Date();
    await user.save();

    // Enregistrer la demande
    tx = await Transaction.create({
      user         : user._id,
      type         : 'withdraw',
      amount,
      operator     : unipayOp,
      momoPhone    : phoneFmt,
      status       : 'pending',
      balanceBefore: originalBalance,
      balanceAfter : user.wallet.balance,
      description  : `Retrait ${unipayOp} ${amount} CDF`,
    });

    // Log admin visible dans Render Logs
    console.log(
      `\n🔔 ═══ NOUVELLE DEMANDE DE RETRAIT ═══\n` +
      `   Joueur   : ${user.username}\n` +
      `   Email    : ${user.email}\n` +
      `   Numéro   : ${phoneFmt} (${unipayOp})\n` +
      `   Montant  : ${amount} CDF\n` +
      `   TxID     : ${tx._id}\n` +
      `   → Allez sur /admin → Retraits en attente pour traiter\n` +
      `═══════════════════════════════════════\n`
    );

    res.json({
      success    : true,
      txId       : tx._id,
      message    : 'Demande de retrait enregistrée',
      amount,
      phone      : phoneFmt,
      operator   : unipayOp,
      newBalance : user.wallet.balance,
      info       : "L'administrateur va vérifier votre demande et vous envoyer l'argent. Délai habituel : quelques heures.",
    });

  } catch (err) {
    // Rembourser si le solde a été débité
    if (originalBalance !== undefined) {
      try {
        const u = await User.findById(req.user.id);
        if (u) { u.wallet.balance = originalBalance; await u.save(); }
      } catch (_) {}
    }
    if (tx) {
      try { tx.status = 'failed'; tx.failReason = err.message; await tx.save(); }
      catch (_) {}
    }
    console.error('[POST /payment/withdraw]', err.message);
    res.status(err.status || 500).json({ message: err.message });
  }
});

// ══════════════════════════════════════════════════════════════
//  GET /api/payment/withdrawals/pending  [ADMIN]
//  Liste des retraits en attente avec toutes les infos
//  pour que l'admin puisse les traiter
// ══════════════════════════════════════════════════════════════
router.get('/withdrawals/pending', auth, adminOnly, async (req, res) => {
  try {
    const list = await Transaction
      .find({ type: 'withdraw', status: 'pending' })
      .populate('user', 'username email phone avatar')
      .sort({ createdAt: 1 });

    res.json({
      count  : list.length,
      total  : list.reduce((s, t) => s + t.amount, 0),
      items  : list.map(t => ({
        id         : t._id,
        username   : t.user?.username,
        email      : t.user?.email,
        userPhone  : t.user?.phone,      // numéro de compte du joueur
        momoPhone  : t.momoPhone,        // numéro Mobile Money de destination
        operator   : t.operator,
        amount     : t.amount,
        createdAt  : t.createdAt,
        minutesAgo : Math.floor((Date.now() - t.createdAt) / 60000),
      })),
    });
  } catch (err) {
    res.status(500).json({ message: err.message });
  }
});

// ══════════════════════════════════════════════════════════════
//  PATCH /api/payment/withdraw/:id/confirm  [ADMIN]
//  Admin marque le retrait comme payé après l'avoir envoyé
// ══════════════════════════════════════════════════════════════
router.patch('/withdraw/:id/confirm', auth, adminOnly, async (req, res) => {
  try {
    const tx = await Transaction
      .findOne({ _id: req.params.id, type: 'withdraw', status: 'pending' })
      .populate('user', 'username email');

    if (!tx) return res.status(404).json({ message: 'Retrait en attente non trouvé' });

    tx.status       = 'success';
    tx.callbackData = {
      confirmedBy: req.user.id,
      confirmedAt: new Date(),
      note       : req.body.note || 'Envoyé manuellement',
    };
    await tx.save();

    console.log(`✅ Retrait confirmé : ${tx.user?.username} ${tx.amount} CDF → ${tx.momoPhone}`);

    res.json({
      success : true,
      message : '✅ Retrait marqué comme payé',
      user    : tx.user?.username,
      amount  : tx.amount,
      phone   : tx.momoPhone,
      operator: tx.operator,
    });
  } catch (err) {
    res.status(500).json({ message: err.message });
  }
});

// ══════════════════════════════════════════════════════════════
//  PATCH /api/payment/withdraw/:id/reject  [ADMIN]
//  Admin annule le retrait → solde remboursé au joueur
// ══════════════════════════════════════════════════════════════
router.patch('/withdraw/:id/reject', auth, adminOnly, async (req, res) => {
  try {
    const tx = await Transaction
      .findOne({ _id: req.params.id, type: 'withdraw', status: 'pending' })
      .populate('user');

    if (!tx) return res.status(404).json({ message: 'Retrait en attente non trouvé' });

    const user = await User.findById(tx.user._id || tx.user);
    if (user) {
      user.wallet.balance += tx.amount;
      await user.save();
    }

    tx.status       = 'cancelled';
    tx.failReason   = req.body.reason || 'Annulé par l\'admin';
    tx.callbackData = { rejectedBy: req.user.id, rejectedAt: new Date() };
    await tx.save();

    res.json({
      success    : true,
      message    : '❌ Retrait annulé — solde remboursé',
      user       : user?.username,
      refunded   : tx.amount,
      newBalance : user?.wallet.balance,
    });
  } catch (err) {
    res.status(500).json({ message: err.message });
  }
});

// ══════════════════════════════════════════════════════════════
//  GET /api/payment/history  [Utilisateur connecté]
// ══════════════════════════════════════════════════════════════
router.get('/history', auth, async (req, res) => {
  try {
    const page  = Math.max(1, parseInt(req.query.page)  || 1);
    const limit = Math.min(50, parseInt(req.query.limit) || 20);
    const txs   = await Transaction
      .find({ user: req.user.id })
      .sort({ createdAt: -1 })
      .skip((page - 1) * limit)
      .limit(limit)
      .select('-callbackData -__v');
    const total = await Transaction.countDocuments({ user: req.user.id });
    res.json({ transactions: txs, page, total, pages: Math.ceil(total / limit) });
  } catch (err) {
    res.status(500).json({ message: err.message });
  }
});

// ══════════════════════════════════════════════════════════════
//  GET /api/payment/config-status
//  Vérifier si Unipay est configuré (sans faire de vrai appel)
// ══════════════════════════════════════════════════════════════
router.get('/config-status', (_, res) => {
  res.json({
    unipayConfigured : unipay.isConfigured(),
    depositMode      : unipay.isConfigured()
      ? 'AUTOMATIQUE via Unipay Congo (4% frais)'
      : 'NON CONFIGURÉ — ajoutez UNIPAY_API_KEY sur Render',
    withdrawalMode   : 'MANUEL — l\'admin envoie l\'argent depuis son compte Unipay',
    operators        : unipay.OPERATOR_NAMES,
  });
});

module.exports = router;
