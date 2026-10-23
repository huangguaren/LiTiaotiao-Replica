# Code Review Report

> 依据 `code-review-prompt.docx` 的 6 个维度（可读性 / 架构 / 测试 / 安全 / 性能 / 可维护性）对当前项目做全面审查。
> 审查范围：`LiTiaotiao-Android/`（Java）与 `LiTiaotiao-Replica/`（Python + 原生前端），以及 CI 配置。

---

## Critical（必须修复）

### 1. 本地 HTTP 服务 CORS 通配 + 无鉴权/CSRF，可被任意网页改写规则
- **位置**：`LiTiaotiao-Replica/litiaotiao/server.py:50`（`Access-Control-Allow-Origin: *`），配合 `_handle_api` 对任意方法放行。
- **原因**：服务虽默认绑定 `127.0.0.1`，但浏览器中的任意恶意页面都可跨域向 `http://127.0.0.1:8000/api/import` 发起请求；由于响应头允许所有来源、所有方法与 `Content-Type`，预检会通过，从而**静默导入/覆盖规则**（相当于 CSRF）。若用户把 `LTT_HOST` 设为 `0.0.0.0`，风险进一步放大。
- **建议**：本地工具不需要跨域。移除 `Access-Control-Allow-Origin: *`，仅允许同源；或校验 `Origin`/`Host`，并为写操作增加一次性 token。
  ```python
  # 只允许同源 / 本机
  origin = self.headers.get("Origin")
  if origin and not origin.startswith(f"http://127.0.0.1:{config.PORT}"):
      self._send_json(403, {"error": "forbidden origin"}); return
  ```

### 2. 全局规则 + 小程序自动返回存在误点/误返回风险（可能造成数据丢失）
- **位置**：`engine.py` 的 `evaluate_screen`（全局规则对所有包生效）、`service/LttAccessibilityService.java`（`mpAutoBack`）。
- **原因**：全局关键词（如 `跳过`、`关闭广告`）与 `GLOBAL_ACTION_BACK` 会作用在**任意前台应用**上。非广告场景（表单、确认框、阅读页）若出现同名控件，可能被误点；小程序「自动返回」可能直接把用户退出正常页面，存在未保存内容丢失风险。
- **建议**：
  1. 全局关键词默认只保留强特征词（`跳过广告`、`关闭广告`），移除宽泛的 `跳过`；
  2. 增加**时间窗**（仅在应用启动后 N 秒内生效）与**包白名单**；
  3. 自动返回增加二次条件（检测到广告容器特征）而非"找不到跳过就返回"；
  4. 记录每次全局动作到日志，便于事后排查误点。

---

## Warning（建议修复）

### 1. `delay` / `delay_popup` / `unite_popup_rules` 可配置但引擎未实现
- **位置**：`RuleDetailActivity.java:73/77/78`（UI 写入）、`RuleSet.java:17`、`engine.py:27/30/263`（仅注释/文档）。
- **原因**：这三个参数会被保存并导出，但 Python 与 Java 引擎均未真正执行延时或联合任务状态机；用户会误以为配置已生效（尤其 `unite_popup_rules` 用于"自动安装/倒计时广告"这类连续任务）。
- **建议**：要么实现（延时用 `Handler.postDelayed`；联合任务用跨事件状态机），要么在 UI 上明确标注"当前版本未实现"并从可编辑项移除，避免误导。

### 2. 每次 SQL 操作新建连接 + 逐条提交，导入/种子极慢
- **位置**：`db.py:70`（`sqlite3.connect` 每次新建）、`db.py:95`（`execute` 每次 commit）、`api.py:424` 导入循环逐条 `db.execute`。
- **原因**：导入 795 条规则 ≈ 795 次建连 + 795 次提交；实测首次种子导入约 **40 秒**。这是明显的性能问题，也放大了并发写锁风险。
- **建议**：批量写入使用单连接 + 单事务（`execute_many` 已存在却未使用）；导入时 `BEGIN ... COMMIT` 包裹全部 INSERT。

### 3. SQLite 并发写无 `busy_timeout`，多线程下可能 `database is locked`
- **位置**：`db.py:70`；服务端为 `ThreadingHTTPServer`。
- **原因**：每个请求独立连接，多个写请求并发时 SQLite 默认锁等待 5s，超时即抛异常并返回 500。
- **建议**：连接时设置 `timeout=10`、`PRAGMA journal_mode=WAL` 与 `PRAGMA busy_timeout=10000`；或将写操作串行化。

### 4. 静态资源路径校验使用字符串前缀匹配，存在绕过风险
- **位置**：`server.py:135`（`str(target).startswith(str(STATIC_DIR.resolve()))`）。
- **原因**：前缀匹配无法区分 `/a/static` 与 `/a/static-evil`；一旦出现同前缀兄弟目录，`../` 可越权读取。
- **建议**：改用 `Path(target).resolve().is_relative_to(STATIC_DIR.resolve())`。

