# 构建产物（可直接安装）

- **`LiTiaotiao-2.2.4-debug.apk`** — debug 签名，自用/测试可直接安装。
- 包名：`hello.litiaotiao.app`
- 版本：`2.2.4`（versionCode 26）
- minSdk 24 / targetSdk 34 / compileSdk 34
- 安装：
  ```bash
  adb install -r LiTiaotiao-2.2.4-debug.apk
  ```
  或手机开启「允许安装未知来源应用」后直接点击安装。
- 完整性校验：见同目录 `SHA256SUMS.txt`。
- 自行重新构建：`python scripts/build_apk.py`（产物在 `app/build/outputs/apk/debug/`）。

## 2.2.4 修复

- **修复「未打开其他应用却一直计跳过数」**：
  - 以**当前窗口所属应用**判定包名（原来用事件来源，可能是系统 UI / 输入法）；
  - **排除系统界面**（状态栏 / 桌面 / 设置 / 输入法 / 权限控制器等），不再对其执行点击；
  - 默认全局关键词收窄为「跳过广告 / 关闭广告」（去掉宽泛的「跳过」，避免命中自己首页的「已为你跳过」）；
  - skip_logs 增加 `package` 列，区分「哪个应用」与「规则类型」。
- 已在模拟器实测：静置 75 秒计数保持 0，不再自增。
- 小程序规则验证：宿主包内出现「跳过广告」→ 点击；纯图片广告 → 自动返回；非宿主包 → 不动作。

> 说明：debug 签名适合自用/测试。正式分发请在 Android Studio 配置自己的 release keystore 后 `assembleRelease`。
