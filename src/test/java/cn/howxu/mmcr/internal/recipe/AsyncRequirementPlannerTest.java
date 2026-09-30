package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityOperation;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityPlanner;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.async.AsyncResourceAction;
import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.capability.NativeAsyncResourceValues;
import cn.howxu.mmcr.internal.storage.BulkItemStorage;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import com.mojang.authlib.GameProfile;
import net.minecraft.SystemReport;
import net.minecraft.util.debugchart.SampleLogger;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.io.IOException;
import java.net.Proxy;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies worker-safe requirement planning from native capability descriptors.
 *
 * @author howxu <dev@howxu.cn>
 */
class AsyncRequirementPlannerTest {
    private static final AsyncResourceValue IRON = new AsyncResourceValue(MMCR.id("iron_ingot"), "{}");
    private static final AsyncResourceValue WATER = new AsyncResourceValue(MMCR.id("water"), "{}");

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void supported_resource_requirements_plan_to_logical_operations_from_value_descriptors() {
        AsyncRequirementPlanner.PlanResult result = new AsyncRequirementPlanner().plan(List.of(
                resourceRequirement(4, MMCR.id("item"), IRON, 2L, false),
                resourceRequirement(7, MMCR.id("fluid"), WATER, 1_000L, false)), List.of(
                resourceCapability(MMCR.id("item"), IRON, 4L, 64L),
                resourceCapability(MMCR.id("fluid"), WATER, 1_000L, 4_000L)));

        assertThat(result.operations()).hasSize(2);
        assertThat(result.operations()).allSatisfy(operation ->
                assertThat(operation.operation()).isInstanceOf(AsyncCapabilityOperation.Group.class));
        assertThat(result.mainThreadRequirements()).isEmpty();
    }

    @Test
    void unsupported_requirement_falls_back_by_original_index_without_discarding_other_operations() {
        AsyncRequirementPlanner.PlanResult result = new AsyncRequirementPlanner().plan(List.of(
                resourceRequirement(3, MMCR.id("item"), IRON, 2L, false),
                new AsyncRequirementPlanner.Requirement(11, 10L, List.of(
                        new AsyncCapabilityRequest.Scalar(MMCR.id("heat"), 1L, 10L, false)))), List.of(
                resourceCapability(MMCR.id("item"), IRON, 4L, 64L)));

        assertThat(result.operations()).singleElement().satisfies(operation ->
                assertThat(operation.requirementIndex()).isEqualTo(3));
        assertThat(result.mainThreadRequirements()).containsExactly(11);
    }

    @Test
    void native_capabilities_expose_live_async_planning_facets() {
        assertThat(new ItemBusCapability(new BulkItemStorage(64L, null), IOType.INPUT)
                .facet(AsyncPlanningFacet.class)).isPresent();
        assertThat(new FluidHatchCapability(new LongFluidStorage(1_000L, null), IOType.INPUT)
                .facet(AsyncPlanningFacet.class)).isPresent();
        assertThat(new EnergyHatchCapability(new LongEnergyStorage(1_000L, 1_000L, null), IOType.INPUT)
                .facet(AsyncPlanningFacet.class)).isPresent();
    }

    @Test
    void capture_scope_reuses_one_snapshot_for_the_same_physical_storage() throws Exception {
        BulkItemStorage storage = new BulkItemStorage(64L, null);
        storage.forceInsert(new ItemStack(Items.IRON_INGOT, 1), 4L, false);
        ItemRequirement input = new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                ItemStack.EMPTY);
        CraftingContext context = new CraftingContext(new CapabilitySnapshot(List.of(
                new ItemBusCapability(storage, IOType.INPUT), new ItemBusCapability(storage, IOType.INPUT))));

        installCurrentServerForTesting();
        AsyncRequirementPlanner.PreparedPlan prepared;
        try (AsyncPlanningFacet.CaptureScope ignored = AsyncPlanningFacet.beginCaptureScope()) {
            prepared = context.planAsync(List.of(input), 1L);
        } finally {
            clearCurrentServerForTesting();
        }

