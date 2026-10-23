# 李跳跳 (LiTiaotiao) 复刻项目

对「李跳跳」——一款利用 Android 无障碍服务自动跳过 App 开屏广告与弹窗的工具——的**高保真复刻**。
本仓库包含两个可运行的实现：

| 目录 | 形态 | 说明 |
| --- | --- | --- |
| [`LiTiaotiao-Android/`](./LiTiaotiao-Android) | **安卓 App** | 真实 Android 工程（Java + AccessibilityService + SQLite），可编译安装 |
| [`LiTiaotiao-Replica/`](./LiTiaotiao-Replica) | **Web 工作台** | Python 标准库后端 + 原生前端，规则管理 / 导入导出 / 节点树模拟器 |

两者共享同一套**规则引擎语义**、数据模型与规则格式。

## 核心能力

- 规则匹配：`+`开头 / `-`结尾 / `=`完全 / 默认模糊包含 / `&`且；动作支持文本·id、`GLOBAL_ACTION_BACK`、坐标 `x1,y1,x2,y2`（含 CheckBox 状态）、`times` 点击次数。
- 参数：`click_way_popup`、`search_times_popup`、`unite_popup_rules`、`delay`、`reverse_traversal` 等，键名还原自原版 APK。
- 规则导入/导出：兼容官方 `AllRules.json / BasicRules.json / ExtendedRules.json`，容错尾随逗号；支持单应用 / readme 文档按包名导入。
- 应用管理：搜索/筛选、启用/禁用、白名单、**手动新增自定义应用**、删除。
- **全局规则 / 小程序**：对所有包生效的全局规则 + 小程序宿主自动返回，拦截以小程序/独立容器打开的开屏广告。
- 内置社区规则：约 328 个应用 / 795 条规则，按 `Java String.hashCode(包名)` 反查真实包名与应用名。

## 安卓 App 构建 / 安装

```bash
cd LiTiaotiao-Android
# 方式一：Android Studio 打开目录，Run ▶
# 方式二：命令行（自动准备 JDK17 / Gradle / Android SDK）
python scripts/build_apk.py
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

> 已编译的 debug APK 位于 `LiTiaotiao-Android/dist/`（未纳入 git，需要时用上述脚本重新生成）。

## Web 工作台运行

```bash
cd LiTiaotiao-Replica
python run.py
# 打开 http://127.0.0.1:8000
```

## 文档

- 安卓：`LiTiaotiao-Android/README.md`
- Web：`LiTiaotiao-Replica/README.md`
- 功能对照 / 架构 / 测试 / 已知问题：`LiTiaotiao-Replica/docs/`

## 声明

原版李跳跳与社区规则库遵循各自开源协议（社区规则库为 AGPL-3.0）。本项目为学习 / 二次开发用途，请遵守各应用服务条款，合法合规使用。
