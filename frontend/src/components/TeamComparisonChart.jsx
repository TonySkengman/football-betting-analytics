import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { EmptyState } from './AsyncStates';
import { SectionHeading } from './Ui';
import { prepareComparison, formatComparisonValue } from '../services/comparisonData';

const teamColors = { home: '#ee858b', away: '#d9c7b0' };

function ComparisonTooltip({ active, payload, label, teams, type }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-md border border-white/15 bg-[#302c2b] p-3 text-xs shadow-lg">
      <p className="font-medium mb-2">{label}</p>
      {teams.map(team => {
        const entry = payload.find(item => item.dataKey === team.side);
        return <p key={team.side} className="mt-1" style={{ color: teamColors[team.side] }}>{team.name}: {formatComparisonValue(entry?.value, type)}</p>;
      })}
    </div>
  );
}

function ComparisonChart({ title, rows, teams, type }) {
  const hasData = rows.some(row => row.home != null || row.away != null);
  return (
    <article className="panel p-4 sm:p-5 min-w-0">
      <h4 className="font-medium mb-1">{title}</h4>
      <p className="text-xs text-slate-400 mb-5">{type === 'count' ? 'Number of matches · integer scale' : 'Goals per match · average scale'}</p>
      {hasData ? (
        <div className="h-64 min-w-0" aria-label={`${title}. Exact values are in the comparison table below.`}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={rows} margin={{ left: -20, right: 8, bottom: 8, top: 10 }} accessibilityLayer>
              <CartesianGrid vertical={false} stroke="#ffffff12" />
              <XAxis dataKey="label" axisLine={false} tickLine={false} tick={{ fill: '#d6d0cb', fontSize: 11 }} interval={0} tickMargin={10} />
              <YAxis axisLine={false} tickLine={false} tick={{ fill: '#bab6b3', fontSize: 11 }} allowDecimals={type !== 'count'} domain={type === 'count' ? [0, 5] : [0, 'auto']} />
              <Tooltip cursor={{ fill: '#ffffff05' }} filterNull={false} content={<ComparisonTooltip teams={teams} type={type} />} />
              {teams.map(team => <Bar key={team.side} dataKey={team.side} name={team.name} fill={teamColors[team.side]} radius={[3, 3, 0, 0]} maxBarSize={35} isAnimationActive={false} />)}
            </BarChart>
          </ResponsiveContainer>
        </div>
      ) : <p className="text-sm text-slate-300 py-12 text-center">No complete statistics available for this comparison.</p>}
    </article>
  );
}

export default function TeamComparisonChart({ performance }) {
  const { teams, results, averages, rows } = prepareComparison(performance);
  const hasData = rows.some(row => row.home != null || row.away != null);
  return (
    <section className="mt-8" aria-label="Recent performance comparison">
      <SectionHeading title="Compare recent performance" eyebrow="Side by side" />
      <p className="text-xs text-slate-300 leading-relaxed mb-4">{performance.competition} · Season starting {performance.season} · Last five completed matches before the selected kickoff, in this competition only.</p>
      <ul className="flex flex-wrap gap-x-6 gap-y-3 mb-5 text-xs">
        {teams.map(team => <li key={team.side} className="flex items-center gap-2"><span className="size-2.5 rounded-sm shrink-0" style={{ background: teamColors[team.side] }} /><span>{team.name} · {team.side === 'home' ? 'Home team' : 'Away team'} · {team.sampleSize ?? 'Unknown'} complete-score matches used</span></li>)}
      </ul>
      <p className="text-xs text-slate-400 mb-5">Each team uses its own available sample. Missing values have no bar; measured zeros remain 0. Compare sample sizes when reading these charts.</p>
      {hasData ? <div className="grid xl:grid-cols-2 gap-4"><ComparisonChart title="Wins, draws and losses" rows={results} teams={teams} type="count" /><ComparisonChart title="Average goals" rows={averages} teams={teams} type="average" /></div> : <EmptyState message="No complete-score matches are available for either team. Comparison charts cannot be calculated yet." />}
      <div className="panel mt-4 overflow-x-auto">
        <table className="w-full text-sm text-left">
          <caption className="text-left p-4 text-xs text-slate-300">Comparison values · result counts and goals per match</caption>
          <thead className="bg-black/20"><tr><th scope="col" className="p-4 font-medium">Statistic</th>{teams.map(team => <th key={team.side} scope="col" className="p-4 font-medium">{team.name}<span className="block text-xs font-normal text-slate-400 mt-1">{team.sampleSize ?? 'Unknown'} matches used</span></th>)}</tr></thead>
          <tbody>{rows.map(row => <tr key={`${row.type}-${row.label}`} className="border-t border-white/10"><th scope="row" className="p-4 font-normal">{row.type === 'average' ? 'Avg. ' : ''}{row.label.toLowerCase()}</th>{teams.map(team => <td key={team.side} className="p-4 tabular-nums">{formatComparisonValue(row[team.side], row.type)}</td>)}</tr>)}</tbody>
        </table>
      </div>
    </section>
  );
}
