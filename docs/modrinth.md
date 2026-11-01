# 末影寻珠 (Ender Pearl Seeker)

> 用末影之眼（以及其它模组的同类定位道具）自动三角定位目标。纯客户端，不用指令、不改服务端，单人/联机都能用。

## 这是什么

扔几次末影之眼，模组会记录每次的飞行方向，把这些方向线求交，算出目标坐标，并在屏幕上给出：

- 左上角信息面板：坐标、距离、有效样本数、置信度、归档数量
- 屏幕空间路标：目标在视野外时贴边显示，视野内画在对应位置，带距离和抗锯齿描边
- 第一次投掷后的**最佳下一投掷点**：横向错开约 400 格并朝目标偏 30°，让两条方向线夹角足够大，通常两次就能锁定

不只是末影之眼。任何「使用后生成一个飞向某个结构的实体」的道具理论上都能用，已内置若干模组道具的识别，也支持关键词自动识别、白名单和手动学习。

## 关于这个项目（请先读）

这是一个**个人用的小工具**，绝大部分代码是和 AI 一起「氛围编程」（vibe coding）出来的——我提需求、做取舍和验证，AI 负责写实现。它能用，但**不是**经过严格工程验证的正式项目。

需要你知道的不足：

- **渲染从未实机验证**。开发机没有图形环境，HUD、路标、管理界面只做到「编译通过 + 逻辑自洽」，实际观感、不同分辨率与 GUI 缩放下的表现、和其它 HUD 模组的遮挡关系，都没有逐项检查过。
- **版本兼容只等于「能编译」**。每个 Minecraft 版本都是编译验证，没有逐个进游戏跑过。**Forge 版一次都没启动过**，第一次装上大概率需要调 bug。
- **测试只覆盖算法层**。共享的几何/投票/持久化逻辑有 JUnit 单元测试，但渲染、界面、事件注册、与具体 Minecraft 版本的交互依然没有自动化测试。
- **代码质量参差**。Fabric 版、共享算法层、Forge 版是在不同时间段写的，风格不完全统一，有些逻辑存在重复。
- **设计按个人习惯来**。配置项、命令命名、界面布局都是我自己用着顺手的样子，不一定适合所有人。
- **其它模组的兼容靠推测**。已内置的几个 Goety 道具是读源码确认的，但没有实际联调；Cataclysm 的实体 id 至今没拿到。其它没见过的模组只能靠猜关键词或手动识别。

遇到问题欢迎提 Issue，但请把它当作「有空就看看」的个人项目，修复速度不保证。如果你需要一个生产级、久经考验的定位工具，这个项目大概不是最佳选择。

## 支持版本

**Fabric**

- Minecraft 1.20.1–1.20.4 / 1.20.6 / 1.21.1–1.21.5 / 1.21.6–1.21.8
- 需要 Fabric Loader 0.15+ 和 Fabric API
- 最低支持 1.20.1

**Forge**

- Minecraft 1.20.1（Forge 47.x）
- 不需要额外前置

两个版本都是纯客户端 Mod，**不需要**装到服务端。配置与归档文件格式通用，可在两个加载器之间迁移。

**ModMenu（可选）**：Fabric 版装了 ModMenu 后，可以在模组列表里直接点「设置」打开管理界面；没装也不影响使用。

## 使用方法

1. 装好对应加载器，把对应版本的 jar 放进 `mods/`。
2. 进入游戏，拿末影之眼。
3. **站着不动**扔第一次，记录第 1 条方位。HUD 会立即给出下一最佳投掷点（青色路标）。
4. 走到青色标记处（约横向错开 400 格，并朝目标方向偏一点），再扔一次。两条不平行的方向线一相交，HUD 立刻显示坐标。
5. 想更准可以再换位置扔第 3、4 次；互相矛盾的方位会被自动剔除。
6. 跟着绿色路标走过去即可。找完了按 `K` 结束当前定位（结果仍保留在归档里），按 `I` 打开管理界面。

> 第二次投掷要**横向错开**首次方位线。站原地重复扔同方向不会提升精度，会被自动去重。

## 功能

