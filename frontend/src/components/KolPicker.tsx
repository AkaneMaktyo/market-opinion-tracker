import { Plus } from 'lucide-react';
import { api } from '../api/client';
import type { Kol } from '../types';

interface Props {
  kols: Kol[];
  selectedId: string;
  onChange: (id: string) => void;
  onCreated: (kol: Kol) => void;
}

export function KolPicker({ kols, selectedId, onChange, onCreated }: Props) {
  async function createKol() {
    const name = window.prompt('输入 KOL 名称');
    if (!name?.trim()) {
      return;
    }
    const kol = await api.createKol({ name: name.trim() });
    onCreated(kol);
  }

  return (
    <div className="kol-picker">
      <select value={selectedId} onChange={(event) => onChange(event.target.value)}>
        {kols.map((kol) => (
          <option key={kol.id} value={kol.id}>
            {kol.name}
          </option>
        ))}
      </select>
      <button onClick={createKol} title="新建 KOL">
        <Plus size={16} />
      </button>
    </div>
  );
}
