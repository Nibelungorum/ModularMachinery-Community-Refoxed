package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CraftingContextPoolTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void returnAndBorrowReusesContextAfterClearingTransientFailure() throws Exception {
        CraftingContextPool pool = new CraftingContextPool();
        MachineRecipe recipe = recipe("pooled");
        MachineControllerBlockEntity first = controller();
        MachineControllerBlockEntity second = controller();
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe, 1);
        RecipeCraftingContext context = pool.borrow(active, first);
        RequirementFailure failure = new RequirementFailure(0, RequirementFailure.Kind.MISSING_INPUT, 3, 1);
        context.setRequirementFailure(RecipeCraftingContext.FAILURE_MISSING_INPUT, failure);

        pool.returnContext(context);
        RecipeCraftingContext reused = pool.borrow(active, second);

        assertThat(reused).isSameAs(context);
        assertThat(reused.getLastFailureUnloc()).isNull();
        assertThat(reused.getLastRequirementFailure()).isNull();
        assertThat(controllerOf(reused)).isSameAs(second);
    }

    @Test
    void reloadClearsPreviouslyReturnedContexts() throws Exception {
        CraftingContextPool pool = new CraftingContextPool();
        MachineRecipe recipe = recipe("reload");
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe, 1);
        RecipeCraftingContext first = pool.borrow(active, controller());

        pool.returnContext(first);
        pool.onReload();
        RecipeCraftingContext afterReload = pool.borrow(active, controller());

        assertThat(afterReload).isNotSameAs(first);
    }

    private static MachineRecipe recipe(String path) {
        Identifier machineId = Identifier.fromNamespaceAndPath("mmcr", "machine");
        return new MachineRecipe(Identifier.fromNamespaceAndPath("mmcr", path), machineId, 20, List.of(), List.of());
    }

    private static MachineControllerBlockEntity controller() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        return (MachineControllerBlockEntity) unsafe.allocateInstance(MachineControllerBlockEntity.class);
    }

    private static MachineControllerBlockEntity controllerOf(RecipeCraftingContext context) throws Exception {
        Field field = RecipeCraftingContext.class.getDeclaredField("controller");
        field.setAccessible(true);
        return (MachineControllerBlockEntity) field.get(context);
    }
}
