package cn.howxu.mmcr.compat.botania;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.definition.MachineIoView;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.compat.botania.BotaniaManaRecipeGameTestFixtures.MachineFixture;
import cn.howxu.mmcr.compat.botania.loaded.ManaPortBlockEntity;
import cn.howxu.mmcr.internal.api.facade.recipe.RequirementAdapters;
import cn.howxu.mmcr.internal.api.facade.runtime.IoAdapters;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeThread;
import cn.howxu.mmcr.internal.recipe.FactorySearchContext;
import cn.howxu.mmcr.internal.recipe.AsyncRequirementPlanner;
import cn.howxu.mmcr.internal.recipe.EffectiveRecipeSet;
import cn.howxu.mmcr.internal.recipe.RecipeSearchContextKey;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineRecipesEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.recipe.BotaniaIo;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import vazkii.botania.api.mana.ManaItem;
import vazkii.botania.common.item.BotaniaItems;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static cn.howxu.mmcr.compat.botania.BotaniaManaRecipeGameTestFixtures.*;

/** Real controller recipe lifecycle against native pools and runtime-backed mana items.
 * @author howxu <dev@howxu.cn>
 */
public final class BotaniaManaRecipeGameTest {
    public static final ResourceLocation MACHINE_ID = MMCR.id("botania_mana_task9");
    public static final ResourceLocation RECIPE_ID = MMCR.id("botania_mana_task9_recipe");
    public static final ResourceLocation OUTPUT_WAIT_ID = MMCR.id("botania_mana_task9_output_wait");
    public static final ResourceLocation ASYNC_FILL_ID = MMCR.id("botania_mana_task9_async_fill");
    public static final ResourceLocation TAGGED_RECIPE_ID = MMCR.id("botania_mana_task9_tagged");

    public static void registerMachines(RegisterMachineDefinitionsEvent event) {
        event.registerMachine(MACHINE_ID, machine -> machine.maxParallelism(3).parallelizable(true));
    }

    public static void registerStructures(RegisterMachineStructuresEvent event) {
        event.registerStructure(MACHINE_ID, structure -> structure.fullStructure(stage -> stage.pattern(pattern ->
                pattern.layer("IICOO", "  P  ")
                        .where('I', BlockConditions.manaInput())
                        .where('C', BlockConditions.deferredBlock(() -> ModBlocks.controllerFor(MACHINE_ID).get()))
                        .where('O', BlockConditions.manaOutput())
                        .where('P', BlockConditions.parallelControllers()).controller('C'))));
    }

    public static void registerRecipes(RegisterMachineRecipesEvent event) {
        event.registerRecipe(RECIPE_ID, recipe -> recipe.recipePool(MACHINE_ID).duration(3).parallelized(true)
                .custom(BotaniaIo.manaInput(300)).custom(BotaniaIo.manaOutput(100)));
        event.registerRecipe(OUTPUT_WAIT_ID, recipe -> recipe.recipePool(MACHINE_ID).duration(3)
                .custom(BotaniaIo.manaOutput(200)));
        event.registerRecipe(ASYNC_FILL_ID, recipe -> recipe.recipePool(MACHINE_ID).duration(3)
                .custom(BotaniaIo.manaInput(300)).custom(BotaniaIo.manaOutput(800)));
        event.registerRecipe(TAGGED_RECIPE_ID, recipe -> recipe.recipePool(MACHINE_ID).duration(3)
                .requirement(RequirementAdapters.wrap(new ManaRequirement(RecipeModifier.IOType.INPUT,
                        300, List.of("selected")))).custom(BotaniaIo.manaOutput(100)));
    }

    public void multiplePoolsLowerParallelismAndConsumeOnlyAtStart(GameTestHelper helper) {
        MachineFixture fixture = machine(helper);
        fixture.firstInput().receiveMana(400);
        fixture.secondInput().receiveMana(350);
        // Neither input alone can supply the batch. Both outputs are needed to accept it.
        fixture.firstOutput().storage().setAmount(BotaniaManaIds.CAPACITY - 100);
        fixture.secondOutput().storage().setAmount(BotaniaManaIds.CAPACITY - 100);
        CraftingRuntime crafting = fixture.crafting();
        crafting.start(recipe(helper, RECIPE_ID), 3);
        helper.assertTrue(crafting.active() && crafting.parallelism() == 2 && fixture.inputAmount() == 150,
                "Discovered input pools share 600 mana and lower the requested triple batch to two");
        helper.assertTrue(fixture.firstInput().storage().amount() < 400 && fixture.secondInput().storage().amount() < 350,
                "Both physical inputs contribute to the single start debit");
        complete(helper, crafting, fixture);
        crafting.finish();
        helper.assertTrue(!crafting.active() && fixture.inputAmount() == 150
                        && fixture.firstOutput().storage().amount() == BotaniaManaIds.CAPACITY
                        && fixture.secondOutput().storage().amount() == BotaniaManaIds.CAPACITY,
                "Completion splits exactly 200 mana over the two outputs without a second parallel scaling");
        crafting.tick();
        crafting.finish();
        helper.assertTrue(fixture.inputAmount() == 150 && fixture.outputAmount() == 2L * BotaniaManaIds.CAPACITY,
                "Repeated idle lifecycle entrypoints cannot duplicate output or consume another start");
        helper.succeed();
    }

