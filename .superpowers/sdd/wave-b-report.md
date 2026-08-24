# Wave B Report: Unify Crafting and Factory Runtime

## Final State Ownership

- `MachineControllerRuntime` owns the controller's `CraftingRuntime` and `FactoryRuntime`.
- `CraftingRuntime` owns one `ActiveMachineRecipe`, capability plans, version captures, start/tick/finish state, persistence, and failure status.
- `FactoryRuntime` aggregates per-lane `CraftingRuntime` instances and publishes `FactorySnapshot` data.
- `RecipeThread`, `MachineRecipeThread`, and `FactoryRecipeThread` retain only scheduling, shared-domain tokens, recipe-lock metadata, and event callbacks. They no longer own `RecipeCraftingContext` or concrete item/fluid/energy routes.
- `FactoryRecipeScheduler` selects candidates, limits threads, registers lane runtimes, and coordinates shared-domain scheduling.

## Runtime Migration

- Start planning uses an immutable `ControllerRuntimeSnapshot`, `CraftingContext`, `RequirementPlanner`, and `CraftingPlan`.
- Input operations are committed separately from output operations, with root transaction rollback on operation failure.
- Tick planning handles per-tick input capability operations and finish-pending transitions.
- Finish planning commits outputs before clearing the active runtime.
- Structure, capability, and modifier versions invalidate active execution through `ExecutionStatus`.
- Factory lane snapshots aggregate active parallelism and lane failures; scheduler thread limits are synchronized with `FactoryRuntime.laneLimit`.
- Active recipe persistence is stored through `CraftingRuntime` and factory lane runtimes.

## Static Verification

- Runtime old-path scan: no matches for `RecipeCraftingContext`, concrete resource route helpers, or concrete item/fluid/energy port classes under `internal/runtime`.
- Recipe adapter old-path scan: no matches for `RecipeCraftingContext`, context-pool calls, concrete resource route helpers, or concrete item/fluid/energy port classes under `internal/recipe`.
- Recipe adapter dependency scan: `CraftingPlan`, `CraftingRuntime`, and `FactoryRuntime` references are present.
- `git diff --check`: passed with no whitespace errors.
- Test and Gradle commands: intentionally not run, per Wave B constraints.
- Test sources and GameTest sources: untouched.

## Wave B Final Important Re-review Closure

- Shared start, tick, and finish requests now capture and validate `stateVersion` at coordinator resolution and through their pending/commit validators. Module connection state changes therefore discard asynchronous requests before capability work can commit.
- `CraftingRuntime.start()` and `RecipeThread` now use the requested/available lane parallelism. `recipe.maxThreads()` remains a factory lane/thread-count limit in `FactoryRuntime.availableCandidates()` and is not a single-lane parallelism cap.
- `CraftingRuntime.invalidate()` clears the active recipe and failure state before reset/failure publication. Reset paths can no longer republish a stale runtime failure after clearing the controller failure.
- The unused `FactoryRecipeScheduler` import was removed from `MachineControllerMenu`; Wave D legacy context and parallelism calculator files remain unchanged.

## Final Important Re-review Static Verification

- Shared request scan: all six production start/tick/finish requests carry state-version snapshots and suppliers; `SharedIoCoordinator` validates both structure and state versions.
- Parallelism scan: no `recipe.maxThreads()` use remains in `CraftingRuntime` or `RecipeThread`; only factory candidate lane accounting retains it.
- `git diff --check`: passed with no whitespace errors.
- Test and Gradle commands: intentionally not run, per explicit final re-review constraints.
- Test sources and other docs: untouched.

## Wave B Important Re-review Closure: Snapshot and Persistence

- `FactorySnapshot` now owns the immutable factory presentation lanes, active lane count, maximum parallelism, and pause state alongside lane execution state and failure aggregation. `MachineControllerRuntime.publishSnapshot()` captures those values at one publication point.
- Factory menu, network, Jade, and machine-state presentation paths consume the published factory snapshot. Factory ticking no longer sends a menu snapshot before the final crafting/structure/factory publication; lock and capacity mutations publish before presentation reads.
- `pendingFactoryRuntimeInput` is consumed exactly once after structure/component state restoration and before any factory snapshot, factory menu snapshot, save, or runtime tick can observe the runtime. Save retains loaded lanes even when the current structure has not yet re-formed.
- Shared version invalidation and asynchronous shared start/tick/finish failures synchronize structured `CraftingRuntime` failure into the controller failure key and final `CraftingStateSnapshot`; version invalidation no longer falls through to published IDLE state.
- Static verification for this closure: `git diff --check` and targeted `rg`/CodeGraph source review only. Tests and Gradle commands remain intentionally unrun.

