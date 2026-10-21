import { quoteFor, quoteFresh } from './bookmakerOdds.js';
import { compareOdds, validProbability } from './oddsComparison.js';

export function prepareOutcomeOverview(performance, odds, bookmaker, now = Date.now()) {
  const estimate = performance?.prediction;
  if (estimate?.status !== 'available' || !estimate.probabilities) return { likely: [], value: [] };
  const p = estimate.probabilities;
  const outcomes = [
    ['home', `${performance.home.team.name} win`, 'Match result', p.homeWin, 'h2h', odds?.fixture?.home],
    ['draw', 'Draw', 'Match result', p.draw, 'h2h', 'Draw'],
    ['away', `${performance.away.team.name} win`, 'Match result', p.awayWin, 'h2h', odds?.fixture?.away],
    ['over', 'Over 2.5 goals', 'Total goals', p.over25, 'totals', 'Over'],
    ['under', 'Under 2.5 goals', 'Total goals', p.under25, 'totals', 'Under'],
    ['bttsYes', 'Both teams to score: Yes', 'Both teams to score', p.bttsYes, 'btts', 'Yes'],
    ['bttsNo', 'Both teams to score: No', 'Both teams to score', p.bttsNo, 'btts', 'No'],
  ].filter(([, , , probability]) => validProbability(probability)).map(([id, label, market, probability, key, outcome]) => {
    const quote = bookmaker && outcome ? quoteFor(odds, bookmaker, key, outcome) : null;
    const fresh = quoteFresh(quote, now);
    const comparison = compareOdds(probability, fresh ? String(quote.decimalOdds) : '');
    return { id, label, market, probability, quote: fresh ? quote : null, ...comparison };
  });
  return {
    likely: [...outcomes].sort((a, b) => b.probability - a.probability),
    value: outcomes.filter(row => row.status === 'positive').sort((a, b) => b.expectedReturn - a.expectedReturn),
  };
}