    public void droppedTabletWakesBlockedInputSearch(GameTestHelper helper) {
        MachineFixture fixture = synchronousMachine(helper);
        var factory = fixture.factory();
        factory.ensureBaseLane(fixture.controller());
        long searchTime = helper.getLevel().getGameTime() - 1;
        factory.tick(List.of(recipe(helper, RECIPE_ID)), 1, searchTime);
        FactoryRecipeThread lane = baseLane(factory);
        RecipeSearchContextKey key = lane.searchFailureKey();
        helper.assertTrue(key != null && !lane.canSearch(searchTime, key) && !lane.runtime().active()
                        && lane.runtime().failure() != null
                        && lane.runtime().failure().reason().equals(ManaFailureReasons.INPUT_MISSING),
                "Missing real mana blocks the controller lane with typed input failure and search backoff");
        helper.assertTrue(fixture.firstInput().storage().move(300, true, true) == 300,
                "Input simulation reports the available transfer");
        helper.assertTrue(fixture.inputAmount() == 0, "Simulation does not mutate pool storage");
        helper.assertTrue(!lane.canSearch(searchTime, key), "Simulation does not wake the blocked lane");
        ItemEntity tablet = tablet(helper, fixture.firstInput(), 300);
        try {
            helper.assertTrue(new AABB(fixture.firstInput().getBlockPos()).intersects(tablet.getBoundingBox()),
                    "Dropped tablet is inside the actual input pool ticker scan");
            helper.assertTrue(ManaItem.LOOKUP.find(tablet.getItem()).canDrainManaToPool(fixture.firstInput()),
                    "Native tablet permission allows draining into this input pool");
            helper.assertTrue(mana(tablet) == 300, "Dropped tablet starts with the required mana");
            fixture.firstInput().tickMana();
            helper.assertTrue(fixture.inputAmount() == 300, "Pool ticker transfers the tablet mana into real storage");
            helper.assertTrue(mana(tablet) == 0, "Pool ticker drains the native tablet exactly once");
            helper.assertTrue(lane.canSearch(searchTime, key), "Real input change emits INPUT_AVAILABLE immediately");
            factory.tick(List.of(recipe(helper, RECIPE_ID)), 1, searchTime);
            helper.assertTrue(lane.runtime().active(), "Woken search starts in the controller-local synchronous fixture");
            helper.assertTrue(lane.runtime().parallelism() == 1, "Woken search commits a single batch");
            helper.assertTrue(fixture.inputAmount() == 0, "Woken start consumes the transferred mana once");
            complete(helper, lane.runtime(), fixture);
            lane.runtime().finish();
            helper.assertTrue(!lane.runtime().active() && fixture.outputAmount() == 100 && fixture.inputAmount() == 0,
                    "Woken recipe consumes the tablet's mana once and completes once");
        } finally {
            tablet.discard();
            factory.clear();
        }
        helper.succeed();
    }

