package org.nibelungorum;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureFamily;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class DefaultMachinesTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void structure_of_preserves_stage_declaration_kinds() throws Exception {
        Identifier id = Identifier.parse("mmcr:stage_kind_conversion");
        MachineStructureDefinition source = new MachineStructureDefinition(id, List.of(
                MachineStructureDefinition.Declaration.full(new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.Any()))),
                MachineStructureDefinition.Declaration.extension(new BlockArray(Map.of(new BlockPos(1, 0, 0), new BlockPredicate.Any())))));
        List<MachineStructureStage> stages = MachineStructureFamily.of(source).stages();
        Machine machine = new Machine() {
            @Override public Identifier registryName() { return id; }
            @Override public BlockArray pattern() { return source.pattern(); }
            @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(id); }
            @Override public List<MachineStructureStage> structureStages() { return stages; }
        };
        Method structureOf = LegacyDefaultMachines.class.getDeclaredMethod("structureOf", Machine.class);
        structureOf.setAccessible(true);

        MachineStructureDefinition converted = (MachineStructureDefinition) structureOf.invoke(null, machine);

        assertThat(converted.declarations()).extracting(MachineStructureDefinition.Declaration::kind)
                .containsExactly(MachineStructureDefinition.Declaration.Kind.FULL,
                        MachineStructureDefinition.Declaration.Kind.EXTENSION);
    }

    @Test
    void built_in_representative_machines_are_public_definitions() {
        var definitions = DefaultMachines.definitions();

        assertThat(definitions).containsKeys(Identifier.parse("mmcr:blast_furnace"),
                Identifier.parse("mmcr:alloy_furnace"));
        assertThat(definitions.get(Identifier.parse("mmcr:blast_furnace")).id())
                .isEqualTo(Identifier.parse("mmcr:blast_furnace"));
        assertThat(definitions.get(Identifier.parse("mmcr:alloy_furnace")).structureStages()).hasSize(2);
    }
}
