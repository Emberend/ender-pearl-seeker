# v0.11.1 — 兼容表可以自己换了

这一版只有一个主线：把「哪些道具算定位道具」从代码里挪出来，变成一份可以整体替换的文件。顺便记录了 Forge 1.20.1 的实机验证结果。

## 新功能

- **兼容表外置**：内置兼容表（原版末影之眼 + Goety / Goety: Awaken / Cataclysm 已知道具 + 需要排除的实体）现在从 `config/eyelocator_compat.json` 读取，**整份替换文件即可生效**，不用改代码，也不用在原文件里逐条改内置条目。
  - 文件不存在时自动生成一份包含全部内置条目的模板。
  - 文件存在时默认**整份替换**内置表；把 `inheritBuiltIn` 设为 `true` 则改为叠加，同名条目以文件为准。
  - `channels` 写实体 id → `{name, key}`，`ignored` 写明确要排除的实体 id。
  - 解析失败时保留内置默认表，**不覆盖你的文件**，日志里给出原因，修好后 `/eyelocator reload` 即可。
  - 启动时读一次，`/eyelocator reload` 可以重新加载，两个加载器行为一致。

## 改进

- **`/eyelocator reload`**：现在会同时重读配置文件和兼容表，提示文案改为「配置与兼容表已重新读取」。
- **文档**：README 与 Modrinth 描述里补了「替换兼容表」小节，写清文件位置、字段含义、读取优先级、失败回退与生效时机；识别优先级一节也链到了这里。

## 验证状态

- **Forge 1.20.1 已实机跑过一轮**：2026-11-01 在装有 Goety / Goety: Awaken / Cataclysm 的实例上测试，核心流程可用。这是本项目第一次进游戏实测，README 里「Forge 版一次都没启动过」的说法已经删掉。
- Fabric 各版本（1.20.1 / 1.20.6 / 1.21.x）仍然只做到编译验证；不同分辨率、GUI 缩放与其它 HUD 模组的遮挡关系仍未逐项检查。

## 工程

- 新增 `BuiltInCompatTest`（5 例）：模板生成、整份替换、叠加内置、损坏文件回退、空条目过滤。测试总数 35 例，Fabric 与 Forge 的 `build` 都会执行。
- 修复 CI 的 Release 步骤：发布任务之前没有 checkout，读不到 `docs/release-<版本>.md`，导致 Release 说明退化成自动生成的 changelog。

## 升级提示

- 配置项没有变化，旧的 `config/eyelocator.json` 可直接使用。
- 升级后首次启动会生成 `config/eyelocator_compat.json`。**如果你之前手动改过内置兼容表相关代码，请把差异搬到这个文件里**；如果没动过，用生成的模板即可，行为与 0.11.0 完全一致。
- 整份替换兼容表时，内置的排除项（`goety:survey_eye`、`goetyawaken:eye_of_overwatch_entity`）也会一起失效，需要的话请写进 `ignored`。
- 这依然是个个人项目：单元测试只覆盖纯算法层，渲染与多数版本组合没有实机验证。