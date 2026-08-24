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
