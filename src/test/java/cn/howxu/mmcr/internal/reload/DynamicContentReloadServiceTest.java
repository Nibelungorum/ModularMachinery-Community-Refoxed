package cn.howxu.mmcr.internal.reload;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import cn.howxu.mmcr.api.machine.BlockArrayCache;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import net.minecraft.core.Direction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamicContentReloadServiceTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        MachineDefinitions.clearForTesting();
        MachineRegistry.clearForTesting();
        MachineStructureRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void producerFailureRetainsPreviousDynamicSnapshot() {
        register("mmcr:test_cube");
        register("mmcr:controller_tick");
        DynamicContentReloadService.reload(candidate -> {
            candidate.registerStructure(structure("mmcr:test_cube"));
            candidate.registerRecipe(recipe("mmcr:old_recipe", "mmcr:test_cube"));
        });

        assertThatThrownBy(() -> DynamicContentReloadService.reload(candidate -> {
            candidate.registerStructure(structure("mmcr:controller_tick"));
            throw new IllegalStateException("script failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(MachineRegistry.getMachine(Identifier.parse("mmcr:test_cube"))).isNotNull();
        assertThat(RecipeRegistry.getRecipe(Identifier.parse("mmcr:old_recipe"))).isNotNull();
        assertThat(MachineRegistry.getMachine(Identifier.parse("mmcr:controller_tick"))).isNull();
    }

    @Test
    void successfulReloadReportsRemovedStructuresAndDropsTheirRecipes() {
        register("mmcr:test_cube");
        register("mmcr:controller_tick");
        DynamicContentReloadService.reload(candidate -> {
            candidate.registerStructure(structure("mmcr:test_cube"));
            candidate.registerStructure(structure("mmcr:controller_tick"));
            candidate.registerRecipe(recipe("mmcr:old_recipe", "mmcr:test_cube"));
        });

        var result = DynamicContentReloadService.reload(candidate ->
                candidate.registerStructure(structure("mmcr:controller_tick")));

        assertThat(result.removedStructures()).containsExactly(Identifier.parse("mmcr:test_cube"));
        assertThat(MachineRegistry.getCompiled(Identifier.parse("mmcr:test_cube"))).isNull();
        assertThat(RecipeRegistry.getRecipe(Identifier.parse("mmcr:old_recipe"))).isNull();
    }

    @Test
    void compilationFailureRetainsPreviousRuntimeSnapshotAndCache() {
        register("mmcr:test_cube");
        register("mmcr:controller_tick");
        var oldStructure = structure("mmcr:test_cube");
        DynamicContentReloadService.reload(candidate -> candidate.registerStructure(oldStructure));
        var oldMachine = MachineRegistry.getMachine(Identifier.parse("mmcr:test_cube"));
        var oldCompiled = MachineRegistry.getCompiled(oldMachine.registryName());
        var oldRotated = BlockArrayCache.get(oldMachine.pattern(), Direction.NORTH);

        assertThatThrownBy(() -> DynamicContentReloadService.reload(candidate ->
                candidate.registerStructure(failingStructure("mmcr:controller_tick"))))
                .isInstanceOf(RuntimeException.class);

        assertThat(MachineRegistry.getMachine(oldMachine.registryName())).isNotNull();
        assertThat(MachineRegistry.getCompiled(oldMachine.registryName())).isSameAs(oldCompiled);
        assertThat(BlockArrayCache.get(oldMachine.pattern(), Direction.NORTH))
                .isSameAs(oldRotated);
        assertThat(MachineRegistry.getMachine(Identifier.parse("mmcr:controller_tick"))).isNull();
    }

    @Test
    void candidateRecipeCanReferenceStaticMachine() {
        var staticMachine = new DynamicMachine(Identifier.parse("mmcr:static"), "mmcr:static", new BlockArray(Map.of()));
        MachineRegistry.register(staticMachine);
        DynamicContentReloadService.reload(candidate ->
                candidate.registerRecipe(recipe("mmcr:static_recipe", "mmcr:static")));
        assertThat(RecipeRegistry.getRecipe(Identifier.parse("mmcr:static_recipe"))).isNotNull();
    }

    private static void register(String id) {
        Identifier identifier = Identifier.parse(id);
        MachineDefinitions.register(MachineRegistration.builder(identifier).localizedName(id).build());
    }

    private static MachineRecipe recipe(String id, String machineId) {
        return new MachineRecipe(Identifier.parse(id), Identifier.parse(machineId), 1, List.of(), List.of());
    }

    private static MachineStructureDefinition structure(String id) {
        Identifier identifier = Identifier.parse(id);
        return new MachineStructureDefinition(identifier, new BlockArray(Map.of()), PortRequirementSpec.none(), List.of(),
                MachineStructureRequirements.EMPTY);
    }

    private static MachineStructureDefinition failingStructure(String id) {
        Identifier identifier = Identifier.parse(id);
        BlockPos outsidePattern = new BlockPos(1, 0, 0);
        var replacement = new SingleBlockModifierReplacement("invalid", new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK), List.of(), ItemStack.EMPTY);
        return new MachineStructureDefinition(identifier, new BlockArray(Map.of()), PortRequirementSpec.none(), List.of(),
                MachineStructureRequirements.builder().modifier('X', replacement).build());
    }
}
