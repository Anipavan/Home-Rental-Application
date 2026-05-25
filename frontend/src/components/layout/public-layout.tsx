import { Outlet } from "react-router-dom";
import { PublicNav } from "./public-nav";
import { PublicFooter } from "./public-footer";
import { PageEnter } from "@/components/ui/page-enter";

export function PublicLayout() {
  return (
    <div className="flex min-h-screen flex-col">
      <PublicNav />
      <main className="flex-1">
        {/* PageEnter remounts on every route change so each public page
            (landing, browse, about, property detail) gets a gentle
            fade-up entry. Cheap — no scroll observers, no per-page
            opt-in. Pages that already wrap themselves in
            `animate-fade-in` keep working; the two animations are
            indistinguishable when they fire together. */}
        <PageEnter>
          <Outlet />
        </PageEnter>
      </main>
      <PublicFooter />
    </div>
  );
}