    public void outputCapacityRetriesAfterNativeItemDrain(GameTestHelper helper) {
        MachineFixture fixture = machine(helper);
        fixture.firstInput().receiveMana(600);
        CraftingRuntime crafting = fixture.crafting();
        crafting.start(recipe(helper, RECIPE_ID), 2);
        complete(helper, crafting, fixture);
        fixture.firstOutput().storage().setAmount(BotaniaManaIds.CAPACITY - 100);
        fixture.secondOutput().storage().setAmount(BotaniaManaIds.CAPACITY);
        helper.assertTrue(crafting.prepareAsyncFinish(), "Prepare the exact active parallel batch for completion");
        crafting.finish();
        long blockedAmount = fixture.outputAmount();
        helper.assertTrue(crafting.active() && crafting.finishPending() && crafting.parallelism() == 2
                        && crafting.failure() != null && crafting.failure().reason().equals(ManaFailureReasons.OUTPUT_BLOCKED)
                        && fixture.inputAmount() == 0 && blockedAmount == 2L * BotaniaManaIds.CAPACITY - 100,
                "Capacity for only one output blocks the whole committed batch without partial insertion");

        var factory = fixture.factory();
        factory.ensureBaseLane(fixture.controller());
        long searchTime = helper.getLevel().getGameTime() - 1;
        // Save the blocked normal execution while the independent factory base lane searches for output space.
        CompoundTag saved = new CompoundTag();
        crafting.save(saved, helper.getLevel().registryAccess());
        crafting.invalidate();
        factory.tick(List.of(recipe(helper, OUTPUT_WAIT_ID)), 1, searchTime);
        FactoryRecipeThread lane = baseLane(factory);
        RecipeSearchContextKey key = lane.searchFailureKey();
        helper.assertTrue(key != null && !lane.canSearch(searchTime, key) && lane.runtime().failure() != null
                        && lane.runtime().failure().reason().equals(ManaFailureReasons.OUTPUT_BLOCKED),
                "Insufficient output capacity also blocks recipe search with targeted backoff");
        crafting.load(saved, fixture.controller().resourceDomain(), helper.getLevel().registryAccess());
        helper.assertTrue(crafting.active() && crafting.finishPending() && crafting.parallelism() == 2,
                "Blocked finish restores the complete committed parallel batch");
        helper.assertTrue(fixture.firstOutput().storage().move(100, false, true) == 100,
                "Output simulation reports the available drain");
        helper.assertTrue(fixture.outputAmount() == blockedAmount, "Simulated draining does not mutate real output");
        helper.assertTrue(!lane.canSearch(searchTime, key), "Simulated draining cannot wake a capacity-blocked lane");
        ItemEntity tablet = tablet(helper, fixture.firstOutput(), 0);
        try {
            helper.assertTrue(ManaItem.LOOKUP.find(tablet.getItem()).canReceiveManaFromPool(fixture.firstOutput()),
                    "Native tablet permission allows charging from this output pool");
            helper.assertTrue(fixture.firstOutput().transferManaItem(tablet), "Native output pool charges a real tablet");
            int extracted = mana(tablet);
            helper.assertTrue(extracted >= 100, "Native item charging frees enough output capacity for the blocked batch");
            helper.assertTrue(fixture.outputAmount() + extracted == blockedAmount, "Native item charging conserves mana");
            helper.assertTrue(lane.canSearch(searchTime, key), "Native item charging immediately emits matching OUTPUT_CAPACITY");
            makeFinishEligible(helper, crafting);
            crafting.finish();
            helper.assertTrue(!crafting.active() && fixture.inputAmount() == 0
                            && fixture.outputAmount() + mana(tablet) == blockedAmount + 200,
                    "Freed capacity accepts the restored full batch once without reconsuming input");
            crafting.tick();
            crafting.finish();
            helper.assertTrue(fixture.outputAmount() + mana(tablet) == blockedAmount + 200,
                    "Repeated completion cannot duplicate restored output");
        } finally {
            tablet.discard();
            factory.clear();
        }
        helper.succeed();
    }

