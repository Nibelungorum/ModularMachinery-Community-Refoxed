# Task 2 Report

## Status

Complete. KubeJS machine recipe builder normalization now uses the shared recipe validation boundary while preserving KubeJS component decoding, fluent builder lifecycle, transaction registration, and existing derive semantics.

## Files

- `src/main/java/cn/howxu/mmcr/api/recipe/MachineRecipeJson.java`
- `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineRecipeBuilderJS.java`
- `src/test/java/cn/howxu/mmcr/compat/kubejs/ModuleRecipeBuilderJSTest.java`
- `src/test/java/cn/howxu/mmcr/compat/kubejs/ExampleScriptCompatibilityTest.java`

## RED / Green

- RED: `./gradlew test --no-daemon --tests 'cn.howxu.mmcr.compat.kubejs.ModuleRecipeBuilderJSTest.create_object_matches_shared_json_parser_for_complete_recipe_values'`
  - Failed as expected before implementation because the KubeJS path did not use the shared parser; the first fixture failure exposed the required runtime machine registration boundary.
- Focused Green: `./gradlew test --no-daemon --tests 'cn.howxu.mmcr.compat.kubejs.*'`
  - Successful, 96 tests.
- Full tests: `./gradlew test --no-daemon`
  - Successful.
- Game tests: `./gradlew runGameTestServer --no-daemon`
  - Successful.

## Implementation

- Added a typed `MachineRecipeJson.normalize` boundary for KubeJS state that delegates final construction and validation to the shared recipe contract.
- Preserved KubeJS-only component output decoding in the active KubeJS registry context.
- Preserved output chance-derived requirements, explicit requirements, level requirements, required host order, and `deriveRequirements` behavior.
- Kept KubeJS transaction collection and duplicate handling unchanged.
- Removed the JavaScript source-text `contains` syntax scan from `ExampleScriptCompatibilityTest`; structural example behavior assertions remain.

## Self-review

- No serializer or unrelated lifecycle code was changed.
- No new Java class was added, so no new class author Javadoc was required.
- No docs outside this required report were changed.
- No `runClient` task was run.

## Concerns

- KubeJS typed builder state includes registry-backed values that cannot be safely round-tripped through a generic JSON codec without losing live tag/registry context. The typed normalization boundary is intentional and avoids that lossy conversion while sharing final construction and validation.
- Data-pack/KubeJS conflict snapshot behavior remains in the existing transaction/registry path for Task 4 to extend.

## Commit

Commit hash is recorded after the Task 2-only commit.
