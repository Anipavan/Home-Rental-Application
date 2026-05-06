// Small inline SVGs for payment-method branding so we don't ship external assets.

export function PhonePeIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 64 64" className={className}>
      <rect width="64" height="64" rx="14" fill="#5F259F" />
      <path
        d="M22 18h20a4 4 0 0 1 0 8H35v6a14 14 0 1 1-13-13.96V18Zm0 6.5A7.5 7.5 0 1 0 29.5 32V18.06A12 12 0 0 0 22 24.5Z"
        fill="#fff"
      />
    </svg>
  );
}

export function GPayIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 64 64" className={className}>
      <rect width="64" height="64" rx="14" fill="#fff" stroke="#dadce0" />
      <text
        x="32"
        y="40"
        textAnchor="middle"
        fontSize="22"
        fontFamily="Inter, sans-serif"
        fontWeight="700"
      >
        <tspan fill="#4285F4">G</tspan>
        <tspan fill="#EA4335">P</tspan>
        <tspan fill="#FBBC04">a</tspan>
        <tspan fill="#34A853">y</tspan>
      </text>
    </svg>
  );
}

export function PaytmIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 64 64" className={className}>
      <rect width="64" height="64" rx="14" fill="#00BAF2" />
      <path
        d="M14 23h36a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H14V23Zm10 7v8h6v-8h-6Zm10 0v8h6v-8h-6Z"
        fill="#fff"
      />
      <text
        x="32"
        y="55"
        textAnchor="middle"
        fontSize="9"
        fontFamily="Inter, sans-serif"
        fontWeight="700"
        fill="#002E6E"
      >
        Paytm
      </text>
    </svg>
  );
}

export function UPIIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 64 64" className={className}>
      <rect width="64" height="64" rx="14" fill="#fff" stroke="#dadce0" />
      <path d="M28 14l16 18-16 18 6-18-6-18Z" fill="#FF7E1B" />
      <path d="M21 14l16 18-16 18 6-18-6-18Z" fill="#098041" opacity=".9" />
    </svg>
  );
}

export function CardIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 64 64" className={className}>
      <rect width="64" height="64" rx="14" fill="url(#card-grad)" />
      <defs>
        <linearGradient id="card-grad" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#0f172a" />
          <stop offset="100%" stopColor="#1e293b" />
        </linearGradient>
      </defs>
      <rect x="10" y="20" width="44" height="6" fill="#facc15" />
      <rect x="14" y="38" width="14" height="3" rx="1.5" fill="#fff" opacity=".8" />
      <rect x="14" y="44" width="22" height="3" rx="1.5" fill="#fff" opacity=".5" />
    </svg>
  );
}

export function CashIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 64 64" className={className}>
      <rect width="64" height="64" rx="14" fill="#10b981" />
      <rect x="10" y="20" width="44" height="24" rx="3" fill="#fff" />
      <circle cx="32" cy="32" r="6" fill="#10b981" />
      <text
        x="32"
        y="36"
        textAnchor="middle"
        fontSize="10"
        fontFamily="Inter"
        fontWeight="700"
        fill="#fff"
      >
        ₹
      </text>
    </svg>
  );
}
