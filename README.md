# 虎助手（zhhhelper）

安卓形码编码与拆字查询工具。输入汉字，实时查询该字的形码编码、拆分部件、拼音、Unicode，内置文本数据库，纯离线查询。

- 项目英文名：zhhhelper
- 应用名：虎助手
- 包名：com.qiuminal.zhhhelper

## 功能

- 输入汉字，实时查询该字的形码编码、拆分部件、拼音、Unicode
- 支持多个编码显示（如简码、全码）
- 拆分部件上方显示字根编码（可选）
- 结果卡片字体大小可加减调节
- “字统”“叶典”链接：点击用系统浏览器打开对应网站的当前字查询页
- 数据全部内置在 APK assets 中，纯离线，无需网络

## 数据库格式

文件位置：pp/src/main/assets/huoma_data.txt

每行一个字，**制表符（Tab）分隔**，共 6 列（最后一列可选）：

`
字头	编码列表	拆分部件	拼音	Unicode	字根编码(可选)
`

| 列 | 说明 | 示例 |
|---|---|---|
| 1 字头 | 要查询的汉字 | 国 |
| 2 编码 | 形码编码，多个用空格分隔 | ni rn rnid |
| 3 拆分 | 拆字部件，用空格分隔 | 囗 王 丶 |
| 4 拼音 | 汉语拼音（可带声调） | guó |
| 5 Unicode | Unicode 码位 | U+56FD |
| 6 字根编码 | 可选，拆分上方小字显示 | Rk Nw Id |

> 替换数据时，直接覆盖 huoma_data.txt 即可，无需修改代码。文件必须为 UTF-8 编码、制表符分隔。

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
        │   └── huoma_data.txt          # 内置数据库（替换这里）
        ├── java/com/qiuminal/zhhhelper/
        │   ├── MainActivity.java        # 主页面：搜索 + 结果展示 + 外部链接
        │   ├── CharData.java            # 单字数据模型
        │   └── DataLoader.java          # assets 文本加载 + 内存查询
        └── res/
            ├── layout/activity_main.xml # 主布局
            ├── values/                  # 颜色、字符串、主题
            ├── drawable/                # 背景、图标
            └── mipmap-anydpi-v26/       # 自适应图标
`

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

> elease.jks 请妥善保管并加入 .gitignore，不要提交到仓库。

## 说明

- minSdk 21（Android 5.0+），targetSdk 34，纯 Java，无第三方依赖，仅用 AndroidX + Material Components。
- 刷新按钮会重新读取 assets 数据库，数据文件损坏后可通过刷新恢复。
- 查询会自动跳过输入中的空白与标点，取第一个汉字作为查询键。
