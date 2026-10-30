package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.RecipeEnergyPrefetchFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.CapabilityRequest;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.compat.appliedflux.loaded.capability.FluxEnergyInputCapability;
import cn.howxu.mmcr.compat.appliedflux.loaded.storage.FluxEnergyBuffer;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Flux transaction boundaries exercised through runtime payment, persistence and installed grants.
 * @author howxu <dev@howxu.cn>
 */
class FluxRuntimeBoundaryTest {
    private static final Identifier MACHINE = MMCR.id("test_cube");
    private static final HolderLookup.Provider LOOKUP = HolderLookup.Provider.create(Stream.empty());
    private ServerThreadTestScope serverThreadScope;

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
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.SYNC);
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            RecipeRegistry.clearForTesting();
            ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        } finally {
            if (serverThreadScope != null) serverThreadScope.close();
        }
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void requirement_classification_is_reused_across_real_prefetch_debits(MachineWorkMode mode) throws Exception {
        ConfigTestSupport.setMachineWorkMode(mode);
        Fixture fixture = new Fixture(4);
        assertThat(fixture.start(false)).isTrue();
        assertThat(fixture.runtime.ensureInstalledProgram()).isNotNull();
        List<?> requirements = tickRequirements(fixture.runtime);
        assertThat(requirements).hasSize(1);
        for (int tick = 1; tick <= 3; tick++) {
            assertThat(fixture.attempt(mode, tick).advance()).isTrue();
            assertThat(fixture.runtime.tickCount()).isEqualTo(tick);
            assertThat(tickRequirements(fixture.runtime)).isSameAs(requirements);
            assertThat(fixture.first.buffer.reserved() + fixture.second.buffer.reserved()).isEqualTo(5L * (4 - tick));
        }
        fixture.runtime.invalidate();
    }

    @Test
    void requirement_classification_rebuilds_when_prefetch_ends_even_with_the_same_requirement_source() throws Exception {
        Fixture fixture = new Fixture(4);
        assertThat(fixture.start(false)).isTrue();
        List<?> prefetched = tickRequirements(fixture.runtime);
        Method release = CraftingRuntime.class.getDeclaredMethod("releaseActivePrefetches");
        release.setAccessible(true);
        release.invoke(fixture.runtime);
        List<?> ordinary = tickRequirements(fixture.runtime);
        assertThat(ordinary).isNotSameAs(prefetched).hasSize(3);
        assertThat(tickRequirements(fixture.runtime)).isSameAs(ordinary);
        assertThat(fixture.first.buffer.reserved() + fixture.second.buffer.reserved()).isZero();
        assertThat(fixture.first.buffer.amount() + fixture.second.buffer.amount()).isEqualTo(20L);
        fixture.runtime.invalidate();
        assertThat(tickRequirements(fixture.runtime)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void single_prefetch_publishes_payment_before_notification_and_finishes_without_reextracting(MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        Fixture fixture = new Fixture(3);
        ObservedFlux energy = new ObservedFlux("single", 15L);
        startSinglePrefetch(fixture, energy, 3);
        AtomicReference<CompoundTag> checkpoint = new AtomicReference<>();
        energy.notification.set(() -> {
            checkpoint.set(save(fixture.runtime));
            assertThat(allocations(checkpoint.get())).containsEntry("single", 10L);
            assertThat(fixture.runtime.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.RESOURCE_PAID);
        });
        for (int tick = 1; tick <= 3; tick++) {
            assertThat(fixture.attempt(mode, tick).advance()).isTrue();
            assertThat(energy.buffer.amount()).isEqualTo(5L * (3 - tick));
            assertThat(energy.buffer.reserved()).isEqualTo(energy.buffer.amount());
        }
        assertThat(checkpoint.get()).isNotNull();
        assertThat(energy.consumeCalls).isEqualTo(3);
        assertThat(energy.networkCommits).isEqualTo(1);
        assertThat(fixture.output.itemStorage().amount(0)).isEqualTo(1L);
        assertThat(fixture.runtime.ledger().state().outputPaid()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"false,SYNC", "true,SYNC", "false,SEMI_SYNC", "true,SEMI_SYNC", "false,ASYNC", "true,ASYNC"})
    void single_prefetch_rejected_or_partial_debit_rolls_back_and_cannot_replay(boolean partial, MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        Fixture fixture = new Fixture(3);
        ObservedFlux energy = new ObservedFlux("single", 15L);
        startSinglePrefetch(fixture, energy, 3);
        if (partial) energy.consumeLimit = 2L;
        else energy.rejectConsumption = true;

        assertThat(fixture.attempt(mode).advance()).isFalse();

        assertThat(fixture.runtime.tickCount()).isZero();
        assertThat(allocations(save(fixture.runtime))).containsEntry("single", 15L);
        assertThat(energy.buffer.amount()).isEqualTo(15L);
        assertThat(fixture.runtime.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.ERROR_PAUSED);
        CraftingRuntime restored = new CraftingRuntime(fixture.controller, fixture.controller.componentRuntime());
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, LOOKUP, save(fixture.runtime)), null);
        restored.tick();
        restored.finish();
        assertThat(restored.tickCount()).isZero();
        assertThat(energy.consumeCalls).isEqualTo(1);
        assertThat(energy.buffer.amount()).isEqualTo(15L);
        restored.invalidate();
        assertThat(energy.buffer.reserved()).isZero();
        assertThat(energy.buffer.idleExcess()).isEqualTo(15L);
    }

    @ParameterizedTest
    @CsvSource({"cancel,SYNC", "cancel,SEMI_SYNC", "cancel,ASYNC", "throw,SYNC", "throw,SEMI_SYNC", "throw,ASYNC",
            "cancel_throw,SYNC", "cancel_throw,SEMI_SYNC", "cancel_throw,ASYNC"})
    void single_prefetch_notification_cannot_grant_progress_after_cancellation_or_throw(String action, MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        Fixture fixture = new Fixture(3);
        ObservedFlux energy = new ObservedFlux("single", 15L);
        startSinglePrefetch(fixture, energy, 3);
        energy.notification.set(() -> {
            assertThat(allocations(save(fixture.runtime))).containsEntry("single", 10L);
            if (action.startsWith("cancel")) fixture.runtime.invalidate();
            if (action.endsWith("throw")) throw new IllegalStateException("single allocation notification");
        });
        assertThat(fixture.attempt(mode).advance()).isFalse();
        assertThat(fixture.runtime.tickCount()).isZero();
        assertThat(energy.consumeCalls).isEqualTo(1);
        assertThat(energy.buffer.amount()).isEqualTo(10L);
        assertThat(fixture.output.itemStorage().amount(0)).isZero();
        if (action.startsWith("cancel")) {
            assertThat(fixture.runtime.active()).isFalse();
            assertThat(energy.buffer.reserved()).isZero();
            assertThat(energy.releaseCalls).isEqualTo(1);
        } else {
            assertThat(fixture.runtime.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.ERROR_PAUSED);
            assertThat(allocations(save(fixture.runtime))).containsEntry("single", 10L);
        }
        fixture.runtime.invalidate();
        assertThat(energy.releaseCalls).isEqualTo(1);
    }

    private static List<?> tickRequirements(CraftingRuntime runtime) throws Exception {
        Method method = CraftingRuntime.class.getDeclaredMethod("perTickRequirements");
        method.setAccessible(true);
        return (List<?>) method.invoke(runtime);
    }

    private static void startSinglePrefetch(Fixture fixture, ObservedFlux energy, int duration) {
        List<ProcessingComponent> components = new ArrayList<>(fixture.controller.componentRuntime().components());
        components.removeIf(component -> component.getContainer() instanceof TestCapabilityHost);
        components.add(new ProcessingComponent(null, new TestCapabilityHost(List.of(energy)),
                BlockPos.ZERO, BlockPos.ZERO, (String) null));
        fixture.controller.componentRuntime().replaceComponents(components);
        RuntimeTestFixtures.republish(fixture.controller);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("single_prefetch_boundary"), MACHINE, duration,
                List.of(new EnergyRequirement(5L)), List.of(new ItemStack(Items.IRON_NUGGET)));
        RecipeRegistry.registerStatic(recipe);
        fixture.runtime.start(recipe, 1L);
        assertThat(fixture.runtime.active()).isTrue();
        assertThat(allocations(save(fixture.runtime))).containsExactly(Map.entry("single", 5L * duration));
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void start_notification_cancel_releases_only_the_old_paid_prepared_once(boolean pattern, boolean throwAfterCancel) {
        Fixture fixture = new Fixture(2);
        fixture.first.notification.set(() -> {
            fixture.runtime.invalidate();
            if (throwAfterCancel) throw new IllegalStateException("cancelled start notification");
        });

        assertThat(fixture.start(pattern)).isFalse();
        assertThat(fixture.runtime.active()).isFalse();
        fixture.assertReleased(4L, 6L);
        assertThat(fixture.first.network.amount()).isEqualTo(96L);
        assertThat(fixture.second.network.amount()).isEqualTo(94L);
        assertThat(fixture.first.networkCommits).isEqualTo(1);
        assertThat(fixture.second.networkCommits).isEqualTo(1);
        assertThat(save(fixture.runtime).getBooleanOr("active", true)).isFalse();
        fixture.runtime.invalidate();
        fixture.assertReleased(4L, 6L);
        assertThat(fixture.first.releaseCalls).isEqualTo(1);
        assertThat(fixture.second.releaseCalls).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void cancelled_start_notification_can_start_a_replacement_without_old_cleanup_touching_it(
            boolean pattern, boolean throwAfterReplacement) {
        Fixture fixture = new Fixture(2);
        AtomicReference<RecipeCommitLedger> replacementPayment = new AtomicReference<>();
        fixture.first.notification.set(() -> {
            fixture.runtime.invalidate();
            assertThat(fixture.runtime.start(fixture.recipe, 1L).isCrafting()).isTrue();
            replacementPayment.set(fixture.runtime.ledger());
            if (throwAfterReplacement) throw new IllegalStateException("old start notification");
        });

        boolean started = fixture.start(pattern);
        if (pattern) assertThat(started).isFalse();
        assertThat(fixture.runtime.active()).isTrue();
        assertThat(fixture.runtime.ledger()).isSameAs(replacementPayment.get());
        assertThat(fixture.runtime.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.READY);
        assertThat(fixture.first.buffer.reserved()).isEqualTo(4L);
        assertThat(fixture.second.buffer.reserved()).isEqualTo(6L);
        assertThat(fixture.first.buffer.idleExcess()).isEqualTo(4L);
        assertThat(fixture.second.buffer.idleExcess()).isEqualTo(6L);
        assertThat(allocations(save(fixture.runtime))).isEqualTo(Map.of("first", 4L, "second", 6L));
        assertThat(fixture.first.releaseCalls).isEqualTo(1);
        assertThat(fixture.second.releaseCalls).isEqualTo(1);
        assertThat(fixture.first.networkCommits).isEqualTo(2);
        assertThat(fixture.second.networkCommits).isEqualTo(2);
        fixture.runtime.invalidate();
        fixture.assertReleased(8L, 12L);
        assertThat(fixture.first.releaseCalls).isEqualTo(2);
        assertThat(fixture.second.releaseCalls).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void valid_start_notification_throw_retains_real_reservations_in_error_pause(boolean pattern) {
        Fixture fixture = new Fixture(2);
        fixture.first.notification.set(() -> { throw new IllegalStateException("valid start notification"); });
        assertThat(fixture.start(pattern)).isTrue();
        assertThat(fixture.runtime.active()).isTrue();
        assertThat(fixture.runtime.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.ERROR_PAUSED);
        assertThat(fixture.first.buffer.reserved()).isEqualTo(4L);
        assertThat(fixture.second.buffer.reserved()).isEqualTo(6L);
        assertThat(fixture.first.releaseCalls).isZero();
        assertThat(fixture.second.releaseCalls).isZero();
        fixture.assertNoReplay(fixture.runtime);
        fixture.runtime.invalidate();
        fixture.assertReleased(4L, 6L);
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void caller_owned_pattern_commit_cannot_activate_the_cancelled_prepared_over_a_replacement(boolean replace) {
        Fixture fixture = new Fixture(2);
        var prepared = fixture.runtime.preparePatternStart(fixture.recipe, 1L, List.of());
        assertThat(prepared).isNotNull();
        assertThat(fixture.runtime.reservePatternStart()).isTrue();
        fixture.first.notification.set(() -> {
            fixture.runtime.invalidate();
            if (replace) assertThat(fixture.start(false)).isTrue();
        });
        var reservation = PatternStartReservation.reserved(fixture.recipe, "base", fixture.runtime,
                prepared, fixture.runtime::releasePatternStart);
        reservation.commit();
        // The external reservation's status contract is separate; assert resource/execution ownership here.
        assertThat(fixture.runtime.active()).isEqualTo(replace);
        assertThat(fixture.first.releaseCalls).isEqualTo(1);
        assertThat(fixture.second.releaseCalls).isEqualTo(1);
        if (replace) {
            assertThat(fixture.runtime.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.READY);
            assertThat(fixture.first.buffer.reserved()).isEqualTo(4L);
            assertThat(fixture.second.buffer.reserved()).isEqualTo(6L);
        } else {
            fixture.assertReleased(4L, 6L);
        }
    }

    @ParameterizedTest
    @CsvSource({"1,SYNC", "2,SYNC", "1,SEMI_SYNC", "2,SEMI_SYNC", "1,ASYNC", "2,ASYNC"})
    void successful_segments_grant_once_and_finish_without_reextracting_or_releasing_consumed_energy(
            int duration, MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        Fixture fixture = new Fixture(duration);
        assertThat(fixture.start(false)).isTrue();
        for (int tick = 1; tick <= duration; tick++) {
            var installed = fixture.attempt(mode, tick);
            assertThat(installed.advance()).isTrue();
            assertThat(installed.tryAttempt(tick, tick)).isFalse();
            assertThat(fixture.ordinary.amount()).isEqualTo(7L * tick);
            assertThat(fixture.first.buffer.reserved() + fixture.second.buffer.reserved())
                    .isEqualTo(5L * (duration - tick));
            if (tick < duration) {
                assertThat(fixture.runtime.tickCount()).isEqualTo(tick);
                assertThat(allocations(save(fixture.runtime))).isEqualTo(Map.of("first", 0L, "second", 5L));
            }
        }
        assertThat(fixture.runtime.active()).isFalse();
        assertThat(fixture.runtime.ledger().state().outputPaid()).isTrue();
        assertThat(fixture.runtime.ledger().state().paidAttempts()).isEqualTo(1L + duration);
        assertThat(fixture.output.itemStorage().amount(0)).isEqualTo(1L);
        fixture.assertReleased(0L, 0L);
        assertThat(fixture.first.releaseCalls).isZero();
        assertThat(fixture.second.releaseCalls).isZero();
        assertThat(fixture.first.networkCommits).isEqualTo(1);
        assertThat(fixture.second.networkCommits).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"1,SYNC", "2,SYNC", "1,SEMI_SYNC", "2,SEMI_SYNC", "1,ASYNC", "2,ASYNC"})
    void ordinary_notification_save_throw_keeps_reservation_unconsumed_and_blocks_grant_and_finish(
            int duration, MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        Fixture fixture = new Fixture(duration);
        assertThat(fixture.start(false)).isTrue();
        AtomicReference<CompoundTag> saved = new AtomicReference<>();
        fixture.ordinaryNotification.set(() -> {
            saved.set(fixture.saveAll());
            throw new IllegalStateException("ordinary notification");
        });
        InstalledRecipeProgram installed = fixture.attempt(mode);
        assertThat(installed.advance()).isFalse();
        fixture.assertPaidWithoutGrant(duration == 1);
        assertThat(fixture.ordinary.amount()).isEqualTo(7L);
        fixture.assertReservations(2L * duration, 3L * duration);
        assertThat(fixture.first.consumeCalls).isZero();
        assertThat(fixture.second.consumeCalls).isZero();
        assertThat(saved.get().getCompoundOrEmpty("runtime").getCompoundOrEmpty("execution_commit")
                .getStringOr("phase", "")).isEqualTo("RESOURCE_PAID");
        fixture.assertNoReplay(fixture.runtime);
        CraftingRuntime restored = fixture.restore(saved.get());
        fixture.assertNoReplay(restored);
        assertThat(installed.advance()).isFalse();
        restored.invalidate();
        fixture.assertReleased(2L * duration, 3L * duration);
    }

    @ParameterizedTest
    @CsvSource({"1,SYNC", "2,SYNC", "1,SEMI_SYNC", "2,SEMI_SYNC", "1,ASYNC", "2,ASYNC"})
    void reservation_rejection_rolls_back_all_keys_but_retains_ordinary_payment(int duration, MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        Fixture fixture = new Fixture(duration);
        assertThat(fixture.start(false)).isTrue();
        fixture.second.rejectConsumption = true;
        var installed = fixture.attempt(mode);
        assertThat(installed.advance()).isFalse();
        fixture.assertPaidWithoutGrant(duration == 1);
        assertThat(fixture.ordinary.amount()).isEqualTo(7L);
        fixture.assertReservations(2L * duration, 3L * duration);
        assertThat(fixture.first.consumeCalls).isEqualTo(1);
        assertThat(fixture.second.consumeCalls).isEqualTo(1);
        fixture.second.rejectConsumption = false;
        fixture.assertNoReplay(fixture.runtime);
        CraftingRuntime restored = fixture.restore(fixture.saveAll());
        fixture.assertNoReplay(restored);
        assertThat(installed.advance()).isFalse();
        restored.invalidate();
        fixture.assertReleased(2L * duration, 3L * duration);
    }

    @ParameterizedTest
    @CsvSource({"1,SYNC", "2,SYNC", "1,SEMI_SYNC", "2,SEMI_SYNC", "1,ASYNC", "2,ASYNC"})
    void reservation_notification_save_throw_persists_consumed_keys_without_grant_or_replay(
            int duration, MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        Fixture fixture = new Fixture(duration);
        assertThat(fixture.start(false)).isTrue();
        AtomicReference<CompoundTag> saved = new AtomicReference<>();
        fixture.second.notification.set(() -> {
            saved.set(fixture.saveAll());
            throw new IllegalStateException("reservation notification");
        });
        var compilation = fixture.runtime.captureProgramCompilation();
        var installed = fixture.attempt(mode);
        assertThat(installed.advance()).isFalse();
        fixture.assertPaidWithoutGrant(duration == 1);
        long secondRemaining = 5L * (duration - 1L);
        assertThat(fixture.ordinary.amount()).isEqualTo(7L);
        fixture.assertReservations(0L, secondRemaining);
        assertThat(fixture.first.consumeCalls).isEqualTo(1);
        assertThat(fixture.second.consumeCalls).isEqualTo(1);
        CompoundTag runtimeSave = saved.get().getCompoundOrEmpty("runtime");
        assertThat(allocations(runtimeSave)).isEqualTo(Map.of("first", 0L, "second", secondRemaining));
        assertThat(runtimeSave.getCompoundOrEmpty("execution_commit").getStringOr("phase", ""))
                .isEqualTo("RESOURCE_PAID");
        fixture.assertNoReplay(fixture.runtime);
        CraftingRuntime restored = fixture.restore(saved.get());
        fixture.assertNoReplay(restored);
        assertThat(installed.advance()).isFalse();
        assertThat(restored.installCompiledProgram(compilation.identity(), compilation.compile())).isNull();
        restored.invalidate();
        fixture.assertReleased(0L, secondRemaining);
        assertThat(fixture.first.releaseCalls).isZero();
        assertThat(fixture.second.releaseCalls).isEqualTo(duration == 1 ? 0 : 1);
    }

    private static CompoundTag save(CraftingRuntime runtime) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, LOOKUP);
        runtime.save(output);
        return output.buildResult();
    }

    private static Map<String, Long> allocations(CompoundTag saved) {
        Map<String, Long> result = new LinkedHashMap<>();
        var entries = saved.getCompoundOrEmpty("recipe").getCompoundOrEmpty("data")
                .getListOrEmpty("prefetched_energy_allocations");
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag allocation = entries.getCompoundOrEmpty(index);
            result.put(allocation.getStringOr("key", ""), allocation.getLongOr("amount", -1L));
        }
        return result;
    }

    /** Typed hosts keep real capability facets and storage identities at the runtime boundary.
     * @author howxu <dev@howxu.cn>
     */
    private static final class Fixture {
        private final AtomicReference<Runnable> ordinaryNotification = new AtomicReference<>();
        private final LongValueStorage ordinary = new LongValueStorage(100L, 100L,
                () -> notifyOnce(ordinaryNotification));
        private final ObservedFlux first;
        private final ObservedFlux second;
        private final ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(3, 0, 0));
        private final MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MACHINE, output);
        private final CraftingRuntime runtime;
        private final MachineRecipe recipe;

        private Fixture(int duration) {
            first = new ObservedFlux("first", 2L * duration);
            second = new ObservedFlux("second", 3L * duration);
            var components = new ArrayList<>(controller.componentRuntime().components());
            components.add(new ProcessingComponent(null, new TestCapabilityHost(List.of(first, second,
                    new EnergyHatchCapability(ordinary, IOType.OUTPUT))), BlockPos.ZERO, BlockPos.ZERO, (String) null));
            controller.componentRuntime().replaceComponents(components);
            RuntimeTestFixtures.republish(controller);
            runtime = RuntimeTestFixtures.craftingRuntime(controller);
            recipe = RecipeTestSupport.create(MMCR.id("flux_boundary_" + duration), MACHINE, duration,
                    List.of(new EnergyRequirement(2L), new EnergyRequirement(3L),
                            new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 7L)),
                    List.of(new ItemStack(Items.IRON_NUGGET)));
            RecipeRegistry.registerStatic(recipe);
        }

        private boolean start(boolean pattern) {
            if (!pattern) {
                runtime.start(recipe, 1L);
                return runtime.active();
            }
            var prepared = runtime.preparePatternStart(recipe, 1L, List.of());
            assertThat(prepared).isNotNull();
            assertThat(runtime.reservePatternStart()).isTrue();
            return runtime.commitPatternStart(prepared);
        }

        private InstalledRecipeProgram attempt(MachineWorkMode mode) {
            return attempt(mode, 1L);
        }

        private InstalledRecipeProgram attempt(MachineWorkMode mode, long round) {
            LevelStub.setGameTime(controller.getLevel(), round);
            var installed = runtime.ensureInstalledProgram();
            assertThat(installed).isNotNull();
            if (mode != MachineWorkMode.SYNC) {
                assertThat(runtime.prepareInstalledTick(round)).isTrue();
            }
            assertThat(installed.tryAttempt(round, round)).isTrue();
            return installed;
        }

        private void assertPaidWithoutGrant(boolean finalTick) {
            assertThat(runtime.tickCount()).isZero();
            assertThat(runtime.finishPending()).isFalse();
            assertThat(runtime.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.ERROR_PAUSED);
            assertThat(runtime.ledger().state().paidAttempts()).isEqualTo(2L);
            assertThat(runtime.ledger().state().finalTickPaid()).isEqualTo(finalTick);
            assertThat(runtime.ledger().state().outputPaid()).isFalse();
        }

        private void assertReservations(long firstAmount, long secondAmount) {
            assertThat(first.buffer.amount()).isEqualTo(firstAmount);
            assertThat(first.buffer.reserved()).isEqualTo(firstAmount);
            assertThat(second.buffer.amount()).isEqualTo(secondAmount);
            assertThat(second.buffer.reserved()).isEqualTo(secondAmount);
            assertThat(allocations(save(runtime))).isEqualTo(Map.of("first", firstAmount, "second", secondAmount));
        }

        private void assertReleased(long firstAmount, long secondAmount) {
            assertThat(first.buffer.reserved()).isZero();
            assertThat(second.buffer.reserved()).isZero();
            assertThat(first.buffer.amount()).isEqualTo(firstAmount);
            assertThat(first.buffer.idleExcess()).isEqualTo(firstAmount);
            assertThat(second.buffer.amount()).isEqualTo(secondAmount);
            assertThat(second.buffer.idleExcess()).isEqualTo(secondAmount);
            assertThat(first.released).isEqualTo(firstAmount);
            assertThat(second.released).isEqualTo(secondAmount);
        }

        private void assertNoReplay(CraftingRuntime owner) {
            var paid = owner.ledger().state();
            long ordinaryAmount = ordinary.amount();
            long firstAmount = first.buffer.amount();
            long secondAmount = second.buffer.amount();
            int firstCalls = first.consumeCalls;
            int secondCalls = second.consumeCalls;
            assertThat(owner.active()).isTrue();
            assertThat(paid.phase()).isEqualTo(RecipeCommitLedger.Phase.ERROR_PAUSED);
            owner.resume();
            LevelStub.setGameTime(controller.getLevel(), 2L);
            owner.tick();
            owner.finish();
            assertThat(owner.tickCount()).isZero();
            assertThat(owner.finishPending()).isFalse();
            assertThat(owner.ledger().state()).isEqualTo(paid);
            assertThat(ordinary.amount()).isEqualTo(ordinaryAmount);
            assertThat(first.buffer.amount()).isEqualTo(firstAmount);
            assertThat(second.buffer.amount()).isEqualTo(secondAmount);
            assertThat(first.consumeCalls).isEqualTo(firstCalls);
            assertThat(second.consumeCalls).isEqualTo(secondCalls);
            assertThat(output.itemStorage().amount(0)).isZero();
            assertThat(first.networkCommits).isEqualTo(1);
            assertThat(second.networkCommits).isEqualTo(1);
        }

        private CompoundTag saveAll() {
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, LOOKUP);
            runtime.save(output.child("runtime"));
            first.delegate.save(output.child("first"));
            second.delegate.save(output.child("second"));
            output.putLong("ordinary", ordinary.amount());
            return output.buildResult();
        }

        private CraftingRuntime restore(CompoundTag saved) {
            var input = TagValueInput.create(ProblemReporter.DISCARDING, LOOKUP, saved);
            first.delegate.load(input.childOrEmpty("first"));
            second.delegate.load(input.childOrEmpty("second"));
            ordinary.setAmount(input.getLongOr("ordinary", -1L));
            var restored = new CraftingRuntime(controller, controller.componentRuntime());
            restored.load(input.childOrEmpty("runtime"), null);
            assertThat(restored.active()).isTrue();
            assertThat(allocations(save(restored))).isEqualTo(allocations(saved.getCompoundOrEmpty("runtime")));
            return restored;
        }
    }

    private static void notifyOnce(AtomicReference<Runnable> notification) {
        Runnable action = notification.getAndSet(null);
        if (action != null) action.run();
    }

    /** Observes calls, constrains exact network planning, and injects rejection after a real local extraction.
     * @author howxu <dev@howxu.cn>
     */
    private static final class ObservedFlux implements MachineCapability, RecipeEnergyPrefetchFacet {
        private final AtomicReference<Runnable> notification = new AtomicReference<>();
        private final FluxEnergyBuffer buffer = new FluxEnergyBuffer(() -> notifyOnce(notification));
        private final LongValueStorage network = new LongValueStorage(100L, 100L, null);
        private final FluxEnergyInputCapability delegate;
        private final FluxEnergyInputCapability missing = new FluxEnergyInputCapability(new FluxEnergyBuffer(), "missing");
        private int networkCommits;
        private int consumeCalls;
        private int releaseCalls;
        private long released;
        private boolean rejectConsumption;
        private long consumeLimit = Long.MAX_VALUE;

        private ObservedFlux(String key, long exactAmount) {
            network.setAmount(100L);
            delegate = new FluxEnergyInputCapability(buffer, key, requested -> {
                if (requested != exactAmount) return Optional.empty();
                return Optional.of(transaction -> {
                    networkCommits++;
                    assertThat(network.extract(requested, transaction)).isEqualTo(requested);
                    return CapabilityResult.successful();
                });
            });
        }

        @Override public CapabilityType type() { return delegate.type(); }
        @Override public CapabilityDirections directions() { return delegate.directions(); }
        @Override public CapabilityView view() { return delegate.view(); }
        @Override public CapabilityOperation prepare(CapabilityRequest request) { return delegate.prepare(request); }
        @Override public <F extends CapabilityFacet> Optional<F> facet(Class<F> type) {
            return type == RecipeEnergyPrefetchFacet.class ? Optional.of(type.cast(this)) : delegate.facet(type);
        }
        @Override public String reservationKey() { return delegate.reservationKey(); }
        @Override public Optional<PrefetchPlan> planPrefetch(long amount) { return delegate.planPrefetch(amount); }
        @Override public void restoreReservation(long amount) { delegate.restoreReservation(amount); }
        @Override public long releaseReservation(long amount) {
            releaseCalls++;
            long result = delegate.releaseReservation(amount);
            released += result;
            return result;
        }
        @Override public CapabilityResult consumeReservation(long amount, TransactionContext transaction) {
            consumeCalls++;
            long debited = Math.min(amount, consumeLimit);
            CapabilityResult result = delegate.consumeReservation(debited, transaction);
            return result.success() && (rejectConsumption || debited < amount)
                    ? missing.consumeReservation(1L, transaction) : result;
        }
    }

    /** Concrete BE host accepted by the same ProcessingComponent path as ports.
     * @author howxu <dev@howxu.cn>
     */
    private static final class TestCapabilityHost extends BlockEntity implements CapabilityHost {
        private final CapabilitySnapshot capabilities;

        private TestCapabilityHost(List<MachineCapability> capabilities) {
            this(RuntimeTestFixtures.energyInput(BlockPos.ZERO), capabilities);
        }

        private TestCapabilityHost(BlockEntity entity, List<MachineCapability> capabilities) {
            super(entity.getType(), entity.getBlockPos(), entity.getBlockState());
            this.capabilities = new CapabilitySnapshot(capabilities);
        }

        @Override public CapabilitySnapshot capabilitySnapshot() { return capabilities; }
    }
}
