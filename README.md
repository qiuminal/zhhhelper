# 虎助手（zhhhelper）

虎助手是一款安卓端「形码编码与拆字查询」工具。输入汉字即可实时查询该字的形码编码、拆分部件、拼音、Unicode 与整句码，码表全部内置，纯离线运行。

## 功能特性

- **多字查询**：一次输入一个或多个汉字，自上而下逐字输出查询卡片，下滑可查看全部结果
- **结果卡片**：每张卡片展示字头、编码、拆分（字根码 + 部件两行）、拼音、Unicode、整句码（单字在虎整句中的打法）
- **图片分享**：每张卡片可一键生成与界面渲染一致的结果图（带虎助手水印），分享到微信、QQ 群等应用，内置字体在分享图中同样正常展示
- **在线跳转**：字统（zi.tools）、汉典（zdic.net）一键直达当前字的查询页
- **全局字体**：内置 TumanPUA、霞鹜文楷、遍黑体 P1/P2 四款字体，按字符级 fallback 渲染，生僻字、CJK 扩展区汉字与拆分部件均可正常显示
- **字号调节**：结果卡片字号可自由加减，分享图字号同步生效
- **侧滑菜单**：首页 / 关于

## 数据

内置虎码全字集码表（zi / chai / zheng 三表，以字头为主键合并）。码表在构建时编译为二进制索引 `assets/tables.bin`，启动快、查询快、数据不可直接编辑；更新码表只需替换 `data/` 目录下的原始 txt 后重新构建。

## 技术

- 语言：Kotlin（全部源码）
- 兼容：minSdk 23（Android 6.0+），targetSdk 34
- 依赖：AndroidX、Material Components

## 构建

环境要求：JDK 17、Android SDK（compileSdk 34）、Gradle 8.x。

```bash
./gradlew assembleRelease
```

## 开源与授权

- 字体：霞鹜文楷、遍黑体（SIL OFL 1.1）；TumanPUA（MIT）。许可证全文见 `app/src/main/assets/fonts/licenses/`
- 本项目采用 GPL-3.0 协议开源，详见 [LICENSE](LICENSE)
