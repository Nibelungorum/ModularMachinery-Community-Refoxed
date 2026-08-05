# Phase 2 剩余执行文档：Requirement / Component 正式层

日期：2026-08-05

关联设计：`docs/superpowers/specs/2026-08-05-phase2-requirement-component-design.md`

> 本文不是替代原 Phase 2 设计，而是把当前 worktree 已完成的边角工作与剩余主体拆成可执行批次。不要因为改动沉重而继续在 `MachineIngredient` 简化模型上打补丁；Phase 2 的核心目标就是把 IO 路由从“上下文硬编码”迁移到“requirement 自治”。

## 1. 当前状态判断

### 1.1 已在 worktree 看到的完成项

- 输出 bus 能通过 capability 插入，GameTest 已从“拒绝插入”改为“允许插入”。
- item bus 菜单槽位变成普通容器语义：输入/输出都允许玩家取放，quick move 也不再按 IO 方向硬拒绝。
- `RecipeCraftingContext` 的 item output commit 会先合并同类 stack，再占用空槽，避免输出 bus 被碎片化。
- `DefaultRecipes` 改回普通 `new ItemStack(Items.IRON_NUGGET, 1)`，并补了 max stack size 验证。
- Jade controller provider 已新增，能显示机器、结构、状态、进度、并行和组件数量。
- 翻译里已经补了 Jade controller 文案。

### 1.2 这些完成项的边界

- 它们解决的是“端口可用性 / 输出合并 / 信息展示”问题，不等价于 Phase 2 requirement 层完成。
- 现有 `RecipeCraftingContext` 仍然直接消费 `MachineIngredient.ItemIngredient` / `FluidIngredient` / `EnergyIngredient`。
- 现有 `MachineRecipe` 仍然没有正式 `requirements()`，旧字段仍是配方 IO 的源头。
- 现有 `ProcessingComponent.tag` 仍未由结构定义填充；selector tag 没有真正进入匹配路径。

## 2. Phase 2 剩余目标

Phase 2 剩余工作必须把以下事实变成代码现实：

- `MachineRecipe.requirements()` 是控制器运行时唯一的 IO 路由入口。
- `MachineRequirement` 自己负责匹配组件、模拟、提交、输出、per-tick IO。
- `RecipeCraftingContext` 负责生命周期上下文和 route 缓存，不再知道 item/fluid/energy 的具体消耗细节。
- item / fluid / energy 三类 requirement 足够覆盖 vanilla + NeoForge 首期闭环。
- selector tag 至少在 Java/KubeJS 构造层可用，旧机器不声明 tag 时行为完全不变。
- 错误信息能告诉开发者缺的是哪个 requirement、差多少、查过哪些组件。

## 3. 推荐执行批次

### 批次 A：引入 requirement 数据模型，但不切运行时

目标：让 `MachineRequirement` 能被构造、序列化、从旧字段派生，但不急着改 controller tick。

改动范围：

- 新增 `cn.howxu.mmcr.api.recipe.requirement.MachineRequirement` sealed interface。
- 新增 `ItemRequirement`、`FluidRequirement`、`EnergyRequirement`。
- 新增最小枚举或字段表达 IO 方向，优先复用现有 `IOType`，不要再造语义重复的 enum。
- `MachineRecipe` 新增 `List<MachineRequirement> requirements` 字段和 `requirements()` getter。
- `MachineRecipe` 构造器继续接受旧 `inputs` / `outputs` / `fluidOutputs`，内部派生成 requirements。
- codec 新增可选 `requirements` 字段；解码时 `requirements` 存在则优先，否则从旧字段派生。
- 编码策略固定为优先输出 requirements，避免旧字段和新字段同时写出造成不稳定 shape。

验收：

- 旧 recipe JSON 不改仍能解码。
- 新 requirements JSON 能解码并 roundtrip。
- mixed shape 时 requirements 优先，旧字段不参与运行时派生。
- `MachineRecipe.outputs()` / `fluidOutputs()` 仍可用，但结果来自 requirements 派生。

### 批次 B：抽 route 与 failure 结构

目标：在不完全重写 item/fluid/energy 逻辑前，先把 context 的“模拟结果快照”标准化。

改动范围：

