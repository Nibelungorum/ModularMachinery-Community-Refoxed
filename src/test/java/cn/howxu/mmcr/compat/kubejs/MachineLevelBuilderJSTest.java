package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.test.TestBootstrap;
import java.util.Set;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RotatedPillarBlock;
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
        StructureRegistration.resetCollector();
        StructureRegistration.prepare(Set.of());
    }

    @Test
    void startup_builders_register_type_and_level_with_modifier_definition() {
        new LevelTypeBuilderJS("test:coil").displayName("Coils").registerObject();

        new MachineLevelBuilderJS("test:copper_coil")
                .type(ResourceLocation.parse("test:coil"))
                .priority(2)
                .state("minecraft:copper_block")
                .modifier(new ModifierDefinition(List.of(
                        MachineModifier.numeric("energy", "input", 0.75D, "multiply", false),
                        MachineModifier.numeric("parallelism", "machine", 2D, "add", false))))
                .registerObject();

        var event = StructureRegistration.current();
        event.freeze();
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

        var displayName = StructureRegistration.current().levelTypes()
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

    @Test
    void missing_block_is_reported_at_freeze_instead_of_registering_air() {
        new LevelTypeBuilderJS("test:coil").register();
        new MachineLevelBuilderJS("test:missing_coil").type("test:coil")
                .state("missing_mod:coil").register();

        assertThat(StructureRegistration.current().levels()).isEmpty();
        assertThatThrownBy(() -> StructureRegistration.current().freeze())
                .hasMessageContaining("test:missing_coil").hasMessageContaining("missing_mod:coil");
        assertThat(StructureRegistration.current().levels()).isEmpty();
    }

    @Test
    void prepare_preserves_immutable_level_reference_when_builder_is_reused() {
        new LevelTypeBuilderJS("test:coil").register();
        var builder = new MachineLevelBuilderJS("test:captured_coil").type("test:coil")
                .state("minecraft:copper_block");
        builder.register();
        builder.state("missing_mod:changed_coil");

        var snapshot = StructureRegistration.prepare(Set.of()).freeze();

        assertThat(snapshot.levels().get(ResourceLocation.parse("test:captured_coil")).statePredicate()
                .matches(Blocks.COPPER_BLOCK.defaultBlockState())).isTrue();
    }

    @Test
    void duplicate_pending_level_is_rejected_before_resolution() {
        new LevelTypeBuilderJS("test:coil").register();
        var builder = new MachineLevelBuilderJS("test:duplicate_coil").type("test:coil")
                .state("missing_mod:coil");
        builder.register();

        assertThatThrownBy(builder::register).hasMessageContaining("Duplicate machine level: test:duplicate_coil");
    }

    @Test
    void state_string_retains_explicit_properties_without_accepting_the_default_state() {
        new LevelTypeBuilderJS("test:coil").register();
        new MachineLevelBuilderJS("test:horizontal_coil").type("test:coil")
                .state("minecraft:oak_log[axis=x]").register();

        var level = StructureRegistration.current().freeze().levels().get(ResourceLocation.parse("test:horizontal_coil"));
        var horizontal = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        assertThat(level.statePredicate().matches(horizontal)).isTrue();
        assertThat(level.statePredicate().matches(Blocks.OAK_LOG.defaultBlockState())).isFalse();
    }
}
