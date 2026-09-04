package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class TerminalDataTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void defaults_are_inventory_lowest_stage_all_layers_and_preview_off() {
        assertThat(TerminalData.DEFAULT.inventoryMode()).isEqualTo(TerminalInventoryMode.INVENTORY);
        assertThat(TerminalData.DEFAULT.container()).isNull();
        assertThat(TerminalData.DEFAULT.stage()).isEqualTo(1);
        assertThat(TerminalData.DEFAULT.previewEnabled()).isFalse();
        assertThat(TerminalData.DEFAULT.previewLayer()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void level_selection_is_remembered_per_type() {
        Identifier coils = Identifier.parse("test:coils");
        Identifier voltage = Identifier.parse("test:voltage");
        TerminalData data = TerminalData.DEFAULT
                .withSelectedLevel(coils, Identifier.parse("test:iron"))
                .withSelectedLevel(voltage, Identifier.parse("test:high"));

        assertThat(data.selectedLevels()).containsEntry(coils, Identifier.parse("test:iron"))
                .containsEntry(voltage, Identifier.parse("test:high"));
    }

    @Test
    void clear_returns_the_exact_default_snapshot() {
        TerminalData data = TerminalData.DEFAULT.withStage(4).withPreview(true, 12)
                .withInventoryMode(TerminalInventoryMode.CONTAINER);

        assertThat(data.clear()).isEqualTo(TerminalData.DEFAULT);
    }
}
