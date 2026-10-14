# 如约 开发指南

## 项目状态与边界

- 本仓库公开未完成的实验性源码，发布版本为 1.0.0 / code 13，沿用此前 1.4.3 快照的业务行为。截至 2026-09-30 开发暂停，不承诺维护、回复、后续版本或生产可用；不要擅自将材料写成活跃维护或成熟产品。
- 如约管理亲密关系中的私人约定：关系 → 提出与接受 → 执行与验收 → 奖励兑现。主状态为待接受、待完成、待验收、待兑现、已兑现；兑现方标记即结束，无接收方二次收讫。
- 详情只有一份进度记录，包含创建和接受，不恢复独立“留下说明”入口。旧 `offered` 读取为已兑现，保留历史和原幂等回执，不伪造收讫事件。
- 任务达成与奖励兑现独立；生效条款经双方同意才变更，到期或解除关系不能清除已产生的奖励。
- 家长代记保留真实操作人和代记来源；共享关系不允许一人代替另一方确认。
- 首版不含陌生人市场、支付、积分商城、自动惩罚、后台推送或 AI 裁决。

## 权威入口

- 范围与行为：[产品需求](docs/prd.md)、[使用说明](docs/user-guide.md)、[验收标准](docs/acceptance.md)。
- 实现与验证：[共享 API](docs/api.md)、[架构](docs/architecture.md)、[开发指南](docs/development.md)、[测试指南](docs/testing.md)。
- 参与与素材：[贡献指南](CONTRIBUTING.md)、[安全说明](SECURITY.md)、[插画来源](docs/design/ruyue-illustrations.md)。保留 MIT 及第三方许可。
- 当前事实以代码和运行证据为准，预期行为以有效契约为准；发现偏差先说明，不降低验收标准或引用未公开的历史作为公开证据。

## 修改约定

- `android/` 是原生 Java 客户端，`server/` 是 Python 标准库服务，`site/` 是静态介绍页。
- 服务端负责权限、版本、时间和状态；业务变更、事件和幂等回执必须在同一事务提交。
- 断网不能显示提交成功；刷新和恢复前台读取权威状态。修改 API 或状态时同步客户端、服务端、契约和回归测试。
- 保留 PA 验收 ID 和当前应用身份。界面局部调整延续现有关系分组，不新增顶部常驻“全部关系”。
- 仅使用合成测试资料；账号、令牌、签名密钥、真实家庭记录和数据库不得提交。保留已有设备与数据，不自动清理或迁移。
- `.local/` 保存本地运行数据，`artifacts/` 保存可再生输出，均保持 Git 忽略；不得在公开文档中引用私有证据或具体机器环境。
- GitHub Pages 只发布 `site/`，不运行后端。源码公开、设备测试和静态主页不能描述为生产服务或真实用户验收；1.0.0 提供 Debug 签名预览 APK 与源码下载，运行仍需自行启动服务，见[下载说明](docs/download.md)。

## 最小验证

使用 Python 3.11+、JDK 17、Android SDK 36 和仓库中的 Gradle Wrapper：

```sh
python scripts/validate_project.py
python -m unittest discover -s tests -p 'test_*.py' -v
sh android/gradlew -p android :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
git diff --check
```

Windows 可使用 `android/gradlew.bat` 或 `scripts/android.ps1`。按改动面选择检查；界面与业务流程变化还需在专用测试设备验证受影响交互，详见测试指南。收尾说明实际检查与未覆盖范围，不将旧报告或代码存在当作当前通过。
