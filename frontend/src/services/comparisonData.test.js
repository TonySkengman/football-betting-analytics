import test from 'node:test';
import assert from 'node:assert/strict';
import { prepareComparison, formatComparisonValue } from './comparisonData.js';

function team(name, summary) {
  return { team: { name }, summary };
}

test('prepares separate result and average scales with unequal sample sizes', () => {
  const chart = prepareComparison({
    home: team('Arsenal', { scoredMatches: 5, wins: 3, draws: 1, losses: 1, averageScored: 2.4, averageConceded: 0.8 }),
    away: team('Chelsea', { scoredMatches: 2, wins: 1, draws: 0, losses: 1, averageScored: 1.5, averageConceded: 2 }),
  });
  assert.deepEqual(chart.teams.map(value => [value.name, value.sampleSize]), [['Arsenal', 5], ['Chelsea', 2]]);
  assert.equal(chart.results.length, 3);
  assert.equal(chart.averages.length, 2);
  assert.equal(chart.results[0].home, 3);
  assert.equal(chart.averages[0].home, 2.4);
  assert.equal(chart.averages[1].away, 2);
});

test('preserves measured zero counts and averages', () => {
  const summary = { scoredMatches: 2, wins: 0, draws: 2, losses: 0, averageScored: 0, averageConceded: 0 };
  const chart = prepareComparison({ home: team('Home', summary), away: team('Away', summary) });
  assert.equal(chart.results[0].home, 0);
  assert.equal(chart.averages[0].home, 0);
  assert.equal(formatComparisonValue(0, 'average'), '0.00');
  assert.equal(formatComparisonValue(0, 'count'), '0');
});

test('zero or missing samples never turn placeholder summary zeros into measured values', () => {
  const chart = prepareComparison({
    home: team('Home', { scoredMatches: 0, wins: 0, draws: 0, losses: 0, averageScored: null }),
    away: team('Away', { wins: 0, averageScored: 0 }),
  });
  assert.ok(chart.rows.every(row => row.home === null && row.away === null));
  assert.equal(chart.teams[0].sampleSize, 0);
  assert.equal(chart.teams[1].sampleSize, null);
});

test('missing metrics stay null independently while the other team still has data', () => {
  const chart = prepareComparison({
    home: team('Home', { scoredMatches: 3, wins: null, draws: 1, averageScored: undefined }),
    away: team('Away', { scoredMatches: 1, wins: 1, averageScored: 2 }),
  });
  assert.equal(chart.results[0].home, null);
  assert.equal(chart.results[0].away, 1);
  assert.equal(chart.results[1].home, 1);
  assert.equal(chart.averages[0].home, null);
  assert.equal(formatComparisonValue(null, 'count'), 'Unavailable');
});

test('invalid metrics and missing responses do not produce misleading bars', () => {
  const chart = prepareComparison({ home: team('Home', { scoredMatches: 2, wins: -1, draws: 0.5, losses: '0', averageScored: NaN, averageConceded: Infinity }) });
  assert.ok(chart.rows.every(row => row.home === null && row.away === null));
  assert.ok(prepareComparison(null).rows.every(row => row.home === null));
});
