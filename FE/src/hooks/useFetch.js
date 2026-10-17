import { useEffect, useState } from 'react';
import { errorMessage } from '../utils/format';

export default function useFetch(fn, deps = []) {
  const [state, setState] = useState({ data: null, loading: true, error: '' });
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let alive = true;
    setState((s) => ({ ...s, loading: true, error: '' }));
    fn()
      .then((data) => alive && setState({ data, loading: false, error: '' }))
      .catch((e) => alive && setState({ data: null, loading: false, error: errorMessage(e) }));
    return () => {
      alive = false;
    };
  }, [...deps, tick]);

  return { ...state, reload: () => setTick((t) => t + 1) };
}
