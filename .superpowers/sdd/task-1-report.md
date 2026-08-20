STATUS: DONE_WITH_CONCERNS

修改文件:
- src/main/java/cn/howxu/mmcr/api/publicapi/event/RegisterMachineDefinationsEvent.java
- src/main/java/cn/howxu/mmcr/api/publicapi/event/RegisterMachineStructuresEvent.java
- src/main/java/cn/howxu/mmcr/api/publicapi/event/MMCRRegisterRecipesEvent.java
- src/main/java/cn/howxu/mmcr/api/publicapi/machine/MachineStructureBuilder.java
- src/main/java/cn/howxu/mmcr/api/publicapi/package-info.java
- src/test/java/cn/howxu/mmcr/api/publicapi/PublicEventSubscribersTest.java

提交 hash: 37b52dc

测试命令及结果:
- `./gradlew test --no-daemon --tests cn.howxu.mmcr.api.publicapi.PublicEventSubscribersTest`: PASS
- `./gradlew test --no-daemon`: FAIL，1052 tests completed，1 failed；既有失败为 `PublicRecipeBuilderTest.adapts_public_recipe_values_to_internal_recipe_semantics()`，位置 `src/test/java/cn/howxu/mmcr/api/publicapi/PublicRecipeBuilderTest.java:103`。
- `./gradlew runGameTestServer --no-daemon`: FAIL，46 个 GameTest 中 4 个 required tests 失败：`mmcr:block_array_match`、`mmcr:controller_tick`、`mmcr:datapack_recipe_override`、`mmcr:e2e_distillation_tower_partial_outputs`。

遗留疑问:
- 本 Task 的精确结构事件签名要求 `MachineStructureBuilder`，但基线中不存在该类型，因此新增了最小结构阶段 Builder；后续 Builder 解耦与消费者迁移未实现。
- 新事件已提供冻结和不可变快照契约，但现有 `MMCR`/`PublicApiBootstrap` 消费链仍使用旧机器事件，待后续 Task 迁移。
- 全量单测与 GameTest 的既有失败未在本 Task 中修复。

---

STATUS: FIXED_WITH_GAMETEST_CONCERNS

修复提交 hash: `962c9e9`

修复内容:
- `MachineBuilder` 仅构建机器基础属性，移除 pattern/stage/结构要求入口。
- `MachineStructureBuilder` 独立构建主结构、extension、端口和结构要求。
- 新增 public `MachineStructureDefinition`，公共事件不再暴露 `cn.howxu.mmcr.api.machine` 结构类型；内部转换集中在 `PublicMachineAdapter`。
- 配方事件增加收集快照、重复 ID、null、freeze 后写入契约。
- 补齐定义、结构、配方的真实注册及 ID、未知机器、重复 ID、null consumer、缺少主结构、freeze 后写入测试。
- 未修改 `MMCR`/`PublicApiBootstrap` 实际发布顺序。

测试命令及完整结果:
- `./gradlew test --no-daemon --tests cn.howxu.mmcr.api.publicapi.PublicEventSubscribersTest --tests cn.howxu.mmcr.api.publicapi.PublicMachineBuilderTest --tests cn.howxu.mmcr.api.publicapi.PublicApiAdapterTest`: PASS，18 actionable tasks，3 executed，15 up-to-date。
- `./gradlew test --no-daemon`: PASS，18 actionable tasks，17 up-to-date。
- `./gradlew runGameTestServer --no-daemon`: FAIL，46 tests completed，4 required tests failed：`mmcr:block_array_match`、`mmcr:controller_tick`、`mmcr:datapack_recipe_override`、`mmcr:e2e_distillation_tower_partial_outputs`。

concerns:
- GameTest 的 4 个失败仍存在，表现为结构在 tick 0 形成、静态配方未被覆盖、蒸馏配方在 tick 0 消耗输入；本修复未改动 MMCR/PublicApiBootstrap 生命周期，Task 3 仍需处理发布接入。

---

STATUS: FIXED_WITH_EXISTING_TEST_CONCERNS

本次二次审查修复:
- `PublicEventSubscribersTest` 通过 `NeoForge.EVENT_BUS` 发布并接收定义、结构、配方三个精确事件，分别验证接收一次及注册 ID。
- 从公共 `MachineBuilder`、`MachineDefinition` 和 `PublicMachineAdapter` 移除 `expandableStructure`；扩展能力仅由结构声明决定。
- 公共 `MachineStructureDefinition` 拒绝多个 `FULL` 主结构，只允许一个 `FULL` 加后续扩展阶段。
- 恢复公共 pattern 不可变性、非法绑定和结构转换覆盖。

