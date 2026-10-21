import { quoteFresh } from '../services/bookmakerOdds';
import { useEffect, useId, useState } from 'react';
import { compareOdds, validProbability } from '../services/oddsComparison';

export default function OutcomeCard({ label, probability, bookmaker = '', quote = null }) {
  const [odds, setOdds] = useState('');
  const inputId = useId();
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (!bookmaker) return;
    const timer = setInterval(() => setNow(Date.now()), 30000);
    return () => clearInterval(timer);
  }, [bookmaker]);
  const fresh = quoteFresh(quote, now);
  const comparison = compareOdds(probability, bookmaker ? fresh ? String(quote.decimalOdds) : '' : odds);
  const available = validProbability(probability);
  return <div className="border-t border-white/10 pt-4 mt-4">
    <div className="flex items-start justify-between gap-3"><h4 className="text-sm">{label}</h4><span className="text-2xl font-semibold tabular-nums">{available ? `${(probability * 100).toFixed(1)}%` : 'Unavailable'}</span></div>
    <p className="text-xs text-slate-400 mt-2">Fair odds: {comparison.fairOdds == null ? 'Unavailable' : comparison.fairOdds.toFixed(2)}</p>
    {bookmaker ? <div className="text-xs text-slate-300 mt-4">
      <p>{bookmaker}: <strong>{quote ? quote.decimalOdds.toFixed(2) : 'Not available for this outcome'}</strong></p>
      {quote && <p className="mt-2">Updated: {Number.isFinite(Date.parse(quote.updatedAt)) ? new Date(quote.updatedAt).toISOString().replace('T', ' ').replace('.000Z', ' UTC') : 'Unknown'}</p>}
      {quote && !fresh && <p className="text-amber-200 mt-2">Quote is stale or its time cannot be confirmed. Value comparison is unavailable.</p>}
      {!quote && <p className="mt-2">Choose another bookmaker or use manual entry.</p>}
    </div> : <>
    <label htmlFor={inputId} className="block text-xs text-slate-300 mt-4 mb-2">Bookmaker odds (optional)</label>
    <input id={inputId} type="text" inputMode="decimal" autoComplete="off" placeholder="e.g. 1.80" value={odds} onChange={event => setOdds(event.target.value)} aria-invalid={comparison.status === 'invalid_odds'} aria-describedby={`${inputId}-result`} className="w-full min-w-0 rounded-md border border-white/20 bg-black/20 p-2 text-sm focus-visible:outline-2 focus-visible:outline-red-400" />
    </>}
    <div id={`${inputId}-result`} aria-live="polite" className="text-xs leading-relaxed mt-3 text-slate-300">
      {comparison.status === 'missing_odds' && !bookmaker && <p>Enter odds to compare value. A high probability alone does not mean good value.</p>}
      {comparison.status === 'invalid_odds' && <p className="text-red-300">Enter decimal odds greater than 1, using a dot (e.g. 1.80).</p>}
      {comparison.status === 'unavailable' && <p>A probability estimate is needed to compare value.</p>}
      {comparison.expectedReturn != null && <>
        <p className={comparison.status === 'positive' ? 'text-amber-200' : ''}>{comparison.status === 'positive' ? 'Potential value according to the model' : comparison.status === 'negative' ? 'No positive value according to the model' : 'Break-even according to the model'}</p>
        <p className="mt-1">Expected return per unit staked: {comparison.expectedReturn > 0 ? '+' : ''}{comparison.expectedReturn.toFixed(3)} ({(comparison.expectedReturn * 100).toFixed(1)}%).</p>
        <p className="mt-1">This comparison depends on the estimate being accurate; it is not a guaranteed return.</p>
      </>}
    </div>
  </div>;
}
