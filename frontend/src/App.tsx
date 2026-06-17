import { DashboardPage } from './pages/DashboardPage';
import { useHashRoute } from './hashRoute';
import { YouTubePage } from './pages/YouTubePage';

export default function App() {
  const route = useHashRoute();
  if (route === 'youtube') {
    return <YouTubePage />;
  }
  return <DashboardPage />;
}
