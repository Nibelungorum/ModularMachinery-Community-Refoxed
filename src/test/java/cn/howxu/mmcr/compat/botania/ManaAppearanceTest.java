package cn.howxu.mmcr.compat.botania;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.botania.client.ManaPortAppearance;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonObject;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies actual model builders preserve the pool geometry and inherited item transforms.
 * @author howxu <dev@howxu.cn>
 */
class ManaAppearanceTest {
    @BeforeAll static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void bothDirectionsGeneratePoolShapeWithFourNativeTextureSlotsAndMatchingItemParent() {
        for (String name : List.of(BotaniaManaIds.INPUT, BotaniaManaIds.OUTPUT)) {
            Block block = registeredBlock(name);
            // Model serialization does not need a live optional-mod resource manager.
            var files = new ExistingFileHelper(List.of(), Set.of(), false, null, null);
            var provider = new BlockStateProvider(new PackOutput(Path.of(".")), MMCR.MODID, files) {
                @Override protected void registerStatesAndModels() {}
            };
            ManaPortAppearance.generateModels(provider, block, name);
            JsonObject model = provider.models().getBuilder(name).toJson();
            assertThat(model.get("parent").getAsString()).isEqualTo("botania:block/shapes/mana_pool");
            assertThat(model.has("elements")).isFalse();
            JsonObject textures = model.getAsJsonObject("textures");
            assertThat(textures.keySet()).containsExactlyInAnyOrder("bottom", "inside", "side", "top");
            ManaPortAppearance.wallTextures().forEach((slot, texture) ->
                    assertThat(textures.get(slot).getAsString()).isEqualTo(texture.toString()));
            assertThat(provider.itemModels().getBuilder(name).toJson().get("parent").getAsString())
                    .isEqualTo("mmcr:block/" + name);
            assertThat(provider.getVariantBuilder(block).toJson().getAsJsonObject("variants")
                    .getAsJsonObject("").get("model").getAsString()).isEqualTo("mmcr:block/" + name);
        }
    }

    private static Block registeredBlock(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mmcr_test", name);
        var registry = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        if (registry.containsKey(id)) return registry.get(id);
        registry.unfreeze();
        try {
            return Registry.register(registry, id, new Block(Blocks.IRON_BLOCK.properties()));
        } finally {
            registry.freeze();
        }
    }
}
