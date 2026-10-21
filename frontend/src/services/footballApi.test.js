import test from 'node:test';
import assert from 'node:assert/strict';
import { fetchList, formatMatch, formatStanding, fetchMatchDetails, fetchRecentPerformance, formatScore, formatStatus } from './footballApi.js';

test('loads arrays and supports empty responses', async () => {
  assert.deepEqual(await fetchList('/api/matches', undefined, async () => new Response('[{"id":1}]')), [{ id: 1 }]);
  assert.deepEqual(await fetchList('/api/matches', undefined, async () => new Response(null, { status: 204 })), []);
});
test('surfaces safe backend errors and rejects malformed responses', async () => {
  await assert.rejects(fetchList('/api/matches', undefined, async () => new Response('{"message":"Rate limit reached"}', { status: 429 })), /Rate limit reached/);
  await assert.rejects(fetchList('/api/matches', undefined, async () => new Response('{}')), /unexpected response/);
  await assert.rejects(fetchList('/api/matches', undefined, async () => new Response('<html>')), /unreadable/);
  await assert.rejects(fetchList('/api/matches', undefined, async () => { throw new TypeError('Failed to fetch'); }), /Cannot reach the backend/);
});
test('preserves cancellation', async () => {
  await assert.rejects(fetchList('/api/matches', undefined, async () => { throw new DOMException('Aborted', 'AbortError'); }), { name: 'AbortError' });
});
test('formats UTC kickoff and missing dates', () => {
  assert.equal(formatMatch({ utcDate: '2026-10-24T17:30:00Z' }).time, '17:30');
  assert.equal(formatMatch({ utcDate: null }).time, 'TBC');
});
test('maps standings including missing form and negative goal difference', () => {
  const row = { team: { id: 1, name: 'Team' }, position: 2, played: 8, points: 10, scored: 4, conceded: 9, goalDifference: -5, form: null };
  assert.equal(formatStanding(row).form, '');
  assert.equal(formatStanding(row).goalDifference, -5);
  assert.equal(formatStanding({ ...row, form: 'W,D,L' }).form, 'WDL');
});


test('preserves crest URLs for fixtures and standings', () => {
  const team = { id: 57, name: 'Arsenal', code: 'ARS', crest: 'https://crests.football-data.org/57.png' };
  assert.equal(formatMatch({ home: team }).home.crest, team.crest);
  assert.equal(formatStanding({ team }).crest, team.crest);
});


test('match details preserve zero scores and unavailable statistics', async () => {
  const detail = { id: 7, home: { id: 1 }, away: { id: 2 }, homeScore: 0, awayScore: 0, statistics: [] };
  const match = await fetchMatchDetails(7, undefined, async path => {
    assert.equal(path, '/api/matches/7');
    return new Response(JSON.stringify(detail));
  });
  assert.equal(formatScore(match.homeScore, match.awayScore), '0 : 0');
  assert.equal(formatScore(null, null), '— : —');
  assert.equal(formatStatus('POSTPONED'), 'Postponed');
  assert.deepEqual(match.statistics, []);
});
test('match details handle empty, malformed, and not found responses', async () => {
  assert.equal(await fetchMatchDetails(7, undefined, async () => new Response(null, { status: 204 })), null);
  await assert.rejects(fetchMatchDetails(7, undefined, async () => new Response('{}')), /unexpected match details/);
  await assert.rejects(fetchMatchDetails(7, undefined, async () => new Response('{"message":"Match not found"}', { status: 404 })), /Match not found/);
});


test('recent performance loads both teams without inventing missing results', async () => {
  const performance = { competition: 'Premier League', home: { team: { id: 1 }, results: [], summary: { matches: 0, averageScored: null } }, away: { team: { id: 2 }, results: [], summary: { matches: 0 } } };
  const result = await fetchRecentPerformance(99, undefined, async path => {
    assert.equal(path, '/api/matches/99/performance');
    return new Response(JSON.stringify(performance));
  });
  assert.equal(result.home.summary.averageScored, null);
  assert.deepEqual(result.home.results, []);
});
test('recent performance handles empty responses, rate limits and malformed data', async () => {
  assert.equal(await fetchRecentPerformance(99, undefined, async () => new Response(null, { status: 204 })), null);
  await assert.rejects(fetchRecentPerformance(99, undefined, async () => new Response('{"message":"Rate limit reached"}', { status: 429 })), /Rate limit reached/);
  await assert.rejects(fetchRecentPerformance(99, undefined, async () => new Response('{}')), /unexpected recent performance/);
});


test('performance response preserves experimental availability and genuine zero probabilities', async () => {
  const base = { home: { team: { id: 1 }, summary: {}, results: [] }, away: { team: { id: 2 }, summary: {}, results: [] } };
  const prediction = { modelVersion: 'independent-poisson-v1', status: 'available', homeExpectedGoals: 0, probabilities: { homeWin: 0, bttsYes: 0 } };
  const result = await fetchRecentPerformance(1, undefined, async () => new Response(JSON.stringify({ ...base, prediction })));
  assert.equal(result.prediction.probabilities.homeWin, 0);
  assert.equal(result.prediction.homeExpectedGoals, 0);
  const unavailable = { status: 'insufficient_data', reason: 'Not enough regulation scores', probabilities: null };
  const missing = await fetchRecentPerformance(1, undefined, async () => new Response(JSON.stringify({ ...base, prediction: unavailable })));
  assert.equal(missing.prediction.probabilities, null);
  assert.equal(missing.prediction.reason, unavailable.reason);
});
