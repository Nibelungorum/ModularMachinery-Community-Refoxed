package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.internal.runtime.ResourceAvailabilityNotifier;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static cn.howxu.mmcr.internal.runtime.ResourceAvailabilityNotifier.Reason.INPUT_AVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies generic failure matcher delegation for factory recipe threads.
 *
 * @author howxu <dev@howxu.cn>
 */
class FactoryRecipeThreadTest {
    private static final HolderLookup.Provider EMPTY_LOOKUP = HolderLookup.Provider.create(Stream.empty());

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void delegates_custom_requirement_wakeups_without_concrete_requirement_dispatch() {
        try (var ignored = RequirementHandlerRegistry.openTestScope()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mmcr_test", "factory_wakeup");
            RequirementHandler<TestRequirement> handler = new RequirementHandler<>() {
                @Override
                public RequirementPlan plan(TestRequirement requirement, List<MachineCapability> capabilities,
                                            PlanningContext context) {
                    return new RequirementPlan(context.requirementIndex(), context.requestedParallelism(), List.of(), null);
                }

                @Override
                public List<ResourceWakeup> resourceWakeups(TestRequirement requirement) {
                    return List.of(new ResourceWakeup(Set.of(BuiltinFailureReasons.MISSING_INPUT.id()), WakeupReason.INPUT_AVAILABLE,
                            resource -> resource.equals("virtual-resource")));
                }
            };
            RequirementType<TestRequirement> type = new RequirementType.Definition<>(id,
                    MapCodec.unit(() -> new TestRequirement(null, RecipeModifier.IOType.INPUT)), handler);
            RequirementHandlerRegistry.register(type);

            EnumMap<ResourceAvailabilityNotifier.Reason, List<Predicate<Object>>> matchers =
                    new EnumMap<>(ResourceAvailabilityNotifier.Reason.class);
            FactoryRecipeThread.addRequirementMatchers(matchers,
                    new TestRequirement(type, RecipeModifier.IOType.INPUT), BuiltinFailureReasons.MISSING_INPUT.id());

            assertThat(matchers).containsKey(INPUT_AVAILABLE);
            assertThat(matchers.get(INPUT_AVAILABLE).getFirst().test("virtual-resource")).isTrue();
            assertThat(matchers.get(INPUT_AVAILABLE).getFirst().test("other-resource")).isFalse();
        }
    }

    @Test
    void load_migrates_legacy_search_failure_reason_into_a_typed_id() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("legacy_search_failure"), MMCR.id("test_cube"),
                1, List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new ItemRequirement(
                        RecipeModifier.IOType.INPUT, null, 1, new ItemStack(Items.IRON_INGOT, 1))));

        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1, 0L)).isFalse();
        CompoundTag legacy = new CompoundTag();
        thread.save(legacy, EMPTY_LOOKUP);
        legacy.remove("search_failure_reason_id");
        legacy.putString("search_failure_reason", "insufficient_resource");

        FactoryRecipeThread restored = FactoryRecipeThread.load(legacy, controller, EMPTY_LOOKUP);

        assertThat(restored.searchFailureReason()).isEqualTo(BuiltinFailureReasons.MISSING_INPUT.id());
    }

    @Test
    void load_clears_retry_state_when_legacy_search_failure_reason_is_unknown() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("unknown_search_failure"), MMCR.id("test_cube"),
                1, List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new ItemRequirement(
                        RecipeModifier.IOType.INPUT, null, 1, new ItemStack(Items.IRON_INGOT, 1))));

        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1, 0L)).isFalse();
        CompoundTag legacy = new CompoundTag();
        thread.save(legacy, EMPTY_LOOKUP);
        legacy.remove("search_failure_reason_id");
        legacy.putString("search_failure_reason", "legacy:removed_reason");

        FactoryRecipeThread restored = FactoryRecipeThread.load(legacy, controller, EMPTY_LOOKUP);

        assertThat(restored.searchFailureReason()).isNull();
        assertThat(restored.searchFailureKey()).isNull();
    }

    @Test
    void load_clears_retry_state_when_legacy_search_failure_reason_is_malformed() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("malformed_search_failure"), MMCR.id("test_cube"),
                1, List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(new ItemRequirement(
                        RecipeModifier.IOType.INPUT, null, 1, new ItemStack(Items.IRON_INGOT, 1))));

        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1, 0L)).isFalse();
        CompoundTag legacy = new CompoundTag();
        thread.save(legacy, EMPTY_LOOKUP);
        legacy.remove("search_failure_reason_id");
        legacy.putString("search_failure_reason", "not a valid identifier");

        FactoryRecipeThread restored = FactoryRecipeThread.load(legacy, controller, EMPTY_LOOKUP);

        assertThat(restored.searchFailureReason()).isNull();
        assertThat(restored.searchFailureKey()).isNull();
    }

    @Test
    void factory_search_uses_the_controller_selected_recipe_pool() {
        ResourceLocation machineId = MMCR.id("factory_selected_pool_machine");
        ResourceLocation firstPool = MMCR.id("factory_selected_pool_first");
        ResourceLocation secondPool = MMCR.id("factory_selected_pool_second");
        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(MachineRegistration.builder(machineId)
                .recipePoolIds(List.of(firstPool, secondPool)).build());
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        controller.setMachine(new DynamicMachine(machineId, "factory multi pool", new BlockArray(Map.of())));
        assertThat(controller.selectRecipePool(secondPool)).isTrue();
        MachineRecipe first = RecipeTestSupport.create(MMCR.id("factory_selected_pool_first_recipe"), firstPool,
                20, List.of(), List.of());
        MachineRecipe second = RecipeTestSupport.create(MMCR.id("factory_selected_pool_second_recipe"), secondPool,
                20, List.of(), List.of());
        Map<ResourceLocation, MachineRecipe> previous = RecipeRegistry.dynamicSnapshot();
        try {
            RecipeRegistry.replaceDynamic(Map.of(first.id(), first, second.id(), second), ignored -> true);
            FactoryRecipeThread thread = FactoryRecipeThread.simple(controller);

            assertThat(thread.candidatesFor(null)).containsExactly(second);
            assertThat(thread.searchAndStartRecipe(null, 1,
                    controller.runtimeSnapshot().structure().version())).isTrue();
            assertThat(thread.runtime().recipe()).isEqualTo(second);
        } finally {
            RecipeRegistry.replaceDynamic(previous, ignored -> true);
        }
    }

    private record TestRequirement(RequirementType<TestRequirement> type, RecipeModifier.IOType io)
            implements MachineRequirement {
    }
}
