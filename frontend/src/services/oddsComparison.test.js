import test from 'node:test';
import assert from 'node:assert/strict';
import { compareOdds, validProbability } from './oddsComparison.js';

test('fair odds and expected return use the unrounded probability', () => {
  const value = compareOdds(0.65, '1.80');
  assert.equal(value.fairOdds, 1 / 0.65);
  assert.ok(Math.abs(value.expectedReturn - 0.17) < 1e-12);
  assert.equal(value.status, 'positive');
});
test('high probability does not guarantee positive value', () => {
  assert.equal(compareOdds(0.9, '1.05').status, 'negative');
  assert.equal(compareOdds(0.5, '2.00').status, 'neutral');
  assert.equal(compareOdds(0.5, '2.10').status, 'positive');
});
test('missing odds never imply value, including high probabilities', () => {
  for (const odds of ['', '   ', null, undefined]) {
    const result = compareOdds(0.99, odds);
    assert.equal(result.status, 'missing_odds');
    assert.equal(result.expectedReturn, null);
  }
});
test('invalid decimal odds are rejected', () => {
  for (const odds of ['0', '1', '-2', 'abc', 'Infinity', '1.8abc', '1,80', '0x10', '1e3', '9'.repeat(400)]) {
    const result = compareOdds(0.5, odds);
    assert.equal(result.status, 'invalid_odds', odds);
    assert.equal(result.expectedReturn, null);
  }
});
test('missing and invalid probabilities cannot become zero estimates', () => {
  for (const probability of [null, undefined, NaN, Infinity, -0.1, 1.1, '0.5']) {
    assert.equal(validProbability(probability), false);
    assert.deepEqual(compareOdds(probability, '2.00'), { fairOdds: null, expectedReturn: null, status: 'unavailable' });
  }
});
test('genuine zero probability has no finite fair odds and a full expected loss', () => {
  assert.deepEqual(compareOdds(0, '10.00'), { fairOdds: null, expectedReturn: -1, status: 'negative' });
  assert.equal(validProbability(0), true);
  assert.equal(compareOdds(1, '1.50').fairOdds, 1);
});
