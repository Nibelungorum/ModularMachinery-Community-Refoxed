package cn.howxu.mmcr.compat.botania.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;

import java.util.Map;

/** Central resource entry for the native pool shape and liquid.
 * @author howxu <dev@howxu.cn>
 */
public final class ManaPortAppearance {
    private ManaPortAppearance() {}

    public static ResourceLocation model() {
        return ResourceLocation.parse("botania:block/mana_pool");
    }

    public static ResourceLocation itemModel() { return model(); }

    public static Map<String, ResourceLocation> wallTextures() {
        return Map.of("bottom", ResourceLocation.parse("botania:block/mana_pool_bottom"),
                "inside", ResourceLocation.parse("botania:block/mana_pool_inside"),
                "side", ResourceLocation.parse("botania:block/mana_pool_side"),
                "top", ResourceLocation.parse("botania:block/mana_pool_top"));
    }

    public static ResourceLocation manaTexture() {
        return ResourceLocation.parse("botania:block/mana_water");
    }

    public static void generateModels(BlockStateProvider provider, Block block, String name) {
        var model = provider.models().withExistingParent(name,
                model().withPath("block/shapes/mana_pool"));
        wallTextures().forEach(model::texture);
        provider.simpleBlockWithItem(block, model);
    }
}
