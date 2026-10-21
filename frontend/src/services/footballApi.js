async function fetchJson(path, signal, fetcher = fetch) {
  let response;
  try {
    response = await fetcher(path, { signal });
  } catch (error) {
    if (error.name === 'AbortError') throw error;
    throw new Error('Cannot reach the backend. Check that Spring Boot is running.');
  }
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || `Could not load football data (${response.status}).`);
  }
  if (response.status === 204) return null;
  const data = await response.json().catch(() => {
    throw new Error('The backend returned an unreadable response.');
  });
  return data;
}

export async function fetchList(path, signal, fetcher = fetch) {
  const data = await fetchJson(path, signal, fetcher);
  if (data === null) return [];
  if (!Array.isArray(data)) throw new Error('The backend returned an unexpected response.');
  return data;
}

export async function fetchMatchDetails(id, signal, fetcher = fetch) {
  const data = await fetchJson(`/api/matches/${id}`, signal, fetcher);
  if (!data) return null;
  if (!data.id || !data.home || !data.away || !Array.isArray(data.statistics)) {
    throw new Error('The backend returned unexpected match details.');
  }
  return formatMatch(data);
}

export function formatScore(home, away) {
  return `${home ?? '—'} : ${away ?? '—'}`;
}

export function formatStatus(status) {
  const labels = { TIMED: 'Kickoff confirmed', SCHEDULED: 'Scheduled', IN_PLAY: 'In play',
    PAUSED: 'Half-time', FINISHED: 'Finished', POSTPONED: 'Postponed',
    SUSPENDED: 'Suspended', CANCELLED: 'Cancelled', AWARDED: 'Awarded' };
  return labels[status] || status?.replaceAll('_', ' ') || 'Status unavailable';
}

export function formatMatch(match) {
  const date = new Date(match.utcDate);
  const valid = match.utcDate && !Number.isNaN(date.getTime());
  return {
    ...match,
    date: valid ? new Intl.DateTimeFormat('en-GB', {
      weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC',
    }).format(date) : 'Date to be confirmed',
    time: valid ? new Intl.DateTimeFormat('en-GB', {
      hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZone: 'UTC',
    }).format(date) : 'TBC',
  };
}

export function formatStanding(row) {
  return {
    ...row.team,
    position: row.position,
    played: row.played,
    points: row.points,
    scored: row.scored,
    conceded: row.conceded,
    goalDifference: row.goalDifference,
    form: row.form ? row.form.split(',').map(value => value.trim()).filter(value => ['W', 'D', 'L'].includes(value)).join('') : '',
  };
}


export async function fetchRecentPerformance(id, signal, fetcher = fetch) {
  const data = await fetchJson(`/api/matches/${id}/performance`, signal, fetcher);
  if (!data) return null;
  if (!data.home?.team || !data.away?.team || !data.home.summary || !data.away.summary
      || !Array.isArray(data.home.results) || !Array.isArray(data.away.results)) {
    throw new Error('The backend returned unexpected recent performance data.');
  }
  return data;
}
