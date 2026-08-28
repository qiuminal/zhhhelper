# 内置字体与开源授权说明（Fonts & Licenses）

虎助手 APK 内置四款字体，按「字符级 fallback」顺序全局使用：

| 优先级 | 文件 | 字体族 | 来源项目 | 授权 |
|---|---|---|---|---|
| 1 | assets/fonts/TumanPUA.ttf | TumanPUA | [ywxt/rime-huma](https://github.com/ywxt/rime-huma)（虎码输入方案） | MIT License |
| 2 | assets/fonts/LXGWWenKaiGBScreen.ttf | LXGW WenKai GB Screen | [lxgw/LxgwWenKai](https://github.com/lxgw/LxgwWenKai)（霞鹜文楷） | SIL Open Font License 1.1 |
| 3 | assets/fonts/PlangothicP1.ttf | Plangothic P1 | [Fitzgerald-Porthmouth-Koenigsegg/Plangothic_Project](https://github.com/Fitzgerald-Porthmouth-Koenigsegg/Plangothic_Project)（遍黑体） | SIL Open Font License 1.1 |
| 4 | assets/fonts/PlangothicP2.ttf | Plangothic P2 | 同上（遍黑体） | SIL Open Font License 1.1 |

## 授权判定：霞鹜文楷与遍黑体

霞鹜文楷（LxgwWenKai）与遍黑体（Plangothic Project）均以 **SIL Open Font License 1.1（OFL-1.1）** 开源发布。OFL 对「商业 / 非商业」并不作区分，**允许把字体与软件捆绑（embedding）分发，且免费、无需书面授权、无版税**。因此对本 APP（个人、非商用）而言，直接内置字体即合规，前提是满足 OFL 的下列义务：

- 随字体一起分发 OFL 许可证全文（本目录与 APK 内 `assets/fonts/licenses/` 均已附带）；
- 不得单独出售字体文件（作为软件一部分捆绑不受限）；
- 修改后的字体不得使用「保留字体名」（Reserved Font Name）发布，除非满足原作者的附加许可；本 APP 使用原版字体、未做修改，不受此限制；
- 不得使用字体名或作者名暗示对软件产品的背书。

### 霞鹜文楷附加说明
作者保留字体名：`霞鹜`、`霞鶩`、`落霞孤鹜`、`落霞孤鶩`、`LXGW`，并给出附加许可：由原版重新编译、或仅做子集化/格式转换（如 WOFF/WOFF2）用于网页字体的修改版本，可继续使用保留字体名。本 APP 直接捆绑原版 `.ttf`，符合 OFL-1.1 要求。

### 遍黑体附加说明
遍黑体 P1/P2 覆盖 CJK 扩展 B–G 等超大字库，适合显示拆分部件中的生僻字与扩展区字符。其授权同为 OFL-1.1，随附许可证全文。

### TumanPUA 来源声明
TumanPUA（assets/fonts/TumanPUA.ttf）来自虎码输入方案 [ywxt/rime-huma](https://github.com/ywxt/rime-huma)，项目以 MIT License 开源。该字体将大量拆字部件映射到 Unicode 私有区（U+E000–U+F8FF），用于显示码表中非汉字部件。MIT 许可允许自由使用、修改与再分发，需保留版权声明（见 `LICENSE-rime-huma-MIT.txt`）。

## 许可证全文清单

| 文件 | 对应字体 | 说明 |
|---|---|---|
| OFL-LXGWWenKai.txt | LXGWWenKaiGBScreen.ttf | SIL OFL 1.1 全文 + 版权/保留字体名声明 |
| OFL-Plangothic.txt | PlangothicP1.ttf、PlangothicP2.ttf | SIL OFL 1.1 全文 + 版权声明 |
| LICENSE-rime-huma-MIT.txt | TumanPUA.ttf | MIT 全文（来源项目 ywxt/rime-huma） |

> 以上许可证文本同时打包进 APK 的 `assets/fonts/licenses/`，随应用分发，满足 OFL 的随附要求。