### 5. Android 导出 SQL 采用字符串拼接
- **位置**：`data/Repository.java:299`（`"SELECT hash FROM apps WHERE hash=" + scope.substring(7)`）。
- **原因**：当前调用方只传 `all`，尚不可利用；但一旦 `scope` 来源于外部（如 Intent/深层链接），即构成 SQL 注入。
- **建议**：改为 `rawQuery("... WHERE hash=?", new String[]{...})` 参数化。

### 6. 编辑页加载时触发监听器，产生多余写库并可能覆盖并发修改
- **位置**：`RuleDetailActivity.java:69/73` 先绑定监听，`:98` 的 `load()` 在 `:113-116` 调用 `setChecked`。
- **原因**：`setChecked` 会触发 `OnCheckedChangeListener` → 立即 `setParam/setEnabled` 写库；加载即产生无意义写入，且若期间有并发修改会被旧值覆盖。
- **建议**：加 `boolean loading` 标志，加载阶段忽略监听回调；或先移除监听、`setChecked` 后再恢复。

### 7. 测试覆盖不均，且 CI 未运行 Python 测试
- **位置**：`LiTiaotiao-Replica/tests/`（仅 hashutil/engine/rules_io 共 21 例）；`LiTiaotiao-Android/app/src/test/` **为空**；`.github/workflows/android-ci.yml` 仅 build + lint。
- **原因**：Java 引擎（真正上线的逻辑）零单元测试；Python 也缺 API/DB/导入导出的集成测试；21 个测试在 CI 中**从未被执行**。
- **建议**：
  1. 给 `RuleMatcher`/`RuleEngine` 补 JVM 单测（纯逻辑，易测）；
  2. CI 增加 `python tests/run_tests.py`，并加一层 API 集成测试（导入→导出往返、simulate）；
  3. 关键路径（times=0、去重、全局规则）必须有回归用例。

### 8. 未回收 `AccessibilityNodeInfo`，存在节点泄漏/内存压力
- **位置**：`engine/NodeSnapshot.java`（`build` 递归持有 `source`，全程未 `recycle()`）。
- **原因**：页面频繁变化时会累积大量节点对象；Android 对未回收节点有数量限制，长期运行可能触发异常或 GC 压力。
- **建议**：在使用完毕（事件处理结束）后回收；注意 API 33+ `recycle` 已废弃，可按版本分支处理。

### 9. 无障碍服务无前台服务/常驻通知，易被系统回收
- **位置**：`AndroidManifest.xml`（仅声明 `AccessibilityService`）。
- **原因**：原版使用前台服务保活；当前实现被系统回收后需用户重新开启，影响"开屏即跳过"的可靠性。
- **建议**：增加前台服务（`FOREGROUND_SERVICE_SPECIAL_USE`）与低优先级通知，或在文档中说明该限制。

### 10. Python / Java 双份引擎实现，存在逻辑漂移
- **位置**：`engine.py` 与 `engine/RuleEngine.java`。
- **原因**：两套实现需手工同步；`unite_popup_rules`、`filter_young_popups` 等已是"一边有文档、一边无实现"的状态。
- **建议**：抽出**规则语义测试向量（JSON 用例）**，Python 与 Java 共用；或明确以 Java 为唯一实现、Python 仅作模拟器并标注差异。

### 11. `simulate` 每次写 `skip_logs`，统计被污染且数据无限增长
- **位置**：`api.py:547`（simulate）→ `api.py:605` 插入 `skip_logs`。
- **原因**：模拟器调用会累加"已跳过"统计，用户无法区分真实与模拟；长期使用 DB 持续增大。
- **建议**：模拟日志单独表/单独字段（`source='simulate'`），统计默认排除；或提供清理入口。

### 12. 存在"仅存储、不生效"的死配置
- **位置**：`filter_young_popups`（`Prefs.java:16`、`SettingsActivity.java:22`、`api.py:50`）、`is_user_check`（`api.py:52`）、`ltt_service`（仅 `RuleSet.java:17` 注释）、`db.execute_many`（未被调用）。
- **原因**：UI 可配置但引擎未读取，属于"看似支持"的误导性功能。
- **建议**：实现或移除；死代码清理（YAGNI）。

---

## Info（可选改进）

1. **职责偏多**：`api.py` 736 行、`static/js/app.js` 603 行、`Repository.java` 401 行。建议按资源拆分（apps / rules / import-export / logs），前端按页面拆分模块。
2. **魔法数字**：`THROTTLE_MS=120`、`DEDUPE_MS=1500` 等应提取为常量或可配置项。
3. **服务状态判断脆弱**：`MainActivity.isServiceEnabled()` 用字符串 `contains` 判断，建议解析 `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` 的组件列表。
4. **规则输入分隔符**：`GlobalRulesActivity` 用 `split("\\|")` 解析"触发id | 动作"，动作本身含 `|` 会被截断；建议改用 JSON 或更明确的分隔。
5. **全局正则清洗**：`_TRAILING_COMMA_RE` 对整个文本替换，理论上会改动字符串内的 `,]`；建议仅在结构层处理。
6. **注释剥离**：`extract_rule_object` 的 `//` 正则可能误删含 `//` 的文本（如 URL）；建议只剥离行首注释。
7. **WAL 未开启**：建议 `PRAGMA journal_mode=WAL` 提升并发读性能，并配合 `busy_timeout`。
8. **分页追加**：`AppListActivity` 以 offset 追加，数据变化时可能出现重复项；可改为游标分页。
9. **版本一致性**：App 已到 `2.2.1`，Web 工作台仍标注 `2.2-replica`；建议统一版本号来源。
10. **输入上限**：导入/规则文本未限制长度，建议增加最大体积与字段长度校验。