## Wave B Important Re-review Closure

- Queued shared starts and shared tick/finish requests now include the redstone pause state in their final validators. A pause discards only the pending reservation/request, never the active runtime or its finish-pending state; resume therefore re-enters the existing search/tick/finish scheduling path.
- `RecipeThread` pending start/tick/finish validators use the same pause gate, so factory lanes cannot consume inputs or commit outputs from a request resolved after redstone pause.
- Finish-pending scheduling now checks `CraftingRuntime.shouldRetryFinish()` before enqueueing another finish request, including the shared-controller callback path.
- `RecipeThread.onStartSearchFailed` records the search `ExecutionStatus` in its lane `CraftingRuntime`; level failures now carry structured status data, and `FactoryRuntime` retains per-lane and global failure snapshots through its existing aggregation.

## Important Re-review Static Verification

- Runtime/recipe old-path `rg` scan: no matches for `RecipeCraftingContext`, concrete resource route helpers, or concrete item/fluid/energy port classes.
- Runtime/recipe dependency `rg` scan: `CraftingPlan`, `CraftingRuntime`, and `FactoryRuntime` remain present.
- `git diff --check`: passed with no whitespace errors.
- Test and Gradle commands: intentionally not run, per explicit re-review constraints.
- Test sources and other docs: untouched.

## Wave B Final Review Fixes

- `CraftingRuntime.finish()` now keeps the active recipe, finish plan, and retry state when output planning or its root transaction fails. Only a successful output transaction reaches the finish cleanup path; per-tick cancellation remains isolated to `waiting()`.
- Running crafting captures and compares `ControllerRuntimeSnapshot.stateVersion()`. `ComponentRuntime.replaceModuleConnectionState()` already advances this component/domain generation, so module disconnects invalidate both direct and shared tick/finish paths before another capability plan executes.
- `RecipeSearchTask.hasMoreSpecificPendingInputCandidate()` now applies the same module connection gate as the main candidate loop, so an incompatible earlier recipe cannot introduce conflict delay.
- `FactoryRuntime` now owns the `FactoryRecipeThread` lane collection, start reservations, recipe locks, lane creation/removal, idle cleanup, ticking, failure aggregation, persistence, and runtime-owned lane snapshots. `FactoryRecipeScheduler` retains only candidate filtering and lane-limit forwarding.
- `FactorySnapshot` carries the immutable presentation lanes used by controller/network consumers together with factory execution aggregates.

## Final Review Static Verification

- Runtime/recipe old-path `rg` scan: no matches for `RecipeCraftingContext`, concrete resource route helpers, or concrete item/fluid/energy port classes.
- Scheduler ownership `rg` scan: no scheduler-owned lane list, reservation map, recipe lock state, lane tick/cleanup state, or presentation adapter.
- Runtime dependency `rg` scan: `CraftingPlan`, `CraftingRuntime`, and `FactoryRuntime` remain present in the final runtime/recipe path.
- `git diff --check`: passed with no whitespace errors.
- Test and Gradle commands: intentionally not run, per explicit final review constraints.
- Test sources and other docs: untouched.

## Wave B Re-review Closure

- `RecipeSearchTask` now applies the module connection gate before reporting success, records the same `module_connection` runtime failure semantics, and continues searching later candidates instead of handing an incompatible recipe to `CraftingRuntime.start()`.
- `MachineControllerBlockEntity.factoryControllerSnapshot()` now consumes the published `ControllerRuntimeSnapshot.factory()` aggregate. `FactoryRuntime` publishes immutable lane presentation data together with runtime lane failures, progress, and locks, so network data does not re-aggregate scheduler state.
- Redstone pause/resume is represented in `CraftingStatus` and the published crafting/factory snapshots. Runtime recipes remain active while physical block active is off; resume restores the working status and physical active state without resetting recipe progress or tick counters.
- The unused `FactoryRuntime` controller reference and constructor parameter were removed; no compatibility constructor or adapter was added.

## Re-review Static Verification

