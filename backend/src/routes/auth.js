const express = require('express');
const jwt     = require('jsonwebtoken');
const { body, validationResult } = require('express-validator');
const router  = express.Router();
const User    = require('../models/User');
const { auth } = require('../middleware/auth');

const sign = (user) => jwt.sign(
  { id: user._id, role: user.role },
  process.env.JWT_SECRET,
  { expiresIn: process.env.JWT_EXPIRES_IN || '7d' }
);

// POST /api/auth/register
router.post('/register', [
  body('username').trim().isLength({ min: 3, max: 20 }),
  body('email').isEmail().normalizeEmail(),
  body('password').isLength({ min: 8 }),
  body('phone').notEmpty(),
], async (req, res) => {
  const errors = validationResult(req);
  if (!errors.isEmpty()) return res.status(400).json({ errors: errors.array() });

  const { username, email, password, phone, avatar } = req.body;
  const exists = await User.findOne({ $or: [{ email }, { username }, { phone }] });
  if (exists) return res.status(409).json({ message: 'Email, pseudo ou téléphone déjà utilisé' });

  const user = await User.create({ username, email, password, phone, avatar: avatar || '😊' });
  res.status(201).json({ token: sign(user), user: { id: user._id, username, avatar, role: user.role } });
});

// POST /api/auth/login
router.post('/login', async (req, res) => {
  const { email, password } = req.body;
  const user = await User.findOne({ email }).select('+password');
  if (!user || !(await user.checkPassword(password)))
    return res.status(401).json({ message: 'Identifiants incorrects' });
  if (!user.isActive) return res.status(403).json({ message: 'Compte désactivé' });
  user.lastLogin = new Date(); await user.save();
  res.json({ token: sign(user), user: {
    id: user._id, username: user.username, avatar: user.avatar,
    role: user.role, balance: user.wallet.balance
  }});
});

// GET /api/auth/me
router.get('/me', auth, async (req, res) => {
  const user = await User.findById(req.user.id).select('-password');
  if (!user) return res.status(404).json({ message: 'Non trouvé' });
  res.json(user);
});

// PATCH /api/auth/momo — Configurer le mobile money
router.patch('/momo', auth, async (req, res) => {
  const { operator, momoNumber } = req.body;
  if (!['mpesa','orange_money','airtel_money'].includes(operator))
    return res.status(400).json({ message: 'Opérateur invalide' });
  await User.findByIdAndUpdate(req.user.id, {
    'kyc.operator': operator, 'kyc.momoNumber': momoNumber,
  });
  res.json({ message: 'Mobile money configuré' });
});

module.exports = router;

// ══════════════════════════════════════════════════════════════
//  POST /api/auth/setup-admin
//  ─────────────────────────────────────────────────────────────
//  Endpoint de BOOTSTRAP : crée le premier compte admin.
//  Règles de sécurité strictes :
//   • Ne fonctionne QUE si aucun admin n'existe encore en base
//   • Dès qu'un admin existe → retourne 403 DEFINITIVELY
//   • Aucune auth requise (puisqu'il n'y a pas encore d'admin)
//   • Après usage réussi, appels suivants → 403 pour toujours
// ══════════════════════════════════════════════════════════════
router.post('/setup-admin', [
  body('username').trim().isLength({ min: 3, max: 20 }).withMessage('Pseudo 3-20 caractères'),
  body('email').isEmail().normalizeEmail().withMessage('Email invalide'),
  body('password').isLength({ min: 8 }).withMessage('Mot de passe min 8 caractères'),
  body('phone').notEmpty().withMessage('Téléphone requis'),
  body('setupKey').notEmpty().withMessage('Clé de setup requise'),
], async (req, res) => {
  try {
    // 1. Validation des champs
    const errors = validationResult(req);
    if (!errors.isEmpty())
      return res.status(400).json({ errors: errors.array() });

    // 2. Vérifier la clé de setup (simple protection anti-bot)
    const SETUP_KEY = process.env.ADMIN_SETUP_KEY || 'ludomaster-setup-2024';
    if (req.body.setupKey !== SETUP_KEY)
      return res.status(403).json({ message: 'Clé de setup incorrecte' });

    // 3. SÉCURITÉ CRITIQUE : vérifier qu'aucun admin n'existe déjà
    const existingAdmin = await User.findOne({ role: 'admin' });
    if (existingAdmin)
      return res.status(403).json({
        message: 'Un administrateur existe déjà. Cet endpoint est désactivé.',
        hint: 'Connectez-vous avec votre compte admin existant.'
      });

    // 4. Vérifier doublons email/username/phone
    const { username, email, password, phone, avatar } = req.body;
    const exists = await User.findOne({ $or: [{ email }, { username }, { phone }] });
    if (exists)
      return res.status(409).json({ message: 'Email, pseudo ou téléphone déjà utilisé' });

    // 5. Créer le compte admin
    const admin = await User.create({
      username,
      email,
      password,
      phone,
      avatar:   avatar || '👑',
      role:     'admin',
      isActive: true,
      kyc:      { verified: true },
    });

    const token = sign(admin);

    console.log(`✅ Compte admin créé : ${username} (${email})`);

    res.status(201).json({
      message: '🎉 Compte administrateur créé avec succès !',
      token,
      user: {
        id:       admin._id,
        username: admin.username,
        email:    admin.email,
        role:     admin.role,
        avatar:   admin.avatar,
      },
    });

  } catch (err) {
    console.error('setup-admin error:', err);
    res.status(500).json({ message: 'Erreur serveur', detail: err.message });
  }
});

// GET /api/auth/setup-status
// Vérifie si un admin existe déjà (pour que le dashboard sache
// s'il doit afficher le formulaire de setup ou pas).
router.get('/setup-status', async (_req, res) => {
  const adminExists = await User.exists({ role: 'admin' });
  res.json({ adminExists: !!adminExists });
});
  
