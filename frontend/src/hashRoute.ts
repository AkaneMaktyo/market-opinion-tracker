import { useEffect, useState } from 'react';

export type AppRoute = 'dashboard' | 'youtube' | 'positions' | 'celebrity';

export function useHashRoute() {
  const [route, setRoute] = useState<AppRoute>(() => readRoute());
  useEffect(() => {
    const onChange = () => setRoute(readRoute());
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return route;
}

export function goToDashboard() {
  window.location.hash = '#/';
}

export function goToYouTubePage() {
  window.location.hash = '#/youtube';
}

export function goToPositionsPage() {
  window.location.hash = '#/positions';
}

export function goToCelebrityPage() {
  window.location.hash = '#/celebrity';
}

function readRoute(): AppRoute {
  if (window.location.hash === '#/youtube') {
    return 'youtube';
  }
  if (window.location.hash === '#/celebrity') {
    return 'celebrity';
  }
  return window.location.hash === '#/positions' ? 'positions' : 'dashboard';
}
