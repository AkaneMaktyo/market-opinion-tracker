import { useEffect, useState } from 'react';

export type AppRoute = 'dashboard' | 'youtube' | 'positions';

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

function readRoute(): AppRoute {
  if (window.location.hash === '#/youtube') {
    return 'youtube';
  }
  return window.location.hash === '#/positions' ? 'positions' : 'dashboard';
}
