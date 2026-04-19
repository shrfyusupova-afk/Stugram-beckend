import React, { useEffect, useMemo, useState } from "react";
import { apiFetch } from "../api/client";

type AuditLogItem = {
  _id: string;
  action: string;
  category: string;
  status: "success" | "failure" | "warning";
  actor?: { username: string; fullName: string } | null;
  targetUser?: { username: string; fullName: string } | null;
  createdAt?: string;
  details?: any;
};

type Meta = { page: number; limit: number; total: number; totalPages: number; appliedFilters?: any };

export default function AuditLogsPage() {
  const [items, setItems] = useState<AuditLogItem[]>([]);
  const [meta, setMeta] = useState<Meta | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");
  const [page, setPage] = useState(1);

  const queryString = useMemo(() => {
    const params = new URLSearchParams();
    params.set("page", String(page));
    params.set("limit", "30");
    if (search.trim()) params.set("search", search.trim());
    if (category) params.set("category", category);
    return params.toString();
  }, [page, search, category]);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const res = await apiFetch<AuditLogItem[]>(`/admin/audit-logs?${queryString}`);
      setItems(res.data || []);
      setMeta(res.meta || null);
    } catch (e: any) {
      setError(e?.message || "Failed to load audit logs");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queryString]);

  return (
    <div className="space-y-5">
      <div className="rounded-[30px] border border-white/10 bg-white/[0.04] px-5 py-4 shadow-[0_20px_60px_rgba(0,0,0,0.2)] backdrop-blur-2xl">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <div className="text-[11px] uppercase tracking-[0.22em] text-slate-500">Active Surface</div>
            <div className="mt-2 text-2xl font-semibold tracking-tight text-slate-50">Audit logs</div>
            <div className="mt-1 text-sm text-slate-400">Immutable trail of sensitive actions.</div>
          </div>
          <button
            onClick={load}
            className="inline-flex items-center justify-center rounded-2xl border border-white/10 bg-white/[0.05] px-4 py-2 text-sm font-medium text-slate-100"
          >
            Refresh
          </button>
        </div>

        <div className="mt-4 flex flex-col gap-3 md:flex-row">
          <input
            value={search}
            onChange={(e) => {
              setPage(1);
              setSearch(e.target.value);
            }}
            className="h-12 flex-1 rounded-2xl border border-white/10 bg-black/20 px-4 text-sm text-slate-100 outline-none placeholder:text-slate-500 focus:border-sky-300/30"
            placeholder="Search action/username…"
          />
          <select
            value={category}
            onChange={(e) => {
              setPage(1);
              setCategory(e.target.value);
            }}
            className="h-12 rounded-2xl border border-white/10 bg-black/20 px-4 text-sm text-slate-100 outline-none"
          >
            <option value="">Category: All</option>
            <option value="auth">auth</option>
            <option value="security">security</option>
            <option value="abuse">abuse</option>
            <option value="chat">chat</option>
            <option value="call">call</option>
            <option value="support">support</option>
          </select>
        </div>
      </div>

      {loading ? (
        <div className="rounded-[28px] border border-white/10 bg-white/[0.04] p-6 text-sm text-slate-400">Loading…</div>
      ) : error ? (
        <div className="rounded-[28px] border border-rose-300/20 bg-rose-400/10 p-6 text-sm text-rose-200">
          {error}{" "}
          <button className="ml-2 underline" onClick={load}>
            Retry
          </button>
        </div>
      ) : (
        <div className="overflow-hidden rounded-[28px] border border-white/10 bg-white/[0.03] shadow-[0_20px_60px_rgba(0,0,0,0.22)] backdrop-blur-2xl">
          <div className="overflow-x-auto">
            <table className="min-w-full text-left text-sm">
              <thead className="border-b border-white/10 bg-white/[0.03] text-xs uppercase tracking-wider text-slate-500">
                <tr>
                  <th className="px-5 py-4">Time</th>
                  <th className="px-5 py-4">Action</th>
                  <th className="px-5 py-4">Actor</th>
                  <th className="px-5 py-4">Target</th>
                  <th className="px-5 py-4">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/10">
                {items.map((l) => (
                  <tr key={l._id} className="hover:bg-white/[0.02]">
                    <td className="px-5 py-4 text-xs text-slate-500">{l.createdAt ? new Date(l.createdAt).toLocaleString() : "—"}</td>
                    <td className="px-5 py-4">
                      <div className="font-medium text-slate-100">{l.action}</div>
                      <div className="text-xs text-slate-500">{l.category}</div>
                    </td>
                    <td className="px-5 py-4 text-slate-300">{l.actor?.username ? `@${l.actor.username}` : "—"}</td>
                    <td className="px-5 py-4 text-slate-300">{l.targetUser?.username ? `@${l.targetUser.username}` : "—"}</td>
                    <td className="px-5 py-4">
                      <span className="inline-flex rounded-full border border-white/10 bg-white/[0.05] px-3 py-1 text-xs font-medium text-slate-200">
                        {l.status}
                      </span>
                    </td>
                  </tr>
                ))}
                {items.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-5 py-10 text-center text-sm text-slate-400">
                      No audit logs.
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>

          <div className="flex items-center justify-between border-t border-white/10 bg-white/[0.02] px-5 py-4 text-xs text-slate-400">
            <div>
              Page {meta?.page ?? page} / {meta?.totalPages ?? "—"} · Total {meta?.total ?? "—"}
            </div>
            <div className="flex gap-2">
              <button
                disabled={page <= 1}
                onClick={() => setPage((p) => Math.max(1, p - 1))}
                className="rounded-xl border border-white/10 bg-white/[0.05] px-3 py-2 disabled:opacity-40"
              >
                Prev
              </button>
              <button
                disabled={meta ? page >= meta.totalPages : false}
                onClick={() => setPage((p) => p + 1)}
                className="rounded-xl border border-white/10 bg-white/[0.05] px-3 py-2 disabled:opacity-40"
              >
                Next
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

