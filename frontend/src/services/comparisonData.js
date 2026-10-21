const metrics = [
  { label: 'Wins', key: 'wins', type: 'count' },
  { label: 'Draws', key: 'draws', type: 'count' },
  { label: 'Losses', key: 'losses', type: 'count' },
  { label: 'Goals scored', key: 'averageScored', type: 'average' },
  { label: 'Goals conceded', key: 'averageConceded', type: 'average' },
];

function sampleSize(summary) {
  const value = summary?.scoredMatches;
  return Number.isInteger(value) && value >= 0 ? value : null;
}

function metricValue(summary, metric) {
  // A zero-sized sample has no measured results, even if its summary contains zeros.
  if (!(sampleSize(summary) > 0)) return null;
  const value = summary?.[metric.key];
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) return null;
  if (metric.type === 'count' && !Number.isInteger(value)) return null;
  return value;
}

export function prepareComparison(performance) {
  const teams = ['home', 'away'].map(side => ({
    side,
    name: performance?.[side]?.team?.name || 'Unknown team',
    sampleSize: sampleSize(performance?.[side]?.summary),
  }));
  const rows = metrics.map(metric => ({
    label: metric.label,
    type: metric.type,
    home: metricValue(performance?.home?.summary, metric),
    away: metricValue(performance?.away?.summary, metric),
  }));
  return {
    teams,
    results: rows.filter(row => row.type === 'count'),
    averages: rows.filter(row => row.type === 'average'),
    rows,
  };
}

export function formatComparisonValue(value, type) {
  if (value == null) return 'Unavailable';
  return type === 'average' ? value.toFixed(2) : String(value);
}
