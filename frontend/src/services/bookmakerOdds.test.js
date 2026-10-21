import test from 'node:test';
import assert from 'node:assert/strict';
import { fetchBookmakerOdds, quoteFor, quoteFresh } from './bookmakerOdds.js';

test('selects exact bookmaker, market and outcome without falling back to another quote', () => {
  const price = { bookmaker: 'A', market: 'totals', outcome: 'Over', decimalOdds: 1.9 };
  const data = { prices: [price, { ...price, bookmaker: 'B', decimalOdds: 2.2 }] };
  assert.equal(quoteFor(data, 'A', 'totals', 'Over'), price);
  assert.equal(quoteFor(data, 'A', 'btts', 'Yes'), null);
  assert.equal(quoteFor(data, 'C', 'totals', 'Over'), null);
  assert.equal(quoteFor({ prices: [{ ...price, decimalOdds: 0 }] }, 'A', 'totals', 'Over'), null);
});
test('freshness excludes stale, missing, invalid and future timestamps', () => {
  const now = Date.parse('2026-10-21T20:10:00Z');
  assert.equal(quoteFresh({ updatedAt: '2026-10-21T20:00:00Z' }, now), true);
  assert.equal(quoteFresh({ updatedAt: '2026-10-21T19:55:00Z' }, now), true);
  assert.equal(quoteFresh({ updatedAt: '2026-10-21T19:54:59Z' }, now), false);
  assert.equal(quoteFresh({ updatedAt: '2026-10-21T20:11:00Z' }, now), false);
  assert.equal(quoteFresh({ updatedAt: null }, now), false);
  assert.equal(quoteFresh(null, now), false);
});
test('loads odds only through backend and preserves empty market responses', async () => {
  const response = { fixture: { home: 'A', away: 'B' }, prices: [], missingMarkets: ['btts'] };
  const data = await fetchBookmakerOdds(42, undefined, async path => {
    assert.equal(path, '/api/odds/matches/42');
    return { ok: true, json: async () => response };
  });
  assert.deepEqual(data, response);
});
test('surfaces errors, malformed data and cancellation', async () => {
  await assert.rejects(fetchBookmakerOdds(42, undefined, async () => ({ ok: false, status: 429, json: async () => ({ message: 'Quota reached' }) })), /Quota reached/);
  await assert.rejects(fetchBookmakerOdds(42, undefined, async () => ({ ok: true, json: async () => ({}) })), /unexpected bookmaker odds/);
  const cancellation = new DOMException('Cancelled', 'AbortError');
  await assert.rejects(fetchBookmakerOdds(42, undefined, async () => { throw cancellation; }), error => error === cancellation);
});
