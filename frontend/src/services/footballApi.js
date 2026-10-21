export async function fetchList(path, signal, fetcher = fetch) {
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
  if (response.status === 204) return [];
  const data = await response.json().catch(() => {
    throw new Error('The backend returned an unreadable response.');
  });
  if (!Array.isArray(data)) throw new Error('The backend returned an unexpected response.');
  return data;
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
