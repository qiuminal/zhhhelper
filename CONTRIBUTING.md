# 贡献指南（Contributing）

虎助手（zhhhelper）是一个个人维护的开源项目。欢迎以 Issue、PR 或建议的方式参与，
请先阅读 README 与本文件。

---

# ⚠️ 铁律（动手前必读）

> 以下三条是本项目**反复踩过坑**的规则。任何接手本项目的维护者（含 AI 助手）
> 在改动日志、交付产物、发布版本前，**必须先逐条对照**，不得凭印象行事。

## 铁律一：更新日志分「对内详细版」与「对外精简版」，任何情况下都不得合并为一版

本项目**始终同时存在两版日志**，职责不同、篇幅不同、受众不同。把它们统一成一种写法（无论统一成详细版还是统一成精简版）都是错误。

| | 对内详细版 | 对外精简版 |
| --- | --- | --- |
| **载体** | `CHANGELOG.md`（仓库根目录） | ① 客户端关于页 `app/src/main/res/values/strings_about.xml`<br>② GitHub Release 正文 |
| **受众** | 维护者、后续接手者、排查问题的人 | 终端用户 |
| **篇幅** | **不受限制，越详细越好** | 只保留用户可感知的结论，通常 1 条即可 |
| **内容** | 技术实现、涉及范围与数据核对结论、构建与验证结果、工程决策与踩坑、版本号变更等 | 如「码表更新」这类一句话结论，**不含**内部实现细节 |
| **约束** | **严禁**以「对外要简洁」为由删减本文件 | 可为对内版的精简子集，但事实不得与其冲突 |

- 两版必须使用**相同的版本号与日期**。
- `CHANGELOG.md` 首行即标注「详细版」，修改前请先读该文件的自身说明。
- 发版流程固定为：**先写全对内详细版 → 再由维护者确认对外精简版**；顺序不可颠倒，也不可跳过对内版。
- ✅ 正确：`CHANGELOG.md` 写清 472 条区块名变更、逐行比对结论、记录数、校验值；关于页与 Release 正文只写「· 码表更新」。
- ❌ 错误：把 `CHANGELOG.md` 里这一版的内容也简化成「· 码表更新」。

## 铁律二：交付产物必须给出完整绝对路径

- 交付任何产物（APK、日志、报告、生成数据等）都必须给出**从盘符或根目录开始的完整绝对路径**，例如 `D:\dsh\zhhhelper\build-artifacts\zhhhelper-0.3.2-signed.apk`。
- 禁止只给相对路径或省略前级的写法（如 `build-artifacts\xxx.apk`、`app\build\outputs\...`）；此类写法无法直接定位文件，视为不合格交付。
- 一次交付多个产物时逐个列出完整路径，并附体积与 SHA-256。

## 铁律三：交付给用户安装测试的 APK 必须已用正式证书签名

- 未签名 APK **无法覆盖安装**，不得作为测试包交付；必须使用项目正式发布证书签名。
- 签名走 `app/build.gradle` 的 `signingConfigs.release`，通过环境变量 `STORE_FILE` / `STORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` 注入，与 CI 保持一致。
- 交付前用 `apksigner verify --verbose --print-certs` 确认 `Verifies` 通过，并核对证书 SHA-256。
- 命名区分：签名测试包用 `-signed`，未签名中间产物用 `-unsigned`。

---

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

### 产物路径必须完整（不可省略盘符/根目录）
- 交付任何产物（APK、日志、报告、生成数据等）时，必须给出**从盘符或根目录开始的完整绝对路径**，例如 `D:\dsh\zhhhelper\build-artifacts\zhhhelper-0.3.2-signed.apk`。
- 禁止只给相对路径或省略前级的写法（如 `build-artifacts\xxx.apk`、`app\build\outputs\...`）；此类写法无法直接定位文件，视为不合格交付。
- 一次交付多个产物时，逐个列出完整路径，并同时给出体积与 SHA-256，便于校验与比对。

