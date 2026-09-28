# 如约 开发指南

## 产品边界

- 如约管理亲密关系中的私人约定：建立关系、提出与接受、执行与验收、奖励给出与收讫。安卓客户端共享同一服务端状态。
- 任务达成和奖励兑现独立；生效条款经双方同意才变更，到期或解除关系不能清除已产生的奖励。
- 家长代记始终保留真实操作人和代记来源；普通共享关系不允许一人代替另一方确认。
- 不加入陌生人市场、支付、积分商城、自动惩罚、后台推送或 AI 裁决。

## 权威入口

- 范围与行为：[产品需求](docs/prd.md)、[使用说明](docs/user-guide.md)、[验收标准](docs/acceptance.md)。
- 实现与验证：[共享 API](docs/api.md)、[架构](docs/architecture.md)、[开发指南](docs/development.md)、[测试指南](docs/testing.md)。
- 当前事实以代码和运行证据为准，预期行为以有效契约为准；发现偏差先说明，不降低验收标准。

## 修改约定

- `android/` 是原生 Java 客户端，`server/` 是 Python 标准库服务，`site/` 是静态介绍页。
- 服务端负责权限、版本、时间和状态；业务变更、事件和幂等回执必须在同一事务提交。
- 断网不能显示提交成功；刷新和恢复前台读取权威状态。修改 API 或状态时同步客户端、服务端、契约和回归测试。
- 保留 PA 验收 ID 和当前应用身份。不得将课程业务或旧资源重新接入构建。
- 仅使用合成测试资料；账号、令牌、签名密钥、真实家庭记录和数据库不得提交。保留已有设备和用户数据，不自动清理或迁移。
- `.local/` 用于本地数据，`artifacts/` 用于可再生输出，均保持 Git 忽略。预览包需要独立运行服务端。
- GitHub Pages 只发布 `site/`，不运行后端。测试、演示和静态站点不能描述为生产服务或真实用户验收。

## 最小验证

使用 Python 3.11+、JDK 17、Android SDK 36 和仓库中的 Gradle Wrapper：

```sh
python scripts/validate_project.py
python -m unittest discover -s tests -p 'test_*.py' -v
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
cd ..
git diff --check
```

Windows 可使用 `android/gradlew.bat` 或 `scripts/android.ps1`。界面与业务流程变化还需在专用测试设备上验证受影响交互，详见测试指南。收尾说明实际检查及未覆盖范围；本地构建不代表已部署、上架或完成用户研究。
