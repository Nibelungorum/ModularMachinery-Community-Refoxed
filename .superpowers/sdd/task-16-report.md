# Task 16 Report

## Scope

- Repaired all Task16 Important review findings and the approved facet-discovery Minor.
- Excluded `wiki/` from staging and commit.

## Changes

- Removed the fixed Item/Fluid/Energy native-registration map from `ModCapabilities`.
  NeoForge item, fluid, and energy providers are registered once per port kind and resolve compatible storage from each non-external binding's declared runtime facets. Generic `ExternalCapabilityAdapter` exposure handling remains unchanged.
- Moved capability registry closure into the real startup commit path. `PublicApiLifecycleTest` now completes startup, creates a runtime capability snapshot, then proves late capability registration is rejected.
- Added the missing Smart Interface persistence facet. New state is written under its named child; old root-level state remains readable.
- Added `ValueFacet.isStateless()` and audited each mutable resource/value facet in public lifecycle coverage against either this declaration or a snapshot `PersistenceFacet`.
- Made parent facet discovery precise: a declared sub-facet such as `ResourceFacet` now satisfies a `ValueFacet` query.

## Verification

- Ran `git diff --check` successfully.
- Did not run Gradle, compilation, unit tests, or GameTests as requested.
