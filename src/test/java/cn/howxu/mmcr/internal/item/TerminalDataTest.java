package cn.howxu.mmcr.internal.item;

import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
    void clear_preserves_the_active_inventory_source() {
        GlobalPos accessPoint = GlobalPos.of(Level.OVERWORLD, new BlockPos(3, 64, 5));
        TerminalData data = TerminalData.DEFAULT.withController(GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO))
                .withStage(4).withPreview(true, 12)
                .withAe2AccessPoint(accessPoint).withInventoryMode(TerminalInventoryMode.AE2);

        TerminalData cleared = data.clear();

        assertThat(cleared.controller()).isNull();
        assertThat(cleared.inventoryMode()).isEqualTo(TerminalInventoryMode.AE2);
        assertThat(cleared.ae2AccessPoint()).isEqualTo(accessPoint);
        assertThat(cleared.container()).isNull();
        assertThat(cleared.stage()).isEqualTo(TerminalData.DEFAULT.stage());
        assertThat(cleared.previewEnabled()).isFalse();
    }

    @Test
    void ae2_access_point_binding_is_retained_in_ae2_mode() {
        GlobalPos accessPoint = GlobalPos.of(Level.OVERWORLD, new BlockPos(3, 64, 5));

        TerminalData data = TerminalData.DEFAULT.withAe2AccessPoint(accessPoint)
                .withInventoryMode(TerminalInventoryMode.AE2);

        assertThat(data.ae2AccessPoint()).isEqualTo(accessPoint);
        assertThat(data.inventoryMode()).isEqualTo(TerminalInventoryMode.AE2);
    }

    @Test
    void legacy_data_without_an_ae2_access_point_still_decodes() {
        var legacyData = TerminalData.CODEC.encodeStart(JsonOps.INSTANCE, TerminalData.DEFAULT)
                .getOrThrow().getAsJsonObject();
        legacyData.remove("ae2_access_point");

        TerminalData decoded = TerminalData.CODEC.parse(JsonOps.INSTANCE, legacyData).getOrThrow();

        assertThat(decoded).isEqualTo(TerminalData.DEFAULT);
    }

    @ParameterizedTest
    @EnumSource(TerminalInventoryMode.class)
    void switching_inventory_modes_preserves_bindings_and_other_settings(TerminalInventoryMode initialMode) {
        TerminalData data = TerminalData.DEFAULT.withInventoryMode(initialMode)
                .withController(GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO))
                .withContainer(GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 64, 1)))
                .withAe2AccessPoint(GlobalPos.of(Level.OVERWORLD, new BlockPos(2, 64, 2)))
                .withSelectedLevel(Identifier.parse("test:coils"), Identifier.parse("test:iron"))
                .withStage(2).withPreview(true, -1);

        for (TerminalInventoryMode mode : TerminalInventoryMode.values()) {
            TerminalData switched = data.withInventoryMode(mode);

            assertThat(switched.inventoryMode()).isEqualTo(mode);
            assertThat(switched).usingRecursiveComparison().ignoringFields("inventoryMode").isEqualTo(data);
            var encoded = TerminalData.CODEC.encodeStart(JsonOps.INSTANCE, switched).getOrThrow();
            TerminalData decoded = TerminalData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
            assertThat(decoded).isEqualTo(switched);
            assertThat(decoded.withInventoryMode(initialMode)).isEqualTo(data);
        }
    }
}
