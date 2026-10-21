import { useEffect, useState } from 'react';
import { fetchBookmakerOdds } from '../services/bookmakerOdds';

export default function useBookmakerOdds(matchId) {
  const [attempt, setAttempt] = useState(0);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    if (!attempt) return;
    const controller = new AbortController();
    setLoading(true);
    setError('');
    setData(null);
    fetchBookmakerOdds(matchId, controller.signal)
      .then(result => { if (!controller.signal.aborted) setData(result); })
      .catch(failure => { if (!controller.signal.aborted) setError(failure.message); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [matchId, attempt]);
  return { data, loading, error, load: () => setAttempt(value => value + 1) };
}
