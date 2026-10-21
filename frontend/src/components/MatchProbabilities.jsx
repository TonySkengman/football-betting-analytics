import { SectionHeading } from './Ui';

function percentage(value) {
  return `${(value * 100).toFixed(1)}%`;
}

function Market({ title, outcomes }) {
  return (
    <article className="panel p-5">
      <h4 className="text-sm font-medium mb-4">{title}</h4>
      <dl className="space-y-3">
        {outcomes.map(([label, value]) => <div key={label} className="flex justify-between gap-4 border-t border-white/10 pt-3"><dt className="text-sm text-slate-300">{label}</dt><dd className="font-semibold tabular-nums">{percentage(value)}</dd></div>)}
      </dl>
    </article>
  );
}

function HistorySummary({ name, history }) {
  if (!history) return null;
  return (
    <article className="panel p-4 text-xs min-w-0">
      <h4 className="font-medium text-sm mb-2">{name} · Model history</h4>
      {history.error && <p className="text-red-300 mb-3" role="status">{history.error}</p>}
      <p>{history.used} / {history.target} matches selected · {history.homeMatches} home · {history.awayMatches} away</p>
      <p className="mt-2 text-slate-400">{history.firstDate ? `${history.firstDate.slice(0, 10)} to ${history.lastDate.slice(0, 10)}` : 'No contributing date range'}</p>
      <ul className="mt-3 space-y-1">{history.competitions.map(item => <li key={item.code}>{item.name} ({item.code}): {item.matches}</li>)}</ul>
      <p className="mt-3 text-slate-400 leading-relaxed">Excluded in fetched history: {history.excludedScores} unconfirmed regulation scores, {history.excludedMetadata} invalid metadata, {history.outsideWindow} outside the cutoff/status/window, {history.duplicates} duplicates. Older valid matches beyond the latest 20 are not used.</p>
    </article>
  );
}

export default function MatchProbabilities({ performance }) {
  const estimate = performance.prediction;
  const available = estimate?.status === 'available' && estimate.probabilities;
  const samples = estimate?.samples;
  return (
    <section className="mt-8" aria-label="Experimental match probabilities">
      <SectionHeading title="Experimental match estimates" eyebrow="Independent Poisson model" />
      <p className="sample-notice"><span>Experimental, unvalidated model estimates for regulation time including stoppage time. These are not betting recommendations. Model expected goals are not measured shot-based xG.</span></p>
      <p className="text-xs text-slate-300 mb-4">Pre-kickoff model inputs: up to 20 completed matches per team across available competitions and seasons in the preceding 365 days · {estimate?.modelVersion || 'Model unavailable'}. The competition-specific statistics above are separate.</p>
      {samples && <p className="text-xs text-slate-400 mb-5 leading-relaxed">Venue samples used: {performance.home.team.name} at home: {samples.homeMatches}; {performance.away.team.name} away: {samples.awayMatches}. Minimums: {samples.minimumTotalMatches} total matches per team and {samples.minimumVenueMatches} in the relevant venue split. Other venue matches are retained in the history scope but do not enter that team's goal-rate calculation.</p>}
      {estimate?.warnings?.length > 0 && <ul className="sample-notice block space-y-2">{estimate.warnings.map(warning => <li key={warning}>{warning}</li>)}</ul>}
      <div className="grid lg:grid-cols-2 gap-4 mb-5"><HistorySummary name={performance.home.team.name} history={estimate?.homeHistory} /><HistorySummary name={performance.away.team.name} history={estimate?.awayHistory} /></div>
      {!available ? (
        <div className="panel p-5" role="status"><h4 className="font-medium mb-2">{estimate?.status === 'insufficient_data' ? 'Insufficient data' : 'Estimates unavailable'}</h4><p className="text-sm text-slate-300">{estimate?.reason || 'Restart the backend to load model estimates.'}</p></div>
      ) : (
        <>
          <div className="panel p-5 mb-4"><h4 className="font-medium mb-3">Model expected goals</h4><dl className="grid sm:grid-cols-2 gap-4">{[[performance.home.team.name, estimate.homeExpectedGoals], [performance.away.team.name, estimate.awayExpectedGoals]].map(([name, value]) => <div key={name}><dt className="text-xs text-slate-400 mb-1">{name}</dt><dd className="text-2xl">{value.toFixed(2)}</dd></div>)}</dl></div>
          <div className="grid lg:grid-cols-3 gap-4">
            <Market title="Match result · 1X2" outcomes={[[`${performance.home.team.name} win`, estimate.probabilities.homeWin], ['Draw', estimate.probabilities.draw], [`${performance.away.team.name} win`, estimate.probabilities.awayWin]]} />
            <Market title="Total goals · 2.5" outcomes={[[ 'Over 2.5', estimate.probabilities.over25], ['Under 2.5', estimate.probabilities.under25]]} />
            <Market title="Both teams to score" outcomes={[[ 'Yes', estimate.probabilities.bttsYes], ['No', estimate.probabilities.bttsNo]]} />
          </div>
          <details className="panel p-5 mt-4 text-sm"><summary className="cursor-pointer font-medium">How these estimates are calculated</summary><div className="text-slate-300 mt-4 space-y-3 leading-relaxed"><p>Home expected goals = (home-team home goals scored per match + away-team away goals conceded per match) / 2.</p><p>Away expected goals = (away-team away goals scored per match + home-team home goals conceded per match) / 2. No competition scoring baseline is applied to these mixed-competition inputs.</p><p>Both rates receive equal weight regardless of sample sizes. Inputs use confirmed regulation scores from the displayed venue samples. Previous seasons can contribute. Goal counts are assumed independent and Poisson distributed. No injuries, lineups, opponent strength adjustments or recent-form weighting are included. Rounded percentages may not add to exactly 100%.</p><p>Omitted score-grid probability mass: {estimate.probabilities.omittedMass.toExponential(2)}. The 1X2 probabilities are normalized over the retained grid. Goals and BTTS markets use untruncated formulas.</p></div></details>
        </>
      )}
    </section>
  );
}
