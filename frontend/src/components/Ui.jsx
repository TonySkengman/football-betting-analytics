import { useState } from 'react';
import { ArrowUpRight } from 'lucide-react';

export function TeamBadge({ team, small = false }) {
  const [failedCrest, setFailedCrest] = useState(null);
  const showCrest = team.crest && team.crest !== failedCrest;

  return (
    <span
      className={`team-badge ${small ? 'small' : ''}`}
      style={{ '--team-color': team.color || '#38BDF8' }}
    >
      {showCrest ? (
        <img
          src={team.crest}
          alt={`${team.name} crest`}
          className="h-full w-full object-contain p-1"
          loading="lazy"
          onError={() => setFailedCrest(team.crest)}
        />
      ) : team.code}
    </span>
  );
}

export function SectionHeading({ eyebrow, title, action, onAction }) {
  return <div className="flex flex-wrap items-end justify-between gap-4 mb-6"><div>{eyebrow && <p className="eyebrow mb-2">{eyebrow}</p>}<h2 className="text-xl font-semibold tracking-tight">{title}</h2></div>{action && <button className="text-button" onClick={onAction}>{action}<ArrowUpRight size={15} /></button>}</div>;
}

export function StatCard({ icon: Icon, label, value, detail, accent }) {
  return <article className="panel p-5"><div className="flex items-center justify-between"><p className="text-sm text-slate-400">{label}</p><Icon size={18} className={accent ? 'text-[#38BDF8]' : 'text-slate-500'} /></div><p className="text-3xl font-semibold tracking-tight mt-5">{value}</p><p className="text-xs text-slate-500 mt-2">{detail}</p></article>;
}
