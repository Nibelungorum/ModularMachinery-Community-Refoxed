package cn.howxu.mmcr.publicapi.behavior;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.machine.definition.MachineBehaviorContext;
import cn.howxu.mmcr.api.machine.definition.MachineIoView;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.machine.definition.TickBehavior;
import cn.howxu.mmcr.api.machine.definition.TickBehaviorContext;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.internal.api.facade.behavior.BehaviorAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.OutputAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.RequirementAdapters;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextState;
import cn.howxu.mmcr.publicapi.runtime.ItemOutputView;
import cn.howxu.mmcr.publicapi.runtime.RecipeExecutionView;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Core-owned contexts are wrapped only when Java hooks run; writes remain authoritative.
 * @author howxu <dev@howxu.cn>
 */
class BehaviorBoundaryTest {
    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void all_seven_hooks_wrap_the_real_context_and_preserve_registration_flags() {
        List<String> calls = new ArrayList<>();
        var base = base();
        var recipe = recipe();
        var start = new cn.howxu.mmcr.api.machine.definition.RecipeStartContext(recipe, 2, 1);
        var tick = new cn.howxu.mmcr.api.machine.definition.RecipeTickContext(recipe, 1, 20, 1);
        var finish = new cn.howxu.mmcr.api.machine.definition.RecipeFinishContext(recipe, 2, 1, List.of());
        var builder = RecipeBehavior.builder();
        assertSame(builder, BehaviorAdapters.configureRecipe(builder, hooks -> hooks
                .idleStart(c -> { assertSame(base, BehaviorAdapters.unwrap(c)); calls.add("idleStart"); })
                .idleEnd(c -> calls.add("idleEnd"))
                .beforeStart(c -> { c.setDuration(40); calls.add("beforeStart"); })
                .recipeTick(c -> { assertEquals(1, c.currentTick()); calls.add("recipeTick"); })
                .beforeFinish(c -> { c.cancel(); calls.add("beforeFinish"); })
                .preServerTick(c -> calls.add("preServerTick"))
                .postServerTick(c -> calls.add("postServerTick"))));
        var behavior = builder.build();
        assertTrue(behavior.hasIdleStart()); assertTrue(behavior.hasIdleEnd()); assertTrue(behavior.hasBeforeStart());
        assertTrue(behavior.hasRecipeTick()); assertTrue(behavior.hasBeforeFinish());
        assertTrue(behavior.hasPreServerTick()); assertTrue(behavior.hasPostServerTick());
        behavior.idleStart().accept(base); behavior.idleEnd().accept(base); behavior.beforeStart().accept(start);
        behavior.recipeTick().accept(tick); behavior.beforeFinish().accept(finish);
        behavior.preServerTick().accept(base); behavior.postServerTick().accept(base);
        assertEquals(List.of("idleStart", "idleEnd", "beforeStart", "recipeTick", "beforeFinish", "preServerTick", "postServerTick"), calls);
        assertEquals(40, start.duration()); assertTrue(finish.cancelled()); assertFalse(finish.outputsDiscarded());
        var defaults = BehaviorAdapters.configureRecipe(RecipeBehavior.builder(), ignored -> {}).build();
        assertFalse(defaults.hasIdleStart()); assertFalse(defaults.hasBeforeStart()); assertFalse(defaults.hasPreServerTick());
        assertThrows(NullPointerException.class, () -> BehaviorAdapters.configureRecipe(RecipeBehavior.builder(), h -> h.idleStart(null)));
    }

    @Test
    void start_edits_update_core_requirements_and_snapshot_while_output_reads_are_copies() {
        var input = new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 4, ItemStack.EMPTY);
        var nativeStart = new cn.howxu.mmcr.api.machine.definition.RecipeStartContext(base(), recipe(), 2, 1, 20,
                List.of(input), List.of());
        RecipeStartContext start = BehaviorAdapters.wrap(nativeStart);
        assertThrows(IllegalArgumentException.class, () -> start.setDuration(0));
        assertTrue(start.replaceExactItemInputCount(Items.IRON_INGOT, 4, 1));
        assertEquals(1, ((ItemRequirement) nativeStart.requirements().getFirst()).count());
        start.setOutputs(List.of(OutputAdapters.item(new ItemStack(Items.GOLD_NUGGET, 3), 1F)));
        ItemOutputView output = (ItemOutputView) start.outputs().getFirst();
        output.stack().setCount(1);
        assertEquals(3, ((MachineOutput.ItemOutput) nativeStart.outputs().getFirst()).stack().getCount());
        assertEquals(2, nativeStart.requirements().size());
        RecipeExecutionView snapshot = start.snapshot();
        start.setDuration(40);
        start.setRequirements(List.of(RequirementAdapters.wrap(input)));
        assertEquals(20, snapshot.duration());
        assertEquals(1, snapshot.outputs().size());
        assertTrue(start.outputs().isEmpty());
        start.cancel(); assertTrue(nativeStart.cancelled());
    }

    @Test
    void finish_cancel_and_discard_are_distinct_and_output_replacements_write_back() {
        var nativeFinish = new cn.howxu.mmcr.api.machine.definition.RecipeFinishContext(recipe(), 1, 1,
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.IRON_NUGGET, 2), 1F)));
        RecipeFinishContext finish = BehaviorAdapters.wrap(nativeFinish);
        assertThrows(IllegalArgumentException.class, () -> finish.setOutputs(List.of(OutputAdapters.item(ItemStack.EMPTY, 1F))));
        ((ItemOutputView) finish.outputs().getFirst()).stack().setCount(1);
        assertEquals(2, ((MachineOutput.ItemOutput) nativeFinish.outputs().getFirst()).stack().getCount());
        finish.setOutputs(List.of(OutputAdapters.item(new ItemStack(Items.GOLD_NUGGET, 3), 1F)));
        assertEquals(3, ((MachineOutput.ItemOutput) nativeFinish.outputs().getFirst()).stack().getCount());
        finish.cancel(); assertTrue(nativeFinish.cancelled()); assertFalse(nativeFinish.outputsDiscarded());
        finish.discardOutputs(); assertTrue(nativeFinish.outputsDiscarded()); assertTrue(nativeFinish.outputs().isEmpty());
    }

    @Test
    void direct_tick_uses_fresh_plans_and_callback_exceptions_propagate() {
        var nativeTick = new TickBehaviorContext(base(), new CapabilitySnapshot(List.of()), 2, 3);
        var builder = TickBehavior.builder();
        assertSame(builder, BehaviorAdapters.configureTick(builder, hooks -> hooks.serverTick(context -> {
            assertSame(nativeTick, BehaviorAdapters.unwrap(context));
            assertEquals(2, context.factoryThreadCount()); assertEquals(3, context.parallelism());
            assertNotSame(context.ioPlan(), context.ioPlan());
            assertNull(context.dataStorage());
            throw new IllegalStateException("addon failure");
        })));
        assertTrue(builder.build().hasServerTick());
        assertThrows(IllegalStateException.class, () -> builder.build().serverTick().accept(nativeTick));
        assertFalse(BehaviorAdapters.configureTick(TickBehavior.builder(), ignored -> {}).build().hasServerTick());
    }

    private static MachineBehaviorContext base() {
        return new MachineBehaviorContext(null, null, BlockPos.ZERO, ResourceLocation.parse("test:machine"), 40,
                new ControllerScreenTextState(), null, new MachineIoView(new CapabilitySnapshot(List.of())));
    }
    private static MachineRecipe recipe() {
        return RecipeTestSupport.create(ResourceLocation.parse("test:recipe"), ResourceLocation.parse("test:pool"), 20, List.of(), List.of());
    }
}
