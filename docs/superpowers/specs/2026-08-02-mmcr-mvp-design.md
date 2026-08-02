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
| D2 | **机器定义不能用 JSON；配方可以用 datapack JSON** | 机器走 Java API / KubeJS builder；配方走 Java API / KubeJS / datapack JSON（KubeJS 原生支持） |
| D3 | **CraftTweaker 完全替换为 KubeJS** | 已在 build.gradle；ZenScript → KubeJS |
| D4 | **KubeJS 是可选绑定** | 缺 KubeJS 时 MMCR 仍能跑（仅无脚本入口） |
| D5 | **GeckoLib / Lumenized OUT** | NeoForge 26.1.2 原生渲染已够；不引入第三方渲染库 |
| D6 | **GSON 双阶段反序列化 OUT** | 直接用 NeoForge `Codec` / `MapCodec` |
| D7 | **Mixin 按需使用**（非硬性 OUT） | MMCE 4 个 mixin 删除（GeckoLib/AE2/JEI-GUI 对应功能 OUT）；首期不主动写；遇到 NeoForge API 限制时**按需新增** |
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
- KubeJS 插件（`MMCRKubeJSPlugin` + `MachineBuilderJS` + `MachineRecipeBuilderJS` + 程序化 `MachineRecipeSchema`）
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
| GSON 完全废弃后，配方怎么注册 | 3 入口：① Java API `RecipeRegistry.register(MachineRecipe)`；② KubeJS 脚本；③ datapack JSON `data/<ns>/recipe/*.json`（用 `MachineRecipeSerializer` + `MapCodec` 反序列化） |

## 7. 测试策略（**每个功能必须有测试**）

> 每个新增功能 / 修复 bug 必须伴随：① 单元测试（如适用）；② GameTest（涉及 Block / Level / Tick 的功能）。

### 7.1 测试分层

| 层 | 框架 | 跑在哪 | 覆盖什么 | 例子 |
|---|---|---|---|---|
| **Unit** | JUnit 5（Jupiter）+ AssertJ | 纯 JVM，无 MC | 纯算法 / 数据结构 | `BlockArray` 旋转；`BlockPredicate.Air.matches`；`MachineIngredient` codec |
| **GameTest** | NeoForge 26.1.2 `GameTest` 内置 | 真实 MC 实例 | Block / Level / Tick 行为 | 放方块 → 结构匹配；跑配方 → IO 流通；拆方块 → unform |
| **GameTest + KubeJS** | GameTest + KubeJS 运行时 | 真实 MC 实例 + KubeJS | 脚本入口 | KubeJS 注册机器 + 配方 → registries 可见；reload → recipes 重建 |

### 7.2 必须写测试的功能清单（首期）

| 功能 | 测试类型 | 测试文件 | 关键断言 |
|---|---|---|---|
| `BlockPredicate` 5 个变体 | Unit | `BlockPredicateTest.java` | 每个变体的 `matches(BlockState)` 行为正确 |
| `BlockArray.matches(...)` | Unit + GameTest | `BlockArrayTest.java` + `BlockArrayMatchGameTest.java` | 全匹配 / 单方块错 / 朝向不同 / 镜像 |
| `MachineIngredient.CODEC` | Unit | `MachineIngredientCodecTest.java` | 3 子类编解码 round-trip 正确 |
| `MachineRegistry.register/getMachine` | Unit | `MachineRegistryTest.java` | 注册冲突抛错；不存在的 id 返回 null |
| `MachineRecipe.matches(RecipeInput, Level)` | GameTest | `MachineRecipeMatchGameTest.java` | 输入够 / 不够；能量够 / 不够 |
| `MachineControllerBlockEntity.serverTick()` | GameTest | `ControllerTickGameTest.java` | formed → tick → 输出到 IO；拆壳 → unform |
| `MMCRKubeJSPlugin` 注册链 | GameTest | `KubeJSIntegrationGameTest.java` | KubeJS 在场时 builder 可用；缺 KubeJS 时不崩 |
| `/mmcr reload` 命令 | GameTest | `CommandReloadGameTest.java` | 注册新机器后 reload 可见 |

### 7.3 测试目录结构

```
src/
├─ main/                    # 生产代码
├─ test/java/               # Unit 测试（JUnit 5）
│   └─ cn/howxu/mmcr/
│      ├─ machine/BlockArrayTest.java
│      ├─ machine/BlockPredicateTest.java
│      ├─ recipe/MachineIngredientCodecTest.java
│      └─ recipe/MachineRegistryTest.java
├─ gametest/java/           # GameTest（运行时）
│   └─ cn/howxu/mmcr/
│      ├─ BlockArrayMatchGameTest.java
│      ├─ MachineRecipeMatchGameTest.java
│      ├─ ControllerTickGameTest.java
│      ├─ KubeJSIntegrationGameTest.java
│      └─ CommandReloadGameTest.java
└─ main/resources/
   ├─ pack.mcmeta
   ├─ data/mmcr/gametests/<test_name>.json   # GameTest 入口（可选）
```

### 7.4 build.gradle 增量

```gradle
// JUnit 5（unit test）
testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher'

// GameTest 由 NeoForge 26.1.2 `runs.gameTestServer` 自动加载；无需额外依赖
```

### 7.5 测试覆盖率目标（首期）

| 模块 | 行覆盖目标 |
|---|---|
| `machine/BlockArray.java` | ≥ 80% |
| `machine/BlockPredicate.java` | ≥ 90% |
| `recipe/MachineIngredient.java` (含子类) | ≥ 70% |
| `recipe/MachineRecipe.java` | ≥ 60% |
| `tile/MachineControllerBlockEntity.java` | ≥ 50%（核心路径必须有 GameTest） |

**GameTest 测试总数 ≥ 6 个**（覆盖结构匹配 + tick 调度 + KubeJS 集成 + 命令）。

### 7.6 编写测试的硬性要求（每 PR）

1. **新功能**：必须伴随至少 1 个 unit 测试 或 1 个 GameTest（涉及 Block/Level 的必须 GameTest）。
2. **改算法**：必须更新 / 补对应测试；覆盖率不下降。
3. **修 bug**：先写能复现 bug 的测试（red），再修代码（green），最后 refactor。
4. **禁止** 写只跑通自己 mock 的测试——必须验证真实行为。

## 8. 验收标准（DoD）

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
| 9 | 关闭 KubeJS 单独启动 | MMCR 仍加载；可走 Java API 注册机器 / 配方；datapack JSON 配方仍可用 |
| 10 | 跑 `./gradlew test` | 全部 unit 测试通过，覆盖率达 §7.5 目标 |
| 11 | 跑 `./gradlew runGameTestServer` | 全部 GameTest 通过 |

## 9. 不在范围内（永久）

- 自定义网络协议多于 1 个 Pkt
- 自定义 JSON 机器定义文件
- 任何 mod 联动代码（AE2 / Mekanism / GTCeu / GeckoLib / ModularMagic）
- 任何 GSON 反序列化器
- MMCE 1.12.2 的 4 个 mixin 包（已删除，对应功能 OUT）
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
- 2026-08-02（v1.1）：删除 KubeJS 配方 schema JSON；`MachineRecipe` 仅 Java / KubeJS 注入；D2「JSON 严格 OUT」
- 2026-08-02（v1.2）：
  - 恢复 KubeJS 原生 datapack JSON 配方支持（仅配方，机器仍 OUT）
  - Mixin 从「硬 OUT」改为「按需使用」
  - 补充测试策略章节（每个功能必有测试）