package cn.howxu.mmcr.compat.fluxnetworks;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.facet.RecipeEnergyPrefetchFacet;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.RecipeSearchTask;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkInputBlockEntity;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.registration.MachineRecipeConverter;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import sonar.fluxnetworks.common.connection.FluxNetwork;
import sonar.fluxnetworks.common.device.TileFluxStorage;

import java.util.List;
import java.util.Map;

import static cn.howxu.mmcr.compat.fluxnetworks.FluxNetworksGameTestFixtures.*;

/** Whole-duration parallel prefetch and allocation recovery through the actual crafting runtime.
 * @author howxu <dev@howxu.cn>
 */
public final class FluxNetworksRecipeGameTest {
    public static final ResourceLocation MACHINE_ID = MMCR.id("fluxnetworks_test_machine");
    public static final ResourceLocation RECIPE_ID = MMCR.id("fluxnetworks_test_recipe");
    public static final ResourceLocation OUTPUT_RECIPE_ID = MMCR.id("fluxnetworks_test_output");
    public static final ResourceLocation HIGH_BUDGET_RECIPE_ID = MMCR.id("fluxnetworks_test_high_budget");

    public void prefetchAndConsumeOnce(GameTestHelper helper) {
        FluxNetwork network = createNetwork(helper, player(helper));
        CraftingRuntime runtime = null;
        MachineRig rig = null;
        try {
            rig = machine(helper);
            form(helper, rig.controller());
            TileFluxStorage supply = storage(helper, STORAGE, 500L);
            connectAndCycle(supply, network);
            connectAndCycle(rig.input(), network);
            connectAndCycle(rig.output(), network);
            runtime = runtime(rig.controller()).craftingRuntime();
            MachineRecipe recipe = recipe(helper, RECIPE_ID);
            runtime.start(recipe, 2);
            rig.input().getTransferHandler().onCycleStart();
            helper.assertTrue(!runtime.active() && rig.input().getTransferBuffer() == 0L
                            && rig.input().getTransferHandler().getRequest() == 100L
                            && rig.input().getTransferHandler().reserved() == 0L && supply.getTransferBuffer() == 500L,
                    "Failed start publishes the entire 10 FE/t x 5 duration x 2 parallel budget, without committing anything");
            runtime.start(recipe, 2);
            network.onEndServerTick();
            helper.assertTrue(supply.getTransferBuffer() == 400L && rig.input().getTransferBuffer() == 100L,
                    "Repeated failed candidates publish one demand; actual native Storage supplies only 100 FE");
            runtime.start(recipe, 2);
            helper.assertTrue(runtime.active() && runtime.parallelism() == 2
                            && rig.input().getTransferHandler().reserved() == 100L && rig.input().getTransferBuffer() == 100L,
                    "Retry reserves the arrived whole-duration parallel budget without consuming it at start");
            for (int step = 0; step < runtime.totalTick(); step++) {
                long before = rig.input().getTransferBuffer();
                runtime.start(recipe, 2);
                runtime.tick();
                network.onEndServerTick();
                helper.assertTrue(rig.input().getTransferBuffer() == before - 20L
                                && rig.input().getTransferHandler().reserved() == rig.input().getTransferBuffer()
                                && supply.getTransferBuffer() == 400L,
                        "Each explicit main-thread settlement consumes one local parallel rate, without a second network debit");
            }
            helper.assertTrue(runtime.finishPending() && rig.input().getTransferBuffer() == 0L
                            && rig.input().getTransferHandler().reserved() == 0L,
                    "Five recipe settlements conserve the single 100 FE native prefetch");
            runtime.finish();
            runtime.tick();
            runtime.finish();
            helper.assertTrue(!runtime.active() && supply.getTransferBuffer() == 400L,
                    "Completion and repeated idle calls never consume energy twice");

            var simulation = new CraftingContext(rig.output().capabilitySnapshot()).planRequirements(
                    List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 10L)), 2, Map.of());
            helper.assertTrue(simulation.successful() && rig.output().getTransferBuffer() == 0L
                            && supply.getTransferBuffer() == 400L,
                    "Actual Plug output admission planning is side-effect free and uses the native Storage's request limiter");
            runtime.start(recipe(helper, OUTPUT_RECIPE_ID), 2);
            helper.assertTrue(runtime.active() && runtime.parallelism() == 2 && rig.output().getTransferBuffer() == 0L,
                    "Output-only recipe start produces nothing in advance");
            int duration = runtime.totalTick();
            for (int step = 0; step < duration; step++) {
                runtime.tick();
                helper.assertTrue(rig.output().getTransferBuffer() == 20L,
                        "Real per-tick recipe output commits exactly one parallel rate into the native Plug buffer");
                network.onEndServerTick();
                helper.assertTrue(rig.output().getTransferBuffer() == 0L && supply.getTransferBuffer() == 400L + 20L * (step + 1),
                        "Native cycle drains committed output to real Storage, conserving each settlement");
            }
            runtime.finish();
            runtime.tick();
            network.onEndServerTick();
            helper.assertTrue(!runtime.active() && supply.getTransferBuffer() == 500L,
                    "Parallel output completes once and restores exactly 100 FE to the native network");
        } finally {
            try {
                if (runtime != null) runtime.invalidate();
                if (rig != null) rig.controller().invalidateFormedStructure();
            } finally { closeNetwork(network); }
        }
        helper.succeed();
    }

    public void restoreAndCancelWithoutSecondDebit(GameTestHelper helper) {
        ServerPlayer owner = player(helper);
        FluxNetwork network = createNetwork(helper, owner);
        CraftingRuntime original = null;
        CraftingRuntime restored = null;
        MachineRig rig = null;
        try {
            rig = machine(helper);
            rig.input().getTransferHandler().setLimit(1000L);
            form(helper, rig.controller());
            TileFluxStorage supply = storage(helper, STORAGE, 500L);
            connectAndCycle(supply, network);
            connectAndCycle(rig.input(), network);
            connectAndCycle(rig.output(), network);
            original = runtime(rig.controller()).craftingRuntime();
            MachineRecipe recipe = recipe(helper, RECIPE_ID);
            warmAndStart(helper, original, recipe, 2, network);
            original.tick();
            helper.assertTrue(rig.input().getTransferBuffer() == 80L && rig.input().getTransferHandler().reserved() == 80L,
                    "One real settlement consumes 20 FE before saving the active allocation");
            CompoundTag savedRuntime = new CompoundTag();
            original.save(savedRuntime, helper.getLevel().registryAccess());
            var allocations = original.activeRecipe().getDataCompound().getList("prefetched_energy_allocations", Tag.TAG_COMPOUND);
            String key = rig.input().capabilitySnapshot().facets(RecipeEnergyPrefetchFacet.class).getFirst().reservationKey();
            helper.assertTrue(allocations.size() == 1 && allocations.getCompound(0).getString("key").equals(key)
                            && allocations.getCompound(0).getLong("amount") == 80L,
                    "Actual runtime serializes one dimension/position-keyed remaining Flux allocation");
            CompoundTag savedPort = rig.input().saveWithFullMetadata(helper.getLevel().registryAccess());
            CompoundTag savedController = rig.controller().saveWithFullMetadata(helper.getLevel().registryAccess());
            rig = reloadMachine(helper, rig, savedController, savedPort, network, true);
            FluxNetworkInputBlockEntity input = rig.input();
            restored = runtime(rig.controller()).craftingRuntime();
            network.onEndServerTick();
            helper.assertTrue(restored.active() && restored.parallelism() == 2 && restored.tickCount() == 1
                            && input.getTransferBuffer() == 180L && input.getTransferHandler().reserved() == 80L
                            && supply.getTransferBuffer() == 300L
                            && input.getNetwork() == network && network.getConnectionByPos(input.getGlobalPos()) == input,
                    "Actual runtime load rebinds the remaining allocation and prefetches exactly one next batch");
            restored.load(savedRuntime, rig.controller().resourceDomain(), helper.getLevel().registryAccess());
            restored.rebindCurrentVersions();
            restored.start(recipe, 2);
            network.onEndServerTick();
            helper.assertTrue(restored.active() && input.getTransferBuffer() == 180L
                            && input.getTransferHandler().reserved() == 80L && supply.getTransferBuffer() == 300L,
                    "Repeated load and active start remain idempotent against actual native supply");
            restored.tick();
            helper.assertTrue(input.getTransferBuffer() == 160L && input.getTransferHandler().reserved() == 60L,
                    "Restored allocation consumes only the next parallel settlement from the reloaded local buffer");
            restored.invalidate();
            tickDevice(input);
            network.onEndServerTick();
            helper.assertTrue(!restored.active() && input.getTransferHandler().reserved() == 0L
                            && input.getTransferBuffer() == 0L && supply.getTransferBuffer() == 460L,
                    "Cancellation returns both unused allocation and next-batch energy to their native source");
            warmAndStart(helper, restored, recipe, 1, network);
            helper.assertTrue(restored.active() && input.getTransferHandler().reserved() == 50L,
                    "Next actual recipe obtains its whole-duration single batch from the refunded network");
            settle(helper, restored);
            network.onEndServerTick();
            helper.assertTrue(input.getTransferBuffer() == 50L && input.getTransferHandler().reserved() == 0L
                            && supply.getTransferBuffer() == 360L,
                    "Completion consumes exactly 50 FE and retains one next-batch budget for continuation");

            original = restored;
            warmAndStart(helper, original, recipe, 2, network);
            long paid = supply.getTransferBuffer();
            helper.assertTrue(input.getTransferBuffer() == 100L && input.getTransferHandler().reserved() == 100L
                            && paid == 310L,
                    "A new real parallel batch pays only its 50 FE shortfall");
            FluxNetworksDeviceGameTest.configure(rig.output(), "live-settings", 29, 1000L, true);
            FluxNetworksDeviceGameTest.pasteFrom(helper, owner, rig.output(), input);
            helper.assertTrue(original.active() && input.getTransferBuffer() == 100L
                            && input.getTransferHandler().reserved() == 100L && input.getRawPriority() == 29
                            && input.getSurgeMode(),
                    "Settings-only native paste keeps the active runtime and its paid committed reservation intact");
            CompoundTag pastedPort = input.saveWithFullMetadata(helper.getLevel().registryAccess());
            CompoundTag pastedController = rig.controller().saveWithFullMetadata(helper.getLevel().registryAccess());
            // Capture immediately after paste, with no cancel/restart workaround; only then simulate disk teardown.
            rig = reloadMachine(helper, rig, pastedController, pastedPort, network, false);
            input = rig.input();
            original = runtime(rig.controller()).craftingRuntime();
            network.onEndServerTick();
            helper.assertTrue(original.active() && original.tickCount() == 0
                            && input.getTransferBuffer() == 200L && input.getTransferHandler().reserved() == 100L
                            && input.getRawPriority() == 29 && input.getSurgeMode() && supply.getTransferBuffer() == paid - 100L,
                    "Direct post-paste save restores owner and settings, reserving once and warming one next batch");
            original.tick();
            helper.assertTrue(original.tickCount() == 1 && input.getTransferBuffer() == 180L
                            && input.getTransferHandler().reserved() == 80L && supply.getTransferBuffer() == paid - 100L,
                    "The restored post-paste runtime really settles its next 20 FE from the paid local reservation");
            rig.controller().invalidateFormedStructure();
            tickDevice(input);
            network.onEndServerTick();
            helper.assertTrue(!original.active() && input.getTransferHandler().reserved() == 0L
                            && input.getTransferBuffer() == 0L && input.getTransferHandler().getRequest() == 0L
                            && supply.getTransferBuffer() == paid + 80L,
                    "Real controller teardown cancels reservations and refunds all unused energy without losing consumed energy");

            form(helper, rig.controller());
            original.start(recipe(helper, OUTPUT_RECIPE_ID), 2);
            original.tick();
            helper.assertTrue(rig.output().getTransferBuffer() == 20L, "Real output runtime commits energy before teardown");
            rig.controller().invalidateFormedStructure();
            helper.assertTrue(!original.active() && rig.output().getTransferBuffer() == 20L,
                    "Unformation blocks new admission but preserves already-committed native Plug output");
            network.onEndServerTick();
            helper.assertTrue(rig.output().getTransferBuffer() == 0L && supply.getTransferBuffer() == paid + 100L,
                    "Unformed Plug still drains committed output through the actual native network");
        } finally {
            try {
                try { if (restored != null) restored.invalidate(); }
                finally {
                    if (original != null) original.invalidate();
                    if (rig != null) rig.controller().invalidateFormedStructure();
                }
            } finally { closeNetwork(network); }
        }
        helper.succeed();
    }

    public void failedLoadReleasesUnownedReservation(GameTestHelper helper) {
        FluxNetwork network = createNetwork(helper, player(helper));
        MachineRig rig = null;
        CraftingRuntime runtime = null;
        try {
            rig = machine(helper);
            rig.input().getTransferHandler().setLimit(1000L);
            form(helper, rig.controller());
            TileFluxStorage supply = storage(helper, STORAGE, 500L);
            connectAndCycle(supply, network);
            connectAndCycle(rig.input(), network);
            runtime = runtime(rig.controller()).craftingRuntime();
            // A real prior definition shares the ID/pool but no longer matches the startup catalog's duration=5.
            MachineRecipe priorDefinition = MachineRecipeConverter.toRecipe(MachineRecipeBuilder.recipe(RECIPE_ID)
                    .recipePool(MACHINE_ID).duration(4).parallelized(true).inputEnergy(10L).build(),
                    new StructureRegistration.Snapshot(Map.of(), Map.of(), Map.of(), Map.of()));
            warmAndStart(helper, runtime, priorDefinition, 2, network);
            runtime.tick();
            helper.assertTrue(rig.input().getTransferBuffer() == 60L && rig.input().getTransferHandler().reserved() == 60L
                            && supply.getTransferBuffer() == 420L,
                    "Prior recipe version really pays 80 FE and settles 20 FE before its definition changes");
            CompoundTag savedController = rig.controller().saveWithFullMetadata(helper.getLevel().registryAccess());
            CompoundTag savedPort = rig.input().saveWithFullMetadata(helper.getLevel().registryAccess());
            rig = reloadMachine(helper, rig, savedController, savedPort, network, true);
            FluxNetworkInputBlockEntity input = rig.input();
            runtime = runtime(rig.controller()).craftingRuntime();
            helper.assertTrue(!runtime.active() && runtime.failure() != null
                            && runtime.failure().reason().equals(BuiltinFailureReasons.RECIPE_LOAD),
                    "Actual load rejects a fully serialized old recipe definition that no longer matches the catalog");
            tickDevice(input);
            network.onEndServerTick();
            helper.assertTrue(input.getTransferHandler().reserved() == 0L && input.getTransferBuffer() == 0L,
                    "Actual load rejects the stale definition and refunds its now-ownerless reservation");
            helper.assertTrue(supply.getTransferBuffer() == 480L, "Failed owner restore returns paid energy without replacing consumed energy");
            warmAndStart(helper, runtime, recipe(helper, RECIPE_ID), 1, network);
            helper.assertTrue(runtime.active() && input.getTransferHandler().reserved() == 50L,
                    "A current recipe reserves its whole budget from the recovered source network");
            settle(helper, runtime);
            network.onEndServerTick();
            helper.assertTrue(input.getTransferBuffer() == 50L && input.getTransferHandler().reserved() == 0L
                            && supply.getTransferBuffer() == 380L,
                    "Recovery consumes exactly its local budget and leaves one unreserved continuation batch");
            runtime.stopEnergyPrefetch();
            tickDevice(input);
            network.onEndServerTick();
            helper.assertTrue(input.getTransferBuffer() == 0L && supply.getTransferBuffer() == 430L,
                    "Confirmed idle refunds continuation energy, conserving both recipes' actual consumption");
        } finally {
            try {
                if (runtime != null) runtime.invalidate();
                if (rig != null) rig.controller().invalidateFormedStructure();
            } finally { closeNetwork(network); }
        }
        helper.succeed();
    }

    public void successfulCandidateRetractsFailedWarmup(GameTestHelper helper) {
        FluxNetwork network = createNetwork(helper, player(helper));
        MachineRig rig = null;
        CraftingRuntime runtime = null;
        try {
            rig = machine(helper);
            form(helper, rig.controller());
            rig.input().getTransferHandler().setLimit(200L);
            TileFluxStorage supply = storage(helper, STORAGE, 500L);
            connectAndCycle(supply, network);
            connectAndCycle(rig.input(), network);
            rig.input().getTransferHandler().requestWarmup(100L);
            network.onEndServerTick();
            helper.assertTrue(rig.input().getTransferBuffer() == 100L && supply.getTransferBuffer() == 400L,
                    "The Point holds a real reusable budget while another 100 FE of same-tick limit remains available");
            MachineRecipe high = recipe(helper, HIGH_BUDGET_RECIPE_ID);
            MachineRecipe low = recipe(helper, RECIPE_ID);
            var snapshot = rig.controller().currentRuntimeSnapshot();
            var capabilities = rig.controller().componentRuntime().capabilities();
            var failed = new RecipeSearchTask(snapshot, MACHINE_ID, MACHINE_ID, snapshot.structure().version(),
                    2, List.of(high), capabilities).compute();
            rig.input().getTransferHandler().onCycleStart();
            helper.assertTrue(!failed.success() && rig.input().getTransferHandler().getRequest() == 100L
                            && rig.input().getTransferHandler().reserved() == 0L,
                    "Real high-budget candidate search fails and publishes its missing 100 FE without committing a reservation");
            var selected = new RecipeSearchTask(snapshot, MACHINE_ID, MACHINE_ID, snapshot.structure().version(),
                    2, List.of(high, low), capabilities).compute();
            helper.assertTrue(selected.success() && selected.recipe().id().equals(RECIPE_ID)
                            && rig.input().getTransferHandler().reserved() == 0L,
                    "Actual ordered search visits the failed high budget then selects and rolls back the feasible low candidate");
            runtime = runtime(rig.controller()).craftingRuntime();
            runtime.start(selected.recipe(), 2);
            helper.assertTrue(runtime.active() && runtime.parallelism() == 2
                            && rig.input().getTransferHandler().reserved() == 100L,
                    "Low-budget activation owns exactly the arrived local whole-duration budget");
            network.onEndServerTick();
            helper.assertTrue(rig.input().getTransferHandler().getRequest() == 0L
                            && rig.input().getTransferBuffer() == 200L && supply.getTransferBuffer() == 300L,
                    "Successful activation replaces failed-candidate hints with exactly one next-batch allocation");
            runtime.tick();
            network.onEndServerTick();
            helper.assertTrue(rig.input().getTransferBuffer() == 180L && rig.input().getTransferHandler().reserved() == 80L
                            && supply.getTransferBuffer() == 300L,
                    "Actual settlement consumes 20 FE while retaining only the selected recipe's next batch");
        } finally {
            try {
                if (runtime != null) runtime.invalidate();
                if (rig != null) rig.controller().invalidateFormedStructure();
            } finally { closeNetwork(network); }
        }
        helper.succeed();
    }

    private static MachineRecipe recipe(GameTestHelper helper, ResourceLocation id) {
        MachineRecipe recipe = RecipeRegistry.getRecipe(id);
        helper.assertTrue(recipe != null, "Flux recipe fixture was registered by startup lifecycle: " + id);
        return recipe;
    }

    private static void warmAndStart(GameTestHelper helper, CraftingRuntime runtime, MachineRecipe recipe,
                                     int parallelism, FluxNetwork network) {
        runtime.start(recipe, parallelism);
        if (!runtime.active()) {
            network.onEndServerTick();
            runtime.start(recipe, parallelism);
        }
        helper.assertTrue(runtime.active() && runtime.parallelism() == parallelism,
                "Explicit native cycle supplies the whole budget before the actual runtime admits the batch");
    }

    private static void settle(GameTestHelper helper, CraftingRuntime runtime) {
        int maximum = runtime.totalTick();
        for (int step = 0; step < maximum && !runtime.finishPending(); step++) runtime.tick();
        helper.assertTrue(runtime.finishPending(), "Explicit runtime settlements reach completion without world tick assertions");
        runtime.finish();
        helper.assertTrue(!runtime.active(), "Actual runtime completes the local-budget recipe");
    }
}