    public void activeControllerSaveRestoreKeepsConsumedMana(GameTestHelper helper) {
        MachineFixture fixture = machine(helper);
        fixture.firstInput().receiveMana(400);
        fixture.secondInput().receiveMana(350);
        fixture.factory().ensureBaseLane(fixture.controller());
        CraftingRuntime crafting = fixture.crafting();
        crafting.start(recipe(helper, RECIPE_ID), 3);
        crafting.tick();
        crafting.pause();
        helper.assertTrue(crafting.active() && crafting.snapshot().status().getStatus() == CraftingStatus.Status.PAUSED
                        && fixture.inputAmount() == 150 && fixture.outputAmount() == 0,
                "Active paused execution has already consumed its two-batch input and no output");
        CompoundTag saved = fixture.controller().saveWithoutMetadata(helper.getLevel().registryAccess());
        CompoundTag savedRuntime = saved.getCompound("crafting_runtime");
        int[] consumed = savedRuntime.getCompound("recipe").getCompound("inputConsumptionPlan")
                .getIntArray("consumedInputBatches");
        helper.assertTrue(savedRuntime.getBoolean("active")
                        && consumed.length > 0 && consumed[0] == 1,
                "Controller NBT carries the real active execution and its consumed-at-start mana marker");
        MachineControllerBlockEntity original = fixture.controller();
        original.setRemoved();
        MachineControllerBlockEntity replacement = new MachineControllerBlockEntity(original.getBlockPos(), original.getBlockState());
        replacement.setLevel(helper.getLevel());
        helper.getLevel().setBlockEntity(replacement);
        replacement.setMachine(MachineRegistry.getMachine(MACHINE_ID));
        replacement.loadWithComponents(saved, helper.getLevel().registryAccess());
        replacement.onLoad();
        form(helper, replacement, fixture.ports());
        helper.assertTrue(replacement.runtimeRestorationComplete(), "Replacement controller reconstructs linked topology before restore");
        CraftingRuntime restored = runtime(replacement).craftingRuntime();
        helper.assertTrue(restored.active() && restored.parallelism() == 2 && restored.tickCount() > 0
                        && restored.activeRecipe().inputConsumptionPlan().consumedBatches(0) == 1
                        && fixture.inputAmount() == 150 && fixture.outputAmount() == 0,
                "Real replacement BE restores progress and consumed-at-start markers without another debit");
        restored.resume();
        complete(helper, restored, fixture);
        restored.finish();
        helper.assertTrue(!restored.active() && fixture.inputAmount() == 150 && fixture.outputAmount() == 200,
                "Restored controller completes only the original committed batch");
        restored.tick();
        restored.finish();
        helper.assertTrue(fixture.inputAmount() == 150 && fixture.outputAmount() == 200,
                "Finished restored execution remains idempotent");
        helper.succeed();
    }

    public void taggedQueriesAndPlanningUseRealPoolStorage(GameTestHelper helper) {
        MachineFixture fixture = synchronousMachine(helper);
        fixture.secondInput().receiveMana(900);
        asyncTaggedSearchReselectsExecutableCandidate(helper, fixture);
        fixture.firstInput().receiveMana(100);
        fixture.firstOutput().storage().setAmount(BotaniaManaIds.CAPACITY - 50);
        fixture.secondOutput().storage().setAmount(BotaniaManaIds.CAPACITY - 150);
        var discovered = IoAdapters.wrap(new MachineIoView(fixture.snapshot()));
        helper.assertTrue(fixture.firstInput().getCurrentMana() == 0 && fixture.secondInput().getCurrentMana() == 0
                        && discovered.manaInput() == 1000 && discovered.manaOutputCapacity() == 200,
                "Public controller IO reads real mana and output space rather than native zero/full projections");
        MachineCapability selected = tagged(fixture.firstInput().capabilitySnapshot().capabilities().getFirst(), "selected");
        MachineCapability decoy = tagged(fixture.secondInput().capabilitySnapshot().capabilities().getFirst(), "decoy");
        // Structural metadata is overlaid on discovered native capabilities; facets and physical identity remain real.
        CapabilitySnapshot snapshot = new CapabilitySnapshot(List.of(selected, selected, decoy,
                tagged(fixture.firstOutput().capabilitySnapshot().capabilities().getFirst(), "selected"),
                tagged(fixture.secondOutput().capabilitySnapshot().capabilities().getFirst(), "decoy")));
        var query = IoAdapters.wrap(new MachineIoView(snapshot));
        helper.assertTrue(query.manaInput() == 1000 && query.forTags(Set.of("selected")).manaInput() == 100
                        && query.forTags(Set.of("selected")).manaOutputCapacity() == 50
                        && query.forTags(Set.of("absent")).manaInput() == 0,
                "Tag filtering and physical alias deduplication preserve actual input/output direction");
        CraftingContext context = new CraftingContext(snapshot);
        var blocked = context.planInputs(recipe(helper, TAGGED_RECIPE_ID), 1);
        helper.assertTrue(!blocked.successful() && blocked.failure().reason().equals(ManaFailureReasons.INPUT_MISSING)
                        && fixture.inputAmount() == 1000,
                "A rich wrong-tag pool and a duplicate alias cannot satisfy the selected input");
        ItemEntity tablet = tablet(helper, fixture.firstInput(), 200);
        try {
            fixture.firstInput().transferManaItem(tablet);
            helper.assertTrue(query.forTags(Set.of("selected")).manaInput() == 300 && mana(tablet) == 0,
                    "The same public view immediately reflects native tablet insertion into its real store");
            var plan = context.planInputs(recipe(helper, TAGGED_RECIPE_ID), 1);
            helper.assertTrue(plan.successful() && plan.plan().commit() && fixture.firstInput().storage().amount() == 0
                            && fixture.secondInput().storage().amount() == 900 && query.manaInput() == 900,
                    "Tagged recipe commit consumes only the selected physical pool once");
        } finally {
            tablet.discard();
        }
        helper.succeed();
    }

