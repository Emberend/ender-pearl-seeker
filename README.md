# 末影寻珠 (Ender Pearl Seeker)

[![Build](https://github.com/Emberend/ender-pearl-seeker/actions/workflows/build.yml/badge.svg)](https://github.com/Emberend/ender-pearl-seeker/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

用**末影之眼**（以及其它模组的同类定位道具）自动三角定位目标的客户端 Mod。不用任何指令，扔几次就能算出目标坐标，并在屏幕上给出距离、方向指引线和路标。

**Fabric 版**

- Minecraft **1.20.1–1.20.4 / 1.20.6 / 1.21.1–1.21.5 / 1.21.6–1.21.8**
  - 以下载页的 4 个区间包为准，一个包覆盖整段（组内字节码一致）
  - **最低支持 1.20.1**：1.19 及更早版本的渲染 / 界面 API 差异过大，已放弃兼容
  - 1.21.9+ 暂未支持（渲染/按键 API 改动较大）
- 依赖 [Fabric API](https://modrinth.com/mod/fabric-api)

**Forge 版**

- Minecraft **1.20.1**（Forge 47.x），下载文件名带 `-forge-1.20.1`
- 无需任何前置；配置与归档文件与 Fabric 版通用
- 管理界面暂未移植，所有设置用 `/eyelocator` 命令修改（见下）

两个版本都是纯**客户端** Mod，单人、联机都能用，不需要装到服务端。

## 功能

- **自动三角定位**：扔出的末影之眼会被追踪，自动提取它飞行的方向，多条方向线求交点即要塞位置。
- **兼容其它模组的同类道具**：凡是"使用后飞向某个结构"的道具都能用同一套算法定位；支持自动识别、白名单与手动学习，不同道具指向不同结构时各自独立计算（见下）。
- **最佳下一投掷点**：只扔了第一次时，直接算出第二次该站的"最佳点位"（垂直于首次方位错开约 400 格），保证两条方位线夹角足够大、交点稳定，**两次锁定、不浪费眼睛/珍珠**；若你已经横向错开够远，会提示原地再扔即可。
- **HUD 信息**：左上角显示估计坐标、水平距离、有效样本数（含剔除）、置信度和已归档数量。
- **方向路标**：把目标位置投影到屏幕上，用手绘的极简路标标记（抗锯齿、加粗描边，支持菱形/圆环/十字三种样式）；跟随视角实时移动，超出视野贴到屏幕边缘，并显示距离。
- **异常样本过滤**：样本 ≥3 条时自动剔除残差过大的坏射线，避免一次手滑污染结果。
- **多要塞管理**：自动归档已找到的要塞并持久化；当你移动到新区域、眼睛开始指向另一个要塞时自动识别并保留旧记录。按 `I` 打开管理界面可查看/删除归档、调整所有设置。
- **自管理**：切换维度自动归档并重置；按 `K` 结束当前定位并收起 HUD（**归档保留**）。

## 使用方法

1. 装好对应加载器（Fabric Loader + Fabric API，或 Forge 47.x），把对应版本的 `eyelocator-*.jar` 放进 `mods/`。
2. 进入游戏，拿末影之眼。
3. **站着不动**扔第一次，记录第 1 条方位（此时还只有方向，没有交点）。HUD 会立即给出**下一最佳投掷点**（青色路标）。
4. 走到青色标记处（大约横向错开 400 格），再扔一次。两条不平行的方向线一相交，HUD 立刻显示要塞坐标。
5. 想要更准可以再换位置扔第 3、4 次，样本越多、残差越小，置信度越高；离群样本会被自动剔除。
6. 跟着绿色路标走过去即可；到达后眼睛会明显向下掉/原地打转。
7. 找完了按 `K` 结束当前定位、收起 HUD（结果仍保留在归档里）；Fabric 版按 `I` 打开管理界面（设置 + 归档）。

> 找新要塞时，已归档的旧要塞标记默认会自动隐藏（可在 GUI 或配置里关闭），避免干扰。

> 关键：第二次投掷要**横向错开**首次方位线，方向线越不平行，交点越准。推荐点已经替你算好了，跟着青色标记走即可；站原地重复扔同方向不会提升精度（会被自动去重）。

## 兼容其它模组的同类道具

只要某个道具的行为和末影之眼一样——**使用后生成一个飞向某个结构的实体**——就能用同一套三角定位。识别按以下优先级：

1. **内置兼容表**：原版末影之眼，以及下表这些已确认的模组道具，零配置直接可用：

   | 实体 id | 道具 | 指向 |
   |---|---|---|
   | `minecraft:eye_of_ender` | 末影之眼 | 末地要塞 |
   | `goety:cryptic_eye` | 隐秘之眼 | Goety 的结构 |
   | `goety:void_eye` | 虚空之眼 | Goety 的结构 |
   | `goety:survey_eye` | 探知之眼 | Goety 的结构 |
   | `goetyawaken:ominous_eye` | 不祥之眼 | 不祥城堡 |
   | `goetyawaken:prison_eye` | 囚禁之眼 | 对应结构 |
   | `goetyawaken:mirage_eye` | 幻影之眼 | 对应结构 |
   | `goetyawaken:eye_of_overwatch_entity` | 守望之眼 | 对应结构 |

   > Goety 系列目前只有 Forge 版，因此这些条目实际生效于 **Forge 版**；Fabric 版的内置表同样保留它们，装了对应的 Fabric 移植后可直接用。

2. **自动启发式**：实体 id 里含 `eye` / `seeker` / `locator` / `finder` / `stronghold` 之一时自动追踪（关键词可配置，也可整体关闭；只对非生物实体生效）。
3. **白名单**：在 `config/eyelocator.json` 的 `trackedEntities` 里写实体 id，支持 `*` 通配（白名单命中时不限制实体是否生物，可兼容用 `FlyingMob` 实现的定位道具）：

   ```json
   "trackedEntities": ["somemod:ancient_eye", "othermod:*finder*"]
   ```

4. **手动学习（兜底）**：遇到不认识的模组，先用一次再识别：
   ```
   /eyelocator track next      # 15 秒内使用那个道具
   ```
   第一个从你身边飞出的实体（非生物）会被记住，并自动写入 `trackedEntities`，之后永久生效。

**不同道具指向不同结构时互不干扰。** 每个实体 id 是一个独立「通道」，有自己的射线集合和归档。HUD 展示最近使用的那种道具的结果；用 `/eyelocator channels` 查看全部，`/eyelocator channel <实体id>` 切换显示。

通道显示名可以自定义：

```json
"channelNames": {
  "somemod:ancient_eye": "远古城市",
  "othermod:*finder*": "海底神殿"
}
```

> **不支持的形态**：有些模组不生成实体，而是直接给坐标、放粒子或往聊天栏发消息。这类拿不到"飞行方向"，无法用三角定位，不在兼容范围内。

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
| `/eyelocator archived <true\|false>` | 是否显示已归档目标标记 |
| `/eyelocator track next` | 15 秒内使用一次道具，自动识别其实体并加入白名单 |
| `/eyelocator track stop` | 取消识别模式 |
| `/eyelocator track <实体id>` | 手动把实体加入白名单 |
| `/eyelocator channels` | 列出所有定位通道 |
| `/eyelocator channel <实体id>` | 切换 HUD 显示的通道 |

> `/eyelocator gui`（管理界面）只有 Fabric 版提供；Forge 版用上面的命令调整全部设置，配置项与 Fabric 版完全一致。

配置文件主要字段：`markerStyle`、`markerScale`、`markerThickness`、`colorStronghold`、`colorAdvice`、`colorArchived`、`showDistance`、`showLabels`、`showPanel`、`baseline`、`outlierResidual`、`detectStrongholdChange`、`changeDistance`、`showArchived`、`hideArchivedWhileSearching`、`dedupeDistance`；兼容相关：`autoDetect`、`autoDetectKeywords`、`trackedEntities`、`channelNames`。

## 构建

### Fabric 版

需要 JDK 25+（Loom 1.19 要求；Mod 本身编译目标仍是 Java 21，游戏运行不受影响）。为避免污染全局缓存，建议把 Gradle 缓存放在项目内：

```bash
GRADLE_USER_HOME="$PWD/.gradle-home" ./gradlew build
```

产物：`build/libs/eyelocator-<version>.jar`（外加 `-sources.jar`）。

### 清理构建缓存

Loom 会为每个编译过的 Minecraft 版本各存一份 remap 产物（每个 120~150 MB），时间久了项目目录会膨胀到数 GB。源码本身只有 100 KB 左右，其余全是缓存。

```bash
./scripts/clean-caches.sh
```

只保留 `build-all.sh` 实际使用的 4 个编译版本（1.20.1 / 1.20.6 / 1.21.1 / 1.21.6），其余版本、`remapped_mods` 和 `build/` 中间产物会被删除（`build/versions` 里的成品 jar 保留）。下次构建未保留的版本时会重新下载，属正常现象。

### 一键部署到游戏
构建并自动复制到本机 mods 目录（会先删掉旧的 `eyelocator-*.jar`，避免重复 mod id）：

```bash
GRADLE_USER_HOME="$PWD/.gradle-home" ./gradlew deployMod
```

目标目录优先级：`-PmodsDir=<路径>` > 环境变量 `MC_MODS_DIR` > 项目根目录 `local.properties` 里的 `modsDir` > 默认 `~/.minecraft/mods`。`local.properties` 已被 gitignore，适合放本机路径。

开发运行（需要图形环境）：`./gradlew runClient`。

### Forge 版

`forge/` 是**独立的 Gradle 工程**（ForgeGradle 不兼容 Gradle 9，所以用自带的 Gradle 8.8 wrapper），需要 **JDK 17**：

```bash
cd forge
JAVA_HOME=/usr/lib/jvm/java-17-openjdk GRADLE_USER_HOME="$PWD/../.gradle-home" ./gradlew build
```

产物：`forge/build/libs/eyelocator-forge-<version>.jar`。

`common/src/main/java/` 是与加载器无关的纯算法代码（三角定位、最佳投掷点、通道管理），两个版本直接共用同一份源码，无需复制。

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
先把 `gradle.properties` 和 `forge/gradle.properties` 里的 `mod_version` 改成同一版本号，然后打 tag 推送。GitHub Actions 会构建 4 个 Fabric 包 + 1 个 Forge 包，并把它们一起附到 Release：

```bash
git tag vX.Y.Z && git push origin vX.Y.Z
```

## 目录结构

```
common/src/main/java/com/eyelocator/core/   # 两个加载器共用
├── StrongholdLocator.java   # 最小二乘求交 + 离群剔除，坐标/残差/置信度
├── NextThrowAdvisor.java    # 首次投掷后计算最佳下一投掷点
├── LocatorChannel.java      # 一个定位来源（实体 id）及其独立射线集合
├── ChannelManager.java      # 管理所有通道，记录最近使用的通道
└── BuiltInCompat.java       # 已知模组定位道具的内置 id -> 显示名映射

src/main/java/com/eyelocator/client/        # Fabric 适配层
├── EyeLocatorClient.java    # 客户端入口：事件、命令、归档/变更检测
├── EyeLocatorCommands.java  # /eyelocator 客户端命令
├── EyeLocatorScreen.java    # 管理界面（设置 / 归档）
├── EyeLocatorConfig.java    # JSON 配置读写
├── EyeTracker.java          # 追踪定位投射物（任意实体），产出方位射线
├── EntityClassifier.java    # 识别哪些实体是"会飞向结构的道具"
├── StrongholdRegistry.java  # 按通道隔离的多目标归档（持久化）
└── HudRenderer.java         # 2D HUD：信息面板 + 可配置路标 + 归档标记

forge/src/main/java/com/eyelocator/forge/    # Forge 1.20.1 适配层
├── EyeLocatorForge.java     # 入口、事件、归档/变更检测
├── ForgeCommands.java       # /eyelocator 客户端命令
├── ForgeConfig.java         # 配置（与 Fabric 版共用同一份 JSON）
├── ForgeTracker.java        # 实体飞行方向追踪
├── ForgeClassifier.java     # 实体识别
├── ForgeRegistry.java       # 归档（与 Fabric 版格式相同）
└── ForgeHud.java            # HUD 渲染

scripts/build-all.sh         # 为所有支持版本批量构建
scripts/clean-caches.sh      # 清理过期的 Gradle / Loom 缓存
```

## 已知限制

- 只按水平面求交，路标高度锚定相机高度（不采样目标地形）。
- 兼容性依赖"道具会生成飞行实体"这一前提；直接给坐标或只发消息的道具无法支持。
- 自动识别按实体 id 关键词判断，个别命名特殊的道具需要手动 `/eyelocator track` 加白名单。
- 路标是屏幕空间绘制，会穿透地形显示（作为路标通常更有用）。
- 当前定位（未完成的射线）仅存在于会话中；已归档的目标会持久化。
- Forge 版目前只有 1.20.1，且没有管理界面（用命令调参）；Fabric 版的管理界面不会同步过去。

## 许可

MIT
