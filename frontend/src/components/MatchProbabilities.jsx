import { useState } from 'react';
import useBookmakerOdds from '../hooks/useBookmakerOdds';
import BookmakerSelector from './BookmakerSelector';
import { quoteFor } from '../services/bookmakerOdds';
import { SectionHeading } from './Ui';
import OutcomeCard from './OutcomeCard';
import AnalysisSection from './AnalysisSection';

function Market({ title, outcomes, market, resource, bookmaker }) {
  return <article className="panel p-5 min-w-0"><h3 className="font-medium">{title}</h3>
    {outcomes.map(([label, value, outcome]) => <OutcomeCard key={`${label}-${bookmaker}`} label={label} probability={value} bookmaker={bookmaker} quote={quoteFor(resource.data, bookmaker, market, outcome)} />)}
  </article>;
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

export default function MatchProbabilities({ performance, matchId }) {
  const resource = useBookmakerOdds(matchId);
  const [bookmaker, setBookmaker] = useState('');
  const estimate = performance.prediction;
  const available = estimate?.status === 'available' && estimate.probabilities;
  const samples = estimate?.samples;
  return (
    <section className="mt-8" aria-label="Experimental match probabilities">
      <SectionHeading title="Match probabilities" eyebrow="Explore the outcomes" />
      <p className="sample-notice">Experimental, unvalidated estimates for regulation time including stoppage time. Higher probability means more likely according to this model; value also depends on the odds.</p>
      <p className="text-xs text-slate-300 mb-4">Based on up to 20 earlier matches per team across available competitions within one year. {samples && `${performance.home.team.name}: ${samples.homeTotal} matches (${samples.homeMatches} at home); ${performance.away.team.name}: ${samples.awayTotal} matches (${samples.awayMatches} away).`}</p>
      {estimate?.warnings?.length > 0 && <ul className="sample-notice block space-y-2">{estimate.warnings.map(warning => <li key={warning}>{warning}</li>)}</ul>}
      {!available ? (
        <div className="panel p-5" role="status"><h4 className="font-medium mb-2">{estimate?.status === 'insufficient_data' ? 'Insufficient data' : 'Estimates unavailable'}</h4><p className="text-sm text-slate-300">{estimate?.reason || 'Restart the backend to load model estimates.'}</p></div>
      ) : (
        <>
          <BookmakerSelector resource={resource} selected={bookmaker} onSelect={setBookmaker} upcoming={Date.parse(performance.cutoff) > Date.now()} />
          <div className="grid lg:grid-cols-3 gap-4">
            <Market market="h2h" resource={resource} bookmaker={bookmaker} title="Match result · 1X2" outcomes={[[`${performance.home.team.name} win`, estimate.probabilities.homeWin, resource.data?.fixture.home], ['Draw', estimate.probabilities.draw, 'Draw'], [`${performance.away.team.name} win`, estimate.probabilities.awayWin, resource.data?.fixture.away]]} />
            <Market market="totals" resource={resource} bookmaker={bookmaker} title="Total goals · 2.5" outcomes={[[ 'Over 2.5', estimate.probabilities.over25, 'Over'], ['Under 2.5', estimate.probabilities.under25, 'Under']]} />
            <Market market="btts" resource={resource} bookmaker={bookmaker} title="Both teams to score" outcomes={[[ 'Yes', estimate.probabilities.bttsYes, 'Yes'], ['No', estimate.probabilities.bttsNo, 'No']]} />
          </div>
          <AnalysisSection title="Model inputs and formulas">
          <div className="panel p-5 mb-4"><h4 className="font-medium mb-3">Model expected goals</h4><dl className="grid sm:grid-cols-2 gap-4">{[[performance.home.team.name, estimate.homeExpectedGoals], [performance.away.team.name, estimate.awayExpectedGoals]].map(([name, value]) => <div key={name}><dt className="text-xs text-slate-400 mb-1">{name}</dt><dd className="text-2xl">{Number.isFinite(value) ? value.toFixed(2) : 'Unavailable'}</dd></div>)}</dl></div>
          <p className="text-xs text-slate-400 mb-4">{estimate.modelVersion} · Model goal means are not measured shot-based xG.</p><div className="text-slate-300 mt-4 space-y-3 leading-relaxed"><p>Home expected goals = (home-team home goals scored per match + away-team away goals conceded per match) / 2.</p><p>Away expected goals = (away-team away goals scored per match + home-team home goals conceded per match) / 2. No competition scoring baseline is applied to these mixed-competition inputs.</p><p>Both rates receive equal weight regardless of sample sizes. Inputs use confirmed regulation scores from the displayed venue samples. Previous seasons can contribute. Goal counts are assumed independent and Poisson distributed. No injuries, lineups, opponent strength adjustments or recent-form weighting are included. Rounded percentages may not add to exactly 100%.</p><p>Omitted score-grid probability mass: {Number.isFinite(estimate.probabilities.omittedMass) ? estimate.probabilities.omittedMass.toExponential(2) : 'Unavailable'}. The 1X2 probabilities are normalized over the retained grid. Goals and BTTS markets use untruncated formulas.</p></div></AnalysisSection>
        </>
      )}
      <AnalysisSection title="Model match history">
        <div className="grid lg:grid-cols-2 gap-4"><HistorySummary name={performance.home.team.name} history={estimate?.homeHistory} /><HistorySummary name={performance.away.team.name} history={estimate?.awayHistory} /></div>
      </AnalysisSection>
    </section>
  );
}
