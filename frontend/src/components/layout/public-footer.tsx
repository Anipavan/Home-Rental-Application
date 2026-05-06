import { Link } from "react-router-dom";
import { Logo } from "./logo";

export function PublicFooter() {
  return (
    <footer className="border-t border-border/60 bg-secondary/30 mt-16">
      <div className="container py-12 grid gap-10 md:grid-cols-4">
        <div className="space-y-4">
          <Logo />
          <p className="text-sm text-muted-foreground max-w-xs">
            Find a home you'll love, manage rentals you'll trust. Built for
            tenants and owners across India.
          </p>
        </div>
        <FooterCol
          title="Tenants"
          items={[
            ["Browse homes", "/browse"],
            ["Pay rent", "/login"],
            ["Maintenance", "/login"],
          ]}
        />
        <FooterCol
          title="Owners"
          items={[
            ["List property", "/register"],
            ["Owner dashboard", "/login"],
            ["Pricing", "/about"],
          ]}
        />
        <FooterCol
          title="Company"
          items={[
            ["About", "/about"],
            ["Contact", "/about"],
            ["Privacy", "/about"],
          ]}
        />
      </div>
      <div className="border-t border-border/60">
        <div className="container py-5 flex flex-col md:flex-row items-center justify-between gap-2 text-xs text-muted-foreground">
          <span>© {new Date().getFullYear()} Hearth. All rights reserved.</span>
          <span>Made with care · Bengaluru · Mumbai · Delhi</span>
        </div>
      </div>
    </footer>
  );
}

function FooterCol({
  title,
  items,
}: {
  title: string;
  items: [string, string][];
}) {
  return (
    <div>
      <h4 className="text-sm font-semibold text-foreground mb-3">{title}</h4>
      <ul className="space-y-2">
        {items.map(([label, to]) => (
          <li key={label}>
            <Link
              to={to}
              className="text-sm text-muted-foreground hover:text-foreground transition-colors"
            >
              {label}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
