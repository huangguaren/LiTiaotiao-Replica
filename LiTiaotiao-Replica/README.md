# 李跳跳 (LiTiaotiao) · 高保真复刻工作台

一款利用 **Android 无障碍服务（AccessibilityService）** 自动跳过 App 开屏广告与弹窗的工具的 **高保真复刻版**。
本仓库基于原项目仓库（`rongzhiy/LiTiaotiao`）及其社区规则库（`Snoopy1866/LiTiaotiao-Custom-Rules`）提取出的
**资源、字符串、规则数据结构与匹配语义**，完整复刻了其**规则引擎**与**管理界面**，并提供一个**可运行、可测试**的 Web 工作台。

> ⚠️ 复刻说明：原项目仓库仅包含加固后的 APK（腾讯乐固）与规则文件，**不含源码**，且本环境**无 Android SDK / Gradle**，
> 无法在此编译并运行原版 APK。因此以**功能等价、可独立运行与测试**的 Web 工作台作为交付物：其规则匹配逻辑与原版一致，
> 可直接用于学习、二次开发与规则管理。

---

## 功能特性

| 模块 | 说明 |
| --- | --- |
| 规则引擎 | 完整复刻李跳跳的匹配语义：`+` 开头 / `-` 结尾 / `=` 完全 / 默认模糊包含 / `&` 且；动作支持文本·id、`GLOBAL_ACTION_BACK`、坐标 `x1,y1,x2,y2`（含 CheckBox 状态 `,0/1`）；`times` 点击次数、`click_way_popup` 强制点击、`search_times_popup` 扫描次数、`unite_popup_rules` 联合规则 |
| 应用管理 | 浏览全部已适配应用（社区规则库 300+ 款），支持按名称/包名/hash 搜索、启用/禁用、加入白名单；支持**手动新增自定义应用**（填包名，自动计算 hashCode）与**删除应用**，新增后即可自行编写规则 |
| 全局规则 / 小程序 | **对所有包生效**的全局跳过关键词与弹窗规则，专门拦截以**小程序 / 独立容器**打开的开屏广告（前台包名变了、按应用匹配查不到的情况）；可配置**小程序宿主包名**，找不到跳过按钮时**自动返回**兜底 |
| 规则编辑 | 每个应用可查看/新增/编辑/删除弹窗规则（popup_rules）与开屏关键词（keywords / keywords_append），并配置应用级参数 |
| 规则导入 / 导出 | 兼容官方 `AllRules.json / BasicRules.json / ExtendedRules.json`（数组，键为 `Java String.hashCode(包名)`）；**容错外层与内层的尾随逗号**（`ExtendedRules.json` 就有外层尾随逗号）；支持**单应用规则对象 / 社区 readme.md 代码块**按包名导入（自动算 hashCode） |
| 模拟器 | 粘贴一段无障碍节点树（UI 快照），选择某应用规则或自定义规则，运行引擎并可视化展示「命中的控件 → 将要点击的目标」 |
| 全局设置 | 还原 APK 资源中的真实参数键名：`is_main_switch_on`、`hide_toast`、`filter_invisible_nodes`、`filter_young_popups`、`reverse_traversal` 等 |
| 真实存储 | 使用 SQLite 持久化应用、规则、参数、跳过日志与设置 |
| 零外部依赖 | 后端仅使用 Python 标准库（`http.server` + `sqlite3`），前端为原生 HTML/CSS/JS，**无需联网安装任何包即可运行** |

---

## 技术栈

- **后端**：Python 3.10+ 标准库（`http.server` 多线程、`sqlite3`、内置 `json`/`re`/`logging`）
- **前端**：原生 HTML + CSS + JavaScript（单页应用，无构建步骤）
- **数据**：SQLite（文件型数据库，位于 `data/litiaotiao.db`）
- **无第三方运行时依赖**（测试也仅用标准库 `unittest`）

---

## 目录结构

```
LiTiaotiao-Replica/
├── run.py                      # 启动入口（自动建库 + 导入种子规则 + 启动服务）
├── requirements.txt            # 运行依赖说明（标准库，无需 pip install）
├── .env.example               # 环境变量示例
├── litiaotiao/                # 后端包
│   ├── config.py              # 配置（环境变量 + 默认值）
│   ├── logging_setup.py       # 日志（文件 + 控制台）
│   ├── db.py                  # SQLite 连接 / Schema / 查询封装
│   ├── hashutil.py            # Java String.hashCode 复刻
│   ├── engine.py              # ★ 规则引擎（匹配 + 点击决策）
│   ├── rules_io.py            # AllRules.json 格式 解析 / 导出
│   ├── package_index.py       # hash ↔ 包名 ↔ 应用名 反向索引
│   ├── api.py                 # API 路由处理（统一错误处理）
│   ├── server.py              # HTTP 服务 + 静态资源 + 路由分发
│   └── seed.py                # 从 AllRules.json 初始化数据
├── static/                    # 前端
│   ├── index.html
│   ├── css/styles.css
│   └── js/{api,app}.js
├── data/                      # 运行时数据（数据库、包名索引）
├── scripts/                   # 构建/种子脚本
│   ├── build_package_index.py # 从社区规则库生成包名索引
│   └── seed.py
├── tests/                     # 单元测试（engine / hashutil / rules_io）
│   └── run_tests.py
└── docs/                      # 交付文档
    ├── 功能对照清单.md
    ├── 测试结果.md
    ├── 架构设计.md
    └── 已知问题.md
```

