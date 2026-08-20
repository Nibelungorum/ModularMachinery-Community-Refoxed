package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineStructureDefinition;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
}
