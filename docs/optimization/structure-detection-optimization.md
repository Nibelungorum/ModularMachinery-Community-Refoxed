# 结构检测性能优化迁移方案

## 目标

结构检测是多方块机器的另一个高频性能热点。每个控制器都可能周期性读取大量方块状态、旋转 pattern、统计端口、查找组件，并在结构变化时重建运行时上下文。MMCE 对此做了两类核心优化：启动/重载时预构建结构旋转缓存，以及控制器形成后复用已匹配结构并只做必要复查。

当前项目已经有 `BlockArrayCache` 和 `foundPattern` 复用，但仍缺少 MMCE 的完整缓存预热、结构版本、动态结构缓存、tile/component 位置预索引和检测节流体系。本文记录 MMCE 的结构检测优化，并给出完整迁移方案。

## MMCE 优化调研

### `BlockArray.uid` 与旋转缓存

MMCE 的 `BlockArray` 构造时会从 `BlockArrayCache.nextUID()` 获取稳定 uid。`BlockArrayCache` 用 `Long2ObjectMap<EnumMap<EnumFacing, BlockArray>>` 保存同一个原始 pattern 在不同 facing 下的旋转结果。

关键文件：

- `reference/mmce/src/main/java/hellfirepvp/modularmachinery/common/util/BlockArray.java`
- `reference/mmce/src/main/java/hellfirepvp/modularmachinery/common/util/BlockArrayCache.java`

这种设计避免把 `BlockArray` 本身作为 map key，也避免深度比较 pattern。每个结构定义只需要一个 uid，所有旋转结果按 facing 查表。

### 启动/重载预热

MMCE 在 `CommonProxy.loadComplete` 调用：

`CompletableFuture.runAsync(() -> BlockArrayCache.buildCache(MachineRegistry.getLoadedMachines()))`

`buildCache()` 会清空缓存并 parallelStream 遍历所有已加载机器：

- 构建机器主 pattern 的四向水平旋转缓存。
- 构建 multi-block modifier replacement pattern 缓存。
- 构建 dynamic pattern 的 start/end pattern 缓存。
- 每次旋转后调用 `flushTileBlocksCache()`。

这使运行期 `BlockArrayCache.getBlockArrayCache(pattern, facing)` 基本是 O(1) 查表，而不是控制器首次检测时再旋转。

### Tile block 缓存

MMCE 的 `BlockArray` 维护 `tileBlocksArray`，`BlockInformation` 记录 `hasTileEntity` 和 `hasStateMachineComponent`。匹配、上色、组件更新等路径可以只遍历 tile/component 相关坐标，而不是每次对整个 pattern 做二次筛选。

示例：`TileMultiblockMachineController.distributeCasingColor()` 使用 `foundPattern.getTileBlocksArray().keySet()` 遍历可上色组件位置。

### 控制器形成后复用

MMCE 控制器持有：

- `foundMachine`
- `foundPattern`
- `foundReplacements`
- `controllerRotation`
- `foundDynamicPatterns`

结构检查时优先验证当前 found pattern 是否仍匹配当前 facing，而不是每 tick 从所有机器开始扫描。只有缓存失效或未 formed 时才 fallback 到候选机器匹配。

`matchesRotation()` 还会先检查 `world.isAreaLoaded(pattern.getPatternBoundingBox(getPos()))`，如果结构区域未加载则直接返回 false，避免跨未加载 chunk 读方块造成异常或误判。

### DynamicPattern

MMCE 支持动态长度结构。`DynamicPattern.matches()` 使用预旋转的 start/end pattern，从起始 offset 开始沿 `structureSizeOffset` 扩展匹配，返回最大匹配 size 和匹配方向。控制器形成时把匹配到的动态结构状态保存为 `DynamicPattern.Status`，后续复查会确认这些动态结构仍然匹配。

动态结构性能优化点：

- start/end pattern 也进入 `BlockArrayCache`。
- offset 会按 controller facing 旋转。
- 返回最大 size，避免每次重新构造完整 expanded pattern。
- formed 后可通过 `addDynamicPatternToBlockArray()` 把动态段合并到 found pattern，便于组件扫描和显示。

### 结构检测节流

MMCE 控制器维护 `structureCheckCounter`，并通过 `canCheckStructure()` / `doStructureCheck()` 控制检测频率。结构检测不只是匹配 pattern，还会更新组件、触发结构事件、处理 chunk unloaded 状态，因此它被明确放入控制器生命周期，而不是散落在 recipe tick 中。

