import { fetchJson } from './footballApi.js';

export async function fetchBookmakerOdds(matchId, signal, fetcher = fetch) {
  const data = await fetchJson(`/api/odds/matches/${matchId}`, signal, fetcher);
  if (!data?.fixture || !Array.isArray(data.prices)) throw new Error('The backend returned unexpected bookmaker odds.');
  return data;
}

export function quoteFor(data, bookmaker, market, outcome) {
  return data?.prices?.find(price => price.bookmaker === bookmaker && price.market === market
    && price.outcome === outcome && Number.isFinite(price.decimalOdds) && price.decimalOdds > 1) ?? null;
}

export function quoteFresh(quote, now = Date.now()) {
  const date = Date.parse(quote?.updatedAt);
  return Number.isFinite(date) && date <= now && now - date <= 15 * 60 * 1000;
}
