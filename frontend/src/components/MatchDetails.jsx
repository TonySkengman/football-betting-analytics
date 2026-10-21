import { useEffect, useState } from 'react';
import { ArrowLeft, CalendarDays, MapPin } from 'lucide-react';
import RecentPerformance from './RecentPerformance';
import { TeamBadge, SectionHeading } from './Ui';
import { LoadingState, EmptyState, ErrorState } from './AsyncStates';
import { fetchMatchDetails, formatScore, formatStatus } from '../services/footballApi';

export default function MatchDetails({ id, standings }) {
  const [match, setMatch] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    fetchMatchDetails(id, controller.signal)
      .then(data => { if (!controller.signal.aborted) setMatch(data); })
      .catch(failure => { if (!controller.signal.aborted) setError(failure.message); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [id, attempt]);

  const retry = () => setAttempt(value => value + 1);
  return (
    <div>
      <a href="#" className="text-button mb-6"><ArrowLeft size={16} />Back to matches</a>
      {loading ? <LoadingState message="Loading match details…" /> : error ? (
        <ErrorState message={error} onRetry={retry} />
      ) : !match ? <EmptyState message="Match details are not available." /> : (
        <>
          <p className="eyebrow mb-3">{match.competition || 'Football match'}</p>
          <h1 className="text-2xl sm:text-3xl font-semibold mb-6">Match details</h1>
          <section className="panel p-5 sm:p-8 mb-8" aria-label="Match summary">
            <div className="flex justify-center mb-7"><span className="league-tag">{formatStatus(match.status)}</span></div>
            <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-3 sm:gap-8">
              <div className="match-team"><TeamBadge team={match.home} /><h2>{match.home.name}</h2><span className="text-slate-500 text-xs">Home</span></div>
              <div className="text-center"><p className="text-3xl sm:text-5xl font-semibold tabular-nums">{formatScore(match.homeScore, match.awayScore)}</p><p className="text-xs text-slate-500 mt-3">Score</p></div>
              <div className="match-team"><TeamBadge team={match.away} /><h2>{match.away.name}</h2><span className="text-slate-500 text-xs">Away</span></div>
            </div>
            <div className="flex flex-wrap justify-center gap-5 mt-8 pt-5 border-t border-white/5 text-xs text-slate-400">
              <span className="flex gap-2 items-center"><CalendarDays size={14} />{match.date} · {match.time} UTC</span>
              {match.venue && <span className="flex gap-2 items-center"><MapPin size={14} />{match.venue}</span>}
              {match.matchday && <span>Matchday {match.matchday}</span>}
            </div>
            {match.homeScore == null && match.awayScore == null && <p className="text-center text-xs text-slate-500 mt-4">A score has not been reported yet.</p>}
          </section>
          <SectionHeading title="Match statistics" eyebrow="On the pitch" />
          {match.statistics.length ? (
            <div className="panel p-5 mb-8">
              <div className="grid grid-cols-3 text-xs text-slate-500 pb-4"><span>{match.home.code}</span><span className="text-center">Statistic</span><span className="text-right">{match.away.code}</span></div>
              {match.statistics.map(stat => <div key={stat.label} className="grid grid-cols-3 py-4 border-t border-white/5 text-sm"><span className="text-sky-300">{stat.home == null ? '—' : `${stat.home}${stat.unit}`}</span><span className="text-center text-slate-400">{stat.label}</span><span className="text-right text-sky-300">{stat.away == null ? '—' : `${stat.away}${stat.unit}`}</span></div>)}
            </div>
          ) : <EmptyState message="Match statistics have not been supplied. Availability depends on kickoff time, coverage and API access." />}
          <RecentPerformance key={id} matchId={id} />
          <div className="mt-8" />
          <SectionHeading title="Team comparison" eyebrow="Current competition standings" />
          {standings.loading ? <LoadingState message="Loading team statistics…" /> : standings.error ? (
            <ErrorState message={standings.error} onRetry={standings.retry} />
          ) : (
            <div className="grid sm:grid-cols-2 gap-4">
              {[match.home, match.away].map(team => {
                const row = standings.data.find(item => item.team.id === team.id);
                return <article className="panel p-5" key={team.id}><h3 className="flex items-center gap-3 font-medium mb-5"><TeamBadge team={team} small />{team.name}</h3>{row ? <dl className="grid grid-cols-2 gap-4 text-sm">{[['Position', row.position], ['Points', row.points], ['Played', row.played], ['Goals scored', row.scored], ['Goals conceded', row.conceded], ['Goal difference', row.goalDifference]].map(([label, value]) => <div key={label}><dt className="text-xs text-slate-500 mb-1">{label}</dt><dd>{value}</dd></div>)}</dl> : <p className="text-sm text-slate-400">No current standings statistics available for this team.</p>}</article>;
              })}
            </div>
          )}
          <p className="text-xs text-slate-500 mt-6">Source: football-data.org · No automatic live refresh</p>
        </>
      )}
    </div>
  );
}
