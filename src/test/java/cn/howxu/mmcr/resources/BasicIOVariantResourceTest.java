package cn.howxu.mmcr.resources;

import cn.howxu.mmcr.datagen.Translations;
import cn.howxu.mmcr.registry.PortKinds;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies every port kind registered by {@link PortKinds#all()} has no
 * generated blockstate or item model, and has
 * {@code block.mmcr.<id>} / {@code container.mmcr.<id>} translation keys
 * in both {@code en_us} and {@code zh_cn}.
 *
 * @author howxu <dev@howxu.cn>
 */
class BasicIOVariantResourceTest {

    private static final Path MAIN_ASSETS = Path.of("src/main/resources/assets/mmcr");
    private static final Path GENERATED_ASSETS = Path.of("src/generated/resources/assets/mmcr");
    @Test
    void everyPortKindKeepsTranslationsWithoutGeneratedModels() {
        for (var kind : PortKinds.all()) {
            assertThat(assetExists("blockstates/" + kind.id() + ".json"))
                    .as(kind.id() + " blockstate")
                    .isFalse();
            assertThat(assetExists("models/item/" + kind.id() + ".json"))
                    .as(kind.id() + " item model")
                    .isFalse();
            assertThat(Translations.ALL.get("en_us"))
                    .as(kind.id() + " en_us translations")
                    .containsKeys("block.mmcr." + kind.id(), "container.mmcr." + kind.id());
            assertThat(Translations.ALL.get("zh_cn"))
                    .as(kind.id() + " zh_cn translations")
                    .containsKeys("block.mmcr." + kind.id(), "container.mmcr." + kind.id());
        }
    }

    private static boolean assetExists(String path) {
        return Files.exists(MAIN_ASSETS.resolve(path)) || Files.exists(GENERATED_ASSETS.resolve(path));
    }
}
