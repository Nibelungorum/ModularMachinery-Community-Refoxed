# Task 12 Report

## Implemented

- Added immutable `CustomRecipeIo` with null, object-payload, and defensive-copy validation.
- Opened codec-backed custom recipe IO through the public Java builder and KubeJS API, builder, and schema function.
- Validated custom payloads through the registered requirement/output codecs before storage.
- Converted typed public builder helpers to one `RecipeRequirement` storage path.
- Added Java and KubeJS tests for registered requirement/output payloads and invalid type errors.
- Preserved registered `OutputType` payloads as canonical `MachineOutput` values when no requirement factory exists;
  requirement-backed outputs retain their existing runtime requirement behavior.
- Added test-only custom requirement/output registrations for Java and KubeJS paths, including payload accessor copy coverage.

## Static Verification

- `git diff --check` passed.
- Gradle compilation, unit tests, and GameTests were intentionally not run per task instruction.

## Scope

- Task11 wire-critical and final residuals were not changed.
- Task13, `wiki/`, publishing, and CraftingRuntime were not changed.
