package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class ModuleRecipeBuilderJSTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        MachineDefinitions.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void builder_declares_required_hosts_with_stable_deduplication() {
        var builder = new MachineRecipeBuilderJS("mmcr:module_recipe")
                .requiredHost("mmcr:first")
                .requiredHosts("mmcr:second", "mmcr:first", "mmcr:third");

        assertThat(builder.requiredHostIds)
                .containsExactly(Identifier.parse("mmcr:first"), Identifier.parse("mmcr:second"), Identifier.parse("mmcr:third"));
    }

    @Test
    void build_preserves_required_host_declaration_order() {
        Identifier machineId = MMCR.id("module_machine");
        Identifier recipeId = MMCR.id("module_recipe");
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());

        new MachineRecipeBuilderJS(recipeId)
                .machine(machineId.toString())
                .requiredHosts("mmcr:first", "mmcr:second", "mmcr:first", "mmcr:third")
                .build();

        assertThat(RecipeRegistry.getRecipe(recipeId).requiredHostIds())
                .containsExactly(Identifier.parse("mmcr:first"), Identifier.parse("mmcr:second"), Identifier.parse("mmcr:third"));
    }
}
