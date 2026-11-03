package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.ParallelTier;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.recipe.RequirementPlanner;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.ConfigTestSupport;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Runtime/resource differential tests for the installed lane, with explicit attempts and no worker sleeps.
 * @author howxu <dev@howxu.cn>
 */
class InstalledRecipeProgramTest {
    private ServerThreadTestScope serverThreadScope;
    private static final Identifier MACHINE = MMCR.id("test_cube");
    private static final HolderLookup.Provider LOOKUP = HolderLookup.Provider.create(Stream.empty());

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        TestBootstrap.bootstrapCapabilities();
        CommentedConfig config = CommentedConfig.inMemory();
        ServerConfig.SPEC.correct(config);
        var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        ServerConfig.SPEC.acceptConfig((IConfigSpec.ILoadedConfig) constructor.newInstance(config, null, null));
    }

    @BeforeEach
    void installServerThread() throws Exception {
        serverThreadScope = ServerThreadTestScope.open();
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
            RecipeRegistry.clearForTesting();
        } finally {
            if (serverThreadScope != null) serverThreadScope.close();
        }
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void long_recipe_pays_real_resources_without_recompiling_or_rebuilding_callback_contexts(MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        var input = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        input.getEnergyStorage().setAmount(2000L);
        var controller = RuntimeTestFixtures.controller(MACHINE, input);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(runtime.start(recipe("installed_long", 200, List.of(new EnergyRequirement(7L))), 1).isCrafting()).isTrue();
        InstalledRecipeProgram installed = runtime.ensureInstalledProgram();
        long afterStart = input.getEnergyStorage().amount();
        int compilations = runtime.programCompileCountForTesting();
        int recipeContexts = runtime.recipeTickContextBuildCountForTesting();
        int capabilityContexts = runtime.capabilityTickContextBuildCountForTesting();
        for (int tick = 1; tick <= 100; tick++) {
            LevelStub.setGameTime(controller.getLevel(), tick);
            if (mode != MachineWorkMode.SYNC) assertThat(runtime.prepareInstalledTick(tick)).isTrue();
            assertThat(installed.tryAttempt(tick, tick)).isTrue();
            assertThat(installed.advance()).isTrue();
            assertThat(input.getEnergyStorage().amount()).isEqualTo(afterStart - 7L * tick);
            assertThat(runtime.tickCount()).isEqualTo(tick);
            assertThat(installed.tryAttempt(tick, tick)).isFalse();
            assertThat(runtime.ensureInstalledProgram()).isSameAs(installed);
        }
        assertThat(runtime.programCompileCountForTesting()).isEqualTo(compilations);
        assertThat(runtime.recipeTickContextBuildCountForTesting()).isEqualTo(recipeContexts);
        assertThat(runtime.capabilityTickContextBuildCountForTesting()).isEqualTo(capabilityContexts);
        assertThat(runtime.ledger().state().paidAttempts()).isEqualTo(101L); // Start and each actual resource transaction.
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void shortages_never_lower_active_parallelism_and_recovery_is_a_new_real_tick(MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        var input = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        input.getEnergyStorage().setAmount(100L);
        var controller = RuntimeTestFixtures.controller(MACHINE, input);
        makeParallel(controller);
        var runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("installed_fixed_parallel"), MACHINE, 20,
                List.of(new EnergyRequirement(7L)), List.of(), List.of(), 0, 1, false, true, List.of(), false, Set.of());
        assertThat(runtime.start(recipe, 3L).isCrafting()).isTrue();
        var installed = runtime.ensureInstalledProgram();
        long fixedParallelism = runtime.parallelism();
        assertThat(fixedParallelism).isEqualTo(3L);
        input.getEnergyStorage().setAmount(7L);
        LevelStub.setGameTime(controller.getLevel(), 1);
        if (mode != MachineWorkMode.SYNC) runtime.prepareInstalledTick(1L);
        assertThat(installed.tryAttempt(1L, 1L)).isTrue();
        assertThat(installed.advance()).isFalse();
        assertThat(input.getEnergyStorage().amount()).isEqualTo(7L);
        assertThat(runtime.tickCount()).isZero();
        assertThat(runtime.parallelism()).isEqualTo(fixedParallelism);
        assertThat(runtime.failure().reason()).isEqualTo(BuiltinFailureReasons.MISSING_ENERGY);
        input.getEnergyStorage().setAmount(100L);
        assertThat(installed.tryAttempt(2L, 1L)).isFalse();
        LevelStub.setGameTime(controller.getLevel(), 10);
        if (mode != MachineWorkMode.SYNC) runtime.prepareInstalledTick(10L);
        assertThat(installed.tryAttempt(10L, 10L)).isTrue();
        assertThat(installed.advance()).isTrue();
        assertThat(runtime.tickCount()).isEqualTo(1);
        assertThat(input.getEnergyStorage().amount()).isEqualTo(100L - 7L * fixedParallelism);
    }

    @Test
    void caller_owned_transaction_rolls_back_resource_and_paid_facts_together() {
        var input = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        input.getEnergyStorage().setAmount(100L);
        var controller = RuntimeTestFixtures.controller(MACHINE, input);
        var runtime = new CraftingRuntime(controller, controller.componentRuntime());
        runtime.start(recipe("installed_rollback", 5, List.of(new EnergyRequirement(7L))), 1);
        var installed = runtime.ensureInstalledProgram();
        long amount = input.getEnergyStorage().amount();
        var before = installed.ledger().state();
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(installed.executeContinuous(transaction)).isNull();
            installed.ledger().markResourcePaid(runtime.tickCount(), false, transaction);
        }
        assertThat(input.getEnergyStorage().amount()).isEqualTo(amount);
        assertThat(installed.ledger().state()).isEqualTo(before);
        assertThat(runtime.tickCount()).isZero();
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void installed_energy_input_debits_extreme_parallelism_in_one_rollback_safe_transaction(MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        long parallelism = Integer.MAX_VALUE;
        long amount = 1_000_000_000L * parallelism;
        AtomicInteger changes = new AtomicInteger();
        LongValueStorage storage = storage(Long.MAX_VALUE, 1_000_000_000L, amount, changes::incrementAndGet);
        changes.set(0);
        var runtime = runtimeWithCapabilities(List.of(input(storage)));
        var recipe = recipe("installed_bulk_energy", 5, List.of(new EnergyRequirement(1_000_000_000L)));
        var snapshot = runtime.controller().currentRuntimeSnapshot();
        var active = new ActiveMachineRecipe(recipe, parallelism);
        active.setParallelism(parallelism);
        runtime.restore(active, null, snapshot.structure().version(),
                snapshot.capabilityVersion(), snapshot.modifierVersion(), snapshot.stateVersion());
        var installed = runtime.ensureInstalledProgram();

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(installed.executeContinuous(transaction)).isNull();
            assertThat(storage.amount()).isZero();
            assertThat(changes.get()).isZero();
        }
        assertThat(storage.amount()).isEqualTo(amount);
        assertThat(changes.get()).isZero();
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(installed.executeContinuous(transaction)).isNull();
            transaction.commit();
        }
        assertThat(storage.amount()).isZero();
        assertThat(changes.get()).isEqualTo(1);
    }

    @Test
    void installed_energy_output_ignores_transfer_limit_and_revalidates_capacity() {
        LongValueStorage storage = storage(2_000_000L, 512_000L, 0L, null);
        var runtime = runtimeWithCapabilities(List.of(new EnergyHatchCapability(storage, IOType.OUTPUT)));
        var recipe = recipe("installed_capacity_output", 5,
                List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 800_000L)));
        assertThat(runtime.start(recipe, 1L).isCrafting()).isTrue();
        var installed = runtime.ensureInstalledProgram();
        long before = storage.amount();
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(installed.executeContinuous(transaction)).isNull();
            assertThat(storage.amount()).isEqualTo(before + 800_000L);
        }
        assertThat(storage.amount()).isEqualTo(before);
        storage.setAmount(1_300_000L);
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(installed.executeContinuous(transaction)).isNotNull();
        }
        assertThat(storage.amount()).isEqualTo(1_300_000L);
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void recipe_output_above_transfer_limit_advances_in_every_work_mode(MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        LongValueStorage storage = storage(4_000_000L, 0L, 0L, null);
        var runtime = runtimeWithCapabilities(List.of(new EnergyHatchCapability(storage, IOType.OUTPUT)));
        assertThat(runtime.start(recipe("installed_output_modes", 5,
                List.of(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 800_000L))), 1L).isCrafting()).isTrue();
        var installed = runtime.ensureInstalledProgram();
        long before = storage.amount();
        LevelStub.setGameTime(runtime.controller().getLevel(), 1L);
        if (mode != MachineWorkMode.SYNC) assertThat(runtime.prepareInstalledTick(1L)).isTrue();
        assertThat(installed.tryAttempt(1L, 1L)).isTrue();
        assertThat(installed.advance()).isTrue();
        assertThat(storage.amount()).isEqualTo(before + 800_000L);
        assertThat(runtime.tickCount()).isEqualTo(1);
    }

    @Test
    void closed_native_energy_path_matches_planner_with_aliases_split_ports_and_transfer_limits() {
        for (long limit : new long[] { 40L, 5L }) {
            LongValueStorage first = storage(40L, limit, 7L, null);
            LongValueStorage second = storage(40L, limit, 25L, null);
            LongValueStorage oldFirst = storage(40L, limit, 7L, null);
            LongValueStorage oldSecond = storage(40L, limit, 25L, null);
            List<MachineRequirement> requirements = List.of(new EnergyRequirement(8L), new EnergyRequirement(2L));
            var runtime = runtimeWithCapabilities(List.of(input(first), input(first), input(second)));
            MachineRecipe recipe = recipe("installed_differential_" + limit, 5, requirements);
            var snapshot = runtime.controller().currentRuntimeSnapshot();
            runtime.restore(new ActiveMachineRecipe(recipe, 2L), null, snapshot.structure().version(),
                    snapshot.capabilityVersion(), snapshot.modifierVersion(), snapshot.stateVersion());
            var installed = runtime.ensureInstalledProgram();
            first.setAmount(7L);
            second.setAmount(25L);
            var old = new RequirementPlanner().plan(requirements, List.of(input(oldFirst), input(oldFirst), input(oldSecond)),
                    new PlanningContext(runtime.parallelism(), 0));
            boolean oldCommitted = old.successful() && old.plan().commit();
            boolean newCommitted = false;
            try (Transaction transaction = Transaction.openRoot()) {
                if (installed.executeContinuous(transaction) == null) {
                    transaction.commit();
                    newCommitted = true;
                }
            }
            assertThat(newCommitted).isEqualTo(oldCommitted);
            assertThat(first.amount()).isEqualTo(oldFirst.amount());
            assertThat(second.amount()).isEqualTo(oldSecond.amount());
        }
    }

    @Test
    void notification_failure_preserves_payment_and_pauses_across_save_load_and_resume() {
        AtomicBoolean throwOnCommit = new AtomicBoolean();
        AtomicReference<CompoundTag> reentrantSave = new AtomicReference<>();
        AtomicReference<CraftingRuntime> owner = new AtomicReference<>();
        LongValueStorage storage = storage(100L, 100L, 60L, () -> {
            if (throwOnCommit.get()) {
                reentrantSave.set(save(owner.get()));
                throw new IllegalStateException("commit notification");
            }
        });
        CraftingRuntime runtime = runtimeWithCapabilities(List.of(input(storage)));
        owner.set(runtime);
        MachineRecipe recipe = recipe("installed_notification_pause", 5, List.of(new EnergyRequirement(7L)));
        RecipeRegistry.registerStatic(recipe);
        runtime.start(recipe, 1);
        var installed = runtime.ensureInstalledProgram();
        LevelStub.setGameTime(runtime.controller().getLevel(), 1);
        runtime.prepareInstalledTick(1L);
        long before = storage.amount();
        throwOnCommit.set(true);
        assertThat(installed.tryAttempt(1L, 1L)).isTrue();
        assertThat(installed.advance()).isFalse();
        assertThat(storage.amount()).isEqualTo(before - 7L);
        assertThat(runtime.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.ERROR_PAUSED);
        assertThat(runtime.tickCount()).isZero();
        throwOnCommit.set(false);
        runtime.resume();
        LevelStub.setGameTime(runtime.controller().getLevel(), 2);
        runtime.tick();
        assertThat(storage.amount()).isEqualTo(before - 7L);
        var restored = new CraftingRuntime(runtime.controller(), runtime.controller().componentRuntime());
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, LOOKUP, reentrantSave.get()), null);
        assertThat(restored.active()).isTrue();
        assertThat(restored.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.ERROR_PAUSED);
        restored.resume();
        restored.tick();
        assertThat(storage.amount()).isEqualTo(before - 7L);
    }

    @Test
    void cancelled_binding_and_late_compilation_cannot_operate_on_a_replacement_execution() {
        var input = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        input.getEnergyStorage().setAmount(100L);
        var controller = RuntimeTestFixtures.controller(MACHINE, input);
        var runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("installed_replacement", 5, List.of(new EnergyRequirement(7L)));
        runtime.start(recipe, 1);
        var old = runtime.ensureInstalledProgram();
        var pending = runtime.captureProgramCompilation();
        runtime.invalidate();
        runtime.start(recipe, 1);
        var replacement = runtime.ensureInstalledProgram();
        assertThat(replacement.identity()).isNotEqualTo(old.identity());
        long before = input.getEnergyStorage().amount();
        assertThat(old.tryAttempt(1L, 1L)).isFalse();
        assertThat(old.advance()).isFalse();
        assertThat(runtime.installCompiledProgram(pending.identity(), pending.compile())).isNull();
        assertThat(input.getEnergyStorage().amount()).isEqualTo(before);
    }

    @Test
    void mixed_catalyst_failure_rolls_back_the_whole_continuous_set_including_energy() {
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        var items = RuntimeTestFixtures.itemInput(new BlockPos(2, 0, 0));
        energy.getEnergyStorage().setAmount(100L);
        try (Transaction transaction = Transaction.openRoot()) {
            items.itemStorage().insert(0, ItemResource.of(new ItemStack(Items.IRON_INGOT)), 1L, transaction);
            transaction.commit();
        }
        var controller = RuntimeTestFixtures.controller(MACHINE, energy, items);
        var runtime = new CraftingRuntime(controller, controller.componentRuntime());
        var catalyst = new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                ItemStack.EMPTY, 1F, List.of(), DataComponentPredicateSet.EMPTY, 0F);
        runtime.start(recipe("installed_mixed_catalyst", 10, List.of(new EnergyRequirement(7L), catalyst)), 1L);
        var installed = runtime.ensureInstalledProgram();
        long afterStart = energy.getEnergyStorage().amount();
        var paid = runtime.ledger().state();
        try (Transaction transaction = Transaction.openRoot()) {
            items.itemStorage().extract(0, items.itemStorage().resource(0), 1L, transaction);
            transaction.commit();
        }
        LevelStub.setGameTime(controller.getLevel(), 1L);
        runtime.prepareInstalledTick(1L);
        assertThat(installed.tryAttempt(1L, 1L)).isTrue();
        assertThat(installed.advance()).isFalse();
        assertThat(runtime.tickCount()).isZero();
        assertThat(energy.getEnergyStorage().amount()).isEqualTo(afterStart);
        assertThat(runtime.ledger().state()).isEqualTo(paid);
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void cancellation_after_claim_rejects_both_advance_and_direct_resource_execution(MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        energy.getEnergyStorage().setAmount(100L);
        var controller = RuntimeTestFixtures.controller(MACHINE, energy);
        var runtime = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(runtime.start(recipe("installed_claim_cancel", 10, List.of(new EnergyRequirement(7L))), 1L)
                .isCrafting()).isTrue();
        var installed = runtime.ensureInstalledProgram();
        LevelStub.setGameTime(controller.getLevel(), 1L);
        if (mode != MachineWorkMode.SYNC) assertThat(runtime.prepareInstalledTick(1L)).isTrue();
        assertThat(installed.tryAttempt(1L, 1L)).isTrue();
        long before = energy.getEnergyStorage().amount();
        var paid = installed.ledger().state();
        runtime.cancelInstalledProgram();

        assertThat(installed.advance()).isFalse();
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(installed.executeContinuous(transaction)).isNotNull();
            transaction.commit();
        }
        assertThat(energy.getEnergyStorage().amount()).isEqualTo(before);
        assertThat(installed.ledger().state()).isEqualTo(paid);
        assertThat(runtime.tickCount()).isZero();
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void capability_change_after_claim_rejects_payment_even_when_preparation_was_current(MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        energy.getEnergyStorage().setAmount(100L);
        var controller = RuntimeTestFixtures.controller(MACHINE, energy);
        var runtime = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(runtime.start(recipe("installed_claim_version", 10, List.of(new EnergyRequirement(7L))), 1L)
                .isCrafting()).isTrue();
        var installed = runtime.ensureInstalledProgram();
        LevelStub.setGameTime(controller.getLevel(), 1L);
        if (mode != MachineWorkMode.SYNC) assertThat(runtime.prepareInstalledTick(1L)).isTrue();
        assertThat(installed.tryAttempt(1L, 1L)).isTrue();
        long before = energy.getEnergyStorage().amount();
        var paid = installed.ledger().state();
        controller.componentRuntime().replaceComponents(List.of());

        assertThat(installed.current()).isFalse();
        assertThat(installed.advance()).isFalse();
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(installed.executeContinuous(transaction)).isNotNull();
            transaction.commit();
        }
        assertThat(energy.getEnergyStorage().amount()).isEqualTo(before);
        assertThat(installed.ledger().state()).isEqualTo(paid);
        assertThat(runtime.tickCount()).isZero();
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void recipe_tick_invalidation_prevents_payment_after_the_callback(MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        for (boolean cancel : new boolean[] { true, false }) {
            var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
            energy.getEnergyStorage().setAmount(100L);
            var controller = RuntimeTestFixtures.controller(MACHINE, energy);
            AtomicReference<Runnable> callback = new AtomicReference<>(() -> { });
            AtomicBoolean invoked = new AtomicBoolean();
            Machine original = controller.structureSnapshot().configuredMachine();
            var behavior = RecipeBehavior.builder().recipeTick(context -> {
                invoked.set(true);
                callback.get().run();
            }).build();
            controller.setMachine(new Machine() {
                public Identifier registryName() { return original.registryName(); }
                public BlockArray pattern() { return original.pattern(); }
                public MachineControllerSpec controller() { return original.controller(); }
                public RecipeBehavior behavior() { return behavior; }
            });
            var runtime = new CraftingRuntime(controller, controller.componentRuntime());
            assertThat(runtime.start(recipe("installed_callback_invalidation_" + cancel, 10,
                    List.of(new EnergyRequirement(7L))), 1L).isCrafting()).isTrue();
            var installed = runtime.ensureInstalledProgram();
            long before = energy.getEnergyStorage().amount();
            var paid = installed.ledger().state();
            callback.set(cancel ? runtime::cancelInstalledProgram
                    : () -> controller.componentRuntime().replaceComponents(List.of()));
            LevelStub.setGameTime(controller.getLevel(), 1L);

            if (mode == MachineWorkMode.SYNC) {
                assertThat(installed.tryAttempt(1L, 1L)).isTrue();
            } else {
                assertThat(runtime.prepareInstalledTick(1L)).isFalse();
                assertThat(installed.tryAttempt(1L, 1L)).isFalse();
            }
            assertThat(installed.advance()).isFalse();
            assertThat(invoked).isTrue();
            assertThat(energy.getEnergyStorage().amount()).isEqualTo(before);
            assertThat(installed.ledger().state()).isEqualTo(paid);
            assertThat(runtime.tickCount()).isZero();
        }
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void commit_notification_invalidation_keeps_paid_resources_without_advancing_or_replaying(MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        for (boolean cancel : new boolean[] { true, false }) {
            AtomicReference<Runnable> notification = new AtomicReference<>(() -> { });
            var storage = storage(100L, 100L, 60L, () -> notification.get().run());
            var runtime = runtimeWithCapabilities(List.of(input(storage)));
            assertThat(runtime.start(recipe("installed_commit_invalidation_" + cancel, 10,
                    List.of(new EnergyRequirement(7L))), 1L).isCrafting()).isTrue();
            var installed = runtime.ensureInstalledProgram();
            LevelStub.setGameTime(runtime.controller().getLevel(), 1L);
            if (mode != MachineWorkMode.SYNC) assertThat(runtime.prepareInstalledTick(1L)).isTrue();
            assertThat(installed.tryAttempt(1L, 1L)).isTrue();
            long before = storage.amount();
            long paidAttempts = installed.ledger().state().paidAttempts();
            notification.set(cancel ? runtime::cancelInstalledProgram
                    : () -> runtime.controller().componentRuntime().replaceComponents(List.of()));

            assertThat(installed.advance()).isTrue();
            assertThat(storage.amount()).isEqualTo(before - 7L);
            assertThat(installed.ledger().state().paidAttempts()).isEqualTo(paidAttempts + 1L);
            assertThat(installed.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.RESOURCE_PAID);
            assertThat(runtime.tickCount()).isZero();
            assertThat(installed.current()).isFalse();
            assertThat(installed.advance()).isFalse();
            assertThat(storage.amount()).isEqualTo(before - 7L);
            assertThat(installed.ledger().state().paidAttempts()).isEqualTo(paidAttempts + 1L);
        }
    }

    @Test
    void same_tick_mode_switch_and_replacement_share_the_lane_payment_fence() {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.SYNC);
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        energy.getEnergyStorage().setAmount(100L);
        var controller = RuntimeTestFixtures.controller(MACHINE, energy);
        var runtime = new CraftingRuntime(controller, controller.componentRuntime());
        var recipe = recipe("installed_mode_switch", 10, List.of(new EnergyRequirement(7L)));
        runtime.start(recipe, 1L);
        var installed = runtime.ensureInstalledProgram();
        long afterStart = energy.getEnergyStorage().amount();
        LevelStub.setGameTime(controller.getLevel(), 1L);
        assertThat(installed.tryAttempt(1L, 1L)).isTrue();
        assertThat(installed.advance()).isTrue();
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.SEMI_SYNC);
        assertThat(installed.tryAttempt(2L, 1L)).isFalse();
        runtime.cancelInstalledProgram();
        var rebound = runtime.ensureInstalledProgram();
        assertThat(rebound.tryAttempt(2L, 1L)).isFalse();
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        assertThat(runtime.prepareInstalledTick(2L)).isFalse();
        assertThat(energy.getEnergyStorage().amount()).isEqualTo(afterStart - 7L);
        assertThat(runtime.tickCount()).isEqualTo(1);
    }

    @Test
    void restart_admits_the_current_single_recipe_maximum_for_increasing_and_decreasing_supply() {
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        energy.getEnergyStorage().setAmount(21L);
        var controller = RuntimeTestFixtures.controller(MACHINE, energy);
        makeParallel(controller);
        var runtime = new CraftingRuntime(controller, controller.componentRuntime());
        var recipe = RecipeTestSupport.create(MMCR.id("installed_restart_supply"), MACHINE, 1,
                List.of(new EnergyRequirement(7L)), List.of(), List.of(), 0, 1, false, true, List.of(), false, Set.of());
        long gameTime = 0L;
        for (long supply : new long[] {21L, 70L, 14L}) {
            energy.getEnergyStorage().setAmount(supply);
            assertThat(runtime.start(recipe, 16L).isCrafting()).isTrue();
            assertThat(runtime.parallelism()).isEqualTo(supply / 7L);
            runtime.ensureInstalledProgram();
            assertThat(energy.getEnergyStorage().amount()).isEqualTo(supply);
            LevelStub.setGameTime(controller.getLevel(), ++gameTime);
            runtime.tick();
            runtime.finish();
            assertThat(runtime.active()).isFalse();
            assertThat(energy.getEnergyStorage().amount()).isZero();
        }
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void mixed_live_requirements_reuse_static_bindings_without_controller_projection_capture(MachineWorkMode mode) throws Exception {
        ConfigTestSupport.setMachineWorkMode(mode);
        var energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        var items = RuntimeTestFixtures.itemInput(new BlockPos(2, 0, 0));
        energy.getEnergyStorage().setAmount(1000L);
        try (Transaction transaction = Transaction.openRoot()) {
            items.itemStorage().insert(0, ItemResource.of(new ItemStack(Items.IRON_INGOT)), 1L, transaction);
            transaction.commit();
        }
        var controller = RuntimeTestFixtures.controller(MACHINE, energy, items);
        var runtime = RuntimeTestFixtures.craftingRuntime(controller);
        var catalyst = new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                ItemStack.EMPTY, 1F, List.of(), DataComponentPredicateSet.EMPTY, 0F);
        assertThat(runtime.start(recipe("installed_mixed_static_capture", 200,
                List.of(new EnergyRequirement(7L), catalyst)), 1L).isCrafting()).isTrue();
        var installed = runtime.ensureInstalledProgram();
        int captures = controllerProjectionBuildCount(controller);
        int contexts = runtime.installedPlanningContextBuildCountForTesting();
        long afterStart = energy.getEnergyStorage().amount();
        for (int tick = 1; tick <= 50; tick++) {
            LevelStub.setGameTime(controller.getLevel(), tick);
            if (mode != MachineWorkMode.SYNC) runtime.prepareInstalledTick(tick);
            assertThat(installed.tryAttempt(tick, tick)).isTrue();
            assertThat(installed.advance()).isTrue();
            assertThat(runtime.tickCount()).isEqualTo(tick);
            assertThat(energy.getEnergyStorage().amount()).isEqualTo(afterStart - 7L * tick);
        }
        try (Transaction transaction = Transaction.openRoot()) {
            items.itemStorage().extract(0, items.itemStorage().resource(0), 1L, transaction);
            transaction.commit();
        }
        LevelStub.setGameTime(controller.getLevel(), 51L);
        if (mode != MachineWorkMode.SYNC) runtime.prepareInstalledTick(51L);
        assertThat(installed.tryAttempt(51L, 51L)).isTrue();
        assertThat(installed.advance()).isFalse();
        assertThat(runtime.tickCount()).isEqualTo(50);
        assertThat(energy.getEnergyStorage().amount()).isEqualTo(afterStart - 350L);
        assertThat(runtime.installedPlanningContextBuildCountForTesting()).isEqualTo(contexts);
        assertThat(controllerProjectionBuildCount(controller)).isEqualTo(captures);
        // Prove that the actual projection seam is live: an explicit public query publishes current progress.
        assertThat(controller.currentRuntimeSnapshot().crafting().tick()).isEqualTo(50);
        assertThat(controllerProjectionBuildCount(controller)).isGreaterThan(captures);
    }

    private static int controllerProjectionBuildCount(MachineControllerBlockEntity controller) throws Exception {
        Field field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
        field.setAccessible(true);
        Object runtime = field.get(controller);
        Method method = runtime.getClass().getDeclaredMethod("projectionBuildCountForTesting");
        method.setAccessible(true);
        return (int) method.invoke(runtime);
    }

    private static CraftingRuntime runtimeWithCapabilities(List<MachineCapability> capabilities) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MACHINE);
        TestCapabilityHost host = new TestCapabilityHost(RuntimeTestFixtures.energyInput(BlockPos.ZERO), capabilities);
        controller.componentRuntime().replaceComponents(List.of(new ProcessingComponent(null, host,
                BlockPos.ZERO, BlockPos.ZERO, (String) null)));
        RuntimeTestFixtures.republish(controller);
        return new CraftingRuntime(controller, controller.componentRuntime());
    }

    private static EnergyHatchCapability input(LongValueStorage storage) {
        return new EnergyHatchCapability(storage, IOType.INPUT);
    }

    /** A real block-entity host accepted by ProcessingComponent. @author howxu <dev@howxu.cn> */
    private static final class TestCapabilityHost extends BlockEntity implements CapabilityHost {
        private final CapabilitySnapshot capabilities;

        private TestCapabilityHost(BlockEntity blockEntity, List<MachineCapability> capabilities) {
            super(blockEntity.getType(), blockEntity.getBlockPos(), blockEntity.getBlockState());
            this.capabilities = new CapabilitySnapshot(capabilities);
        }

        @Override
        public CapabilitySnapshot capabilitySnapshot() { return capabilities; }
    }

    private static LongValueStorage storage(long capacity, long limit, long amount, Runnable notification) {
        var storage = new LongValueStorage(capacity, limit, notification);
        storage.setAmount(amount);
        return storage;
    }

    private static MachineRecipe recipe(String name, int duration, List<MachineRequirement> requirements) {
        return RecipeTestSupport.create(MMCR.id(name), MACHINE, duration, requirements, List.of());
    }

    private static void makeParallel(MachineControllerBlockEntity controller) {
        Machine original = controller.structureSnapshot().configuredMachine();
        controller.setMachine(new Machine() {
            public Identifier registryName() { return original.registryName(); }
            public BlockArray pattern() { return original.pattern(); }
            public MachineControllerSpec controller() { return original.controller(); }
            public long maxParallelism() { return 16L; }
            public boolean parallelizable() { return true; }
        });
        var parallel = new ParallelControllerBlockEntity(ParallelTier.PLUS, new BlockPos(3, 0, 0),
                ModBlocks.BLOCKS.get(ParallelTier.PLUS.idSuffix()).get().defaultBlockState());
        var members = new ArrayList<>(controller.componentRuntime().components());
        members.add(new ProcessingComponent(null, parallel, parallel.getBlockPos(), parallel.getBlockPos(), (String) null));
        controller.componentRuntime().replaceComponents(members);
        RuntimeTestFixtures.republish(controller);
    }

    private static CompoundTag save(CraftingRuntime runtime) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, LOOKUP);
        runtime.save(output);
        return output.buildResult();
    }
}
