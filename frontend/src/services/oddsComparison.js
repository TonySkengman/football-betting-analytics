export function validProbability(value) {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= 1;
}

export function compareOdds(probability, rawOdds) {
  const hasProbability = validProbability(probability);
  const fairOdds = hasProbability && probability > 0 ? 1 / probability : null;
  const base = { fairOdds: Number.isFinite(fairOdds) ? fairOdds : null, expectedReturn: null };
  if (rawOdds == null || String(rawOdds).trim() === '') return { ...base, status: 'missing_odds' };
  const text = String(rawOdds).trim();
  const odds = Number(text);
  if (!/^\d+(\.\d+)?$/.test(text) || !Number.isFinite(odds) || odds <= 1) {
    return { ...base, status: 'invalid_odds' };
  }
  if (!hasProbability) return { ...base, status: 'unavailable' };
  const expectedReturn = probability * odds - 1;
  return { ...base, expectedReturn, status: expectedReturn > 1e-12 ? 'positive' : expectedReturn < -1e-12 ? 'negative' : 'neutral' };
}
