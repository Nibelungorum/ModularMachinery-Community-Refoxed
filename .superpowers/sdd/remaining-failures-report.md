# Remaining Failures Report

## Root Cause

- GameTest startup originally executed `registerPublicApiLifecycle()` from the mod constructor. `GameTestRegistry.registerMachineStructures()` then dereferenced unbound `ModBlocks` holders, producing `NullPointerException: Trying to access unbound value: mmcr:basic_casing`.
- Test-only machine definitions had been moved out of the pre-`ModBlocks` registration phase. They now contribute suppliers before block registration, while their public structure declarations are composed after registries are bound.
- Sound registration still had a pending-request side effect. The request queue and mod-bus listener were removed; sound identifiers now require an existing registry entry. Nullable sound fields remain valid.
- The remaining unit failures are lifecycle/bootstrap assumptions in `MachineBuilderJSTest`, `PluginBindingTest`, `ModuleRecipeBuilderJSTest`, `ReloadCommandTest`, `DynamicModuleReloadValidationTest`, `RuntimeContentSnapshotTest`, and `MachineSoundManagerTest`. The final unit run reports 8 failures.
- The remaining GameTest failures are runtime/bootstrap behavior after the startup crash was removed: 14 required tests fail, chiefly structure timing, missing machine objects for expandable test machines, and terminal tests receiving empty lookup results.

## Modified Files

- `src/main/java/cn/howxu/mmcr/MMCR.java`
- `src/main/java/cn/howxu/mmcr/api/machine/MachineDefinitions.java`
- `src/main/java/cn/howxu/mmcr/api/machine/MachineRegistration.java`
- `src/main/java/cn/howxu/mmcr/api/sound/MachineSoundRegistry.java`
- `src/main/java/cn/howxu/mmcr/compat/kubejs/MachineBuilderJS.java`
- `src/main/java/cn/howxu/mmcr/internal/api/PublicApiBootstrap.java`
- `src/gametest/java/cn/howxu/mmcr/GameTestRegistry.java`

## Verification

- `./gradlew compileJava --no-daemon`: PASS.
- Focused public API tests: PASS before final lifecycle follow-up.
- `./gradlew test --no-daemon`: FAIL, `1048 tests completed, 8 failed`; output captured at `/home/howxu/.local/share/rtk/tee/1787230008_gradlew_test.log`.
- `./gradlew runGameTestServer --no-daemon`: FAIL, `14 required tests failed`; output captured at `/home/howxu/.local/share/rtk/tee/1787230044_gradlew_test.log`.

## Residual Risk

- The branch is not fully green. The remaining failures require a broader reconciliation of KubeJS test bootstrap and runtime dynamic-content initialization, beyond the sound and initial GameTest holder-lifecycle fixes made here.
