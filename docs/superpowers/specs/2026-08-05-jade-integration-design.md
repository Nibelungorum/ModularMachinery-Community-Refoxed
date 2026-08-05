# Jade Integration Design

## Goal

Port MMCE's in-world machine status display to the current MC 26.1.2 NeoForge project through Jade.

The reference implementation to migrate from is MMCE's The One Probe integration, not a WAILA/Jade implementation. MMCE puts the user-facing machine status logic in `reference/mmce/src/main/java/hellfirepvp/modularmachinery/common/integration/theoneprobe/MMInfoProvider.java` and registers it from `ModIntegrationTOP`. The current project already has a Jade dependency in `build.gradle`, so the work should focus on implementing the Jade plugin/providers and exposing any missing read-only controller state.

## Source Findings

- `ModIntegrationTOP` registers two TOP providers and owns display config colors/booleans.
- `MMInfoProvider` is the main source to port. It shows owner, structure state, working/failure state, recipe progress, energy usage, parallelism, thread counts, and CPU/work-mode diagnostics.
- `MachineryHatchInfoProvider` only covers AE2 ME machinery hatch channel state. The current project does not yet have equivalent ME hatch classes, so this should be deferred.
- The current project has `MachineControllerBlockEntity`, `EnergyHatchBlockEntity`, `FluidHatchBlockEntity`, and `ItemBusBlockEntity`, but no Jade provider classes and no `assets/mmcr/lang/*.json` translation files yet.
- Jade's modern API uses `@WailaPlugin`, `IWailaPlugin`, `IWailaClientRegistration.registerBlockComponent`, and optionally `IWailaCommonRegistration.registerBlockDataProvider` / `registerProgress` for server-backed data.

## Recommended Approach

Use a Jade-native provider pair with a small read-only snapshot model.

This keeps MMCE's display semantics while avoiding direct TOP API translation. Jade client tooltip code should stay simple and render from data collected on the server or from synchronized block entity fields.

### Alternatives Considered

- **Direct TOP-to-Jade tooltip port:** fastest for first display, but risks reading server-only or stale client-side block entity state in `appendTooltip`.
- **Jade progress extension first:** elegant for progress bars, but more moving parts and less useful until controller state/status text is ported.
- **Recommended hybrid:** start with `IComponentProvider` plus `IServerDataProvider` and use Jade tooltip elements/text for v1. Add Jade progress extension only after basic status is correct.

## Architecture

Add a small compat package, tentatively `cn.howxu.mmcr.compat.jade`:

- `JadePlugin` implements `IWailaPlugin` and is annotated `@WailaPlugin`.
- `MachineControllerComponentProvider` implements `IComponentProvider<BlockAccessor>` and appends the tooltip rows.
- `MachineControllerDataProvider` implements `IServerDataProvider<BlockAccessor>` and serializes the controller snapshot to the Jade server data tag.
- Optional later providers can cover storage views for item/fluid/energy hatches if Jade's built-in universal providers do not show enough detail.

Register providers against the highest practical target classes:

- Client component: `MachineControllerBlock.class`.
- Server data provider: `MachineControllerBlockEntity.class`.

The provider should not create new machine runtime behavior. If data is unavailable, it should display a concise fallback rather than mutating controller state.

## Data Model

Create a private snapshot format in the Jade data provider. Suggested keys:

- `machine`: found machine id or bound machine id.
- `formed`: current formed state.
- `activeRecipe`: active recipe id, if present.
- `tick` and `totalTick`: active recipe progress.
- `parallelism` and `maxParallelism`: from `ActiveMachineRecipe`.
- `status`: derived status enum/string for `idle`, `working`, `waiting`, `unformed`, or `no_recipe`.
- `componentCount` and per-kind counts from `MachineControllerBlockEntity.getComponents()`.
- Optional v1.1: `energyInputPerTick` / `energyOutputPerTick` after recipe energy requirement access is stable.

`MachineControllerBlockEntity` already exposes most of this: `isFormed()`, `getFoundMachine()`, `getMachine()`, `getActive()`, `getActiveRecipe()`, `getTickCounter()`, and `getComponents()`. If implementation finds direct field access is missing, add narrow read-only getters rather than moving logic into the Jade provider.

## Tooltip Behavior

Initial v1 should show:

- Machine: found machine id/display name when formed, otherwise bound machine id if available.
- Structure: green formed / red not formed.
- State: working if an active recipe is ticking, waiting if active but not advancing is later exposed, idle/no recipe otherwise.
- Recipe: active recipe id when present.
- Progress: percent by default; seconds format when Jade details key is pressed, matching MMCE's sneak-details behavior conceptually.
- Parallelism: show only when `parallelism > 1` or `maxParallelism > 1`.
- Components: compact count summary for item inputs/outputs, fluid inputs/outputs, energy inputs/outputs.

Defer MMCE's factory-controller and parallel-controller lines because the current project does not yet have those block entities. Keep the design open for those providers later instead of adding placeholder classes now.

## Config And Localization

Add Jade config toggles only where they change visible behavior:

- `mmcr:machine_controller` default `true`.
- Optional `mmcr:machine_controller.components` default `true` if the component-count block feels too noisy.

Add `assets/mmcr/lang/en_us.json` and `assets/mmcr/lang/zh_cn.json` as part of the implementation because current resources do not include language files. Use `Component.translatable` in the provider instead of literal English/Chinese strings.

## Implementation Plan

1. Add the `compat.jade` package with `JadePlugin`, controller component provider, and controller data provider.
2. Register client and common providers with Jade and add the main Jade config key.
3. Serialize a minimal controller snapshot from `MachineControllerBlockEntity` without mutating controller runtime state.
4. Render the Jade tooltip rows from server data, including formed state, active recipe, progress, parallelism, and component counts.
5. Add translation files for English and Simplified Chinese Jade labels.
6. Compile with `./gradlew compileJava --no-daemon` and fix API mismatches against the bundled Jade version.
7. Do a client smoke check with Jade installed: look at unformed controller, formed idle controller, and active recipe controller.

## Out Of Scope

- Porting MMCE factory-controller multi-thread displays before the current runtime has factory controllers.
- Porting AE2 `MachineryHatchInfoProvider` before ME hatches exist.
- Rebuilding MMCE's full TOP color config system in v1.
- Replacing existing controller GUI or debug wrench behavior.
- Adding new recipe mechanics solely to make Jade display more data.

## Risks

- Jade API signatures may differ slightly from the reference snapshot and Curse dependency. Verify against `compileJava` instead of assuming exact source parity.
- Client-only tooltip providers must not read stale server-only state. Prefer Jade server data for active recipe progress and status.
- The current controller does not expose a rich `CraftingStatus` equivalent yet. v1 should derive simple status from `formed` and `active`; richer waiting/failure reasons need controller runtime support first.
- Progress can divide by zero if `totalTick` is absent or zero; render no progress bar/text in that case.

## Verification

- `./gradlew compileJava --no-daemon` passes.
- No hard dependency crash when Jade is absent, if the dependency remains optional at runtime later.
- Jade tooltip appears on machine controller blocks.
- Unformed controllers show structure missing and do not show recipe progress.
- Formed idle controllers show formed and no active recipe.
- Running controllers show active recipe id and progress from `tick / totalTick`.
- Existing JEI/KubeJS/controller code compiles without unrelated behavior changes.
