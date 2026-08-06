package cn.howxu.mmcr.datagen;

import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import com.mojang.math.Quadrant;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.core.Direction;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;

/**
 * Emits all controller blockstate variants for {@code facing=*,formed=*,active=*}.
 * The controller block declares three boolean properties; if any combination is
 * missing from the generated blockstate, Minecraft treats that state as a missing
 * model. All three properties are enumerated here and the model texture is shared,
 * so only the rotations differ between formed/active combinations.
 */
final class MachineControllerVariants {

    private static final boolean[] BOOLEANS = {false, true};

    private MachineControllerVariants() {
    }

    static PropertyDispatch<VariantMutator> full() {
        PropertyDispatch.C3<VariantMutator, Direction, Boolean, Boolean> dispatch =
                PropertyDispatch.modify(
                        MachineControllerBlock.FACING,
                        MachineControllerBlock.FORMED,
                        MachineControllerBlock.ACTIVE);

        for (Direction facing : Direction.values()) {
            VariantMutator rotation = rotationFor(facing);
            for (boolean formed : BOOLEANS) {
                for (boolean active : BOOLEANS) {
                    dispatch.select(facing, formed, active, rotation);
                }
            }
        }
        return dispatch;
    }

    private static VariantMutator rotationFor(Direction facing) {
        return switch (facing) {
            case NORTH -> v -> v;
            case EAST -> VariantMutator.Y_ROT.withValue(Quadrant.R90);
            case SOUTH -> VariantMutator.Y_ROT.withValue(Quadrant.R180);
            case WEST -> VariantMutator.Y_ROT.withValue(Quadrant.R270);
            case UP -> VariantMutator.X_ROT.withValue(Quadrant.R270);
            case DOWN -> VariantMutator.X_ROT.withValue(Quadrant.R90);
        };
    }
}
