const express     = require('express');
const router      = express.Router();
const User        = require('../models/User');
const Transaction = require('../models/Transaction');
const Competition = require('../models/Competition');
const { auth, adminOnly } = require('../middleware/auth');

// Toutes les routes admin nécessitent auth + adminOnly
router.use(auth, adminOnly);

// ══════════════════════════════════════════════════════════════
//  GET /api/admin/dashboard — KPIs généraux
// ══════════════════════════════════════════════════════════════
router.get('/dashboard', async (req, res) => {
  const [
    totalUsers, activeUsers,
    totalDeposits, totalWithdraws,
    totalPlatformFees,
    openComps, playingComps, finishedComps,
    recentTx,
  ] = await Promise.all([
    User.countDocuments(),
    User.countDocuments({ isActive: true }),

    Transaction.aggregate([
      { $match: { type: 'deposit', status: 'success' } },
      { $group: { _id: null, total: { $sum: '$amount' }, count: { $sum: 1 } } },
    ]),
    Transaction.aggregate([
      { $match: { type: 'withdraw', status: 'success' } },
      { $group: { _id: null, total: { $sum: '$amount' }, count: { $sum: 1 } } },
    ]),
    Competition.aggregate([
      { $match: { status: 'finished' } },
      { $group: { _id: null, total: { $sum: '$platformFee' } } },
    ]),

    Competition.countDocuments({ status: 'open' }),
    Competition.countDocuments({ status: 'playing' }),
    Competition.countDocuments({ status: 'finished' }),

    Transaction.find({ status: 'success' })
      .sort({ createdAt: -1 }).limit(10)
      .populate('user', 'username').lean(),
  ]);

  res.json({
    users: { total: totalUsers, active: activeUsers },
    finance: {
      totalDeposited:   totalDeposits[0]?.total    || 0,
      depositCount:     totalDeposits[0]?.count    || 0,
      totalWithdrawn:   totalWithdraws[0]?.total   || 0,
      withdrawCount:    totalWithdraws[0]?.count   || 0,
      platformRevenue:  totalPlatformFees[0]?.total || 0,
    },
    competitions: { open: openComps, playing: playingComps, finished: finishedComps },
    recentTransactions: recentTx,
  });
});

// ══════════════════════════════════════════════════════════════
//  Gestion des utilisateurs
// ══════════════════════════════════════════════════════════════
router.get('/users', async (req, res) => {
  const { page = 1, limit = 30, search, role } = req.query;
  const filter = {};
  if (search) filter.$or = [
    { username: new RegExp(search, 'i') },
    { email:    new RegExp(search, 'i') },
    { phone:    new RegExp(search, 'i') },
  ];
  if (role) filter.role = role;

  const users = await User.find(filter)
    .select('-password')
    .sort({ createdAt: -1 })
    .skip((page - 1) * limit).limit(Number(limit));
  const total = await User.countDocuments(filter);
  res.json({ users, total, page: Number(page), pages: Math.ceil(total / limit) });
});

router.get('/users/:id', async (req, res) => {
  const user = await User.findById(req.params.id).select('-password');
  if (!user) return res.status(404).json({ message: 'Non trouvé' });
  const txs  = await Transaction.find({ user: user._id }).sort({ createdAt: -1 }).limit(20);
  const comps = await Competition.find({ 'players.user': user._id }).sort({ createdAt: -1 }).limit(10);
  res.json({ user, transactions: txs, competitions: comps });
});

// Modifier le solde manuellement (ajustement admin)
router.patch('/users/:id/wallet', async (req, res) => {
  const { adjustment, reason } = req.body;
  const user = await User.findById(req.params.id);
  if (!user) return res.status(404).json({ message: 'Non trouvé' });

  const before = user.wallet.balance;
  user.wallet.balance = Math.max(0, user.wallet.balance + Number(adjustment));
  await user.save();

  await Transaction.create({
    user:          user._id,
    type:          adjustment > 0 ? 'deposit' : 'withdraw',
    amount:        Math.abs(adjustment),
    status:        'success',
    balanceBefore: before,
    balanceAfter:  user.wallet.balance,
    description:   `Ajustement admin: ${reason || 'Manuel'}`,
  });

  res.json({ message: 'Solde mis à jour', newBalance: user.wallet.balance });
});

// Activer / désactiver un compte
router.patch('/users/:id/status', async (req, res) => {
  const user = await User.findByIdAndUpdate(
    req.params.id, { isActive: req.body.isActive }, { new: true }
  );
  if (!user) return res.status(404).json({ message: 'Non trouvé' });
  res.json({ message: `Compte ${user.isActive ? 'activé' : 'désactivé'}` });
});

// Promouvoir en admin
router.patch('/users/:id/role', async (req, res) => {
  const { role } = req.body;
  if (!['player','admin'].includes(role))
    return res.status(400).json({ message: 'Rôle invalide' });
  const user = await User.findByIdAndUpdate(req.params.id, { role }, { new: true });
  res.json({ message: `Rôle mis à jour : ${role}`, user });
});

// ══════════════════════════════════════════════════════════════
//  Gestion des compétitions
// ══════════════════════════════════════════════════════════════
router.get('/competitions', async (req, res) => {
  const { page = 1, limit = 20, status } = req.query;
  const filter = status ? { status } : {};
  const comps = await Competition.find(filter)
    .populate('createdBy', 'username')
    .sort({ createdAt: -1 })
    .skip((page - 1) * limit).limit(Number(limit));
  const total = await Competition.countDocuments(filter);
  res.json({ competitions: comps, total });
});

// Créer une compétition planifiée
router.post('/competitions', async (req, res) => {
  const {
    title, description, entryFee, maxPlayers = 4,
    prizeDistribution = [60, 30, 10], openAt, startAt,
    isPublic = true, aiAllowed = false,
  } = req.body;

  if (!title || entryFee == null)
    return res.status(400).json({ message: 'title et entryFee requis' });
  if (prizeDistribution.reduce((a, b) => a + b, 0) !== 100)
    return res.status(400).json({ message: 'prizeDistribution doit totaliser 100' });

  const comp = await Competition.create({
    title, description, entryFee, maxPlayers,
    prizeDistribution,
    openAt:  openAt  || new Date(),
    startAt: startAt || null,
    isPublic, aiAllowed,
    createdBy: req.user.id,
    status: 'open',
  });
  res.status(201).json(comp);
});

// ══════════════════════════════════════════════════════════════
//  Rapports financiers
// ══════════════════════════════════════════════════════════════
router.get('/reports/daily', async (req, res) => {
  const days = parseInt(req.query.days) || 30;
  const since = new Date(Date.now() - days * 86400000);

  const data = await Transaction.aggregate([
    { $match: { status: 'success', createdAt: { $gte: since } } },
    {
      $group: {
        _id: {
          date: { $dateToString: { format: '%Y-%m-%d', date: '$createdAt' } },
          type: '$type',
        },
        total: { $sum: '$amount' },
        count: { $sum: 1 },
      },
    },
    { $sort: { '_id.date': 1 } },
  ]);

  // Récapitulatif par opérateur
  const byOperator = await Transaction.aggregate([
    { $match: { status: 'success', type: { $in: ['deposit','withdraw'] } } },
    { $group: { _id: '$operator', total: { $sum: '$amount' }, count: { $sum: 1 } } },
  ]);

  res.json({ daily: data, byOperator });
});

// Top joueurs (gains)
router.get('/reports/top-players', async (req, res) => {
  const top = await User.find()
    .sort({ 'stats.totalEarned': -1 })
    .limit(20)
    .select('username avatar stats wallet.balance');
  res.json(top);
});

module.exports = router;
