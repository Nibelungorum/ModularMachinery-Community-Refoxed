# KubeJS Item Output Holder Fix

## Problem

`MachineRecipeBuilderJS.itemOutput` and `chancedItemOutput` construct their
output stacks with `Holder.direct`. These stacks reach item output buses but
cannot be encoded when the block entity inventory is saved because the item
holder has no registered key.

## Scope

Change only the two non-component KubeJS output builder methods. Component
outputs continue to use `ItemStack.CODEC` with KubeJS's world registry ops.

## Design

Construct normal and chanced outputs from the item resolved through
`BuiltInRegistries.ITEM`, using the standard `ItemStack` constructor. That
constructor retains the registry-backed item holder required by block entity
serialization.

Add a focused regression test for both builder methods. It verifies the output
item holders are bound to registry keys, preventing direct holders from entering
recipe outputs and output-bus inventories.

## Verification

Run the focused test, then run `./gradlew compileJava --no-daemon`.
