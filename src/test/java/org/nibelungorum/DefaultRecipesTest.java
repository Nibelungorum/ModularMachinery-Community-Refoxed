package org.nibelungorum;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultRecipesTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        MachineRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void default_recipes_are_published_from_org_nibelungorum_package() {
        assertThat(DefaultRecipes.class.getPackageName()).isEqualTo("org.nibelungorum");
    }

    @Test
    void ensureRegistered_currently_has_no_builtin_recipes() {
        DefaultMachines.ensureRegistered();
        DefaultRecipes.ensureRegistered();

        var machine = (DynamicMachine) MachineRegistry.getMachine(MMCR.id("blast_furnace"));

        assertThat(machine).isNotNull();
        assertThat(RecipeRegistry.byMachine(machine)).isEmpty();
    }
}
