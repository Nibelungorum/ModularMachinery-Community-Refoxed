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
