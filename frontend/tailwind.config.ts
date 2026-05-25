import type { Config } from "tailwindcss";
import animate from "tailwindcss-animate";

export default {
  darkMode: ["class"],
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    container: {
      center: true,
      padding: "1rem",
      screens: { "2xl": "1320px" },
    },
    extend: {
      fontFamily: {
        // Body: Inter stays — it's a workhorse for UI text.
        sans: [
          "Inter",
          "ui-sans-serif",
          "system-ui",
          "-apple-system",
          "Segoe UI",
          "sans-serif",
        ],
        // Display: Plus Jakarta Sans — modern geometric sans. Pairs
        // cleanly with Inter (no awkward contrast) and reads as
        // "fresh product" rather than the editorial-magazine voice
        // of the previous Fraunces serif. Falls back to the same
        // system stack as `sans` so display headings degrade
        // gracefully when Google Fonts is blocked.
        display: [
          "'Plus Jakarta Sans'",
          "Inter",
          "ui-sans-serif",
          "system-ui",
          "-apple-system",
          "Segoe UI",
          "sans-serif",
        ],
      },
      colors: {
        border: "hsl(var(--border))",
        input: "hsl(var(--input))",
        ring: "hsl(var(--ring))",
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        primary: {
          DEFAULT: "hsl(var(--primary))",
          foreground: "hsl(var(--primary-foreground))",
        },
        secondary: {
          DEFAULT: "hsl(var(--secondary))",
          foreground: "hsl(var(--secondary-foreground))",
        },
        destructive: {
          DEFAULT: "hsl(var(--destructive))",
          foreground: "hsl(var(--destructive-foreground))",
        },
        muted: {
          DEFAULT: "hsl(var(--muted))",
          foreground: "hsl(var(--muted-foreground))",
        },
        accent: {
          DEFAULT: "hsl(var(--accent))",
          foreground: "hsl(var(--accent-foreground))",
        },
        success: {
          DEFAULT: "hsl(var(--success))",
          foreground: "hsl(var(--success-foreground))",
        },
        warning: {
          DEFAULT: "hsl(var(--warning))",
          foreground: "hsl(var(--warning-foreground))",
        },
        card: {
          DEFAULT: "hsl(var(--card))",
          foreground: "hsl(var(--card-foreground))",
        },
        popover: {
          DEFAULT: "hsl(var(--popover))",
          foreground: "hsl(var(--popover-foreground))",
        },
      },
      borderRadius: {
        lg: "var(--radius)",
        md: "calc(var(--radius) - 2px)",
        sm: "calc(var(--radius) - 4px)",
      },
      boxShadow: {
        // Slate-tinted shadows aligned with the new accent so cards
        // subtly read as "lifted off a cool surface" without the
        // off-brand warm-umber tone of the previous theme.
        soft: "0 1px 2px 0 rgb(15 25 40 / 0.06), 0 1px 3px 0 rgb(15 25 40 / 0.05)",
        lift: "0 12px 32px -12px rgb(16 132 92 / 0.22), 0 4px 10px -4px rgb(15 25 40 / 0.10)",
        glow: "0 0 0 4px hsl(var(--primary) / 0.18)",
      },
      backgroundImage: {
        // Soft grid for hero / empty-state textures, in cool slate.
        "grid-light":
          "linear-gradient(to right, rgb(15 25 40 / 0.05) 1px, transparent 1px), linear-gradient(to bottom, rgb(15 25 40 / 0.05) 1px, transparent 1px)",
        // Hero radial — emerald wash up from the top of the page.
        // Auto-retints if the --primary token ever changes.
        "hero-radial":
          "radial-gradient(60% 80% at 50% 0%, hsl(var(--primary) / 0.20), transparent 70%)",
        // Secondary radial — slate accent from bottom-right. Layered
        // with hero-radial gives pages a true two-colour ambient wash.
        "hero-radial-accent":
          "radial-gradient(60% 60% at 100% 100%, hsl(var(--accent) / 0.10), transparent 70%)",
      },
      keyframes: {
        "fade-in": {
          from: { opacity: "0", transform: "translateY(8px)" },
          to: { opacity: "1", transform: "translateY(0)" },
        },
        // About-page scroll reveal — larger travel + slower easing
        // than fade-in so a section feels intentional (not jumpy)
        // when it crosses the viewport.
        "reveal-up": {
          from: { opacity: "0", transform: "translateY(28px)" },
          to: { opacity: "1", transform: "translateY(0)" },
        },
        // Slow, subtle drift for the ambient gradient orbs in the
        // About-page hero. Translate / scale only — no rotation or
        // opacity churn so it stays unobtrusive on long sessions.
        "ambient-drift": {
          "0%, 100%": { transform: "translate3d(0, 0, 0) scale(1)" },
          "50%": { transform: "translate3d(0, -18px, 0) scale(1.04)" },
        },
        shimmer: {
          "0%": { backgroundPosition: "-1000px 0" },
          "100%": { backgroundPosition: "1000px 0" },
        },
      },
      animation: {
        "fade-in": "fade-in 0.4s ease-out both",
        "reveal-up": "reveal-up 0.8s cubic-bezier(0.22, 1, 0.36, 1) both",
        "ambient-drift-slow":
          "ambient-drift 14s ease-in-out infinite",
        "ambient-drift-slower":
          "ambient-drift 22s ease-in-out infinite",
        shimmer: "shimmer 2s linear infinite",
      },
    },
  },
  plugins: [animate],
} satisfies Config;
