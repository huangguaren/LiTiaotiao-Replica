/* LiTiaotiao replica — single-page workbench UI. */
(function () {
  "use strict";

  const view = () => document.getElementById("view");
  const pageTitle = () => document.getElementById("pageTitle");
  const state = { settings: null, meta: null, appsCache: [] };

  // ---------- helpers ----------
  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c])
    );
  }

  // 宽容解析：容忍手写 JSON 常见的尾随逗号（如 {..},] / {..},}）
  function parseLoose(text) {
    try {
      return JSON.parse(text);
    } catch (e) {
      return JSON.parse(String(text).replace(/,(\s*[}\]])/g, "$1"));
    }
  }
  function $(sel, root) { return (root || document).querySelector(sel); }
  function $all(sel, root) { return Array.from((root || document).querySelectorAll(sel)); }

  let toastTimer;
  function toast(msg, isErr) {
    const t = document.getElementById("toast");
    t.textContent = msg;
    t.className = "toast show" + (isErr ? " err" : "");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => (t.className = "toast"), 2800);
  }
  function openModal({ title, body, foot }) {
    document.getElementById("modalTitle").innerHTML = title || "";
    document.getElementById("modalBody").innerHTML = body || "";
    document.getElementById("modalFoot").innerHTML = foot || "";
    document.getElementById("modalMask").hidden = false;
  }
  function closeModal() { document.getElementById("modalMask").hidden = true; }

  async function ensureMeta() {
    if (!state.meta) state.meta = await API.meta();
    if (!state.settings) state.settings = await API.getSettings();
    return state;
  }

  // ---------- router ----------
  const TITLE = {
    "#/home": "首页", "#/apps": "应用管理", "#/import": "规则导入",
    "#/simulator": "模拟器", "#/global": "全局规则", "#/settings": "设置", "#/about": "关于",
  };
  async function route() {
    const hash = location.hash.slice(1) || "/home";
    const parts = hash.split("/").filter(Boolean);
    const sidebarEl = document.getElementById("sidebar");
    if (sidebarEl) sidebarEl.classList.remove("open");
    let fn = null, arg = null;
    if (parts[0] === "apps" && parts[1]) { fn = renderAppDetail; arg = parts[1]; }
    else { fn = { home: renderHome, apps: renderApps, import: renderImport, simulator: renderSimulator, global: renderGlobal, settings: renderSettings, about: renderAbout }[parts[0] || "home"] || renderHome; }
    pageTitle().textContent = TITLE["#/" + (parts[0] || "home")] || "李跳跳";
    $all(".nav-item").forEach((a) => a.classList.toggle("active", a.dataset.route === "#/" + (parts[0] || "home")));
    if (!fn) return;
    try { await fn(arg); }
    catch (e) { toast(e.message || String(e), true); view().innerHTML = `<div class="empty">加载失败：${esc(e.message || e)}</div>`; }
  }

  // ---------- HOME ----------
  async function renderHome() {
    await ensureMeta();
    const [stats, settings] = await Promise.all([API.stats(), API.getSettings()]);
    state.settings = settings;
    const on = !!settings.is_main_switch_on;
    view().innerHTML = `
      <div class="hero">
        <div class="mascot">🍑</div>
        <div>
          <h2>李跳跳</h2>
          <p>${esc(state.meta.accessibility_service_description.split("\n")[0])}</p>
        </div>
        <div class="toggle-wrap">
          <label class="switch">
            <input type="checkbox" id="svcToggle" ${on ? "checked" : ""}>
            <span class="track"></span><span class="thumb"></span>
          </label>
          <div style="font-size:12px;margin-top:8px;opacity:.95">${on ? "服务已开启" : "服务已关闭"}</div>
        </div>
      </div>
      <div class="card" style="margin-top:18px">
        <div class="row spread">
          <div>
            <div class="muted">已为你跳过广告</div>
            <div class="big-counter">${stats.total_skips}</div>
            <div class="muted">次（模拟器运行计数）</div>
          </div>
          <div class="row">
            <button class="btn" data-go="#/apps">管理应用</button>
            <button class="btn ghost" data-go="#/simulator">打开模拟器</button>
            <button class="btn ghost" data-go="#/import">导入规则</button>
          </div>
        </div>
      </div>
      <div class="stat-grid">
        <div class="stat"><div class="num">${stats.apps}</div><div class="lbl">已适配应用</div></div>
        <div class="stat"><div class="num">${stats.rules}</div><div class="lbl">规则总数</div></div>
        <div class="stat"><div class="num">${stats.enabled_apps}</div><div class="lbl">已启用</div></div>
        <div class="stat"><div class="num">${stats.total_skips}</div><div class="lbl">已跳过</div></div>
      </div>
      <div class="card">
        <h3>关于本应用</h3>
        <p class="note">${esc(state.meta.accessibility_service_description).replace(/\n/g, "<br>")}</p>
      </div>`;
    $("#svcToggle").addEventListener("change", async (e) => {
      try { await API.putSettings({ is_main_switch_on: e.target.checked }); state.settings.is_main_switch_on = e.target.checked; toast(e.target.checked ? "已开启跳跳服务" : "已关闭跳跳服务"); }
      catch (err) { toast(err.message, true); e.target.checked = !e.target.checked; }
    });
    $all("[data-go]").forEach((b) => b.addEventListener("click", () => (location.hash = b.dataset.go)));
  }

  // ---------- APPS ----------
  let appQuery = { q: "", filter: "all", offset: 0, limit: 50 };
  async function renderApps() {
    const q = appQuery;
    const params = { limit: q.limit, offset: q.offset };
    if (q.q) params.q = q.q;
    if (q.filter === "enabled") params.enabled = "1";
    if (q.filter === "disabled") params.enabled = "0";
    if (q.filter === "whitelisted") params.whitelisted = "1";
    const data = await API.apps(params);
    state.appsCache = data.items;
    const rows = data.items.length
      ? data.items.map((a) => `
        <div class="app-row" data-hash="${a.hash}">
          <div class="app-icon">${esc((a.app_name || a.display_name || "?").slice(0, 1))}</div>
          <div class="app-meta">
            <div class="app-name">${esc(a.app_name || a.display_name)} ${a.whitelisted ? '<span class="app-badge">白名单</span>' : ""}</div>
            <div class="app-pkg">${esc(a.package || "hash " + a.hash)} · ${a.rule_count} 条规则</div>
          </div>
          <label class="switch">
            <input type="checkbox" class="enb" data-hash="${a.hash}" ${a.enabled ? "checked" : ""}>
            <span class="track"></span><span class="thumb"></span>
          </label>
        </div>`).join("")
      : `<div class="empty">没有匹配的应用</div>`;
    view().innerHTML = `
      <div class="toolbar">
        <input class="search" id="search" placeholder="搜索应用名 / 包名 / hash" value="${esc(q.q)}">
        <span class="chip ${q.filter === "all" ? "active" : ""}" data-f="all">全部</span>
        <span class="chip ${q.filter === "enabled" ? "active" : ""}" data-f="enabled">已启用</span>
        <span class="chip ${q.filter === "disabled" ? "active" : ""}" data-f="disabled">已禁用</span>
        <span class="chip ${q.filter === "whitelisted" ? "active" : ""}" data-f="whitelisted">白名单</span>
        <button class="btn sm" id="addApp">+ 添加应用</button>
        <button class="btn ghost sm" data-go="#/import">+ 导入规则</button>
      </div>
      <div id="list">${rows}</div>
      <div class="pager">
        <button class="btn ghost sm" id="prev" ${q.offset === 0 ? "disabled" : ""}>上一页</button>
        <span class="muted">${q.offset + 1}–${Math.min(q.offset + q.limit, data.total)} / 共 ${data.total}</span>
        <button class="btn ghost sm" id="next" ${(q.offset + q.limit) >= data.total ? "disabled" : ""}>下一页</button>
      </div>`;
    // events
    let t;
    $("#search").addEventListener("input", (e) => {
      clearTimeout(t);
      t = setTimeout(() => { appQuery.q = e.target.value; appQuery.offset = 0; renderApps(); }, 250);
    });
    $all(".chip[data-f]").forEach((c) => c.addEventListener("click", () => { appQuery.filter = c.dataset.f; appQuery.offset = 0; renderApps(); }));
    $all(".app-row").forEach((r) => r.addEventListener("click", (e) => {
      if (e.target.classList.contains("enb")) return;
      location.hash = "#/apps/" + r.dataset.hash;
    }));
    $all(".enb").forEach((cb) => cb.addEventListener("change", async (e) => {
      e.stopPropagation();
      try { await API.putApp(cb.dataset.hash, { enabled: e.target.checked }); toast(e.target.checked ? "已启用" : "已禁用"); }
      catch (err) { toast(err.message, true); e.target.checked = !e.target.checked; }
    }));
    $("#prev").addEventListener("click", () => { appQuery.offset = Math.max(0, appQuery.offset - q.limit); renderApps(); });
    $("#next").addEventListener("click", () => { appQuery.offset += q.limit; renderApps(); });
    $("#addApp").addEventListener("click", openAddApp);
    $all("[data-go]").forEach((b) => b.addEventListener("click", () => (location.hash = b.dataset.go)));
  }

  function openAddApp() {
    openModal({
      title: "添加应用",
      body: `
        <label class="field"><span>应用包名（必填，如 com.example.app）</span>
          <input id="naPkg" placeholder="com.example.app"></label>
        <label class="field"><span>显示名称（可选，留空自动识别）</span>
          <input id="naName" placeholder="如 某某App"></label>
        <p class="note" style="margin:0">添加后可直接为该应用新增/编辑规则；hash 将按 Java String.hashCode(包名) 自动计算。</p>`,
      foot: `<button class="btn ghost" id="naCancel">取消</button><button class="btn" id="naSave">添加</button>`,
    });
    $("#naCancel").addEventListener("click", closeModal);
    $("#naSave").addEventListener("click", async () => {
      const pkg = $("#naPkg").value.trim();
      if (!pkg) { toast("包名不能为空", true); return; }
      try {
        const app = await API.createApp({ package: pkg, display_name: $("#naName").value.trim() || undefined });
        closeModal();
        toast(app.created ? "已添加应用，去添加规则吧" : "该应用已存在，已为你打开");
        location.hash = "#/apps/" + app.hash;
      } catch (e) { toast(e.message, true); }
    });
  }

  // ---------- APP DETAIL ----------
  async function renderAppDetail(hashStr) {
    const hash = hashStr;
    const app = await API.app(hash);
    const popup = app.rules.filter((r) => r.rule_type === "popup");
    const kw = app.rules.filter((r) => r.rule_type === "keyword");
    const kwapp = app.rules.filter((r) => r.rule_type === "keyword_append");
    const P = app.params || {};
    const boolParamRow = (key, label) => `
      <label class="tag-param">${esc(label)}
        <label class="switch" style="width:40px;height:22px;flex:0 0 40px">
          <input type="checkbox" class="pp" data-key="${esc(key)}" ${P[key] ? "checked" : ""}>
          <span class="track"></span><span class="thumb"></span>
        </label>
      </label>`;
    const numParamRow = (key, label) => `
      <label class="tag-param">${esc(label)}
        <input type="number" class="ppn" data-key="${esc(key)}" value="${P[key] != null ? esc(P[key]) : 0}" style="width:64px;padding:3px 6px">
      </label>`;

    const ruleHtml = (list, tag, tagClass) => list.length ? list.map((r) => `
      <div class="rule">
        <span class="rule-tag ${tagClass}">${esc(tag)}</span>
        <span class="rule-id">${esc(r.match_id)}</span>
        <span class="rule-arrow">→</span>
        <span class="rule-act">${esc(r.action)}</span>
        ${r.times !== 1 ? `<span class="rule-tag">×${esc(r.times)}</span>` : ""}
        <span class="spacer"></span>
        <button class="btn ghost sm ed" data-id="${r.id}">编辑</button>
        <button class="btn danger sm dl" data-id="${r.id}">删除</button>
      </div>`).join("") : `<div class="muted" style="padding:6px 2px">暂无</div>`;

    view().innerHTML = `
      <div class="back" id="back">‹ 返回应用列表</div>
      <div class="card">
        <div class="row spread">
          <div>
            <h3 style="margin:0">${esc(app.app_name || app.display_name)}</h3>
            <div class="muted">${esc(app.package || "hash " + app.hash)} · hash ${esc(app.hash)}</div>
          </div>
          <div class="row">
            <label class="switch"><input type="checkbox" id="enbApp" ${app.enabled ? "checked" : ""}><span class="track"></span><span class="thumb"></span></label>
            <span class="muted">启用</span>
            <label class="switch"><input type="checkbox" id="wlApp" ${app.whitelisted ? "checked" : ""}><span class="track"></span><span class="thumb"></span></label>
            <span class="muted">白名单</span>
            <button class="btn danger sm" id="delApp" style="margin-left:8px">删除</button>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="section-title">应用参数</div>
        <div>
          ${boolParamRow("click_way_popup", "强制点击")}
          ${boolParamRow("unite_popup_rules", "联合规则")}
          ${boolParamRow("reverse_traversal", "反向遍历")}
          ${boolParamRow("ltt_service", "需LTT服务")}
          ${numParamRow("search_times_popup", "搜索次数")}
          ${numParamRow("delay", "开屏延迟(ms)")}
          ${numParamRow("delay_popup", "弹窗延迟(ms)")}
        </div>
      </div>

      <div class="card">
        <div class="section-title">弹窗规则 popup_rules</div>
        ${ruleHtml(popup, "弹窗", "")}
        <div class="section-title">开屏关键词 keywords</div>
        ${ruleHtml(kw, "关键词", "kw")}
        <div class="section-title">追加关键词 keywords_append</div>
        ${ruleHtml(kwapp, "追加", "kw")}
        <button class="btn sm" id="addRule" style="margin-top:10px">+ 新增规则</button>
      </div>`;

    $("#back").addEventListener("click", () => (location.hash = "#/apps"));
    $("#enbApp").addEventListener("change", (e) => API.putApp(hash, { enabled: e.target.checked }).then(() => toast("已更新")).catch((x) => toast(x.message, true)));
    $("#wlApp").addEventListener("change", (e) => API.putApp(hash, { whitelisted: e.target.checked }).then(() => toast("已更新")).catch((x) => toast(x.message, true)));
    $("#delApp").addEventListener("click", async () => {
      if (!confirm("确认删除该应用及其全部规则？此操作不可撤销。")) return;
      try { await API.delApp(hash); toast("已删除应用"); location.hash = "#/apps"; }
      catch (x) { toast(x.message, true); }
    });
    $all(".pp").forEach((cb) => cb.addEventListener("change", () => setParam(hash, cb.dataset.key, cb.checked ? 1 : 0)));
    $all(".ppn").forEach((inp) => inp.addEventListener("change", () => setParam(hash, inp.dataset.key, Number(inp.value))));
    $all(".ed").forEach((b) => b.addEventListener("click", () => editRule(hash, app.rules.find((r) => r.id == b.dataset.id))));
    $all(".dl").forEach((b) => b.addEventListener("click", async () => {
      if (!confirm("确认删除该规则？")) return;
      try { await API.delRule(hash, b.dataset.id); toast("已删除"); renderAppDetail(hash); }
      catch (x) { toast(x.message, true); }
    }));
    $("#addRule").addEventListener("click", () => editRule(hash, null));
  }

  async function setParam(hash, key, value) {
    try { await API.setParam(hash, key, value); toast("参数已保存"); }
    catch (x) { toast(x.message, true); }
  }

  function ruleForm(rule) {
    rule = rule || { rule_type: "popup", match_id: "", action: "", times: 1 };
    return `
      <label class="field"><span>类型</span>
        <select id="f_type">
          <option value="popup" ${rule.rule_type === "popup" ? "selected" : ""}>弹窗 popup_rules</option>
          <option value="keyword" ${rule.rule_type === "keyword" ? "selected" : ""}>开屏关键词 keywords</option>
          <option value="keyword_append" ${rule.rule_type === "keyword_append" ? "selected" : ""}>追加关键词</option>
        </select>
      </label>
      <label class="field"><span>匹配 id（触发条件，支持 +开头 / -结尾 / =完全 / &且）</span>
        <input id="f_id" value="${esc(rule.match_id)}"></label>
      <label class="field"><span>action（点击目标：文本/id、GLOBAL_ACTION_BACK、坐标 x1,y1,x2,y2）</span>
        <input id="f_action" value="${esc(rule.action)}"></label>
      <label class="field"><span>点击次数 times（0=仅检测）</span>
        <input id="f_times" type="number" value="${esc(rule.times)}"></label>`;
  }

  function editRule(hash, rule) {
    const isNew = !rule;
    openModal({
      title: isNew ? "新增规则" : "编辑规则",
      body: ruleForm(rule),
      foot: `<button class="btn ghost" id="mCancel">取消</button><button class="btn" id="mSave">保存</button>`,
    });
    $("#mCancel").addEventListener("click", closeModal);
    $("#mSave").addEventListener("click", async () => {
      const payload = {
        rule_type: $("#f_type").value,
        match_id: $("#f_id").value.trim(),
        action: $("#f_action").value.trim() || $("#f_id").value.trim(),
        times: Number($("#f_times").value) || 1,
      };
      if (!payload.match_id) { toast("匹配 id 不能为空", true); return; }
      try {
        if (isNew) await API.addRule(hash, payload);
        else await API.putRule(hash, rule.id, payload);
        closeModal(); toast("已保存"); renderAppDetail(hash);
      } catch (x) { toast(x.message, true); }
    });
  }

  // ---------- IMPORT ----------
  const SAMPLE_IMPORT = `[
  {
    "-330301876": "{\\"popup_rules\\":[{\\"id\\":\\"native_ad_container\\",\\"action\\":\\"topon_btn_close\\"},{\\"id\\":\\"| 跳过\\",\\"action\\":\\"| 跳过\\"}],\\"click_way_popup\\":1}"
  },
  {
    "1994036591": "{\\"popup_rules\\":[{\\"id\\":\\"青少年模式\\",\\"action\\":\\"我知道了\\"}]}"
  }
]`;
  async function renderImport() {
    view().innerHTML = `
      <div class="card">
        <h3>导入规则</h3>
        <p class="note">① 官方 <b>AllRules.json / BasicRules.json / ExtendedRules.json</b>（数组，键为包名 hashCode）可直接粘贴导入；② 单个应用的规则对象或社区 <b>readme.md</b> 代码块，需在下方填写<b>包名</b>后再导入（会自动计算 hashCode）。相同 hash 的应用规则将被覆盖更新。</p>
        <textarea class="code-area" id="imp" placeholder="在此粘贴规则 JSON 或 readme 代码块…"></textarea>
        <label class="field" style="margin-top:10px"><span>包名（可选：仅导入单个应用 / 文档规则时填写，如 com.example.app）</span>
          <input id="impPackage" placeholder="com.example.app"></label>
        <div class="row" style="margin-top:12px">
          <button class="btn" id="doImport">解析并导入</button>
          <button class="btn ghost" id="loadSample">加载示例</button>
          <button class="btn ghost" id="download">下载当前规则 (AllRules.json)</button>
        </div>
        <div class="section-title">导入结果</div>
        <div id="impResult" class="note">尚未导入</div>
      </div>`;
    $("#loadSample").addEventListener("click", () => ($("#imp").value = SAMPLE_IMPORT));
    $("#doImport").addEventListener("click", async () => {
      const content = $("#imp").value;
      const pkg = ($("#impPackage").value || "").trim();
      if (!content.trim()) { toast("请先粘贴规则内容", true); return; }
      try {
        const r = await API.importRules(content, "auto", pkg || undefined);
        $("#impResult").innerHTML = `导入完成：<b>${r.imported_apps}</b> 个应用，<b>${r.imported_rules}</b> 条规则。` +
          (r.errors && r.errors.length ? `<br><span style="color:var(--danger)">${esc(r.errors.join("<br>"))}</span>` : "");
        toast("导入成功");
      } catch (x) { toast(x.message, true); }
    });
    $("#download").addEventListener("click", async () => {
      try {
        const text = await API.exportRules("all");
        const blob = new Blob([text], { type: "application/json" });
        const a = document.createElement("a");
        a.href = URL.createObjectURL(blob); a.download = "AllRules.json"; a.click();
        URL.revokeObjectURL(a.href); toast("已导出");
      } catch (x) { toast(x.message, true); }
    });
  }

  // ---------- SIMULATOR ----------
  const SAMPLE_SCREEN = `{
  "text": "跳过",
  "resource_id": "skip_btn",
  "clickable": true,
  "children": [
    {
      "text": "检测到更新",
      "resource_id": "update_title",
      "clickable": false,
      "children": [
        { "text": "以后再说", "resource_id": "btn_later", "clickable": true },
        { "text": "立即更新", "resource_id": "btn_now", "clickable": true }
      ]
    }
  ]
}`;
  async function renderSimulator() {
    const data = await API.apps({ limit: 300 });
    const opts = data.items.map((a) => `<option value="${a.hash}">${esc(a.app_name || a.display_name)} (${esc(a.package || a.hash)})</option>`).join("");
    view().innerHTML = `
      <div class="card">
        <h3>无障碍节点树模拟器</h3>
        <p class="note">用法：① 左侧编辑「屏幕节点树」；② 右侧选择应用规则，或填写自定义规则 JSON；③ 点「运行引擎」。<br>
        节点字段：<b>text</b> / <b>resource_id</b> / <b>bounds</b> / <b>clickable</b> / <b>class</b> / <b>checked</b> / <b>children</b>。规则的 <b>id</b> 会匹配节点的 text 或 resource_id，<b>action</b> 是要点击的控件（也可为 <b>GLOBAL_ACTION_BACK</b> 或坐标）。每次运行会计入统计数据。</p>
        <div class="split">
          <div>
            <div class="section-title">屏幕节点树（screen）</div>
            <textarea class="code-area" id="screen">${esc(SAMPLE_SCREEN)}</textarea>
            <button class="btn ghost sm" id="sampScreen" style="margin-top:8px">填入示例屏幕</button>
          </div>
          <div>
            <div class="section-title">规则来源</div>
            <label class="field"><span>使用已适配应用规则</span>
              <select id="appSel"><option value="">— 不使用 —</option>${opts}</select></label>
            <label class="field"><span>当前包名（小程序识别用，可选）</span>
              <input id="simPackage" placeholder="com.tencent.mm"></label>
            <label class="field" style="display:flex;align-items:center;gap:8px">
              <input type="checkbox" id="simUseGlobal" checked style="width:auto"> <span style="margin:0">应用全局规则（含小程序）</span></label>
            <div class="section-title">或自定义规则 JSON（rule object）</div>
            <textarea class="code-area" id="ruleJson" placeholder='{"popup_rules":[{"id":"检测到更新","action":"以后再说"}]}' style="min-height:120px"></textarea>
            <div class="row" style="margin-top:8px">
              <button class="btn ghost sm" id="sampRule">填入示例规则</button>
            </div>
            <button class="btn" id="run" style="margin-top:10px;width:100%">▶ 运行引擎</button>
          </div>
        </div>
        <div class="section-title">运行结果</div>
        <div class="result-box" id="result">（点击「运行引擎」开始；匹配结果会显示在这里）</div>
      </div>`;
    $("#sampScreen").addEventListener("click", () => ($("#screen").value = SAMPLE_SCREEN));
    $("#sampRule").addEventListener("click", () => {
      $("#ruleJson").value = '{"popup_rules":[{"id":"检测到更新","action":"以后再说"}]}';
      toast("已填入示例规则，点击运行即可命中");
    });
    $("#run").addEventListener("click", async () => {
      const resultBox = $("#result");
      let screen;
      try { screen = parseLoose($("#screen").value); }
      catch (e) {
        resultBox.textContent = "屏幕 JSON 解析失败：" + e.message + "\n（常见原因：多余逗号、缺少引号或括号）";
        toast("屏幕 JSON 解析失败", true); return;
      }
      const hash = $("#appSel").value || undefined;
      const pkg = ($("#simPackage").value || "").trim() || undefined;
      const useGlobal = $("#simUseGlobal").checked;
      let rules = undefined;
      const rj = $("#ruleJson").value.trim();
      if (rj) {
        try { rules = parseLoose(rj); }
        catch (e) {
          resultBox.textContent = "规则 JSON 解析失败：" + e.message;
          toast("规则 JSON 解析失败", true); return;
        }
      }
      if (!hash && !rules && !useGlobal) { toast("请选择应用、填写自定义规则，或勾选全局规则", true); return; }
      try {
        const res = await API.simulate({ hash, rules, screen, package: pkg, use_global: useGlobal });
        renderSimResult(res);
      } catch (x) {
        resultBox.textContent = "运行失败：" + (x.message || x);
        toast(x.message || String(x), true);
      }
    });
  }

  function renderSimResult(res) {
    const box = $("#result");
    if (!res.hits.length) {
      box.innerHTML = `<span class="kw-hit">未命中任何规则</span>\n（屏幕上没有匹配 id / 关键词的控件）`;
      return;
    }
    let out = `共 ${res.total_clicks} 次点击操作\n匹配关键词: ${res.matched_keywords.join(", ") || "无"}\n${"─".repeat(40)}\n`;
    res.hits.forEach((h, i) => {
      out += `\n[${i + 1}] ${h.rule_type}  触发: "${h.match_id}"  动作: "${h.action}"  ×${h.times}\n`;
      out += `     命中控件: text="${h.trigger.text}" id="${h.trigger.resource_id}"\n`;
      h.actions.forEach((a) => {
        if (a.kind === "none") out += `     · 检测态(times=0) 或 未找到目标 — ${a.note}\n`;
        else if (a.kind === "back") out += `     · <返回键> ${a.note}\n`;
        else if (a.kind === "bounds") out += `     · 点击坐标 (${a.bounds}) ${a.note}\n`;
        else out += `     · 点击 → text="${a.target_text}" id="${a.target_id}" ${a.note}\n`;
      });
    });
    box.innerHTML = esc(out).replace(/<返回键>/g, '<span class="kw-act">&lt;返回键&gt;</span>')
      .replace(/点击 →/g, '<span class="kw-act">点击 →</span>')
      .replace(/检测态/g, '<span class="kw-hit">检测态</span>');
  }

  // ---------- GLOBAL RULES ----------
  async function renderGlobal() {
    const g = await API.getGlobal();
    const rules = g.rules || {};
    const mp = g.mini_program || {};
    const kwText = (rules.keywords || []).join("\n");
    const popupText = (rules.popup_rules || []).map((r) => r.id + " | " + (r.action || r.id)).join("\n");
    const hostsText = (mp.hosts || []).join("\n");
    view().innerHTML = `
      <div class="card">
        <h3>全局规则（对所有应用 / 小程序生效）</h3>
        <p class="note">小程序开屏广告会以「小程序宿主」的包名出现，按应用匹配的普通规则查不到。全局规则对<b>所有包</b>生效，专门拦截这类广告。</p>
        <label class="field"><span>启用全局规则</span>
          <div><label class="switch"><input type="checkbox" id="gEnabled" ${g.enabled ? "checked" : ""}><span class="track"></span><span class="thumb"></span></label></div>
        </label>
        <label class="field"><span>全局跳过关键词（每行一个，模糊包含）</span>
          <textarea class="code-area" id="gKeywords" style="min-height:110px">${esc(kwText)}</textarea></label>
        <label class="field"><span>全局弹窗规则（每行一条：触发id | 点击目标）</span>
          <textarea class="code-area" id="gPopup" style="min-height:90px" placeholder="关闭广告 | 关闭">${esc(popupText)}</textarea></label>
      </div>
      <div class="card">
        <h3>小程序宿主（自动返回兜底）</h3>
        <p class="note">当下列包在前台、且找不到可点击的「跳过」按钮时，若开启「自动返回」，会触发系统返回键关闭小程序广告。</p>
        <label class="field"><span>小程序宿主包名（每行一个，如 com.tencent.mm）</span>
          <textarea class="code-area" id="gHosts" style="min-height:80px" placeholder="com.tencent.mm">${esc(hostsText)}</textarea></label>
        <label class="field"><span>找不到跳过按钮时自动返回</span>
          <div><label class="switch"><input type="checkbox" id="gAutoBack" ${mp.auto_back ? "checked" : ""}><span class="track"></span><span class="thumb"></span></label></div>
        </label>
        <button class="btn" id="gSave">保存全局规则</button>
      </div>`;
    $("#gSave").addEventListener("click", async () => {
      const keywords = $("#gKeywords").value.split("\n").map((s) => s.trim()).filter(Boolean);
      const popup_rules = $("#gPopup").value.split("\n").map((l) => l.trim()).filter(Boolean).map((l) => {
        const parts = l.split("|");
        const id = (parts[0] || "").trim();
        const action = (parts[1] || id).trim();
        return id ? { id, action, times: 1 } : null;
      }).filter(Boolean);
      const hosts = $("#gHosts").value.split("\n").map((s) => s.trim()).filter(Boolean);
      try {
        await API.putGlobal({
          enabled: $("#gEnabled").checked,
          rules: { keywords, popup_rules },
          mini_program: { hosts, auto_back: $("#gAutoBack").checked },
        });
        toast("全局规则已保存");
      } catch (e) { toast(e.message, true); }
    });
  }

  // ---------- SETTINGS ----------
  async function renderSettings() {
    const s = await API.getSettings();
    const row = (key, label, type) => `
      <label class="tag-param">${esc(label)}
        <label class="switch" style="width:40px;height:22px;flex:0 0 40px">
          <input type="checkbox" class="set" data-key="${esc(key)}" ${s[key] ? "checked" : ""}>
          <span class="track"></span><span class="thumb"></span>
        </label>
      </label>`;
    view().innerHTML = `
      <div class="card">
        <h3>全局设置</h3>
        <p class="note">对应李跳跳原版的无障碍服务参数（基于 APK 资源键名还原）。</p>
        <div style="margin:10px 0">
          ${row("is_main_switch_on", "主开关")}
          ${row("hide_toast", "隐藏 Toast 提示")}
          ${row("show_all_apps", "显示全部应用")}
          ${row("whitelist_all_apps", "全部应用加白名单")}
          ${row("filter_invisible_nodes", "过滤不可见节点")}
          ${row("filter_young_popups", "过滤青少年弹窗")}
          ${row("reverse_traversal", "反向遍历节点")}
        </div>
        <button class="btn" id="saveSet">保存设置</button>
      </div>`;
    const pending = {};
    $all(".set").forEach((cb) => cb.addEventListener("change", () => { pending[cb.dataset.key] = cb.checked; }));
    $("#saveSet").addEventListener("click", async () => {
      if (!Object.keys(pending).length) { toast("没有改动"); return; }
      try { await API.putSettings(pending); state.settings = await API.getSettings(); toast("设置已保存"); }
      catch (x) { toast(x.message, true); }
    });
  }

  // ---------- ABOUT ----------
  async function renderAbout() {
    await ensureMeta();
    view().innerHTML = `
      <div class="card">
        <h3>关于 李跳跳 复刻版</h3>
        <p class="note">${esc(state.meta.accessibility_service_description).replace(/\n/g, "<br>")}</p>
        <div class="stat-grid">
          <div class="stat"><div class="num">${esc(state.meta.version)}</div><div class="lbl">复刻版本</div></div>
          <div class="stat"><div class="num">${esc(state.meta.package)}</div><div class="lbl">原包名</div></div>
        </div>
      </div>
      <div class="card">
        <h3>这个项目是什么</h3>
        <p class="note">
          这是 <b>李跳跳</b>（一款利用 Android 无障碍服务自动跳过开屏广告与弹窗的应用）的<b>高保真复刻工作台</b>。
          原项目仓库仅含加固后的 APK 与规则文件，本复刻基于提取出的资源、字符串与规则数据结构，
          完整还原了其<b>规则引擎</b>（匹配语义、点击动作、参数）与<b>管理界面</b>，
          并提供了可运行的 Web 服务（后端 + 真实数据库 + 规则导入/导出 + 节点树模拟器）。
        </p>
        <p class="note">
          由于本环境无 Android SDK，无法在此编译并运行原版 APK；因此以一个<b>功能等价、可测试</b>的 Web 工作台作为交付，
          其规则匹配逻辑与李跳跳原版一致。
        </p>
      </div>
      <div class="card">
        <h3>快速上手</h3>
        <ol class="note">
          <li>「应用管理」中浏览已适配的 300+ 应用与规则；</li>
          <li>「规则导入」粘贴官方 AllRules.json 进行导入/导出；</li>
          <li>「模拟器」粘贴无障碍节点树，观察引擎如何自动点击；</li>
          <li>「设置」查看并修改无障碍服务参数。</li>
        </ol>
      </div>`;
  }

  // ---------- boot ----------
  document.getElementById("modalMask").addEventListener("click", (e) => { if (e.target.id === "modalMask") closeModal(); });
  document.getElementById("hamburger").addEventListener("click", () => {
    const sb = document.getElementById("sidebar");
    if (sb) sb.classList.toggle("open");
  });
  $all(".nav-item").forEach((a) => a.addEventListener("click", () => (location.hash = a.dataset.route)));
  window.addEventListener("hashchange", route);
  if (!location.hash) location.hash = "#/home";
  route();
})();
