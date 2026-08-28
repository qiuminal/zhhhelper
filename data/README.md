# 码表源数据与二进制编译

本目录存放三个**原始格式**码表 txt（每次更新码表时，直接替换本目录下的 txt 即可，其余不用动）：

| 文件 | 内容 | 列格式 |
| --- | --- | --- |
| `zi.txt` | 字头 + 编码 | 第1列字头，第2列编码 |
| `chai.txt` | 字头 + 拆分两行 + 拼音 + U码 | 第1列字头，第2/3列拆分两行，第4列拼音，第5列U码区块，第6列U码码点 |
| `zheng.txt` | 字头 + 整句码 | 第1列字头，第2列整句码 |

## 二进制转换（构建时自动执行，无需手动操作）

Gradle 任务 `generateTablesBin`（定义在 `app/build.gradle`）会在每次构建前读取本目录三个 txt，
按字头 UTF-16 码元排序合并，编译为 `app/src/main/assets/tables.bin`（大端序二进制字典），
App 运行时对 `tables.bin` 做二分查找，不再逐行解析 txt。

- 更新码表：替换本目录下的 txt 文件 → 重新构建 APK → 自动生成新 `tables.bin`
- 二进制格式细节见 `app/build.gradle` 中 `generateTablesBin` 上方的注释
- `tables.bin` 带 Adler32 校验，损坏/被改会拒绝加载（真正的防篡改由 APK 签名保证）
