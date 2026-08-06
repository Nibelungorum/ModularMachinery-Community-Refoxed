# Recipe 性能优化迁移方案

## 目标

Recipe 查找与可执行性检查是控制器 tick 中最容易放大的成本。MMCE 在长期迭代后，把“扫描候选配方、构造检查上下文、模拟 I/O、失败状态回传、工厂并行调度”拆成可复用的异步任务与上下文池。本项目当前已经有 `ActiveMachineRecipe`、`RecipeCraftingContext`、按机器和优先级索引的 `RecipeRegistry`，但配方启动仍由 `MachineControllerBlockEntity.tryStartNewRecipe()` 在服务端 tick 内同步遍历全部候选。

本文目标是把 MMCE 的 Recipe 性能优化完整迁移到 26.1.2 NeoForge 语义下，同时保持当前项目的轻量 API 与已有行为不退化。

## MMCE 优化调研

### 核心入口

MMCE 的普通控制器通过 `MachineRecipeThread.searchAndStartRecipe()` 驱动配方搜索。该方法不会每 tick 重扫配方：如果已有 `RecipeSearchTask` 未完成则直接返回；完成后读取任务结果、记录搜索耗时、成功时启动 active recipe，失败时递增重试计数。是否开始新搜索由 `RecipeThread.shouldSearchRecipe()` 决定：立即搜索标志或 `ticksExisted % currentRecipeSearchDelay() == 0`。

关键文件：

- `reference/mmce/src/main/java/hellfirepvp/modularmachinery/common/machine/RecipeThread.java`
- `reference/mmce/src/main/java/hellfirepvp/modularmachinery/common/machine/MachineRecipeThread.java`
- `reference/mmce/src/main/java/github/kasuminova/mmce/common/concurrent/RecipeSearchTask.java`

### `RecipeSearchTask`

`RecipeSearchTask` 是 MMCE 普通机器的配方查找任务。它持有控制器、当前 formed machine、最大并行数、候选配方列表和可选 `RecipeThread`。任务执行时逐个候选配方：

1. 为候选配方创建 `ActiveMachineRecipe(recipe, maxParallelism)`。
2. 通过线程或控制器创建 `RecipeCraftingContext`。
3. 调用 `controller.onCheck(context)` 完成启动前 I/O 检查。
4. 如果成功，再次确认控制器当前 formed machine 仍等于任务启动时的 `currentMachine`。
5. 成功则返回可直接启动的 context；失败则记录 validity 最高的失败结果，并把 context 还给池。

这个设计解决三类问题：

- **搜索不阻塞 tick 主路径**：普通 tick 只轮询任务完成状态。
- **失败原因不丢失**：没有可启动配方时，保留最高 validity 的失败信息，而不是只显示“无配方”。
- **异步结果防陈旧**：任务完成前结构可能变化，返回前比较 `foundMachine` 和 `currentMachine`，避免旧结果启动到新结构上。

### `RecipeCraftingContextPool`

MMCE 的 `RecipeCraftingContext` 比当前项目更重：构造时会 deep copy 每个 requirement，并维护组件路由、modifier、parallelism、I/O 状态等。因此 MMCE 提供 `RecipeCraftingContextPool`：

- 按 recipe id 分桶缓存 context。
- `borrowCtx(activeRecipe, ctrl)` 优先从队列复用，否则新建。
- `returnCtx(ctx)` 在 reload counter 一致时 reset 后放回池。
- `onReload()` 清空池并递增 reload counter，防止旧 recipe 结构污染新加载内容。

这个池不是普通对象池优化，而是配方检查性能的核心组成：在“多机器、多候选配方、频繁失败重试”情况下，避免每次扫描都重新分配 requirement/context 路由对象。

### 搜索节流与失败退避

MMCE 控制器维护 `recipeResearchRetryCounter`，普通控制器的搜索间隔随失败次数增长，工厂控制器还会根据核心线程数量调整延迟。成功启动后重置计数；失败时递增。这样可以避免“输入不足或输出堵塞时每 tick 扫完整配方表”。

当前项目已有 `lastFailureUnloc`，但没有 retry counter 和搜索延迟；如果某机器 recipe 数量较大，`tryStartNewRecipe()` 会在形成结构后每 tick 同步检查候选。

### 工厂控制器扩展

`FactoryRecipeSearchTask` 继承 `RecipeSearchTask`，额外处理：

- 已运行 recipe 数量 `runningRecipes`，按 recipe id 统计。
- recipe 指定 `threadName` 时只允许匹配对应工厂线程。
- recipe 指定 `maxThreads` 时限制相同 recipe 的并发数量。

这说明 MMCE 的 Recipe 搜索抽象不是只服务普通控制器，而是为后续并行线程、工厂控制器、核心线程预留了统一入口。

