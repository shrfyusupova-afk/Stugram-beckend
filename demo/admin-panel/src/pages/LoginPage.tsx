import React, { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { login } from "../api/client";

export default function LoginPage() {
  const navigate = useNavigate();
  const [identityOrUsername, setIdentityOrUsername] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canSubmit = useMemo(() => identityOrUsername.trim().length >= 3 && password.length >= 6, [identityOrUsername, password]);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const payload = await login(identityOrUsername.trim(), password);
      const role = payload?.user?.role;
      if (role !== "admin") {
        setError("Access denied: this account is not an admin.");
        return;
      }
      navigate("/", { replace: true });
    } catch (err: any) {
      setError(err?.message || "Login failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="min-h-screen bg-[#070a0f] text-slate-100">
      <div className="pointer-events-none fixed inset-0 bg-[radial-gradient(circle_at_top_left,rgba(124,199,255,0.08),transparent_24%),radial-gradient(circle_at_bottom_right,rgba(110,231,200,0.08),transparent_18%)]" />
      <div className="relative mx-auto flex min-h-screen max-w-xl items-center px-6">
        <div className="w-full overflow-hidden rounded-[36px] border border-white/10 bg-white/[0.04] p-8 shadow-[0_24px_80px_rgba(0,0,0,0.34)] backdrop-blur-2xl">
          <div className="text-[11px] uppercase tracking-[0.22em] text-slate-500">Moderation Console</div>
          <div className="mt-2 text-2xl font-semibold tracking-tight text-slate-50">Admin login</div>
          <div className="mt-2 text-sm text-slate-400">Sign in with a real admin account.</div>

          <form onSubmit={onSubmit} className="mt-6 space-y-4">
            <div>
              <label className="mb-2 block text-xs font-medium text-slate-300">Username or identity</label>
              <input
                value={identityOrUsername}
                onChange={(e) => setIdentityOrUsername(e.target.value)}
                className="h-12 w-full rounded-2xl border border-white/10 bg-black/20 px-4 text-sm text-slate-100 outline-none placeholder:text-slate-500 focus:border-sky-300/30 focus:shadow-[0_0_0_1px_rgba(124,199,255,0.22)]"
                placeholder="admin username"
                autoComplete="username"
              />
            </div>
            <div>
              <label className="mb-2 block text-xs font-medium text-slate-300">Password</label>
              <input
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                type="password"
                className="h-12 w-full rounded-2xl border border-white/10 bg-black/20 px-4 text-sm text-slate-100 outline-none placeholder:text-slate-500 focus:border-sky-300/30 focus:shadow-[0_0_0_1px_rgba(124,199,255,0.22)]"
                placeholder="••••••••"
                autoComplete="current-password"
              />
            </div>

            {error ? (
              <div className="rounded-2xl border border-rose-300/20 bg-rose-400/10 px-4 py-3 text-sm text-rose-200">{error}</div>
            ) : null}

            <button
              disabled={!canSubmit || busy}
              className="inline-flex h-12 w-full items-center justify-center rounded-2xl bg-gradient-to-r from-sky-400/90 to-cyan-300/90 text-sm font-semibold text-slate-950 shadow-[0_10px_30px_rgba(124,199,255,0.35)] disabled:opacity-60"
              type="submit"
            >
              {busy ? "Signing in..." : "Sign in"}
            </button>
          </form>

          <div className="mt-6 text-xs text-slate-500">
            If you sign in with <span className="text-slate-300">adminjokkhaa</span>, you’ll be redirected to the admin panel after backend verification.
          </div>
        </div>
      </div>
    </div>
  );
}