## 当前 MMCR 状态

### 已具备基础

当前项目已经实现：

- `BlockArrayCache.get(pattern, facing)` 基于 `ConcurrentHashMap<Key, BlockArray>` 懒加载旋转。
- `BlockRotator.rotateSouthTo(...)` 支持 south-facing 约定。
- `StructureMatcher.matchesRotated(...)` 对 rotated pattern 做直接匹配。
- `MachineControllerBlockEntity` 持有 `foundMachine`、`foundPattern`、`controllerFacing`。
- formed 后如果 `foundPattern` 和 facing 未变，会先复查 cached pattern。
- `updateComponents()` 根据 `foundPattern.pattern().keySet()` 查找 `MachineComponentTile`。
- `PortRequirementSpec` 已在形成时基于 rotated pattern 统计端口。

### 主要差距

与 MMCE 完整优化相比，当前项目还有这些性能缺口：

- `BlockArrayCache` 是懒加载，不会在机器注册/重载后预热所有 pattern。
- cache key 使用 `BlockArray` record 本身，pattern 内容较大时 key equality/hash 成本可能高于 uid 查表。
- 控制器每 tick 都调用 `checkStructure()`，没有明确结构检测节流或脏标记。
- cached pattern 复查失败后会立即 fallback 到当前 machine 和全 registry 扫描。
- `StructureMatcher` 每次遍历完整 pattern，没有 tile/component 子集缓存。
- `updateComponents()` 每次遍历完整 pattern key set，再读取 block entity。
- 端口计数每次形成/复查都遍历完整 pattern。
- 没有 chunk/area loaded guard。
- 没有动态结构缓存和扩展结构状态。
- 没有结构版本号供 recipe search、component context、GUI 状态判断陈旧。

## 迁移原则

### 保持 south-facing 约定

当前项目已经以 south-facing 作为原始 pattern 方向，`BlockRotator.rotateSouthTo()` 是运行期旋转基础。迁移 MMCE 缓存时不要改成 1.12.2 的 north-facing 约定，否则会影响现有导出工具和机器定义。

### 优先预计算不可变内容

机器 pattern、旋转结果、端口位置、组件候选位置、bounding box 都是机器定义派生数据，应在机器注册/重载后计算。控制器 tick 只消费这些缓存。

### 控制器只验证必要内容

formed 后的常规 tick 应优先验证：

- facing 是否没变。
- 结构区域是否加载。
- cached rotated pattern 是否仍匹配。
- 端口要求是否仍满足。

只有这些失败时才重置并进入候选扫描。

### 缓存失效必须显式

结构缓存与 recipe search/context pool 都依赖结构状态。每次结构形成、结构破坏、机器切换、方向变化、reload 都应递增 structure/cache version，让旧任务和旧 context 可被安全丢弃。

## 目标架构

### `CompiledMachinePattern`

建议新增机器级编译结果：

`src/main/java/cn/howxu/mmcr/api/machine/CompiledMachinePattern.java`

字段建议：

- `Machine machine`
- `EnumMap<Direction, BlockArray> rotatedPatterns`
- `EnumMap<Direction, BoundingBox> boundingBoxes`
- `EnumMap<Direction, List<BlockPos>> componentPositions`
- `EnumMap<Direction, List<BlockPos>> portPositions`
- `EnumMap<Direction, Map<String, Integer>> staticPortCounts` 或可快速统计的 port classifier
- 后续动态结构 compiled 信息

这样控制器形成结构时拿到的是已编译对象，不需要反复扫描 `BlockArray.pattern()`。

坐标约定必须统一：`rotatedPatterns`、`componentPositions`、`portPositions`、`boundingBoxes` 全部使用“已按 facing 旋转、相对 controller 原点”的坐标；世界坐标只在控制器使用 `controllerPos.offset(relativePos)` 时生成。静态 compiled positions 包含 controller block，但 `updateComponents()` 可以按 block entity 类型过滤掉非 component。动态结构未实现前不进入这些列表；实现后应把 matched dynamic 展开段追加到 formed 运行态，而不是污染静态 compiled cache。

### `BlockArrayCache` 改造

当前 `BlockArrayCache` 可以分两步迁移：

1. 保留现有 API `get(BlockArray, Direction)`，内部继续兼容懒加载。
2. 增加 `buildCache(Collection<Machine>)` 和 `clear()`，在机器注册/重载后预热。

