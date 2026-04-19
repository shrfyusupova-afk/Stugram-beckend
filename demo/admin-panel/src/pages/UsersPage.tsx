import React, { useEffect, useMemo, useState } from "react";
import { apiFetch } from "../api/client";

type UserItem = {
  _id: string;
  username: string;
  fullName: string;
  identity?: string | null;
  role: "admin" | "moderator" | "user";
  isSuspended: boolean;
  suspendedUntil?: string | null;
  suspensionReason?: string | null;
  lastLoginAt?: string | null;
  createdAt?: string;
};

type Meta = { page: number; limit: number; total: number; totalPages: number; appliedFilters?: any };

export default function UsersPage() {
  const [items, setItems] = useState<UserItem[]>([]);
  const [meta, setMeta] = useState<Meta | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<"" | "active" | "banned" | "admin">("");
  const [page, setPage] = useState(1);
  const [busyIds, setBusyIds] = useState<Record<string, boolean>>({});

  const queryString = useMemo(() => {
    const params = new URLSearchParams();
    params.set("page", String(page));
    params.set("limit", "20");
    if (search.trim()) params.set("search", search.trim());
    if (status) params.set("status", status);
    return params.toString();
  }, [page, search, status]);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const res = await apiFetch<UserItem[]>(`/admin/users?${queryString}`);
      setItems(res.data || []);
      setMeta(res.meta || null);
    } catch (e: any) {
      setError(e?.message || "Failed to load users");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queryString]);

  async function banUser(userId: string) {
    const reason = prompt("Ban reason (optional):") || undefined;
    setBusyIds((p) => ({ ...p, [userId]: true }));
    try {
      await apiFetch(`/admin/users/${encodeURIComponent(userId)}/ban`, {
        method: "PATCH",
        body: JSON.stringify({ reason }),
      });
      await load();
    } catch (e: any) {
      alert(e?.message || "Ban failed");
    } finally {
      setBusyIds((p) => ({ ...p, [userId]: false }));
    }
  }

  async function unbanUser(userId: string) {
    setBusyIds((p) => ({ ...p, [userId]: true }));
    try {
      await apiFetch(`/admin/users/${encodeURIComponent(userId)}/unban`, { method: "PATCH" });
      await load();
    } catch (e: any) {
      alert(e?.message || "Unban failed");
    } finally {
      setBusyIds((p) => ({ ...p, [userId]: false }));
    }
  }

  async function deleteUser(userId: string) {
    const ok = confirm("Delete user? This is destructive.");
    if (!ok) return;
    setBusyIds((p) => ({ ...p, [userId]: true }));
    try {
      await apiFetch(`/admin/users/${encodeURIComponent(userId)}`, { method: "DELETE" });
      await load();
    } catch (e: any) {
      alert(e?.message || "Delete failed");
    } finally {
      setBusyIds((p) => ({ ...p, [userId]: false }));
    }
  }

  return (
    <div className="space-y-5">
      <div className="rounded-[30px] border border-white/10 bg-white/[0.04] px-5 py-4 shadow-[0_20px_60px_rgba(0,0,0,0.2)] backdrop-blur-2xl">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <div className="text-[11px] uppercase tracking-[0.22em] text-slate-500">Active Surface</div>
            <div className="mt-2 text-2xl font-semibold tracking-tight text-slate-50">Users</div>
            <div className="mt-1 text-sm text-slate-400">Search, ban/unban, delete.</div>
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
            placeholder="Search username…"
          />
          <select
            value={status}
            onChange={(e) => {
              setPage(1);
              setStatus(e.target.value as any);
            }}
            className="h-12 rounded-2xl border border-white/10 bg-black/20 px-4 text-sm text-slate-100 outline-none"
          >
            <option value="">Status: All</option>
            <option value="active">Active</option>
            <option value="banned">Banned</option>
            <option value="admin">Admins</option>
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
                  <th className="px-5 py-4">User</th>
                  <th className="px-5 py-4">Role</th>
                  <th className="px-5 py-4">Status</th>
                  <th className="px-5 py-4">Last login</th>
                  <th className="px-5 py-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/10">
                {items.map((u) => {
                  const busy = Boolean(busyIds[u._id]);
                  return (
                    <tr key={u._id} className="hover:bg-white/[0.02]">
                      <td className="px-5 py-4">
                        <div className="font-medium text-slate-100">@{u.username}</div>
                        <div className="text-xs text-slate-500">{u.fullName}</div>
                      </td>
                      <td className="px-5 py-4 text-slate-300">{u.role}</td>
                      <td className="px-5 py-4">
                        {u.isSuspended ? (
                          <span className="inline-flex rounded-full border border-rose-300/20 bg-rose-400/12 px-3 py-1 text-xs font-medium text-rose-200">
                            banned
                          </span>
                        ) : (
                          <span className="inline-flex rounded-full border border-emerald-300/20 bg-emerald-400/12 px-3 py-1 text-xs font-medium text-emerald-200">
                            active
                          </span>
                        )}
                      </td>
                      <td className="px-5 py-4 text-slate-400">{u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString() : "—"}</td>
                      <td className="px-5 py-4 text-right">
                        <div className="flex justify-end gap-2">
                          {u.isSuspended ? (
                            <button
                              disabled={busy}
                              onClick={() => unbanUser(u._id)}
                              className="rounded-2xl border border-emerald-300/20 bg-emerald-400/12 px-4 py-2 text-xs font-medium text-emerald-200 disabled:opacity-60"
                            >
                              Unban
                            </button>
                          ) : (
                            <button
                              disabled={busy}
                              onClick={() => banUser(u._id)}
                              className="rounded-2xl border border-rose-300/20 bg-rose-400/12 px-4 py-2 text-xs font-medium text-rose-200 disabled:opacity-60"
                            >
                              Ban
                            </button>
                          )}
                          <button
                            disabled={busy}
                            onClick={() => deleteUser(u._id)}
                            className="rounded-2xl border border-white/10 bg-white/[0.05] px-4 py-2 text-xs font-medium text-slate-100 disabled:opacity-60"
                          >
                            Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
                {items.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-5 py-10 text-center text-sm text-slate-400">
                      No users found.
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

