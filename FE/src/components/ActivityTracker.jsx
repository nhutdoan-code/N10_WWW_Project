import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import { recordActivity } from '../utils/activityTracking';

function pageType(pathname, search) {
  if (pathname === '/') return search ? 'PRODUCT_LIST' : 'HOME';
  if (/^\/products\/[^/]+$/.test(pathname)) return 'PRODUCT_DETAIL';
  if (pathname === '/cart') return 'CART';
  if (pathname === '/compare') return 'COMPARE';
  if (pathname === '/login') return 'LOGIN';
  if (pathname === '/register') return 'REGISTER';
  return null;
}

export default function ActivityTracker() {
  const { pathname, search } = useLocation();
  const lastSearch = useRef('');

  useEffect(() => {
    const page = pageType(pathname, search);
    if (page) recordActivity({ type: 'PAGE_VIEW', pageType: page });
  }, [pathname]);

  useEffect(() => {
    const query = pathname === '/' ? new URLSearchParams(search).get('q')?.trim() || '' : '';
    if (query && query !== lastSearch.current) {
      recordActivity({ type: 'SEARCH', searchTerm: query.slice(0, 120) });
    }
    lastSearch.current = query;
  }, [pathname, search]);

  return null;
}
