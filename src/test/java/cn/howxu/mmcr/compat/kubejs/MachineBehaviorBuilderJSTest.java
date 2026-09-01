package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.data.DataStorage;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehavior;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehaviorContext;
import cn.howxu.mmcr.api.publicapi.machine.RecipeBehavior;
import cn.howxu.mmcr.api.publicapi.machine.RecipeFinishContext;
import cn.howxu.mmcr.api.publicapi.machine.RecipeStartContext;
import cn.howxu.mmcr.api.publicapi.machine.RecipeTickContext;
import cn.howxu.mmcr.api.publicapi.machine.MachineIoView;
import cn.howxu.mmcr.api.publicapi.machine.TickBehavior;
import cn.howxu.mmcr.api.publicapi.machine.TickBehaviorContext;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextState;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RecipeTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies KubeJS machine behavior callbacks are retained as startup data.
 * @author howxu <dev@howxu.cn>
 */
class MachineBehaviorBuilderJSTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void create_object_retains_tick_callback() {
        AtomicInteger calls = new AtomicInteger();
        MachineRegistration registration = new MachineBuilderJS(MMCR.id("kubejs_tick_machine"))
                .tickBehavior(builder -> builder.serverTick(context -> {
                    assertThat(context.ioView()).isNotNull();
                    assertThat(context.ioPlan().simulate().inputsSatisfied()).isTrue();
                    calls.incrementAndGet();
                }))
                .createObject();

