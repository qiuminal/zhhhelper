# 虎助手（zhhhelper）

安卓形码编码与拆字查询工具。输入汉字，实时查询该字的形码编码、拆分部件、拼音、U 码、整句码，内置码表，纯离线查询。

- 项目英文名：zhhhelper
- 应用名：虎助手
- 包名：com.qiuminal.zhhhelper

## 功能

- 输入汉字，实时查询编码、拆分（两行）、拼音、U 码、整句码
- 支持多个编码显示（如简码、全码）
- “字统”“叶典”链接：点击用系统浏览器打开对应网站的当前字查询页
- 结果卡片字体大小可加减调节
- 数据全部内置在 APK assets 中，纯离线，无需网络

## 数据来源（三张码表，均以「字头」为主键）

### ssets/zi.txt —— 字头 + 编码

`
字头	编码（多个用空格分隔）
`

示例：虎	zhh zh

### ssets/chai.txt —— 拆分、拼音、U 码

`
字头	拆分第1行	拆分第2行	拼音	CJK	U+XXXX
`

示例：虎	Zh	虎	hǔ hù	CJK	U+864E

- 拆分：展示两行，分别取第 2 列、第 3 列
- 拼音：取第 4 列
- U 码：取第 5、6 列拼接显示（如 CJK U+864E）

### ssets/zheng.txt —— 整句码

`
字头	整句码
`

示例：虎	zhh

> 文件均为 UTF-8 编码、Tab 分隔。chai.txt 首行带 BOM 也能正常解析。

## 项目结构

`
zhhhelper/
├── settings.gradle
├── build.gradle
└── app/
    ├── build.gradle
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/
        │   ├── zi.txt       # 字头 + 编码
        │   ├── chai.txt     # 拆分两行 + 拼音 + U码
        │   ├── zheng.txt    # 整句码
        │   └── fonts/       # 内置四款字体 + 授权文件
        ├── java/com/qiuminal/zhhhelper/
        │   ├── MainActivity.java        # 主页面：搜索 + 结果展示 + 外部链接
        │   ├── AppFonts.java            # 全局字体管理（字符级 fallback）
        │   ├── CharData.java            # 单字数据模型（三表合并）
        │   └── DataLoader.java          # 三码表加载 + 合并 + 内存查询
        └── res/
            ├── layout/activity_main.xml # 主布局
            ├── values/                  # 颜色、字符串、主题
            ├── drawable/                # 背景、图标
            └── mipmap-anydpi-v26/       # 自适应图标
`

## 内置字体与全局 fallback

为解决拆分部件（很多不属于汉字、位于 Unicode 私有区）无法显示的问题，APP 内置四款字体，在全局按「字符级 fallback」顺序逐字符渲染：

1. TumanPUA（虎码私有区部件）
2. 霞鹜文楷屏幕阅读版 LXGW WenKai GB Screen（常用汉字）
3. 遍黑体 Plangothic P1（CJK 扩展 B–F）
4. 遍黑体 Plangothic P2（CJK 扩展 G）

实现位于 `app/src/main/java/com/qiuminal/zhhhelper/AppFonts.java`：加载四款字体后用 `Paint.hasGlyph` 逐字符选择第一款能渲染的字体，并以 span 方式应用到整个界面；某字符四款字体都不包含时（如 emoji）交给系统字体渲染。

授权说明：霞鹜文楷与遍黑体均为 SIL OFL 1.1（允许免费捆绑进软件分发，商用/非商用均可，须附带许可证全文）；TumanPUA 来自 [ywxt/rime-huma](https://github.com/ywxt/rime-huma)（MIT）。许可证全文与详细判定见 [`licenses/FONTS.md`](licenses/FONTS.md)，并随 APK 打包进 `assets/fonts/licenses/`。

## 编译

环境要求：JDK 17、Android SDK（compileSdk 34）、Gradle 8.x。

`ash
# 首次同步
./gradlew build

# 生成 release APK（未签名，输出 app-release-unsigned.apk）
./gradlew assembleRelease
`

## 签名

未配置自动签名，release 产物需自行用 pksigner 签名：

`ash
# 1. 生成密钥库（仅需一次）
keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias zhhhelper -dname "CN=虎助手"

# 2. 对齐 + 签名
zipalign -p -f 4 app-release-unsigned.apk app-aligned.apk
apksigner sign --ks release.jks --out zhhhelper-release.apk app-aligned.apk
`

> elease.jks 请妥善保管并加入 .gitignore，不要提交到仓库。后续升级需用同一密钥签名，否则无法覆盖安装。

## 说明

- minSdk 23（Android 6.0+，因逐字符字形判断需 API 23 的 `Paint.hasGlyph`），targetSdk 34，纯 Java，无第三方依赖，仅用 AndroidX + Material Components。
- 查询会自动跳过输入中的空白与标点，取第一个汉字作为查询键。
