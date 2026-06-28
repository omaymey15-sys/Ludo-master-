/**
 * ══════════════════════════════════════════════════════════════
 *  Unipay Congo — Intégration officielle (PRODUCTION READY)
 *  Base URL : https://unipay-api.onrender.com/v1
 * ══════════════════════════════════════════════════════════════
 */

const axios = require('axios');

const BASE_URL = 'https://unipay-api.onrender.com/v1';
const API_KEY  = process.env.UNIPAY_API_KEY;

// ─────────────────────────────────────────────
// Vérification config
// ─────────────────────────────────────────────
if (!API_KEY) {
  console.warn(
    '⚠️ UNIPAY_API_KEY manquant dans les variables d’environnement'
  );
}

function assertConfigured() {
  if (!API_KEY) {
    const err = new Error('UNIPAY_API_KEY non configuré');
    err.status = 503;
    throw err;
  }
}

// ─────────────────────────────────────────────
// HTTP CLIENT
// ─────────────────────────────────────────────
const client = axios.create({
  baseURL: BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
    'X-API-Key': API_KEY || '',
  },
});

client.interceptors.response.use(
  res => res.data,
  err => {
    const msg = err.response?.data?.message || err.message;
    const status = err.response?.status || 500;

    const e = new Error(`Unipay: ${msg}`);
    e.status = status;
    throw e;
  }
);

// ─────────────────────────────────────────────
// INITIER UN DÉPÔT (C2B / collect)
// ─────────────────────────────────────────────
async function initiateDeposit({ phone, amount, operator, reference }) {
  assertConfigured();

  const body = {
    operator : mapOperator(operator),
    phone    : normalizePhone(phone),
    amount   : Math.floor(amount),
    reference: reference || `LMP-${Date.now()}`,
    direction: 'collect',
    currency : 'CDF', // ✅ OBLIGATOIRE (fix erreur)
  };

  const resp = await client.post('/payment/initiate', body);

  return {
    transactionId: resp.transaction_id,
    status       : resp.status,
    amount       : resp.amount,
    fee          : resp.fee,
    netAmount    : resp.net_amount,
    currency     : resp.currency,
    raw          : resp,
  };
}

// ─────────────────────────────────────────────
// CHECK STATUS
// ─────────────────────────────────────────────
async function checkStatus(transactionId) {
  assertConfigured();

  const resp = await client.get(`/payment/status/${transactionId}`);

  return {
    transactionId: resp.transaction_id,
    status       : resp.status,
    amount       : resp.amount,
    fee          : resp.fee,
    netAmount    : resp.net_amount,
    operator     : resp.operator,
    phone        : resp.phone,
    createdAt    : resp.created_at,
    currency     : resp.currency,
    raw          : resp,
  };
}

// ─────────────────────────────────────────────
// OPERATORS MAP
// ─────────────────────────────────────────────
const OPERATOR_MAP = {
  orange_money: 'orange',
  orange      : 'orange',
  airtel_money: 'airtel',
  airtel      : 'airtel',
  afrimoney   : 'afrimoney',
  mpesa       : 'vodacash',
  vodacash    : 'vodacash',
};

function mapOperator(op) {
  const mapped = OPERATOR_MAP[op?.toLowerCase()];
  if (!mapped) {
    const err = new Error(
      `Opérateur invalide: ${op}`
    );
    err.status = 400;
    throw err;
  }
  return mapped;
}

// ─────────────────────────────────────────────
// PHONE NORMALIZATION (RDC)
// ─────────────────────────────────────────────
function normalizePhone(phone) {
  let p = String(phone).replace(/[^0-9+]/g, '');

  if (p.startsWith('00243')) p = '+243' + p.slice(5);
  if (p.startsWith('0')) p = '+243' + p.slice(1);
  if (!p.startsWith('+')) p = '+243' + p;

  return p;
}

// ─────────────────────────────────────────────
// CALCUL FRAIS (4%)
// ─────────────────────────────────────────────
function calcFee(amount) {
  const fee = Math.ceil(amount * 0.04);
  return {
    gross: amount,
    fee,
    netAmount: amount - fee,
  };
}

// ─────────────────────────────────────────────
// EXPORTS
// ─────────────────────────────────────────────
module.exports = {
  initiateDeposit,
  checkStatus,
  normalizePhone,
  mapOperator,
  calcFee,
  assertConfigured,
  isConfigured: () => !!API_KEY,
};