## 当前 MMCR 状态

### 已具备基础

当前项目已经具备以下迁移基础：

- `RecipeRegistry` 使用 `RECIPES` 和 `BY_MACHINE`，按 machine id 与 priority 建索引。
- `ActiveMachineRecipe` 持有 recipe、tick、totalTick、maxParallelism、parallelism 和 data，并负责 `start()` / `tick()`。
- `RecipeCraftingContext` 已集中处理 requirement simulate、I/O tick、finish、输入输出路由和失败详情。
- `MachineControllerBlockEntity` 已持有 `ActiveMachineRecipe active` 与 `RecipeCraftingContext context`，结构形成后可启动并执行配方。

### 主要差距

当前性能瓶颈集中在 `MachineControllerBlockEntity.tryStartNewRecipe()`：

- 每次无 active recipe 时同步调用 `recipesForMachine()`。
- 每个候选都新建 `RecipeCraftingContext`。
- 启动前在 tick 内串行执行 `simulateInputs()` 和 `simulateOutputs()`。
- 没有 `RecipeSearchTask`，无法把纯计算和路由模拟从控制器主逻辑拆出去。
- 没有 context pool，失败候选越多，临时对象越多。
- 没有搜索退避，输入不足/输出堵塞时会持续扫描。
- datapack recipe 合并每次启动搜索都重新遍历 `ServerRecipeAccess`。
- 没有异步任务完成后的 formed machine 陈旧校验。

## 迁移原则

### 主线程边界

Minecraft / NeoForge 的 `Level`、`BlockEntity`、capability 访问必须谨慎处理。MMCE 1.12.2 可以把更多逻辑放进 ForkJoin 任务，但 26.1.2 下不能直接假设异步线程安全访问世界对象。

迁移时采用三层方案：

- **第一层：同步任务壳**。先把搜索抽成 `RecipeSearchTask` / `RecipeSearchResult`，但仍在 server tick 调度点执行 live capability 检查；这一层已经能获得节流、context pool、候选缓存和失败结果缓存。
- **第二层：主线程快照**。在结构形成和组件更新时，由主线程维护候选 recipe、组件位置、handler 可用性等轻量快照，搜索任务只读取快照，不直接读 `Level`。
- **第三层：异步筛选**。只有当检查逻辑完全基于不可变快照时，才把搜索任务提交到 executor。任何真实 world/capability commit 仍留在 server tick 内。

### 行为优先

迁移优化不能改变现有语义：

- 仍按 `RecipeRegistry.byMachine(machine)` 与 datapack recipe 合并后的顺序选择首个可启动配方。
- `ActiveMachineRecipe.tick(context)` 的完成、等待、失败动作不改变。
- 输出空间不足时不消耗输入。
- 红石暂停、结构破坏、NBT 恢复等现有生命周期继续由控制器负责。

### 分阶段吸收 MMCE

不一次性引入 MMCE 的完整工厂线程系统。先把普通控制器的搜索任务、上下文池、候选缓存、搜索退避迁移完成，再把工厂 `maxThreads` / `threadName` 作为扩展字段接入。

## 目标架构

### 新增 `RecipeSearchTask`

建议新建：

`src/main/java/cn/howxu/mmcr/api/recipe/RecipeSearchTask.java`

职责：

- 捕获 `MachineControllerBlockEntity` 的结构版本、`foundMachine` id、最大并行数、候选 recipe 快照。
- 遍历候选 recipe，为每个 recipe 借用或创建 `RecipeCraftingContext`。
- 调用启动前检查协议：`simulateInputs(recipe)` + `simulateOutputs(recipe)` 或后续统一的 `canStartCrafting(recipe)`。
- 成功时返回 `RecipeSearchResult.success(activeRecipe, context, machineId, structureVersion)`。
- 失败时返回 validity 最高的 `RequirementFailure` / `lastFailureUnloc`。
- 对未使用的 context 调用 pool return。

Phase 1 的 `RecipeSearchTask` 只是“任务壳”，必须同步执行在 server tick 调度点内；不得提交后台线程，也不得让后台线程调用 `simulateInputs()` / `simulateOutputs()`。只有 Phase 4 完成主线程快照后，才允许把纯快照筛选提交 executor。

不建议直接让任务继承 `ForkJoinTask`。当前项目可以先定义同步 `run()` / `compute()` 风格接口，Phase 4 再用普通 `CompletableFuture<RecipeSearchResult>` 或小型 executor 包装，后续如确有必要再接入全局执行管理器。

### 新增 `RecipeSearchResult`

建议新建不可变结果类型：

