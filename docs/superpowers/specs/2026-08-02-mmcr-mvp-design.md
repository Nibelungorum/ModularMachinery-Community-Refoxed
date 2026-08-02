# MMCR MVP — 设计规范（首期）

> 把 MMCE 1.12.2（`reference/mmce`）移植到 NeoForge 26.1.2 的**最小可行版本**设计规范。
> 本文档是 4 篇配套文档的「执行摘要」——细节在各篇里。

| 配套文档 | 内容 |
|---|---|
| `docs/MMCE.md`（37 章） | MMCE 1.12.2 功能全景索引（参考，不动） |
| `docs/scope.md` | 首期 In/Out 清单 + 未来阶段 TODO |
| `docs/api-mapping.md` | MMCE 1.12.2 → NeoForge 26.1.2 的 API 对照表 |
| `docs/kubejs-integration.md` | KubeJS 可选桥接层设计 |
| `docs/architecture.md` | 包结构 + 类级翻译路线图 |

## 1. 目标（一句话）

在 NeoForge 26.1.2 上跑通：

> 一个 3×3×3 外壳 + 1 个控制器 + 1 个 item 输入仓 + 1 个 item 输出仓 + 1 个 energy 输入仓的最小机器，能用 KubeJS 脚本写一条「2 铁锭 → 1 铁板，能耗 80 FE/tick」配方，每秒处理一次，能耗/物品流转正确。

## 2. 设计约束（决策清单）

| # | 决策 | 理由 |
|---|---|---|
| D1 | **第三方 mod 联动全部 OUT** | 1.12.2 → 26.1.2 各 mod API 大改；首期零深度依赖 |
| D2 | **JSON 机器 / 配方定义 OUT** | KubeJS 脚本覆盖；MMCE 玩家用 JSON 的体验债用 KubeJS 解决 |
| D3 | **CraftTweaker 完全替换为 KubeJS** | 已在 build.gradle；ZenScript → KubeJS |
| D4 | **KubeJS 是可选绑定** | 缺 KubeJS 时 MMCR 仍能跑（仅无脚本入口） |
| D5 | **GeckoLib / Lumenized OUT** | NeoForge 26.1.2 原生渲染已够；不引入第三方渲染库 |
| D6 | **GSON 双阶段反序列化 OUT** | 直接用 NeoForge `Codec` / `MapCodec` |
| D7 | **Mixin 全部 OUT** | 首期零 Mixin，零 AccessTransformer |
| D8 | **并行 / 多线程 / 工厂 / 智能接口 / 升级 OUT** | 单 tick 单 RecipeSearchTask；所有机器 `maxParallelism = 1` |
| D9 | **算力系统 OUT** | 用户口径；智能接口 / 数值化修饰全 OUT |
| D10 | **结构预览 / Blueprint / 自动组装 / 安全系统 OUT** | Phase 5 再加 |
| D11 | **3 类原生 Requirement（Item/Fluid/Energy）合并为 `sealed MachineIngredient`** | 比 1.12.2 ResourceLocation 注册更类型安全；与 KubeJS Schema 配合好 |
| D12 | **方块 IN/OUT 合并** | 1.12.2 时代 `BlockInputBus` + `BlockOutputBus` 9 等级 × 2 = 18 个类；首期 1 个类 + `IOType` 属性 |
| D13 | **15 个 Pkt 合并为 1 个 `PktMachineStatePayload`** | 99% Pkt 对应 OUT 功能；只保留状态同步 |
| D14 | **资源（贴图 / 模型）直接复用 MMCE 现成文件** | 只改 namespace + 路径命名（`textures/blocks/` → `textures/block/`） |
| D15 | **包结构 `api/ internal/ kubejs/ resources/`** | api 单向依赖 internal；kubejs 单独子包，缺 KubeJS 不影响 |

## 3. 范围边界（In / Out）

### 3.1 In Scope（约 40-60 .java + 资源）

- Mod 入口、注册、配置（`MMCRConfig` 1 个 ModConfigSpec）
- `Machine` / `MachineRegistry` / `BlockArray`（结构匹配）
- `MachineRecipe`（record implements `Recipe<>`） + `RecipeRegistry`
- 3 类 `MachineIngredient` sealed 子类
- 控制器 `MachineControllerBlock` + `MachineControllerBlockEntity`（核心）
- 1 个外壳方块 `MachineCasingBlock`
- 3 类仓（Item / Fluid / Energy）× 各 1 个 Block + 1 个 Tile（用 `IOType` BlockState 属性区分 IN/OUT），共 3 个方块 + 3 个 Tile
- 1 个 `PktMachineStatePayload`
- 1 个 `/mmcr reload` 命令
- `api.MMCR` 入口类
- KubeJS 插件（`MMCRKubeJSPlugin` + `MachineBuilderJS` + `MachineRecipeBuilderJS` + JSON schema）
- 资源（最小集 ~10 贴图 + 模型 + lang）

### 3.2 Out of Scope（Phase 5/6 再开 spec）

详见 `docs/scope.md §7`。总计 21 项 TODO + 9 项永久 OUT。

## 4. 架构（一图）

