# 构建产物（可直接安装）

- **`LiTiaotiao-2.2.5-debug.apk`** — debug 签名，自用/测试可直接安装（当前版本）。
- **`LiTiaotiao-2.2.4-debug.apk`** — 上一版本，保留备查。
- 包名：`hello.litiaotiao.app`
- 版本：`2.2.5`（versionCode 27）
- minSdk 24 / targetSdk 34 / compileSdk 34
- 安装：
  ```bash
  adb install -r LiTiaotiao-2.2.5-debug.apk
  ```
  或手机开启「允许安装未知来源应用」后直接点击安装。
- 完整性校验：见同目录 `SHA256SUMS.txt`。
- 自行重新构建：`python scripts/build_apk.py`（产物在 `app/build/outputs/apk/debug/`）。
- 跑规则引擎单元测试：`python scripts/run_tests.py`（35 项，纯 JVM）。
- 设备上自动验证效果：`python scripts/verify_on_device.py [-s 序列号]`。

> **构建可复现**：本目录的 APK 由**干净构建**（`gradle clean assembleDebug`）产出，
> 与 GitHub Release v2.2.5 附带的 APK 体积完全一致（5,897,758 字节），
> sha256 = `75700c0c7b936b981acbff5a443dca096aa63ce53bbb8e8f8625c75d0c089f54`。
> 增量构建产物体积会略大（含陈旧中间产物），以干净构建为准。

## 2.2.5 新增 / 修复

- **新增「严格防重复点击（5 秒）」开关**（设置页，默认开启）：
  常驻「跳过 / 关闭」按钮不再被每隔一两秒反复点击（冷却由 1.5 秒放宽到 5 秒）。
- **修复动画期间重复点击**：转场 / 开屏倒计时会让同一控件的屏幕坐标漂移，
  原去重签名把坐标算进身份，导致 1 秒内点两次。改为「有 resource id 就用 id 作身份，无 id 才用坐标」。
  实测「仅启动 App」由 2 次点击降为 1 次。
- **去重改用实际执行时刻**：原来用事件到达时刻，规则配了 `delay` 时会让间隔被低估而误判。
- **规则引擎可单元测试**：把 `AccessibilityNodeInfo` 依赖抽成 `NodeHandle` 接口（点击/回收），
  Android 侧由新增的 `NodeTree` 实现；引擎现在是零 `android.*` 依赖的纯 Java，新增 35 项 JVM 测试并接入 CI。
- **新增设备端自动验证脚本** `scripts/verify_on_device.py`：
  自动跑「开屏跳过 / 弹窗跳过 / 白名单」三场景，依据节点树与 `skip_logs` 判定 PASS / FAIL。

## 2.2.4 修复

- **修复「未打开其他应用却一直计跳过数」**：
  - 以**当前窗口所属应用**判定包名（原来用事件来源，可能是系统 UI / 输入法）；
  - **排除系统界面**（状态栏 / 桌面 / 设置 / 输入法 / 权限控制器等），不再对其执行点击；
  - 默认全局关键词收窄为「跳过广告 / 关闭广告」（去掉宽泛的「跳过」，避免命中自己首页的「已为你跳过」）；
  - skip_logs 增加 `package` 列，区分「哪个应用」与「规则类型」。
- 已在模拟器实测：静置 75 秒计数保持 0，不再自增。
- 小程序规则验证：宿主包内出现「跳过广告」→ 点击；纯图片广告 → 自动返回；非宿主包 → 不动作。

> 说明：debug 签名适合自用/测试。正式分发请在 Android Studio 配置自己的 release keystore 后 `assembleRelease`。
