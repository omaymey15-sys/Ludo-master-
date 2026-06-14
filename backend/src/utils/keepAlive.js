/**
 * keepAlive.js
 * Ping automatique toutes les 14 minutes pour éviter
 * que Render (plan Free) mette le service en veille.
 * S'active uniquement en production.
 */

const https = require('https');
const http  = require('http');

function startKeepAlive(url) {
  if (process.env.NODE_ENV !== 'production') return;

  const interval = 14 * 60 * 1000; // 14 minutes
  const client   = url.startsWith('https') ? https : http;

  console.log(`💓 Keep-alive actif → ping toutes les 14 min`);

  setInterval(() => {
    client.get(`${url}/health`, (res) => {
      console.log(`💓 Keep-alive: ${res.statusCode} ${new Date().toISOString()}`);
    }).on('error', (err) => {
      console.warn(`💓 Keep-alive erreur: ${err.message}`);
    });
  }, interval);
}

module.exports = { startKeepAlive };
