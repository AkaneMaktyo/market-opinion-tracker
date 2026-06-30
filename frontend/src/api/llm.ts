import type { LlmCallLog, LlmSceneSummary } from '../types/llm';
import { json } from './http';

export const llmApi = {
  llmLogs: (date: string, limit = 50) => {
    const params = new URLSearchParams();
    if (date) params.set('date', date);
    params.set('limit', String(limit));
    return json<LlmCallLog[]>(`/api/llm/logs?${params.toString()}`);
  },
  llmSummary: (date: string) => {
    const params = new URLSearchParams();
    if (date) params.set('date', date);
    return json<LlmSceneSummary[]>(`/api/llm/summary?${params.toString()}`);
  },
};
