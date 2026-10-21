import { useEffect, useState } from 'react';
import { fetchList } from '../services/footballApi';

export default function useFootballData(path) {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [loadedPath, setLoadedPath] = useState(null);

  useEffect(() => {
    const controller = new AbortController();
    setLoadedPath(path);
    setData([]);
    setLoading(true);
    setError('');
    fetchList(path, controller.signal)
      .then(result => {
        if (!controller.signal.aborted) setData(result);
      })
      .catch(failure => {
        if (!controller.signal.aborted) setError(failure.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [path, attempt]);

  return { data: loadedPath === path ? data : [], loading: loadedPath !== path || loading, error: loadedPath === path ? error : '', retry: () => setAttempt(value => value + 1) };
}
