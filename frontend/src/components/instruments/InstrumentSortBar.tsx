import { ListOrdered, TrendingDown, TrendingUp } from 'lucide-react';
import { sortOptions, type SortMode } from './instrumentList';

const sortIcons = { manual: ListOrdered, gain: TrendingUp, loss: TrendingDown };

interface Props {
  mode: SortMode;
  onChange: (mode: SortMode) => void;
}

export function InstrumentSortBar({ mode, onChange }: Props) {
  return (
    <div className="rail-sort">
      {sortOptions.map((option) => {
        const Icon = sortIcons[option.value];
        return (
          <button
            className={mode === option.value ? 'sort-button active' : 'sort-button'}
            key={option.value}
            onClick={() => onChange(option.value)}
            type="button"
          >
            <Icon size={14} />
            <span>{option.label}</span>
          </button>
        );
      })}
    </div>
  );
}
