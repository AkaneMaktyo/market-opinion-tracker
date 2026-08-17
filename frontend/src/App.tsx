import { DashboardPage } from './pages/DashboardPage';
import { useHashRoute } from './hashRoute';
import { MobileApp } from './mobile/MobileApp';
import { useAndroidApp } from './mobile/useAndroidApp';
import { useLiveUpdate } from './mobile/useLiveUpdate';
import { PositionsPage } from './pages/PositionsPage';
import { YouTubePage } from './pages/YouTubePage';

export default function App() {
  const android = useAndroidApp();
  const liveUpdate = useLiveUpdate();
  const route = useHashRoute();
  if (android) {
    return <MobileApp liveUpdate={liveUpdate} />;
  }
  if (route === 'youtube') {
    return <YouTubePage />;
  }
  if (route === 'positions') {
    return <PositionsPage />;
  }
  return <DashboardPage />;
}
