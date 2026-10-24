# 构建产物（可直接安装）

- **`LiTiaotiao-2.2.3-debug.apk`** — debug 签名，自用/测试可直接安装。
- 包名：`hello.litiaotiao.app`
- 版本：`2.2.3`（versionCode 25）
- minSdk 24 / targetSdk 34 / compileSdk 34
- 安装：
  ```bash
  adb install -r LiTiaotiao-2.2.3-debug.apk
  ```
  或手机开启「允许安装未知来源应用」后直接点击安装。
- 完整性校验：见同目录 `SHA256SUMS.txt`。
- 自行重新构建：`python scripts/build_apk.py`（产物在 `app/build/outputs/apk/debug/`）。

## 2.2.3 修复

- 修复启动闪退：`Card` 样式缺少 `layout_width/layout_height` 导致 `InflateException`；
- 修复卡片内控件宽度为 0：`Card` 样式补充 `android:orientation="vertical"`；
- 已在 Android 14 模拟器（雷电）实测启动、首页、应用列表正常，无崩溃。

> 说明：debug 签名适合自用/测试。正式分发请在 Android Studio 配置自己的 release keystore 后 `assembleRelease`。
