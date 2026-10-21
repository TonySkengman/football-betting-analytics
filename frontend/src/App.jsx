import { lazy, Suspense, useEffect, useState } from 'react';
import { Activity, CalendarDays, FlaskConical, Goal, Menu, Shield, Sparkles } from 'lucide-react';
import Sidebar from './components/Sidebar';
import MatchDetails from './components/MatchDetails';
import { SectionHeading, StatCard } from './components/Ui';
import { MatchCard, PredictionCard } from './components/Matches';
import TeamTable from './components/TeamTable';
const GoalsChart = lazy(() => import('./components/GoalsChart'));
import { LoadingState, EmptyState, ErrorState } from './components/AsyncStates';
import { goalTrend, matches as sampleMatches } from './data/sampleData';
import useFootballData from './hooks/useFootballData';
import { formatMatch, formatStanding } from './services/footballApi';

function DataSection({ resource, emptyMessage, children }) {
  if (resource.loading) return <LoadingState />;
  if (resource.error) return <ErrorState message={resource.error} onRetry={resource.retry} />;
  if (!resource.data.length) return <EmptyState message={emptyMessage} />;
  return children;
}

const descriptions = {
  Dashboard: 'The numbers behind your next football insight.',
  Matches: 'Explore upcoming fixtures and compare the matchups.',
  Teams: 'A closer look at team performance and recent form.',
  Predictions: 'Explore illustrative match probabilities across key markets.',
};
export default function App() {
  const [hash, setHash] = useState(window.location.hash);
  useEffect(() => {
    const handleHash = () => setHash(window.location.hash);
    window.addEventListener('hashchange', handleHash);
    return () => window.removeEventListener('hashchange', handleHash);
  }, []);
  const matchId = /^#match\/(\d+)\/(?:PL|CL)$/.exec(hash)?.[1];
  const [competition, setCompetition] = useState(() => window.location.hash.endsWith('/CL') ? 'CL' : 'PL');
  const competitionName = competition === 'PL' ? 'Premier League' : 'UEFA Champions League';
  const matchResource = useFootballData(`/api/matches?competition=${competition}`);
  const standingsResource = useFootballData(`/api/standings?competition=${competition}`);
  const matches = matchResource.data.map(formatMatch).sort((a, b) => new Date(a.utcDate) - new Date(b.utcDate));
  const teams = standingsResource.data.map(formatStanding);
  const [active, setActive] = useState('Dashboard');
  const [menuOpen, setMenuOpen] = useState(false);
  const [selectedMatch, setSelectedMatch] = useState(null);
  const [previewState, setPreviewState] = useState('ready');
  function navigate(page) {
    window.location.hash = '';
    setActive(page);
    setMenuOpen(false);
    setPreviewState('ready');
    setSelectedMatch(null);
  }
  const dashboard = active === 'Dashboard';
  return <div className="app-shell"><Sidebar active={active} onNavigate={navigate} open={menuOpen} onClose={() => setMenuOpen(false)} /><div className="main-shell"><header className="topbar"><div className="flex items-center gap-3"><button className="icon-button lg:hidden" onClick={() => setMenuOpen(true)} aria-expanded={menuOpen} aria-label="Open navigation"><Menu size={21} /></button><span className="text-slate-500 text-sm hidden sm:inline">Workspace <span className="mx-3 text-slate-700">/</span></span><span className="text-sm">{active}</span></div><span className="sample-pill"><span className="size-1.5 rounded-full bg-amber-300" />football-data.org</span></header><main className="main-content">{matchId ? <MatchDetails key={`${competition}-${matchId}`} id={matchId} standings={standingsResource} /> : <><div className="flex flex-wrap items-end justify-between gap-5 mb-7"><div><p className="eyebrow text-[#38BDF8] mb-3">Football Betting Analytics</p><h1 className="text-3xl sm:text-4xl font-semibold tracking-tight">{dashboard ? 'Your football intelligence.' : active}</h1><p className="text-sm text-slate-500 mt-3">{descriptions[active]}</p></div><div className="league-tag"><span className="text-[#38BDF8]"><Shield size={16} /></span><select aria-label="Competition" className="state-select" value={competition} onChange={event => { setCompetition(event.target.value); setPreviewState('ready'); }}><option value="PL">Premier League</option><option value="CL">UEFA Champions League</option></select><span className="text-slate-600">/</span><span className="text-slate-400">Current season</span></div></div><div className="sample-notice"><FlaskConical size={17} className="shrink-0 text-amber-300/80" /><p><strong>{competitionName} data.</strong> Matches and standings are supplied by football-data.org. Chart and prediction cards remain fictional samples, not betting advice.</p></div>{dashboard && <section className="grid grid-cols-2 xl:grid-cols-4 gap-3 sm:gap-4 mb-9" aria-label="Overview statistics"><StatCard icon={CalendarDays} label="Upcoming matches" value={matchResource.loading || matchResource.error ? '—' : matches.length} detail="Scheduled this season" /><StatCard icon={Shield} label="Teams tracked" value={standingsResource.loading || standingsResource.error ? '—' : teams.length} detail={competition === 'CL' ? 'League-phase table' : 'Current league table'} /><StatCard icon={Goal} label="Goals scored" value={standingsResource.loading || standingsResource.error ? '—' : teams.reduce((total, team) => total + team.scored, 0)} detail="Goals in the current table" accent /><StatCard icon={Activity} label="Markets covered" value="3" detail="Sample markets · Not implemented" /></section>}<div className="flex flex-wrap items-center justify-between gap-3 mb-6"><div className="flex items-center gap-2 text-xs text-slate-500"><span className="size-1.5 rounded-full bg-[#38BDF8]" />{dashboard ? 'Season overview' : `${active} overview`}</div><label className="flex items-center gap-2 text-xs text-slate-500">Preview state<select value={previewState} onChange={event => setPreviewState(event.target.value)} className="state-select"><option value="ready">API data</option><option value="loading">Loading</option><option value="empty">Empty</option><option value="error">Error</option></select></label></div>{previewState === 'loading' ? <LoadingState /> : previewState === 'empty' ? <EmptyState message={`No ${active === 'Teams' ? 'teams' : active === 'Predictions' ? 'predictions' : 'matches'} available for this view.`} /> : previewState === 'error' ? <ErrorState onRetry={() => setPreviewState('ready')} /> : <>{(dashboard || active === 'Matches') && <section className="mb-10"><SectionHeading title="Upcoming matches" eyebrow="The next whistle" action={dashboard ? 'All matches' : undefined} onAction={() => navigate('Matches')} /><DataSection resource={matchResource} emptyMessage={`No upcoming ${competitionName} matches found.`}><div className="grid md:grid-cols-3 gap-4">{(dashboard ? matches.slice(0, 3) : matches).map(match => <MatchCard key={match.id} match={match} competition={competition} />)}</div></DataSection></section>}{dashboard && <section className="mb-10"><SectionHeading title="The scoring picture · Sample data" eyebrow="Performance trends" /><Suspense fallback={<LoadingState message="Loading chart…" />}><GoalsChart data={goalTrend} /></Suspense></section>}{(dashboard || active === 'Predictions') && <section className="mb-10"><SectionHeading title="Match predictions · Sample data" eyebrow="Explore the probabilities" action={dashboard ? 'All predictions' : undefined} onAction={() => navigate('Predictions')} /><div className="grid md:grid-cols-3 gap-4">{sampleMatches.map(match => <PredictionCard key={match.id} match={match} selected={selectedMatch === match.id} />)}</div><p className="text-xs text-slate-500 mt-4 flex items-center gap-2"><Sparkles size={13} />Premier League sample percentages for interface demonstration only; unrelated to the selected competition.</p></section>}{(dashboard || active === 'Teams') && <section className="mb-8"><SectionHeading title={competition === 'CL' ? 'Champions League · League-phase standings' : 'Premier League · Standings'} eyebrow="Beyond the scoreline" action={dashboard ? 'All teams' : undefined} onAction={() => navigate('Teams')} /><DataSection resource={standingsResource} emptyMessage={competition === 'CL' ? 'No Champions League standings available. A league-phase table may not be available during knockout rounds.' : 'No Premier League standings available.'}><TeamTable teams={teams} competitionName={competitionName} /></DataSection></section>}</>}</>}<footer className="footer"><span>Touchline <span className="text-slate-700 mx-2">/</span> Football Betting Analytics</span><span>Data: football-data.org · Chart and predictions: samples</span></footer></main></div></div>;
}
