import React, { useEffect, useMemo, useState } from "react";
import { apiFetch } from "../api/client";

type PostItem = {
  _id: string;
  caption?: string;
  createdAt?: string;
  isHiddenByAdmin?: boolean;
  hiddenByAdminReason?: string;
  author?: { username: string; fullName: string; avatar?: string | null };
  media?: { url: string; type: "image" | "video" }[];
};

type Meta = { page: number; limit: number; total: number; totalPages: number; appliedFilters?: any };

export default function PostsPage() {
  const [items, setItems] = useState<PostItem[]>([]);
  const [meta, setMeta] = useState<Meta | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [visibility, setVisibility] = useState<"" | "visible" | "hidden">("");
  const [page, setPage] = useState(1);
  const [busyIds, setBusyIds] = useState<Record<string, boolean>>({});

  const queryString = useMemo(() => {
    const params = new URLSearchParams();
    params.set("page", String(page));
    params.set("limit", "20");
    if (search.trim()) params.set("search", search.trim());
    if (visibility) params.set("visibility", visibility);
    return params.toString();
  }, [page, search, visibility]);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const res = await apiFetch<PostItem[]>(`/admin/posts?${queryString}`);
      setItems(res.data || []);
      setMeta(res.meta || null);
    } catch (e: any) {
      setError(e?.message || "Failed to load posts");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queryString]);

  async function hidePost(postId: string) {
    const reason = prompt("Hide reason (optional):") || undefined;
    setBusyIds((p) => ({ ...p, [postId]: true }));
    try {
      await apiFetch(`/admin/posts/${encodeURIComponent(postId)}/hide`, {
        method: "PATCH",
        body: JSON.stringify({ reason }),
      });
      await load();
    } catch (e: any) {
      alert(e?.message || "Hide failed");
    } finally {
      setBusyIds((p) => ({ ...p, [postId]: false }));
    }
  }

  async function deletePost(postId: string) {
    const ok = confirm("Delete post? This is destructive.");
    if (!ok) return;
    setBusyIds((p) => ({ ...p, [postId]: true }));
    try {
      await apiFetch(`/admin/posts/${encodeURIComponent(postId)}`, { method: "DELETE" });
      await load();
    } catch (e: any) {
      alert(e?.message || "Delete failed");
    } finally {
      setBusyIds((p) => ({ ...p, [postId]: false }));
    }
  }

  return (
    <div className="space-y-5">
      <div className="rounded-[30px] border border-white/10 bg-white/[0.04] px-5 py-4 shadow-[0_20px_60px_rgba(0,0,0,0.2)] backdrop-blur-2xl">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <div className="text-[11px] uppercase tracking-[0.22em] text-slate-500">Active Surface</div>
            <div className="mt-2 text-2xl font-semibold tracking-tight text-slate-50">Posts</div>
            <div className="mt-1 text-sm text-slate-400">Hide or delete posts.</div>
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
            placeholder="Search caption…"
          />
          <select
            value={visibility}
            onChange={(e) => {
              setPage(1);
              setVisibility(e.target.value as any);
            }}
            className="h-12 rounded-2xl border border-white/10 bg-black/20 px-4 text-sm text-slate-100 outline-none"
          >
            <option value="">Visibility: All</option>
            <option value="visible">Visible</option>
            <option value="hidden">Hidden</option>
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
          {items.map((p) => {
            const busy = Boolean(busyIds[p._id]);
            const thumb = p.media?.[0]?.url || null;
            return (
              <div
                key={p._id}
                className="rounded-[28px] border border-white/10 bg-white/[0.03] p-5 shadow-[0_20px_60px_rgba(0,0,0,0.22)] backdrop-blur-2xl"
              >
                <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
                  <div className="flex min-w-0 gap-4">
                    <div className="h-16 w-16 overflow-hidden rounded-2xl border border-white/10 bg-black/20">
                      {thumb ? <img src={thumb} className="h-full w-full object-cover" /> : null}
                    </div>
                    <div className="min-w-0">
                      <div className="text-sm font-medium text-slate-100">
                        @{p.author?.username || "unknown"} ·{" "}
                        <span className="text-slate-500">{p.createdAt ? new Date(p.createdAt).toLocaleString() : "—"}</span>
                      </div>
                      <div className="mt-1 line-clamp-2 text-sm text-slate-300">{p.caption || "—"}</div>
                      {p.isHiddenByAdmin ? (
                        <div className="mt-2 text-xs text-amber-200">
                          Hidden by admin{p.hiddenByAdminReason ? `: ${p.hiddenByAdminReason}` : ""}
                        </div>
                      ) : null}
                    </div>
                  </div>
                  <div className="flex gap-2">
                    <button
                      disabled={busy}
                      onClick={() => hidePost(p._id)}
                      className="rounded-2xl border border-amber-300/20 bg-amber-400/12 px-4 py-2 text-xs font-medium text-amber-200 disabled:opacity-60"
                    >
                      Hide
                    </button>
                    <button
                      disabled={busy}
                      onClick={() => deletePost(p._id)}
                      className="rounded-2xl border border-rose-300/20 bg-rose-400/12 px-4 py-2 text-xs font-medium text-rose-200 disabled:opacity-60"
                    >
                      Delete
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
          {items.length === 0 ? (
            <div className="rounded-[28px] border border-white/10 bg-white/[0.04] p-10 text-center text-sm text-slate-400">
              No posts found.
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