    public void staleAsyncPlansRevalidateAfterNativeAndRecipeChanges(GameTestHelper helper) {
        MachineFixture fixture = machine(helper);
        fixture.firstInput().receiveMana(900);
        var inputPrepared = new CraftingContext(fixture.firstInput().capabilitySnapshot())
                .planAsync(List.of(ManaRequirement.input(300)), 2);
        helper.assertTrue(inputPrepared.initialMainThreadRequirements().isEmpty(), "Mana has a worker-safe async descriptor");
        ItemEntity inputTablet = tablet(helper, fixture.firstInput(), 100);
        try {
            fixture.firstInput().transferManaItem(inputTablet);
            fixture.crafting().start(recipe(helper, RECIPE_ID), 3);
            helper.assertTrue(fixture.crafting().active() && fixture.inputAmount() == 100 && mana(inputTablet) == 0,
                    "Native insertion and a competing real start change the captured live input");
            var planned = CompletableFuture.supplyAsync(inputPrepared::plan).join();
            helper.assertTrue(planned.mainThreadRequirements().isEmpty() && planned.operations().size() == 1,
                    "Worker plans the original immutable 900-mana snapshot");
            var result = fixture.firstInput().capabilitySnapshot().capabilities().getFirst()
                    .facet(AsyncPlanningFacet.class).orElseThrow().commit(planned.operations().getFirst().operation());
            helper.assertTrue(!result.success() && result.status().reason().equals(ManaFailureReasons.INPUT_MISSING)
                            && fixture.inputAmount() == 100,
                    "Main-thread commit rejects a stale 600-mana debit without moving the remaining 100");
            fixture.crafting().invalidate();
        } finally {
            inputTablet.discard();
        }

        fixture.firstInput().receiveMana(300);
        fixture.firstOutput().storage().setAmount(BotaniaManaIds.CAPACITY - 900);
        fixture.secondOutput().storage().setAmount(BotaniaManaIds.CAPACITY);
        var outputPrepared = new CraftingContext(fixture.firstOutput().capabilitySnapshot())
                .planAsync(List.of(ManaRequirement.output(300)), 2);
        helper.assertTrue(outputPrepared.initialMainThreadRequirements().isEmpty(), "Output mana also captures on the server thread");
        ItemEntity outputTablet = tablet(helper, fixture.firstOutput(), 0);
        try {
            // Leave only 100 mana item space so native output transfer frees precisely that much pool space.
            ManaItem item = ManaItem.LOOKUP.find(outputTablet.getItem());
            item.addMana(item.getMaxMana() - 100);
            fixture.firstOutput().transferManaItem(outputTablet);
            fixture.crafting().start(recipe(helper, ASYNC_FILL_ID), 1);
            complete(helper, fixture.crafting(), fixture);
            fixture.crafting().finish();
            helper.assertTrue(!fixture.crafting().active()
                            && fixture.firstOutput().storage().amount() == BotaniaManaIds.CAPACITY - 200,
                    "Native item drain followed by real completion changes the captured output capacity");
            var planned = CompletableFuture.supplyAsync(outputPrepared::plan).join();
            helper.assertTrue(planned.mainThreadRequirements().isEmpty() && planned.operations().size() == 1,
                    "Worker retains the original 900-space output snapshot");
            var result = fixture.firstOutput().capabilitySnapshot().capabilities().getFirst()
                    .facet(AsyncPlanningFacet.class).orElseThrow().commit(planned.operations().getFirst().operation());
            helper.assertTrue(!result.success() && result.status().reason().equals(ManaFailureReasons.OUTPUT_BLOCKED)
                            && fixture.firstOutput().storage().amount() == BotaniaManaIds.CAPACITY - 200,
                    "Stale output commit refuses a 600 total when only 200 space remains, without partial insertion");
        } finally {
            outputTablet.discard();
        }
        helper.succeed();
    }

    private static ItemEntity tablet(GameTestHelper helper, ManaPortBlockEntity port, int amount) {
        ItemStack stack = new ItemStack(BotaniaItems.MANA_TABLET);
        ManaItem.LOOKUP.find(stack).addMana(amount);
        Vec3 center = port.getBlockPos().getCenter();
        ItemEntity tablet = new ItemEntity(helper.getLevel(), center.x, center.y - 0.25, center.z, stack);
        tablet.setNoGravity(true);
        tablet.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(tablet);
        return tablet;
    }