测试命令及结果:
- `./gradlew test --no-daemon --tests cn.howxu.mmcr.api.publicapi.PublicEventSubscribersTest --tests cn.howxu.mmcr.api.publicapi.PublicMachineBuilderTest --tests cn.howxu.mmcr.api.publicapi.PublicApiAdapterTest`: PASS。
- `./gradlew test --no-daemon`: FAIL，1039 tests completed，1 failed；`PublicRecipeBuilderTest.adapts_public_recipe_values_to_internal_recipe_semantics()`，既有问题。
- `./gradlew runGameTestServer --no-daemon`: FAIL，46 tests completed，4 required tests failed；均为 Task 3 生命周期接入 concerns：`mmcr:block_array_match`、`mmcr:controller_tick`、`mmcr:datapack_recipe_override`、`mmcr:e2e_distillation_tower_partial_outputs`。
To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/9.2.1/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build 
Reusing configuration cache.
> Task :processTestResources UP-TO-DATE
> Task :cacheVersionManifest26.1.2 UP-TO-DATE
> Task :cacheVersionExecutableServer26.1.2 UP-TO-DATE
> Task :cacheVersionExecutableClient26.1.2 UP-TO-DATE
> Task :neoFormSetup UP-TO-DATE
> Task :processResources UP-TO-DATE
> Task :neoFormListTransformLibraries UP-TO-DATE
> Task :neoFormListLibraries UP-TO-DATE
> Task :neoFormDecompile UP-TO-DATE
> Task :neoFormPatch UP-TO-DATE
> Task :neoFormPatchUserDev UP-TO-DATE
> Task :neoFormTransformSource UP-TO-DATE
> Task :neoFormRecompile UP-TO-DATE
> Task :supplyRawJarForneoFormJoined26.1.2-1 UP-TO-DATE
> Task :selectRawArtifactNg_dummy_ng.net.neoforged_neoforge_26.1.2.84 UP-TO-DATE
> Task :compileJava UP-TO-DATE
> Task :classes UP-TO-DATE
> Task :compileTestJava UP-TO-DATE
> Task :testClasses UP-TO-DATE
> Task :test UP-TO-DATE

BUILD SUCCESSFUL in 7s
18 actionable tasks: 18 up-to-date
Configuration cache entry reused.

---

STATUS: FIXED_WITH_TASK_3_CONCERNS

本次测试覆盖回退修复:
- `PublicMachineBuilderTest` 恢复机器定义 Builder 的控制器、外观、工厂线程、并行、失败动作及角色/模块语义断言。
- `PublicMachineBuilderTest` 将端口需求、端口等级、等级槽和 modifier replacement 断言迁移到 `MachineStructureBuilder`。
- `PublicApiAdapterTest` 验证完整定义和结构转换后的最终 `DynamicMachine`，并验证带结构的最终 `MachineRegistration`。
- 未恢复结构字段到 `MachineBuilder`，未增加旧 API 兼容层，未修改 Task 3 生命周期。

测试命令及结果:
- `./gradlew test --no-daemon --tests cn.howxu.mmcr.api.publicapi.PublicMachineBuilderTest --tests cn.howxu.mmcr.api.publicapi.PublicApiAdapterTest --tests cn.howxu.mmcr.api.publicapi.PublicEventSubscribersTest`: PASS，18 actionable tasks，2 executed，16 up-to-date。
- 中间一次编译失败为测试断言误用内部 `levelSlots` 的 `BlockPos` 键，已改为验证最终位置映射包含等级槽 ID；修复后同一命令通过。

提交:
- 待提交（本报告随测试覆盖修复一并提交）。

concerns:
- Task 3 生命周期接入及既有 GameTest 失败未触碰，沿用前序报告中的 concerns。

---

STATUS: FIXED

最终审查修复:
- `PublicMachineAdapter.toDynamicMachine(definition, structure)` 和带结构的 `toStartupRegistration(definition, structure)` 现在校验定义 ID 与结构 machine ID；不一致时抛出 `ApiRegistrationException`。
- `PublicApiAdapterTest` 增加两个不匹配 ID 的行为测试，分别覆盖 DynamicMachine 与 MachineRegistration 转换入口。
- `PublicRecipeBuilderTest` 在每个测试前恢复默认机器等级，隔离其他测试对全局 `MachineLevelRegistry` 的污染；保留原有适配语义断言。根因是全量执行时先前测试重置了等级注册表，适配器正确转换等级后被 `MachineRecipe` 的等级校验拒绝。
- 未修改 Task 3 生命周期接入。

测试命令及结果:
- `./gradlew test --no-daemon --tests cn.howxu.mmcr.api.publicapi.PublicRecipeBuilderTest --tests cn.howxu.mmcr.api.publicapi.PublicApiAdapterTest --tests cn.howxu.mmcr.api.publicapi.PublicMachineBuilderTest --tests cn.howxu.mmcr.api.publicapi.PublicEventSubscribersTest`: PASS。
- `./gradlew test --no-daemon`: PASS，1046 tests completed。

提交:
- 待提交。

concerns:
- 本次未运行 `runGameTestServer`；Task 3 生命周期相关 concerns 保持不变。
