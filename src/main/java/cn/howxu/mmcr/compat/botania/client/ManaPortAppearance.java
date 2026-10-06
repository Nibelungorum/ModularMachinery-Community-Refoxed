package cn.howxu.mmcr.compat.botania.client;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.botania.BotaniaManaIds;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;

/** Central resource entry for the fixed pool models and mana liquid.
 * @author howxu <dev@howxu.cn>
 */
public final class ManaPortAppearance {
    private ManaPortAppearance() {}

    public static ResourceLocation model(IOType ioType) {
        return MMCR.id("block/mana_pool_" + ioType.getSerializedName());
    }

    public static ResourceLocation manaTexture() {
        return ResourceLocation.parse("botania:block/mana_water");
    }

    public static void generateModels(BlockStateProvider provider, Block block, String name) {
        IOType ioType = BotaniaManaIds.INPUT.equals(name) ? IOType.INPUT : IOType.OUTPUT;
        provider.simpleBlockWithItem(block, provider.models().getExistingFile(model(ioType)));
    }
}
