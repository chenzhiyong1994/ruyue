# 如约 · Ruyue

**把答应，变成相伴。 · Promises, kept together.**

[English](README.md) · [项目主页](https://chenzhiyong1994.github.io/ruyue/) · [开发指南](docs/development.md) · [参与贡献](CONTRIBUTING.md)

如约是一款面向亲密关系的安卓私人约定应用。情侣、家人或彼此认识的两个人，可以说清条件与奖励，记录进度、提交验收，再分别确认奖励已经给出和真正收到。

**当前是开发预览，不是生产服务。** 仓库包含安卓客户端和需要自行运行的后端，没有托管账号服务，也不提供 APK Release 下载；请从源码构建预览包。应用界面目前为简体中文。

## 任务达成，奖励也要兑现

任务完成和奖励收讫分别记录：

```text
提出约定 → 对方接受 → 记录进度 → 提交完成 → 验收达成
                                           ↓
                                       奖励待兑现
                                           ↓
                                       给予方标记给出
                                           ↓
                                       接受方确认收讫
```

- **私人关系：** 两个真实账号通过邀请建立关系，各段关系的记录与权限相互隔离。
- **说清条款：** 支持一次性与累计目标、可选截止时间、奖励说明，以及三步创建流程。
- **进度与证据：** 用文字和照片提交完成情况；达到目标次数仍需提交和验收。
- **双方确认：** 生效条款变更需协商，给予方不能单方面结清奖励；解除关系不抹除已经产生的奖励。
- **明确的家长代记：** 孩子没有账号时，家长可以代记；操作保留代记来源，不冒充孩子独立确认。
- **两端共享状态：** 服务端负责权限、版本、时间和状态转换，断网失败不会显示为提交成功。

不包含陌生人市场、支付、积分商城、自动惩罚、后台系统推送或 AI 裁决。

## 界面预览

下图为原生安卓界面，内容均为合成演示数据。

| 日常 | 约定详情 | 三步创建 |
| --- | --- | --- |
| ![日常页的重点约定](site/assets/home.png) | ![约定详情、进度与下一步行动](site/assets/detail.png) | ![创建约定并填写奖励](site/assets/create.png) |

## 本地运行

准备 **Python 3.11+**、**JDK 17**，以及包含 **Platform 36**、SDK Build-Tools 和 Platform-Tools 的 Android SDK。设置对应的 `JAVA_HOME`、`ANDROID_HOME`，并将 `adb` 加入 `PATH`。应用最低支持 **Android 8.0 / API 26**。仓库包含 Gradle Wrapper，首次构建需要下载依赖。

```sh
git clone https://github.com/chenzhiyong1994/ruyue.git
cd ruyue
python -m server --host 127.0.0.1 --port 8765 --database .local/pact/pact.sqlite3
```

如果系统使用 `python3` 调用 Python 3.11+，请替换命令名。后端仅使用 Python 标准库，无需安装额外 Python 依赖。保持服务运行，在另一终端的仓库根目录构建客户端。

**Windows PowerShell：**

```powershell
.\scripts\android.ps1
```

**macOS / Linux：**

```sh
sh android/gradlew -p android :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

调试 APK 位于 `android/app/build/outputs/apk/debug/app-debug.apk`，包名为 `cn.zhiya.pact.preview`。

将安卓设备或模拟器连接到本机开发服务；把 `<device-serial>` 替换为 `adb devices` 显示的目标设备：

```sh
adb -s "<device-serial>" install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s "<device-serial>" reverse tcp:8765 tcp:8765
```

打开应用中的“开发连接设置”，将服务地址设为 `http://127.0.0.1:8765`，再注册测试账号。另一台设备使用独立账号、连接同一后端，即可体验双方完整流程。`adb reverse` 只用于本地开发连接，不代表远程部署。

后端尚未生产部署。公开运营需要 HTTPS、生产签名、账号找回、备份恢复流程与运行安全审查。静态项目主页不承载账号数据或 API。详见[开发指南](docs/development.md)和[安全说明](SECURITY.md)。

## 文档

| 文档 | 内容 |
| --- | --- |
| [开发指南](docs/development.md) | 工具链、本地后端、安卓构建与配置，英文 |
| [测试指南](docs/testing.md) | 单测、HTTP、原生及独立双设备检查，英文 |
| [使用说明](docs/user-guide.md) | 建立关系、约定、验收与兑现 |
| [产品需求](docs/prd.md) | 产品范围与业务规则 |
| [共享 API](docs/api.md) | 接口、权限与状态转换 |
| [架构与数据](docs/architecture.md) | 身份、存储与一致性 |
| [验收标准](docs/acceptance.md) | 稳定的 PA-001 至 PA-009 验收项 |

## 贡献与许可

欢迎范围清晰的修复与改进。修改流程或契约前请阅读[贡献指南](CONTRIBUTING.md)，漏洞报告请遵循[安全说明](SECURITY.md)。

如约使用 [MIT 许可证](LICENSE)。捆绑的第三方组件保留各自许可，详见 [NOTICE](NOTICE)。
