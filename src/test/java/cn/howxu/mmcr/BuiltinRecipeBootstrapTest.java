package cn.howxu.mmcr;

import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltinRecipeBootstrapTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        MachineRegistry.clearForTesting();
        MachineStructureRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void bootstrap_registers_builtin_blast_furnace_recipe_explicitly() {
        TestBootstrap.registerRuntimeBuiltins();

        assertThat(RecipeRegistry.byMachineId(MMCR.id("blast_furnace")))
                .hasSize(17)
                .extracting(recipe -> recipe.id())
                .contains(MMCR.id("blast_furnace_iron_to_nugget"));
    }

    @Test
    void bootstrap_installs_default_test_machines_through_structure_registry() {
        TestBootstrap.registerRuntimeBuiltins();

        assertThat(MachineStructureRegistry.dynamicSnapshot())
                .containsKeys(MMCR.id("test_cube"), MMCR.id("controller_tick"), MMCR.id("iron_compressor"));
        assertThat(MachineRegistry.getMachine(MMCR.id("test_cube"))).isNotNull();
        assertThat(MachineRegistry.getMachine(MMCR.id("controller_tick"))).isNotNull();
        assertThat(MachineRegistry.getMachine(MMCR.id("iron_compressor"))).isNotNull();
    }
}