```
        KubeJS Script (optional)              Java API (required)
                │                                  │
                ▼                                  ▼
        ┌──────────────────────────────────────────────┐
        │            MachineRegistry + RecipeRegistry  │
        └──────┬───────────────────────────────────┬───┘
               │                                   │
               ▼                                   ▼
        Machine (record)                  MachineRecipe (record impl Recipe<>)
               │                                   │
               ▼                                   ▼
        BlockArray (Map<BlockPos, BlockPredicate>)  MachineIngredient (sealed)
               │                                   ├─ ItemIngredient
               │                                   ├─ FluidIngredient
               │                                   └─ EnergyIngredient
               ▼
        MachineControllerBlockEntity
               │ serverTick()
               ▼
        StructureMatcher.checkStructure()
               │ formed?
               ▼ yes
        tryStartNewRecipe() → tickRecipe() → 写入 IO bus/hatch via NeoForge capabilities
```

## 5. 关键算法（保留 MMCE 不动）

以下算法**不翻译、不重写**，只接 NeoForge 类型：

1. **结构匹配**：`BlockArray.matches(Level, BlockPos, EnumFacing)` —— 1.12.2 → 26.1.2 仅 `World` → `Level`，其他不变。
2. **配方检查**：`MachineRecipe.matches(RecipeInput, Level)` —— 1.12.2 自实现逻辑 → 实现 NeoForge `Recipe.matches` 接口。
3. **Tick 调度**：单 tick 单 thread，无并发。
4. **能力暴露**：`Capabilities.ItemHandler.BLOCK` / `FluidHandler.BLOCK` / `EnergyStorage.BLOCK` —— MMCE 1.12.2 自定义 Capability → NeoForge 同名 Capability（API 形态完全一致）。

## 6. 风险与缓解

| 风险 | 缓解 |
|---|---|
| `FluidType` + `Fluid` 双层是 1.12.2 没的概念 | 首期不注册新 `Fluid` / `FluidType`；只消费 vanilla 流体；流出 `FluidStack` 通过 fluid_hatch capability |
| DataComponent 替代 NBT 是大认知差 | 首期 `CompoundTag` 数据组件包打天下；后续按需拆分 |
| `World`/`Entity` → `Level`/`Entity` 命名变化 | 重构时全局替换 + 重新校验；1.12.2 → 26.1.2 概念 1:1 |
| `Capability` → `BlockCapability` 的 context 差异 | 首期只用标准 3 类；addon 自写 capability 时再学 |
| KubeJS 编译时飘红（runtimeOnly 时） | 把 `kubejs-neoforge` 改 `compileOnly + runtimeOnly` 重复声明（建议改 `build.gradle`） |
| 资源 namespace 替换（`modularmachinery` → `mmcr`） | 批量替换 + 重新校验所有引用；贴图直接拷贝改名 |
| GSON 完全废弃后，配方 JSON 怎么读 | NeoForge 自动从 `data/<ns>/recipe/*.json` 加载；用 `MapCodec` 反序列化（参考 `docs/api-mapping.md §7.3`） |

## 7. 验收标准（DoD）

跑通以下场景视为完成首期：

| # | 场景 | 期望 |
|---|---|---|
| 1 | 无 KubeJS 启动 | MMCR 正常加载，无 `Failed to load plugin` 错误 |
| 2 | 有 KubeJS 启动 | KubeJS 日志显示 `Found plugin source mmcr` |
| 3 | 玩家放控制器 + 外壳 + 仓 | 结构匹配成功，控制器 `formed = true` |
| 4 | KubeJS 注册 1 个机器 + 1 个配方 | `/mmcr reload` 后 `MachineRegistry` 含机器，`RecipeManager` 含配方 |
| 5 | 配方执行（每秒一次） | 输入仓扣 2 铁锭，输出仓加 1 铁板，能量仓扣 80 FE/tick |
| 6 | 缺能量或输入 | 配方不执行，控制器 idle |
| 7 | 拆 1 个外壳 | 控制器 `formed = false`，状态变为 idle |
| 8 | 重启世界 | 配方 + 机器 + 已组装的控制器状态全部保留 |
| 9 | 关闭 KubeJS 单独启动 | datapack JSON 配方仍能跑（如果有写） |

## 8. 不在范围内（永久）

- 自定义网络协议多于 1 个 Pkt
- 自定义 JSON 机器定义文件
- 任何 mod 联动代码（AE2 / Mekanism / GTCeu / GeckoLib / ModularMagic）
- 任何 GSON 反序列化器
- 任何 Mixin / AccessTransformer
- 任何第三方渲染库
- 任何「可配置 / 可扩展」超出 `sealed MachineIngredient` 的新 Requirement 类型（除非重开 spec）

## 9. 后续步骤

1. ✅ 4 篇设计文档写完（`scope.md` / `api-mapping.md` / `kubejs-integration.md` / `architecture.md`）
2. ⏳ **本规范等待用户审阅**
3. ⏳ 用户批准后：进入 `writing-plans` 技能，写实施计划（按 `scope.md §7` 拆分 Phase）
4. ⏳ 实施 Phase 1（首期 MVP），独立分支、独立 PR
5. ⏳ Phase 2+ 各自独立开 spec

---

**变更历史**
- 2026-08-02：初版（D1-D15 决策清单 + §3 范围 + §7 DoD）