        assertThat(prepared.capabilities()).hasSize(2);
        assertThat(prepared.capabilities().get(0).snapshot())
                .isSameAs(prepared.capabilities().get(1).snapshot());
    }

    @Test
    void shared_snapshot_cannot_be_reserved_twice_through_capability_aliases() {
        var capabilityId = MMCR.id("energy");
        AsyncCapabilitySnapshot snapshot = new AsyncCapabilitySnapshot.Scalar(capabilityId, 6L, 6L, 6L);
        AsyncRequirementPlanner.Capability capability = new AsyncRequirementPlanner.Capability(
                new AsyncCapabilityPlanner.Scalar(capabilityId), snapshot, Set.of(IOType.INPUT));
        AsyncRequirementPlanner.Requirement requirement = new AsyncRequirementPlanner.Requirement(0, 10L,
                IOType.INPUT, List.of(new AsyncCapabilityRequest.Scalar(capabilityId, 1L, 10L, false)));

        AsyncRequirementPlanner.PlanResult result = new AsyncRequirementPlanner().plan(
                List.of(requirement), List.of(capability, capability));

        assertThat(result.operations()).isEmpty();
        assertThat(result.mainThreadRequirements()).containsExactly(0);
    }

    @Test
    void crafting_context_returns_a_worker_safe_descriptor_with_ordered_fallback_indexes() {
        AsyncRequirementPlanner.PreparedPlan prepared = new CraftingContext(new CapabilitySnapshot(List.of()))
                .planAsync(List.of(new EnergyRequirement(4L)), 1L);

        assertThat(prepared.plan().operations()).isEmpty();
        assertThat(prepared.plan().mainThreadRequirements()).containsExactly(0);
    }

    @Test
    void zero_consume_chance_item_input_does_not_create_an_async_extraction_request() throws Exception {
        BulkItemStorage storage = new BulkItemStorage(64L, null);
        storage.forceInsert(new ItemStack(Items.IRON_INGOT, 1), 2L, false);
        ItemRequirement input = new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 2,
                ItemStack.EMPTY, 1F, List.of(), DataComponentPredicateSet.EMPTY, 0F);
        CraftingContext context = new CraftingContext(new CapabilitySnapshot(List.of(
                new ItemBusCapability(storage, IOType.INPUT))));

        installCurrentServerForTesting();
        AsyncRequirementPlanner.PreparedPlan prepared;
        try {
            prepared = context.planAsync(List.of(input), 1L);
        } finally {
            clearCurrentServerForTesting();
        }
        assertThat(prepared.initialMainThreadRequirements()).containsExactly(0);
        var fallback = context.planInputRequirements(List.of(input), 1L, Set.of(), Set.of());
        assertThat(fallback.successful()).isTrue();
        assertThat(fallback.plan().commit()).isTrue();
        assertThat(storage.amount(0)).isEqualTo(2L);
    }

    @Test
    void partial_consume_chance_item_input_does_not_create_an_async_extraction_request() throws Exception {
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 1);
        ItemRequirement input = new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 2,
                ItemStack.EMPTY, 1F, List.of(), DataComponentPredicateSet.EMPTY, 0.5F);

        assertThat(prepareAsyncInput(input, NativeAsyncResourceValues.item(iron), 2L)).isNull();
    }

    @Test
    void zero_consume_chance_fluid_input_does_not_create_an_async_extraction_request() throws Exception {
        FluidStack water = new FluidStack(Fluids.WATER, 1);
        FluidRequirement input = new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 100,
                FluidStack.EMPTY, 1F, List.of(), 0F);

        assertThat(prepareAsyncInput(input, NativeAsyncResourceValues.fluid(water), 100L)).isNull();
    }

    @Test
    void partial_consume_chance_fluid_input_does_not_create_an_async_extraction_request() throws Exception {
        FluidStack water = new FluidStack(Fluids.WATER, 1);
        FluidRequirement input = new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 100,
                FluidStack.EMPTY, 1F, List.of(), 0.5F);

        assertThat(prepareAsyncInput(input, NativeAsyncResourceValues.fluid(water), 100L)).isNull();
    }

    @Test
    void batch_limited_energy_groups_revalidate_and_roll_back_when_storage_changes() throws Exception {
        LongEnergyStorage storage = new LongEnergyStorage(100L, 20L, null);
        storage.setAmount(60L);
        EnergyHatchCapability capability = new EnergyHatchCapability(storage, IOType.INPUT);
        AsyncCapabilityOperation operation = new AsyncCapabilityPlanner.Scalar(capability.type().id()).plan(
                new AsyncCapabilitySnapshot.Scalar(capability.type().id(), 60L, 100L, 20L),
                new AsyncCapabilityRequest.Scalar(capability.type().id(), 3L, 60L, false)).orElseThrow();
        storage.setAmount(40L);

        assertThat(commit(capability, operation).success()).isFalse();

        assertThat(storage.getAmountAsLong()).isEqualTo(40L);
    }

    private static AsyncRequirementPlanner.Requirement resourceRequirement(int index,
                                                                            net.minecraft.resources.ResourceLocation capabilityId,
                                                                            AsyncResourceValue resource,
                                                                            long amount, boolean insert) {
        return new AsyncRequirementPlanner.Requirement(index, amount, List.of(
                new AsyncCapabilityRequest.Resource(capabilityId, 1L,
                        List.of(new AsyncResourceAction(resource, amount, insert)))));
    }

    private static AsyncRequirementPlanner.Capability resourceCapability(net.minecraft.resources.ResourceLocation capabilityId,
                                                                          AsyncResourceValue resource,
                                                                          long amount, long capacity) {
        return new AsyncRequirementPlanner.Capability(new AsyncCapabilityPlanner.Resource(capabilityId),
                new AsyncCapabilitySnapshot.Resource(capabilityId, List.of(
                        new AsyncCapabilitySnapshot.ResourceSlot(Optional.of(resource), amount, capacity))));
    }

    private static Object prepareAsyncInput(MachineRequirement input, AsyncResourceValue resource, long amount)
            throws Exception {
        AsyncRequirementPlanner.Capability capability = new AsyncRequirementPlanner.Capability(
                new AsyncCapabilityPlanner.Resource(input.type().id()),
                new AsyncCapabilitySnapshot.Resource(input.type().id(), List.of(
                        new AsyncCapabilitySnapshot.ResourceSlot(Optional.of(resource), amount, amount))), Set.of(IOType.INPUT));
        Method method = CraftingContext.class.getDeclaredMethod("prepareAsyncRequirement", int.class,
                MachineRequirement.class, long.class, List.class);
        method.setAccessible(true);
        return method.invoke(null, 0, input, 1L, List.of(capability));
    }

    private static void installCurrentServerForTesting() throws Exception {
        TestServer server = allocate(TestServer.class);
        server.serverThread = Thread.currentThread();
        Field currentServer = ServerLifecycleHooks.class.getDeclaredField("currentServer");
        currentServer.setAccessible(true);
        currentServer.set(null, server);
    }

    private static void clearCurrentServerForTesting() throws Exception {
        Field currentServer = ServerLifecycleHooks.class.getDeclaredField("currentServer");
        currentServer.setAccessible(true);
        currentServer.set(null, null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (T) ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
    }

    /**
     * Minimal server identity used to exercise AsyncPlanningFacet's main-thread guard.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class TestServer extends MinecraftServer {
        private Thread serverThread;

        private TestServer() {
            super(null, null, null, null, Proxy.NO_PROXY, null, null, null);
        }

        @Override
        public Thread getRunningThread() {
            return serverThread;
        }

        @Override
        protected boolean initServer() throws IOException {
            return false;
        }

        @Override
        public int getOperatorUserPermissionLevel() {
            return 4;
        }

        @Override
        public int getFunctionCompilationLevel() {
            return 4;
        }

        @Override
        public boolean shouldRconBroadcast() {
            return false;
        }

        @Override
        public boolean isDedicatedServer() {
            return false;
        }

        @Override
        public int getRateLimitPacketsPerSecond() {
            return 0;
        }

        @Override
        public boolean isEpollEnabled() {
            return false;
        }

        @Override
        public boolean isCommandBlockEnabled() {
            return false;
        }

        @Override
        public boolean isPublished() {
            return false;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        @Override
        public boolean isSingleplayerOwner(GameProfile profile) {
            return false;
        }

        @Override
        protected SampleLogger getTickTimeLogger() {
            return null;
        }

        @Override
        public boolean isTickTimeLoggingEnabled() {
            return false;
        }

        @Override
        public int getMaxPlayers() {
            return 1;
        }

        @Override
        public SystemReport fillServerSystemReport(SystemReport report) {
            return report;
        }
    }

    private static CapabilityResult commit(EnergyHatchCapability capability, AsyncCapabilityOperation operation)
            throws Exception {
        Method method = EnergyHatchCapability.class.getDeclaredMethod("commitAsync", AsyncCapabilityOperation.class);
        method.setAccessible(true);
        return (CapabilityResult) method.invoke(capability, operation);
    }
}
