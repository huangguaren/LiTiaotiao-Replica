# 李跳跳 (LiTiaotiao) · 安卓复刻版

一款 **真正的 Android 应用**，基于 **无障碍服务（AccessibilityService）** 自动跳过 App 的开屏广告与弹窗。
本项目是对原版李跳跳（`hello.litiaotiao.app`）的**高保真复刻**：规则引擎、匹配语义、规则文件格式、数据库结构与管理界面均按原版还原，可直接用 **Android Studio** 编译安装到手机使用。

> 说明：原版仓库仅含加固后的 APK 与规则文件（无源码），且本复刻环境无 Android SDK/Gradle，无法在此直接产出 APK。
> 因此交付**完整、可编译的 Android 工程源码**——用 Android Studio 打开即可一键构建安装（详见下方「构建」）。

---

## 功能

- **无障碍服务** `LttAccessibilityService`：监听窗口/内容变化，取当前前台应用规则，遍历节点树自动点击「跳过/关闭」控件，或触发系统返回键。
- **规则引擎**（`engine/`）：与原版一致的匹配语义
  - `+foo` 开头 / `-foo` 结尾 / `=foo` 完全 / 默认模糊包含 / `&` 且；
  - 动作：文本·资源 id、`GLOBAL_ACTION_BACK`、坐标 `x1,y1,x2,y2`（含 CheckBox 状态 `,0/1`）；
  - 参数：`times` 点击次数（0=仅检测）、`click_way_popup` 强制点击、`search_times_popup` 扫描次数、`unite_popup_rules` 联合规则、`delay`/`delay_popup`、`reverse_traversal`。
- **全局规则 / 小程序**：对所有包生效的全局跳过关键词与弹窗规则，拦截以**小程序 / 独立容器**打开的开屏广告；可配置**小程序宿主包名**，找不到跳过按钮时**自动返回**兜底（设置入口：首页「全局规则 / 小程序」）。
- **首页**：服务开关（跳转系统无障碍设置）、已跳过次数、功能入口。
- **应用管理**：搜索、筛选（全部/启用/禁用/白名单）、启用/禁用、白名单。
- **规则详情**：查看/新增/编辑/删除弹窗规则与开屏关键词，配置应用级参数。
- **规则导入/导出**：兼容官方 `AllRules.json / BasicRules.json / ExtendedRules.json` 格式（容错尾随逗号）；可一键分享导出。
- **设置 / 关于**：还原 APK 资源中的真实参数键名（`is_main_switch_on`、`hide_toast`、`filter_invisible_nodes` 等）。
- **内置规则**：首次启动自动从 `assets/AllRules.json` 导入社区 328 个应用 / 795 条规则，并按 `Java String.hashCode(包名)` 反查真实包名与应用名。

---

## 技术栈

- 语言：**Java**（源码兼容 Java 8）
- 最低版本：`minSdk 24`，目标/编译：`compileSdk 34` / `targetSdk 34`
- 依赖：`androidx.appcompat`、`com.google.android.material`、`androidx.constraintlayout`
- 存储：`SQLiteOpenHelper`（结构见 `data/DbHelper.java`，与 Web 工作台一致，数据可互通）
- 无障碍点击坐标：`dispatchGesture` 手势点击
- 无 Kotlin、无注解处理器，构建简单稳定

---

## 已构建产物（可直接安装）

- **`dist/LiTiaotiao-2.2-replica-debug.apk`**（约 5.9 MB，debug 签名）
- 包名 `hello.litiaotiao.app`，版本 `2.2-replica`（versionCode 22），`minSdk 24` / `targetSdk 34`
- 安装方式：手机开启「允许安装未知来源应用」后点击安装；或连接电脑执行：
  ```bash
  adb install -r dist/LiTiaotiao-2.2-replica-debug.apk
  ```
- 说明：debug 签名适合自用/测试。若要上架或正式分发，请在 Android Studio 中配置自己的 release keystore 后 `assembleRelease`。

