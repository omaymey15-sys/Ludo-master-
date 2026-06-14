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
