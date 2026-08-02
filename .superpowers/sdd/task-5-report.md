# Task 5 Report: BlockArray.matches() Structure Matching

## What Changed
- Added `StructureMatcher.matches(BlockArray, Level, BlockPos, Direction)`.
- Implemented 4-way horizontal rotation so pattern `+Z` aligns to the supplied controller facing.
- Added structure matcher tests to `BlockArrayTest` covering perfect match, wrong-block rejection, and NORTH/SOUTH/EAST/WEST rotation.
- Added `LevelStub` test helper with in-memory `getBlockState(BlockPos)` behavior.

## TDD Evidence
- RED command: `./gradlew --offline test --tests "cn.howxu.mmcr.api.machine.BlockArrayTest"`
- RED result: failed during `compileTestJava` because `StructureMatcher` was missing after adapting `LevelStub` for MC 26.1.2 (`~/.local/share/rtk/tee/1785648821_gradlew_test.log`).
- GREEN command: `./gradlew --offline test --tests "cn.howxu.mmcr.api.machine.BlockArrayTest"`
- GREEN result: `BUILD SUCCESSFUL`; 8 focused tests passed.

## Final Verification
- `./gradlew --offline compileJava`
- Result: `BUILD SUCCESSFUL`.

## MC 26.1.2 API Deviations
- The brief's proxy-based `LevelStub` could not be used because `net.minecraft.world.level.Level` is an abstract class, not an interface.
- Replaced it with the smallest compiling test helper feasible for this API: an `Unsafe`-allocated minimal `Level` subclass that overrides `getBlockState(BlockPos)` and required abstract methods.
- Adjusted the brief's perfect-structure fixture origin from `(0, 0, 0)` to `(-1, 0, -1)` for `Direction.NORTH`, matching the implemented origin-based rotation semantics from the brief.

## Files Changed
- `src/main/java/cn/howxu/mmcr/api/machine/StructureMatcher.java`
- `src/test/java/cn/howxu/mmcr/LevelStub.java`
- `src/test/java/cn/howxu/mmcr/api/machine/BlockArrayTest.java`
- `.superpowers/sdd/task-5-report.md`

## Self-Review
- Scope is limited to the allowed production/test files plus the required report.
- No build files, mod metadata, `.gitignore`, or `.codegraph` files were modified by this task.
- Matcher returns `false` for empty patterns and short-circuits on first failed predicate.
- Rotation behavior is covered explicitly for all four horizontal facings.

## Staging/Commit Confirmation
- Only scoped Task 5 files were staged and committed.
