import { Mic2 } from 'lucide-react';
import { goToYouTubePage } from '../../hashRoute';

export function YouTubePageButton() {
  return (
    <button className="primary secondary" onClick={goToYouTubePage} type="button">
      <Mic2 size={16} />
      YouTube
    </button>
  );
}
