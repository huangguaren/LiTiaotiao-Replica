/* Thin fetch wrapper around the LiTiaotiao replica backend. */
(function (global) {
  const BASE = "";

  async function request(method, path, { query, body, raw } = {}) {
    let url = BASE + path;
    if (query) {
      const qs = Object.entries(query)
        .filter(([, v]) => v !== undefined && v !== null && v !== "")
        .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
        .join("&");
      if (qs) url += "?" + qs;
    }
    const opts = { method, headers: {} };
    if (body !== undefined) {
      opts.headers["Content-Type"] = "application/json";
      opts.body = JSON.stringify(body);
    }
    const res = await fetch(url, opts);
    const text = await res.text();
    let data;
    try {
      data = text ? JSON.parse(text) : null;
    } catch (e) {
      data = text;
    }
    if (!res.ok) {
      const msg = (data && data.error) || `请求失败 (${res.status})`;
      throw new Error(msg);
    }
    return data;
  }

  const API = {
    health: () => request("GET", "/api/health"),
    meta: () => request("GET", "/api/meta"),

    getSettings: () => request("GET", "/api/settings"),
    putSettings: (body) => request("PUT", "/api/settings", { body }),

    getGlobal: () => request("GET", "/api/global-rules"),
    putGlobal: (body) => request("PUT", "/api/global-rules", { body }),

    stats: () => request("GET", "/api/stats"),
    logs: (limit) => request("GET", "/api/logs", { query: { limit } }),

    apps: (q) => request("GET", "/api/apps", { query: q }),
    createApp: (body) => request("POST", "/api/apps", { body }),
    app: (hash) => request("GET", `/api/apps/${hash}`),
    putApp: (hash, body) => request("PUT", `/api/apps/${hash}`, { body }),
    delApp: (hash) => request("DELETE", `/api/apps/${hash}`),

    addRule: (hash, body) => request("POST", `/api/apps/${hash}/rules`, { body }),
    putRule: (hash, rid, body) => request("PUT", `/api/apps/${hash}/rules/${rid}`, { body }),
    delRule: (hash, rid) => request("DELETE", `/api/apps/${hash}/rules/${rid}`),
    setParam: (hash, key, value) =>
      request("POST", `/api/apps/${hash}/params`, { body: { key, value } }),

    importRules: (content, format, pkg) =>
      request("POST", "/api/import", { body: { content, format, package: pkg } }),
    exportRules: (scope) =>
      request("GET", "/api/export", { query: { scope } }, { raw: true }),

    simulate: (body) => request("POST", "/api/simulate", { body }),
  };

  global.API = API;
})(window);
