# 李跳跳测试专用 App

用于在真机 / 模拟器上**验证李跳跳的广告跳过效果**（开屏广告 + 应用内弹窗）。

## 功能

| 界面元素 | 用途 |
| --- | --- |
| 开屏广告区「跳过广告」按钮 | 命中李跳跳**默认全局关键词**（跳过广告），验证开屏广告跳过 |
| 「打开更新弹窗」按钮 | 弹出「检测到更新」对话框，验证**弹窗规则**跳过 |

## 构建

复用 `LiTiaotiao-Android/.buildtools` 里的工具链（JDK / Gradle / SDK）：

```bash
cd LiTiaotiao-TestTarget
export JAVA_HOME="../LiTiaotiao-Android/.buildtools/jdk_extract/jdk-17.0.20.1+1"
export ANDROID_HOME="../LiTiaotiao-Android/.buildtools/sdk"
export GRADLE_USER_HOME="../LiTiaotiao-Android/.buildtools/gradle_home"
../LiTiaotiao-Android/.buildtools/gradle-8.2/bin/gradle.bat assembleDebug --no-daemon
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 使用步骤

1. **安装**：`adb install -r app/build/outputs/apk/debug/app-debug.apk`
2. **（可选）注入弹窗规则**到李跳跳数据库：
   ```bash
   HASH=$(python -c "import sys;sys.path.insert(0,'../LiTiaotiao-Replica');from litiaotiao.hashutil import java_hashcode;print(java_hashcode('hello.litiaotiao.testapp'))")
   adb shell "run-as hello.litiaotiao.app sqlite3 databases/litiaotiao.db \
     \"INSERT OR REPLACE INTO apps(hash,package,display_name,enabled,whitelisted,source,created_at,updated_at) \
      VALUES($HASH,'hello.litiaotiao.testapp','跳跳测试',1,0,'manual','2026-10-24','2026-10-24'); \
      DELETE FROM rules WHERE app_hash=$HASH; \
      INSERT INTO rules(app_hash,rule_type,match_id,action,times,sort_order) \
      VALUES($HASH,'popup','检测到更新','以后再说',1,0);\""
   ```
3. **启动测试 App**（`adb shell am start -n hello.litiaotiao.testapp/.MainActivity`），观察：
   - 「跳过广告」被自动点击；
   - 点「打开更新弹窗」后，「以后再说」被自动点击。
4. **验证**：查看李跳跳首页计数与数据库 `skip_logs` 表。

## 已验证结果（2026-10-24，Android 14 模拟器）

```
skip_logs:
  keyword | hello.litiaotiao.testapp | 跳过广告 | NODE | ...:id/btnSkip
  popup   | hello.litiaotiao.testapp | 以后再说 | NODE | android:id/button2
```

两条链路（全局关键词 → 开屏广告；应用规则 → 更新弹窗）均真实生效。