Phase 1/2 不改 `BlockArray` record 形态，避免扩大 API 变更。uid 化或 wrapper key 属于后续独立优化；首批迁移只要求机器注册阶段保存 compiled result，不让控制器直接用 `BlockArray` 做高频 key 查询。

### `StructureMatcher` 改造

保留：

- `matches(pattern, level, ctrlPos, ctrlFacing)`
- `matchesRotated(pattern, level, ctrlPos)`

新增：

- `matchesCompiled(CompiledMachinePattern compiled, Direction facing, Level level, BlockPos ctrlPos)`
- `firstMismatch(...)` 用于调试工具或日志。
- `isAreaLoaded(...)` guard。

匹配仍按完整 pattern 遍历，因为完整验证不可避免；但组件扫描、端口计数、bounding box 不应再复用完整遍历。

`isAreaLoaded(...)` 在 26.1.2 下应使用 chunk 坐标范围检查，而不是读取未加载 block。未加载区域的初始语义应与 MMCE 一致：返回“不通过本次结构检查”并设置 chunk unloaded / missing structure 状态，但不要把它当作普通 mismatch 继续全机器扫描；是否立即 reset formed 状态由控制器策略决定，第一阶段建议保守暂停 recipe 并保持可恢复状态。

### 控制器结构状态

`MachineControllerBlockEntity` 增加：

- `private CompiledMachinePattern foundCompiledPattern;`
- `private long structureVersion;`
- `private int structureCheckCounter;`
- `private boolean structureDirty;`
- `private PortRequirementSpec.PortCounts cachedPortCounts;`
- `private List<ProcessingComponent> cachedComponents;` 或继续使用现有 `components` 但只从 compiled component positions 更新。

`structureVersion` 递增时机：

- 新结构形成。
- 结构 reset。
- facing 改变。
- reload 清理缓存。
- 组件列表变化。

### 检测节流与脏标记

建议引入 `shouldCheckStructure()`：

- `structureDirty == true` 时立即检查。
- 未 formed 时按较短间隔检查，例如 10 tick。
- formed 时按较长间隔复查，例如 20 tick。
- 红石、邻居更新、方块变化事件命中 bounding box 时设置 dirty。

如果暂时没有可靠的 world block update 监听，可先实现 tick 间隔节流；后续再接入事件脏标记。

节流安全语义必须明确：formed 结构在节流间隔内允许 active recipe 继续 tick，因此默认 formed 复查间隔不应过长，第一阶段建议不超过 20 tick。必须立即设置 dirty 的事件包括 controller facing 改变、控制器 block state 改变、结构 bounding box 内方块变化、chunk unload/reload、机器 reload、端口 block entity 变化。没有 dirty 事件时，破坏 required block 最多延迟一个 formed 复查间隔才 reset，这是可接受但必须可配置/可调小的行为。

### 端口与组件预索引

形成结构时：

- 从 compiled `portPositions` 读取对应世界坐标，只检查这些位置是否是 `IOPortBlockEntity`。
- 从 compiled `componentPositions` 读取对应世界坐标，只检查这些位置是否是 `MachineComponentTile`。
- 不再遍历完整 pattern 查端口和组件。

这等价于 MMCE 的 `tileBlocksArray` 思路，但保留当前项目的 `MachineComponentTile` / `IOPortBlockEntity` 体系。

分类来源采用“pattern predicate 派生 + runtime fallback”两层：编译阶段能从 `BlockPredicate.OfBlock` 或机器定义 metadata 判断的端口/component 先加入 positions；无法静态判断的 predicate 进入 fallback positions。形成结构时只遍历 `componentPositions + fallbackPositions`，并以实际 `BlockEntity instanceof MachineComponentTile` / `IOPortBlockEntity` 为权威结果。这样不会因为 AnyOf、tag、状态 predicate 无法静态分类而漏组件。

### DynamicPattern 扩展位

当前项目还没有 MMCE 动态长度结构。为了“完全拿过来”，文档层面应预留但不强行第一阶段实现：

- `DynamicPatternSpec`：start pattern、optional end pattern、min/max size、offset direction、allowed faces。
- `CompiledDynamicPattern`：每个 facing 的 start/end rotated pattern 和 offset。
- `DynamicPatternMatch`：name、size、matchFacing。
- formed 后把 dynamic pattern 展开为运行期 `foundPattern` 或单独保存 expanded component positions。

第一阶段先完成静态结构缓存；第二阶段再引入动态结构，避免同时改变机器定义格式和检测核心。

## 迁移步骤

