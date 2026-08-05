# 多方块检测工具设计

## 背景

当前项目已提供 `BlockArray`、`BlockPredicate`、机器注册、`/mmcr build` 命令和调试扳手。这个功能新增一个开发/调试用物品，用来在游戏内选定一个控制器方块和一个区域，并将区域导出为可直接粘贴使用的多方块结构 API 代码。

本设计参考 MMCE 的 `BlockArrayBuilder` 思路：按相对坐标逐格描述结构，不依赖固定尺寸的符号化 pattern。当前项目导出的目标 API 使用 `BlockArray` 与 `BlockPredicate.OfBlock`。

## 用户流程

1. 玩家拿着“多方块检测工具”。
2. 对一个方块按中键，记录该方块为控制器方块，同时记录被点击面为控制器正面。
3. 对一个方块右键，记录第一坐标点。
4. 对一个方块 Shift+右键，记录第二坐标点。
5. 右键与 Shift+右键可重复执行，新选择覆盖旧选择，便于修正点错。
6. Shift+右键空气时，清空工具上已有的全部选择数据。
7. 执行 `/mmcr export`，导出当前选定区域。

第一坐标点和第二坐标点互相独立；它们共同定义一个包含端点的长方体区域。右键和 Shift+右键点到的方块也被纳入结构。

## 数据模型

工具只把以下数据作为 Item 数据存储：

- 控制器坐标 `controllerPos`
- 控制器被点击面 `controllerFace`
- 第一坐标点 `firstPos`
- 第二坐标点 `secondPos`

工具不会把区域内方块内容缓存到 Item 中。导出命令执行时从服务端世界读取当前区域状态。

建议实现为一个专用数据组件记录这些字段。这样可以避免直接操作自定义 NBT，也符合新版 Minecraft / NeoForge 的 ItemStack 数据习惯。

## 交互实现

新增 `MultiblockDetectorItem`：

- `useOn` 处理右键方块。
- 普通右键记录第一坐标点。
- Shift+右键记录第二坐标点。
- 成功记录后向玩家发送简短反馈。

中键选控制器通过服务端可见的交互事件或现有 NeoForge 输入/点击事件实现，优先选择当前项目已使用的事件风格。中键记录方块坐标和 `BlockHitResult#getDirection()`。

Shift+右键空气通过 Item 的空右键逻辑处理，检测玩家正在潜行时清空数据并反馈。

所有选择操作只在服务端写入 Item 数据；客户端只负责触发交互，不维护权威状态。

## 命令设计

新增 `/mmcr export`，挂在现有 `/mmcr` 命令树下。

命令执行规则：

- 检查玩家主手和副手中恰好持有 1 个多方块检测工具。
- 如果持有 0 个或 2 个及以上，命令失败并提示玩家。
- 校验工具数据完整：控制器坐标、控制器正面、第一点、第二点必须存在。
- 校验选择区域包含控制器；如果不包含，命令失败，避免导出不可用结构。

## 导出坐标

导出以控制器为原点 `(0, 0, 0)`。

控制器被点击面表示控制器正面。导出时将世界坐标相对控制器的偏移旋转到 API 默认方向，默认方向采用当前 `BlockRotator` / `StructureMatcher` 使用的 south-facing 约定。这样生成的代码能作为机器定义的原始 pattern，并由现有运行期旋转逻辑按控制器朝向匹配。

区域内每个非空气方块都会导出。空气方块不导出，因此不会生成 `BlockPredicate.Air`。

## 导出格式

按 MMCE 风格逐坐标生成代码，不使用符号化 `BlockArray.builder().pattern(...)`。

输出示例结构：

```java
Map<BlockPos, BlockPredicate> blocks = new LinkedHashMap<>();
blocks.put(new BlockPos(0, 0, 0), new BlockPredicate.OfBlock(controller));
blocks.put(new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(casing));
blocks.put(new BlockPos(-1, 0, 0), new BlockPredicate.OfBlock(ioPort));
BlockArray pattern = new BlockArray(Map.copyOf(blocks));
```

为保证代码可直接使用，导出文本包含必要 import、局部变量声明和 block id 注释。实际方块引用优先生成 registry lookup 形式，避免凭空假设调用方已有 `casing`、`controller`、`ioPort` 变量。

同一种 `Block` 可以复用同一个局部变量，减少重复 registry lookup；不同坐标仍逐行 `blocks.put(...)`，保留 MMCE 逐坐标风格。

## 异步计算

`/mmcr export` 不在游戏主线程执行完整计算。

流程：

1. 命令在服务端线程读取选择数据并创建导出任务。
2. 服务端线程按区域扫描当前 `BlockState`，生成轻量快照：相对坐标、Block registry id、是否空气。
3. 快照完成后提交到专用单线程 executor。
4. executor 负责排序、旋转归一化、生成文本和写文件。
5. 完成后通过 server executor 回到主线程给玩家发送成功或失败提示。

由于 Minecraft 世界访问通常必须在服务端线程完成，异步线程不直接读取 `Level` 或 `BlockState` 对象引用，只处理不可变快照数据。

对大型结构，主线程扫描阶段会做体积上限保护，并在必要时分批调度扫描，避免单 tick 长时间阻塞。默认先采用保守批量扫描策略；如果当前 NeoForge API 对跨 tick 任务调度支持有限，则至少保证文本生成和文件写入在异步线程进行。

## 文件输出

文件输出到游戏根目录：

`yyyy-MM-dd-HH-mm-ss-多方块导出-编号.txt`

编号从 `1` 开始；如果同名文件已存在则递增。写入失败时向玩家反馈错误，并在日志中记录异常。

## 错误处理

- 未持有检测工具：命令失败。
- 同时持有多个检测工具：命令失败。
- 工具选择数据不完整：命令失败。
- 区域不包含控制器：命令失败。
- 区域过大超过保护上限：命令失败并提示范围体积。
- 文件写入异常：命令失败并记录日志。

## 资源与本地化

新增物品需要：

- 物品注册 `multiblock_detector`
- 英文/中文显示名
- 简单 item model，复用现有 item generated 模型风格

如果项目已有 datagen 覆盖这些资源，优先接入现有 datagen；否则添加最小静态资源。

## 验证

实现完成后至少运行：

- `./gradlew compileJava --no-daemon`

建议补充轻量单元测试覆盖纯逻辑：

- 数据组件序列化/反序列化
- 坐标归一化
- 导出文本生成
- 文件名冲突编号

不强制添加游戏内集成测试，除非实现过程中发现现有测试框架可以低成本覆盖交互。

## 非目标

- 不实现 GUI 预览。
- 不将区域方块内容存入 Item。
- 不自动注册机器。
- 不自动推断 `AnyOf`、标签或方块状态 predicate。
- 不导出符号化 `BlockArray.builder().pattern(...)` 格式。
