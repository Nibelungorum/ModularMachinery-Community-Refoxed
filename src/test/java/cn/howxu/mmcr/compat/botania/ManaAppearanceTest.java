package cn.howxu.mmcr.compat.botania;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.botania.client.ManaPortAppearance;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies static model references and authored texture resources.
 * @author howxu <dev@howxu.cn>
 */
class ManaAppearanceTest {
    @BeforeAll static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void bothDirectionsReferenceAuthoredModelsWithOnlyTheDefaultBlockstateAndMatchingItemParent() {
        for (String name : List.of(BotaniaManaIds.INPUT, BotaniaManaIds.OUTPUT)) {
            Block block = registeredBlock(name);
            var files = new ExistingFileHelper(List.of(Path.of("src/main/resources")), Set.of(), true, null, null);
            var provider = new BlockStateProvider(new PackOutput(Path.of(".")), MMCR.MODID, files) {
                @Override protected void registerStatesAndModels() {}
            };
            ManaPortAppearance.generateModels(provider, block, name);
            String model = BotaniaManaIds.INPUT.equals(name)
                    ? "mmcr:block/mana_pool_input" : "mmcr:block/mana_pool_output";
            assertThat(provider.itemModels().getBuilder(name).toJson().get("parent").getAsString())
                    .isEqualTo(model);
            JsonObject variants = provider.getVariantBuilder(block).toJson().getAsJsonObject("variants");
            assertThat(variants.keySet()).containsExactly("");
            assertThat(variants.getAsJsonObject("").get("model").getAsString()).isEqualTo(model);
        }
    }

    @Test
    void authoredPoolModelsReferenceExistingLocalTextureResources() throws IOException {
        Path assets = Path.of("src/main/resources/assets/mmcr");
        for (String name : List.of("mana_pool_input", "mana_pool_output")) {
            try (var reader = Files.newBufferedReader(assets.resolve("models/block/" + name + ".json"))) {
                JsonObject model = JsonParser.parseReader(reader).getAsJsonObject();
                assertThat(model.getAsJsonArray("elements")).isNotEmpty();
                for (var texture : model.getAsJsonObject("textures").entrySet()) {
                    ResourceLocation id = ResourceLocation.parse(texture.getValue().getAsString());
                    if (id.getNamespace().equals(MMCR.MODID)) {
                        assertThat(assets.resolve("textures/" + id.getPath() + ".png"))
                                .as("%s texture %s", name, texture.getKey()).exists();
                    }
                }
            }
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
