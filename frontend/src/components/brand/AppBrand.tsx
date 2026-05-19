export function AppBrand() {
  return (
    <div className="app-brand">
      <span className="brand-mark" aria-hidden="true">
        <svg viewBox="0 0 64 64" role="img">
          <rect width="64" height="64" rx="14" fill="#0f766e" />
          <polyline
            points="12,44 26,30 36,36 52,16"
            fill="none"
            stroke="#ffffff"
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth="4"
          />
          <circle cx="52" cy="16" r="6" fill="#fbbf24" stroke="#ffffff" strokeWidth="2" />
          <circle cx="26" cy="30" r="2.5" fill="#ffffff" />
          <circle cx="36" cy="36" r="2.5" fill="#ffffff" />
          <circle cx="12" cy="44" r="2.5" fill="#ffffff" />
        </svg>
      </span>
      <div>
        <span className="eyebrow">Market Opinion Tracker</span>
        <h1>美股直播观点追踪</h1>
      </div>
    </div>
  );
}