### 交付给用户测试的 APK 必须签名
- 凡交付给用户安装测试的 APK，**必须使用项目正式发布证书签名**，确保能覆盖安装既有版本；未签名 APK 无法覆盖安装，不得作为测试包交付。
- 签名一律使用 `app/build.gradle` 的 `signingConfigs.release` 流程：通过环境变量 `STORE_FILE` / `STORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` 注入，与 CI 保持一致；密钥库为 `keystore/zhhhelper-release.jks`，别名 `zhhhelper`。
- 交付前必须验证签名：用 `apksigner verify --verbose --print-certs` 确认 `Verifies` 通过，并核对证书 SHA-256 与正式发布证书一致。
- 交付时必须区分命名：签名测试包用 `-signed`，未签名中间产物用 `-unsigned`，禁止把未签名包当作可安装测试包发出。

### 版本号与变更记录
- 每个可测试版本先更新 `app/build.gradle` 中的 `versionCode` 与 `versionName`，并按下文「更新日志双版本」规则分别维护对内详细版与对外精简版。
- 调试试验、临时验证和发布准备不得直接冒充正式 Release；测试 APK 应明确标注为 debug、trial 或 unsigned。

### 更新日志双版本（细则见开头「铁律一」）
- **对内详细版**：`CHANGELOG.md`，写清技术细节、涉及范围、数据核查结论、构建与验证结果；**严禁为对外简洁而删减**。
- **对外精简版**：① `app/src/main/res/values/strings_about.xml`（客户端关于页）；② GitHub Release 正文。只写用户可感知的结论条目（如「码表更新」）。
- 两版版本号与日期一致，对外版是对内版的精简子集，事实不得冲突。

### 构建方式（离线环境）
- 本机**无网络**，**不要用 `gradlew`**（wrapper 会尝试联网下载 Gradle 发行包而失败）。
- 统一使用共享 Gradle：
  ```powershell
  $env:JAVA_HOME        = 'D:\dsh\tools\jdk\jdk-17.0.20.1+1'
  $env:GRADLE_USER_HOME = 'D:\dsh\gradle-home'
  & 'D:\dsh\tools\gradle-8.5\bin\gradle.bat' clean assembleRelease --offline --no-daemon
  ```
- 修改 `strings_about.xml` 等资源后，必须 `clean` 后重建，否则可能残留旧文案（增量缓存）。
- **出包必字节扫描核验**：构建成功 ≠ 改动进包。需解包 APK 校验 `assets/tables.bin`（记录数 + Adler32）与 `resources.arsc` 中的文案字节（注意资源字符串多为 UTF-8）。

### 调试信息与临时组件清理
- 调试日志、文件诊断信息、路径/大小/开关状态等内部诊断文案，只允许在本地调试或临时测试分支使用；功能验证完成后必须删除，不能保留在正式代码路径中。
- 临时按钮、调试入口、测试菜单、占位视图、试验性组件和仅用于排查问题的资源，验证完成后必须移除；若功能本身需要保留，必须改成正式用户界面和正式文案。
- Release 前必须全局搜索并确认没有残留调试输出或调试文案，例如 `Log.d`、`println`、`printStackTrace`、`文件存在=`、`大小=`、`prefs=`、`debug` 测试入口等；必要的错误处理只能记录非敏感、面向用户的正式提示。
- Release APK 只能由清理后的 Release 源码构建，禁止把调试 APK、unsigned APK 或临时测试组件作为正式版本上传；签名必须使用项目正式发布证书。
- PR 描述应说明调试代码和临时组件已清理，并附上构建/测试结果；发现残留时不得合并或发布。
- GitHub Release 附件统一命名为 `zhhhelper-vX.Y.Z-release.apk`，例如 `zhhhelper-v0.2.3-release.apk`；每个 Release 只上传一个正式签名 APK。
- Release 标题统一为 `虎助手 vX.Y.Z`，正文沿用历史版本结构：版本号与日期、以 `·` 开头的变更条目、`详细记录见 CHANGELOG.md`，然后追加 GitHub 可点击的 `**Full Changelog**: https://github.com/qiuminal/zhhhelper/compare/v上一版本...v当前版本`。

