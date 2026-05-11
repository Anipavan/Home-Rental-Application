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
        // Body: Inter stays — it's a workhorse. The editorial flourish
        // comes from the display face below.
        sans: [
          "Inter",
          "ui-sans-serif",
          "system-ui",
          "-apple-system",
          "Segoe UI",
          "sans-serif",
        ],
        // Display: Fraunces, a variable serif with optical-size + soft
        // axes. Gives Hearth's headings a warm, editorial voice — much
        // more "home" than the previous Plus Jakarta Sans which read as
        // generic SaaS-tech. Falls back to system serifs.
        display: [
          "'Fraunces'",
          "'Georgia'",
          "'Times New Roman'",
          "serif",
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
        // Warm-tinted shadows — the old "lift" used indigo at 0.18α
        // which looked off-brand under the cream backgrounds. These
        // mirror the terracotta primary so cards subtly carry the
        // brand colour through.
        soft: "0 1px 2px 0 rgb(60 30 20 / 0.06), 0 1px 3px 0 rgb(60 30 20 / 0.05)",
        lift: "0 12px 32px -12px rgb(192 92 60 / 0.20), 0 4px 10px -4px rgb(60 30 20 / 0.10)",
        glow: "0 0 0 4px hsl(var(--primary) / 0.14)",
      },
      backgroundImage: {
        // Soft grid for hero / empty-state textures, in warm umber.
        "grid-light":
          "linear-gradient(to right, rgb(60 30 20 / 0.05) 1px, transparent 1px), linear-gradient(to bottom, rgb(60 30 20 / 0.05) 1px, transparent 1px)",
        // Hero radial — terracotta wash up from the top of the page.
        // Auto-retints if the --primary token ever changes.
        "hero-radial":
          "radial-gradient(60% 80% at 50% 0%, hsl(var(--primary) / 0.18), transparent 70%)",
      },
      keyframes: {
        "fade-in": {
          from: { opacity: "0", transform: "translateY(8px)" },
          to: { opacity: "1", transform: "translateY(0)" },
        },
        shimmer: {
          "0%": { backgroundPosition: "-1000px 0" },
          "100%": { backgroundPosition: "1000px 0" },
        },
      },
      animation: {
        "fade-in": "fade-in 0.4s ease-out both",
        shimmer: "shimmer 2s linear infinite",
      },
    },
  },
  plugins: [animate],
} satisfies Config;
