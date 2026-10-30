# Android 构建与测试

## 工具

- JDK 17，设置 JAVA_HOME。
- Android SDK，设置 ANDROID_HOME，安装 platforms;android-35、build-tools;35.0.0 和 platform-tools。
- 官方 Gradle 8.9，加入 PATH（https://gradle.org/releases/）。本项目使用 AGP 8.7.3、AndroidX WorkManager 2.9.1。
- 测试另需 Python 3.11+；界面测试需 Node.js 和 Playwright。

## 构建

在仓库根目录执行：

```powershell
gradle --no-daemon -p outputs/eidolon-android assembleDebug
```

Gradle 首次会从 Google Maven/Maven Central 下载依赖，应用运行时不需要电脑后台。产物位于 work/android-build-1.16/gradle/outputs/apk/debug/；该目录不进版本库。也可在 Windows 执行：

```powershell
powershell -ExecutionPolicy Bypass -File outputs/eidolon-android/build.ps1
```

release 源码编译用 assembleRelease，生成未签名包；正式分发需使用自己的私有签名流程。私钥、密码和本地 SDK 路径不能提交。本仓库不提供维护者升级私钥。

## 离线规则与数据库测试

先运行 assembleRelease。将 org.json 的 JVM 测试依赖 json-20240303.jar 放到 work/android-test-tools/json.jar（Maven Central: org/json/json/20240303），再执行：

```powershell
powershell -ExecutionPolicy Bypass -File outputs/eidolon-android/test.ps1
```

Python 可通过 EIDOLON_PYTHON 指定，默认 python；JSON_JAR 可指定测试 jar。测试覆盖 Java 规则、真实 SQLite 事务与并发回滚，以及假 Android helper 的连接池所有权，不调用模型或手机。

## 模拟界面测试

```powershell
npm install --no-save playwright
npx playwright install chromium
node outputs/eidolon-android/tests/schedule-rollover-ui.cjs
```

其他 *-ui.cjs 测试在同目录，均为假本地桥接；可设置 PLAYWRIGHT_MODULE 指向已有 Playwright 包，CHROME_EXECUTABLE 指向已有浏览器。界面模拟通过不等于 Android 实机后台调度已通过。

## 当前源码

当前Android源码与APK为1.16/code71，数据库和加密备份格式v15。变更与验证范围见[架构改进](ARCHITECTURE-1.16.md)。

## 首次公开时的源码对应

首次公开的 Android 版本为1.13/code66。src、res、assets 和 AndroidManifest.xml 对应现有本地1.12；本次只整理公开副本的说明和工具路径，不改应用行为。完整性清单见 docs/android-1.13-files.json。

outputs/eidolon-android 下的历史发布说明保留原开发记录，一些旧脚本路径和工具前提已由本指南替代。

## 维护更新公告

每次发版更新 `outputs/eidolon-android/assets/update-notices.json`，版本/递增code与build.gradle一致。PowerShell构建入口自动执行检查；直接Gradle构建前应手动执行 `tools/check-release-notes.ps1`。更多步骤见[发版说明](../outputs/eidolon-android/RELEASING.md)。