- `RecipeCraftingContext` 新增 route map，key 可以先用 requirement 实例或稳定 index。
- route value 不要用裸 `Object`；至少拆成 item route / fluid route / energy route，或使用 sealed route record。
- 新增 `RequirementFailure` / `RequirementShortage` 之类的数据结构，包含：
  - requirement 描述。
  - required 数量。
  - available 数量。
  - short 数量。
  - searched components 列表。
  - matched components 列表。
- `CraftCheck.failure(...)` 保留字符串入口，但新增可携带结构化 failure 的路径。
- 控制器日志只输出 summary，不要每 tick 打爆日志。

验收：

- simulate 失败不会 commit。
- failure 能区分 missing input / missing output / missing energy。
- 单测能断言 short 数量和 searched components，而不是只比对文案。

### 批次 C：ItemRequirement 接管 item input/output

目标：先迁移最常用、最容易用测试闭环的 item 路由。

改动范围：

- `ItemRequirement.matches(ProcessingComponent)` 根据 component kind、IOType、selector tag 判断候选。
- input simulate：遍历所有匹配 item input bus，跨 slot 聚合匹配 `Ingredient` 的 count。
- input commit：严格按 simulate route 抽取；不要重新搜索，避免 simulate/commit 不一致。
- output simulate：优先检查同类 stack 可合并空间，再检查空槽空间。
- output commit：沿用当前 worktree 中“先合并后空槽”的行为，但下沉到 `ItemRequirement`。
- `RecipeCraftingContext.simulateInputs` / `commitInputs` / `simulateOutputs` / `commitOutputs` 对 item 路径改成遍历 requirements。
- 旧 helper 可以暂时保留，但不应再作为新路径的主逻辑。

验收：

- 单 item input bus 可消耗。
- 多 item input bus 可共同提供同一 ingredient。
- item output bus 满时不启动配方，不吞输入。
- output commit 先合并同类 stack，再使用空槽。
- route 中途容器失效时 commit 返回 false 并记录日志。

### 批次 D：FluidRequirement 接管 fluid input/output

目标：把 Phase 1 fluid 闭环迁移到同一 requirement 路由模型。

改动范围：

- `FluidRequirement.matches(ProcessingComponent)` 根据 hatch kind、IOType、selector tag 判断候选。
- input simulate：跨多个 fluid input hatch 聚合可 drain 的 amount。
- input commit：按 route drain；不要重新计算候选。
- output simulate：跨多个 fluid output hatch 聚合可 fill 的空间。
- output commit：按 route fill；保留 NeoForge `FluidStack` 与 `FluidIngredient` 语义。
- `MachineRecipe.fluidOutputs()` 改为从 fluid output requirements 派生。

验收：

- 多 hatch 合计流体足够时可启动。
- 单 hatch 不够但多个 hatch 足够时可启动。
- output hatch 空间不足时不启动，不消耗 item/fluid/energy。
- fluid-only recipe 的 `assemble()` 仍返回 `ItemStack.EMPTY`。

### 批次 E：EnergyRequirement 接入 per-tick IO

目标：保持 `EnergyRecipeIo` 公开签名不动，但让 recipe runtime 从 requirements 找能量需求。

改动范围：

- `EnergyRequirement(int fePerTick)` 只表达输入，不做输出。
- 旧 `MachineIngredient.EnergyIngredient` 解码后派生成 `EnergyRequirement`。
- `RecipeCraftingContext.ioTick` 遍历 requirements，只对 energy requirement 调用 per-tick IO。
- 能量 route 可以继续委派给 `EnergyRecipeIo`，不要在本批次重写能量存储策略。
- failure action 继续沿用 Phase 1 接好的 `Machine.failureAction()`。

验收：

- 能量不足时 active recipe 按既有策略暂停/失败，不继续消耗 item/fluid。
- `EnergyRecipeIoTest` 继续覆盖核心行为。
- controller 日志仍保持降噪：生命周期 + failure summary。

### 批次 F：Selector tag 与 BlockArray metadata

目标：让 requirement 能限定组件组，但旧结构默认通配。

改动范围：

- `ProcessingComponent` 的 tag 从 `@Nullable String` 升级为 `List<String>` 或保持单 tag 但明确 Phase 2 只支持单 tag；建议直接做 `List<String>`，避免马上返工。
- `BlockArray` 增加 per-position tag map：`Map<BlockPos, List<String>>`。
- `BlockArray.tagged(BlockPos pos, String... tags)` 返回自身或新 copy，按现有 builder 风格决定。
- `BlockArrayCache` 旋转/缓存时必须同步旋转 tag map，不能只旋转 pattern。
- `MachineControllerBlockEntity.updateComponents()` 根据 rotated pattern 的 relative pos 查 tag，传给 `ProcessingComponent`。
- `MachineRecipeBuilderJS` 或 Java API 允许给 requirement 填 tag；JSON tag 可以先支持读写，但示例延后。

