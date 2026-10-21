export default function BookmakerSelector({ resource, selected, onSelect, upcoming }) {
  const names = [...new Set(resource.data?.prices.map(price => price.bookmaker) || [])].sort();
  return <div className="panel p-5 mb-5">
    <div className="flex flex-wrap justify-between items-center gap-4">
      <div><h3 className="font-medium">Compare bookmaker odds</h3><p className="text-xs text-slate-300 mt-2">Load prices when needed, or enter your own. No automatic refresh.</p></div>
      {upcoming && <button className="secondary-button text-sm" disabled={resource.loading} onClick={() => { onSelect(''); resource.load(); }}>{resource.loading ? 'Loading odds…' : resource.data ? 'Check odds again' : resource.error ? 'Retry odds' : 'Load bookmaker odds'}</button>}
    </div>
    {upcoming && <p className="text-xs text-slate-400 mt-3">Up to 3 credits per uncached lookup · Cached for 10 minutes.</p>}
    {!upcoming && <p className="text-xs text-slate-400 mt-3">Provider odds are available for upcoming matches only.</p>}
    {resource.error && <p role="alert" className="text-sm text-red-300 mt-3">{resource.error} Manual entry is still available.</p>}
    {resource.data && <>
      {!names.length && <p role="status" className="text-sm text-slate-300 mt-4">No supported prices were returned for this match. Enter odds manually instead.</p>}
      <label className="block text-xs mt-4">Odds source<select className="state-select block w-full sm:w-auto mt-2" value={selected} onChange={event => onSelect(event.target.value)}><option value="">Manual entry</option>{names.map(name => <option key={name} value={name}>{name}</option>)}</select></label>
      <p className="text-xs text-slate-400 mt-3">Source: The Odds API · Availability varies by bookmaker. Quotes older than 15 minutes or without an update time are excluded from value comparisons. Exchange commission and account-specific conditions are not included.</p>
    </>}
  </div>;
}
