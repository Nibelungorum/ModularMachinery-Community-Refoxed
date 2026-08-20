# Task 1 and Task 2 Report

## Files

- Deleted `src/test/java/org/nibelungorum/PublicBuiltinDefinitionsTest.java`.
- Updated `src/test/java/cn/howxu/mmcr/test/TestBootstrap.java` to remove the four default NeoForge event listeners and default machine controller bindings, use `PublicBuiltinRuntime`, and bind water fluid components required by public recipe construction.
- Updated `src/test/java/cn/howxu/mmcr/api/machine/MachineStructureFamilyTest.java` to use the `test_cube` controller.
- Updated `src/test/java/cn/howxu/mmcr/internal/tile/MachinePortAppearanceTest.java` to use the `test_cube` controller.
- Updated `src/test/java/cn/howxu/mmcr/client/preview/StructurePreviewSchemaFactoryTest.java` to use the `test_cube` controller.

## Commands and Results

- `./gradlew compileTestJava --no-daemon`: passed. `BUILD SUCCESSFUL in 13s`.
- `./gradlew test --tests cn.howxu.mmcr.api.machine.MachineStructureFamilyTest --tests cn.howxu.mmcr.internal.tile.MachinePortAppearanceTest --tests cn.howxu.mmcr.client.preview.StructurePreviewSchemaFactoryTest --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --no-daemon`: failed. 141 tests completed, 139 passed, 2 failed. The failures are `built_in_blast_furnace_forms_with_required_ports` at line 1747 and `built_in_blast_furnace_forms_when_top_factory_slot_is_casing` at line 1767.
- `./gradlew test --tests cn.howxu.mmcr.api.publicapi.PublicEventSubscribersTest --no-daemon`: failed. 4 tests completed, 4 failed. The tests expect the removed `TestBootstrap` NeoForge subscriber registrations.

## Commit

- `4c48011 test: bootstrap public builtin runtime`.

## Concerns

- The two blast furnace formation tests still fail because their existing fixture does not form against the current public built-in declaration/port layout. They were not changed because the requested fixture changes were limited to the three named files.
- `PublicEventSubscribersTest` is incompatible with the requested removal of the four default event listeners and remains failing.

## Failure Follow-up

### Root Cause

- `NeoForge.EVENT_BUS` is constructed without being started. The removed default-content subscribers had previously caused the test bus to be initialized indirectly; after Task 1/2 removed them, listeners added by `PublicEventSubscribersTest` were not dispatched.
- The two blast furnace positive-formation tests depended on the default `blast_furnace` controller binding removed by Task 1/2. Their fixture constructed a synthetic controller block with the default machine's structure, so they no longer verified a supported isolated test fixture.

### Review Follow-up

#### Changes

- Kept the single `NeoForge.EVENT_BUS.start()` call in `TestBootstrap.bootstrap()`; no default-content listener or default blast furnace controller binding was restored.
- Restored the two positive formation paths using a self-created `test_cube` declaration with the required input, output, ludicrous energy, and factory semantics. Its controller is the test-bootstrap-bound `test_cube` controller, so the tests do not depend on default content.
- Wrapped every persistent `PublicEventSubscribersTest` listener activation in `try`/`finally`, ensuring it is disabled even when an assertion throws. The Javadoc now describes only the self-created machine/recipe event contract.

#### Commands And Results

- `./gradlew compileTestJava --no-daemon`: passed. `BUILD SUCCESSFUL in 9s`.
- `./gradlew test --tests cn.howxu.mmcr.api.publicapi.PublicEventSubscribersTest --no-daemon`: passed. `BUILD SUCCESSFUL in 11s`.
- `./gradlew test --tests cn.howxu.mmcr.api.machine.MachineStructureFamilyTest --tests cn.howxu.mmcr.internal.tile.MachinePortAppearanceTest --tests cn.howxu.mmcr.client.preview.StructurePreviewSchemaFactoryTest --tests cn.howxu.mmcr.internal.tile.MachineControllerBlockEntityTest --no-daemon`: passed. `BUILD SUCCESSFUL in 11s`.

#### Commit

- `PENDING` (replace with the follow-up commit ID after committing).

#### Concerns

- Prior revision removed the two positive blast furnace coverage paths. This follow-up restores their behavior against a self-created `test_cube` fixture rather than reintroducing default-content coupling.