匹配规则：

- requirement tag 为空：允许所有同类组件。
- component tag 为空：允许无 tag requirement；对有 tag requirement 不匹配。
- 两边都有 tag：任一 tag 重叠即匹配。

验收：

- 未声明 tag 的旧机器行为不变。
- recipe 声明 tag 后，只从对应 tag 的 bus/hatch 消耗或输出。
- tag 不命中时 failure 能说明查过哪些组件、哪些被 tag 排除。

### 批次 G：Context 去具体 BE 化

目标：让 `RecipeCraftingContext` 不再直接散落依赖具体 `ItemInputBusBlockEntity` / `FluidInputHatchBlockEntity` 等类。

改动范围：

- 在 `ProcessingComponent` 或 requirement 内集中解析 item/fluid/energy container。
- 优先依赖 NeoForge capability / handler 接口：`IItemHandler`、`IFluidHandler`、`IEnergyStorage`。
- 保留必要的 kind 判断，因为输入/输出方向仍来自 MMCR component kind。
- 如果 capability 获取需要 side，先沿用 `null` side；不要在 Phase 2 引入 side-aware 路由。

验收：

- context 层不再出现多个具体 bus/hatch BE 分支。
- requirement 单测可以用 mock handler / 简单 fake component 覆盖大部分路径。
- GameTest 仍覆盖真实 BE 集成。

### 批次 H：控制器 GUI 聚合展示

目标：完成原 Phase 2 spec §11 的 GUI 增量；这不是 requirement 切换的前置，但应在 P2 结束前完成。

改动范围：

- `MachineControllerBlockEntity` 新增：
  - `long totalStoredEnergy()`。
  - `long totalCapacityEnergy()`。
  - `FluidStack primaryFluid()`。
  - `FluidStack primaryOutputFluid()`。
- `MachineControllerMenu` 新增同步槽；如果 DataSlot 只能同步 int，需要明确拆高低位或接受上限裁剪，不要静默溢出。
- fluid id 同步若 DataSlot 不够表达 registry id，优先用已有菜单 owner fallback；必要时引入小 payload，但不要扩大成全量 inventory sync。
- `MachineMenuScreen` 新增 energy bar 与 fluid preview，位置不能遮挡现有状态/进度/玩家背包。

验收：

- 没有 energy hatch 时不画 energy bar。
- 没有 fluid hatch 或 fluid 为空时画空槽或不画内容，不崩溃。
- client-only menu 没 owner 时 fallback 为 0/empty。
- hatch/bus 自己的 GUI 不被 controller GUI 新增展示影响。

## 4. 切换策略

### 4.1 不允许的中间态

- 同一次 simulate 用 requirements，commit 又回到旧 inputs/outputs。
- item 走 requirements，fluid output 仍由 context 另一路提交，但二者共享同一 route map。
- `MachineRecipe.requirements()` 和 `inputs()` 各自持有可变真源，导致两边不同步。
- failure 文案靠字符串拼接冒充结构化信息，后续 Jade/HUD 无法读取。

### 4.2 允许的中间态

- 批次 A 后，requirements 只作为派生模型存在，运行时暂不使用。
- 批次 C 后，item 已走 requirements，fluid/energy 暂时仍旧路径，但 context 入口要明确分段，不要混写。
- 批次 E 后，`MachineIngredient` codec 仍保留，作为旧 JSON 兼容层。
- selector tag API 先支持 Java/KubeJS 构造，JSON 示例延后。

### 4.3 建议提交边界

- Commit 1：requirement 模型 + codec + roundtrip tests。
- Commit 2：route/failure 结构 + item requirement runtime。
- Commit 3：fluid requirement runtime。
- Commit 4：energy requirement runtime + context 去旧分支。
- Commit 5：selector tag metadata + matching tests。
- Commit 6：controller GUI 聚合展示 + Jade/translation polish。

## 5. 测试清单

### 5.1 单元测试

