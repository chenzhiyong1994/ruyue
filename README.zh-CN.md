# 如约 · Ruyue

[English](README.md) · [项目主页](https://chenzhiyong1994.github.io/ruyue/) · [开发指南](docs/development.md) · [测试指南](docs/testing.md)

**这是一个未完成的实验性安卓原型。截至 2026 年 9 月 30 日，开发已暂停。** 此次公开源码供学习、实验和独立 fork 使用，不承诺继续迭代、维护、回复 Issue 或 PR、修复安全问题，也不保证生产可用。

如约帮助彼此认识的人管理私人约定：说清目标和奖励，接受条款，记录进度，验收完成情况，再标记奖励已兑现。应用界面目前为简体中文，本次源码快照版本为 **1.4.3 / versionCode 12**。

仓库包含安卓客户端和需要自行运行的本地后端，没有托管账号服务，也不提供 APK Release 下载；请从源码构建开发预览包。

## 已实现的功能

```text
待接受 → 待完成 → 待验收 → 待兑现 → 已兑现
```

兑现方标记“已兑现”后，约定即结束，不需要收取方再次确认。任务达成与奖励兑现仍分别记录。

- **私人关系：** 两个账号通过邀请建立关系，各段关系的记录与权限分别管理。
- **明确条款：** 支持一次性或累计目标、可选截止时间、奖励和三步创建。生效条款的变更需要双方同意。
- **单一进度记录：** 创建、接受、进度说明与照片、提交、验收和兑现放在同一份历史中，不提供独立“留下说明”入口。
- **家长代记：** 孩子没有账号时可以由家长代记，历史保留真实操作人和代记来源。
- **共享状态：** 服务端负责权限、版本、时间与状态转换；解除关系不会抹除已经产生的奖励。

不包含陌生人市场、支付、积分商城、自动惩罚、后台推送或 AI 裁决。

## 界面预览

下图为原生安卓界面，内容均为合成演示数据。

| 日常 | 约定 | 奖励 |
| --- | --- | --- |
| ![按关系分组的日常页](site/assets/home.png) | ![按关系和状态分组的约定](site/assets/agreements.png) | ![奖励与兑现状态](site/assets/rewards.png) |

两张装饰性静物插画由 AI 生成，随应用本地提供。素材来源、提示词和使用位置见[插画素材记录](docs/design/ruyue-illustrations.md)。

## 本地运行

准备 **Python 3.11+**、**JDK 17**，以及包含 **Platform 36**、SDK Build-Tools 和 Platform-Tools 的 Android SDK。设置 `JAVA_HOME`、`ANDROID_HOME`，将 `adb` 加入 `PATH`。应用最低支持 **Android 8.0 / API 26**。使用仓库中的 Gradle Wrapper，首次构建需要联网下载依赖。

```sh
git clone https://github.com/chenzhiyong1994/ruyue.git
cd ruyue
python -m server --host 127.0.0.1 --port 8765 --database .local/pact/pact.sqlite3
```

如果系统使用 `python3`，请替换命令名。后端仅使用 Python 标准库。保持服务运行，在另一终端的仓库根目录构建。

**Windows PowerShell：**

```powershell
.\scripts\android.ps1
```

**macOS / Linux：**

```sh
sh android/gradlew -p android :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

将生成的 Debug APK 安装到明确选定的测试设备，把 `<device-serial>` 替换为 `adb devices` 显示的目标设备：

```sh
adb -s "<device-serial>" install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s "<device-serial>" reverse tcp:8765 tcp:8765
```

打开“开发连接设置”，填入 `http://127.0.0.1:8765`，注册合成测试账号。另一台设备使用不同账号，连接同一后端，即可尝试双方流程。调试包名为 `cn.zhiya.pact.preview`；不要为了强行安装而卸载或清空已有应用数据。

## 架构与限制

原生 Java 客户端通过 HTTP 连接 Python 标准库服务，SQLite 保存账号、关系、约定、事件和媒体。服务端是共享状态的权威来源，双方需要连接同一后端。[项目主页](https://chenzhiyong1994.github.io/ruyue/)只是静态介绍页，不运行 API。

此原型不提供生产托管、账号找回、托管备份、运行维护或持续安全支持。调试签名和本地构建成功不代表生产可用，请使用合成数据评估。考虑本地测试之外的部署前，请先阅读[安全说明](SECURITY.md)。

| 文档 | 内容 |
| --- | --- |
| [开发指南](docs/development.md) | 工具链、本地服务、安卓构建与配置，英文 |
| [测试指南](docs/testing.md) | 单测、HTTP、安卓及独立原生检查，英文 |
| [使用说明](docs/user-guide.md) | 应用操作流程 |
| [产品范围](docs/prd.md) | 目标与产品边界 |
| [共享 API](docs/api.md) | 权限与状态转换 |
| [架构与数据](docs/architecture.md) | 存储与一致性 |
| [验收标准](docs/acceptance.md) | PA-001 至 PA-009 验收项 |

## Fork 与许可

欢迎独立 fork。开发暂停期间，Issue 和 PR 可能无人回复；提交建议或代码不代表维护者承诺后续处理。详见[贡献指南](CONTRIBUTING.md)。

如约使用 [MIT 许可证](LICENSE)，不提供担保。第三方组件保留各自许可，详见 [NOTICE](NOTICE)。