- Runtime old-path `rg` scan: no matches for `RecipeCraftingContext`, concrete resource route helpers, or concrete item/fluid/energy port classes under `internal/runtime`.
- Recipe adapter old-path `rg` scan: no matches for `RecipeCraftingContext`, context-pool calls, concrete resource route helpers, or concrete item/fluid/energy port classes under `internal/recipe`.
- Runtime/presentation scan: `RecipeSearchTask` contains the module gate; `factoryControllerSnapshot()` consumes `ControllerRuntimeSnapshot.factory()` and has no scheduler aggregation calls; paused runtime transitions are explicit in `CraftingRuntime`/`FactoryRuntime`.
- `git diff --check`: passed with no whitespace errors.
- Test and Gradle commands: intentionally not run, per explicit re-review constraints.
- Test sources and GameTest sources: untouched.

## Cross-Wave Risks

- Existing tests still target the pre-Wave-B constructors and context-based APIs; they must be migrated in Wave E without restoring compatibility facades.
- Compilation and runtime behavior remain unverified because Wave B explicitly forbids Gradle, compiler, test, and GameTest commands.
- The legacy `RecipeCraftingContext` remains outside the current runtime/recipe adapter path for later migration of remaining API utilities.
- Smart-interface and level-driven behavior should receive focused Wave E runtime coverage against the new snapshot/capability-plan path.

## Wave B Review Closure

- Shared starts now keep a tokenized pending reservation through the successful `runtime.start` commit. The committer validates the active runtime before clearing pending state, publishing active state, and emitting the start continuation. Structure, capability, modifier, and resource-domain changes invalidate the reservation and clear it through both controller and coordinator discard paths.
- Shared tick and finish requests use the same invalidation cleanup and token identity. A tick that enters finish-pending keeps its reservation until the finish request resolves, so failed or discarded requests cannot strand a lane.
- `FactoryRuntime` is the only lane owner. Scheduler capacity, limit trimming, runtime registration, failure recomputation, and lane snapshots use the runtime-owned collection; scheduler metadata is synchronized to runtimes removed by limit cleanup. `FactoryRecipeLane` and the independent scheduler lane execution path were deleted.
- One cached `ControllerRuntimeSnapshot` now captures crafting and factory immutable snapshots at the same runtime publish point. Controller reads no longer aggregate the factory independently on each snapshot access.
- `CraftingRuntime` persists structure, capability, and modifier version tokens. Loading restores those exact tokens; missing or stale tokens fail through the normal version invalidation path rather than being replaced with current versions.
- Factory recipe threads reuse the same `RecipeSearchResult` conflict/start-delay decision and shared start request flow as single-recipe controllers. Search results and restart metadata carry capability and modifier versions as well as structure versions.
- Factory snapshots retain active and failed lane state, while global failure is recomputed from the current runtime-owned lane set. Removing the failed lane removes its contribution from the aggregate failure.

## Review Static Verification

- Production old-lane scan: no matches for `FactoryRecipeLane`, `FactoryRecipeScheduler.Lane`, `startLane`, or the scheduler-owned lane list.
- Production runtime/recipe scan: no matches for `RecipeCraftingContext` or concrete resource-route classes in the Wave B runtime/recipe paths.
- Version-token scan: pending starts, search results, restart metadata, and persisted crafting runtimes carry structure/capability/modifier versions.
- Snapshot scan: factory aggregation is only called from `MachineControllerRuntime` snapshot publication; controller snapshot reads return the cached immutable aggregate.
- `git diff --check`: passed with no whitespace errors.
- Test and Gradle commands: intentionally not run, per Wave B constraints.
- Test sources and GameTest sources: untouched.

## Wave B Failure Cleanup Re-review

- `syncCraftingFailure()` now clears the controller fallback when the shared crafting runtime recovers or is explicitly invalidated; `publishRuntimeState()` preserves unrelated search fallback until an explicit crafting synchronization occurs.
- `FactoryRuntime.recomputeFailure()` now publishes the aggregate failure boundary to the controller, so lane removal, lane-limit trimming, and idle-timeout removal clear stale controller fallback when no failed lane remains.
- `CraftingRuntime.invalidate(String)` now clears `consumedAtStart` and `retainedInputs` together with the other transient runtime state.

## Failure Cleanup Static Verification

- CodeGraph and source review covered shared runtime validation, factory lane removal/limit/timeout paths, controller failure publication, and both invalidate overloads.
- Targeted `rg` scans confirmed the new sync and transient-state cleanup call sites; no tests or Gradle commands were run.
- `git diff --check`: passed with no whitespace errors.
