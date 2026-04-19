import React, { useEffect, useState } from "react";
import { apiFetch } from "../api/client";

type DashboardData = {
  totalUsers: number;
  activeUsersToday: number;
  postsCount: number;
  commentsCount: number;
  likesCount: number;
  followsCount: number;
  reportsCount: number;
  errorsCount: number;
  recommendationQueueFailedCount: number;
  deadLetterCount: number;
  analytics?: any;
};

function Card({ title, value }: { title: string; value: React.ReactNode }) {
  return (
    <div className="rounded-[28px] border border-white/10 bg-white/[0.04] p-5 shadow-[0_20px_60px_rgba(0,0,0,0.2)] backdrop-blur-2xl">
      <div className="text-[11px] uppercase tracking-[0.22em] text-slate-500">{title}</div>
      <div className="mt-2 text-2xl font-semibold tracking-tight text-slate-50">{value}</div>
    </div>
  );
}

export default function DashboardPage() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const res = await apiFetch<DashboardData>("/admin/dashboard");
      setData(res.data);
    } catch (e: any) {
      setError(e?.message || "Failed to load dashboard");
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
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <div className="text-[11px] uppercase tracking-[0.22em] text-slate-500">Active Surface</div>
            <div className="mt-2 text-2xl font-semibold tracking-tight text-slate-50">Dashboard</div>
            <div className="mt-1 text-sm text-slate-400">Real platform metrics (backend-driven).</div>
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
      ) : !data ? (
        <div className="rounded-[28px] border border-white/10 bg-white/[0.04] p-6 text-sm text-slate-400">Empty.</div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <Card title="Total Users" value={data.totalUsers} />
          <Card title="Active Today" value={data.activeUsersToday} />
          <Card title="Posts" value={data.postsCount} />
          <Card title="Comments" value={data.commentsCount} />
          <Card title="Likes" value={data.likesCount} />
          <Card title="Follows" value={data.followsCount} />
          <Card title="Reports" value={data.reportsCount} />
          <Card title="Auth/Op Errors" value={data.errorsCount} />
          <Card title="Queue Failed" value={data.recommendationQueueFailedCount} />
          <Card title="Dead Letters" value={data.deadLetterCount} />
        </div>
      )}
    </div>
  );
}

