import React, { useEffect, useState } from "react";
import { apiFetch } from "../api/client";

type SystemHealth = {
  serverUptimeSeconds: number;
  memoryUsage: Record<string, number>;
  cpuUsage: Record<string, number>;
  loadAverage: number[];
  queueHealthSummary?: any;
  recommendationHealthSummary?: any;
};

export default function SystemHealthPage() {
  const [data, setData] = useState<SystemHealth | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const res = await apiFetch<SystemHealth>("/admin/system/health");
      setData(res.data);
    } catch (e: any) {
      setError(e?.message || "Failed to load system health");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  return (
    <div className="space-y-5">
      <div className="rounded-[30px] border border-white/10 bg-white/[0.04] px-5 py-4 shadow-[0_20px_60px_rgba(0,0,0,0.2)] backdrop-blur-2xl">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <div className="text-[11px] uppercase tracking-[0.22em] text-slate-500">Active Surface</div>
            <div className="mt-2 text-2xl font-semibold tracking-tight text-slate-50">System health</div>
            <div className="mt-1 text-sm text-slate-400">Backend runtime + queue summaries.</div>
          </div>
          <button
            onClick={load}
            className="inline-flex items-center justify-center rounded-2xl border border-white/10 bg-white/[0.05] px-4 py-2 text-sm font-medium text-slate-100"
          >
            Refresh
          </button>
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
      ) : data ? (
        <div className="grid gap-4 md:grid-cols-2">
          <div className="rounded-[28px] border border-white/10 bg-white/[0.03] p-5 backdrop-blur-2xl">
            <div className="text-[11px] uppercase tracking-[0.22em] text-slate-500">Uptime</div>
            <div className="mt-2 text-2xl font-semibold text-slate-50">{data.serverUptimeSeconds}s</div>
            <div className="mt-3 text-xs text-slate-500">Load avg: {data.loadAverage?.join(", ")}</div>
          </div>
          <div className="rounded-[28px] border border-white/10 bg-white/[0.03] p-5 backdrop-blur-2xl">
            <div className="text-[11px] uppercase tracking-[0.22em] text-slate-500">Queue</div>
            <pre className="mt-3 overflow-auto rounded-2xl border border-white/10 bg-black/20 p-3 text-xs text-slate-300">
{JSON.stringify(
  {
    queueHealthSummary: data.queueHealthSummary,
    recommendationHealthSummary: data.recommendationHealthSummary,
  },
  null,
  2
)}
            </pre>
          </div>
          <div className="rounded-[28px] border border-white/10 bg-white/[0.03] p-5 backdrop-blur-2xl">
            <div className="text-[11px] uppercase tracking-[0.22em] text-slate-500">Memory</div>
            <pre className="mt-3 overflow-auto rounded-2xl border border-white/10 bg-black/20 p-3 text-xs text-slate-300">
{JSON.stringify(data.memoryUsage, null, 2)}
            </pre>
          </div>
          <div className="rounded-[28px] border border-white/10 bg-white/[0.03] p-5 backdrop-blur-2xl">
            <div className="text-[11px] uppercase tracking-[0.22em] text-slate-500">CPU</div>
            <pre className="mt-3 overflow-auto rounded-2xl border border-white/10 bg-black/20 p-3 text-xs text-slate-300">
{JSON.stringify(data.cpuUsage, null, 2)}
            </pre>
          </div>
        </div>
      ) : (
        <div className="rounded-[28px] border border-white/10 bg-white/[0.04] p-6 text-sm text-slate-400">Empty.</div>
      )}
    </div>
  );
}

