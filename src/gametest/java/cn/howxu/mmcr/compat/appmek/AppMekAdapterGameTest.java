package cn.howxu.mmcr.compat.appmek;

import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.config.Actionable;
import appeng.me.helpers.BaseActionSource;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.api.capability.async.AsyncResourceAction;
import cn.howxu.mmcr.api.capability.sync.CapabilitySyncRegistry;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.machine.definition.MachineIoPlan;
import cn.howxu.mmcr.api.machine.definition.MachineIoView;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.compat.kubejs.KubeJSApi;
import cn.howxu.mmcr.internal.api.facade.runtime.IoAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.RecipeAdapters;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.appmek.loaded.InventoryChemicalHandler;
import cn.howxu.mmcr.compat.appmek.loaded.MEChemicalCapability;
import cn.howxu.mmcr.compat.appmek.loaded.NetworkChemicalHandler;
import cn.howxu.mmcr.compat.appmek.loaded.OutputChemicalHandler;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.material.Fluids;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import cn.howxu.mmcr.util.IOType;

/**
 * Chemical handler simulation, physical reservations and long-quantity behavior.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class AppMekAdapterGameTest {
    public void queriesAndExistingApisSeeAllChemicalKinds(GameTestHelper helper) {
        GenericStackInv inventory = AppMekGameTestFixtures.inventory(3, 5_000L);
        MekanismKey oxygen = MekanismKey.of(AppMekGameTestFixtures.chemical("oxygen", 1L));
        MekanismKey hydrogen = MekanismKey.of(AppMekGameTestFixtures.chemical("hydrogen", 1L));
        inventory.setStack(0, new GenericStack(oxygen, 600L));
        inventory.setStack(1, new GenericStack(oxygen, 700L));
        inventory.setStack(2, new GenericStack(hydrogen, 900L));
        MEChemicalCapability first = new MEChemicalCapability(null, new InventoryChemicalHandler(inventory), CapabilityDirections.input(), false);
        MEChemicalCapability alias = new MEChemicalCapability(null, new InventoryChemicalHandler(inventory), CapabilityDirections.input(), false);
        CapabilitySnapshot snapshot = new CapabilitySnapshot(List.of(first, alias));
        var view = IoAdapters.wrap(new MachineIoView(snapshot));
        helper.assertTrue(view.chemicalInputs().size() == 2 && view.chemicalAmount(oxygen.getId()) == 1_300L
                        && view.chemicalAmount(hydrogen.getId()) == 900L,
                "Public IO snapshot aggregates chemical slots without double-counting aliases");
        MachineIoPlan kubejs = new MachineIoPlan(snapshot);
        kubejs.addInput(new KubeJSApi().chemicalInput("mekanism:oxygen", 1_000L));
        helper.assertTrue(kubejs.simulate().inputsSatisfied() && kubejs.commit().successful(),
                "Existing KubeJS chemical input commits against an ME inventory");
        var declaration = RecipeAdapters.recipe(MMCR.id("appmek_public_chemical_recipe"))
                .recipePool(MMCR.id("test_cube")).duration(1).inputChemical(oxygen.getId(), 300L).build();
        var transaction = IoAdapters.wrap(new MachineIoPlan(snapshot));
        declaration.requirements().forEach(transaction::addInput);
        helper.assertTrue(transaction.simulate().inputsSatisfied() && transaction.commit().successful(),
                "Public RecipeDraft chemical requirements execute through the existing IO transaction");
        helper.assertTrue(inventory.getAmount(0) + inventory.getAmount(1) == 0L && inventory.getAmount(2) == 900L,
                "Script and public API consume only their requested chemical");
        AppMekGameTestFixtures.LimitedStorage network = new AppMekGameTestFixtures.LimitedStorage(2_000L);
        network.insert(oxygen, 800L, Actionable.MODULATE, new BaseActionSource());
        var networkView = new MachineIoView(new CapabilitySnapshot(List.of(
                new MEChemicalCapability(null, new NetworkChemicalHandler(() -> network, () -> List.of(oxygen), new BaseActionSource()), CapabilityDirections.input(), false),
                new MEChemicalCapability(null, new NetworkChemicalHandler(() -> network, () -> List.of(oxygen), new BaseActionSource()), CapabilityDirections.input(), false))));
        helper.assertTrue(networkView.chemicalAmount(oxygen.getId()) == 800L, "Two configured interfaces query one network key once");
        helper.succeed();
    }

    public void tagQueriesCountOnlyMatchingChemicals(GameTestHelper helper) {
        ChemicalStack steam = AppMekGameTestFixtures.chemical("steam", 600L);
        ChemicalStack oxygen = AppMekGameTestFixtures.chemical("oxygen", 900L);
        var tag = steam.getChemicalHolder().tags().filter(candidate -> !oxygen.getChemicalHolder().is(candidate)).findFirst().orElseThrow();
        GenericStackInv inventory = AppMekGameTestFixtures.inventory(3, 5_000L);
        inventory.setStack(0, new GenericStack(MekanismKey.of(steam), 600L));
        inventory.setStack(1, new GenericStack(MekanismKey.of(steam), 700L));
        inventory.setStack(2, new GenericStack(MekanismKey.of(oxygen), 900L));
        var view = new MachineIoView(new CapabilitySnapshot(List.of(new MEChemicalCapability(null,
                new InventoryChemicalHandler(inventory), CapabilityDirections.input(), false))));
        helper.assertTrue(view.chemicalTagAmount(tag.location()) == 1_300L, "Tag query sums matching slots without including other chemical kinds");
        helper.succeed();
    }

    public void multiSlotSyncPreservesMixedResourcesAndNetworkKeys(GameTestHelper helper) {
        GenericStackInv source = AppMekGameTestFixtures.inventory(4, 5_000L);
        GenericStackInv target = AppMekGameTestFixtures.inventory(4, 5_000L);
        source.setStack(0, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 5L));
        source.setStack(1, new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000L));
        MekanismKey oxygen = MekanismKey.of(AppMekGameTestFixtures.chemical("oxygen", 1L));
        source.setStack(2, new GenericStack(oxygen, 1_500L));
        var encoded = new MEChemicalCapability(null, new InventoryChemicalHandler(source), CapabilityDirections.input(), false);
        var decoded = new MEChemicalCapability(null, new InventoryChemicalHandler(target), CapabilityDirections.input(), false);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            CapabilitySnapshot sourceSnapshot = new CapabilitySnapshot(List.of(
                    new ItemBusCapability(AE2NativeAdapters.items(source), IOType.INPUT),
                    new FluidHatchCapability(AE2NativeAdapters.fluids(source), IOType.INPUT), encoded));
            CapabilitySnapshot targetSnapshot = new CapabilitySnapshot(List.of(
                    new ItemBusCapability(AE2NativeAdapters.items(target), IOType.INPUT),
                    new FluidHatchCapability(AE2NativeAdapters.fluids(target), IOType.INPUT), decoded));
            for (var entry : CapabilitySyncRegistry.encode(sourceSnapshot, buffer)) {
                CapabilitySyncRegistry.decode(targetSnapshot, entry, buffer);
            }
            helper.assertTrue(target.getStack(0).equals(source.getStack(0)) && target.getStack(1).equals(source.getStack(1))
                            && target.getStack(2).equals(source.getStack(2)),
                    "Chemical sync updates chemical slots and preserves item and fluid projections");
            source.setStack(0, new GenericStack(oxygen, 300L));
            source.setStack(1, new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 4L));
            source.setStack(2, new GenericStack(AEFluidKey.of(Fluids.WATER), 500L));
            for (var entry : CapabilitySyncRegistry.encode(sourceSnapshot, buffer)) {
                CapabilitySyncRegistry.decode(targetSnapshot, entry, buffer);
            }
            for (int slot = 0; slot < source.size(); slot++) helper.assertTrue(Objects.equals(source.getStack(slot), target.getStack(slot)),
                    "Full mixed sync handles a changed resource family at slot " + slot);
            buffer.clear();
            List<AEKey> keys = new ArrayList<>(List.of(oxygen, MekanismKey.of(AppMekGameTestFixtures.chemical("hydrogen", 1L))));
            NetworkChemicalHandler mirror = new NetworkChemicalHandler(() -> {
                throw new AssertionError("Client sync must not resolve live ME storage");
            }, () -> keys, new BaseActionSource(), () -> true);
            mirror.setChemicalInTank(0, oxygen.withAmount(1_500L));
            var client = new MEChemicalCapability(null, mirror, CapabilityDirections.input(), false);
            client.encode(buffer);
            client.decode(buffer);
            Collections.reverse(keys);
            helper.assertTrue(mirror.getChemicalInTank(1).getAmount() == 1_500L && mirror.getChemicalInTank(0).isEmpty(),
                    "Network sync mirror follows chemical keys when configuration order changes");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    public void localAsyncSnapshotKeepsMixedNetworkInputOnMainThread(GameTestHelper helper) {
        GenericStackInv inventory = AppMekGameTestFixtures.inventory(1, 5_000L);
        MekanismKey oxygen = MekanismKey.of(AppMekGameTestFixtures.chemical("oxygen", 1L));
        inventory.setStack(0, new GenericStack(oxygen, 600L));
        MEChemicalCapability local = new MEChemicalCapability(null, new InventoryChemicalHandler(inventory), CapabilityDirections.input(), false);
        AsyncPlanningFacet facet = local.facet(AsyncPlanningFacet.class).orElseThrow();
        var alias = new MEChemicalCapability(null, new InventoryChemicalHandler(inventory), CapabilityDirections.input(), false);
        try (var scope = AsyncPlanningFacet.beginCaptureScope()) {
            helper.assertTrue(facet.captureSnapshot() == alias.facet(AsyncPlanningFacet.class).orElseThrow().captureSnapshot(),
                    "Aliased ME chemical views capture one physical inventory snapshot");
            var prepared = new CraftingContext(new CapabilitySnapshot(List.of(local, alias))).planAsync(
                    List.of(LoadedChemicalRequirement.input(ChemicalIngredient.chemical(oxygen.getId(), 1_000L))), 1L);
            helper.assertTrue(prepared.plan().mainThreadRequirements().equals(List.of(0)),
                    "Async aliases cannot reserve the same chemical inventory twice");
        }
        AsyncCapabilitySnapshot.Resource snapshot = (AsyncCapabilitySnapshot.Resource) facet.captureSnapshot();
        var planner = facet.workerPlanner();
        var operation = CompletableFuture.supplyAsync(() -> planner.plan(snapshot, new AsyncCapabilityRequest.Resource(local.type().id(),
                1L, List.of(new AsyncResourceAction(snapshot.slots().getFirst().resource().orElseThrow(), 100L, false))))).join().orElseThrow();
        helper.assertTrue(inventory.getAmount(0) == 600L && facet.commit(operation).success() && inventory.getAmount(0) == 500L,
                "Worker plans immutable chemical values and only main-thread commit changes inventory");
        AppMekGameTestFixtures.LimitedStorage network = new AppMekGameTestFixtures.LimitedStorage(2_000L);
        network.insert(oxygen, 800L, Actionable.MODULATE, new BaseActionSource());
        var remote = new MEChemicalCapability(null, new NetworkChemicalHandler(() -> network, () -> List.of(oxygen), new BaseActionSource()), CapabilityDirections.input(), false);
        var context = new CraftingContext(new CapabilitySnapshot(List.of(local, remote)));
        var requirement = LoadedChemicalRequirement.input(ChemicalIngredient.chemical(oxygen.getId(), 1_000L));
        helper.assertTrue(remote.facet(AsyncPlanningFacet.class).isEmpty() && context.planAsync(List.of(requirement), 1L)
                .initialMainThreadRequirements().equals(List.of(0)), "Mixed local and network chemical input uses full main-thread planning");
        helper.assertTrue(context.planInputRequirements(List.of(requirement), 1L, Set.of(), Set.of()).successful(),
                "Fallback includes both local and network chemical supply");
        helper.succeed();
    }

    public void partialNetworkOutputConservesRemainder(GameTestHelper helper) {
        GenericStackInv inventory = AppMekGameTestFixtures.inventory(2, 5_000L);
        AppMekGameTestFixtures.LimitedStorage network = new AppMekGameTestFixtures.LimitedStorage(600L);
        OutputChemicalHandler handler = new OutputChemicalHandler(inventory, () -> network, new BaseActionSource(), () -> {});
        ChemicalStack oxygen = AppMekGameTestFixtures.chemical("oxygen", 1_500L);
        MekanismKey key = MekanismKey.of(oxygen);
        helper.assertTrue(handler.insertChemical(oxygen, Action.SIMULATE).isEmpty(), "Simulation accepts network and cache output");
        helper.assertTrue(network.amount(key) == 0L && inventory.isEmpty(), "Simulation preserves network and cache");
        ChemicalStack remainder = handler.insertChemical(oxygen, Action.EXECUTE);
        helper.assertTrue(network.amount(key) == 600L && inventory.getAmount(0) == 900L && remainder.isEmpty(),
                "Only the real network remainder goes to local storage");
        helper.assertTrue(network.amount(key) + inventory.getAmount(0) + remainder.getAmount() == oxygen.getAmount(),
                "Chemical output conserves total amount");
        helper.succeed();
    }

    public void disconnectedOutputReturnsUnacceptedAmount(GameTestHelper helper) {
        GenericStackInv inventory = AppMekGameTestFixtures.inventory(1, 300L);
        OutputChemicalHandler handler = new OutputChemicalHandler(inventory, () -> null, new BaseActionSource(), () -> {});
        ChemicalStack requested = AppMekGameTestFixtures.chemical("oxygen", 700L);
        ChemicalStack remainder = handler.insertChemical(requested, Action.EXECUTE);
        helper.assertTrue(inventory.getAmount(0) == 300L && remainder.getAmount() == 400L,
                "Disconnected output caches what fits and returns the exact unaccepted amount");
        helper.succeed();
    }

    public void networkInputFiltersDeduplicatesAndSharesReservations(GameTestHelper helper) {
        AppMekGameTestFixtures.LimitedStorage network = new AppMekGameTestFixtures.LimitedStorage(10_000L);
        MekanismKey oxygen = MekanismKey.of(AppMekGameTestFixtures.chemical("oxygen", 1L));
        MekanismKey hydrogen = MekanismKey.of(AppMekGameTestFixtures.chemical("hydrogen", 1L));
        BaseActionSource source = new BaseActionSource();
        network.insert(oxygen, 1_000L, Actionable.MODULATE, source);
        network.insert(hydrogen, 2_000L, Actionable.MODULATE, source);
        NetworkChemicalHandler first = new NetworkChemicalHandler(() -> network, () -> List.of(oxygen, oxygen), source);
        NetworkChemicalHandler second = new NetworkChemicalHandler(() -> network, () -> List.of(oxygen), source);
        helper.assertTrue(first.getChemicalTanks() == 1, "Repeated configured chemical key has only one network slot");
        MachineIoPlan plan = new MachineIoPlan(new CapabilitySnapshot(List.of(
                new MEChemicalCapability(null, first, CapabilityDirections.input(), false),
                new MEChemicalCapability(null, second, CapabilityDirections.input(), false))));
        plan.addInput(LoadedChemicalRequirement.input(ChemicalIngredient.chemical(oxygen.getId(), 1_500L)));
        helper.assertTrue(!plan.simulate().inputsSatisfied(), "Two interfaces cannot double-count a network key");
        helper.assertTrue(first.extractChemical(0, 400L, Action.EXECUTE).getAmount() == 400L,
                "Configured network chemical is extracted");
        helper.assertTrue(network.amount(oxygen) == 600L && network.amount(hydrogen) == 2_000L,
                "Unconfigured chemicals are not consumed");
        helper.succeed();
    }

    public void localSimulationPreservesInventory(GameTestHelper helper) {
        GenericStackInv inventory = AppMekGameTestFixtures.inventory(2, 5_000L);
        InventoryChemicalHandler handler = new InventoryChemicalHandler(inventory);
        ChemicalStack oxygen = AppMekGameTestFixtures.chemical("oxygen", 1_500L);
        helper.assertTrue(handler.insertChemical(0, oxygen, Action.SIMULATE).isEmpty(), "Simulation accepts oxygen");
        helper.assertTrue(inventory.isEmpty(), "Simulation must not write inventory");
        helper.assertTrue(handler.insertChemical(0, oxygen, Action.EXECUTE).isEmpty(), "Execution accepts oxygen");
        helper.assertTrue(handler.extractChemical(0, 700L, Action.SIMULATE).getAmount() == 700L,
                "Extraction simulation reports actual availability");
        helper.assertTrue(inventory.getAmount(0) == 1_500L, "Extraction simulation preserves amount");
        helper.assertTrue(handler.extractChemical(0, 700L, Action.EXECUTE).getAmount() == 700L,
                "Execution extracts requested amount");
        helper.assertTrue(inventory.getAmount(0) == 800L, "Only committed extraction changes amount");
        helper.succeed();
    }

    public void multiSlotInputAndAliasedViewsRespectReservations(GameTestHelper helper) {
        GenericStackInv inventory = AppMekGameTestFixtures.inventory(2, 5_000L);
        MekanismKey oxygen = MekanismKey.of(AppMekGameTestFixtures.chemical("oxygen", 1L));
        inventory.setStack(0, new GenericStack(oxygen, 600L));
        inventory.setStack(1, new GenericStack(oxygen, 700L));
        MEChemicalCapability first = new MEChemicalCapability(null, new InventoryChemicalHandler(inventory),
                CapabilityDirections.input(), false);
        MEChemicalCapability second = new MEChemicalCapability(null, new InventoryChemicalHandler(inventory),
                CapabilityDirections.input(), false);
        ResourceLocation id = oxygen.getId();
        MachineIoPlan blocked = new MachineIoPlan(new CapabilitySnapshot(List.of(first, second)));
        blocked.addInput(LoadedChemicalRequirement.input(ChemicalIngredient.chemical(id, 2_000L)));
        helper.assertTrue(!blocked.simulate().inputsSatisfied(), "Aliased views cannot double-count chemical storage");
        helper.assertTrue(inventory.getAmount(0) + inventory.getAmount(1) == 1_300L,
                "Rejected simulation preserves both slots");
        MachineIoPlan plan = new MachineIoPlan(new CapabilitySnapshot(List.of(first)));
        plan.addInput(LoadedChemicalRequirement.input(ChemicalIngredient.chemical(id, 1_100L)));
        helper.assertTrue(plan.simulate().inputsSatisfied(), "Input can span two chemical slots");
        helper.assertTrue(plan.commit().successful(), "Multi-slot chemical input commits");
        helper.assertTrue(inventory.getAmount(0) + inventory.getAmount(1) == 200L,
                "Only the requested amount is consumed across slots");
        helper.succeed();
    }

    public void mixedViewsSharePhysicalSlotAndSyncPreservesItems(GameTestHelper helper) {
        GenericStackInv inventory = AppMekGameTestFixtures.inventory(1, 5_000L);
        PlanningReservations reservations = new PlanningReservations();
        var items = AE2NativeAdapters.items(inventory);
        InventoryChemicalHandler chemicals = new InventoryChemicalHandler(inventory);
        helper.assertTrue(reservations.reserveItemInsert(items, 0, new ItemStack(Items.IRON_INGOT), 1L, 64L),
                "Item output reserves the physical slot");
        ChemicalStack oxygen = AppMekGameTestFixtures.chemical("oxygen", 1L);
        helper.assertTrue(!reservations.reserveNativeInsert(chemicals.reservationIdentity(), 0,
                        chemicals.resourceKey(oxygen), chemicals.storedKey(0), chemicals.storedAmount(0), 5_000L, 1L),
                "Chemical view cannot claim the same empty slot");
        items.insertItem(0, new ItemStack(Items.IRON_INGOT), false);
        chemicals.setChemicalInTank(0, ChemicalStack.EMPTY);
        helper.assertTrue(items.getStackInSlot(0).is(Items.IRON_INGOT), "Empty chemical projection preserves the item slot");
        helper.succeed();
    }

    public void chemicalAmountsAreNotTruncated(GameTestHelper helper) {
        long amount = (long) Integer.MAX_VALUE + 5_000L;
        GenericStackInv inventory = AppMekGameTestFixtures.inventory(1, Long.MAX_VALUE);
        InventoryChemicalHandler handler = new InventoryChemicalHandler(inventory);
        helper.assertTrue(handler.insertChemical(0, AppMekGameTestFixtures.chemical("oxygen", amount), Action.EXECUTE).isEmpty(),
                "Long chemical amount is inserted without truncation");
        ChemicalStack extracted = handler.extractChemical(0, amount - 100L, Action.EXECUTE);
        helper.assertTrue(extracted.getAmount() == amount - 100L && inventory.getAmount(0) == 100L,
                "Long extraction and its remainder conserve the stored amount");
        helper.succeed();
    }
}