> 该 APK 由本仓库源码实际编译产出（Gradle 8.2 + AGP 8.2.2 + JDK 17 + compileSdk 34），已通过 `apksigner` 校验。

## 一键命令行构建（可选）

仓库附带构建脚本，可在联网环境自动准备工具链并编译：

```bash
python scripts/build_apk.py
# 自动下载 JDK17 / Gradle / Android SDK 组件到 .buildtools/，随后执行 gradle assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 构建与安装（Android Studio）

**环境要求**：JDK 17、Android SDK（Platform 34 + Build-Tools 34）、Android Studio（Hedgehog 或更新）。

1. 用 Android Studio 打开本目录（`LiTiaotiao-Android/`）。
2. 等待 Gradle Sync（首次会自动下载 Gradle 8.2 与依赖，需联网）。
3. 连接手机，点击 Run ▶，或命令行：

   ```bash
   ./gradlew installDebug        # 若本地有系统 Gradle，可先执行 `gradle wrapper` 生成 gradlew
   ```

4. 安装后打开「李跳跳」→ 首页打开开关 → 在系统**无障碍设置**中启用「李跳跳」。

> 若命令行没有 `gradlew`，直接在 Android Studio 里 Run 即可（Studio 会按其自带 Gradle 与
> `gradle/wrapper/gradle-wrapper.properties` 声明的 8.2 版本完成构建）。

---

## 目录结构

```
LiTiaotiao-Android/
├── settings.gradle / build.gradle / gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/
        │   ├── AllRules.json          # 内置社区规则
        │   └── package_index.json     # hash ↔ 包名 ↔ 应用名
        ├── java/hello/litiaotiao/app/
        │   ├── MainActivity.java
        │   ├── AppListActivity.java / RuleDetailActivity.java
        │   ├── ImportActivity.java / SettingsActivity.java / AboutActivity.java
        │   ├── service/LttAccessibilityService.java   # 核心无障碍服务
        │   ├── engine/                               # 规则引擎（与 Web 版等价）
        │   │   ├── RuleEngine.java / RuleMatcher.java
        │   │   ├── NodeSnapshot.java / RuleSet.java / Rule.java
        │   │   └── ActionType.java / PlannedAction.java
        │   ├── data/DbHelper.java / Repository.java
        │   ├── io/RuleJson.java                      # AllRules.json 解析/导出
        │   ├── ui/AppListAdapter.java / RuleListAdapter.java
        │   └── util/JavaHash.java / Prefs.java / PackageIndex.java / Assets.java
        └── res/ (layout / values / xml / drawable / mipmap)
```

---

## 规则文件格式

```json
[
  {"-330301876": "{\"popup_rules\":[{\"id\":\"native_ad_container\",\"action\":\"topon_btn_close\"},{\"id\":\"| 跳过\",\"action\":\"| 跳过\"}],\"click_way_popup\":1}"}
]
```

- 键 = `Java String.hashCode(packageName)`（可为负数）；
- 值 = 规则 JSON 字符串：`popup_rules` / `keywords` / `keywords_append` + `click_way_popup`、`search_times_popup`、`delay`、`unite_popup_rules` 等参数。

---

## 已知限制

- 本复刻环境无 Android SDK/Gradle，未在此编译打包；请在 Android Studio 中构建（代码为标准 API，无 Kotlin、无注解处理器，构建稳定）。
- 坐标类规则（bounds）随设备/分辨率变化，与原版一样存在设备相关性。
- 详见仓库根目录 `LiTiaotiao-Replica/docs/已知问题.md` 的通用说明（引擎语义完全一致）。

---

## 许可与声明

原版李跳跳与社区规则库遵循各自开源协议（社区规则库 AGPL-3.0）。本复刻为学习 / 二次开发用途，请遵守各应用服务条款，合法合规使用。
