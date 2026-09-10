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

## 版本与发布规范

### 版本号与变更记录
- 每个可测试版本先更新 `app/build.gradle` 中的 `versionCode` 与 `versionName`，并在 `CHANGELOG.md`、关于页文案中记录用户可见变更。
- 调试试验、临时验证和发布准备不得直接冒充正式 Release；测试 APK 应明确标注为 debug、trial 或 unsigned。

### 调试信息与临时组件清理
- 调试日志、文件诊断信息、路径/大小/开关状态等内部诊断文案，只允许在本地调试或临时测试分支使用；功能验证完成后必须删除，不能保留在正式代码路径中。
- 临时按钮、调试入口、测试菜单、占位视图、试验性组件和仅用于排查问题的资源，验证完成后必须移除；若功能本身需要保留，必须改成正式用户界面和正式文案。
- Release 前必须全局搜索并确认没有残留调试输出或调试文案，例如 `Log.d`、`println`、`printStackTrace`、`文件存在=`、`大小=`、`prefs=`、`debug` 测试入口等；必要的错误处理只能记录非敏感、面向用户的正式提示。
- Release APK 只能由清理后的 Release 源码构建，禁止把调试 APK、unsigned APK 或临时测试组件作为正式版本上传；签名必须使用项目正式发布证书。
- PR 描述应说明调试代码和临时组件已清理，并附上构建/测试结果；发现残留时不得合并或发布。
- GitHub Release 附件统一命名为 `zhhhelper-vX.Y.Z-release.apk`，例如 `zhhhelper-v0.2.3-release.apk`；每个 Release 只上传一个正式签名 APK。
- Release 标题统一为 `虎助手 vX.Y.Z`，正文沿用历史版本结构：版本号与日期、以 `·` 开头的变更条目、`详细记录见 CHANGELOG.md`，然后追加 GitHub 可点击的 `**Full Changelog**: https://github.com/qiuminal/zhhhelper/compare/v上一版本...v当前版本`。

## 发布流程（维护者）
本地打 tag 并推送，同时更新 CHANGELOG.md 与关于页文案；发布前按上述清理规范检查并构建正式 Release APK。APK 签名密钥仅存于本地/GitHub Secrets，绝不入库。
