import { useEffect, useState } from 'react';
import { prepareOutcomeOverview } from '../services/outcomeOverview';

function OutcomeList({ rows, value = false }) {
  return <ol className="divide-y divide-white/10 mt-4">
    {rows.map(row => <li key={row.id} className="py-3">
      <div className="flex justify-between items-start gap-3 text-sm"><span>{row.label}</span><strong className="tabular-nums whitespace-nowrap">{value ? `+${(row.expectedReturn * 100).toFixed(1)}%` : `${(row.probability * 100).toFixed(1)}%`}</strong></div>
      <p className="text-xs text-slate-400 mt-1">{row.market} · {value ? 'Estimated value (expected return)' : 'Estimated probability'}</p>
      {value && <div className="text-xs text-slate-300 mt-3 space-y-2">
        <p>Estimated probability {(row.probability * 100).toFixed(1)}% · Odds {row.quote.decimalOdds.toFixed(2)}</p>
        <p>Fair odds according to the model: {row.fairOdds.toFixed(2)}</p>
        <p className="text-slate-400">Updated: {new Date(row.quote.updatedAt).toISOString().replace('T', ' ').replace('.000Z', ' UTC')}</p>
      </div>}
    </li>)}
  </ol>;
}

export default function OutcomeOverview({ performance, resource, bookmaker }) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 30000);
    return () => clearInterval(timer);
  }, []);
  const { likely, value } = prepareOutcomeOverview(performance, resource.data, bookmaker, Math.max(now, Date.now()));
  return <section className="mb-6" aria-label="Outcome overview">
    <p className="text-xs text-slate-300 mb-4">Experimental model overview. Outcomes across these markets can overlap; they are not independent picks or a combined bet.</p>
    <div className="grid lg:grid-cols-2 gap-4">
      <article className="panel p-5 min-w-0">
        <h3 className="font-semibold">Bets ranked by potential value</h3>
        <p className="text-xs text-slate-300 mt-2">{bookmaker ? `Using ${bookmaker} prices for this match. Ordered by estimated expected return per unit staked.` : 'Load odds and select a bookmaker to rank this match’s bets by potential value.'}</p>
        {resource.loading ? <p role="status" className="text-sm text-slate-300 mt-4">Loading bookmaker odds…</p>
          : resource.error ? <p className="text-sm text-slate-300 mt-4">Bookmaker odds are unavailable. Retry above or use manual entry in the cards below.</p>
          : value.length ? <OutcomeList rows={value} value />
          : <p role="status" className="text-sm text-slate-300 mt-4">{!resource.data ? 'Load bookmaker odds above to compare bet types.' : !bookmaker ? 'Select a bookmaker above. Manual comparisons remain available in the cards below.' : 'No potential value found among the selected bookmaker’s available, fresh quotes.'}</p>}
        <p className="text-xs text-slate-400 mt-4">Potential value according to the model, conditional on its estimates being accurate. Missing or stale quotes are excluded. This is not a guaranteed return or a betting recommendation.</p>
      </article>
      <article className="panel p-5 min-w-0">
        <h3 className="font-semibold">Most likely</h3>
        <p className="text-xs text-slate-300 mt-2">Ordered by estimated probability. A high probability does not establish value.</p>
        {likely.length ? <OutcomeList rows={likely} /> : <p className="text-sm text-slate-300 mt-4">No outcome probabilities available.</p>}
      </article>
    </div>
  </section>;
}
