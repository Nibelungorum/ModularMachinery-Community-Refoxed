package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.machine.MachineBuilder;
import cn.howxu.mmcr.api.publicapi.machine.MachineDefinition;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.publicapi.recipe.MachineRecipeDefinition;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.internal.api.PublicMachineAdapter;
import cn.howxu.mmcr.internal.api.PublicRecipeAdapter;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies public declarations retain runtime adapter semantics.
 * @author howxu <dev@howxu.cn>
 */
class PublicApiAdapterTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void reset() {
        MachineDefinitions.clearForTesting();
        MachineRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @AfterEach
    void cleanup() {
        MachineDefinitions.clearForTesting();
        MachineRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void machine_and_recipe_adapters_preserve_structure_and_requirement_kinds() {
        MachineDefinition definition = MachineBuilder.machine(MMCR.id("adapter_machine"))
                .pattern(pattern -> pattern.layer("F").where('F', BlockPredicate.block(Blocks.FURNACE)).controller('F'))
                .build();
        MachineRecipeDefinition recipe = MachineRecipeBuilder.recipe(MMCR.id("adapter_recipe"), definition.id())
                .duration(20)
                .inputItem(Blocks.STONE.asItem(), 1)
                .inputEnergy(10)
                .build();

        var registration = PublicMachineAdapter.toRegistration(definition);
        var internalRecipe = PublicRecipeAdapter.toRecipe(recipe);

        assertThat(registration.id()).isEqualTo(definition.id());
        assertThat(registration.pattern().get(net.minecraft.core.BlockPos.ZERO))
                .isInstanceOf(cn.howxu.mmcr.api.machine.BlockPredicate.OfBlock.class);
        assertThat(internalRecipe.machineId()).isEqualTo(definition.id());
        assertThat(internalRecipe.requirements()).extracting(Object::getClass)
                .contains(cn.howxu.mmcr.api.recipe.requirement.ItemRequirement.class,
                        cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement.class);
    }
}
