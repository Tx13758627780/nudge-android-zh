# Nudge 简体中文版

基于 [astraedus/nudge](https://github.com/astraedus/nudge) 的 Android 简体中文汉化，保留原项目的 GPL-3.0 许可。上游基准版本为 1.19.0（提交 `f05a1e7c2a1f2a577afe6b1f44f169e8c7f4ccbe`）。

汉化版版本号：`1.19.0-zh.1`；独立包名：`dev.astraedus.nudge.zh`，可以与官方版同时安装。

## 下载安装

[下载最新版汉化 APK](https://github.com/Tx13758627780/nudge-android-zh/releases/latest/download/nudge-zh.apk)，或在[版本发布页面](https://github.com/Tx13758627780/nudge-android-zh/releases)选择所需版本。下载完成后打开 APK 安装，系统语言为简体中文时自动显示中文。源码与构建记录保存在[汉化仓库](https://github.com/Tx13758627780/nudge-android-zh)。

## 中文支持

系统语言为简体中文时，自动显示中文。其他语言回退为英文。本次覆盖 Android 应用的首次使用引导、首页、规则与应用分组、统计图表、设置、备份提示、拦截界面、呼吸练习、严格模式、强力模式、二维码操作、通知和桌面小组件。

用户自定义的应用分组、规则名称和提示语保留原文。数据库枚举、第三方应用界面检测标识、备份文件格式保持兼容。浏览器扩展沿用上游英文版，此次汉化针对 Android 应用。

两个应用各自保存数据。从官方版迁移时，可先在官方版导出备份，再在汉化版导入。测试汉化版的拦截时，请关闭官方版的拦截，避免两套无障碍服务同时拦截。

## 开发环境

- Android Studio 稳定版，或命令行 Android SDK。
- 完整 JDK 17（需要 `javac`，仅安装 JRE 无法编译）。
- Android SDK Platform 36、Build Tools 36.0.0、Platform Tools。
- Gradle 8.13 已由仓库的 Gradle Wrapper 固定，无需另装 Gradle。
- Python 3，用于翻译资源检查。

Android Studio：打开项目根目录，设置 Gradle JDK 为 17，在 SDK Manager 中安装上述 SDK，再同步项目。

命令行安装 SDK 组件：

```bash
sdkmanager 'platforms;android-36' 'build-tools;36.0.0' 'platform-tools'
sdkmanager --licenses
```

在项目根目录创建 `local.properties`，写入你的 SDK 路径（不要提交这个文件）：

```properties
sdk.dir=/你的路径/Android/Sdk
```

验证和构建：

```bash
python3 scripts/check-translations.py
./gradlew testDebugUnitTest lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Windows 使用 `gradlew.bat`。设备需要 Android 8.0 或更新版本。调试 APK 适合开发测试，其签名可能随构建环境变化；长期使用和更新请用同一密钥签名的 release APK。

## GitHub 自动构建

推送到 `main` 或 `localization/zh-CN`、提交 PR，或者手动运行 Actions 中的“Android 中文版构建”，都会检查翻译、运行单元测试和 Android Lint，并生成 `nudge-zh-debug` APK 构建产物。此流程不需要签名密钥。

发布签名 APK 时，在仓库 Settings → Secrets and variables → Actions 中设置：

- `KEYSTORE_BASE64`：`nudge-release.keystore` 的 Base64 内容。
- `KEYSTORE_PASSWORD`：该密钥的密码。密钥别名为 `nudge`，密钥密码与存储密码相同。

随后推送 `zh-v*` 格式的版本标签，工作流会先验证，再创建带 `nudge-zh.apk` 的 Release。不要使用上游的签名密钥或商店发布凭据。

## 本地签名与后续更新

已生成的汉化版签名密钥及密码必须单独备份，不能上传到 Git 仓库。后续更新应一直使用同一密钥；重新生成密钥会导致已安装用户无法直接覆盖更新。

首次自行开发且没有现有密钥时，可生成你自己的密钥：

```bash
keytool -genkeypair -keystore nudge-release.keystore -alias nudge \
  -keyalg RSA -keysize 3072 -validity 10000 -storetype PKCS12
```

在根目录创建 `keystore.properties`：

```properties
storeFile=../nudge-release.keystore
storePassword=你的密码
keyAlias=nudge
keyPassword=你的密码
```

执行 `./gradlew assembleRelease`，APK 在 `app/build/outputs/apk/release/app-release.apk`。签名文件、密码和 `local.properties` 都已被 `.gitignore` 排除。

## 维护翻译

英文默认文案在 `app/src/main/res/values/strings*.xml`，中文在 `app/src/main/res/values-zh-rCN/strings*.xml`。界面使用资源引用，新增功能时请同时更新中英文资源。

`python3 scripts/check-translations.py` 检查资源覆盖、重复资源、格式参数及复数资源，防止遗漏翻译或格式化时崩溃。纯计算辅助函数中保留的英文兼容值仅用于原有接口和测试，显示时由界面资源映射为中文。

## 许可与上游

完整许可见 [LICENSE](LICENSE)。原项目作者、贡献记录和原始英文介绍见 [README.md](README.md)。汉化修改同样遵循 GPL-3.0。
