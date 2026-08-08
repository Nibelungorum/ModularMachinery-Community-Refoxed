package cn.howxu.mmcr.resources;

import cn.howxu.mmcr.datagen.Translations;
import cn.howxu.mmcr.registry.PortKinds;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies every port kind registered by {@link PortKinds#all()} has a
 * matching blockstate, block model, item model, and
 * {@code block.mmcr.<id>} / {@code container.mmcr.<id>} translation keys
 * in both {@code en_us} and {@code zh_cn}.
 *
 * @author howxu <dev@howxu.cn>
 */
class BasicIOVariantResourceTest {

    private static final Path MAIN_ASSETS = Path.of("src/main/resources/assets/mmcr");
    private static final Path GENERATED_ASSETS = Path.of("src/generated/resources/assets/mmcr");
    private static final List<String> DEFAULT_CONTROLLER_IDS = List.of(
            "blast_furnace_controller",
            "alloy_furnace_controller",
            "cracker_controller",
            "reactor_controller");

    @Test
    void everyPortKindHasBlockstateItemModelAndTranslations() {
        for (var kind : PortKinds.all()) {
            assertThat(assetExists("blockstates/" + kind.id() + ".json"))
                    .as(kind.id() + " blockstate")
                    .isTrue();
            assertThat(assetExists("models/item/" + kind.id() + ".json"))
                    .as(kind.id() + " item model")
                    .isTrue();
            assertThat(Translations.ALL.get("en_us"))
                    .as(kind.id() + " en_us translations")
                    .containsKeys("block.mmcr." + kind.id(), "container.mmcr." + kind.id());
            assertThat(Translations.ALL.get("zh_cn"))
                    .as(kind.id() + " zh_cn translations")
                    .containsKeys("block.mmcr." + kind.id(), "container.mmcr." + kind.id());
        }
    }

    @Test
    void controllerAndPortItemsUseStaticOverlayItemModels() throws Exception {
        for (String id : DEFAULT_CONTROLLER_IDS) {
            assertItemDefinitionUsesModel(id, "mmcr:item/" + id);
            assertThat(resourceText("models/item/" + id + ".json"))
                    .as(id + " item model")
                    .contains("\"parent\": \"mmcr:block/cube_all_overlay\"")
                    .contains("mmcr:block/basic_casing")
                    .contains("mmcr:block/basic_controller");
        }
        for (var kind : PortKinds.all()) {
            String id = kind.id();
            assertItemDefinitionUsesModel(id, "mmcr:item/" + id);
            assertThat(resourceText("models/item/" + id + ".json"))
                    .as(id + " item model")
                    .contains("\"parent\": \"mmcr:block/cube_all_overlay\"")
                    .contains("mmcr:block/basic_casing")
                    .contains("mmcr:block/" + overlayTextureFor(id));
        }
    }

    private static void assertItemDefinitionUsesModel(String id, String model) throws Exception {
        assertThat(resourceText("items/" + id + ".json"))
                .as(id + " item definition")
                .contains("\"model\": \"" + model + "\"");
    }

    private static String resourceText(String path) throws Exception {
        Path main = MAIN_ASSETS.resolve(path);
        if (Files.exists(main)) return Files.readString(main);
        return Files.readString(GENERATED_ASSETS.resolve(path));
    }

    private static String overlayTextureFor(String blockName) {
        if (blockName.startsWith("item_input_bus")) return overlayTexture(blockName, "item_input_bus", "overlay_inputbus");
        if (blockName.startsWith("item_output_bus")) return overlayTexture(blockName, "item_output_bus", "overlay_outputbus");
        if (blockName.startsWith("fluid_input_hatch")) return overlayTexture(blockName, "fluid_input_hatch", "overlay_fluidinputhatch");
        if (blockName.startsWith("fluid_output_hatch")) return overlayTexture(blockName, "fluid_output_hatch", "overlay_fluidoutputhatch");
        if (blockName.startsWith("energy_input_hatch")) return overlayTexture(blockName, "energy_input_hatch", "overlay_energyinputhatch");
        if (blockName.startsWith("energy_output_hatch")) return overlayTexture(blockName, "energy_output_hatch", "overlay_energyoutputhatch");
        throw new IllegalArgumentException("No overlay texture for I/O port: " + blockName);
    }

    private static String overlayTexture(String blockName, String baseName, String textureBase) {
        String tier = blockName.equals(baseName) ? "normal" : blockName.substring(baseName.length() + 1);
        return textureBase + "_" + tier;
    }

    private static boolean assetExists(String path) {
        return Files.exists(MAIN_ASSETS.resolve(path)) || Files.exists(GENERATED_ASSETS.resolve(path));
    }
}
