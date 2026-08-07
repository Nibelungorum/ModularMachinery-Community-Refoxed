package cn.howxu.mmcr.resources;

import cn.howxu.mmcr.datagen.Translations;
import cn.howxu.mmcr.registry.PortKinds;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

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

    private static final Path ASSETS = Path.of("src/main/resources/assets/mmcr");

    @Test
    void everyPortKindHasBlockstateBlockModelItemModelAndTranslations() {
        for (var kind : PortKinds.all()) {
            assertThat(Files.exists(ASSETS.resolve("blockstates/" + kind.id() + ".json")))
                    .as(kind.id() + " blockstate")
                    .isTrue();
            assertThat(Files.exists(ASSETS.resolve("models/block/" + kind.id() + ".json")))
                    .as(kind.id() + " block model")
                    .isTrue();
            assertThat(Files.exists(ASSETS.resolve("models/item/" + kind.id() + ".json")))
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
}