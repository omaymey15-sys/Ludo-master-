const express     = require('express');
const router      = express.Router();
const Competition = require('../models/Competition');
const Transaction = require('../models/Transaction');
const User        = require('../models/User');
const { auth, adminOnly } = require('../middleware/auth');
const unipay    = require('../services/unipay');

// ══════════════════════════════════════════════════════════════
//  GET /api/competitions — Liste des compétitions ouvertes
// ══════════════════════════════════════════════════════════════
router.get('/', auth, async (req, res) => {
  const { status = 'open', page = 1, limit = 20 } = req.query;
  const comps = await Competition
    .find({ status, isPublic: true })
    .populate('players.user', 'username avatar stats.winRate')
    .sort({ openAt: -1 })
    .skip((page-1)*limit).limit(Number(limit));
  const total = await Competition.countDocuments({ status, isPublic: true });
  res.json({ competitions: comps, total });
});

// GET /api/competitions/:id — Détail d'une compétition
router.get('/:id', auth, async (req, res) => {
  const comp = await Competition.findById(req.params.id)
    .populate('players.user', 'username avatar stats');
  if (!comp) return res.status(404).json({ message: 'Compétition non trouvée' });
  res.json(comp);
});

// ══════════════════════════════════════════════════════════════
//  POST /api/competitions — Créer une compétition (ADMIN)
// ══════════════════════════════════════════════════════════════
router.post('/', auth, adminOnly, async (req, res) => {
  const {
    title, description, entryFee, maxPlayers = 4,
    prizeDistribution = [60, 30, 10], openAt, startAt,
    isPublic = true, aiAllowed = false,
  } = req.body;

  if (!title || entryFee === undefined)
    return res.status(400).json({ message: 'title et entryFee requis' });

  if (prizeDistribution.reduce((a, b) => a + b, 0) !== 100)
    return res.status(400).json({ message: 'prizeDistribution doit totaliser 100%' });

  const comp = await Competition.create({
    title, description, entryFee, maxPlayers,
    prizeDistribution, openAt: openAt || new Date(),
    startAt, isPublic, aiAllowed,
    createdBy: req.user.id,
    status: 'open',
  });

  res.status(201).json(comp);
});

// ══════════════════════════════════════════════════════════════
//  POST /api/competitions/:id/join — S'inscrire (débite le wallet)
// ══════════════════════════════════════════════════════════════
router.post('/:id/join', auth, async (req, res) => {
  const { color } = req.body;  // RED|BLUE|GREEN|YELLOW
  if (!['RED','BLUE','GREEN','YELLOW'].includes(color))
    return res.status(400).json({ message: 'Couleur invalide' });

  const comp = await Competition.findById(req.params.id);
  if (!comp)        return res.status(404).json({ message: 'Compétition non trouvée' });
  if (comp.status !== 'open') return res.status(400).json({ message: `Compétition ${comp.status}` });
  if (comp.players.length >= comp.maxPlayers)
    return res.status(400).json({ message: 'Compétition complète' });
  if (comp.players.some(p => p.user?.toString() === req.user.id))
    return res.status(400).json({ message: 'Déjà inscrit' });
  if (comp.players.some(p => p.color === color))
    return res.status(400).json({ message: 'Couleur déjà prise' });

  const user = await User.findById(req.user.id);
  if (user.wallet.balance < comp.entryFee)
    return res.status(400).json({ message: `Solde insuffisant. Requis: ${comp.entryFee} CDF` });

  // Débiter le wallet
  user.wallet.balance -= comp.entryFee;
  await user.save();

  // Enregistrer la transaction
  const tx = await Transaction.create({
    user:          user._id,
    type:          'bet',
    amount:        comp.entryFee,
    status:        'success',
    competition:   comp._id,
    balanceBefore: user.wallet.balance + comp.entryFee,
    balanceAfter:  user.wallet.balance,
    description:   `Inscription compétition "${comp.title}"`,
  });

  // Ajouter le joueur
  comp.players.push({
    user:      user._id,
    username:  user.username,
    avatar:    user.avatar,
    color,
    txDeposit: tx._id,
  });

  // Recalculer prizePool
  const gross    = comp.entryFee * comp.players.length;
  const feePct   = parseFloat(process.env.PLATFORM_FEE_PERCENT || 10) / 100;
  comp.platformFee = Math.floor(gross * feePct);
  comp.prizePool   = gross - comp.platformFee;

  // Auto-démarrer si complet
  if (comp.players.length >= comp.maxPlayers) {
    comp.status  = 'full';
    comp.startAt = new Date(Date.now() + 10_000); // démarre dans 10s
  }

  await comp.save();

  res.json({
    message:    'Inscription réussie',
    competition: comp._id,
    prizePool:  comp.prizePool,
    players:    comp.players.length,
    newBalance: user.wallet.balance,
  });
});

