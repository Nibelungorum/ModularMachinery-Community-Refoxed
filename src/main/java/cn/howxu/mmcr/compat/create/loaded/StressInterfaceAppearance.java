package cn.howxu.mmcr.compat.create.loaded;

import com.simibubi.create.content.kinetics.chainDrive.ChainDriveBlock;
import com.simibubi.create.content.kinetics.chainDrive.ChainDriveBlock.Part;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;

/** Native casing model/texture entry point, retaining Create's chain-drive state rules.
 * @author howxu <dev@howxu.cn>
 */
public final class StressInterfaceAppearance {
    private StressInterfaceAppearance() {}

    public static ResourceLocation model(BlockState state) {
        Part part = state.getValue(ChainDriveBlock.PART);
        Axis axis = state.getValue(ChainDriveBlock.AXIS);
        String suffix = part == Part.NONE ? "single"
                : (part == Part.MIDDLE ? "middle" : "end") + (axis == Axis.Y ? "_vertical" : "_horizontal");
        return ResourceLocation.fromNamespaceAndPath("create", "block/encased_chain_drive/" + suffix);
    }

    public static ResourceLocation texture(BlockState state) {
        return ResourceLocation.parse("create:block/encased_chain_drive");
    }

    public static ResourceLocation itemModel() {
        return ResourceLocation.parse("create:block/encased_chain_drive/item");
    }

    public static int xRotation(BlockState state) {
        Part part = state.getValue(ChainDriveBlock.PART);
        boolean first = state.getValue(ChainDriveBlock.CONNECTED_ALONG_FIRST_COORDINATE);
        Axis axis = state.getValue(ChainDriveBlock.AXIS);
        if (part == Part.NONE) return axis == Axis.Y ? 90 : 0;
        if (axis == Axis.X) return (first ? 90 : 0) + (part == Part.START ? 180 : 0);
        if (axis == Axis.Z) return first ? 0 : part == Part.START ? 270 : 90;
        return 0;
    }

    public static int yRotation(BlockState state) {
        Part part = state.getValue(ChainDriveBlock.PART);
        boolean first = state.getValue(ChainDriveBlock.CONNECTED_ALONG_FIRST_COORDINATE);
        Axis axis = state.getValue(ChainDriveBlock.AXIS);
        if (part == Part.NONE) return axis == Axis.X ? 90 : 0;
        if (axis == Axis.Z) return first && part == Part.END ? 270 : 90;
        boolean flip = part == Part.END && !first || part == Part.START && first;
        return axis == Axis.Y ? (first ? 90 : 0) + (flip ? 180 : 0) : 0;
    }

    public static void generateModels(BlockStateProvider provider, Block block, String name) {
        provider.getVariantBuilder(block).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(provider.models().getExistingFile(model(state)))
                .rotationX(xRotation(state)).rotationY(yRotation(state)).build());
        provider.itemModels().withExistingParent(name, itemModel());
    }
}
