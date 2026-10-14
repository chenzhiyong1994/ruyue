# 下载与运行 · Downloads

如约 **1.0.0** 提供 Android 开发预览 APK 和源码。Android 8.0（API 26）及以上可安装；应用界面为简体中文。APK 需要连接自行运行的 Python 服务，项目主页只展示产品，不提供在线账号服务。

| 文件 | 下载 |
| --- | --- |
| Android 预览 APK | [ruyue-1.0.0-preview.apk](https://github.com/chenzhiyong1994/ruyue/releases/download/v1.0.0/ruyue-1.0.0-preview.apk) |
| 完整源码 ZIP | [v1.0.0 source](https://github.com/chenzhiyong1994/ruyue/archive/refs/tags/v1.0.0.zip) |
| SHA-256 校验文件 | [SHA256.txt](https://github.com/chenzhiyong1994/ruyue/releases/download/v1.0.0/SHA256.txt) |
| 版本与构建信息 | [manifest.json](https://github.com/chenzhiyong1994/ruyue/releases/download/v1.0.0/manifest.json) |
| 发布说明 | [GitHub Release v1.0.0](https://github.com/chenzhiyong1994/ruyue/releases/tag/v1.0.0) |

## 三步开始

1. 下载并解压源码，在源码目录运行 Python 3.11+ 服务：

   ```sh
   python -m server --host 127.0.0.1 --port 8765 --database .local/pact/pact.sqlite3
   ```

2. 下载 APK，连接启用 USB 调试的测试设备。安装 Android SDK Platform-Tools 后，用 `adb devices` 取得设备序列号，再执行：

   ```sh
   adb -s "<device-serial>" install -r ruyue-1.0.0-preview.apk
   adb -s "<device-serial>" reverse tcp:8765 tcp:8765
   ```

3. 打开如约，在“开发连接设置”填入 `http://127.0.0.1:8765`，注册测试账号。体验双人约定时，两台设备分别设置端口转发，以不同账号连接同一服务。模拟器也可使用 `http://10.0.2.2:8765` 访问宿主服务。

服务和 USB 转发需要保持运行。用手机直接安装 APK 也可以，但仍需配置同一后端连接。只使用合成资料试用；公网部署、账号找回、后台推送、自动备份和商店发布尚未提供。

## 校验与升级

PowerShell 使用 `Get-FileHash .\ruyue-1.0.0-preview.apk -Algorithm SHA256`；macOS 使用 `shasum -a 256 ruyue-1.0.0-preview.apk`；Linux 使用 `sha256sum -c SHA256.txt`。将结果与 Release 中的校验文件核对。

这是 Debug 签名的开发预览，包名仍为 `cn.zhiya.pact.preview`，实际版本为 `1.0.0-preview-debug` / `versionCode 13`。公开版本从此前的内部快照 1.4.3 重新编号为 1.0.0，安装序号由 12 增至 13；业务和数据库结构保持不变。同签名旧预览包可覆盖安装；若系统提示签名冲突，不要卸载或清除旧应用来强行安装，请先保留原资料并检查签名来源。自行构建的 Debug 包可能使用不同签名。

项目于 2026-09-30 暂停常规 APP 迭代；本次发布整理已有功能并提供下载，不承诺后续维护或生产可用性。更多配置见[开发指南](development.md)，验证方法见[测试指南](testing.md)。

## English quick start

Download the APK and source ZIP above. The preview supports Android 8.0+ and has a Simplified Chinese interface. Run the Python 3.11+ backend from the extracted source, install the APK on a selected test device, and forward port 8765 using the commands above. In **开发连接设置**, enter `http://127.0.0.1:8765`. Both participants must connect to the same backend with different accounts.

This is a debug-signed preview, not a hosted service or store release. Version 1.0.0 renumbers the earlier internal 1.4.3 snapshot while increasing Android's version code to 13. An in-place update requires a matching signing certificate; do not uninstall an existing app to bypass a signature conflict. Verify the APK against `SHA256.txt`. See [Development](development.md), [Testing](testing.md), and [Security](../SECURITY.md) for the evaluation limits.
