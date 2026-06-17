import { useEffect, useState } from 'react';

export type AppRoute = 'dashboard' | 'youtube';

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

function readRoute(): AppRoute {
  return window.location.hash === '#/youtube' ? 'youtube' : 'dashboard';
}
