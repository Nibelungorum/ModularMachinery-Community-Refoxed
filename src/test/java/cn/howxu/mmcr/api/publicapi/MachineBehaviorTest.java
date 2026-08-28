package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenText;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehavior;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehaviorContext;
import cn.howxu.mmcr.api.publicapi.machine.MachineIoPlan;
import cn.howxu.mmcr.api.publicapi.machine.MachineIoView;
import cn.howxu.mmcr.api.publicapi.machine.RecipeFinishContext;
import cn.howxu.mmcr.api.publicapi.machine.RecipeStartContext;
import cn.howxu.mmcr.api.publicapi.machine.RecipeTickContext;
import cn.howxu.mmcr.api.publicapi.machine.RecipeBehavior;
import cn.howxu.mmcr.api.publicapi.machine.TickBehaviorContext;
import cn.howxu.mmcr.api.publicapi.machine.TickBehavior;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the public machine behavior contracts.
 * @author howxu <dev@howxu.cn>
 */
class MachineBehaviorTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    private static final ControllerScreenText SCREEN_TEXT = new ControllerScreenText() {
        @Override
        public void append(cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope scope,
                           Identifier lineId, Component text) {
        }

        @Override
        public void remove(cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope scope,
                           Identifier lineId) {
        }

        @Override
        public void clear(cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope scope) {
        }
    };

    @Test
    void recipe_behavior_retains_all_callbacks_and_context_values() {
        AtomicInteger idleStart = new AtomicInteger();
        AtomicInteger idleEnd = new AtomicInteger();
        AtomicInteger beforeStart = new AtomicInteger();
        AtomicInteger recipeTick = new AtomicInteger();
        AtomicInteger beforeFinish = new AtomicInteger();
        MachineRecipe recipe = recipe();
        MachineBehaviorContext machineContext = new MachineBehaviorContext(null, null,
                new BlockPos(1, 2, 3), MMCR.id("behavior_machine"), 40L, SCREEN_TEXT);
        RecipeStartContext startContext = new RecipeStartContext(recipe, 4, 3);
        RecipeTickContext tickContext = new RecipeTickContext(recipe, 2, 20, 3);
        RecipeFinishContext finishContext = new RecipeFinishContext(recipe, 4, 3,
                List.of(new MachineOutput.ItemOutput(net.minecraft.world.item.ItemStack.EMPTY, 1F)));

        RecipeBehavior behavior = RecipeBehavior.builder()
                .idleStart(context -> idleStart.incrementAndGet())
                .idleEnd(context -> idleEnd.incrementAndGet())
                .beforeStart(context -> beforeStart.incrementAndGet())
                .recipeTick(context -> recipeTick.incrementAndGet())
                .beforeFinish(context -> beforeFinish.incrementAndGet())
                .build();

        behavior.idleStart().accept(machineContext);
        behavior.idleEnd().accept(machineContext);
        behavior.beforeStart().accept(startContext);
        behavior.recipeTick().accept(tickContext);
        behavior.beforeFinish().accept(finishContext);

        assertThat(behavior.kind()).isEqualTo(MachineBehavior.Kind.RECIPE);
        assertThat(idleStart).hasValue(1);
        assertThat(idleEnd).hasValue(1);
        assertThat(beforeStart).hasValue(1);
        assertThat(recipeTick).hasValue(1);
        assertThat(beforeFinish).hasValue(1);
        assertThat(machineContext.controllerPos()).isEqualTo(new BlockPos(1, 2, 3));
        assertThat(machineContext.machineId()).isEqualTo(MMCR.id("behavior_machine"));
        assertThat(machineContext.gameTime()).isEqualTo(40L);
        assertThat(machineContext.screenText()).isSameAs(SCREEN_TEXT);
        assertThat(startContext.recipe()).isSameAs(recipe);
        assertThat(startContext.recipeId()).isEqualTo(recipe.id());
        assertThat(startContext.requestedParallelism()).isEqualTo(4);
        assertThat(startContext.effectiveParallelism()).isEqualTo(3);
        assertThat(startContext.machineContext()).isNotNull();
        assertThat(startContext.machineContext().ioView()).isNotNull();
        startContext.setDuration(40);
        startContext.setOutputs(List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_NUGGET), 1F)));
        assertThat(startContext.snapshot().duration()).isEqualTo(40);
        assertThat(startContext.snapshot().outputs()).containsExactlyElementsOf(startContext.outputs());
        assertThat(tickContext.recipe()).isSameAs(recipe);
        assertThat(tickContext.currentTick()).isEqualTo(2);
        assertThat(tickContext.totalTick()).isEqualTo(20);
        assertThat(tickContext.parallelism()).isEqualTo(3);
        assertThat(tickContext.machineContext()).isNotNull();
        assertThat(tickContext.requirements()).isUnmodifiable();
        assertThat(tickContext.outputs()).isUnmodifiable();
        assertThat(RecipeTickContext.class.getMethods()).extracting(Method::getName)
                .doesNotContain("ioPlan");
        assertThat(finishContext.outputs()).hasSize(1);
        finishContext.cancel();
        assertThat(finishContext.cancelled()).isTrue();
        finishContext.discardOutputs();
        assertThat(finishContext.outputsDiscarded()).isTrue();
    }

    @Test
    void tick_behavior_has_tick_kind_and_retains_callback() {
        AtomicInteger calls = new AtomicInteger();
        TickBehavior behavior = TickBehavior.builder()
                .serverTick(context -> calls.incrementAndGet())
                .build();

        behavior.serverTick().accept(null);

        assertThat(behavior.kind()).isEqualTo(MachineBehavior.Kind.TICK);
        assertThat(calls).hasValue(1);
    }

    @Test
    void tick_callback_receives_tick_context_with_a_fresh_io_plan() {
        AtomicInteger calls = new AtomicInteger();
        MachineBehavior.TickCallback callback = context -> {
            assertThat(context).isInstanceOf(TickBehaviorContext.class);
            assertThat(context.ioPlan()).isNotNull();
            calls.incrementAndGet();
        };
        TickBehavior behavior = TickBehavior.builder().serverTick(callback).build();
        MachineBehaviorContext base = new MachineBehaviorContext(null, null, BlockPos.ZERO,
                MMCR.id("tick_machine"), 0L, SCREEN_TEXT, Map.of(),
                new MachineIoView(new CapabilitySnapshot(List.of())));
        TickBehaviorContext tickContext = new TickBehaviorContext(base, new CapabilitySnapshot(List.of()));

        behavior.serverTick().accept(tickContext);

        assertThat(behavior.serverTick()).isSameAs(callback);
        assertThat(calls).hasValue(1);
        assertThat(tickContext.ioView()).isSameAs(base.ioView());
    }

    @Test
    void tick_io_plan_simulates_without_committing_physical_io() {
        MachineIoPlan plan = new MachineIoPlan(new CapabilitySnapshot(List.of()));

        assertThat(plan.simulate().successful()).isTrue();
        assertThat(plan.inputsSatisfied()).isTrue();
        assertThat(plan.energySatisfied()).isTrue();
        assertThat(plan.commit()).isTrue();
    }

    @Test
    void recipe_start_snapshot_replaces_item_and_fluid_outputs_with_defensive_copies() {
        MachineRecipe recipe = recipe();
        RecipeStartContext context = new RecipeStartContext(recipe, 1, 1);
        List<MachineRequirement> requirements = List.of();
        List<MachineOutput> outputs = List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_NUGGET), 1F));

        context.setRequirements(List.of(MachineRequirement.itemOutput(new ItemStack(Items.IRON_NUGGET))));
        assertThat(context.outputs()).hasSize(1);
        context.setRequirements(requirements);
        context.setOutputs(outputs);

        assertThat(context.requirements()).isUnmodifiable();
        assertThat(context.outputs()).isUnmodifiable();
        assertThat(context.snapshot().requirements()).isEqualTo(context.requirements());
        assertThat(context.snapshot().outputs()).isEqualTo(context.outputs());
        assertThatThrownBy(() -> context.setDuration(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void machine_behavior_context_is_due_only_on_period_modulus() {
        MachineBehaviorContext context = new MachineBehaviorContext(null, null, BlockPos.ZERO,
                MMCR.id("due_machine"), 40L, SCREEN_TEXT);

        assertThat(context.isDue(20)).isTrue();
        assertThat(new MachineBehaviorContext(null, null, BlockPos.ZERO, MMCR.id("due_machine"),
                41L, SCREEN_TEXT).isDue(20)).isFalse();
        assertThatThrownBy(() -> context.isDue(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void default_behavior_is_recipe() {
        assertThat(RecipeBehavior.defaults().kind()).isEqualTo(MachineBehavior.Kind.RECIPE);
        assertThat(TickBehavior.defaults().kind()).isEqualTo(MachineBehavior.Kind.TICK);
    }

    private static MachineRecipe recipe() {
        return new MachineRecipe(MMCR.id("behavior_recipe"), MMCR.id("behavior_machine"), 20,
                List.of(), List.of());
    }
}
