package cn.howxu.mmcr;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.recipe.CraftingContext;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.internal.event.LevelTickFireEvents;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import cn.howxu.mmcr.internal.capability.CapabilityFactories;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.capability.NativeAsyncResourceValues;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram.Stage;
import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram.VersionStamp;
import cn.howxu.mmcr.internal.recipe.RecipeProgramCompiler;
import cn.howxu.mmcr.internal.recipe.RequirementPlanner;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.internal.runtime.RecipeCommitLedger;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.registry.ModBlocks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Real registered ports and registry-backed saves driven by explicit operations.
 * @author howxu <dev@howxu.cn>
 */
public final class RunningRecipeGameTest {
    public static void ordinaryEnergyChargesOnlyRecipeTicks(GameTestHelper helper) {
        var controller = retainedInputController(helper);
        var energy = helper.getBlockEntity(new BlockPos(2, 2, 1), EnergyInputHatchBlockEntity.class);
        energy.getEnergyStorage().setAmount(100L);
        setWorkMode(controller, MachineWorkMode.SYNC);
        var runtime = attachedRuntime(controller);
        long originalTime = helper.getLevel().getGameTime();
        try {
            var recipe = MachineRecipe.fromCanonical(MMCR.id("running_energy_admission"), controller.currentRecipePoolId(),
                    10, List.of(new EnergyRequirement(10L)), List.of(), List.of(), 0, 1,
                    false, false, false, Set.of());
            RecipeRegistry.registerStatic(recipe);
            helper.assertTrue(runtime.start(recipe, 1).isCrafting(),
                    "Exactly the total recipe energy admits the execution");
            helper.assertTrue(energy.getEnergyStorage().amount() == 100L && runtime.tickCount() == 0,
                    "Admission checks ordinary energy without charging an extra tick");
            var installed = runtime.ensureInstalledProgram();
            helper.assertTrue(installed != null, "The real energy execution installs successfully");
            for (int tick = 1; tick <= 10; tick++) {
                long time = originalTime + tick;
                ((ServerLevelData) helper.getLevel().getLevelData()).setGameTime(time);
                long round = SharedIoCoordinator.get(helper.getLevel()).beginExecutionRound(time, true);
                helper.assertTrue(installed.tryAttempt(round, time) && installed.advance(),
                        "Each explicit recipe tick succeeds with exactly its declared energy");
                helper.assertTrue(energy.getEnergyStorage().amount() == 100L - tick * 10L,
                        "The actual hatch pays exactly once per recipe tick");
            }
            helper.assertTrue(!runtime.active() && runtime.ledger().state().outputPaid(),
                    "Ten paid installed recipe ticks complete the execution");
            runtime.finish();
            helper.assertTrue(!runtime.active() && energy.getEnergyStorage().amount() == 0L,
                    "Completion needs no extra energy payment");
        } finally {
            runtime.invalidate();
            SharedIoCoordinator.get(helper.getLevel()).cancel(controller.getBlockPos());
            controller.releaseStructureClaims();
            ((ServerLevelData) helper.getLevel().getLevelData()).setGameTime(originalTime);
        }
        helper.succeed();
    }

