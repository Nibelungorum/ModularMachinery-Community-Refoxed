package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleRecipeHostRequirementTest {
    private static final Identifier MODULE_ID = MMCR.id("module_recipe_host_module");
    private static final Identifier HOST_A = MMCR.id("module_recipe_host_a");
    private static final Identifier HOST_B = MMCR.id("module_recipe_host_b");
    private static final Identifier HOST_C = MMCR.id("module_recipe_host_c");

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void emptyRequiredHostSetAcceptsAnyConnectedHost() {
        MachineRecipe recipe = recipe("empty_required_hosts", Set.of());

        assertThat(recipe.requiredHostIds()).isEmpty();
        assertThat(recipe.canRunOnConnectedHost(HOST_A)).isTrue();
    }

    @Test
    void singleRequiredHostIdAcceptsOnlyThatHost() {
        MachineRecipe recipe = recipe("single_required_host", Set.of(HOST_A));

        assertThat(recipe.requiredHostIds()).containsExactly(HOST_A);
        assertThat(recipe.canRunOnConnectedHost(HOST_A)).isTrue();
        assertThat(recipe.canRunOnConnectedHost(HOST_B)).isFalse();
    }

    @Test
    void multipleRequiredHostIdsAcceptAnyListedHost() {
        MachineRecipe recipe = recipe("multiple_required_hosts", Set.of(HOST_A, HOST_B));

        assertThat(recipe.requiredHostIds()).containsExactlyInAnyOrder(HOST_A, HOST_B);
        assertThat(recipe.canRunOnConnectedHost(HOST_A)).isTrue();
        assertThat(recipe.canRunOnConnectedHost(HOST_B)).isTrue();
        assertThat(recipe.canRunOnConnectedHost(HOST_C)).isFalse();
    }

    @Test
    void requiredHostIdsRoundTripThroughCodec() {
        MachineRecipe recipe = recipe("codec_required_hosts", Set.of(HOST_A, HOST_B));

        MachineRecipe decoded = MachineRecipe.CODEC.codec()
                .parse(JsonOps.INSTANCE, MachineRecipe.CODEC.codec().encodeStart(JsonOps.INSTANCE, recipe).getOrThrow())
                .getOrThrow();

        assertThat(decoded.requiredHostIds()).containsExactlyInAnyOrder(HOST_A, HOST_B);
    }

    @Test
    void unconnectedModuleCannotStartEvenWhenRecipeHasNoSpecificHostRequirement() throws Exception {
        MachineRecipe recipe = recipe("unconnected_module", Set.of());
        MachineControllerBlockEntity controller = moduleController();
        RecipeCraftingContext context = new RecipeCraftingContext(controller);

        assertThat(new ActiveMachineRecipe(recipe).canStartCrafting(context)).isFalse();
    }

    private static MachineRecipe recipe(String path, Set<Identifier> requiredHostIds) {
        return new MachineRecipe(MMCR.id(path), MODULE_ID, 20, List.of(), List.of(), List.of(), 0, 1,
                false, List.of(), List.of(), false, List.of(), requiredHostIds);
    }

    @SuppressWarnings("removal")
    private static MachineControllerBlockEntity moduleController() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        MachineControllerBlockEntity controller = (MachineControllerBlockEntity) unsafe.allocateInstance(MachineControllerBlockEntity.class);
        var machine = new DynamicMachine(MODULE_ID, MODULE_ID.toString(), new BlockArray(Map.of()))
                .withRole(MachineRole.MODULE, Set.of());
        setField(MachineControllerBlockEntity.class, controller, "machine", machine);
        setField(MachineControllerBlockEntity.class, controller, "foundMachine", machine);
        setField(MachineControllerBlockEntity.class, controller, "foundModifiers", new java.util.LinkedHashMap<>());
        setField(MachineControllerBlockEntity.class, controller, "foundLevels", Map.of());
        setField(BlockEntity.class, controller, "worldPosition", BlockPos.ZERO);
        return controller;
    }

    private static void setField(Class<?> declaringClass, Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = declaringClass.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
