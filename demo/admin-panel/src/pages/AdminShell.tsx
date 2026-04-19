import React, { useEffect, useMemo, useState } from "react";
import { Link, Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { apiFetch, getTokens, logout } from "../api/client";
import DashboardPage from "./DashboardPage";
import UsersPage from "./UsersPage";
import PostsPage from "./PostsPage";
import ReportsPage from "./ReportsPage";
import SystemHealthPage from "./SystemHealthPage";
import AuditLogsPage from "./AuditLogsPage";

async function verifyAdminAccess(): Promise<void> {
  // Backend enforces admin role on /admin routes.
  await apiFetch("/admin/system/health", { method: "GET" });
}

const navItems = [
  { to: "/", label: "Dashboard" },
  { to: "/users", label: "Users" },
  { to: "/posts", label: "Posts" },
  { to: "/reports", label: "Reports" },
  { to: "/audit-logs", label: "Audit Logs" },
  { to: "/system-health", label: "System Health" },
];

export default function AdminShell() {
  const navigate = useNavigate();
  const location = useLocation();
  const tokens = getTokens();
  const [loading, setLoading] = useState(true);
  const [blocked, setBlocked] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function run() {
      if (!tokens?.accessToken) return;
      setLoading(true);
      setBlocked(null);
      try {
        await verifyAdminAccess();
        if (cancelled) return;
      } catch (err: any) {
        if (cancelled) return;
        setBlocked(err?.message || "Unauthorized");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    run();
    return () => {
      cancelled = true;
    };
  }, [tokens?.accessToken]);

  const activePath = useMemo(() => location.pathname, [location.pathname]);

  if (!tokens?.accessToken) return <Navigate to="/login" replace />;
  if (!loading && blocked) return <Navigate to="/login" replace />;

  return (
    <div className="min-h-screen bg-[#070a0f] text-slate-100">
      <div className="pointer-events-none fixed inset-0 bg-[radial-gradient(circle_at_top_left,rgba(124,199,255,0.08),transparent_24%),radial-gradient(circle_at_bottom_right,rgba(110,231,200,0.08),transparent_18%)]" />
      <div className="relative flex min-h-screen">
        <aside className="hidden w-[280px] shrink-0 border-r border-white/10 bg-white/[0.03] p-5 backdrop-blur-2xl xl:flex xl:flex-col">
          <div className="rounded-[28px] border border-white/10 bg-white/[0.04] p-5 shadow-[0_20px_60px_rgba(0,0,0,0.24)]">
            <div className="text-[11px] uppercase tracking-[0.22em] text-slate-500">Moderation Console</div>
            <div className="mt-2 text-xl font-semibold tracking-tight text-slate-50">Stugram Admin</div>
            <div className="mt-3 text-sm leading-6 text-slate-400">Real backend-driven operator tool.</div>
          </div>

          <div className="mt-6 space-y-2">
            {navItems.map((item) => {
              const active = item.to === "/" ? activePath === "/" : activePath.startsWith(item.to);
              return (
                <Link
                  key={item.to}
                  to={item.to}
                  className={`flex w-full items-start gap-3 rounded-[24px] px-4 py-4 text-left transition ${
                    active
                      ? "border border-cyan-300/20 bg-cyan-400/12 shadow-[0_14px_40px_rgba(124,199,255,0.14)]"
                      : "border border-transparent bg-transparent hover:border-white/10 hover:bg-white/[0.04]"
                  }`}
                >
                  <div className={`mt-1 h-2.5 w-2.5 rounded-full ${active ? "bg-cyan-300 shadow-[0_0_20px_rgba(103,232,249,0.55)]" : "bg-slate-600"}`} />
                  <div>
                    <div className={`text-sm font-medium ${active ? "text-slate-50" : "text-slate-300"}`}>{item.label}</div>
                    <div className="mt-1 text-xs leading-5 text-slate-500">{item.to}</div>
                  </div>
                </Link>
              );
            })}
          </div>

          <div className="mt-auto space-y-3 rounded-[28px] border border-white/10 bg-white/[0.04] p-4">
            <div className="text-[11px] uppercase tracking-[0.18em] text-slate-500">Session</div>
            <div className="text-sm text-slate-300">Authenticated</div>
            <button
              className="inline-flex w-full items-center justify-center rounded-2xl border border-white/10 bg-white/[0.05] px-4 py-2 text-sm font-medium text-slate-100"
              onClick={async () => {
                await logout();
                navigate("/login", { replace: true });
              }}
            >
              Logout
            </button>
          </div>
        </aside>

        <main className="min-w-0 flex-1 p-4 sm:p-6">
          {loading ? (
            <div className="rounded-[30px] border border-white/10 bg-white/[0.04] px-5 py-10 text-center text-sm text-slate-400 shadow-[0_20px_60px_rgba(0,0,0,0.2)] backdrop-blur-2xl">
              Loading admin session…
            </div>
          ) : (
            <Routes>
              <Route path="/" element={<DashboardPage />} />
              <Route path="/users" element={<UsersPage />} />
              <Route path="/posts" element={<PostsPage />} />
              <Route path="/reports" element={<ReportsPage />} />
              <Route path="/audit-logs" element={<AuditLogsPage />} />
              <Route path="/system-health" element={<SystemHealthPage />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          )}
        </main>
      </div>
    </div>
  );
}

