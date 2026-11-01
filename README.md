# 末影寻珠 (Ender Pearl Seeker)

[![Build](https://github.com/Emberend/ender-pearl-seeker/actions/workflows/build.yml/badge.svg)](https://github.com/Emberend/ender-pearl-seeker/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

用**末影之眼**（以及其它模组的同类定位道具）自动三角定位目标的客户端 Mod。不用任何指令，扔几次就能算出目标坐标，并在屏幕上给出距离、方向指引线和路标。

> ## 关于这个项目（请先读）
>
> 这是一个**个人用的小工具**，绝大部分代码是**和 AI 一起"氛围编程"（vibe coding）出来的**——我提需求、做取舍和验证，AI 负责写实现。它能用，但**不是**一个经过严格工程验证的正式项目。
>
> 需要你知道的不足：
>
> - **渲染从未实机验证**。开发机没有图形环境，HUD、路标、管理界面只做到"编译通过 + 逻辑自洽"，实际观感、不同分辨率与 GUI 缩放下的表现、和其它 HUD 模组的遮挡关系，都没有逐项检查过。
> - **版本兼容只等于"能编译"**。每个 Minecraft 版本都是编译验证，没有逐个进游戏跑过。**Forge 版（1.20.1）一次都没启动过**，第一次装上大概率需要调 bug。
> - **测试只覆盖算法层**。共享的几何/投票/持久化逻辑有 JUnit 单元测试（会随构建自动跑），但渲染、界面、事件注册、与具体 Minecraft 版本的交互依然没有自动化测试。
> - **代码质量参差**。Fabric 版、共享算法层、Forge 版是在不同时间段写的，风格不完全统一，有些逻辑存在重复。
> - **设计按个人习惯来**。配置项、命令命名、界面布局都是我自己用着顺手的样子，不一定适合所有人。
> - **其它模组的兼容靠推测**。已内置的几个 Goety 道具是读源码确认的，但没有实际联调；Cataclysm 的实体 id 至今没拿到。其它没见过的模组只能靠猜关键词或手动识别。
>
> 遇到问题欢迎提 Issue，但请把它当作"有空就看看"的个人项目，修复速度不保证。如果你需要一个生产级、久经考验的定位工具，这个项目大概不是最佳选择。

**Fabric 版**

- Minecraft **1.20.1–1.20.4 / 1.20.6 / 1.21.1–1.21.5 / 1.21.6–1.21.8**
  - 以下载页的 4 个区间包为准，一个包覆盖整段（组内字节码一致）
  - **最低支持 1.20.1**：1.19 及更早版本的渲染 / 界面 API 差异过大，已放弃兼容
  - 1.21.9+ 暂未支持（渲染/按键 API 改动较大）
- 需要 Fabric Loader 0.15+ 与 Fabric API
- **ModMenu 为可选前置**：装了以后可以直接在模组列表里点「设置」打开管理界面；没装不影响任何功能
- 依赖 [Fabric API](https://modrinth.com/mod/fabric-api)

**Forge 版**

- Minecraft **1.20.1**（Forge 47.x），下载文件名带 `-forge-1.20.1`
- 无需任何前置；配置与归档文件与 Fabric 版通用
- 管理界面按 `I` 打开（与 Fabric 版同一套布局），也可以用 `/eyelocator` 命令调参

两个版本都是纯**客户端** Mod，单人、联机都能用，不需要装到服务端。

## 功能

- **自动三角定位**：扔出的末影之眼会被追踪，自动提取它飞行的方向，多条方向线求交点即要塞位置。
- **兼容其它模组的同类道具**：理论上，"使用后飞向某个结构"的道具都能用同一套算法定位；支持自动识别、白名单与手动学习，不同道具指向不同结构时各自独立计算（见下）。实际只在少数几个已知道具上验证过。
- **最佳下一投掷点**：只扔了第一次时，直接算出第二次该站的"最佳点位"（横向错开约 400 格，并朝要塞方向偏转约 30°，降低跨到相邻要塞范围的概率），让两条方位线夹角足够大、交点稳定，**通常两次就能锁定**，省眼睛/珍珠；若你已经横向错开够远，会提示原地再扔即可。
- **HUD 信息**：左上角显示估计坐标、水平距离、有效样本数（含剔除）、置信度和已归档数量；文本与颜色按数值缓存，静止时每帧不重新拼接字符串。
- **方向路标**：把目标位置投影到屏幕上，用手绘的极简路标标记（抗锯齿、加粗描边，支持菱形/圆环/十字三种样式）；跟随视角实时移动，超出视野贴到屏幕边缘，并显示距离。
- **异常样本过滤**：样本 ≥3 条时自动剔除残差过大的坏射线，避免一次手滑污染结果。
- **多要塞管理**：自动归档已找到的要塞并持久化；当你移动到新区域、眼睛开始指向另一个要塞时自动识别并保留旧记录。按 `I` 打开管理界面可查看/删除归档、调整所有设置。
- **未完成定位也会存盘**：还没算出结果的方位射线写入 `config/eyelocator_state.json`，退出游戏重进可以接着投；换维度、换存档或换服务器时会自动丢弃不匹配的残留。
- **投掷失败会提示**：眼睛没飞出足够距离就消失（多半被方块挡住）时，动作栏会提示这次没有记录到方位，不会让你误以为已经记上了。
- **聊天播报**：锁定目标时把坐标发到聊天栏，方便回看或复制给朋友（可在设置里关闭）。
- **中英双语**：HUD、管理界面、命令提示、内置道具的通道名都走语言文件，跟随游戏语言切换。
- **自管理**：切换维度自动归档并重置；按 `K` 结束当前定位并收起 HUD（**归档保留**）。

## 使用方法

1. 装好对应加载器（Fabric Loader + Fabric API，或 Forge 47.x），把对应版本的 `eyelocator-*.jar` 放进 `mods/`。
2. 进入游戏，拿末影之眼。
3. **站着不动**扔第一次，记录第 1 条方位（此时还只有方向，没有交点）。HUD 会立即给出**下一最佳投掷点**（青色路标）。
4. 走到青色标记处（大约横向错开 400 格，并朝目标方向偏一点），再扔一次。两条不平行的方向线一相交，HUD 立刻显示要塞坐标。若两次方位其实指向了不同的要塞，HUD 会提示"方位对不上"并给出一个近处补投点，不会显示错误坐标。
5. 想要更准可以再换位置扔第 3、4 次，样本越多、残差越小，置信度越高；互相矛盾的方位会被自动剔除。
6. 跟着绿色路标走过去即可；到达后眼睛会明显向下掉/原地打转。
7. 找完了按 `K` 结束当前定位、收起 HUD（结果仍保留在归档里）；按 `I` 打开管理界面（设置 + 归档），两个加载器都支持。

> 找新要塞时，已归档的旧要塞标记默认会自动隐藏（可在 GUI 或配置里关闭），避免干扰。

> 关键：第二次投掷要**横向错开**首次方位线，方向线越不平行，交点越准。推荐点已经替你算好了，跟着青色标记走即可；站原地重复扔同方向不会提升精度（会被自动去重）。

## 兼容其它模组的同类道具

只要某个道具的行为和末影之眼一样——**使用后生成一个飞向某个结构的实体**——理论上就能用同一套三角定位（实际验证的道具很少，见下）。识别按以下优先级：

1. **内置兼容表**：原版末影之眼，以及下表这些已确认的模组道具，零配置直接可用：

   | 实体 id | 道具 | 来源 |
   |---|---|---|
   | `minecraft:eye_of_ender` | 末影之眼 | 原版 |
   | `goety:cryptic_eye` | 墓穴之眼 | Goety 2.5.x |
   | `goety:void_eye` | 虚空之眼 | Goety 2.5.x |
   | `goetyawaken:ominous_eye` | 不祥之眼 | Goety: Awaken 1.3.x |
   | `goetyawaken:prison_eye` | 监狱之眼 | Goety: Awaken 1.3.x |
   | `goetyawaken:mirage_eye` | 迷乱之眼 | Goety: Awaken 1.3.x |
   | `cataclysm:eye_of_dungeon` | 灾变地牢（8 种地牢之眼共用） | Cataclysm 3.x |

   这些 id 都是解包对应模组 jar、核对实体注册名 + `signalTo` 调用后确认的，不是猜的。已确认**不是**定位道具的（`goety:survey_eye` 隐形侦察眼、`goetyawaken:eye_of_overwatch_entity` 掉落物）被显式排除，避免被关键词误判。

   > Goety 系列目前只有 Forge 版，因此这几条实际生效于 **Forge 版**；Fabric 版的内置表同样保留它们，装了对应的 Fabric 移植后可直接用。
   >
   > Cataclysm 的 8 种地牢之眼（机械/巨兽/深渊/沙漠/烈焰/风暴/诅咒/虚空）**共用同一个实体 id**，但指向不同的地牢。客户端无法分辨用的是哪一种，只能靠"新射线明显不指向当前估计时自动归档重来"来切换；连续混用不同地牢之眼时，需要多扔几次。

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

**不同道具指向不同结构时，在实现上互不干扰。** 每个实体 id 是一个独立「通道」，有自己的射线集合和归档。HUD 展示最近使用的那种道具的结果；用 `/eyelocator channels` 查看全部，`/eyelocator channel <实体id>` 切换显示。

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

对多条直线两两求交，用"有多少条方位同意这个交点"投票，选票数最多的候选，再用同意它的射线做一次最小二乘精修。残差（各线到交点的垂直距离 RMS）和方向分散度共同决定置信度。

**只接受正向交点**：交点必须落在参与计算的每条射线的前方。如果两次投掷其实指向了不同的要塞，最小二乘仍可能解出一个落在反向延长线上的"交点"，这个校验会把这种结果直接作废，而不是把错误坐标当成答案。两条方位夹角小于 **1.5°** 时也不出结果——这种情况交点对方向误差极其敏感。这里刻意**不用"交点距离上限"**判断：模组可以改结构间距、生成范围甚至探测半径，距离远近说明不了对错；夹角只取决于玩家自己的两次投掷位置，与目标多远无关。若两组候选票数相同、彼此又相距很远，同样判定为"无法确定"，提示补投，不在两座要塞之间瞎猜。

末影之眼只指向**最近**的要塞；要塞位置本身由游戏确定性生成，Mod 不修改任何游戏逻辑。因此站在两座要塞"势力范围"的交界处时，两次投掷可能分别指向不同的要塞，这是末影之眼本身的机制，Mod 只能识别并提示重投，无法预先知道边界在哪。

## 管理界面

按 `I`（可在“按键设置”里改）、`/eyelocator gui`，或装了 ModMenu 时在模组列表里点「设置」打开。两个标签页：

- **设置**：两列布局，保留看得懂的几项——路标样式、大小、描边、显示距离、显示标签、显示面板、显示旧目标标记、找新目标时隐藏旧标记、自动识别道具、**聊天播报坐标**、恢复默认（每项都有悬停说明）。
- **归档**：
  - 「归档当前坐标」：把当前算出的坐标保存进归档，并弹窗命名；
  - 点击任意条目即可**重命名**；
  - 每条右侧 `×` 删除；「去重」合并**同一通道**（同一种目标类型）里同维度距离过近的条目——不同通道代表不同结构，坐标再近也各留一条；「清空归档」清空；
  - 支持分页。自定义名称会显示在对应路标上。

更细的参数（投掷基线、异常阈值、换目标判定、颜色等）不放进界面，编辑 `config/eyelocator.json` 即可。

归档保存在 `config/eyelocator_strongholds.json`，未完成的定位射线保存在 `config/eyelocator_state.json`，重开游戏都还在。

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

> 上面的命令两个版本都能用；Forge 版同样有 `I` 键管理界面和 `/eyelocator gui`，配置项与 Fabric 版完全一致。

配置文件主要字段：`markerStyle`、`markerScale`、`markerThickness`、`colorStronghold`、`colorAdvice`、`colorArchived`、`showDistance`、`showLabels`、`showPanel`、`notifyChat`、`baseline`、`outlierResidual`、`detectStrongholdChange`、`changeDistance`、`showArchived`、`hideArchivedWhileSearching`、`dedupeDistance`；兼容相关：`autoDetect`、`autoDetectKeywords`、`trackedEntities`、`channelNames`。

数据文件：

- `config/eyelocator_strongholds.json`：已归档目标
- `config/eyelocator_state.json`：未完成定位的方位射线（没有射线时自动删除）

### 语言 / 翻译

HUD、界面、命令文案都使用翻译键，语言文件位于：

```
src/main/resources/assets/eyelocator/lang/{zh_cn,en_us}.json
forge/src/main/resources/assets/eyelocator/lang/{zh_cn,en_us}.json
```

四份文件的键必须一致。想加其它语言时，复制一份 `en_us.json` 改名为对应语言代码并翻译即可（游戏会自动匹配）。

## 构建

### Fabric 版

需要 JDK 25+（Loom 1.19 要求；Mod 本身编译目标仍是 Java 21，游戏运行不受影响）。为避免污染全局缓存，建议把 Gradle 缓存放在项目内：

```bash
GRADLE_USER_HOME="$PWD/.gradle-home" ./gradlew build
```

产物：`build/libs/eyelocator-<version>.jar`（外加 `-sources.jar`）。

### 单元测试

共享算法层（求交投票、最佳投掷点、状态快照）有 JUnit 5 测试，两个加载器的 `build` 都会自动执行，CI 同样会跑：

```bash
GRADLE_USER_HOME="$PWD/.gradle-home" ./gradlew test

cd forge
JAVA_HOME=/usr/lib/jvm/java-17-openjdk GRADLE_USER_HOME="$PWD/../.gradle-home" ./gradlew test
```

覆盖范围仅限纯算法，渲染/界面/事件注册不在其中。

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
├── StrongholdLocator.java   # 射线对投票求交 + 离群剔除，坐标/残差/置信度
├── NextThrowAdvisor.java    # 首次投掷后计算最佳下一投掷点
├── LocatorChannel.java      # 一个定位来源（实体 id）及其独立射线集合
├── ChannelManager.java      # 管理所有通道，记录最近使用的通道
├── LocatorState.java        # 未完成定位的磁盘快照（射线 + 维度）
├── Fmt.java                 # 跨加载器统一的坐标格式化
└── BuiltInCompat.java       # 已知模组定位道具的内置 id -> 显示名映射

src/main/java/com/eyelocator/client/        # Fabric 适配层
├── EyeLocatorClient.java    # 客户端入口：事件、命令、归档/变更检测、状态持久化
├── EyeLocatorCommands.java  # /eyelocator 客户端命令
├── EyeLocatorScreen.java    # 管理界面（设置 / 归档）
├── EyeLocatorModMenu.java   # 可选的 ModMenu 设置入口
├── EyeLocatorConfig.java    # JSON 配置读写
├── EyeTracker.java          # 追踪定位投射物（任意实体），产出方位射线
├── EntityClassifier.java    # 识别哪些实体是"会飞向结构的道具"
├── StrongholdRegistry.java  # 按通道隔离的多目标归档（持久化）
├── TextPromptScreen.java    # 命名/重命名输入框
└── HudRenderer.java         # 2D HUD：信息面板 + 可配置路标 + 归档标记

src/test/java/com/eyelocator/core/          # 共享算法层单元测试（两个加载器都跑）
├── StrongholdLocatorTest.java
├── NextThrowAdvisorTest.java
└── LocatorStateTest.java

forge/src/main/java/com/eyelocator/forge/    # Forge 1.20.1 适配层
├── EyeLocatorForge.java     # 入口（只做 DistExecutor 延迟加载）
├── ClientSetup.java         # 客户端事件、按键、归档/变更检测
├── ForgeCommands.java       # /eyelocator 客户端命令
├── ForgeConfig.java         # 配置（与 Fabric 版共用同一份 JSON）
├── ForgeTracker.java        # 实体飞行方向追踪
├── ForgeClassifier.java     # 实体识别
├── ForgeRegistry.java       # 归档（与 Fabric 版格式相同）
├── ForgeEyeLocatorScreen.java  # 管理界面（设置 / 归档）
├── ForgeTextPromptScreen.java  # 命名/重命名输入框
└── ForgeHud.java            # HUD 渲染

common/src/main/java/                      # 纯算法层，被两个加载器直接引用
src/test/java/com/eyelocator/core/          # 算法层单元测试
scripts/build-all.sh         # 为所有支持版本批量构建
scripts/clean-caches.sh      # 清理过期的 Gradle / Loom 缓存
docs/modrinth.md             # Modrinth 上架描述
```

## 已知限制

**算法与数据**

- 只按水平面求交，路标高度锚定相机高度（不采样目标地形），所以路标只保证"方向对"，不保证"看得见"。
- 两条射线只是勉强能解出交点，**结果可能偏差几百格**；想要准还是要多扔几次。HUD 上的置信度只是残差的粗略换算，不是严格的误差范围。
- 末影之眼只飞向"离你最近"的要塞。当你正好站在两座要塞范围的交界处时，两次投掷可能分别指向不同的要塞；此时反向延长线上的"假交点"会被自动拒绝，HUD 会提示方位矛盾并让你侧移补投，但 Mod 无法提前知道边界在哪，只能事后识别。
- 只有 2 条方位、且两条都指向前方却能交出一个错误点时，几何上无法分辨（那和"两投同一个要塞"在数学上是同一组约束）。多扔一两次让票数分开即可，界面上不会强行给出一个高置信度答案。
- 投掷方向被地形挡住、眼睛半路消失、或者飞得不够远，这一条射线就不会被记录，只在动作栏提示一次（同一时间连续失败会合并提示，避免刷屏）。
- 未完成的定位按「存档/服务器 + 维度」区分：单人用存档名、多人用服务器地址。**同名存档**或通过不同地址连同一台服务器时仍可能互相串数据，此时手动 `/eyelocator reset` 清掉即可。
- 换维度会自动归档并清空当前射线，但**不会**帮你保留"上次在哪个维度找的"这类上下文。

**兼容性**

- 依赖"道具会生成客户端可见的飞行实体"这一前提。直接给坐标、只放粒子、只往聊天栏发消息的道具，拿不到飞行方向，无法支持。
- 自动识别只看实体 id 关键词，命名特殊的道具识别不到，需要手动 `/eyelocator track`。
- 同一个实体 id 但指向不同结构的情况无法自动区分——这得看那个模组自己怎么实现。
- 已内置的 Goety 道具 id 是读源码得来的，**没有实际联调**；源码版本和你装的版本不一致时可能对不上。

**版本与平台**

- 1.19 及更早、1.21.9 及更新都没有支持，也不打算在近期补。
- Forge 版只有 1.20.1。管理界面与 Fabric 版分开实现，功能对齐但布局细节可能略有差异。
- Forge 版的 HUD、管理界面、按键只做过「编译通过 + 服务端能启动」的验证，客户端表现需要你实际跑一遍才算数。

**界面**

- 路标是屏幕空间绘制，会穿透地形显示（作为路标通常更有用，但如果你想要"被挡住就看不见"的效果，这个做不到）。
- 中文长名字、超长坐标在面板上可能超出边框，没有做自动换行或截断。
- 没有适配 GUI 缩放、超高分辨率屏幕或其它 HUD 模组的布局冲突。

## 许可

MIT
