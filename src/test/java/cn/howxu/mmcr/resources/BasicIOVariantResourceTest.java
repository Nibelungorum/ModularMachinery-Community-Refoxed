package cn.howxu.mmcr.resources;

import cn.howxu.mmcr.registry.PortKinds;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies every port kind registered by {@link PortKinds#all()} has a
 * matching blockstate and item model in {@code src/main/resources/assets/mmcr}.
 *
 * @author howxu <dev@howxu.cn>
 */
class BasicIOVariantResourceTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/mmcr");

    @Test
    void everyPortKindHasBlockstateAndItemModel() {
        for (var kind : PortKinds.all()) {
            assertThat(Files.exists(ASSETS.resolve("blockstates/" + kind.id() + ".json")))
                    .as(kind.id() + " blockstate")
                    .isTrue();
            assertThat(Files.exists(ASSETS.resolve("models/item/" + kind.id() + ".json")))
                    .as(kind.id() + " item model")
                    .isTrue();
        }
    }
}