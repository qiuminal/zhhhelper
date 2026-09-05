# 贡献指南（Contributing）

虎助手（zhhhelper）是一个个人维护的开源项目。欢迎以 Issue、PR 或建议的方式参与，
请先阅读 README 与本文件。

## 报告问题（Issue）
- 使用内置模板：Bug 请附 设备/系统版本、操作步骤、预期与实际表现；建议请说明场景与价值。
- 涉及字体或码表数据的问题，请说明具体汉字/编码，便于复现。

## 提交代码（PR）
1. 从 main 拉分支开发，PR 标题用 `vX.Y.Z: 变更摘要` 风格，描述尽量简短。
2. 保持单一职责：一个 PR 只做一件事（修 bug / 新功能 / 质量改进）。
3. 代码改动请保持 Kotlin 空安全风格，禁止滥用 `!!`。
4. 影响纯逻辑的改动请尽量补/更新 JVM 单元测试（app/src/test）。
5. 合并前 GitHub Actions 会自动跑单元测试与 Debug 构建，必须通过。

## 码表更新
- 替换 data/ 下对应 txt 即可，Gradle 会在 preBuild 自动重新生成二进制码表。
- 不要手动编辑 app/src/main/assets/*.bin（构建产物，已 gitignore）。

## 发布流程（维护者）
本地打 tag 并推送，同时更新 CHANGELOG.md 与关于页文案；APK 签名密钥仅存于本地
/GitHub Secrets，绝不入库。