---

## 快速开始

```bash
# 1. 使用项目自带的受管 Python（或任意 Python 3.10+）
cd LiTiaotiao-Replica

# 2. 首次启动：自动初始化数据库并导入社区规则（约 30~40 秒）
python run.py
#   或指定端口 / 数据库路径：
#   LTT_PORT=8011 python run.py

# 3. 浏览器打开
#   http://127.0.0.1:8000   （或你指定的端口）
```

启动后默认会：
1. 初始化 SQLite 表结构；
2. 导入社区 `AllRules.json`（328 个应用 / 795 条规则，已自动映射到真实包名与应用名）；
3. 写入默认全局设置；
4. 在本机 `http://127.0.0.1:8000` 提供 Web 工作台。

> 端口 8000 若被占用，可用 `LTT_PORT=8011 python run.py` 指定其他端口。

---

## API 概览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/health` | 健康检查 |
| GET | `/api/meta` | 应用元信息（名称/版本/无障碍描述） |
| GET/PUT | `/api/settings` | 读取/更新全局设置 |
| GET | `/api/stats` | 统计（应用数、规则数、已跳过次数） |
| GET | `/api/logs?limit=` | 最近跳过日志 |
| GET | `/api/apps?q=&enabled=&whitelisted=&limit=&offset=` | 应用列表（搜索/筛选/分页） |
| GET/PUT/DELETE | `/api/apps/<hash>` | 应用详情 / 更新 / 删除 |
| GET/POST | `/api/apps/<hash>/rules` | 规则列表 / 新增规则 |
| PUT/DELETE | `/api/apps/<hash>/rules/<rid>` | 编辑 / 删除规则 |
| POST | `/api/apps/<hash>/params` | 设置应用级参数 |
| POST | `/api/import` | 导入规则 JSON（AllRules 格式） |
| GET | `/api/export?scope=all\|enabled\|single:<hash>` | 导出规则 JSON |
| POST | `/api/simulate` | 运行规则引擎（传入 screen + 规则） |

---

## 规则导入说明

- **整包导入**：粘贴官方 `AllRules.json / BasicRules.json / ExtendedRules.json` 的 JSON 数组即可；键为 `Java String.hashCode(包名)`，导入器会容错**外层与内层**的尾随逗号（`ExtendedRules.json` 本身就有外层尾随逗号）。
- **单应用 / 文档导入**：粘贴某个应用的规则对象，或社区 `readme.md` 中 ``` 代码块的内容，在「包名」输入框填入该应用包名（如 `com.example.app`），系统据此计算 hashCode 后导入该应用。
- 同一 hash 的应用规则在导入时会被**覆盖更新**（与原版行为一致）。

## 规则引擎要点（与原版一致）- **触发器 `id`**：在屏幕节点的 `text` 或 `resource_id` 上匹配。
- **动作 `action`**：
  - 文本/id → 点击匹配控件（优先 `clickable` 节点）；
  - `GLOBAL_ACTION_BACK` → 模拟返回键；
  - `x1,y1,x2,y2` → 点击坐标（可选 `,0/1` 表示 CheckBox 状态）。
- **匹配前缀**：`+` 开头 / `-` 结尾 / `=` 完全匹配 / 默认模糊包含；`&` 连接多个条件（且）。
- **参数**：`times`（点击次数，0=仅检测，用于 `unite_popup_rules` 连续任务）、`click_way_popup`（强制点击）、`search_times_popup`（扫描次数）、`delay`/`delay_popup`、`unite_popup_rules`、`reverse_traversal` 等。
- **包名键**：规则以 `Java String.hashCode(packageName)` 为键（可为负数），本复刻通过社区规则库的目录结构反查出真实包名与应用名。

---

## 测试

```bash
python tests/run_tests.py
```

覆盖：hashCode 一致性、匹配语义（+/-/=/&/包含）、坐标/返回键动作、开屏关键词、`times` 点击次数、`unite_popup_rules` 的 `times=0` 仅检测、扫描去重、`AllRules.json` 解析（含尾随逗号容错）与导出往返。

---

## 许可与声明

- 原项目 `LiTiaotiao` 及其社区规则库遵循其各自的开源协议（社区规则库为 AGPL-3.0）。
- 本复刻为**学习 / 二次开发用途**的高保真还原，规则数据来自公开社区规则库；请遵守各应用的服务条款，合法合规使用。