- `MachineRequirementTest`
  - item input 单 bus 满足。
  - item input 多 bus 聚合满足。
  - item input 不足返回 short。
  - item output 优先合并同类 stack。
  - item output 空间不足返回 failure。
  - fluid input 单 hatch / 多 hatch 聚合。
  - fluid output 空间不足。
  - selector tag 命中 / 不命中 / requirement 无 tag 通配。
- `MachineRecipeCodecTest`
  - 旧 fields 解码派生 requirements。
  - 新 requirements 解码优先。
  - 编码 shape 稳定。
  - item/fluid/energy requirement roundtrip。
- `RecipeCraftingContextTest`
  - simulate 成功后 commit 使用 route。
  - simulate 失败不留下可提交 route。
  - commit 失败不被误报为成功。
  - legacy helper 不重新进入主路径。

### 5.2 GameTest

- 多 item input bus 合计提供 ingredient，recipe 可启动并正确扣除。
- tag 限定 input bus 时，只扣指定 tag 组件。
- tag 不匹配时 recipe 不启动，物品不消耗。
- fluid input 多 hatch 聚合。
- output bus/hatch 空间不足时不启动，不吞输入。
- energy 不足时 per-tick 行为保持 Phase 1 语义。

### 5.3 构建验证

- 每个批次至少跑 `./gradlew compileJava --no-daemon`。
- 完成 P2 主体后跑 `./gradlew test --no-daemon`。
- 如果改了 GameTest 或注册/资源路径，额外跑对应 GameTest 环境；若环境不可用，需要在提交说明里明确未跑原因。

## 6. 风险与决策点

### 6.1 `ProcessingComponent.tag` 单 tag 还是多 tag

建议：直接多 tag。

原因：MMCE selector 概念天然可以按组扩展；如果 Phase 2 做单 tag，很快会在 upgrade/smart interface/结构预览阶段返工。多 tag 的实现成本低，只要匹配规则写清楚即可。

### 6.2 `MachineRequirement` 是 sealed interface 还是 abstract class

建议：sealed interface。

原因：当前 recipe API 已有 sealed `MachineIngredient` 风格，接口能让 record requirement 更轻；共享 helper 用 package-private utility 即可，不必用继承承载状态。

### 6.3 route key 用 requirement 实例还是 index

建议：用 recipe requirement index。

原因：codec roundtrip 和派生 getter 可能创建等价但非同一实例的 requirement；index 更稳定，也更容易在 failure 里定位“第几个 requirement”。

### 6.4 旧字段什么时候删除

建议：Phase 2 不删除。

原因：旧 JSON、KubeJS builder、默认配方和测试都依赖旧字段。Phase 2 只把旧字段降级为兼容输入，不再作为运行时真源。

### 6.5 GUI fluid id 同步

建议：先不强行用 DataSlot 表达 registry id。

原因：DataSlot 是 int，同步 fluid registry id 需要额外映射，容易做出脆弱实现。若 `resolvedOwner()` 在常见场景可用，先走 owner fallback；如果 dedicated client 场景不可靠，再引入小 payload。

## 7. 完成定义

P2 不能只以“能跑配方”为完成标准。必须同时满足：

- `MachineRecipe.requirements()` 是 runtime IO 唯一入口。
- item/fluid/energy 三类 requirement 都有实现和 codec。
- 旧 fields 仍兼容，但运行时不再直接消费旧 `MachineIngredient` 分支。
- 多组件聚合对 item 和 fluid 都可用。
- selector tag 从 `BlockArray` metadata 到 `ProcessingComponent` 再到 requirement matching 全链路可用。
- failure 至少在结构化对象里保留 short 和 searched components。
- controller GUI 能展示结构级 energy/fluid 概览。
- `compileJava` 和 `test` 通过；GameTest 不退化，或明确记录未跑原因。

## 8. 与后续阶段的边界

- JEI 不在本 P2；按 `docs/main-roadmap.md` 现口径属于 Phase 4。
- parallel/factory 不在本 P2；但 route 结构必须能被 Phase 5 扩展为按 parallelism 放大数量。
- smart interface 不在本 P2；但 selector tag 设计不能阻碍后续 numeric requirement 读取指定 interface。
- modifier 全链不在本 P2；但 requirement 的 count/amount getter 不应写死为 raw 值，至少保留后续 modifier 接入点。
- 第三方 mod requirement 不在本 P2；不要为了 Mekanism gas / AE2 ME 提前泛化注册中心。