    private static void asyncTaggedSearchReselectsExecutableCandidate(GameTestHelper helper, MachineFixture fixture) {
        List<MachineRecipe> candidates = List.of(recipe(helper, TAGGED_RECIPE_ID), recipe(helper, OUTPUT_WAIT_ID));
        List<MachineCapability> capabilities = List.of(
                tagged(fixture.firstInput().capabilitySnapshot().capabilities().getFirst(), "selected"),
                tagged(fixture.secondInput().capabilitySnapshot().capabilities().getFirst(), "decoy"),
                fixture.firstOutput().capabilitySnapshot().capabilities().getFirst(),
                fixture.secondOutput().capabilitySnapshot().capabilities().getFirst());
        var snapshot = fixture.controller().currentRuntimeSnapshot();
        long catalogVersion = RecipeRegistry.catalogForPool(MACHINE_ID).version();
        var request = AsyncRequirementPlanner.captureRecipeSearch(snapshot, candidates, 1, capabilities,
                List.of(), catalogVersion, new EffectiveRecipeSet.Cache());
        var worker = CompletableFuture.supplyAsync(request::search).join();
        helper.assertTrue(worker.failure() == null && worker.result() != null && worker.result().success(),
                "Immutable worker search returns a candidate without live native access");
        helper.assertTrue(worker.result().recipe().id().equals(TAGGED_RECIPE_ID),
                "Higher-priority tagged candidate remains provisional until main-thread tag planning");
        helper.assertTrue(worker.requiresMainThreadReplan(),
                "A rich wrong-tag pool cannot make tagged mana a fully planned worker candidate");
        helper.assertTrue(fixture.firstInput().storage().amount() == 0,
                "Worker search cannot mutate the empty selected pool");
        helper.assertTrue(fixture.secondInput().storage().amount() == 900,
                "Worker search cannot consume wrong-tag mana");
        var context = new FactorySearchContext(snapshot, candidates, capabilities, List.of(), catalogVersion,
                fixture.controller().resourceAvailabilityEpoch(), 1, helper.getLevel().getGameTime());
        FactoryRecipeThread lane = FactoryRecipeThread.base(fixture.controller());
        try {
            helper.assertTrue(lane.startSearchResult(context, candidates, snapshot.structure().version(),
                            new FactoryRecipeThread.SearchResult(worker.result(), worker.failure(),
                                    worker.requiresMainThreadReplan())),
                    "Main-thread fallback searches the whole candidate set and starts an executable recipe");
            helper.assertTrue(lane.runtime().active() && lane.runtime().recipe().id().equals(OUTPUT_WAIT_ID),
                    "Empty selected pool rejects the provisional tagged candidate and selects the output-only fallback");
            complete(helper, lane.runtime(), fixture);
            lane.runtime().finish();
            helper.assertTrue(!lane.runtime().active() && fixture.outputAmount() == 200,
                    "Reselected candidate completes its real output rather than repeatedly failing tagged starts");
            helper.assertTrue(fixture.firstInput().storage().amount() == 0,
                    "Fallback never debits the empty selected input");
            helper.assertTrue(fixture.secondInput().storage().amount() == 900,
                    "Fallback never debits the rich wrong-tag input");
        } finally {
            lane.runtime().invalidate();
        }
    }

    private static int mana(ItemEntity tablet) { return ManaItem.LOOKUP.find(tablet.getItem()).getMana(); }

    private static MachineCapability tagged(MachineCapability delegate, String tag) {
        return new MachineCapability() {
            private final CapabilityView view = new CapabilityView() {
                @Override public CapabilityType type() { return delegate.type(); }
                @Override public CapabilityDirections directions() { return delegate.directions(); }
                @Override public Set<Class<? extends CapabilityFacet>> facets() { return delegate.view().facets(); }
                @Override public List<String> tags() { return List.of(tag); }
            };
            @Override public CapabilityType type() { return delegate.type(); }
            @Override public CapabilityDirections directions() { return delegate.directions(); }
            @Override public CapabilityView view() { return view; }
            @Override public CapabilityOperation prepare(CapabilityRequest request) { return delegate.prepare(request); }
            @Override public <F extends CapabilityFacet> Optional<F> facet(Class<F> type) { return delegate.facet(type); }
        };
    }
}