- `boolean success`
- `Identifier machineId`
- `long structureVersion`
- `ActiveMachineRecipe activeRecipe`
- `RecipeCraftingContext context`
- `String failureUnloc`
- `RequirementFailure requirementFailure`
- `float validity`

这样控制器只处理“结果是否仍适用于当前结构”和“成功启动或显示失败”，不再关心搜索内部细节。

结果不变量：

- 成功结果必须包含非 null `activeRecipe` 和 `context`，并且 `failureUnloc` / `requirementFailure` 为空。
- 失败结果必须不包含 `activeRecipe` 和 `context`，只携带失败信息与 validity。
- 陈旧结果不是失败结果；控制器丢弃陈旧成功结果时必须归还其中 context，且不更新用户可见失败原因。

### 新增 `RecipeCraftingContextPool`

建议新建：

`src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextPool.java`

迁移 MMCE 的要点：

- 按 `Identifier recipeId` 分桶。
- `borrow(recipe, controller)` 返回 reset/init 后的 context。
- `returnContext(context)` 清理 route、failure、requirement failure、临时 modifier 状态。
- `onReload()` 清空池并递增 reload counter。

当前 `RecipeCraftingContext` 构造只接收 controller，且 route 字段会在 simulate 时重建。需要增加 `resetFor(controller)` 或 `resetTransientState()`，不要通过反射或新建 controller 引用绕过。

context 归还点必须完整覆盖：

- 候选 recipe 检查失败后立即归还。
- 搜索成功但结果因 structureVersion / machine id / active 状态不匹配而被丢弃时归还。
- active recipe 完成后归还。
- active recipe 因 per-tick failure、红石暂停取消、结构 reset、控制器卸载而清空时归还。
- recipe reload 或 `/mmcr reload` 时清空池，旧 context 不再复用。

`resetFor(controller, activeRecipe)` 至少清理 item/fluid input routes、item/fluid output routes、`lastFailureUnloc`、`lastRequirementFailure`、临时 modifier 或 parallelism 派生状态；不能保留旧 controller、旧 component 列表或旧 recipe 的 route。

### 控制器搜索状态

`MachineControllerBlockEntity` 增加搜索状态字段：

- `private RecipeSearchHandle searchTask;`
- `private int recipeSearchRetryCounter;`
- `private long structureVersion;`
- `private int nextRecipeSearchDelay();`

`structureVersion` 在 `onStructureFormed()`、`resetMachine()`、`updateComponents()` 影响 route 的情况下递增。搜索结果回来时必须校验：

- 当前仍 formed。
- `foundMachine.registryName()` 等于结果 machine id。
- 当前 `structureVersion` 等于结果 structure version。
- active recipe 仍为空。

校验失败则把成功结果的 context 归还池，不启动 recipe。

### 候选 recipe 缓存

`recipesForMachine()` 当前每次会复制 `RecipeRegistry` 并遍历 datapack recipes。建议拆为：

- `RecipeRegistry` 继续维护 Java/KubeJS 注册 recipe。
- 新增 controller 局部 `cachedCandidates`，key 为 `machineId + serverRecipeReloadVersion`。
- reload 时清空缓存，并调用 `RecipeCraftingContextPool.onReload()`。

如果项目暂时没有统一 reload version，可以先在 `/mmcr reload` 和 datapack recipe 同步点显式清理。

最小实现可以在 `RecipeRegistry` 增加 `private static long reloadVersion`：`register()` 不递增，`clearAll()` / `/mmcr reload` / datapack recipe reload 接入点递增。controller 的 `cachedCandidates` key 使用 `machineId + RecipeRegistry.reloadVersion()`。如果 datapack reload 接入点暂时不可用，第一阶段至少在 `recipesForMachine()` 每次看到 server recipe count 或 recipe access identity 变化时清空本 controller 缓存，并在文档/计划中标记为临时方案。

### 失败有效性评分

MMCE 的 `CraftingCheckResult` 有 validity，用于从多个失败配方中选择“最接近成功”的错误提示。当前项目有 `RequirementFailure`，但没有评分。建议引入轻量规则：

- 输入/输出组件完全缺失：低分。
- 有匹配组件但数量不足/容量不足：中分。
- 仅最后一个 requirement 失败：高分。
- 已匹配数量越接近 required，分数越高。

评分 tie-break 必须稳定：同分时保留原候选顺序中更靠前的 recipe。`RequirementFailure` 为空但 `lastFailureUnloc` 存在时给最低非零分；结构/端口形成失败不参与 recipe failure 评分，由结构检测状态优先显示。

这样大量 recipe 同时失败时，Jade/GUI 能显示更有用的失败原因。

## 迁移步骤

### Phase 1：同步任务壳与节流

先不引入异步线程，只把搜索从 `tryStartNewRecipe()` 抽成任务对象并接入 retry delay。

验收：

