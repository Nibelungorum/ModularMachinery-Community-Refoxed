package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Recipe-level coverage for network output admission rather than local slot limits.
 *
 * @author howxu <dev@howxu.cn>
 */
class AE2NetworkOutputPlanningTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @ParameterizedTest
    @ValueSource(ints = {4_095, 4_096, 4_097, 114_514})
    void recipeOutputsCopperBeyondLocalSlotsWithoutTransferEnergy(int amount) {
        GenericStackInv cache = cache();
        GenericStackInv network = network();
        CraftingContext context = context(items(cache, network));

        var result = context.planRequirements(List.of(item(amount)), 1L, Map.of());

        assertThat(result.successful()).isTrue();
        assertThat(cache.isEmpty()).isTrue();
        assertThat(network.isEmpty()).isTrue();
        assertThat(result.plan().commit()).isTrue();
        assertThat(network.getAmount(0)).isEqualTo(amount);
        assertThat(network.getKey(0)).isEqualTo(AEItemKey.of(Items.COPPER_INGOT));
        assertThat(cache.isEmpty()).isTrue();
    }

    @Test
    void parallelItemAndFluidOutputsKeepLongAmounts() {
        GenericStackInv cache = cache();
        GenericStackInv network = network();
        CraftingContext context = context(items(cache, network), fluids(cache, network));

        var result = context.planRequirements(List.of(item(1_073_743_872), fluid(1_073_743_872)),
                2L, Map.of());

        assertThat(result.successful()).isTrue();
        assertThat(network.isEmpty()).isTrue();
        assertThat(result.plan().commit()).isTrue();
        assertThat(network.getAmount(0)).isEqualTo(2_147_487_744L);
        assertThat(network.getAmount(1)).isEqualTo(2_147_487_744L);
        assertThat(cache.isEmpty()).isTrue();
    }

    @Test
    void sharedNetworkSpaceIsNotCountedAgainAcrossInterfacesOrRequirements() {
        GenericStackInv network = network(1);
        network.setCapacity(AEKeyType.items(), 5_000L);
        GenericStackInv first = cache();
        GenericStackInv second = cache();
        first.setCapacity(AEKeyType.items(), 0L);
        second.setCapacity(AEKeyType.items(), 0L);
        CraftingContext context = context(items(first, network), items(second, network));

        var blocked = context.planRequirements(List.of(item(6_000)), 1L, Map.of());
        assertThat(blocked.successful()).isFalse();
        assertThat(blocked.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_OUTPUT);
        var repeated = context.planRequirements(List.of(item(3_000), item(3_000)), 1L, Map.of());
        assertThat(repeated.successful()).isFalse();
        assertThat(network.isEmpty()).isTrue();

        var partial = context.planRequirements(List.of(item(3_000), item(3_000)), 1L,
                Map.of(1, OutputPolicy.ALLOW_PARTIAL));
        assertThat(partial.successful()).isTrue();
        assertThat(partial.plan().commit()).isTrue();
        assertThat(network.getAmount(0)).isEqualTo(5_000L);
    }

    @Test
    void disconnectedMixedOutputsCannotReserveTheSameLocalSlot() {
        GenericStackInv cache = new GenericStackInv(Set.of(AEKeyType.items(), AEKeyType.fluids()), null,
                GenericStackInv.Mode.STORAGE, 1);
        cache.setCapacity(AEKeyType.fluids(), 1_000L);
        CraftingContext context = context(items(cache, null), fluids(cache, null));

        var blocked = context.planRequirements(List.of(item(64), fluid(1_000)), 1L, Map.of());
        assertThat(blocked.successful()).isFalse();
        assertThat(cache.isEmpty()).isTrue();
        var itemOnly = context.planRequirements(List.of(item(64)), 1L, Map.of());
        assertThat(itemOnly.successful()).isTrue();
        assertThat(itemOnly.plan().commit()).isTrue();
        assertThat(cache.getAmount(0)).isEqualTo(64L);
    }

    @Test
    void networkShortfallUsesOnlyUnreservedLocalSpace() {
        GenericStackInv network = network(1);
        network.setCapacity(AEKeyType.items(), 100L);
        GenericStackInv cache = new GenericStackInv(Set.of(AEKeyType.items()), null,
                GenericStackInv.Mode.STORAGE, 1);
        CraftingContext context = context(items(cache, network));

        var blocked = context.planRequirements(List.of(item(100), item(65)), 1L, Map.of());
        assertThat(blocked.successful()).isFalse();
        assertThat(network.isEmpty()).isTrue();
        assertThat(cache.isEmpty()).isTrue();

        var result = context.planRequirements(List.of(item(100), item(64)), 1L, Map.of());
        assertThat(result.successful()).isTrue();
        assertThat(result.plan().commit()).isTrue();
        assertThat(network.getAmount(0)).isEqualTo(100L);
        assertThat(cache.getAmount(0)).isEqualTo(64L);
    }

    @Test
    void preparedOutputFailsSafelyAfterDisconnectAndCanBeReplanned() {
        GenericStackInv cache = cache();
        GenericStackInv network = network();
        AtomicReference<MEStorage> connected = new AtomicReference<>(network);
        CraftingContext context = context(new ItemBusCapability(AE2NativeAdapters.outputItems(cache,
                connected::get, IActionSource.empty(), () -> {}), IOType.OUTPUT));
        var result = context.planRequirements(List.of(item(114_514)), 1L, Map.of());
        assertThat(result.successful()).isTrue();

        connected.set(null);
        assertThat(result.plan().commit()).isFalse();
        assertThat(cache.isEmpty()).isTrue();
        assertThat(network.isEmpty()).isTrue();
        connected.set(network);
        var retry = context.planRequirements(List.of(item(114_514)), 1L, Map.of());
        assertThat(retry.successful()).isTrue();
        assertThat(retry.plan().commit()).isTrue();
        assertThat(network.getAmount(0)).isEqualTo(114_514L);
    }

    @Test
    void networkOutputUsesMainThreadPlanningInsteadOfLocalOnlyWorkerSnapshot() {
        GenericStackInv cache = cache();
        GenericStackInv network = network();
        CraftingContext context = context(items(cache, network), fluids(cache, network));

        assertThat(context.planAsync(List.of(item(114_514), fluid(114_514)), 1L)
                .plan().mainThreadRequirements()).containsExactly(0, 1);
        assertThat(network.isEmpty()).isTrue();
    }

    @Test
    void wholeDestinationActionsCannotExtractOrReachOrdinarySlotHandlers() {
        ItemStack copper = new ItemStack(Items.COPPER_INGOT);
        FluidStack water = new FluidStack(Fluids.WATER, 1);
        assertThatIllegalArgumentException().isThrownBy(() -> new CapabilityRequests.ItemAction(-1, copper, 1L, false));
        assertThatIllegalArgumentException().isThrownBy(() -> new CapabilityRequests.FluidAction(-1, water, 1L, false));
        assertThatIllegalArgumentException().isThrownBy(() -> new CapabilityRequests.ResourceAction<>(-1, copper, 1L, false));
        GenericStackInv cache = cache();
        var items = new ItemBusCapability(AE2NativeAdapters.items(cache), IOType.OUTPUT);
        var fluids = new FluidHatchCapability(AE2NativeAdapters.fluids(cache), IOType.OUTPUT);
        assertThat(items.prepare(new CapabilityRequests.ItemRequest(items.type(), IOType.OUTPUT, 1L,
                List.of(new CapabilityRequests.ItemAction(-1, copper, 1L, true)))).commit().success()).isFalse();
        assertThat(fluids.prepare(new CapabilityRequests.FluidRequest(fluids.type(), IOType.OUTPUT, 1L,
                List.of(new CapabilityRequests.FluidAction(-1, water, 1L, true)))).commit().success()).isFalse();
        assertThat(cache.isEmpty()).isTrue();
    }

    @Test
    void differentKeysCompetingForLastNetworkSlotRetainNativePartialCommitSemantics() {
        GenericStackInv cache = cache();
        cache.setCapacity(AEKeyType.items(), 0L);
        GenericStackInv network = network(1);
        CraftingContext context = context(items(cache, network));
        var iron = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new ItemStack(Items.IRON_INGOT));

        var result = context.planOutputRequirements(List.of(item(1), iron), 1L, false);

        assertThat(result.successful()).isTrue();
        assertThat(network.isEmpty()).isTrue();
        assertThat(result.plan().commit()).isFalse();
        assertThat(network.getKey(0)).isEqualTo(AEItemKey.of(Items.COPPER_INGOT));
        assertThat(network.getAmount(0)).isEqualTo(1L);
        assertThat(cache.isEmpty()).isTrue();
    }

    @Test
    void independentCopperPlansCannotReclaimCommittedCapacityAcrossPlanningRounds() {
        GenericStackInv network = network(1);
        network.setCapacity(AEKeyType.items(), 114_514L);
        GenericStackInv first = cache();
        GenericStackInv second = cache();
        first.setCapacity(AEKeyType.items(), 0L);
        second.setCapacity(AEKeyType.items(), 0L);
        CraftingContext firstContext = context(items(first, network));
        CraftingContext secondContext = context(items(second, network));
        List<MachineRequirement> requirement = List.of(item(114_514));
        var initial = firstContext.planOutputRequirements(requirement, 1L, false);
        var stale = secondContext.planOutputRequirements(requirement, 1L, false);

        assertThat(initial.successful()).isTrue();
        assertThat(stale.successful()).isTrue();
        assertThat(initial.plan().commit()).isTrue();
        assertThat(stale.plan().commit()).isFalse();
        assertThat(secondContext.planOutputRequirements(requirement, 1L, false).successful()).isFalse();
        assertThat(network.getAmount(0)).isEqualTo(114_514L);
        assertThat(first.isEmpty()).isTrue();
        assertThat(second.isEmpty()).isTrue();

        assertThat(network.extract(AEItemKey.of(Items.COPPER_INGOT), 114_514L,
                Actionable.MODULATE, IActionSource.empty())).isEqualTo(114_514L);
        var retry = secondContext.planOutputRequirements(requirement, 1L, false);
        assertThat(retry.successful()).isTrue();
        assertThat(retry.plan().commit()).isTrue();
        assertThat(network.getAmount(0)).isEqualTo(114_514L);
    }

    @Test
    void repeatedOutputsUseRemainingCapacityRatherThanSubtractingFromEachRequest() {
        GenericStackInv network = network(1);
        network.setCapacity(AEKeyType.items(), 12L);
        GenericStackInv cache = cache();
        cache.setCapacity(AEKeyType.items(), 0L);
        CraftingContext context = context(items(cache, network));

        var result = context.planRequirements(List.of(item(4), item(4), item(8)), 1L,
                Map.of(2, OutputPolicy.ALLOW_PARTIAL));

        assertThat(result.successful()).isTrue();
        assertThat(result.outputSimulations()).extracting(simulation -> simulation.accepted())
                .containsExactly(4L, 4L, 4L);
        assertThat(network.isEmpty()).isTrue();
        assertThat(result.plan().commit()).isTrue();
        assertThat(network.getAmount(0)).isEqualTo(12L);
        assertThat(cache.isEmpty()).isTrue();
    }

    private static ItemBusCapability items(GenericStackInv cache, MEStorage network) {
        return new ItemBusCapability(AE2NativeAdapters.outputItems(cache, () -> network,
                IActionSource.empty(), () -> {}), IOType.OUTPUT);
    }

    private static FluidHatchCapability fluids(GenericStackInv cache, MEStorage network) {
        return new FluidHatchCapability(AE2NativeAdapters.outputFluids(cache, () -> network,
                IActionSource.empty(), () -> {}), IOType.OUTPUT);
    }

    private static ItemRequirement item(int amount) {
        return new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                new ItemStack(Items.COPPER_INGOT, amount));
    }

    private static FluidRequirement fluid(int amount) {
        return new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new FluidStack(Fluids.WATER, amount));
    }

    private static CraftingContext context(MachineCapability... capabilities) {
        return new CraftingContext(new CapabilitySnapshot(List.of(capabilities)));
    }

    private static GenericStackInv cache() {
        GenericStackInv cache = new GenericStackInv(Set.of(AEKeyType.items(), AEKeyType.fluids()), null,
                GenericStackInv.Mode.STORAGE, 9);
        cache.setCapacity(AEKeyType.fluids(), 1_000L);
        return cache;
    }

    private static GenericStackInv network() {
        return network(2);
    }

    private static GenericStackInv network(int slots) {
        return new GenericStackInv(Set.of(AEKeyType.items(), AEKeyType.fluids()), null,
                GenericStackInv.Mode.STORAGE, slots) {
            @Override public long getMaxAmount(AEKey key) { return getCapacity(key.getType()); }
            @Override public long insert(int slot, AEKey key, long amount, Actionable mode) {
                return super.insert(slot, key, Math.min(amount, Math.max(0L, getMaxAmount(key) - getAmount(slot))), mode);
            }
        };
    }
}
