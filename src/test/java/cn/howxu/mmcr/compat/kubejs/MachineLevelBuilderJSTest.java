package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.publicapi.event.MMCRMachineStructuresEvent;
import cn.howxu.mmcr.api.publicapi.machine.ModifierDefinition;
import cn.howxu.mmcr.test.TestBootstrap;
import java.util.Set;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MachineLevelBuilderJSTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void beginRegistration() {
        MMCRMachineStructuresEvent.resetCollector();
        MMCRMachineStructuresEvent.prepare(Set.of());
    }

    @Test
    void startup_builders_register_type_and_level_with_modifier_definition() {
        new LevelTypeBuilderJS("test:coil").displayName("Coils").registerObject();

        new MachineLevelBuilderJS("test:copper_coil")
                .type("test:coil")
                .priority(2)
                .state("minecraft:copper_block")
                .modifier(new ModifierDefinition(List.of(
                        MachineModifier.numeric("energy", "input", 0.75D, "multiply", false),
                        MachineModifier.numeric("parallelism", "machine", 2D, "add", false))))
                .registerObject();

        var event = MMCRMachineStructuresEvent.current();
        MachineLevelRegistry.installSnapshot(event.levelTypes().values(), event.levels().values());
        var level = MachineLevelRegistry.getLevel(ResourceLocation.parse("test:copper_coil"));
        assertThat(level.typeId()).isEqualTo(ResourceLocation.parse("test:coil"));
        assertThat(level.priority()).isEqualTo(2);
        assertThat(level.statePredicate().matches(Blocks.COPPER_BLOCK.defaultBlockState())).isTrue();
        assertThat(level.modifier()).isEqualTo(new ModifierDefinition(List.of(
                MachineModifier.numeric("energy", "input", 0.75D, "multiply", false),
                MachineModifier.numeric("parallelism", "machine", 2D, "add", false))));
    }

    @Test
    void level_type_display_name_uses_translation_key() {
        new LevelTypeBuilderJS("test:coil")
                .displayNameKey("level.test.coil")
                .registerObject();

        var displayName = MMCRMachineStructuresEvent.current().levelTypes()
                .get(ResourceLocation.parse("test:coil"))
                .displayName();

        assertThat(displayName.getContents()).isInstanceOf(TranslatableContents.class);
        assertThat(((TranslatableContents) displayName.getContents()).getKey())
                .isEqualTo("level.test.coil");
    }

    @Test
    void startup_event_creates_level_builders() {
        var event = new MMCRStartupEventJS();

        assertThat(event.createLevelType("mmcr:event_level_type")).isInstanceOf(LevelTypeBuilderJS.class);
        assertThat(event.createLevel("mmcr:event_level")).isInstanceOf(MachineLevelBuilderJS.class);
    }

    @Test
    void modifier_rejects_null_definition() {
        var builder = new MachineLevelBuilderJS("test:copper_coil");

        assertThatThrownBy(() -> builder.modifier(null))
                .isInstanceOf(NullPointerException.class);
    }
}
