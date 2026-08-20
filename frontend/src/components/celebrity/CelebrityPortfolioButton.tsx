import { Radar } from 'lucide-react';
import { goToCelebrityPage } from '../../hashRoute';

export function CelebrityPortfolioButton() {
  return (
    <button className="primary secondary" onClick={goToCelebrityPage} type="button">
      <Radar size={16} />
      名人持仓
    </button>
  );
}