        assertThat(registration.behavior().kind()).isEqualTo(MachineBehavior.Kind.TICK);
        MachineBehaviorContext base = new MachineBehaviorContext(null, null, BlockPos.ZERO,
                MMCR.id("kubejs_tick_machine"), 0L, new ControllerScreenTextState(), null,
                new MachineIoView(new CapabilitySnapshot(List.of())));
        ((TickBehavior) registration.behavior()).serverTick()
                .accept(new TickBehaviorContext(base, new CapabilitySnapshot(List.of())));
        assertThat(calls).hasValue(1);
    }

    @Test
    void create_object_retains_recipe_callback_context_boundary() {
        Identifier storagePosId = MMCR.id("kubejs_recipe_context_machine");
        BlockPos storagePos = new BlockPos(2, 3, 4);
        DataStorage storage = new DataStorage();
        ControllerScreenTextState screenText = new ControllerScreenTextState();
        MachineBehaviorContext machineContext = new MachineBehaviorContext(null, null, BlockPos.ZERO,
                storagePosId, 0L, screenText, storage);
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger ticks = new AtomicInteger();
        AtomicInteger finishes = new AtomicInteger();
        MachineRegistration registration = new MachineBuilderJS(storagePosId)
                .recipeBehavior(builder -> builder
                        .beforeStart(context -> {
                            assertThat(context).isInstanceOf(RecipeStartContext.class);
                            assertThat(context.machineContext()).isSameAs(machineContext);
                            starts.incrementAndGet();
                        })
                        .recipeTick(context -> {
                            assertThat(context.machineContext().dataStorage()).isSameAs(storage);
                            context.machineContext().screenText().append(ControllerScreenTextScope.OPERATION,
                                    MMCR.id("kubejs_recipe_tick_status"), Component.literal("running"));
                            ticks.incrementAndGet();
                        })
                        .beforeFinish(context -> {
                            assertThat(context).isInstanceOf(RecipeFinishContext.class);
                            assertThat(context.machineContext()).isSameAs(machineContext);
                            finishes.incrementAndGet();
                        }))
                .createObject();

        assertThat(registration.behavior().kind()).isEqualTo(MachineBehavior.Kind.RECIPE);
        RecipeBehavior behavior = (RecipeBehavior) registration.behavior();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("kubejs_recipe_context"), storagePosId, 1,
                List.of(), List.of());
        behavior.beforeStart().accept(new RecipeStartContext(machineContext, recipe, 1, 1, 1,
                List.of(), List.of()));
        behavior.recipeTick().accept(new RecipeTickContext(machineContext, recipe, 0, 1, 1,
                List.of(), List.of()));
        behavior.beforeFinish().accept(new RecipeFinishContext(machineContext, recipe, 1, 1, List.of()));

        assertThat(starts).hasValue(1);
        assertThat(ticks).hasValue(1);
        assertThat(finishes).hasValue(1);
        assertThat(screenText.snapshot().lines()).singleElement()
                .satisfies(line -> assertThat(line.text()).isEqualTo(Component.literal("running")));
        assertThat(RecipeTickContext.class.getMethods()).extracting(Method::getName)
                .doesNotContain("ioPlan");
    }

    @Test
    void create_object_retains_machine_level_recipe_tick_callbacks() {
        AtomicInteger preCalls = new AtomicInteger();
        AtomicInteger postCalls = new AtomicInteger();
        MachineRegistration registration = new MachineBuilderJS(MMCR.id("kubejs_hook_machine"))
                .preServerTick(context -> {
                    assertThat(context.ioView()).isNotNull();
                    preCalls.incrementAndGet();
                })
                .postServerTick(context -> {
                    assertThat(context.screenText()).isNotNull();
                    postCalls.incrementAndGet();
                })
                .createObject();

        assertThat(registration.behavior()).isInstanceOf(RecipeBehavior.class);
        RecipeBehavior behavior = (RecipeBehavior) registration.behavior();
        MachineBehaviorContext context = new MachineBehaviorContext(null, null, BlockPos.ZERO,
                MMCR.id("kubejs_hook_machine"), 0L, new ControllerScreenTextState());
        behavior.preServerTick().accept(context);
        behavior.postServerTick().accept(context);
        assertThat(preCalls).hasValue(1);
        assertThat(postCalls).hasValue(1);
    }

    @Test
    void create_object_retains_direct_and_recipe_callbacks_together() {
        AtomicInteger calls = new AtomicInteger();
        MachineRegistration registration = new MachineBuilderJS(MMCR.id("kubejs_combined_hook_machine"))
                .recipeBehavior(builder -> builder
                        .idleStart(context -> calls.incrementAndGet())
                        .idleEnd(context -> calls.incrementAndGet())
                        .beforeStart(context -> calls.incrementAndGet())
                        .recipeTick(context -> calls.incrementAndGet())
                        .beforeFinish(context -> calls.incrementAndGet()))
                .preServerTick(context -> calls.incrementAndGet())
                .postServerTick(context -> calls.incrementAndGet())
                .createObject();

        RecipeBehavior behavior = (RecipeBehavior) registration.behavior();
        behavior.idleStart().accept(null);
        behavior.idleEnd().accept(null);
        behavior.beforeStart().accept(null);
        behavior.recipeTick().accept(null);
        behavior.beforeFinish().accept(null);
        behavior.preServerTick().accept(null);
        behavior.postServerTick().accept(null);
        assertThat(calls).hasValue(7);
    }

    @Test
    void create_object_rejects_machine_level_hooks_for_tick_behavior() {
        assertThatThrownBy(() -> new MachineBuilderJS(MMCR.id("kubejs_tick_hook_machine"))
                .tickBehavior(builder -> builder.serverTick(context -> { }))
                .preServerTick(context -> { }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("recipe behavior");

        assertThatThrownBy(() -> new MachineBuilderJS(MMCR.id("kubejs_late_tick_hook_machine"))
                .preServerTick(context -> { })
                .tickBehavior(builder -> builder.serverTick(context -> { })))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("server tick hooks");
    }

    @Test
    void builder_rejects_mixing_recipe_and_tick_behaviors() {
        assertThatThrownBy(() -> new MachineBuilderJS(MMCR.id("kubejs_mixed_machine"))
                .recipeBehavior(builder -> builder.idleStart(context -> { }))
                .tickBehavior(builder -> builder.serverTick(context -> { })))
                .isInstanceOf(IllegalStateException.class);
    }
}
