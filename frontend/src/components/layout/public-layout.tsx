import { Outlet } from "react-router-dom";
import { PublicNav } from "./public-nav";
import { PublicFooter } from "./public-footer";

export function PublicLayout() {
  return (
    <div className="flex min-h-screen flex-col">
      <PublicNav />
      <main className="flex-1">
        <Outlet />
      </main>
      <PublicFooter />
    </div>
  );
}
