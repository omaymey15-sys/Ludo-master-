/**
 * ══════════════════════════════════════════════════════════════
 *  Unipay Congo — Intégration officielle
 *  Base URL : https://unipay-api.onrender.com
 *
 *  Utilisé UNIQUEMENT pour les dépôts (direction: "collect").
 *  Les retraits sont gérés MANUELLEMENT par l'admin.
 *
 *  Auth    : X-API-Key header
 *  Frais   : 4% prélevés automatiquement sur chaque transaction
 *  Statut  : polling GET /v1/payment/status/:id (pas de webhook)
 * ══════════════════════════════════════════════════════════════
 */

const axios = require('axios');

const BASE_URL = 'https://unipay-api.onrender.com/v1';
const API_KEY  = process.env.UNIPAY_API_KEY;   // up_xxxxxxxxxxxxx

// ── Vérification de la config au démarrage ────────────────────
if (!API_KEY) {
  console.warn(
    '⚠️  UNIPAY_API_KEY manquant !\n' +
    '   → Render Dashboard → Environment → ajoutez : UNIPAY_API_KEY=up_...\n' +
    '   → Les dépôts retourneront une erreur 503 tant que ce n\'est pas fait.'
  );
}

function assertConfigured() {
  if (!API_KEY) {
    const err = new Error(
      'UNIPAY_API_KEY non configuré sur le serveur. ' +
      'Ajoutez-le dans Render Dashboard → Environment.'
    );
    err.status = 503;
    throw err;
  }
}

// ── Client HTTP Unipay ────────────────────────────────────────
const client = axios.create({
  baseURL: BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
    'X-API-Key'   : API_KEY || '',
  },
});

client.interceptors.response.use(
  res => res.data,
  err => {
    const msg    = err.response?.data?.message || err.response?.data?.error || err.message;
    const status = err.response?.status || 502;
    const e      = new Error(`Unipay: ${msg}`);
    e.status     = status;
    throw e;
  }
);

// ══════════════════════════════════════════════════════════════
//  INITIER UN DÉPÔT (Collect / C2B)
//  L'utilisateur reçoit une demande sur son téléphone
//  et confirme le paiement vers notre compte marchand.
//
//  Opérateurs acceptés : orange | airtel | afrimoney | vodacash
//  Frais : 4% prélevés (net_amount = montant - frais)
// ══════════════════════════════════════════════════════════════
async function initiateDeposit({ phone, amount, operator, reference }) {
  assertConfigured();

  const body = {
    operator : operator,                   // orange | airtel | afrimoney | vodacash
    phone    : normalizePhone(phone),      // format E.164 : +243...
    amount   : Math.floor(amount),         // entier CDF
    reference: reference || `LMP-${Date.now()}`,
    direction: 'collect',                  // dépôt C2B
  };

  const resp = await client.post('/payment/initiate', body);

  // Réponse : { transaction_id, status, amount, fee, net_amount, currency }
  return {
    transactionId: resp.transaction_id,
    status       : resp.status,           // "pending" au départ
    amount       : resp.amount,
    fee          : resp.fee,              // frais Unipay (4%)
    netAmount    : resp.net_amount,       // ce qu'on reçoit réellement
    currency     : resp.currency,         // "CDF"
    raw          : resp,
  };
}

// ══════════════════════════════════════════════════════════════
//  VÉRIFIER LE STATUT D'UNE TRANSACTION
//  GET /v1/payment/status/:id
//
//  Statuts possibles :
//  pending | processing | success | failed | cancelled
// ══════════════════════════════════════════════════════════════
async function checkStatus(transactionId) {
  assertConfigured();

  const resp = await client.get(`/payment/status/${transactionId}`);

  // Réponse : { transaction_id, status, operator, phone, amount, fee, net_amount, currency, created_at }
  return {
    transactionId: resp.transaction_id,
    status       : resp.status,
    amount       : resp.amount,
    fee          : resp.fee,
    netAmount    : resp.net_amount,
    operator     : resp.operator,
    phone        : resp.phone,
    createdAt    : resp.created_at,
    raw          : resp,
  };
}

// ══════════════════════════════════════════════════════════════
//  MAPPING OPÉRATEURS
//  App Kotlin → Unipay Congo
// ══════════════════════════════════════════════════════════════
const OPERATOR_MAP = {
  // Noms possibles dans l'app → code Unipay
  'orange_money' : 'orange',
  'orange'       : 'orange',
  'airtel_money' : 'airtel',
  'airtel'       : 'airtel',
  'afrimoney'    : 'afrimoney',
  'mpesa'        : 'vodacash',   // Vodacash = M-Pesa RDC (bientôt disponible)
  'vodacash'     : 'vodacash',
};

const OPERATOR_NAMES = {
  orange   : 'Orange Money 🟧',
  airtel   : 'Airtel Money 🟥',
  afrimoney: 'Afrimoney 💛',
  vodacash : 'Vodacash/M-Pesa 🟩',
};

function mapOperator(op) {
  const mapped = OPERATOR_MAP[op?.toLowerCase()];
  if (!mapped) throw Object.assign(
    new Error(`Opérateur inconnu : ${op}. Valeurs acceptées : orange, airtel, afrimoney, vodacash`),
    { status: 400 }
  );
  return mapped;
}

// ── Normalisation numéro DRC → format E.164 ───────────────────
function normalizePhone(phone) {
  let p = String(phone).replace(/[\s\-\.]/g,'').replace(/[^0-9+]/g,'');
  if (p.startsWith('00243')) p = '+243' + p.slice(5);
  if (p.startsWith('0'))     p = '+243' + p.slice(1);
  if (!p.startsWith('+'))    p = '+243' + p;
  return p;
}

// ── Calcul des frais Unipay (4%) ──────────────────────────────
function calcFee(amount) {
  const fee       = Math.ceil(amount * 0.04);
  const netAmount = amount - fee;
  return { gross: amount, fee, netAmount };
}

module.exports = {
  initiateDeposit,
  checkStatus,
  mapOperator,
  normalizePhone,
  calcFee,
  isConfigured : () => !!API_KEY,
  assertConfigured,
  OPERATOR_NAMES,
};