- **自动三角定位**：追踪末影之眼及同类道具的飞行方向，多条方向线投票求交，正向交点才接受。
- **最佳下一投掷点**：第一次投掷后直接算出第二次该站的位置，减少浪费。
- **异常样本过滤**：样本 ≥3 条时自动剔除残差过大的坏射线。
- **方位矛盾识别**：两次投掷指向不同目标时提示补投，而不是给出错误坐标。
- **多目标/多通道**：不同道具指向不同结构时各自独立计算；切换维度自动归档并重置。
- **归档与命名**：自动归档已找到的目标并持久化，可命名、重命名、删除、去重、清空。
- **未完成定位持久化**：还没算出结果的方位射线也会存盘，退出游戏重进可以接着投。
- **聊天播报**：锁定目标时把坐标发到聊天栏（可在设置里关闭）。
- **中英双语**：HUD、界面、命令都走语言文件。
- **可配置**：路标样式（菱形/圆环/十字）、大小、描边、颜色、距离显示、归档显示等。

## 命令

| 命令 | 作用 |
|---|---|
| `/eyelocator` | 查看当前配置与状态 |
| `/eyelocator gui` | 打开管理界面 |
| `/eyelocator reload` | 重新读取配置文件 |
| `/eyelocator reset` | 结束当前定位（归档保留） |
| `/eyelocator clearall` | 清空当前定位与全部归档 |
| `/eyelocator style <diamond\|circle\|cross>` | 路标样式 |
| `/eyelocator scale <0.4~3.0>` | 路标缩放 |
| `/eyelocator baseline <50~2000>` | 最佳投掷点的横向基准距离 |
| `/eyelocator color <stronghold\|advice> <#RRGGBB>` | 路标颜色 |
| `/eyelocator archived <true\|false>` | 是否显示已归档目标标记 |
| `/eyelocator track next` | 15 秒内使用一次道具，自动识别并加入白名单 |
| `/eyelocator track stop` | 取消识别模式 |
| `/eyelocator track <实体id>` | 手动把实体加入白名单 |
| `/eyelocator channels` | 列出所有定位通道 |
| `/eyelocator channel <实体id>` | 切换 HUD 显示的通道 |

## 配置文件

`config/eyelocator.json` 首次启动自动生成。常用字段：

`markerStyle`、`markerScale`、`markerThickness`、`colorStronghold`、`colorAdvice`、`colorArchived`、`showDistance`、`showLabels`、`showPanel`、`notifyChat`、`baseline`、`outlierResidual`、`detectStrongholdChange`、`changeDistance`、`showArchived`、`hideArchivedWhileSearching`、`dedupeDistance`；兼容相关：`autoDetect`、`autoDetectKeywords`、`trackedEntities`、`channelNames`。

数据文件：

- `config/eyelocator_strongholds.json`：已归档目标
- `config/eyelocator_state.json`：未完成定位的射线（没有射线时自动删除）

## 兼容其它模组的道具

识别优先级：

1. **内置兼容表**：原版末影之眼，以及 Goety / Goety: Awaken / Cataclysm 的若干已知道具。
2. **自动启发式**：实体 id 含 `eye` / `seeker` / `locator` / `finder` / `stronghold` 之一时自动追踪（关键词可配置，可整体关闭）。
3. **白名单**：在 `trackedEntities` 里写实体 id，支持 `*` 通配。
4. **手动学习**：`/eyelocator track next` 后用一次道具，自动记住。

不同道具指向不同结构时在实现上互不干扰：每个实体 id 是一个独立「通道」，有自己的射线集合和归档。

## 已知限制

- 只按水平面求交，路标高度锚定相机高度，只保证「方向对」，不保证「看得见」。
- 只有 2 条方位、且两条都指向前方却能交出一个错误点时，几何上无法分辨；多扔一两次让票数分开即可。
- 投掷方向被地形挡住、眼睛半路消失、或飞得不够远，这条射线不会被记录，界面上也没有提示。
- 依赖「道具会生成客户端可见的飞行实体」这一前提。直接给坐标、只放粒子、只往聊天栏发消息的道具无法支持。
- 自动识别只看实体 id 关键词，命名特殊的道具识别不到，需要手动 `/eyelocator track`。
- 1.19 及更早、1.21.9 及更新都没有支持，也不打算在近期补。
- 没有适配 GUI 缩放、超高分辨率屏幕或其它 HUD 模组的布局冲突。

## 构建

需要 JDK 25+（Loom 1.19 要求；Mod 本身编译目标仍是 Java 21，游戏运行不受影响）。

```bash
GRADLE_USER_HOME="$PWD/.gradle-home" ./gradlew build        # Fabric，单版本
./scripts/build-all.sh                                      # Fabric，全部支持区间
cd forge && JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
  GRADLE_USER_HOME="$PWD/../.gradle-home" ./gradlew build   # Forge 1.20.1
```

单元测试：

```bash
GRADLE_USER_HOME="$PWD/.gradle-home" ./gradlew test
```

## 许可

MIT