import { Clock3 } from 'lucide-react';
import { TeamBadge } from './Ui';

export function MatchCard({ match, competition = 'PL' }) {
  return (
    <a href={`#match/${match.id}/${competition}`} className="panel match-card block transition-colors hover:border-red-600/40 focus-visible:outline-2 focus-visible:outline-red-600" aria-label={`View ${match.home.name} versus ${match.away.name} details`}>
      <div className="flex justify-between items-center text-xs text-slate-400">
        <span>{match.date} · UTC</span>
        <span className="flex gap-1.5 items-center"><Clock3 size={12} />{match.time}</span>
      </div>
      <div className="flex items-center justify-between gap-3 my-7">
        <div className="match-team"><TeamBadge team={match.home} /><p>{match.home.name}</p></div>
        <span className="text-xs text-slate-300 font-medium">VS</span>
        <div className="match-team"><TeamBadge team={match.away} /><p>{match.away.name}</p></div>
      </div>
      <div className="flex justify-between text-xs text-slate-300 border-t border-white/10 pt-4">
        <span>{match.matchday ? `Matchweek ${match.matchday}` : 'Matchweek to be confirmed'}</span>
        <span>{match.status === 'TIMED' ? 'Kickoff confirmed' : 'Scheduled'}</span>
      </div>
    </a>
  );
}

function Probability({ label, value }) {
  return <div><div className="flex justify-between text-xs mb-2"><span className="text-slate-300">{label}</span><span className="font-medium">{value}%</span></div><div className="h-1.5 bg-white/10 rounded-full overflow-hidden"><div className="h-full bg-[#c3292e]/80 rounded-full" style={{ width: `${value}%` }} /></div></div>;
}
export function PredictionCard({ match, selected }) {
  return <article id={`prediction-${match.id}`} className={`panel p-5 scroll-mt-6 ${selected ? 'selected-card' : ''}`}><div className="flex items-center gap-2 text-sm font-medium mb-5"><TeamBadge team={match.home} small /><span>{match.home.code}</span><span className="text-slate-300 mx-1">vs</span><span>{match.away.code}</span><TeamBadge team={match.away} small /></div><p className="eyebrow mb-3">Match result · 1X2</p><div className="grid grid-cols-3 gap-2 mb-5">{match.result.map((value, index) => <div key={index} className={`probability-cell ${value === Math.max(...match.result) ? 'leading' : ''}`}><span>{['1 · Home', 'X · Draw', '2 · Away'][index]}</span><strong>{value}%</strong></div>)}</div><div className="space-y-4"><Probability label="Over 2.5 goals" value={match.over} /><Probability label="Under 2.5 goals" value={100 - match.over} /><Probability label="BTTS · Yes" value={match.btts} /><Probability label="BTTS · No" value={100 - match.btts} /></div><p className="text-[10px] text-slate-300 border-t border-white/10 pt-4 mt-5">Illustrative probabilities · No predictive model</p></article>;
}
