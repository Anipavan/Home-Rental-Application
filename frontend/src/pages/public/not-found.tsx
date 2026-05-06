import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";

export function NotFoundPage() {
  return (
    <div className="container py-24 text-center">
      <p className="font-display text-7xl font-bold gradient-text">404</p>
      <h1 className="mt-4 font-display text-2xl font-semibold">
        That page is missing.
      </h1>
      <p className="text-muted-foreground mt-2">
        It may have moved, or never existed in the first place.
      </p>
      <Button asChild variant="gradient" className="mt-6">
        <Link to="/">Take me home</Link>
      </Button>
    </div>
  );
}
