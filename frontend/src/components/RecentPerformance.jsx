import { lazy, Suspense, useEffect, useState } from 'react';
import SeasonPerformance from './SeasonPerformance';
import MatchProbabilities from './MatchProbabilities';
import { TeamBadge, SectionHeading } from './Ui';
import { EmptyState, ErrorState, LoadingState } from './AsyncStates';
import { fetchRecentPerformance, formatMatch } from '../services/footballApi';

const TeamComparisonChart = lazy(() => import('./TeamComparisonChart'));

function TeamPerformance({ performance }) {
  const { team, results, summary } = performance;
  const average = value => value == null ? '—' : value.toFixed(2);
  return (
    <article className="panel p-5 min-w-0">
      <h3 className="flex items-center gap-3 font-medium mb-3"><TeamBadge team={team} small />{team.name}</h3>
      <p className="text-xs text-slate-300 mb-5">{results.length} of 5 matches available · newest first</p>
      {!results.length ? <p className="text-sm text-slate-300">No completed matches before kickoff in this competition season.</p> : (
        <>
          <dl className="grid grid-cols-3 gap-4 mb-6">
            {[
              ['Wins', summary.scoredMatches ? summary.wins : '—'],
              ['Draws', summary.scoredMatches ? summary.draws : '—'],
              ['Losses', summary.scoredMatches ? summary.losses : '—'],
              ['Goals scored', summary.goalsScored ?? '—'],
              ['Goals conceded', summary.goalsConceded ?? '—'],
              ['Avg. scored', average(summary.averageScored)],
              ['Avg. conceded', average(summary.averageConceded)],
            ].map(([label, value]) => <div key={label}><dt className="text-[11px] text-slate-400 mb-1">{label}</dt><dd className="text-lg font-medium">{value}</dd></div>)}
          </dl>
          <p className="text-xs text-slate-400 mb-4">Calculations use {summary.scoredMatches} matches with complete scores. Averages are per match.</p>
          <ul className="divide-y divide-white/10">
            {results.map(result => (
              <li key={result.id} className="py-4">
                <div className="flex items-center gap-2 text-sm">
                  <TeamBadge team={result.opponent} small />
                  <span className="flex-1 min-w-0">{result.opponent.name}</span>
                  <span className="font-medium tabular-nums whitespace-nowrap">{result.scored ?? '—'} : {result.conceded ?? '—'}</span>
                  {result.outcome ? <span className={`form-result form-${result.outcome}`} aria-label={{ W: 'Win', D: 'Draw', L: 'Loss' }[result.outcome]}>{result.outcome}</span> : <span className="text-xs text-slate-400">Unknown</span>}
                </div>
                <p className="text-xs text-slate-400 mt-2">{formatMatch(result).date} · {result.venue} · Team score first</p>
              </li>
            ))}
          </ul>
        </>
      )}
    </article>
  );
}

export default function RecentPerformance({ matchId }) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    fetchRecentPerformance(matchId, controller.signal)
      .then(result => { if (!controller.signal.aborted) setData(result); })
      .catch(failure => { if (!controller.signal.aborted) setError(failure.message); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [matchId, attempt]);

  return (
    <section className="mt-8" aria-label="Recent team performance">
      <SectionHeading title="Recent performance" eyebrow="The last five" />
      {loading ? <LoadingState message="Loading recent results…" /> : error ? (
        <ErrorState message={error} onRetry={() => setAttempt(value => value + 1)} />
      ) : !data ? <EmptyState message="Recent performance is unavailable." /> : (
        <>
          <p className="text-xs text-slate-300 mb-5 leading-relaxed">{data.competition} · Season starting {data.season} only · Before {formatMatch({ utcDate: data.cutoff }).date}, {formatMatch({ utcDate: data.cutoff }).time} UTC. Other competitions and previous seasons are excluded.</p>
          <div className="grid xl:grid-cols-2 gap-4">
            <TeamPerformance performance={data.home} />
            <TeamPerformance performance={data.away} />
          </div>
          <Suspense fallback={<LoadingState message="Loading comparison charts…" />}><TeamComparisonChart performance={data} /></Suspense>
          <SeasonPerformance performance={data} />
          <MatchProbabilities performance={data} />
        </>
      )}
    </section>
  );
}