    public static void installedFluidProbabilityAttempts(GameTestHelper helper) {
        var fluid = place(helper, new BlockPos(1, 1, 1), "fluid_input_hatch", FluidInputHatchBlockEntity.class);
        var energy = place(helper, new BlockPos(2, 1, 1), "energy_input_hatch", EnergyInputHatchBlockEntity.class);
        energy.getEnergyStorage().setAmount(1_000L);
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(fluid.fluidStorage().insert(0, FluidResource.of(Fluids.WATER), 2_000L, transaction) == 2_000L,
                    "Real registered fluid hatch accepts runtime water");
            transaction.commit();
        }
        var controller = controller(helper, List.of(energy, fluid));
        var runtime = attachedRuntime(controller);
        var type = fluidProbabilityType();
        var handler = (FluidProbabilityHandler) type.handler();
        long originalTime = helper.getLevel().getGameTime();
        long time = originalTime;
        try {
            for (var mode : MachineWorkMode.values()) {
                setWorkMode(controller, mode);
                for (float chance : new float[] { 0.5F, 1F }) {
                    var wrapped = new ProbabilityFluidRequirement(type, new FluidRequirement(IOType.INPUT,
                            FluidIngredient.of(Fluids.WATER), 100, FluidStack.EMPTY, 1F, chance));
                    // Independent restored executions isolate formal attempts from admission/start probability.
                    restoreFluidProbability(runtime, controller, wrapped);
                    handler.attempts.clear();
                    handler.rejectOperation = false;
                    var installed = runtime.ensureInstalledProgram();
                    helper.assertTrue(installed != null && handler.attempts.isEmpty(), "Installing cannot sample fluid probability");
                    for (int attempt = 1; attempt <= 2; attempt++) {
                        ((ServerLevelData) helper.getLevel().getLevelData()).setGameTime(++time);
                        long round = SharedIoCoordinator.get(helper.getLevel()).beginExecutionRound(time, true);
                        if (mode != MachineWorkMode.SYNC) helper.assertTrue(runtime.prepareInstalledTick(round), "BE preparation succeeds");
                        helper.assertTrue(handler.attempts.size() == attempt - 1, "BE preparation cannot sample fluid probability");
                        long fe = energy.getEnergyStorage().amount();
                        long water = fluid.fluidStorage().amount(0);
                        helper.assertTrue(installed.tryAttempt(round, time) && installed.advance(), "Formal mixed fluid attempt succeeds");
                        assertFluidDecision(helper, handler, attempt);
                        long consumed = handler.attempts.getLast().committed.getFirst();
                        helper.assertTrue(fluid.fluidStorage().amount(0) == water - consumed
                                        && energy.getEnergyStorage().amount() == fe - 7L
                                        && runtime.tickCount() == attempt && runtime.ledger().state().paidAttempts() == attempt,
                                "One committed attempt pays its recorded fluid decision, FE and progress once");
                        var paid = runtime.ledger().state();
                        helper.assertFalse(installed.tryAttempt(round, time), "Exact duplicate cannot sample again");
                        helper.assertFalse(installed.tryAttempt(round + 1L, time), "Same-time duplicate cannot sample again");
                        helper.assertFalse(installed.tryAttempt(round, time + 1L), "Same-round duplicate cannot sample again");
                        helper.assertTrue(handler.attempts.size() == attempt && runtime.ledger().state().equals(paid)
                                        && fluid.fluidStorage().amount(0) == water - consumed
                                        && energy.getEnergyStorage().amount() == fe - 7L,
                                "Duplicates preserve actual fluid, FE, ledger and handler invocation count");
                    }

                    for (boolean reject : new boolean[] { true, false }) {
                        restoreFluidProbability(runtime, controller, wrapped);
                        handler.attempts.clear();
                        handler.rejectOperation = reject;
                        installed = runtime.ensureInstalledProgram();
                        ((ServerLevelData) helper.getLevel().getLevelData()).setGameTime(++time);
                        long round = SharedIoCoordinator.get(helper.getLevel()).beginExecutionRound(time, true);
                        if (mode != MachineWorkMode.SYNC) helper.assertTrue(runtime.prepareInstalledTick(round), "Rollback preparation succeeds");
                        helper.assertTrue(installed != null && installed.tryAttempt(round, time), "Rollback has one formal attempt");
                        long fe = energy.getEnergyStorage().amount();
                        long water = fluid.fluidStorage().amount(0);
                        var before = runtime.ledger().state();
                        if (reject) {
                            helper.assertFalse(installed.advance(), "Last operation rejects after actual fluid operations");
                            helper.assertTrue(runtime.failure() != null
                                            && runtime.failure().reason().equals(BuiltinFailureReasons.COMMIT_LOST_INPUT),
                                    "Controlled operation reports the real structured commit failure");
                        } else {
                            try (Transaction transaction = Transaction.openRoot()) {
                                helper.assertTrue(installed.executeContinuous(transaction) == null, "Caller transaction runs real fluid operations");
                                runtime.ledger().markResourcePaid(0, false, transaction);
                                helper.assertTrue(runtime.ledger().state().phase() == RecipeCommitLedger.Phase.RESOURCE_PAID
                                                && runtime.ledger().state().paidAttempts() == before.paidAttempts() + 1L
                                                && energy.getEnergyStorage().amount() == fe - 7L,
                                        "Resource payment and its journal are visible inside the uncommitted transaction");
                            }
                        }
                        assertFluidDecision(helper, handler, 1);
                        if (chance == 1F) helper.assertTrue(handler.attempts.getFirst().committed.getFirst() == 100L,
                                "Certain-consumption control proves nonzero real fluid operations preceded rollback");
                        helper.assertTrue(fluid.fluidStorage().amount(0) == water && energy.getEnergyStorage().amount() == fe
                                        && runtime.ledger().state().equals(before) && runtime.tickCount() == 0,
                                "Rollback restores FE, actual water and complete payment state without advancing progress");
                    }
                }
            }
        } finally {
            runtime.invalidate();
            SharedIoCoordinator.get(helper.getLevel()).cancel(controller.getBlockPos());
            ((ServerLevelData) helper.getLevel().getLevelData()).setGameTime(originalTime);
        }
        helper.succeed();
    }

    private static void restoreFluidProbability(CraftingRuntime runtime, MachineControllerBlockEntity controller,
                                               ProbabilityFluidRequirement requirement) {
        var active = new ActiveMachineRecipe(recipe("running_installed_fluid_probability", 20,
                List.of(new EnergyRequirement(7L), requirement), List.of()));
        active.setInputConsumptionPlan(new ActiveMachineRecipe.InputConsumptionPlan(List.of(0, 0)));
        var snapshot = controller.currentRuntimeSnapshot();
        runtime.restore(active, null, snapshot.structure().version(), snapshot.capabilityVersion(),
                snapshot.modifierVersion(), snapshot.stateVersion());
    }

    private static void assertFluidDecision(GameTestHelper helper, FluidProbabilityHandler handler, int count) {
        helper.assertTrue(handler.attempts.size() == count, "Real fluid handler plans once per formal attempt");
        var attempt = handler.attempts.getLast();
        helper.assertTrue(attempt.reserved.size() == 1 && attempt.materialized.equals(attempt.reserved)
                        && attempt.committed.equals(attempt.reserved),
                "Reservation, materialization and actual fluid commit reuse one probability decision");
    }

    private static CraftingRuntime attachedRuntime(MachineControllerBlockEntity controller) {
        try {
            var field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
            field.setAccessible(true);
            return ((MachineControllerRuntime) field.get(controller)).craftingRuntime();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot inspect the real attached runtime", exception);
        }
    }

    private static void setWorkMode(MachineControllerBlockEntity controller, MachineWorkMode mode) {
        try {
            var field = MachineControllerBlockEntity.class.getDeclaredField("activeWorkMode");
            field.setAccessible(true);
            field.set(controller, mode);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot set the fixture controller work mode", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static RequirementType<ProbabilityFluidRequirement> fluidProbabilityType() {
        Identifier id = MMCR.id("gametest_installed_fluid_probability");
        var registered = RequirementHandlerRegistry.typeFor(id);
        if (registered != null) return (RequirementType<ProbabilityFluidRequirement>) registered;
        var handler = new FluidProbabilityHandler();
        RequirementType<ProbabilityFluidRequirement> type = new RequirementType<>() {
            @Override public Identifier id() { return id; }
            @Override public Set<Identifier> capabilityIds() { return FluidRequirement.TYPE.capabilityIds(); }
            @Override public MapCodec<ProbabilityFluidRequirement> codec() {
                return RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.STRING.fieldOf("type").forGetter(value -> id.toString()),
                        FluidRequirement.CODEC.codec().fieldOf("requirement").forGetter(ProbabilityFluidRequirement::requirement)
                ).apply(instance, (ignored, value) -> new ProbabilityFluidRequirement(this, value)));
            }
            @Override public RequirementHandler<ProbabilityFluidRequirement> handler() { return handler; }
            @Override public ProbabilityFluidRequirement copy(ProbabilityFluidRequirement value) {
                return new ProbabilityFluidRequirement(this, FluidRequirement.TYPE.copy(value.requirement()));
            }
        };
        RequirementHandlerRegistry.register(type);
        return type;
    }

    /** Retains actual fluid probability in the installed compatibility stage. @author howxu <dev@howxu.cn> */
    private record ProbabilityFluidRequirement(RequirementType<ProbabilityFluidRequirement> type,
                                               FluidRequirement requirement) implements MachineRequirement {
        @Override public IOType io() { return IOType.INPUT; }
    }

    /** One real fluid plan's observed reservation, materialization and mutation. @author howxu <dev@howxu.cn> */
    private static final class FluidAttempt {
        private final List<Long> reserved = new ArrayList<>();
        private final List<Long> materialized = new ArrayList<>();
        private final List<Long> committed = new ArrayList<>();
    }

    /** Delegates probability and resource operations, controlling only the final rejection. @author howxu <dev@howxu.cn> */
    private static final class FluidProbabilityHandler implements RequirementHandler<ProbabilityFluidRequirement> {
        private final FluidRequirementHandler delegate = new FluidRequirementHandler();
        private final List<FluidAttempt> attempts = new ArrayList<>();
        private boolean rejectOperation;

        @Override
        public RequirementPlan plan(ProbabilityFluidRequirement value, List<MachineCapability> capabilities, PlanningContext context) {
            var attempt = new FluidAttempt();
            attempts.add(attempt);
            var real = delegate.plan(value.requirement(), capabilities, context);
            if (!real.successful()) return real;
            var storage = CapabilityFactories.resourceStorage(capabilities.getFirst(), FluidResource.class);
            return new RequirementPlan(real.requirementIndex(), real.maxParallelism(), real.operations(), real.failure(),
                    real.outputSimulation(), (parallelism, reservations) -> {
                        long before = reservations.amount(storage, 0);
                        var result = real.operationFactory().create(parallelism, reservations);
                        attempt.materialized.add(before - reservations.amount(storage, 0));
                        long initial = storage.amount(0);
                        List<CapabilityOperation> operations = new ArrayList<>(result.operations());
                        operations.add(transaction -> {
                            attempt.committed.add(initial - storage.amount(0));
                            return rejectOperation ? CapabilityResult.failure(RequirementHandlerSupport.blocked(
                                    value.requirement(), context, BuiltinFailureReasons.COMMIT_LOST_INPUT)) : CapabilityResult.successful();
                        });
                        return new RequirementPlan.OperationPlan(operations, result.failure(), result.outputSimulation());
                    }, (parallelism, reservations) -> {
                        long before = reservations.amount(storage, 0);
                        var failure = real.reservationFactory().reserve(parallelism, reservations);
                        attempt.reserved.add(before - reservations.amount(storage, 0));
                        return failure;
                    }, real.preparedParallelism());
        }
    }

    public static void mixedInputCommitRollsBackAfterEnergyChanges(GameTestHelper helper) {
        // Native matching/component encoding is main-thread preparation; workers only compile/read the stage.
        var nestedName = Component.literal("captured nested name");
        var source = new ItemStack(Items.GOLD_NUGGET, 2);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("root: ").append(nestedName));
        var requirement = new ItemRequirement(IOType.OUTPUT, null, 0, source);
        Stage captured = CraftingContext.prepareCompiledStage(List.of(requirement), 1L, List.of(), Map.of());
        helper.assertTrue(captured.fallbackIndices().isEmpty() && captured.requirements().size() == 1,
                "Non-empty component-bearing native item is captured as a resource rule, not fallback");
        var request = (AsyncCapabilityRequest.Resource) captured.requirements().getFirst().requests().getFirst();
        var oldValue = request.actions().getFirst().resource();
        String oldName = source.get(DataComponents.CUSTOM_NAME).getString();
        nestedName.append(Component.literal(" changed after capture"));
        source.setCount(9);
        helper.assertTrue(!NativeAsyncResourceValues.item(ItemResource.of(requirement.stack())).equals(oldValue),
                "Mutating the shared nested component really changes the native requirement after capture");
        var recipeId = MMCR.id("captured_mutable_item_rule");
        var versions = new VersionStamp(1L, 2L, 3L, 4L, 5L, 6L);
        var worker = CompletableFuture.supplyAsync(() -> new RecipeProgramCompiler().compile(recipeId, 1, 1L,
                versions, Stage.empty(), Stage.empty(), captured, false));
        var otherReader = CompletableFuture.supplyAsync(() -> {
            var program = new RecipeProgramCompiler().compile(recipeId, 1, 1L, versions,
                    Stage.empty(), Stage.empty(), captured, false);
            return ((AsyncCapabilityRequest.Resource) program.finish().requirements().getFirst()
                    .requests().getFirst()).actions().getFirst();
        });
        var compiledRequest = (AsyncCapabilityRequest.Resource) worker.join().finish().requirements().getFirst()
                .requests().getFirst();
        var action = compiledRequest.actions().getFirst();
        helper.assertTrue(action.resource().equals(oldValue) && action.amount() == 2L && action.insert()
                        && otherReader.join().equals(action),
                "Independent worker readers preserve the old encoded component and captured amount");
        var readerStack = NativeAsyncResourceValues.item(action.resource()).toStack(2);
        helper.assertTrue(readerStack.get(DataComponents.CUSTOM_NAME).getString().equals(oldName),
                "Registry-backed decode of the compiled value retains the pre-mutation nested component");
        ((MutableComponent) readerStack.get(DataComponents.CUSTOM_NAME)).withColor(0xFF0000);
        helper.assertTrue(!NativeAsyncResourceValues.item(ItemResource.of(readerStack)).equals(oldValue)
                        && NativeAsyncResourceValues.item(ItemResource.of(NativeAsyncResourceValues.item(
                                otherReader.join().resource()).toStack(2))).equals(oldValue),
                "Mutating one decoded reader's component cannot affect the other reader's captured value");
        var input = place(helper, new BlockPos(1, 1, 1), "item_input_bus", ItemInputBusBlockEntity.class);
        var fluid = place(helper, new BlockPos(2, 1, 1), "fluid_input_hatch", FluidInputHatchBlockEntity.class);
        var energy = place(helper, new BlockPos(3, 1, 1), "energy_input_hatch", EnergyInputHatchBlockEntity.class);
        try (Transaction transaction = Transaction.openRoot()) {
            helper.assertTrue(input.itemStorage().insert(0, ItemResource.of(Items.IRON_INGOT), 2, transaction) == 2,
                    "Registered input bus accepts fixture ingredients");
            helper.assertTrue(fluid.fluidStorage().insert(0, FluidResource.of(Fluids.WATER), 250, transaction) == 250,
                    "Registered fluid hatch accepts fixture water");
            helper.assertTrue(energy.getEnergyStorage().insert(20, transaction) == 20,
                    "Registered energy hatch accepts fixture energy");
            transaction.commit();
        }
        var context = context(List.of(input, fluid, energy));
        var planned = context.planRequirements(List.of(itemInput(2, 1F),
                new FluidRequirement(IOType.INPUT, FluidIngredient.of(Fluids.WATER), 250, FluidStack.EMPTY),
                new EnergyRequirement(7)), 1, Map.of());
        helper.assertTrue(planned.successful() && planned.plan() != null, "Mixed plan is initially feasible");
        helper.assertTrue(input.itemStorage().amount(0) == 2 && fluid.fluidStorage().amount(0) == 250
                        && energy.getEnergyStorage().amount() == 20,
                "Planning does not consume any resource family");
        try (Transaction transaction = Transaction.openRoot()) {
            energy.getEnergyStorage().extract(16, transaction);
            transaction.commit();
        }
        var ledger = new RecipeCommitLedger();
        var before = ledger.state();
        try (Transaction transaction = Transaction.openRoot()) {
            ledger.markResourcePaid(0, false, transaction);
            helper.assertFalse(planned.plan().commit(transaction), "Later energy failure rejects the mixed commit");
            helper.assertTrue(input.itemStorage().amount(0) == 0 && fluid.fluidStorage().amount(0) == 0,
                    "Earlier item and fluid operations really ran before the failing energy operation");
        }
        helper.assertTrue(planned.plan().failure() != null
                        && planned.plan().failure().reason().id().equals(BuiltinFailureReasons.MISSING_INPUT.id()),
                "Stale energy commit reports the registered missing-input reason");
        helper.assertTrue(input.itemStorage().amount(0) == 2 && fluid.fluidStorage().amount(0) == 250
                        && energy.getEnergyStorage().amount() == 4 && ledger.state().equals(before),
                "Root rollback restores all recipe mutations and payment facts, preserving the independent energy removal");
        var retry = context.planRequirements(List.of(itemInput(2, 1F),
                new FluidRequirement(IOType.INPUT, FluidIngredient.of(Fluids.WATER), 250, FluidStack.EMPTY),
                new EnergyRequirement(4)), 1, Map.of());
        helper.assertTrue(retry.successful() && retry.plan() != null && retry.plan().commit(),
                "A fresh mixed plan can consume the conserved resources exactly once");
        helper.assertTrue(input.itemStorage().amount(0) == 0 && fluid.fluidStorage().amount(0) == 0
                        && energy.getEnergyStorage().amount() == 0,
                "Successful retry consumes each actual resource family");
        helper.succeed();
    }

    public static void removedRetainedInputStopsContinuousPayment(GameTestHelper helper) {
        var controller = retainedInputController(helper);
        var input = helper.getBlockEntity(new BlockPos(1, 2, 0), ItemInputBusBlockEntity.class);
        var energy = helper.getBlockEntity(new BlockPos(2, 2, 1), EnergyInputHatchBlockEntity.class);
        var runtime = attachedRuntime(controller);
        var retained = itemInput(1, 0F);
        var recipe = MachineRecipe.fromCanonical(MMCR.id("running_retained_input"), controller.currentRecipePoolId(),
                3, List.of(retained, new EnergyRequirement(5)), List.of(), List.of(), 0, 1,
                false, false, false, Set.of());
        long originalTime = helper.getLevel().getGameTime();
        long time = originalTime;
        try {
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertTrue(input.itemStorage().insert(0, ItemResource.of(Items.IRON_INGOT), 1, transaction) == 1,
                        "Retained fixture transaction inserts its actual ingredient");
                helper.assertTrue(energy.getEnergyStorage().insert(40, transaction) == 40,
                        "Retained fixture transaction inserts its actual FE");
                transaction.commit();
            }
            var capabilities = RequirementPlanner.matchingCapabilities(retained, controller.componentRuntime().capabilities());
            helper.assertTrue(RequirementHandlerRegistry.handlerFor(retained.type()) == ItemRequirement.TYPE.handler()
                            && capabilities.size() == 1
                            && CapabilityFactories.resourceStorage(capabilities.getFirst(), ItemResource.class) == input.itemStorage(),
                    "Registered retained handler resolves the real typed input capability with its source, direction and tags");
            var planned = context(List.of(input, energy)).planInputs(recipe, 1);
            helper.assertTrue(planned.successful() && planned.plan() != null && !planned.plan().hasOperations(0),
                    "Available retained input is checked without extraction; planning failure=" + planned.failure());
            var started = runtime.start(recipe, 1);
            helper.assertTrue(started.isCrafting(), "Recipe starts with a retained ingredient: status=" + started.getStatus()
                    + " failure=" + runtime.failure() + " pool=" + controller.currentRecipePoolId());
            long afterStart = energy.getEnergyStorage().amount();
            helper.assertTrue(input.itemStorage().amount(0) == 1, "Start leaves the retained ingredient in its actual bus");
            var installed = runtime.ensureInstalledProgram();
            helper.assertTrue(installed != null, "Actual retained execution installs its runtime program");
            ((ServerLevelData) helper.getLevel().getLevelData()).setGameTime(++time);
            long round = SharedIoCoordinator.get(helper.getLevel()).beginExecutionRound(time, true);
            helper.assertTrue(installed.tryAttempt(round, time) && installed.advance(), "First retained attempt succeeds");
            helper.assertTrue(energy.getEnergyStorage().amount() == afterStart - 5 && runtime.tickCount() == 1,
                    "First explicit continuous operation pays its energy and advances once");
            int paidProgress = runtime.tickCount();
            var paid = runtime.ledger().state();
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertTrue(input.itemStorage().extract(0, ItemResource.of(Items.IRON_INGOT), 1, transaction) == 1,
                        "External transaction really removes the retained ingredient");
                transaction.commit();
            }
            long beforeBlocked = energy.getEnergyStorage().amount();
            ((ServerLevelData) helper.getLevel().getLevelData()).setGameTime(++time);
            round = SharedIoCoordinator.get(helper.getLevel()).beginExecutionRound(time, true);
            helper.assertTrue(installed.tryAttempt(round, time), "Removed retained input gets a fresh formal attempt");
            helper.assertFalse(installed.advance(), "Removed retained input rejects the actual continuous operation");
            helper.assertTrue(runtime.failure() != null
                            && runtime.failure().reason().id().equals(BuiltinFailureReasons.MISSING_INPUT.id()),
                    "Removing the retained input reports a structured missing-input failure: " + runtime.failure());
            helper.assertTrue(runtime.active() && runtime.tickCount() == paidProgress
                            && energy.getEnergyStorage().amount() == beforeBlocked && runtime.ledger().state().equals(paid),
                    "Failed retained-input validation neither advances progress nor pays continuous energy");
            try (Transaction transaction = Transaction.openRoot()) {
                helper.assertTrue(input.itemStorage().insert(0, ItemResource.of(Items.IRON_INGOT), 1, transaction) == 1,
                        "External transaction restores the actual retained ingredient");
                transaction.commit();
            }
            ((ServerLevelData) helper.getLevel().getLevelData()).setGameTime(++time);
            round = SharedIoCoordinator.get(helper.getLevel()).beginExecutionRound(time, true);
            helper.assertTrue(installed.tryAttempt(round, time) && installed.advance(), "Restored retained attempt succeeds");
            helper.assertTrue(runtime.tickCount() == paidProgress + 1
                            && energy.getEnergyStorage().amount() == beforeBlocked - 5
                            && input.itemStorage().amount(0) == 1,
                    "Restoring the retained input resumes one paid operation without consuming the ingredient");
        } finally {
            runtime.invalidate();
            SharedIoCoordinator.get(helper.getLevel()).cancel(controller.getBlockPos());
            controller.releaseStructureClaims();
            ((ServerLevelData) helper.getLevel().getLevelData()).setGameTime(originalTime);
        }
        helper.succeed();
    }

    private static MachineControllerBlockEntity retainedInputController(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            helper.setBlock(new BlockPos(x, 1, z), ModBlocks.CASING.get().defaultBlockState());
        }
        var machineId = MMCR.id("iron_compressor");
        var pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.controllerFor(machineId).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH)
                .setValue(MachineControllerBlock.ROLL_FACING, Direction.NORTH));
        place(helper, new BlockPos(1, 2, 0), "item_input_bus", ItemInputBusBlockEntity.class);
        place(helper, new BlockPos(1, 2, 2), "item_output_bus", ItemOutputBusBlockEntity.class);
        place(helper, new BlockPos(2, 2, 1), "energy_input_hatch", EnergyInputHatchBlockEntity.class);
        var controller = helper.getBlockEntity(pos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(machineId));
        var coordinator = SharedIoCoordinator.get(helper.getLevel());
        coordinator.beginExecutionRound(helper.getLevel().getGameTime(), true);
        controller.serverTick();
        helper.assertTrue(controller.currentStructureSnapshot().formed()
                        && controller.currentStructureSnapshot().structureAreaLoaded() && controller.resourceDomain() != null,
                "Registered compressor forms and binds actual ports synchronously");
        setWorkMode(controller, MachineWorkMode.SYNC);
        helper.assertTrue(coordinator.controllerPermit(controller.getBlockPos()).eligible(coordinator.executionRoundId(),
                        helper.getLevel().getGameTime(), true), "Real BE ticker grants start eligibility");
        return controller;
    }

    public static void finalPaidTickSurvivesBlockedOutputReload(GameTestHelper helper) {
        var controller = retainedInputController(helper);
        var energy = helper.getBlockEntity(new BlockPos(2, 2, 1), EnergyInputHatchBlockEntity.class);
        var output = helper.getBlockEntity(new BlockPos(1, 2, 2), ItemOutputBusBlockEntity.class);
        energy.getEnergyStorage().setAmount(40);
        var runtime = attachedRuntime(controller);
        long gameTime = helper.getLevel().getGameTime();
        try {
            var recipe = MachineRecipe.fromCanonical(MMCR.id("running_final_paid_reload"), controller.currentRecipePoolId(),
                    1, List.of(new EnergyRequirement(7),
                            new ItemRequirement(IOType.OUTPUT, null, 0, new ItemStack(Items.GOLD_NUGGET, 2))),
                    List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_NUGGET, 2), 1F)),
                    List.of(), 0, 1, false, false, false, Set.of());
            RecipeRegistry.registerStatic(recipe);
            var started = runtime.start(recipe, 1);
            helper.assertTrue(started.isCrafting(), "Registered recipe starts before output is blocked: status="
                    + started.getStatus() + " failure=" + runtime.failure() + " pool=" + controller.currentRecipePoolId());
            long afterStart = energy.getEnergyStorage().amount();
            long startPayments = runtime.ledger().state().paidAttempts();
            fill(output.itemStorage());
            runtime.tick();
            helper.assertTrue(runtime.finishPending() && energy.getEnergyStorage().amount() == afterStart - 7,
                    "Final continuous operation pays once and enters finish-pending");
            var finalPayment = runtime.ledger().state();
            helper.assertTrue(finalPayment.phase() == RecipeCommitLedger.Phase.WAITING_FINISH
                            && finalPayment.finalTickPaid() && finalPayment.paidAttempts() == startPayments + 1,
                    "Runtime ledger records the committed final continuous operation");
            runtime.finish();
            helper.assertTrue(runtime.failure() != null
                            && runtime.failure().reason().id().equals(BuiltinFailureReasons.MISSING_OUTPUT.id()),
                    "Full actual output bus reports the registered missing-output reason");
            long paidBalance = energy.getEnergyStorage().amount();
            var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
            runtime.save(saved);
            runtime.invalidate();
            var restored = attachedRuntime(controller);
            restored.load(input(helper, saved.buildResult()), controller.resourceDomain());
            restored.rebindCurrentVersions();
            helper.assertTrue(restored.active() && restored.finishPending() && restored.ledger().state().equals(finalPayment)
                            && energy.getEnergyStorage().amount() == paidBalance,
                    "Real lookup restores the blocked finish phase and its actual payment facts");
            restored.tick();
            // Retry while still full at the persisted deadline, then clear output and drive the new deadline.
            int retryTime = saved.buildResult().getCompoundOrEmpty("recipe").getIntOr("nextFinishRetryTick", 0);
            helper.assertTrue(retryTime > gameTime, "Blocked finish persists a future retry deadline");
            ((ServerLevelData) helper.getLevel().getLevelData()).setGameTime(Math.max(gameTime, retryTime));
            restored.finish();
            helper.assertTrue(restored.failure() != null
                            && restored.failure().reason().id().equals(BuiltinFailureReasons.MISSING_OUTPUT.id()),
                    "Due restored finish really retries the full output and reports missing output");
            helper.assertTrue(restored.active() && restored.finishPending()
                            && energy.getEnergyStorage().amount() == paidBalance
                            && restored.ledger().state().equals(finalPayment),
                    "Failed due retry preserves the final paid tick and paid-attempt count");
            for (int slot = 0; slot < output.itemStorage().size(); slot++) {
                var resource = output.itemStorage().resource(slot);
                helper.assertTrue(ItemResource.of(Items.COBBLESTONE).equals(resource)
                                && output.itemStorage().amount(slot) == output.itemStorage().capacity(slot, resource),
                        "Failed due retry leaves each actual output slot full and unchanged");
            }
            var blockedSave = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
            restored.save(blockedSave);
            int nextRetryTime = blockedSave.buildResult().getCompoundOrEmpty("recipe").getIntOr("nextFinishRetryTick", 0);
            helper.assertTrue(nextRetryTime > helper.getLevel().getGameTime(),
                    "Actual failed retry schedules a fresh future deadline rather than short-circuiting the old gate");
            try (Transaction transaction = Transaction.openRoot()) {
                output.itemStorage().extract(0, output.itemStorage().resource(0), output.itemStorage().amount(0), transaction);
                transaction.commit();
            }
            ((ServerLevelData) helper.getLevel().getLevelData()).setGameTime(nextRetryTime);
            restored.finish();
            helper.assertTrue(!restored.active() && ItemResource.of(Items.GOLD_NUGGET).equals(output.itemStorage().resource(0))
                            && output.itemStorage().amount(0) == 2 && energy.getEnergyStorage().amount() == paidBalance,
                    "Restored finish produces exactly the registered output without charging continuous resources again");
            helper.assertTrue(restored.ledger().state().outputPaid()
                            && restored.ledger().state().paidAttempts() == finalPayment.paidAttempts(),
                    "Terminal ledger records output payment without another continuous attempt");
            restored.tick();
            restored.finish();
            var terminalSave = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
            restored.save(terminalSave);
            var terminal = attachedRuntime(controller);
            terminal.load(input(helper, terminalSave.buildResult()), controller.resourceDomain());
            terminal.tick();
            terminal.finish();
            helper.assertTrue(!terminal.active() && output.itemStorage().amount(0) == 2
                            && energy.getEnergyStorage().amount() == paidBalance,
                    "Repeated terminal operations and terminal reload do not duplicate output or energy payment");
        } finally {
            runtime.invalidate();
            SharedIoCoordinator.get(helper.getLevel()).cancel(controller.getBlockPos());
            controller.releaseStructureClaims();
            ((ServerLevelData) helper.getLevel().getLevelData()).setGameTime(gameTime);
        }
        helper.succeed();
    }

    public static void resourcePaidSaveReentranceRestoresPausedLedger(GameTestHelper helper) {
        var controller = retainedInputController(helper);
        var energy = helper.getBlockEntity(new BlockPos(2, 2, 1), EnergyInputHatchBlockEntity.class);
        energy.getEnergyStorage().setAmount(40);
        var runtime = attachedRuntime(controller);
        MachineControllerBlockEntity restoredController = null;
        try {
            var recipe = MachineRecipe.fromCanonical(MMCR.id("running_resource_paid_reentrant_save"), controller.currentRecipePoolId(),
                    2, List.of(new EnergyRequirement(7)), List.of(), List.of(), 0, 1,
                    false, false, false, Set.of());
            RecipeRegistry.registerStatic(recipe);
            var savedRuntime = new AtomicReference<CompoundTag>();
            var savedPort = new AtomicReference<CompoundTag>();
            var notification = new SaveNotification(() -> {
                var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
                runtime.save(output);
                savedRuntime.set(output.buildResult());
                savedPort.set(energy.saveWithoutMetadata(helper.getLevel().registryAccess()));
            });
            var planned = context(List.of(energy)).planInputs(recipe, 1);
            helper.assertTrue(planned.successful() && planned.plan() != null,
                    "Actual hatch payment is feasible: failure=" + planned.failure());
            var prepared = runtime.preparePatternStart(recipe, 1, List.of());
            helper.assertTrue(prepared != null, "Attached runtime prepares actual hatch payment: failure=" + runtime.failure()
                    + " pool=" + controller.currentRecipePoolId() + " module=" + controller.currentRuntimeSnapshot().moduleConnectionStatus());
            helper.assertTrue(runtime.reservePatternStart(), "Formed, ticker-qualified attached runtime reserves hatch payment");
            helper.assertTrue(runtime.commitPatternStart(prepared, transaction -> {
                notification.enlist(transaction);
                helper.assertTrue(savedRuntime.get() == null, "Save notification cannot run before root commit");
            }), "Runtime commits resources and ledger through its production start path");
            helper.assertTrue(savedRuntime.get() != null && savedPort.get() != null, "Root notification saved real port and runtime NBT");
            helper.assertTrue(savedRuntime.get().getCompoundOrEmpty("execution_commit").getStringOr("phase", "")
                            .equals(RecipeCommitLedger.Phase.RESOURCE_PAID.name()),
                    "Reentrant runtime save records unsettled RESOURCE_PAID rather than inventing progress");
            runtime.invalidate();
            SharedIoCoordinator.get(helper.getLevel()).cancel(controller.getBlockPos());
            controller.releaseStructureClaims();
            helper.setBlock(new BlockPos(1, 1, 1), Blocks.AIR.defaultBlockState());
            helper.setBlock(new BlockPos(2, 2, 1), Blocks.AIR.defaultBlockState());
            restoredController = retainedInputController(helper);
            var restoredEnergy = helper.getBlockEntity(new BlockPos(2, 2, 1), EnergyInputHatchBlockEntity.class);
            helper.assertTrue(restoredController != controller && restoredEnergy != energy,
                    "Reload uses fresh registered controller and hatch instances at the same positions");
            restoredEnergy.loadWithComponents(input(helper, savedPort.get()));
            var restored = attachedRuntime(restoredController);
            restored.load(input(helper, savedRuntime.get()), restoredController.resourceDomain());
            restored.rebindCurrentVersions();
            helper.assertTrue(restored.active() && restored.ledger().state().phase() == RecipeCommitLedger.Phase.ERROR_PAUSED
                            && restored.ledger().state().paidAttempts() == 1
                            && restoredEnergy.getEnergyStorage().amount() == energy.getEnergyStorage().amount()
                            && restoredEnergy.getEnergyStorage().amount() == 40,
                    "Unsettled payment restores paused with its committed actual hatch balance");
            var paused = restored.ledger().state();
            restored.tick();
            restored.finish();
            helper.assertTrue(restored.ensureInstalledProgram() == null && restored.captureProgramCompilation() == null,
                    "Restored ERROR_PAUSED runtime cannot install or submit worker compilation");
            helper.assertTrue(restored.ledger().state().equals(paused)
                            && restored.tickCount() == 0 && restoredEnergy.getEnergyStorage().amount() == 40,
                    "Restored ERROR_PAUSED runtime cannot silently resume or replay payment through either execution path");
        } finally {
            runtime.invalidate();
            var currentController = restoredController == null ? controller : restoredController;
            attachedRuntime(currentController).invalidate();
            SharedIoCoordinator.get(helper.getLevel()).cancel(currentController.getBlockPos());
            currentController.releaseStructureClaims();
        }
        helper.succeed();
    }

    public static void patternAdmissionRollsBackRequestAndOrdinaryInput(GameTestHelper helper) {
        var controller = retainedInputController(helper);
        var port = place(helper, new BlockPos(3, 1, 3), "ae2_me_pattern_interface", PatternInterfaceBlockEntity.class);
        var ordinary = helper.getBlockEntity(new BlockPos(1, 2, 0), ItemInputBusBlockEntity.class);
        insert(ordinary.itemStorage(), 0, ItemResource.of(Items.COAL), 1);
        port.linkControllerAppearance(controller.getBlockPos(), null);
        var components = new ArrayList<>(controller.componentRuntime().components());
        components.add(new ProcessingComponent(new MachineComponent(port.kind(), port.ioType()), port, port.getBlockPos(),
                port.getBlockPos().subtract(controller.getBlockPos()), List.of()));
        controller.componentRuntime().replaceComponents(components);
        var linkedPorts = new HashSet<>(controller.componentRuntime().linkedPortPositions());
        linkedPorts.add(port.getBlockPos());
        controller.componentRuntime().replaceLinkedPortPositions(linkedPorts);
        var runtime = attachedRuntime(controller);
        try {
            helper.assertTrue(runtime.controller() == controller && runtime == attachedRuntime(controller)
                            && helper.getLevel().getBlockEntity(port.getBlockPos()) == port
                            && port.linkedControllerPositions().contains(controller.getBlockPos())
                            && controller.currentRuntimeSnapshot().linkedPortPositions().contains(port.getBlockPos()),
                    "Attached runtime and current registered host have matching bidirectional fixture links");
            var recipe = MachineRecipe.fromCanonical(MMCR.id("running_pattern_atomic_start"), controller.currentRecipePoolId(),
                    1, List.of(itemInput(1, 1F),
                            new ItemRequirement(IOType.INPUT, Ingredient.of(Items.COAL), 1, ItemStack.EMPTY),
                            new ItemRequirement(IOType.OUTPUT, null, 0, new ItemStack(Items.GOLD_INGOT))),
                    List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT), 1F)),
                    List.of(), 0, 1, false, false, false, Set.of());
            helper.assertTrue(helper.getLevel().getBlockEntity(controller.getBlockPos()) == controller
                            && recipe.recipePoolId().equals(controller.currentRecipePoolId())
                            && controller.currentRuntimeSnapshot().moduleConnectionStatus().canRunRecipe(recipe.requiredHostIds()),
                    "Current BE, recipe pool and module gate qualify the actual pattern fixture");
            var withoutRequest = context(List.of(ordinary)).planInputs(recipe, 1);
            helper.assertTrue(!withoutRequest.successful() && withoutRequest.failure() != null
                            && withoutRequest.failure().reason().id().equals(BuiltinFailureReasons.MISSING_INPUT.id()),
                    "Ordinary coal alone cannot supply the pattern iron: failure=" + withoutRequest.failure());
            var holder = new KeyCounter();
            var iron = AEItemKey.of(Items.IRON_INGOT);
            holder.add(iron, 3);
            var request = port.itemRequestStorage(new KeyCounter[]{holder});
            List<MachineCapability> requestCapabilities = List.of(new ItemBusCapability(port, request, cn.howxu.mmcr.util.IOType.INPUT));
            helper.assertTrue(CapabilityFactories.resourceStorage(requestCapabilities.getFirst(), ItemResource.class) == request
                            && request.amount(0) == 3 && holder.get(iron) == 3,
                    "Typed INPUT capability binds the real provider request holder, not the ordinary bus");
            var patternContext = new CraftingContext(controller.componentRuntime().capabilitySnapshot())
                    .forPatternStart(requestCapabilities, port.getBlockPos());
            helper.assertTrue(patternContext.inputRouting().inputPorts().stream()
                            .anyMatch(source -> source.position().equals(port.getBlockPos()) && !source.isolated()),
                    "Actual registered pattern host participates in the controller's ordinary input routing");
            var planned = patternContext.planInputs(recipe, 1);
            helper.assertTrue(planned.successful() && planned.plan() != null,
                    "Actual request plus ordinary input is feasible: failure=" + planned.failure());
            var prepared = runtime.preparePatternStart(recipe, 1, requestCapabilities, port.getBlockPos());
            helper.assertTrue(prepared != null, "Attached runtime prepares actual pattern request storage: failure=" + runtime.failure()
                    + " pool=" + controller.currentRecipePoolId() + " module=" + controller.currentRuntimeSnapshot().moduleConnectionStatus());
            helper.assertTrue(prepared.plan().hasOperations(0) && prepared.plan().hasOperations(1),
                    "Pattern preparation includes actual iron request extraction and ordinary coal extraction");
            helper.assertTrue(runtime.reservePatternStart(), "Formed, ticker-qualified runtime reserves the actual request start");
            try (Transaction transaction = Transaction.openRoot()) {
                ordinary.itemStorage().extract(0, ItemResource.of(Items.COAL), 1, transaction);
                transaction.commit();
            }
            helper.assertFalse(runtime.commitPatternStart(prepared, transaction -> {
                throw new AssertionError("Request acceptance cannot run after ordinary input failure");
            }), "Lost ordinary ingredient rejects pattern admission");
            helper.assertTrue(runtime.failure() != null && runtime.failure().reason().id().equals(BuiltinFailureReasons.MISSING_INPUT.id()),
                    "Rejected admission reports the actual input failure");
            helper.assertTrue(!runtime.active() && request.amount(0) == 3 && holder.get(iron) == 3
                            && port.getLogic().getReturnInv().isEmpty() && ordinary.itemStorage().amount(0) == 0,
                    "Failed admission restores request extraction and native return inventory, preserving external coal removal");
            insert(ordinary.itemStorage(), 0, ItemResource.of(Items.COAL), 1);
            prepared = runtime.preparePatternStart(recipe, 1, requestCapabilities, port.getBlockPos());
            helper.assertTrue(prepared != null && runtime.reservePatternStart(), "Rejected start releases its reservation for retry");
            helper.assertTrue(runtime.commitPatternStart(prepared, transaction -> helper.assertTrue(
                            request.accept(port.itemReturnStorage(), transaction), "Native return inventory accepts unconsumed request remainder")),
                    "Pattern request and ordinary input commit in one real start transaction");
            helper.assertTrue(runtime.active() && ordinary.itemStorage().amount(0) == 0 && holder.get(iron) == 0
                            && port.itemReturnStorage().amount(0) == 2,
                    "Committed start consumes one iron and coal and returns the remaining two iron exactly once");
        } finally {
            runtime.invalidate();
            SharedIoCoordinator.get(helper.getLevel()).cancel(controller.getBlockPos());
            controller.releaseStructureClaims();
        }
        helper.succeed();
    }

    public static void sameGameTimeRoundsRequireFreshBlockEntityEligibility(GameTestHelper helper) {
        var controller = controller(helper, List.of());
        var level = helper.getLevel();
        var coordinator = SharedIoCoordinator.get(level);
        long gameTime = level.getGameTime();
        long first = coordinator.beginExecutionRound(gameTime, true);
        controller.serverTick();
        var permit = coordinator.controllerPermit(controller.getBlockPos());
        helper.assertTrue(permit.eligible(first, gameTime, true), "Actual BE ticker grants this round's eligibility");
        LevelTickFireEvents.completeLevelTick(level);
        long frozen = coordinator.beginExecutionRound(gameTime, false);
        helper.assertFalse(permit.eligible(frozen, gameTime, false), "Frozen round rejects the old permit even at the same gameTime");
        LevelTickFireEvents.completeLevelTick(level);
        long resumed = coordinator.beginExecutionRound(gameTime, true);
        helper.assertFalse(permit.eligible(resumed, gameTime, true), "Resuming without a BE ticker cannot reuse the old qualification");
        LevelTickFireEvents.completeLevelTick(level);
        helper.assertFalse(permit.eligible(resumed, gameTime, true), "Post work cannot grant eligibility to a non-ticking BE");
        controller.serverTick();
        permit = coordinator.controllerPermit(controller.getBlockPos());
        helper.assertTrue(permit.eligible(resumed, gameTime, true), "Fresh actual BE ticker restores eligibility");
        coordinator.cancel(controller.getBlockPos());
        helper.assertFalse(permit.eligible(resumed, gameTime, true), "Cancellation immediately invalidates an already-held permit");
        helper.succeed();
    }

    private static MachineControllerBlockEntity controller(GameTestHelper helper, List<? extends IOPortBlockEntity> ports) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, ModBlocks.controllerFor(MMCR.id("test_cube")).get().defaultBlockState());
        var controller = helper.getBlockEntity(pos, MachineControllerBlockEntity.class);
        controller.setMachine(MachineRegistry.getMachine(MMCR.id("test_cube")));
        for (var port : ports) port.linkControllerAppearance(controller.getBlockPos(), null);
        controller.componentRuntime().replaceComponents(ports.stream().map(port -> new ProcessingComponent(
                new MachineComponent(port.kind(), port.ioType()), port, port.getBlockPos(),
                port.getBlockPos().subtract(controller.getBlockPos()), List.of())).toList());
        return controller;
    }

    private static CraftingContext context(List<? extends IOPortBlockEntity> ports) {
        return new CraftingContext(new CapabilitySnapshot(ports.stream()
                .flatMap(port -> port.capabilitySnapshot().capabilities().stream()).toList()));
    }

    private static ItemRequirement itemInput(int count, float consumeChance) {
        return new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT), count, ItemStack.EMPTY,
                1F, DataComponentPredicateSet.EMPTY, consumeChance);
    }

    private static MachineRecipe recipe(String name, int duration, List<MachineRequirement> requirements, List<MachineOutput> outputs) {
        return MachineRecipe.fromCanonical(MMCR.id(name), MMCR.id("test_cube"), duration, requirements,
                outputs, List.of(), 0, 1, false, false, false, Set.of());
    }

    private static <T extends IOPortBlockEntity> T place(GameTestHelper helper, BlockPos pos, String id, Class<T> type) {
        helper.setBlock(pos, ModBlocks.BLOCKS.get(id).get().defaultBlockState());
        return helper.getBlockEntity(pos, type);
    }

    private static void insert(ResourceStorage<ItemResource> storage, int slot, ItemResource resource, long amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            storage.insert(slot, resource, amount, transaction);
            transaction.commit();
        }
    }

    private static void fill(ResourceStorage<ItemResource> storage) {
        try (Transaction transaction = Transaction.openRoot()) {
            var resource = ItemResource.of(Items.COBBLESTONE);
            for (int slot = 0; slot < storage.size(); slot++) {
                storage.insert(slot, resource, storage.capacity(slot, resource), transaction);
            }
            transaction.commit();
        }
    }

    private static ValueInput input(GameTestHelper helper, CompoundTag tag) {
        return TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag);
    }

    /** Root-commit notification seam; the resource and ledger journals remain production objects.
     * @author howxu <dev@howxu.cn>
     */
    private static final class SaveNotification extends SnapshotJournal<Integer> {
        private final Runnable save;
        private SaveNotification(Runnable save) { this.save = save; }
        private void enlist(TransactionContext transaction) { updateSnapshots(transaction); }
        @Override protected Integer createSnapshot() { return 0; }
        @Override protected void revertToSnapshot(Integer snapshot) { }
        @Override protected void onRootCommit(Integer originalState) { save.run(); }
    }
}
