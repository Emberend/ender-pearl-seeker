# 末影寻珠 (Ender Pearl Seeker)

[![Build](https://github.com/Emberend/ender-pearl-seeker/actions/workflows/build.yml/badge.svg)](https://github.com/Emberend/ender-pearl-seeker/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

用**末影之眼**自动三角定位末地要塞的 Fabric 客户端 Mod。不用任何指令，扔几次末影之眼就能算出要塞坐标，并在屏幕上给出距离、方向指引线和路标。

- Minecraft **1.20.1–1.20.4 / 1.20.6 / 1.21.1–1.21.5 / 1.21.6–1.21.8** / Fabric
  - 以下载页的 4 个区间包为准，一个包覆盖整段（组内字节码一致）
  - **最低支持 1.20.1**：1.19 及更早版本的渲染 / 界面 API 差异过大，已放弃兼容
  - 1.21.9+ 暂未支持（渲染/按键 API 改动较大）
- 纯**客户端** Mod（单人、联机都能用，不需要装到服务端）
- 依赖 [Fabric API](https://modrinth.com/mod/fabric-api)

## 功能

- **自动三角定位**：扔出的末影之眼会被追踪，自动提取它飞行的方向，多条方向线求交点即要塞位置。
- **最佳下一投掷点**：只扔了第一次时，直接算出第二次该站的"最佳点位"（垂直于首次方位错开约 400 格），保证两条方位线夹角足够大、交点稳定，**两次锁定、不浪费眼睛/珍珠**；若你已经横向错开够远，会提示原地再扔即可。
- **HUD 信息**：左上角显示估计坐标、水平距离、有效样本数（含剔除）、置信度和已归档数量。
- **方向路标**：把目标位置投影到屏幕上，用手绘的极简路标标记（抗锯齿、加粗描边，支持菱形/圆环/十字三种样式）；跟随视角实时移动，超出视野贴到屏幕边缘，并显示距离。
- **异常样本过滤**：样本 ≥3 条时自动剔除残差过大的坏射线，避免一次手滑污染结果。
- **多要塞管理**：自动归档已找到的要塞并持久化；当你移动到新区域、眼睛开始指向另一个要塞时自动识别并保留旧记录。按 `I` 打开管理界面可查看/删除归档、调整所有设置。
- **自管理**：切换维度自动归档并重置；按 `K` 结束当前定位并收起 HUD（**归档保留**）。

## 使用方法

1. 装好 Fabric Loader + Fabric API，把 `eyelocator-*.jar` 放进 `mods/`。
2. 进入游戏，拿末影之眼。
3. **站着不动**扔第一次，记录第 1 条方位（此时还只有方向，没有交点）。HUD 会立即给出**下一最佳投掷点**（青色路标）。
4. 走到青色标记处（大约横向错开 400 格），再扔一次。两条不平行的方向线一相交，HUD 立刻显示要塞坐标。
5. 想要更准可以再换位置扔第 3、4 次，样本越多、残差越小，置信度越高；离群样本会被自动剔除。
6. 跟着绿色路标走过去即可；到达后眼睛会明显向下掉/原地打转。
7. 找完了按 `K` 结束当前定位、收起 HUD（结果仍保留在归档里）；按 `I` 打开管理界面（设置 + 归档）。

> 找新要塞时，已归档的旧要塞标记默认会自动隐藏（可在 GUI 或配置里关闭），避免干扰。

> 关键：第二次投掷要**横向错开**首次方位线，方向线越不平行，交点越准。推荐点已经替你算好了，跟着青色标记走即可；站原地重复扔同方向不会提升精度（会被自动去重）。

## 原理

一次末影之眼给出的是"从投掷点指向最近要塞的一条水平射线"。把每次投掷建模成直线：

- 直线过点 `P=(x,z)`，方向单位向量 `d=(dx,dz)`
- 其法线 `n=(-dz, dx)`，直线方程 `n·X = n·P`

对多条直线堆叠后用最小二乘解 2x2 法方程，得到所有射线的最佳交点，即要塞水平坐标。残差（各线到交点的垂直距离 RMS）和方向分散度共同决定置信度。

末影之眼只指向**最近**的要塞；要塞位置本身由游戏确定性生成，Mod 不修改任何游戏逻辑。

## 管理界面

按 `I`（可在“按键设置”里改）或输入 `/eyelocator gui` 打开。两个标签页：

- **设置**：只保留看得懂的几项——路标样式、路标大小、显示距离、显示旧要塞标记、找新要塞时隐藏旧标记、恢复默认（每项都有悬停说明）。
- **归档**：
  - 「归档当前坐标」：把当前算出的要塞坐标保存进归档，并弹窗命名；
  - 点击任意条目即可**重命名**；
  - 每条右侧 `×` 删除；「去重」合并同维度距离过近的条目；「清空归档」清空；
  - 支持分页。自定义名称会显示在对应路标上。

更细的参数（描边粗细、投掷基线、异常阈值、换要塞判定、颜色等）不放进界面，编辑 `config/eyelocator.json` 即可。

归档保存在 `config/eyelocator_strongholds.json`，重开游戏仍在。

## 配置

配置文件：`config/eyelocator.json`（首次启动自动生成）。可在游戏内用命令修改，改完自动保存：

| 命令 | 作用 |
|---|---|
| `/eyelocator` | 查看当前配置与状态 |
| `/eyelocator reload` | 重新读取配置文件 |
| `/eyelocator reset` | 结束当前定位（归档保留） |
| `/eyelocator clearall` | 清空当前定位与全部归档 |
| `/eyelocator gui` | 打开管理界面 |
| `/eyelocator style <diamond\|circle\|cross>` | 路标样式 |
| `/eyelocator scale <0.4~3.0>` | 路标缩放 |
| `/eyelocator baseline <50~2000>` | 最佳投掷点的横向基准距离 |
| `/eyelocator color stronghold <#RRGGBB>` | 要塞路标颜色 |
| `/eyelocator color advice <#RRGGBB>` | 投掷点路标颜色 |
| `/eyelocator archived <true\|false>` | 是否显示已归档要塞标记 |

配置文件主要字段：`markerStyle`、`markerScale`、`markerThickness`、`colorStronghold`、`colorAdvice`、`colorArchived`、`showDistance`、`showLabels`、`showPanel`、`baseline`、`outlierResidual`、`detectStrongholdChange`、`changeDistance`、`showArchived`、`hideArchivedWhileSearching`、`dedupeDistance`。

## 构建

需要 JDK 25+（Loom 1.19 要求；Mod 本身编译目标仍是 Java 21，游戏运行不受影响）。为避免污染全局缓存，建议把 Gradle 缓存放在项目内：

```bash
GRADLE_USER_HOME="$PWD/.gradle-home" ./gradlew build
```

产物：`build/libs/eyelocator-<version>.jar`（外加 `-sources.jar`）。

### 一键部署到游戏
构建并自动复制到本机 mods 目录（会先删掉旧的 `eyelocator-*.jar`，避免重复 mod id）：

```bash
GRADLE_USER_HOME="$PWD/.gradle-home" ./gradlew deployMod
```

目标目录优先级：`-PmodsDir=<路径>` > 环境变量 `MC_MODS_DIR` > 项目根目录 `local.properties` 里的 `modsDir` > 默认 `~/.minecraft/mods`。`local.properties` 已被 gitignore，适合放本机路径。

开发运行（需要图形环境）：`./gradlew runClient`。

### 构建全部支持版本
按跨版本区间生成 4 个 jar（输出到 `build/versions/`）：

```bash
./scripts/build-all.sh
```

| 包 | 覆盖版本 | Java |
|---|---|---|
| `+mc1.20.1-1.20.4` | 1.20.1 / 1.20.2 / 1.20.3 / 1.20.4 | 17 |
| `+mc1.20.6` | 1.20.6 | 21 |
| `+mc1.21.1-1.21.5` | 1.21.1 – 1.21.5 | 21 |
| `+mc1.21.6-1.21.8` | 1.21.6 – 1.21.8 | 21 |

区间内字节码完全一致，所以一个包可通用。

单个版本也可以手动指定，例如 1.20.1（注意 Java 17）：
```bash
./gradlew build -Pminecraft_version=1.20.1 -Pyarn_mappings=1.20.1+build.10 \
  -Pfabric_api_version=0.92.12+1.20.1 -Pjava_release=17 -Pminecraft_dependency=1.20.1
```

### 发布版本
先同步改 `gradle.properties` 里的 `mod_version`，然后打 tag 推送。GitHub Actions 会构建所有支持版本，并把各版本 jar 一起附到 Release：

```bash
git tag v0.7.0 && git push origin v0.7.0
```

## 目录结构

```
src/main/java/com/eyelocator/client/
├── EyeLocatorClient.java    # 客户端入口：事件、命令、归档/变更检测
├── EyeLocatorCommands.java  # /eyelocator 客户端命令
├── EyeLocatorScreen.java    # 管理界面（设置 / 归档）
├── EyeLocatorConfig.java    # JSON 配置读写
├── EyeTracker.java          # 追踪末影之眼实体，产出方位射线
├── StrongholdLocator.java   # 最小二乘求交 + 离群剔除，坐标/残差/置信度
├── StrongholdRegistry.java  # 多要塞归档（持久化）
├── HudRenderer.java         # 2D HUD：信息面板 + 可配置路标 + 归档标记
└── NextThrowAdvisor.java    # 首次投掷后计算最佳下一投掷点

scripts/build-all.sh         # 为 1.21.1–1.21.8 批量构建
```

## 已知限制

- 只按水平面求交，路标高度锚定相机高度（不采样目标地形）。
- 路标是屏幕空间绘制，会穿透地形显示（作为路标通常更有用）。
- 当前定位（未完成的射线）仅存在于会话中；已归档的要塞会持久化。

## 许可

MIT
