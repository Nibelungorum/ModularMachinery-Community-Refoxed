package cn.howxu.mmcr.internal.reload;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
        RecipeRegistry.clearForTesting();
    }

    @Test
    void producerFailureRetainsPreviousDynamicSnapshot() {
        DynamicContentReloadService.reload(candidate -> {
            candidate.registerMachine(machine("mmcr:test_cube"));
            candidate.registerRecipe(recipe("mmcr:old_recipe", "mmcr:test_cube"));
        });

        assertThatThrownBy(() -> DynamicContentReloadService.reload(candidate -> {
            candidate.registerMachine(machine("mmcr:controller_tick"));
            throw new IllegalStateException("script failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(MachineRegistry.getMachine(Identifier.parse("mmcr:test_cube"))).isNotNull();
        assertThat(RecipeRegistry.getRecipe(Identifier.parse("mmcr:old_recipe"))).isNotNull();
        assertThat(MachineRegistry.getMachine(Identifier.parse("mmcr:controller_tick"))).isNull();
    }

    @Test
    void successfulReloadReportsRemovedMachinesAndDropsTheirRecipes() {
        DynamicContentReloadService.reload(candidate -> {
            candidate.registerMachine(machine("mmcr:test_cube"));
            candidate.registerMachine(machine("mmcr:controller_tick"));
            candidate.registerRecipe(recipe("mmcr:old_recipe", "mmcr:test_cube"));
        });

        var result = DynamicContentReloadService.reload(candidate ->
                candidate.registerMachine(machine("mmcr:controller_tick")));

        assertThat(result.removedMachines()).containsExactly(Identifier.parse("mmcr:test_cube"));
        assertThat(MachineRegistry.getCompiled(Identifier.parse("mmcr:test_cube"))).isNull();
        assertThat(RecipeRegistry.getRecipe(Identifier.parse("mmcr:old_recipe"))).isNull();
    }

    @Test
    void compilationFailureRetainsPreviousRuntimeSnapshotAndCache() {
        var oldMachine = machine("mmcr:test_cube");
        DynamicContentReloadService.reload(candidate -> candidate.registerMachine(oldMachine));
        var oldCompiled = MachineRegistry.getCompiled(oldMachine.registryName());
        var oldRotated = cn.howxu.mmcr.api.machine.BlockArrayCache.get(oldMachine.pattern(), net.minecraft.core.Direction.NORTH);

        assertThatThrownBy(() -> DynamicContentReloadService.reload(candidate ->
                candidate.registerMachine(failingMachine("mmcr:controller_tick"))))
                .isInstanceOf(RuntimeException.class);

        assertThat(MachineRegistry.getMachine(oldMachine.registryName())).isSameAs(oldMachine);
        assertThat(MachineRegistry.getCompiled(oldMachine.registryName())).isSameAs(oldCompiled);
        assertThat(cn.howxu.mmcr.api.machine.BlockArrayCache.get(oldMachine.pattern(), net.minecraft.core.Direction.NORTH))
                .isSameAs(oldRotated);
        assertThat(MachineRegistry.getMachine(Identifier.parse("mmcr:controller_tick"))).isNull();
    }

    @Test
    void candidateRecipeCannotReferenceRemovedDynamicMachineButCanReferenceStaticMachine() {
        assertThatThrownBy(() -> DynamicContentReloadService.reload(candidate ->
                candidate.registerMachine(machine("mmcr:unreserved"))))
                .isInstanceOf(IllegalStateException.class);

        var staticMachine = machine("mmcr:static");
        MachineRegistry.register(staticMachine);
        DynamicContentReloadService.reload(candidate ->
                candidate.registerRecipe(recipe("mmcr:static_recipe", "mmcr:static")));
        assertThat(RecipeRegistry.getRecipe(Identifier.parse("mmcr:static_recipe"))).isNotNull();
    }

    private static DynamicMachine machine(String id) {
        Identifier identifier = Identifier.parse(id);
        return new DynamicMachine(identifier, id, new BlockArray(Map.of()));
    }

    private static MachineRecipe recipe(String id, String machineId) {
        return new MachineRecipe(Identifier.parse(id), Identifier.parse(machineId), 1, List.of(), List.of());
    }

    private static cn.howxu.mmcr.api.machine.Machine failingMachine(String id) {
        Identifier identifier = Identifier.parse(id);
        return new cn.howxu.mmcr.api.machine.Machine() {
            @Override public Identifier registryName() { return identifier; }
            @Override public String localizedName() { return id; }
            @Override public BlockArray pattern() { return new BlockArray(Map.of()); }
            @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(identifier); }
            @Override public PortRequirementSpec portRequirements() { return PortRequirementSpec.none(); }
            @Override public RecipeFailureActions failureAction() { return RecipeFailureActions.getDefaultAction(); }
            @Override public List<cn.howxu.mmcr.api.machine.DynamicPatternSpec> dynamicPatterns() { return List.of((cn.howxu.mmcr.api.machine.DynamicPatternSpec) null); }
        };
    }
}
