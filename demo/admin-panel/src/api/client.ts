const API_BASE_URL = (import.meta as any).env?.VITE_API_BASE_URL || "http://localhost:5001/api/v1";

type ApiEnvelope<T> = {
  success: boolean;
  message: string;
  data: T;
  meta?: any;
};

type Tokens = {
  accessToken: string;
  refreshToken: string;
};

const STORAGE_KEY = "stugram_admin_tokens";

export function getTokens(): Tokens | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    return JSON.parse(raw) as Tokens;
  } catch {
    return null;
  }
}

export function setTokens(tokens: Tokens | null) {
  if (!tokens) {
    sessionStorage.removeItem(STORAGE_KEY);
    return;
  }
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(tokens));
}

async function refreshAccessToken(refreshToken: string): Promise<Tokens | null> {
  const res = await fetch(`${API_BASE_URL}/auth/refresh-token`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
  });
  if (!res.ok) return null;
  const json = (await res.json()) as ApiEnvelope<any>;
  if (!json?.data?.accessToken || !json?.data?.refreshToken) return null;
  return { accessToken: json.data.accessToken, refreshToken: json.data.refreshToken };
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit & { retryOn401?: boolean } = {}
): Promise<ApiEnvelope<T>> {
  const url = path.startsWith("http") ? path : `${API_BASE_URL}${path.startsWith("/") ? "" : "/"}${path}`;
  const tokens = getTokens();

  const headers: Record<string, string> = {
    ...(init.headers as any),
  };
  if (!headers["Content-Type"] && init.body) headers["Content-Type"] = "application/json";
  if (tokens?.accessToken) headers["Authorization"] = `Bearer ${tokens.accessToken}`;

  const res = await fetch(url, { ...init, headers });

  if (res.status === 401 && init.retryOn401 !== false && tokens?.refreshToken) {
    const refreshed = await refreshAccessToken(tokens.refreshToken);
    if (!refreshed) {
      setTokens(null);
      const json = (await res.json().catch(() => null)) as ApiEnvelope<any> | null;
      throw new Error(json?.message || "Unauthorized");
    }
    setTokens(refreshed);
    return apiFetch<T>(path, { ...init, retryOn401: false });
  }

  const json = (await res.json().catch(() => null)) as ApiEnvelope<T> | null;
  if (!res.ok || !json) {
    throw new Error(json?.message || `Request failed (${res.status})`);
  }
  return json;
}

export async function login(identityOrUsername: string, password: string) {
  const res = await apiFetch<any>("/auth/login", {
    method: "POST",
    body: JSON.stringify({ identityOrUsername, password }),
    retryOn401: false,
  });
  if (!res.data?.accessToken || !res.data?.refreshToken) {
    throw new Error("Login failed: missing tokens");
  }
  setTokens({ accessToken: res.data.accessToken, refreshToken: res.data.refreshToken });
  return res.data;
}

export async function logout() {
  const tokens = getTokens();
  setTokens(null);
  if (!tokens?.refreshToken) return;
  await apiFetch("/auth/logout", { method: "POST", body: JSON.stringify({ refreshToken: tokens.refreshToken }), retryOn401: false }).catch(
    () => {}
  );
}

