# Dynamic Machine Registration Split Design

## Problem

Dynamic machines previously mixed startup-only controller block registration with reloadable structure and recipe data. That made `/reload` capable of changing machine identity after NeoForge registries had already been frozen.

## Decision

Split lifecycle into three layers:

1. Startup machine registration creates machine identity, controller block, orientation flags, modifier capability, and logical recipe family.
2. Server structure registration loads reloadable `BlockArray`, port requirements, dynamic patterns, and modifier replacement blocks.
3. Server recipe registration loads actual `MachineRecipe` entries.

## Reference Mapping

- MMCE: keep the staged idea from `preloadMachines -> loadMachines -> loadRecipeRegistry`.
- GTCEu: follow registry-time machine/block definition and keep recipes/predicates separate.

## Non-Goals

- No dynamic controller block creation during `/reload`.
- No Minecraft/NeoForge/KubeJS dependency upgrades.
- No new recipe DSL beyond the lifecycle split.