迁移顺序必须先做静态结构缓存，再做 recipe 联动和节流。动态结构是 MMCE 的完整能力之一，但不是第一批性能收益的前置条件。

### Phase 1：缓存预热

在机器注册完成后调用 `BlockArrayCache.buildCache(MachineRegistry.getAll().values())` 或等价 `MachinePatternCompiler.compileAll(...)`。

验收：

- 所有已注册机器的水平 facing rotated pattern 都被预构建。
- `/mmcr reload` 清理并重建缓存。
- `BlockArrayCache.get()` 对未预热 pattern 仍可懒加载，避免测试或外部 API 崩溃。

### Phase 2：Compiled Pattern

引入 `CompiledMachinePattern`，缓存 rotated pattern、bounding box、component positions、port positions。

验收：

- 控制器形成结构后保存 compiled pattern。
- `updateComponents()` 只遍历 component positions。
- `countPorts()` 只遍历 port positions。
- 默认机器形成和端口限制行为不变。

### Phase 3：结构版本与 recipe 联动

给控制器增加 `structureVersion`，并让 recipe search/context 使用版本校验。

验收：

- 结构破坏后旧 recipe search result 被丢弃。
- 结构重形成后 context 不复用旧组件列表。
- Jade/GUI 状态不会显示旧 active recipe。

### Phase 4：检测节流

引入 `shouldCheckStructure()`，先基于 tick 间隔实现，再接入 dirty 标记。

验收：

- formed 结构不再每 tick 完整匹配。
- 未 formed 控制器仍能在合理时间内识别新建结构。
- 手动破坏结构后最多在配置间隔内 reset。
- 红石暂停不阻止必要结构复查。
- chunk unload 时不会继续提交 recipe finish；恢复加载后能重新确认结构。

### Phase 5：动态结构缓存

迁移 MMCE `DynamicPattern` 思路，并适配当前 south-facing 约定。

验收：

- 支持 min/max size 动态段。
- start/end pattern 进入旋转缓存。
- formed 后保存 dynamic match size。
- 组件扫描能覆盖动态展开段。

## 文件级改造清单

预计新增：

- `src/main/java/cn/howxu/mmcr/api/machine/CompiledMachinePattern.java`
- `src/main/java/cn/howxu/mmcr/api/machine/MachinePatternCompiler.java`
- `src/main/java/cn/howxu/mmcr/api/machine/StructureCheckPolicy.java`
- 后续动态结构：`DynamicPatternSpec`、`CompiledDynamicPattern`、`DynamicPatternMatch`

预计修改：

- `src/main/java/cn/howxu/mmcr/api/machine/BlockArrayCache.java`
- `src/main/java/cn/howxu/mmcr/api/machine/StructureMatcher.java`
- `src/main/java/cn/howxu/mmcr/api/machine/MachineRegistry.java`
- `src/main/java/cn/howxu/mmcr/internal/tile/MachineControllerBlockEntity.java`
- `/mmcr reload` 或机器重载入口

## 验证标准

最低验证：

- `./gradlew compileJava --no-daemon`
- 默认机器结构仍能形成。
- 旋转方向 north/east/south/west 均能正确匹配。
- 端口 requirement failure 仍能正确显示。
- formed 后破坏任一 required block，控制器会 reset。
- 结构恢复后控制器能重新 formed。

建议补充纯逻辑测试：

- `BlockArrayCache.buildCache()` 预热后四向 pattern 与懒加载结果一致。
- `CompiledMachinePattern` component/port positions 与原 pattern 扫描一致。
- structureVersion 在 formed/reset/reload 时递增。
- `StructureMatcher.firstMismatch()` 返回正确相对坐标。

## 风险与约束

- 不要改变现有 south-facing 原始方向约定。
- 不要把结构检测完全交给异步线程读取 live world。
- 不要只依赖定时节流而没有 dirty/reload 失效路径。
- 不要在 compiled cache 中保存 live `BlockEntity`，只保存相对坐标和不可变 pattern 派生数据。
- 不要把动态结构和静态结构缓存一次性混在首个实现阶段，避免调试困难。

## 完成定义

结构检测优化迁移完成后，应满足：

- 机器注册/重载后能预构建结构旋转缓存。
- 控制器 formed 后复用 compiled pattern、component positions、port positions。
- 高频 tick 不再重复旋转 pattern 或重复全量扫描组件/端口。
- 结构状态有版本号，可保护 recipe search 和 context 不使用旧结果。
- 后续动态长度结构可按 MMCE `DynamicPattern` 思路接入，而不破坏静态结构路径。