## 发布流程（维护者）

> **本机没有 git 二进制，也没有 gh CLI。** 一切 Git/GitHub 操作**走 GitHub REST API**。
> 不要再问维护者「怎么发版」——照下面流程执行即可。

### 凭据（已配置，直接读取，不要向用户索要）

- Windows 凭据管理器目标名：`git:https://github.com`，用户 `qiuminal`，保存的是 `gho_` 开头 token（`repo` + `workflow` 权限）。
- 读取方式（PowerShell，经 `advapi32.dll` 的 `CredRead`，类型 Generic=1）：
  ```powershell
  # 见 D:\dsh\README.md「重要教训」一节；读出的 token 作为 Bearer 使用
  $h = @{ Authorization = "Bearer $tok"; 'User-Agent' = 'dsh'; Accept = 'application/vnd.github+json' }
  ```
- 每个 pwsh 调用都是新进程，**每次都要重新读取 token**，不要指望环境变量跨调用保留。
- 创建 Release 必须用 `RELEASE_PAT`（仓库自带 `GITHUB_TOKEN` 创建 Release 会 403）。

### 发版步骤

1. **改版本与日志**：更新 `app/build.gradle` 的 `versionCode`/`versionName`；按「铁律一」分别写入对内详细版 `CHANGELOG.md` 与对外精简版关于页文案。
2. **清理检查**：全局搜索 `Log.d`、`println`、`printStackTrace`、`文件存在=`、`大小=`、`prefs=` 等调试残留，确认无误。
3. **离线构建签名 APK**（见上文「构建方式」），产物 `app/build/outputs/apk/release/app-release.apk`。
4. **字节扫描核验**：解包确认 `tables.bin` 与 `resources.arsc` 文案已更新（构建成功 ≠ 进包）。
5. **推送代码**：用 Git Data API 构造 commit（**文本做 LF 规范化，二进制原样**）→ 建 PR → 等 CI → 合并到 `main`。
   - `Invoke-RestMethod` 传中文必须用 `-Body ([Text.Encoding]::UTF8.GetBytes($json)) -ContentType 'application/json; charset=utf-8'`，否则中文变 `?` 且不可逆。
6. **打 tag**：`vX.Y.Z`（如 `v0.3.2`），指向发布提交。
7. **触发 release workflow**：**一律用 `workflow_dispatch` 主动触发并核验运行结果**；`schedule`(cron) 只当兜底（曾出现到点未触发）。
   - workflow 内需把 keystore 写到 `app/signing.jks`（Gradle `file()` 相对 app 模块）。
8. **创建 Release**（用 `RELEASE_PAT`）：
   - 标题：`虎助手 vX.Y.Z`
   - 正文结构：首行 `X.Y.Z（日期）` + `·` 开头的**对外精简**条目 + 末尾 `详细记录见 CHANGELOG.md` + `**Full Changelog**: https://github.com/qiuminal/zhhhelper/compare/v上一版本...v当前版本`
   - 正文**不用** `##`/`###` 标题（Release 名称栏已显示版本）
   - 附件名：`zhhhelper-vX.Y.Z-release.apk`，每个 Release **只上传一个正式签名 APK**。

### 其他注意

- 分支保护（strict checks/reviews）会挡 force push，这是设计目的；确需改写历史要先临时解除、推完立即恢复。
- APK 签名密钥仅存于本地/GitHub Secrets，**绝不入库**。

## 本项目资源位置

- 仓库本体：`D:\dsh\zhhhelper`
- 发布签名密钥：`D:\dsh\zhhhelper\keystore\zhhhelper-release.jks`（别名 `zhhhelper`，密码见同目录 `keystore-password.txt`）
- 构建产物与日志：`D:\dsh\zhhhelper\build-artifacts\`（已 gitignore，不入库）
- 共享工具链：`D:\dsh\tools\`（jdk / android-sdk / gradle-8.5）、`D:\dsh\gradle-home\`（离线依赖缓存）
- 工作区总说明：`D:\dsh\README.md`
