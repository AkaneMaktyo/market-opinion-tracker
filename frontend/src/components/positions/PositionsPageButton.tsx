import { WalletCards } from 'lucide-react';
import { goToPositionsPage } from '../../hashRoute';

export function PositionsPageButton() {
  return (
    <button className="primary secondary" onClick={goToPositionsPage} type="button">
      <WalletCards size={16} />
      虚拟持仓
    </button>
  );
}
