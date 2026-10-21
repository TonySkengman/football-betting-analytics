import test from 'node:test';
import assert from 'node:assert/strict';
import { fetchList, formatMatch, formatStanding } from './footballApi.js';

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
