package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeCraftingContext;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Completion callback coverage for factory recipe lanes.
 *
 * @author howxu <dev@howxu.cn>
 */
class FactoryRecipeLaneTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void factory_lane_notifies_once_when_recipe_finishes() throws Exception {
        AtomicInteger finished = new AtomicInteger();
        FactoryRecipeLane lane = laneWithFinalTickResult(ActiveMachineRecipe.TickStatus.FINISHED, finished::incrementAndGet);

        assertThat(lane.tick(20L)).isTrue();

        assertThat(finished).hasValue(1);
        assertThat(lane.tick(21L)).isTrue();
        assertThat(finished).hasValue(1);
    }

    @Test
    void factory_lane_does_not_notify_when_recipe_is_cancelled() throws Exception {
        AtomicInteger finished = new AtomicInteger();
        FactoryRecipeLane lane = laneWithFinalTickResult(ActiveMachineRecipe.TickStatus.CANCELLED, finished::incrementAndGet);

        assertThat(lane.tick(20L)).isTrue();

        assertThat(finished).hasValue(0);
    }

    private static FactoryRecipeLane laneWithFinalTickResult(ActiveMachineRecipe.TickStatus status,
                                                            Runnable onFinished) throws Exception {
        MachineRecipe recipe = new MachineRecipe(MMCR.id("factory_lane_" + status.name().toLowerCase()),
                MMCR.id("factory_lane_machine"), 1, List.of(), List.of());
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe);
        if (status == ActiveMachineRecipe.TickStatus.CANCELLED) {
            recipe = new MachineRecipe(MMCR.id("factory_lane_cancelled"), MMCR.id("factory_lane_machine"),
                    1, List.of(), List.of(), List.of(), 0, 0, true, List.of(), List.of(
                    new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY)));
            active = new ActiveMachineRecipe(recipe);
        }
        MachineControllerBlockEntity controller = controllerBlockEntityWithoutRunningMinecraftConstructor();
        RecipeCraftingContext context = new RecipeCraftingContext(controller);
        return new FactoryRecipeLane(active, context, ignored -> { }, onFinished);
    }

    private static MachineControllerBlockEntity controllerBlockEntityWithoutRunningMinecraftConstructor() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (MachineControllerBlockEntity) ((sun.misc.Unsafe) unsafeField.get(null))
                .allocateInstance(MachineControllerBlockEntity.class);
    }
}
