import test from 'node:test';
import assert from 'node:assert/strict';
import { prepareOutcomeOverview } from './outcomeOverview.js';

const now = Date.parse('2026-10-21T20:10:00Z');
const performance = {
  home: { team: { name: 'Arsenal FC' } }, away: { team: { name: 'Everton FC' } },
  prediction: { status: 'available', probabilities: { homeWin: 0.7, draw: 0.2, awayWin: 0.1, over25: 0.6, under25: 0.4, bttsYes: 0.55, bttsNo: 0.45 } },
};
const quote = (market, outcome, decimalOdds, extra = {}) => ({ bookmaker: 'Swedish Book', market, outcome, decimalOdds, updatedAt: '2026-10-21T20:05:00Z', ...extra });
const data = prices => ({ fixture: { home: 'Arsenal', away: 'Everton' }, prices });

test('sorts probabilities without requiring odds and does not mutate response', () => {
  const original = structuredClone(performance);
  const result = prepareOutcomeOverview(performance, null, '', now);
  assert.deepEqual(result.likely.map(row => row.id), ['home', 'over', 'bttsYes', 'bttsNo', 'under', 'draw', 'away']);
  assert.deepEqual(result.value, []);
  assert.deepEqual(performance, original);
});
test('ranks positive expected return using only the selected bookmaker and provider team names', () => {
  const odds = data([quote('h2h', 'Arsenal', 1.5), quote('totals', 'Over', 2), quote('h2h', 'Draw', 5), quote('btts', 'Yes', 1.5), quote('h2h', 'Everton', 100, { bookmaker: 'Other Book' })]);
  const result = prepareOutcomeOverview(performance, odds, 'Swedish Book', now);
  assert.deepEqual(result.value.map(row => row.id), ['over', 'home']);
  assert.ok(Math.abs(result.value[0].expectedReturn - 0.2) < 1e-12);
  assert.equal(result.likely[0].id, 'home');
});
test('missing stale future and invalid quotes never become value', () => {
  const odds = data([quote('h2h', 'Arsenal', 3, { updatedAt: '2026-10-21T19:00:00Z' }), quote('totals', 'Over', 3, { updatedAt: null }), quote('btts', 'Yes', 3, { updatedAt: '2026-10-21T21:00:00Z' }), quote('btts', 'No', NaN)]);
  assert.deepEqual(prepareOutcomeOverview(performance, odds, 'Swedish Book', now).value, []);
  assert.deepEqual(prepareOutcomeOverview(performance, odds, '', now).value, []);
});
test('preserves zero probabilities and excludes missing or invalid estimates', () => {
  const input = structuredClone(performance);
  input.prediction.probabilities = { homeWin: 0, draw: null, awayWin: NaN, over25: -1, under25: 2 };
  const result = prepareOutcomeOverview(input, data([quote('h2h', 'Arsenal', 100)]), 'Swedish Book', now);
  assert.equal(result.likely.length, 1);
  assert.equal(result.likely[0].probability, 0);
  assert.equal(result.value.length, 0);
  input.prediction.status = 'insufficient_data';
  assert.deepEqual(prepareOutcomeOverview(input, null, '', now), { likely: [], value: [] });
});
test('fresh quotes disappear from value ranking when they age beyond the cutoff', () => {
  const odds = data([quote('h2h', 'Arsenal', 2)]);
  assert.equal(prepareOutcomeOverview(performance, odds, 'Swedish Book', now).value.length, 1);
  assert.equal(prepareOutcomeOverview(performance, odds, 'Swedish Book', now + 11 * 60000).value.length, 0);
});


test('switching the selected bookmaker changes the bet ranking without comparing bookmakers', () => {
  const odds = data([quote('h2h', 'Arsenal', 1.5), quote('totals', 'Over', 2),
    quote('h2h', 'Arsenal', 2, { bookmaker: 'Svenska Spel' }),
    quote('totals', 'Over', 1.8, { bookmaker: 'Svenska Spel' })]);
  assert.deepEqual(prepareOutcomeOverview(performance, odds, 'Swedish Book', now).value.map(row => row.id), ['over', 'home']);
  const selected = prepareOutcomeOverview(performance, odds, 'Svenska Spel', now).value;
  assert.deepEqual(selected.map(row => row.id), ['home', 'over']);
  assert.ok(selected.every(row => row.quote.bookmaker === 'Svenska Spel'));
  assert.deepEqual(prepareOutcomeOverview(performance, odds, '', now).value, []);
});
test('does not borrow a price when the selected bookmaker has no outcome or only a stale quote', () => {
  const odds = data([quote('h2h', 'Arsenal', 2, { bookmaker: 'Other Book' }),
    quote('h2h', 'Arsenal', 1.8, { updatedAt: '2026-10-21T19:00:00Z' })]);
  assert.deepEqual(prepareOutcomeOverview(performance, odds, 'Swedish Book', now).value, []);
});
