import { useEffect, useState } from 'react';
import { Menu } from 'lucide-react';
import Sidebar from './components/Sidebar';
import MatchDetails from './components/MatchDetails';
import { SectionHeading } from './components/Ui';
import { MatchCard } from './components/Matches';
import TeamTable from './components/TeamTable';
import AnalysisSection from './components/AnalysisSection';
import { LoadingState, EmptyState, ErrorState } from './components/AsyncStates';
import useFootballData from './hooks/useFootballData';
import { formatMatch, formatStanding } from './services/footballApi';

function DataSection({ resource, emptyMessage, children }) {
  if (resource.loading) return <LoadingState />;
  if (resource.error) return <ErrorState message={resource.error} onRetry={resource.retry} />;
  if (!resource.data.length) return <EmptyState message={emptyMessage} />;
  return children;
}

export default function App() {
  const [hash, setHash] = useState(window.location.hash);
  const [competition, setCompetition] = useState(() => window.location.hash.endsWith('/CL') ? 'CL' : 'PL');
  const [active, setActive] = useState('Dashboard');
  const [menuOpen, setMenuOpen] = useState(false);
  useEffect(() => {
    const handleHash = () => setHash(window.location.hash);
    window.addEventListener('hashchange', handleHash);
    return () => window.removeEventListener('hashchange', handleHash);
  }, []);
  const matchId = /^#match\/(\d+)\/(?:PL|CL)$/.exec(hash)?.[1];
  const competitionName = competition === 'PL' ? 'Premier League' : 'UEFA Champions League';
  const matchResource = useFootballData(`/api/matches?competition=${competition}`);
  const standingsResource = useFootballData(`/api/standings?competition=${competition}`);
  const matches = matchResource.data.map(formatMatch).sort((a, b) => new Date(a.utcDate) - new Date(b.utcDate));
  const teams = standingsResource.data.map(formatStanding);
  function navigate(page) {
    window.location.hash = '';
    setActive(page);
    setMenuOpen(false);
  }
  const standings = <DataSection resource={standingsResource} emptyMessage={`No ${competitionName} standings available.`}><TeamTable teams={teams} competitionName={competitionName} /></DataSection>;
  return <div className="app-shell">
    <Sidebar active={active} onNavigate={navigate} open={menuOpen} onClose={() => setMenuOpen(false)} />
    <div className="main-shell">
      <header className="topbar">
        <div className="flex items-center gap-3"><button className="icon-button lg:hidden" onClick={() => setMenuOpen(true)} aria-expanded={menuOpen} aria-label="Open navigation"><Menu size={21} /></button><span className="text-sm">{matchId ? 'Match details' : active}</span></div>
        <span className="sample-pill">football-data.org</span>
      </header>
      <main className="main-content">
        {matchId ? <MatchDetails key={matchId} id={matchId} standings={standingsResource} /> : <>
          <div className="flex flex-wrap items-end justify-between gap-5 mb-7">
            <div><p className="eyebrow mb-3">Football Betting Analytics</p><h1 className="text-3xl sm:text-4xl font-semibold tracking-tight">{active === 'Dashboard' ? 'Explore the next match.' : active}</h1><p className="text-sm text-slate-300 mt-3">{active === 'Teams' ? 'Explore the current competition standings.' : 'Open a match to see estimated probabilities and compare your odds.'}</p></div>
            <label className="league-tag">Competition<select aria-label="Competition" className="state-select" value={competition} onChange={event => setCompetition(event.target.value)}><option value="PL">Premier League</option><option value="CL">UEFA Champions League</option></select></label>
          </div>
          {active === 'Teams' ? standings : <>
            <p className="sample-notice">Real fixtures from football-data.org. Match estimates are experimental and unvalidated. Odds comparisons depend on the estimates being accurate.</p>
            <section className="mb-8">
              <SectionHeading title="Upcoming matches" eyebrow={competitionName} />
              <DataSection resource={matchResource} emptyMessage={`No upcoming ${competitionName} matches found.`}>
                <div className="grid md:grid-cols-2 xl:grid-cols-3 gap-4">{matches.map(match => <MatchCard key={match.id} match={match} competition={competition} />)}</div>
              </DataSection>
            </section>
            <AnalysisSection title="Current standings">{standings}</AnalysisSection>
          </>}
        </>}
        <footer className="footer"><span>Touchline · Football Betting Analytics</span><span>Source: football-data.org · No automatic live refresh</span></footer>
      </main>
    </div>
  </div>;
}
