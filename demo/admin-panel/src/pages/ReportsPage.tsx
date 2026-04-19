import React, { useEffect, useMemo, useState } from "react";
import { apiFetch } from "../api/client";

type ReportItem = {
  _id: string;
  targetType: "post" | "comment" | "user";
  targetId: string;
  reason: string;
  details?: string;
  status: "open" | "resolved";
  createdAt?: string;
  resolvedAt?: string | null;
  resolutionNote?: string | null;
  reporterId?: { username: string; fullName: string; avatar?: string | null };
  resolvedBy?: { username: string; fullName: string; role?: string };
};

type Meta = { page: number; limit: number; total: number; totalPages: number; appliedFilters?: any };

export default function ReportsPage() {
  const [items, setItems] = useState<ReportItem[]>([]);
  const [meta, setMeta] = useState<Meta | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<"" | "open" | "resolved">("");
  const [targetType, setTargetType] = useState<"" | "post" | "comment" | "user">("");
  const [page, setPage] = useState(1);
  const [busyIds, setBusyIds] = useState<Record<string, boolean>>({});

  const queryString = useMemo(() => {
    const params = new URLSearchParams();
    params.set("page", String(page));
    params.set("limit", "20");
    if (status) params.set("status", status);
    if (targetType) params.set("targetType", targetType);
    return params.toString();
  }, [page, status, targetType]);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const res = await apiFetch<ReportItem[]>(`/admin/reports?${queryString}`);
      setItems(res.data || []);
      setMeta(res.meta || null);
    } catch (e: any) {
      setError(e?.message || "Failed to load reports");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queryString]);

  async function resolveReport(reportId: string) {
    const resolutionNote = prompt("Resolution note (optional):") || undefined;
    setBusyIds((p) => ({ ...p, [reportId]: true }));
    try {
      await apiFetch(`/admin/reports/${encodeURIComponent(reportId)}/resolve`, {
        method: "PATCH",
        body: JSON.stringify({ resolutionNote }),
      });
      await load();
    } catch (e: any) {
      alert(e?.message || "Resolve failed");
    } finally {
      setBusyIds((p) => ({ ...p, [reportId]: false }));
    }
  }

  return (
    <div className="space-y-5">
      <div className="rounded-[30px] border border-white/10 bg-white/[0.04] px-5 py-4 shadow-[0_20px_60px_rgba(0,0,0,0.2)] backdrop-blur-2xl">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <div className="text-[11px] uppercase tracking-[0.22em] text-slate-500">Active Surface</div>
            <div className="mt-2 text-2xl font-semibold tracking-tight text-slate-50">Reports</div>
            <div className="mt-1 text-sm text-slate-400">Resolve abuse reports.</div>
          </div>
          <button
            onClick={load}
            className="inline-flex items-center justify-center rounded-2xl border border-white/10 bg-white/[0.05] px-4 py-2 text-sm font-medium text-slate-100"
          >
            Refresh
          </button>
        </div>

        <div className="mt-4 flex flex-col gap-3 md:flex-row">
          <select
            value={status}
            onChange={(e) => {
              setPage(1);
              setStatus(e.target.value as any);
            }}
            className="h-12 rounded-2xl border border-white/10 bg-black/20 px-4 text-sm text-slate-100 outline-none"
          >
            <option value="">Status: All</option>
            <option value="open">Open</option>
            <option value="resolved">Resolved</option>
          </select>
          <select
            value={targetType}
            onChange={(e) => {
              setPage(1);
              setTargetType(e.target.value as any);
            }}
            className="h-12 rounded-2xl border border-white/10 bg-black/20 px-4 text-sm text-slate-100 outline-none"
          >
            <option value="">Target: All</option>
            <option value="post">Post</option>
            <option value="comment">Comment</option>
            <option value="user">User</option>
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
        <div className="space-y-3">
          {items.map((r) => {
            const busy = Boolean(busyIds[r._id]);
            return (
              <div
                key={r._id}
                className="rounded-[28px] border border-white/10 bg-white/[0.03] p-5 shadow-[0_20px_60px_rgba(0,0,0,0.22)] backdrop-blur-2xl"
              >
                <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                  <div className="min-w-0">
                    <div className="text-sm font-medium text-slate-100">
                      {r.reason} · <span className="text-slate-500">{r.targetType}</span>{" "}
                      <span className="text-slate-600">({String(r.targetId).slice(0, 8)}…)</span>
                    </div>
                    <div className="mt-1 text-sm text-slate-300">{r.details || "—"}</div>
                    <div className="mt-2 text-xs text-slate-500">
                      Reporter: @{r.reporterId?.username || "unknown"} · {r.createdAt ? new Date(r.createdAt).toLocaleString() : "—"}
                    </div>
                    {r.status === "resolved" ? (
                      <div className="mt-2 text-xs text-emerald-200">
                        Resolved{r.resolutionNote ? `: ${r.resolutionNote}` : ""}{" "}
                        {r.resolvedAt ? `· ${new Date(r.resolvedAt).toLocaleString()}` : ""}
                      </div>
                    ) : null}
                  </div>
                  <div className="flex gap-2">
                    {r.status === "open" ? (
                      <button
                        disabled={busy}
                        onClick={() => resolveReport(r._id)}
                        className="rounded-2xl border border-emerald-300/20 bg-emerald-400/12 px-4 py-2 text-xs font-medium text-emerald-200 disabled:opacity-60"
                      >
                        Resolve
                      </button>
                    ) : null}
                  </div>
                </div>
              </div>
            );
          })}

          {items.length === 0 ? (
            <div className="rounded-[28px] border border-white/10 bg-white/[0.04] p-10 text-center text-sm text-slate-400">
              No reports found.
            </div>
          ) : null}

          <div className="flex items-center justify-between rounded-[28px] border border-white/10 bg-white/[0.03] px-5 py-4 text-xs text-slate-400 backdrop-blur-2xl">
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

