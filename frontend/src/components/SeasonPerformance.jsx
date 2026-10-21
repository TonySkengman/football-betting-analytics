import { SectionHeading, TeamBadge } from './Ui';
import { EmptyState } from './AsyncStates';

const metrics = [
  ['Played (used)', 'played'], ['Excluded scores', 'excluded'],
  ['Wins', 'wins'], ['Draws', 'draws'], ['Losses', 'losses'],
  ['Goals scored', 'goalsScored'], ['Goals conceded', 'goalsConceded'],
  ['Avg. scored / match', 'averageScored'], ['Avg. conceded / match', 'averageConceded'],
];

export function displaySeasonValue(value, average = false) {
  return value == null ? 'Unavailable' : average ? value.toFixed(2) : String(value);
}

function SeasonRecord({ title, record }) {
  if (!record) return <p className="text-sm text-slate-300">Season record unavailable.</p>;
  return (
    <div className="panel p-5">
      <h4 className="font-medium mb-3">{title}</h4>
      <p className="text-xs text-slate-400 mb-4">{record.played} matches used · {record.excluded} excluded for incomplete or invalid scores</p>
      <dl className="grid grid-cols-2 sm:grid-cols-3 gap-4 text-sm">
        {metrics.filter(([, key]) => !['played', 'excluded'].includes(key)).map(([label, key]) => <div key={key}><dt className="text-xs text-slate-400 mb-1">{label}</dt><dd>{displaySeasonValue(record[key], key.startsWith('average'))}</dd></div>)}
      </dl>
      {!record.played && <p className="text-xs text-slate-400 mt-4">No complete-score matches in this split. Goal totals and averages are unavailable.</p>}
    </div>
  );
}

function TeamSeason({ team, season }) {
  return (
    <article className="panel p-4 sm:p-5 min-w-0">
      <h4 className="flex items-center gap-3 font-medium mb-4"><TeamBadge team={team} small />{team.name}</h4>
      <div className="overflow-x-auto">
        <table className="w-full text-xs text-left">
          <caption className="sr-only">{team.name} season performance by venue</caption>
          <thead><tr><th scope="col" className="py-3 pr-3">Statistic</th>{['Overall', 'Home', 'Away'].map(label => <th scope="col" key={label} className="px-2 py-3">{label}</th>)}</tr></thead>
          <tbody>{metrics.map(([label, key]) => <tr key={key} className="border-t border-white/10"><th scope="row" className="font-normal py-3 pr-3 text-slate-300">{label}</th>{['overall', 'home', 'away'].map(split => <td key={split} className="px-2 py-3 tabular-nums">{displaySeasonValue(season[split]?.[key], key.startsWith('average'))}</td>)}</tr>)}</tbody>
        </table>
      </div>
    </article>
  );
}

export default function SeasonPerformance({ performance }) {
  const season = performance.seasonPerformance;
  return (
    <section className="mt-8" aria-label="Season performance statistics">
      <SectionHeading title="Season performance" eyebrow="The bigger picture" />
      <p className="text-xs text-slate-300 mb-5 leading-relaxed">{performance.competition} · Season starting {performance.season} · Completed fixtures strictly before {performance.cutoff}. Only complete scores are used. Other competitions, seasons and later fixtures are excluded.</p>
      {!season ? <EmptyState message="Season statistics are unavailable. Restart the backend to load the updated performance response." /> : (
        <>
          <div className="panel p-5 mb-5">
            <h4 className="font-medium mb-2">Competition scoring baseline</h4>
            <p className="text-xs text-slate-400 mb-4">{season.competition.played} matches used · {season.competition.excluded} excluded for incomplete or invalid scores</p>
            <dl className="grid sm:grid-cols-2 gap-4"><div><dt className="text-xs text-slate-400 mb-1">Average home goals / match</dt><dd className="text-xl">{displaySeasonValue(season.competition.averageHomeGoals, true)}</dd></div><div><dt className="text-xs text-slate-400 mb-1">Average away goals / match</dt><dd className="text-xl">{displaySeasonValue(season.competition.averageAwayGoals, true)}</dd></div></dl>
            {!season.competition.played && <p className="text-xs text-slate-400 mt-4">No eligible complete-score fixtures before kickoff.</p>}
          </div>
          <h4 className="font-medium mb-4">Venue matchup</h4>
          <div className="grid xl:grid-cols-2 gap-4 mb-6"><SeasonRecord title={`${performance.home.team.name} · Home record`} record={season.home.home} /><SeasonRecord title={`${performance.away.team.name} · Away record`} record={season.away.away} /></div>
          <h4 className="font-medium mb-4">All venue splits</h4>
          <div className="grid xl:grid-cols-2 gap-4"><TeamSeason team={performance.home.team} season={season.home} /><TeamSeason team={performance.away.team} season={season.away} /></div>
        </>
      )}
    </section>
  );
}
