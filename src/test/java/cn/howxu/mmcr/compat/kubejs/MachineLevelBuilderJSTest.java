package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.level.LevelModifier;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineLevelBuilderJSTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void beginRegistration() {
        RegisterMachineStructuresEvent.resetCollector();
        RegisterMachineStructuresEvent.prepare(java.util.Set.of());
    }

    @Test
    void startup_builders_register_type_and_level_with_modifier_defaults() {
        new LevelTypeBuilderJS("test:coil").displayName("Coils").registerObject();

        new MachineLevelBuilderJS("test:copper_coil")
                .type("test:coil")
                .priority(2)
                .state("minecraft:copper_block")
                .modifier(Map.of("energyMultiplier", 0.75D, "parallelismBonus", 2))
                .registerObject();

        var event = RegisterMachineStructuresEvent.current();
        MachineLevelRegistry.install(event.levelTypes().values(), event.levels().values());
        var level = MachineLevelRegistry.getLevel(Identifier.parse("test:copper_coil"));
        assertThat(level.typeId()).isEqualTo(Identifier.parse("test:coil"));
        assertThat(level.priority()).isEqualTo(2);
        assertThat(level.statePredicate().matches(Blocks.COPPER_BLOCK.defaultBlockState())).isTrue();
        assertThat(level.modifier()).isEqualTo(new LevelModifier(1D, 0.75D, 1D, 2, 0));
    }

    @Test
    void startup_event_creates_level_builders() {
        var event = new MMCRStartupEventJS();

        assertThat(event.createLevelType("mmcr:event_level_type")).isInstanceOf(LevelTypeBuilderJS.class);
        assertThat(event.createLevel("mmcr:event_level")).isInstanceOf(MachineLevelBuilderJS.class);
    }

    @Test
    void modifier_rejects_non_positive_multipliers() {
        var builder = new MachineLevelBuilderJS("test:copper_coil");

        assertThatThrownBy(() -> builder.modifier(Map.of("outputMultiplier", 0D)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outputMultiplier");
    }
}
