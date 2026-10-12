package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.ConfigTestSupport;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.test.TestBootstrap;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.fml.config.IConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Installed mixed-resource attempts delegate probability decisions to the real built-in handlers.
 * @author howxu <dev@howxu.cn>
 */
class InstalledProbabilityExecutionTest {
    private static final ResourceLocation MACHINE = MMCR.id("test_cube");
    private ServerThreadTestScope serverThreadScope;

    @BeforeAll
    static void bootstrap() throws Exception {
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
        } finally {
            if (serverThreadScope != null) serverThreadScope.close();
        }
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void each_formal_mixed_attempt_plans_probability_once_and_duplicates_do_not_sample(MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        try (var fixture = new Fixture(0.5F)) {
            assertThat(fixture.itemHandler.attempts).isEmpty();
            for (int round = 1; round <= 2; round++) {
                fixture.prepare(mode, round);
                assertThat(fixture.itemHandler.attempts).hasSize(round - 1);
                assertThat(fixture.installed.tryAttempt(round, round)).isTrue();
                assertThat(fixture.installed.advance()).isTrue();
                fixture.assertSingleDecision(round);
                assertThat(fixture.itemHandler.storage.amount(0)).isEqualTo(16L
                        - fixture.itemHandler.attempts.stream().mapToLong(attempt -> attempt.committed.getFirst()).sum());
                assertThat(fixture.energy.energyStorage().getAmountAsLong()).isEqualTo(1_000L - 7L * round);
                assertThat(fixture.runtime.tickCount()).isEqualTo(round);
                assertThat(fixture.runtime.ledger().state().paidAttempts()).isEqualTo(round);
                var paid = fixture.runtime.ledger().state();
                long items = fixture.itemHandler.storage.amount(0);

                assertThat(fixture.installed.tryAttempt(round, round)).isFalse();
                assertThat(fixture.installed.tryAttempt(round + 100L, round)).isFalse();
                assertThat(fixture.installed.tryAttempt(round, round + 100L)).isFalse();
                assertThat(fixture.runtime.ensureInstalledProgram()).isSameAs(fixture.installed);
                assertThat(fixture.itemHandler.attempts).hasSize(round);
                assertThat(fixture.itemHandler.storage.amount(0)).isEqualTo(items);
                assertThat(fixture.runtime.ledger().state()).isEqualTo(paid);
                assertThat(fixture.energy.energyStorage().getAmountAsLong()).isEqualTo(1_000L - 7L * round);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void rejected_last_native_operation_retains_payment_and_never_samples_or_pays_again(MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        // The certain-consumption control proves real mutations precede rejection even if the partial draw is zero.
        for (float chance : new float[] { 0.5F, 1F }) {
            try (var fixture = new Fixture(chance)) {
                fixture.itemHandler.rejectOperation = true;
                fixture.prepare(mode, 1L);
                assertThat(fixture.installed.tryAttempt(1L, 1L)).isTrue();
                assertThat(fixture.installed.advance()).isFalse();
                fixture.assertSingleDecision(1);
                assertThat(fixture.itemHandler.attempts.getFirst().energyAtCommit).isEqualTo(993L);
                assertThat(fixture.energy.energyStorage().getAmountAsLong()).isEqualTo(993L);
                assertThat(fixture.itemHandler.storage.amount(0)).isEqualTo(16L
                        - fixture.itemHandler.attempts.getFirst().committed.getFirst());
                assertThat(fixture.runtime.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.ERROR_PAUSED);
                assertThat(fixture.runtime.tickCount()).isZero();
                assertThat(fixture.runtime.failure().reason()).isEqualTo(BuiltinFailureReasons.COMMIT_LOST_INPUT);
                if (chance == 1F) fixture.assertCertainConsumption();
                assertThat(fixture.installed.tryAttempt(2L, 1L)).isFalse();
                fixture.runtime.resume();
                LevelStub.setGameTime(fixture.controller.getLevel(), 2L);
                fixture.runtime.tick();
                assertThat(fixture.energy.energyStorage().getAmountAsLong()).isEqualTo(993L);
                assertThat(fixture.itemHandler.attempts).hasSize(1);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void native_probability_payment_requires_explicit_settlement_and_cannot_be_replayed(MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        for (float chance : new float[] { 0.5F, 1F }) {
            try (var fixture = new Fixture(chance)) {
                fixture.prepare(mode, 1L);
                assertThat(fixture.installed.tryAttempt(1L, 1L)).isTrue();
                var before = fixture.runtime.ledger().state();
                {
                    assertThat(fixture.installed.executeContinuous()).isNull();
                    fixture.runtime.ledger().markResourcePaid(0, false);
                    fixture.assertSingleDecision(1);
                    assertThat(fixture.energy.energyStorage().getAmountAsLong()).isEqualTo(993L);
                    assertThat(fixture.runtime.ledger().state().paidAttempts()).isEqualTo(before.paidAttempts() + 1L);
                    assertThat(fixture.runtime.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.RESOURCE_PAID);
                    if (chance == 1F) fixture.assertCertainConsumption();
                }
                fixture.runtime.ledger().pauseAfterCommitFailure();
                fixture.runtime.resume();
                fixture.runtime.tick();
                assertThat(fixture.energy.energyStorage().getAmountAsLong()).isEqualTo(993L);
                assertThat(fixture.itemHandler.storage.amount(0)).isEqualTo(16L
                        - fixture.itemHandler.attempts.getFirst().committed.getFirst());
                assertThat(fixture.itemHandler.attempts).hasSize(1);
                assertThat(fixture.runtime.tickCount()).isZero();
            }
        }
    }

    /** Owns real attached ports/runtime and a scoped compatibility registration. @author howxu <dev@howxu.cn> */
    private static final class Fixture implements AutoCloseable {
        private final RequirementHandlerRegistry.TestScope scope = RequirementHandlerRegistry.openTestScope();
        private final EnergyInputHatchBlockEntity energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        private final MachineControllerBlockEntity controller;
        private final CraftingRuntime runtime;
        private final InstalledRecipeProgram installed;
        private final RecordingHandler<ItemRequirement> itemHandler;

        private Fixture(float chance) {
            var items = RuntimeTestFixtures.itemInput(new BlockPos(2, 0, 0));
            energy.energyStorage().setAmount(1_000L);
            assertThat(items.itemHandler().insertItem(0, new ItemStack(Items.IRON_INGOT, 16), false).isEmpty()).isTrue();
            controller = RuntimeTestFixtures.controller(MACHINE, energy, items);
            runtime = RuntimeTestFixtures.craftingRuntime(controller);
            itemHandler = new RecordingHandler<>(new ItemRequirementHandler(), new ItemRequirement(
                    RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY,
                    1F, List.of(), DataComponentPredicateSet.EMPTY, chance), items.itemHandler(), energy);
            var recipe = RecipeTestSupport.create(MMCR.id("installed_probability"), MACHINE, 20,
                    List.of(new EnergyRequirement(7L), wrapped("item", itemHandler)), List.of());
            // Restore bypasses start-stage I/O, isolating formal installed attempts from admission sampling.
            var active = new ActiveMachineRecipe(recipe, 1L);
            active.setInputConsumptionPlan(new ActiveMachineRecipe.InputConsumptionPlan(List.of(0, 0)));
            var snapshot = controller.currentRuntimeSnapshot();
            runtime.restore(active, null, snapshot.structure().version(), snapshot.capabilityVersion(),
                    snapshot.modifierVersion(), snapshot.stateVersion());
            assertThat(runtime.active()).isTrue();
            installed = runtime.ensureInstalledProgram();
            assertThat(installed).isNotNull();
        }

        private void prepare(MachineWorkMode mode, long round) {
            LevelStub.setGameTime(controller.getLevel(), round);
            if (mode != MachineWorkMode.SYNC) assertThat(runtime.prepareInstalledTick(round)).isTrue();
        }

        private void assertSingleDecision(int attempts) {
            assertThat(itemHandler.attempts).hasSize(attempts);
            Attempt attempt = itemHandler.attempts.getLast();
            assertThat(attempt.reserved).hasSize(1);
            assertThat(attempt.materialized).containsExactlyElementsOf(attempt.reserved);
            assertThat(attempt.committed).containsExactlyElementsOf(attempt.reserved);
        }

        private void assertCertainConsumption() {
            assertThat(itemHandler.attempts.getFirst().committed).containsExactly(1L);
        }

        @Override public void close() { scope.close(); }
    }

    private static WrappedRequirement wrapped(String name, RecordingHandler<?> handler) {
        RequirementType<WrappedRequirement> type = new RequirementType<>() {
            @Override public ResourceLocation id() { return MMCR.id("installed_probability_" + name); }
            @Override public Set<ResourceLocation> capabilityIds() { return handler.requirement.type().capabilityIds(); }
            @Override public MapCodec<WrappedRequirement> codec() { return MapCodec.unit(() -> new WrappedRequirement(this)); }
            @Override public RequirementHandler<WrappedRequirement> handler() { return handler; }
            @Override public WrappedRequirement copy(WrappedRequirement value) { return new WrappedRequirement(this); }
        };
        RequirementHandlerRegistry.register(type);
        return new WrappedRequirement(type);
    }

    /** Keeps built-in input probability active in the continuous compatibility stage. @author howxu <dev@howxu.cn> */
    private record WrappedRequirement(RequirementType<WrappedRequirement> type) implements MachineRequirement {
        @Override public RecipeModifier.IOType io() { return RecipeModifier.IOType.INPUT; }
    }

    /** Captures one real handler plan and its decision across reservation/materialization/commit. @author howxu <dev@howxu.cn> */
    private static final class Attempt {
        private final List<Long> reserved = new ArrayList<>();
        private final List<Long> materialized = new ArrayList<>();
        private final List<Long> committed = new ArrayList<>();
        private long energyAtCommit;
    }

    /** Delegates sampling and all resource operations; only the final rejection is controlled. @author howxu <dev@howxu.cn> */
    private static final class RecordingHandler<R extends MachineRequirement> implements RequirementHandler<WrappedRequirement> {
        private final RequirementHandler<R> delegate;
        private final R requirement;
        private final LongItemStorage storage;
        private final EnergyInputHatchBlockEntity energy;
        private final List<Attempt> attempts = new ArrayList<>();
        private boolean rejectOperation;

        private RecordingHandler(RequirementHandler<R> delegate, R requirement, LongItemStorage storage,
                                 EnergyInputHatchBlockEntity energy) {
            this.delegate = delegate;
            this.requirement = requirement;
            this.storage = storage;
            this.energy = energy;
        }

        @Override
        public RequirementPlan plan(WrappedRequirement value, List<MachineCapability> capabilities, PlanningContext context) {
            Attempt attempt = new Attempt();
            attempts.add(attempt);
            RequirementPlan real = delegate.plan(requirement, capabilities, context);
            assertThat(real.successful()).isTrue();
            assertThat(real.operationFactory()).isNotNull();
            assertThat(real.reservationFactory()).isNotNull();
            return new RequirementPlan(real.requirementIndex(), real.maxParallelism(), real.operations(), real.failure(),
                    real.outputSimulation(), (parallelism, reservations) -> {
                        long before = reservations.itemAmount(storage, 0);
                        var result = real.operationFactory().create(parallelism, reservations);
                        attempt.materialized.add(before - reservations.itemAmount(storage, 0));
                        long initial = storage.amount(0);
                        List<CapabilityOperation> operations = new ArrayList<>(result.operations());
                        operations.add(() -> {
                            attempt.committed.add(initial - storage.amount(0));
                            attempt.energyAtCommit = energy.energyStorage().getAmountAsLong();
                            return rejectOperation ? CapabilityResult.failure(RequirementHandlerSupport.blocked(
                                    requirement, context, BuiltinFailureReasons.COMMIT_LOST_INPUT)) : CapabilityResult.successful();
                        });
                        return new RequirementPlan.OperationPlan(operations, result.failure(), result.outputSimulation());
                    }, (parallelism, reservations) -> {
                        long before = reservations.itemAmount(storage, 0);
                        var failure = real.reservationFactory().reserve(parallelism, reservations);
                        attempt.reserved.add(before - reservations.itemAmount(storage, 0));
                        return failure;
                    }, real.preparedParallelism());
        }
    }
}
