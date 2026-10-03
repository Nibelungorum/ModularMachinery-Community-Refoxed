package cn.howxu.mmcr.compat.create.loaded;

import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;

/** Axis-only casing fallback; runtime models supply the dynamic appearance.
 * @author howxu <dev@howxu.cn>
 */
public final class StressInterfaceAppearance {
    private StressInterfaceAppearance() {}

    public static ResourceLocation model(BlockState state) {
        return ResourceLocation.parse("create:block/encased_chain_drive/single");
    }

    public static ResourceLocation texture(BlockState state) {
        return ResourceLocation.parse("create:block/encased_chain_drive");
    }

    public static ResourceLocation itemModel() {
        return ResourceLocation.parse("create:block/encased_chain_drive/item");
    }

    public static int xRotation(BlockState state) {
        return state.getValue(BlockStateProperties.AXIS) == Axis.Y ? 90 : 0;
    }

    public static int yRotation(BlockState state) {
        return state.getValue(BlockStateProperties.AXIS) == Axis.X ? 90 : 0;
    }

    public static void generateModels(BlockStateProvider provider, Block block, String name) {
        provider.getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(provider.models().getExistingFile(model(state)))
                .rotationX(xRotation(state)).rotationY(yRotation(state)).build());
        provider.itemModels().withExistingParent(name, itemModel());
    }
}