// ══════════════════════════════════════════════════════════════
//  POST /api/competitions/:id/result — Soumettre le résultat (serveur jeu)
//  Distribue automatiquement les gains via Unipay
// ══════════════════════════════════════════════════════════════
router.post('/:id/result', auth, adminOnly, async (req, res) => {
  const { ranking, totalTurns } = req.body;
  // ranking = [{ userId, color, rank }, ...]

  const comp = await Competition.findById(req.params.id);
  if (!comp || comp.status === 'finished')
    return res.status(400).json({ message: 'Compétition déjà terminée ou invalide' });

  comp.status  = 'finished';
  comp.endAt   = new Date();
  comp.gameResult = { totalTurns, ranking };

  const errors = [];
  const prizes = [];

  for (const entry of ranking) {
    const rankIdx = entry.rank - 1;  // 0-based
    if (rankIdx >= comp.prizeDistribution.length) continue;
    const pct   = comp.prizeDistribution[rankIdx] / 100;
    const prize = Math.floor(comp.prizePool * pct);
    if (prize <= 0) continue;

    const player = comp.players.find(p => p.user?.toString() === entry.userId);
    if (!player) continue;

    player.rank  = entry.rank;
    player.prize = prize;

    const user = await User.findById(entry.userId);
    if (!user) continue;

    // Créditer le wallet
    user.wallet.balance   += prize;
    user.stats.totalEarned += prize;
    user.stats.gamesPlayed += 1;
    if (entry.rank === 1) user.stats.gamesWon += 1;
    user.updateWinRate();
    await user.save();

    const tx = await Transaction.create({
      user:          user._id,
      type:          'prize',
      amount:        prize,
      status:        'success',
      competition:   comp._id,
      balanceBefore: user.wallet.balance - prize,
      balanceAfter:  user.wallet.balance,
      description:   `Gain #${entry.rank} - "${comp.title}" (${pct*100}%)`,
    });

    player.txPrize = tx._id;
    prizes.push({ username: user.username, rank: entry.rank, prize, phone: user.kyc.momoNumber });
  }

  await comp.save();
  res.json({ message: 'Résultats enregistrés', prizes, prizePool: comp.prizePool });
});

// ══════════════════════════════════════════════════════════════
//  PATCH /api/competitions/:id — Modifier une compétition (ADMIN)
// ══════════════════════════════════════════════════════════════
router.patch('/:id', auth, adminOnly, async (req, res) => {
  const allowed = ['title','description','status','openAt','startAt',
                   'entryFee','prizeDistribution','isPublic','aiAllowed'];
  const updates = Object.fromEntries(
    Object.entries(req.body).filter(([k]) => allowed.includes(k))
  );
  const comp = await Competition.findByIdAndUpdate(req.params.id, updates, { new: true });
  if (!comp) return res.status(404).json({ message: 'Non trouvée' });
  res.json(comp);
});

// DELETE /api/competitions/:id — Annuler + rembourser
router.delete('/:id', auth, adminOnly, async (req, res) => {
  const comp = await Competition.findById(req.params.id);
  if (!comp) return res.status(404).json({ message: 'Non trouvée' });
  if (comp.status === 'finished')
    return res.status(400).json({ message: 'Impossible d'annuler une partie terminée' });

  // Rembourser tous les joueurs inscrits
  const refunds = [];
  for (const p of comp.players) {
    if (!p.user || !p.txDeposit) continue;
    const user = await User.findById(p.user);
    if (!user) continue;
    user.wallet.balance += comp.entryFee; await user.save();
    await Transaction.create({
      user: user._id, type: 'refund', amount: comp.entryFee,
      status: 'success', competition: comp._id,
      description: `Remboursement annulation "${comp.title}"`,
    });
    refunds.push(user.username);
  }

  comp.status = 'cancelled'; await comp.save();
  res.json({ message: 'Compétition annulée', refunds });
});

module.exports = router;
