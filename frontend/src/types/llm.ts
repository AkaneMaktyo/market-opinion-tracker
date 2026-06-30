export interface LlmCallLog {
  id: string;
  scene: string;
  model: string;
  status: string;
  requestChars: number;
  responseChars: number;
  durationMs: number;
  requestPreview: string;
  responsePreview: string;
  errorMessage: string;
  createdAt: string;
}

export interface LlmSceneSummary {
  scene: string;
  status: string;
  totalCount: number;
}
