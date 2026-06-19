/**
 * ══════════════════════════════════════════════════════════════
 *  WonyaPay Service — Intégration WonyaSoft (wonyasoft.com)
 *  Agrégateur Mobile Money RDC : M-Pesa, Orange Money, Airtel Money
 *  Agréé Banque Centrale du Congo (BCC)
 * ══════════════════════════════════════════════════════════════
 */

const axios  = require('axios');
const crypto = require('crypto');
const { v4: uuid } = require('uuid');

const BASE_URL     = process.env.WONYAPAY_BASE_URL    || 'https://api.wonyasoft.com/v1';
const API_KEY      = process.env.WONYAPAY_API_KEY;
const SECRET       = process.env.WONYAPAY_SECRET;
const MERCHANT_ID  = process.env.WONYAPAY_MERCHANT_ID;
const CALLBACK_URL = process.env.WONYAPAY_CALLBACK_URL;

// ── Vérification de la configuration au démarrage ─────────────
// Évite un crash silencieux (502) plus tard : on prévient
// clairement dans les logs Render si les clés manquent.
const MISSING_CONFIG = [];
if (!API_KEY)     MISSING_CONFIG.push('WONYAPAY_API_KEY');
if (!SECRET)       MISSING_CONFIG.push('WONYAPAY_SECRET');
if (!MERCHANT_ID)  MISSING_CONFIG.push('WONYAPAY_MERCHANT_ID');
if (!CALLBACK_URL) MISSING_CONFIG.push('WONYAPAY_CALLBACK_URL');

if (MISSING_CONFIG.length > 0) {
  console.error(
    `⚠️  WonyaPay NON configuré — variables manquantes : ${MISSING_CONFIG.join(', ')}\n` +
    `   → Allez sur Render Dashboard → Environment → ajoutez ces variables.\n` +
    `   → Les dépôts/retraits échoueront avec un message explicite tant que ce n'est pas fait.`
  );
}

function assertConfigured() {
  if (MISSING_CONFIG.length > 0) {
    const err = new Error(
      `WonyaPay non configuré sur le serveur (variables manquantes : ${MISSING_CONFIG.join(', ')}). ` +
      `Contactez l'administrateur.`
    );
    err.status = 503; // Service Unavailable — plus clair qu'un 502
    throw err;
  }
}

// ── Génération de signature HMAC-SHA256 ───────────────────────
function sign(payload) {
  assertConfigured();
  return crypto
    .createHmac('sha256', SECRET)
    .update(JSON.stringify(payload))
    .digest('hex');
}

// ── Client HTTP ───────────────────────────────────────────────
const client = axios.create({
  baseURL : BASE_URL,
  timeout : 30000,
  headers : {
    'Content-Type': 'application/json',
    'X-Api-Key'   : API_KEY,
    'X-Merchant-Id': MERCHANT_ID,
  },
});

client.interceptors.response.use(
  res => res.data,
  err => {
    const msg = err.response?.data?.message || err.message;
    throw new Error(`WonyaPay: ${msg}`);
  }
);

// ══════════════════════════════════════════════════════════════
//  DÉPÔT (Collect / Pull)
//  Le client reçoit une demande de paiement sur son téléphone
// ══════════════════════════════════════════════════════════════
async function initiateDeposit({ userId, phone, amount, operator, description }) {
  assertConfigured();
  const orderId   = `DEP-${userId}-${uuid().slice(0,8).toUpperCase()}`;
  const payload   = {
    order_id    : orderId,
    amount      : amount,
    currency    : 'CDF',
    phone       : normalizePhone(phone),
    operator    : operator,          // mpesa | orange_money | airtel_money
    description : description || `Dépôt Ludo Master`,
    callback_url: `${CALLBACK_URL}/deposit`,
    merchant_id : MERCHANT_ID,
  };
  payload.signature = sign(payload);

  const response = await client.post('/payment/collect', payload);
  return {
    orderId,
    wonyaRef  : response.reference || response.transaction_id,
    status    : response.status    || 'pending',
    raw       : response,
  };
}

// ══════════════════════════════════════════════════════════════
//  RETRAIT (Disbursement / Push)
//  On envoie l'argent directement sur le mobile money du joueur
// ══════════════════════════════════════════════════════════════
async function initiateWithdraw({ userId, phone, amount, operator, description }) {
  assertConfigured();
  const orderId = `WIT-${userId}-${uuid().slice(0,8).toUpperCase()}`;
  const payload = {
    order_id    : orderId,
    amount      : amount,
    currency    : 'CDF',
    phone       : normalizePhone(phone),
    operator    : operator,
    description : description || `Retrait Ludo Master`,
    callback_url: `${CALLBACK_URL}/withdraw`,
    merchant_id : MERCHANT_ID,
  };
  payload.signature = sign(payload);

  const response = await client.post('/payment/disbursement', payload);
  return {
    orderId,
    wonyaRef: response.reference || response.transaction_id,
    status  : response.status    || 'pending',
    raw     : response,
  };
}

// ══════════════════════════════════════════════════════════════
//  VÉRIFICATION DE STATUT
// ══════════════════════════════════════════════════════════════
async function checkStatus(wonyaRef) {
  const payload   = { reference: wonyaRef, merchant_id: MERCHANT_ID };
  payload.signature = sign(payload);
  const response = await client.post('/payment/status', payload);
  return {
    status : response.status,     // success | pending | failed
    amount : response.amount,
    raw    : response,
  };
}

// ══════════════════════════════════════════════════════════════
//  VÉRIFICATION DU WEBHOOK (callback de WonyaPay)
// ══════════════════════════════════════════════════════════════
function verifyCallback(body, receivedSignature) {
  const { signature, ...rest } = body;
  const expected = sign(rest);
  return crypto.timingSafeEqual(
    Buffer.from(expected),
    Buffer.from(receivedSignature || signature || '')
  );
}

// ══════════════════════════════════════════════════════════════
//  UTILITAIRES
// ══════════════════════════════════════════════════════════════

// Normalise le numéro téléphone → format international DRC
function normalizePhone(phone) {
  let p = phone.replace(/\s+/g, '').replace(/[^0-9+]/g, '');
  if (p.startsWith('0')) p = '+243' + p.slice(1);
  if (!p.startsWith('+')) p = '+243' + p;
  return p;
}

// Détecte l'opérateur depuis le numéro
function detectOperator(phone) {
  const p = normalizePhone(phone).replace('+243', '');
  if (/^(81|82|83|84|85)/.test(p)) return 'mpesa';
  if (/^(85|86|87|88|89|84)/.test(p)) return 'orange_money';
  if (/^(97|98|99)/.test(p))          return 'airtel_money';
  return null;
}

module.exports = {
  initiateDeposit,
  initiateWithdraw,
  checkStatus,
  verifyCallback,
  normalizePhone,
  detectOperator,
  assertConfigured,
  isConfigured: () => MISSING_CONFIG.length === 0,
  missingConfig: () => [...MISSING_CONFIG],
};
    
