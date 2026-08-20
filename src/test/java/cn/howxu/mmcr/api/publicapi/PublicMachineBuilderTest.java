package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.publicapi.machine.PatternBuilder;
import cn.howxu.mmcr.internal.api.PublicMachineAdapter;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the public builders keep definitions and structures separate.
 * @author howxu <dev@howxu.cn>
 */
class PublicMachineBuilderTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void machine_builder_builds_base_properties_without_structure() {
        MachineDefinition definition = MachineBuilder.machine(MMCR.id("base_machine"))
                .displayNameKey("machine.mmcr.base_machine")
                .maxParallelism(4)
                .build();

        assertThat(definition.id()).isEqualTo(MMCR.id("base_machine"));
        assertThat(definition.displayNameKey()).isEqualTo("machine.mmcr.base_machine");
        assertThat(MachineBuilder.class.getDeclaredMethods()).noneMatch(method ->
                method.getName().equals("pattern") || method.getName().equals("stage"));
    }

    @Test
    void structure_builder_owns_main_structure_and_extensions() {
        MachineStructureDefinition structure = MachineStructureBuilder.structure()
                .fullStructure(stage -> stage.pattern(pattern -> pattern.layer("F")
                        .where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F')))
                .extension(stage -> stage.pattern(pattern -> pattern.layer("C")
                        .where('C', BlockPredicate.block(Blocks.STONE)).controller('C')))
                .build(MMCR.id("tower"));

        assertThat(structure.machineId()).isEqualTo(MMCR.id("tower"));
        assertThat(structure.stages()).extracting("kind")
                .containsExactly(cn.howxu.mmcr.api.publicapi.machine.StructureStage.Kind.FULL,
                        cn.howxu.mmcr.api.publicapi.machine.StructureStage.Kind.EXTENSION);
    }

    @Test
    void structure_builder_rejects_missing_main_structure() {
        assertThatThrownBy(() -> MachineStructureBuilder.structure()
                .extension(stage -> stage.pattern(pattern -> pattern.layer("C")
                        .where('C', BlockPredicate.block(Blocks.STONE)).controller('C')))
                .build(MMCR.id("missing_main")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Main machine structure");
    }

    @Test
    void structure_builder_rejects_multiple_main_structures() {
        assertThatThrownBy(() -> MachineStructureBuilder.structure()
                .fullStructure(stage -> stage.pattern(pattern -> pattern.layer("F")
                        .where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F')))
                .fullStructure(stage -> stage.pattern(pattern -> pattern.layer("C")
                        .where('C', BlockPredicate.block(Blocks.STONE)).controller('C')))
                .build(MMCR.id("multiple_main")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only one main machine structure");
    }

    @Test
    void pattern_builder_preserves_immutable_structure_values_and_rejects_invalid_bindings() {
        BlockPredicate casing = BlockPredicate.block(Blocks.STONE);
        BlockPredicate controller = BlockPredicate.block(Blocks.FURNACE);
        var pattern = PatternBuilder.pattern().layer("CCC", "C C", "CFC")
                .where('C', casing).where('F', controller).controller('F').build();

        assertThat(pattern.layers()).containsExactly(List.of("CCC", "C C", "CFC"));
        assertThatThrownBy(() -> pattern.layers().add(List.of("X")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> PatternBuilder.pattern().layer("CC", "CCC"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("same width");
        assertThatThrownBy(() -> PatternBuilder.pattern().layer("CF")
                .where('C', casing).controller('F').build())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Unbound");
    }

    @Test
    void structure_builder_preserves_full_then_extension_conversion() {
        var machineId = MMCR.id("structure_conversion");
        var structure = MachineStructureBuilder.structure()
                .fullStructure(stage -> stage.pattern(pattern -> pattern.layer("F")
                        .where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F')))
                .extension(stage -> stage.pattern(pattern -> pattern.layer("C")
                        .where('C', BlockPredicate.machineCoupler()).controller('C')))
                .build(machineId);

        assertThat(PublicMachineAdapter.toStructureDefinition(structure).declarations())
                .extracting(cn.howxu.mmcr.api.machine.MachineStructureDefinition.Declaration::kind)
                .containsExactly(cn.howxu.mmcr.api.machine.MachineStructureDefinition.Declaration.Kind.FULL,
                        cn.howxu.mmcr.api.machine.MachineStructureDefinition.Declaration.Kind.EXTENSION);
    }
}