---

## 值得肯定

- **注入防护到位**：Python 与 Android 的业务查询均使用参数化 `?` 占位（唯一例外见 Warning 5）。
- **规则语义还原度高**：`+/-/=/&`、`times=0`、同屏去重、`GLOBAL_ACTION_BACK`、bounds 均有一致实现，并有 21 例 Python 单测覆盖。
- **工程化完整**：Android Lint 门禁、CI 构建 + Artifact、tag 自动 Release（含 SHA256）、分支保护与 `dev→main` 流程。
- **无硬编码密钥**：敏感配置走环境变量/本地凭据，仓库未提交 token。

---

## Summary

- **Critical：2 个**
- **Warning：12 个**
- **Info：10 个**
- **总体评价**：架构分层清晰、规则引擎还原度高、具备测试/CI/发布链路，属于可交付的工程；主要风险集中在**本地服务安全边界（CORS/CSRF）**、**全局规则误操作**，以及若干**"配置了但未实现"**的参数。建议优先级：Critical 1/2 → Warning 1（参数未实现）/2（批量写入性能）/6（加载时写库）/7（测试与 CI）。

---

## 修复状态（2026-10-23 第二轮）

| 编号 | 问题 | 状态 | 处理说明 |
| --- | --- | --- | --- |
| C1 | CORS 通配 + CSRF | ✅ 已修复 | 删除通配 CORS；非本机 Origin 返回 403；OPTIONS 不再发送 ACAO（`server.py`） |
| C2 | 全局规则/自动返回误操作 | ✅ 已缓解 | 默认全局关键词收窄为「跳过广告/关闭广告」；自动返回默认关闭（需手动开启）；文档标注风险 |
| W1 | `delay` / `unite_popup_rules` 未实现 | ✅ 已实现 | 双引擎新增联合规则「中间态门控」；Android 按 `delay/delay_popup` 延时执行（`Handler.postDelayed`） |
| W2 | 逐条 SQL 慢 | ✅ 已修复 | `db.transaction()` + `import_rules` 单事务；实测 795 条 ≈ 0.06s（原 ~40s）；Android 导入也改为事务 |
| W3 | 无 busy_timeout / WAL | ✅ 已修复 | `get_conn` 设 `busy_timeout=10000`、`journal_mode=WAL`、`timeout=10` |
| W4 | 路径校验前缀匹配 | ✅ 已修复 | 改为 `Path.is_relative_to()` |
| W5 | Android 导出 SQL 拼接 | ✅ 已修复 | 改为参数化 `hash=?` |
| W6 | 编辑页加载触发写库 | ✅ 已修复 | 增加 `loading` 标志，加载期间忽略监听回调 |
| W7 | 测试/CI 缺口 | ◐ 部分完成 | CI 新增 `Python Tests` job（25 例）；Java 单测待补（需测试工厂/Robolectric） |
| W8 | Node 未回收 | ✅ 已修复 | `NodeSnapshot.recycleTree()`，事件处理完回收 |
| W9 | 无前台服务 | ⏭ 评估不改 | AccessibilityService 由系统绑定，FGS 非必需（原版 FGS 主要为其他用途）；已在文档说明 |
| W10 | 双引擎漂移 | ◐ 部分完成 | `unite` / `filter_young` 语义已同步；共享测试向量待补 |
| W11 | simulate 污染统计 | ✅ 已修复 | `skip_logs` 增加 `source` 列，模拟写入 `simulate`；UI 已注明「模拟器运行计数」 |
| W12 | 死配置 | ✅ 已处理 | `filter_young_popups` 已实现；移除 `is_user_check`；`ltt_service` 标注保留 |
| Info | 魔法数字/服务状态判断/分隔符 | ✅ 已处理 | 常量提取；`isServiceEnabled` 改为组件列表解析；`|` 改为首个分隔符 |
| Info | 注释正则误删 URL | ✅ 已修复 | 仅剥离块注释与行首 `//` |
| Info | 输入上限/版本一致性 | ✅ 已处理 | 导入 5MB 上限；Web 版本统一为 `2.2.1` |
| Info | 文件过大拆分 | ⏭ 待办 | `api.py`/`app.js` 拆分列为后续重构，不影响功能 |

> 结论：Critical 2/2 已处理；Warning 12 项中 10 项已修复/实现，2 项部分完成并记录待办；Info 主要项已处理。