- 控制器不再每 tick 全量扫描。
- Phase 1 的搜索仍同步运行在 server tick 内，不创建后台线程。
- 输入不足时 `recipeSearchRetryCounter` 增长，搜索间隔变长。
- 成功启动后 retry counter 清零。
- 行为与现有 recipe E2E 一致。

### Phase 2：Context Pool

引入 `RecipeCraftingContextPool`，让搜索任务借还 context。

验收：

- 所有失败候选 context 都归还。
- 成功启动的 context 归 active recipe 持有，完成/取消/结构破坏时归还。
- reload 清空池。
- 不留下旧 controller 或旧 recipe 引用。

### Phase 3：候选缓存与失败评分

缓存合并后的候选 recipe 列表，并为失败结果加 validity。

验收：

- datapack recipe 与 Java/KubeJS recipe 仍可启动。
- 重复搜索不再每次遍历全部 `ServerRecipeAccess`。
- `lastFailureUnloc` 和 `RequirementFailure` 指向最有用失败项。

### Phase 4：异步安全化

在明确 world/capability 访问边界后，将纯计算部分提交 executor。真实 commit 仍在 server tick 内执行。

验收：

- 搜索任务完成后经过 structureVersion 校验。
- 结构变化、机器切换、红石暂停、active recipe 已被其他路径设置时，不会启动旧结果。
- 任务异常只影响该次搜索，不破坏控制器状态。

### Phase 5：工厂/并行预留

在 `MachineRecipe` 已有 `maxThreads`、`threadName` 或后续字段时，引入 `FactoryRecipeSearchTask` 等价逻辑。

验收：

- 普通控制器不受工厂字段影响。
- 工厂控制器可按 thread name 和同 recipe 并发数量过滤候选。
- `ActiveMachineRecipe.parallelism` 与最大并行数只通过 search result 设置。

## 文件级改造清单

预计新增：

- `src/main/java/cn/howxu/mmcr/api/recipe/RecipeSearchTask.java`
- `src/main/java/cn/howxu/mmcr/api/recipe/RecipeSearchResult.java`
- `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContextPool.java`
- `src/main/java/cn/howxu/mmcr/api/recipe/RecipeSearchHandle.java` 或等价 executor 封装

预计修改：

- `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`
- `src/main/java/cn/howxu/mmcr/api/recipe/RecipeCraftingContext.java`
- `src/main/java/cn/howxu/mmcr/api/recipe/RecipeRegistry.java`
- reload 命令或 datapack reload 接入点

## 验证标准

最低验证：

- `./gradlew compileJava --no-daemon`
- 现有 recipe GameTest 或 smoke test 继续通过。
- 手工验证 formed controller 能启动并完成默认 recipe。
- 输入缺失时不会每 tick 打日志或每 tick 全量扫描。
- 结构破坏时 active context 和 search result 都不会泄漏。

建议补充纯逻辑测试：

- `RecipeCraftingContextPool` borrow/return/reload。
- `RecipeSearchTask` 成功返回第一个可启动 recipe。
- `RecipeSearchTask` 失败时保留最高 validity failure。
- structureVersion 不匹配时控制器丢弃 search result。

建议增加 debug 计数或日志开关用于验证：搜索启动次数、候选缓存命中次数、context borrow/return 次数、陈旧结果丢弃次数。输入不足场景下，搜索启动次数应符合 `nextRecipeSearchDelay()`，而不是每 tick 增长。

## 风险与约束

- 不要在异步线程直接访问 `Level` 或 live `BlockEntity`，除非已经确认 NeoForge API 与该 capability 访问安全。
- 不要把 `RecipeCraftingContext` 池化后忘记清理 route 和 failure 字段。
- 不要让搜索任务持有过期 controller 结果并直接启动 recipe。
- 不要引入完整 MMCE `TaskExecutor` 之前就复制工厂线程复杂度。
- 不要改变当前配方选择顺序，除非单独设计 priority 语义变更。

## 实施顺序建议

优先顺序应为：Phase 1 同步任务壳与节流 → Phase 2 Context Pool → Phase 3 候选缓存与失败评分。只有完成这三步后，再评估 Phase 4 异步安全化。不要在没有快照边界的情况下先把 `simulateInputs()` / `simulateOutputs()` 放到后台线程。

## 完成定义

Recipe 性能优化迁移完成后，应满足：

- 普通控制器 recipe 搜索从同步全量扫描变为可节流、可缓存、可池化的搜索流程。
- context 生命周期明确，失败候选不产生长期对象压力。
- 搜索失败能显示最接近成功的失败原因。
- 搜索结果具备结构版本校验，不会跨结构误启动。
- 后续工厂控制器和并行 recipe 可以复用同一搜索抽象。
