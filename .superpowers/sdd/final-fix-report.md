# Final Fix Report

## KubeJS 阶段回调绑定修复

### 根因

`MachineStructureBuilderJS` 继承 KubeJS 的 `BuilderBase`，而 `MachineStructureStageBuilderJS` 原先是普通 public class。KubeJS 专用脚本上下文因此没有按 builder 类型包装阶段对象，导致 `stage.pattern(...)` 抛出 `TypeError: Cannot find function pattern in object {}`。

### 修改

- `MachineStructureStageBuilderJS` 继承 `BuilderBase<MachineStructureDefinition.Declaration>`，沿用 KubeJS 公开 builder 的包装路径。
- 用旧 `build()` 实现 `createObject()`，保留现有 Java 单元测试和旧 Java API。
- 新增真实 Rhino callback 测试，执行 `event.createStructure(...).mainStructure(stage => stage.pattern([...]).set(...))`，并断言生成声明包含实际结构位置。
- 未修改 `example/server_scripts/structure/A_Group_Machine.js`。

### 验证

- `./gradlew test --tests '*MachineStructureBuilderJSTest' --tests '*PluginBindingTest' --no-daemon`: 通过。
- `./gradlew test --no-daemon`: 通过。
- `./gradlew runGameTestServer --no-daemon`: 通过。

### Commit

- `f6d6f4d fix: bind kubejs structure stage builder`

## 修复内容

- 为 KubeJS 结构 builder 增加 callback / 顶层结构 API 模式隔离。
- callback 阶段 API 与顶层 `pattern`、`fullStructure`、`extension(BlockArray, ...)` 等结构 API 混用时显式抛出异常，不再静默清空已有阶段声明。
- 保留 legacy `pattern(grid, keys)` 与 chained `pattern/set` 混用拒绝行为。
- 为 `mainStructure` 和 `extension` callback 增加明确的 `Objects.requireNonNull` 校验。
- 扩展 `MachineStructureBuilderJSTest`：断言 `X`、`XX`、`XXX` 三个 callback 阶段的实际 pattern 快照，并验证 main/extension 的 level slot、port、tier 和 dynamic metadata 均被保留。

## TDD

- RED：新增混用拒绝和 null callback 测试后，测试按预期失败。
- GREEN：实现结构 API 模式隔离和 callback 参数校验后，覆盖测试通过。

## 测试

- `./gradlew test --tests '*MachineStructureBuilderJSTest' --tests '*MachineBuilderJSTest' --no-daemon`: 通过。
- `./gradlew test --no-daemon`: 失败，1223 tests completed, 1 failed；失败为既有 `MachineControllerBlockEntityTest.removing_formed_controller_stops_active_recipe_without_restoring_its_block_state()` NPE（line 1695），按要求未修改。
- `./gradlew runGameTestServer --no-daemon`: 通过。

## Commit

- `1e6e4bc fix: isolate kubejs callback structure api`

## GameTest Fixture Root-Cause Fix

### 根因

`GameTestRegistry.registerMachineDefinitions` 使用 public `MachineBuilder` 注册测试机器，但当前 public builder 没有 `expandableStructure()`；该扩展开关仅存在于内部 `MachineRegistration.Builder`。因此带有 extension 阶段的 `distillation_tower_test`、`expandable_structure_stages` 和 `expandable_structure_vertical_roll` 注册为不可扩展机器，导致阶段选择失败及 machine null。

### 修改

- 仅修改 `src/gametest/java/cn/howxu/mmcr/GameTestRegistry.java`。
- 保留现有 public builder 的显示名和垂直朝向配置。
- 对上述三个实际包含 extension 阶段的 fixture `MachineDefinition` 设置 `expandableStructure=true` 后注册。
- 未修改生产运行时逻辑、结构定义或用户业务代码。

### 验证

- `./gradlew compileGametestJava --no-daemon`: 通过。
- `./gradlew runGameTestServer --no-daemon`: 通过；失败列表为空。
- `./gradlew test --no-daemon`: 失败，1223 tests completed, 1 failed。
- 完整失败列表：`MachineControllerBlockEntityTest.removing_formed_controller_stops_active_recipe_without_restoring_its_block_state()`，`MachineControllerBlockEntityTest.java:1695`，`NullPointerException`。该失败与本次 GameTest fixture 修改无关，未修改生产代码处理。
