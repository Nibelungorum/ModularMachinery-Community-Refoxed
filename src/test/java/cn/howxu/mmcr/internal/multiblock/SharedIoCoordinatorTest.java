package cn.howxu.mmcr.internal.multiblock;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.config.ServerConfig;
import com.electronwill.nightconfig.core.CommentedConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.neoforged.fml.config.IConfigSpec;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityPlanner;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.async.AsyncCapabilitySnapshot;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.internal.async.MachineAsyncCoordinator;
import cn.howxu.mmcr.internal.async.AsyncContinuation;
import cn.howxu.mmcr.internal.recipe.AsyncRequirementPlanner;
import cn.howxu.mmcr.internal.recipe.MachineRecipeThread;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.InstalledRecipeProgram;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.internal.runtime.RecipeCommitLedger;
import cn.howxu.mmcr.internal.runtime.RecipeExecutionState;
import cn.howxu.mmcr.internal.runtime.RecipeExecutionState.Acknowledgement;
import cn.howxu.mmcr.internal.runtime.RecipeExecutionState.Receipt;
import cn.howxu.mmcr.internal.recipe.CompiledRecipeProgram.ExecutionIdentity;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.ConfigTestSupport;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import net.neoforged.neoforge.capabilities.ICapabilityInvalidationListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.ArrayDeque;
import java.util.AbstractSet;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class SharedIoCoordinatorTest {
    private ServerThreadTestScope serverThreadScope;
    private final List<ServerLevel> installedLevels = new ArrayList<>();
    private static final BlockPos A = new BlockPos(0, 64, 0);
    private static final BlockPos B = new BlockPos(4, 64, 0);
    private static final BlockPos C = new BlockPos(8, 64, 0);

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        CommentedConfig config = CommentedConfig.inMemory();
        ServerConfig.SPEC.correct(config);
        var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        ServerConfig.SPEC.acceptConfig((IConfigSpec.ILoadedConfig) constructor.newInstance(config, null, null));
    }

    @BeforeEach
    void bootstrapCapabilities() throws Exception {
        TestBootstrap.bootstrapCapabilities();
        serverThreadScope = ServerThreadTestScope.open();
    }

    @AfterEach
    void discardInstalledFixtures() throws Exception {
        try {
            for (ServerLevel level : installedLevels) {
                SharedIoCoordinator.discard(level);
                MachineAsyncCoordinator.discard(level);
                StructureClaimRegistry.discard(level);
            }
            if (!installedLevels.isEmpty()) {
                ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
                RecipeRegistry.clearForTesting();
            }
        } finally {
            if (serverThreadScope != null) serverThreadScope.close();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {19, 20, 21, 60})
    void worker_progress_mirroring_uses_effective_duration_without_delaying_local_progress(int duration) throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, duration);
        RecipeExecutionState confirmed = drainAndGetConfirmedState(fixture);
        int steps = Math.min(duration - 1, 20);
        for (int step = 1; step <= steps; step++) {
            advanceInstalledStep(fixture);
            assertThat(fixture.runtime().tickCount()).isEqualTo(step);
            assertThat(fixture.shared().receiptsForTesting(fixture.runtime(), "base").getFirst().tick()).isEqualTo(step);
            drainReceiptWorkers(fixture);
            assertThat(confirmed.confirmedTick()).isEqualTo(duration <= 20 ? step : step / 20 * 20);
            fixture.shared().resolvePost(fixture.level());
            MachineAsyncCoordinator.get(fixture.level()).pumpReceiptDrains();
            assertThat(fixture.workers()).isEmpty();
        }
        if (duration <= 21) {
            advanceInstalledStep(fixture);
            drainReceiptWorkers(fixture);
            assertThat(fixture.runtime().active()).isFalse();
            assertThat(confirmed.terminal()).isTrue();
            assertThat(confirmed.confirmedTick()).isEqualTo(duration);
        }
    }

    @ParameterizedTest
    @EnumSource(value = MachineWorkMode.class, names = {"SEMI_SYNC", "ASYNC"})
    void resource_free_post_grants_need_only_one_live_domain_validation(MachineWorkMode mode) throws Exception {
        InstalledFixture fixture = installedFixture(mode, 60, 0L);
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        tickInstalledController(fixture.runtime(), fixture);
        var domain = fixture.controller().resourceDomain();
        long before = fixture.input().energyStorage().getAmountAsLong();
        CountingDomainMap domains = countDomainLookups(StructureClaimRegistry.get(fixture.level()));

        assertThat(resolveInstalledDomain(fixture, domain)).isEqualTo(1);

        assertThat(domains.lookups).hasValue(1);
        assertThat(fixture.runtime().tickCount()).isEqualTo(1);
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(before);
        assertThat(resolveInstalledDomain(fixture, domain)).isZero();
        assertThat(domains.lookups).hasValue(1);
    }

    @Test
    void cancellation_mirrors_unpublished_progress_immediately() throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 60);
        RecipeExecutionState confirmed = drainAndGetConfirmedState(fixture);
        advanceInstalledStep(fixture);
        drainReceiptWorkers(fixture);
        assertThat(confirmed.confirmedTick()).isZero();

        fixture.runtime().invalidate();
        fixture.shared().resolvePost(fixture.level());
        drainReceiptWorkers(fixture);

        assertThat(confirmed.terminal()).isTrue();
        assertThat(confirmed.confirmedTick()).isEqualTo(1);
        assertThat(confirmed.confirmedPaidAttempts()).isEqualTo(fixture.program().ledger().state().paidAttempts());
    }

    @Test
    void failure_recovery_and_backward_progress_bypass_the_long_mirror_period() throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 60);
        RecipeExecutionState confirmed = drainAndGetConfirmedState(fixture);
        fixture.input().energyStorage().setAmount(0L);
        advanceInstalledStep(fixture);
        drainReceiptWorkers(fixture);
        assertThat(confirmed.failure()).isNotNull();
        fixture.input().energyStorage().setAmount(1_000L);
        advanceInstalledStep(fixture);
        drainReceiptWorkers(fixture);
        assertThat(confirmed.failure()).isNull();
        assertThat(confirmed.confirmedTick()).isEqualTo(1);
        advanceInstalledStep(fixture);
        advanceInstalledStep(fixture);
        drainReceiptWorkers(fixture);
        assertThat(confirmed.confirmedTick()).isEqualTo(1);

        fixture.runtime().activeRecipe().setTick(0);
        advanceInstalledStep(fixture);
        drainReceiptWorkers(fixture);

        assertThat(confirmed.confirmedTick()).isEqualTo(1);
        assertThat(confirmed.confirmedPaidAttempts()).isEqualTo(fixture.program().ledger().state().paidAttempts());
    }

    @Test
    void a_long_game_time_jump_publishes_only_the_latest_running_fact() throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 60, 0L);
        RecipeExecutionState confirmed = drainAndGetConfirmedState(fixture);
        advanceInstalledStep(fixture);
        drainReceiptWorkers(fixture);
        assertThat(confirmed.confirmedTick()).isZero();
        for (int skipped = 0; skipped < 100; skipped++) RuntimeTestFixtures.advanceGameTime(fixture.level());

        advanceInstalledStep(fixture);
        MachineAsyncCoordinator.get(fixture.level()).pumpReceiptDrains();

        assertThat(fixture.workers()).hasSize(1);
        drainReceiptWorkers(fixture);
        assertThat(confirmed.confirmedTick()).isEqualTo(2);
        assertThat(fixture.runtime().tickCount()).isEqualTo(2);
        assertThat(fixture.workers()).isEmpty();
    }

    @Test
    void revision_replacement_mirrors_unpublished_progress_in_the_same_game_tick() throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 60, 0L);
        RecipeExecutionState confirmed = drainAndGetConfirmedState(fixture);
        advanceInstalledStep(fixture);
        drainReceiptWorkers(fixture);
        assertThat(confirmed.confirmedTick()).isZero();
        Receipt previous = fixture.shared().receiptsForTesting(fixture.runtime(), "base").getFirst();
        InstalledRecipeProgram replacement = new InstalledRecipeProgram(fixture.program().identity(),
                fixture.program().program(), fixture.runtime());
        // A compiled replacement is the live owner binding before the coordinator swaps delivery revision.
        Field installedField = CraftingRuntime.class.getDeclaredField("installedProgram");
        installedField.setAccessible(true);
        installedField.set(fixture.runtime(), replacement);

        assertThat(fixture.shared().replaceExecution(replacement,
                fixture.shared().controllerPermit(fixture.controller().getBlockPos()),
                previous.installedRevision(), 2L)).isTrue();
        drainReceiptWorkers(fixture);

        assertThat(confirmed.confirmedTick()).isEqualTo(1);
        assertThat(confirmed.consumedSequence()).isGreaterThan(previous.sequence());
        assertThat(fixture.shared().receiptsForTesting(fixture.runtime(), "base").getFirst().installedRevision())
                .isGreaterThan(previous.installedRevision());
        assertThat(fixture.shared().resolvePost(fixture.level())).isZero();
        assertThat(replacement.current()).isTrue();
        Object slot = ((Map<?, ?>) schedulingField(fixture.shared(), "activeExecutions")).get(fixture.runtime());
        assertThat(schedulingField(slot, "controller")).isSameAs(fixture.controller());
        assertThat(schedulingField(slot, "acceptedOwner")).isSameAs(replacement.owner());
    }

    @Test
    void acknowledged_slot_reuse_starts_a_new_mirror_period_and_rejects_the_old_ack() throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 60, 0L);
        RecipeExecutionState previous = drainAndGetConfirmedState(fixture);
        for (int step = 0; step < 5; step++) advanceInstalledStep(fixture);
        fixture.runtime().invalidate();
        fixture.shared().resolvePost(fixture.level());
        Receipt terminal = fixture.shared().receiptsForTesting(fixture.runtime(), "base").getFirst();
        drainReceiptWorkers(fixture);
        fixture.shared().drainReceiptAcknowledgements(fixture.level());
        assertThat(previous.terminal()).isTrue();
        assertThat(previous.confirmedTick()).isEqualTo(5);
        assertThat(fixture.shared().reserveExecution(fixture.runtime(), "base")).isTrue();
        assertThat(fixture.runtime().start(fixture.recipe(), 1L).isCrafting()).isTrue();
        InstalledRecipeProgram replacement = fixture.runtime().ensureInstalledProgram();
        assertThat(fixture.shared().install(replacement,
                fixture.shared().controllerPermit(fixture.controller().getBlockPos()))).isTrue();
        RecipeExecutionState current = drainAndGetConfirmedState(fixture);
        Object slot = ((Map<?, ?>) schedulingField(fixture.shared(), "activeExecutions")).get(fixture.runtime());
        assertThat(schedulingField(slot, "controller")).isSameAs(fixture.controller());
        assertThat(schedulingField(slot, "acceptedOwner")).isSameAs(replacement.owner());
        assertThat(current.confirmedTick()).isZero();
        assertThat(current.failure()).isNull();

        for (int step = 1; step <= 20; step++) {
            advanceInstalledStep(fixture);
            drainReceiptWorkers(fixture);
            assertThat(fixture.runtime().tickCount()).isEqualTo(step);
            assertThat(current.confirmedTick()).isEqualTo(step < 20 ? 0 : 20);
        }
        assertThat(fixture.shared().acknowledgeReceipt(new Acknowledgement(terminal.identity(),
                terminal.installedRevision(), terminal.sequence()))).isFalse();
        assertThat(replacement.current()).isTrue();
    }

    @Test
    void detaching_an_installed_controller_still_publishes_terminal_facts_without_charging() throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 60, 0L);
        RecipeExecutionState confirmed = drainAndGetConfirmedState(fixture);
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        tickInstalledController(fixture.runtime(), fixture);
        fixture.controller().setLevel(null);

        assertThat(fixture.shared().resolvePost(fixture.level())).isZero();
        drainReceiptWorkers(fixture);

        assertThat(confirmed.terminal()).isTrue();
        assertThat(fixture.runtime().tickCount()).isZero();
    }

    @Test
    void changed_effective_duration_uses_short_mirroring_without_waiting_for_the_original_period() throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 60);
        RecipeExecutionState confirmed = drainAndGetConfirmedState(fixture);
        fixture.runtime().activeRecipe().setTotalTick(20);
        advanceInstalledStep(fixture);
        drainReceiptWorkers(fixture);
        assertThat(confirmed.confirmedTick()).isEqualTo(1);
    }

    private void advanceInstalledStep(InstalledFixture fixture) {
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        tickInstalledController(fixture.runtime(), fixture);
        fixture.shared().resolvePost(fixture.level());
    }

    private static void drainReceiptWorkers(InstalledFixture fixture) {
        MachineAsyncCoordinator.get(fixture.level()).pumpReceiptDrains();
        while (!fixture.workers().isEmpty()) fixture.workers().removeFirst().run();
    }

    private static RecipeExecutionState drainAndGetConfirmedState(InstalledFixture fixture) throws Exception {
        drainReceiptWorkers(fixture);
        var drainsField = MachineAsyncCoordinator.class.getDeclaredField("receiptDrains");
        drainsField.setAccessible(true);
        Map<?, ?> drains = (Map<?, ?>) drainsField.get(MachineAsyncCoordinator.get(fixture.level()));
        Object drain = drains.values().iterator().next();
        var mailboxesField = drain.getClass().getDeclaredField("mailboxes");
        mailboxesField.setAccessible(true);
        Object mailbox = ((Map<?, ?>) mailboxesField.get(drain)).get(fixture.runtime().installedProgram().identity());
        var stateField = mailbox.getClass().getDeclaredField("state");
        stateField.setAccessible(true);
        return (RecipeExecutionState) stateField.get(mailbox);
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void installed_long_recipe_consumes_real_energy_without_draining_workers_and_post_cannot_replay(MachineWorkMode mode) {
        InstalledFixture fixture = installedFixture(mode, 40);
        long afterStart = fixture.input().energyStorage().getAmountAsLong();
        for (int tick = 1; tick <= 5; tick++) {
            RuntimeTestFixtures.advanceGameTime(fixture.level());
            RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
            fixture.controller().serverTick();
            fixture.runtime().prepareInstalledTick(fixture.shared().executionRoundId());
            if (mode == MachineWorkMode.SYNC) fixture.shared().executeInstalled(fixture.program(), fixture.level());
            fixture.shared().executeInstalled(fixture.level());
            long after = fixture.input().energyStorage().getAmountAsLong();
            assertThat(after).isEqualTo(afterStart - 7L * tick);
            assertThat(fixture.runtime().tickCount()).isEqualTo(tick);
            fixture.shared().executeInstalled(fixture.level());
            fixture.shared().executeInstalled(fixture.program(), fixture.level());
            assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(after);
            assertNoWorkerResourceTasks(fixture);
        }
    }

    @Test
    void installed_budget_backlog_freeze_and_no_ticker_do_not_consume_or_catch_up() throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 40);
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.controller().serverTick();
        fixture.runtime().prepareInstalledTick(fixture.shared().executionRoundId());
        Field remaining = SharedIoCoordinator.class.getDeclaredField("remainingRequests");
        remaining.setAccessible(true);
        remaining.setInt(fixture.shared(), 0);
        long before = fixture.input().energyStorage().getAmountAsLong();
        fixture.shared().executeInstalled(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), false);
        fixture.shared().executeInstalled(fixture.level());
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(before);
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.shared().executeInstalled(fixture.level()); // Loaded controller, no ticker this round.
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(before);
        fixture.controller().serverTick();
        fixture.runtime().prepareInstalledTick(fixture.shared().executionRoundId());
        fixture.shared().executeInstalled(fixture.level());
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(before - 7L);
        assertThat(fixture.runtime().tickCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void paid_lane_repeated_scans_skip_live_validation_but_next_tick_validates_again(MachineWorkMode mode) throws Exception {
        InstalledFixture fixture = installedFixture(mode, 40);
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.shared().markControllerEligible(fixture.controller(), fixture.level().getGameTime());
        fixture.runtime().prepareInstalledTick(fixture.shared().executionRoundId());
        if (mode == MachineWorkMode.SYNC) fixture.shared().executeInstalled(fixture.program(), fixture.level());
        else fixture.shared().resolve(fixture.level());
        assertThat(fixture.runtime().tickCount()).isEqualTo(1);
        long afterPaid = fixture.input().energyStorage().getAmountAsLong();
        var domain = fixture.controller().resourceDomain();
        CountingDomainMap domains = countDomainLookups(StructureClaimRegistry.get(fixture.level()));
        CountingLookupMap<BlockPos, BlockEntity> world = countLookups(fixture.level(), "blockEntities");

        for (int scan = 0; scan < 3; scan++) {
            assertThat(resolveInstalledDomain(fixture, domain)).isZero();
            assertThat(fixture.shared().executeInstalled(fixture.program(), fixture.level())).isFalse();
        }
        assertThat(domains.lookups).hasValue(0);
        assertThat(world.lookups).hasValue(0);
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(afterPaid);

        // Public resolve still reconciles topology once; the paid lane adds no live validation.
        assertThat(fixture.shared().resolve(fixture.level())).isZero();
        assertThat(domains.lookups).hasValue(1);
        assertThat(world.lookups).hasValue(0);

        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.shared().markControllerEligible(fixture.controller(), fixture.level().getGameTime());
        fixture.runtime().prepareInstalledTick(fixture.shared().executionRoundId());
        domains.lookups.set(0);
        world.lookups.set(0);
        if (mode == MachineWorkMode.SYNC) assertThat(fixture.shared().executeInstalled(fixture.program(), fixture.level())).isTrue();
        else assertThat(resolveInstalledDomain(fixture, domain)).isEqualTo(1);
        assertThat(domains.lookups.get()).isGreaterThan(0);
        assertThat(world.lookups.get()).isGreaterThan(0);
        assertThat(fixture.runtime().tickCount()).isEqualTo(2);
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(afterPaid - 7L);
    }

    @ParameterizedTest
    @EnumSource(value = MachineWorkMode.class, names = {"SEMI_SYNC", "ASYNC"})
    void live_selection_qualifies_the_selected_lane_once_before_payment(MachineWorkMode mode) throws Exception {
        InstalledFixture fixture = installedFixture(mode, 40);
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.shared().markControllerEligible(fixture.controller(), fixture.level().getGameTime());
        assertThat(fixture.runtime().prepareInstalledTick(fixture.shared().executionRoundId())).isTrue();
        var domain = fixture.controller().resourceDomain();
        long before = fixture.input().energyStorage().getAmountAsLong();
        CountingLookupMap<BlockPos, BlockEntity> world = countLookups(fixture.level(), "blockEntities");

        assertThat(resolveInstalledDomain(fixture, domain)).isEqualTo(1);

        assertThat(world.lookups).hasValue(1);
        assertThat(fixture.runtime().tickCount()).isEqualTo(1);
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(before - 7L);
        assertThat(resolveInstalledDomain(fixture, domain)).isZero();
        assertThat(fixture.shared().executeInstalled(fixture.program(), fixture.level())).isFalse();
        assertThat(world.lookups).hasValue(1);
        assertThat(fixture.runtime().tickCount()).isEqualTo(1);
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(before - 7L);
    }

    @ParameterizedTest
    @EnumSource(value = MachineWorkMode.class, names = {"SEMI_SYNC", "ASYNC"})
    void callback_invalidation_does_not_reuse_the_previous_controller_qualification(MachineWorkMode mode) {
        InstalledFixture fixture = installedFixture(mode, 40);
        CraftingRuntime second = attachSecondInstalledController(fixture);
        InstalledRecipeProgram firstProgram = fixture.runtime().installedProgram();
        InstalledRecipeProgram secondProgram = second.installedProgram();
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        tickInstalledController(fixture.runtime(), fixture);
        tickInstalledController(second, fixture);
        AtomicInteger callbacks = new AtomicInteger();
        fixture.shared().enqueue(new SharedIoCoordinator.TickRequest(fixture.controller().resourceDomain(),
                new SharedIoCoordinator.LaneKey(second.controller().getBlockPos(), ""), 1L, 0L,
                () -> {
                    assertThat(fixture.runtime().tickCount()).isEqualTo(1);
                    assertThat(second.tickCount()).isZero();
                    second.controller().componentRuntime().replaceComponents(List.of());
                    callbacks.incrementAndGet();
                    return true;
                }, () -> true, () -> 1L, () -> 0L));
        long before = fixture.input().energyStorage().getAmountAsLong();
        long secondPaidAttempts = secondProgram.ledger().state().paidAttempts();

        assertThat(fixture.shared().resolvePost(fixture.level())).isEqualTo(2);

        assertThat(callbacks).hasValue(1);
        assertThat(fixture.runtime().tickCount()).isEqualTo(1);
        assertThat(second.tickCount()).isZero();
        assertThat(secondProgram.current()).isFalse();
        assertThat(fixture.shared().receiptsForTesting(second, "base")).singleElement()
                .satisfies(receipt -> {
                    assertThat(receipt.terminal()).isTrue();
                    assertThat(receipt.paidAttempts()).isEqualTo(secondPaidAttempts);
                });
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(before - 7L);
        assertThat(fixture.shared().resolvePost(fixture.level())).isZero();
        assertThat(fixture.shared().executeInstalled(firstProgram, fixture.level())).isFalse();
        assertThat(fixture.shared().executeInstalled(secondProgram, fixture.level())).isFalse();
        assertThat(callbacks).hasValue(1);
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(before - 7L);
    }

    @Test
    void post_reuses_consumed_candidates_and_admits_a_same_tick_install() throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 40);
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.shared().markControllerEligible(fixture.controller(), fixture.level().getGameTime());
        assertThat(fixture.runtime().prepareInstalledTick(fixture.shared().executionRoundId())).isTrue();
        long before = fixture.input().energyStorage().getAmountAsLong();
        assertThat(fixture.shared().resolvePost(fixture.level())).isEqualTo(1);
        CountingDomainMap domains = countDomainLookups(StructureClaimRegistry.get(fixture.level()));
        Field activeField = SharedIoCoordinator.class.getDeclaredField("activeExecutions");
        activeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var active = new CountingIterationMap<Object, Object>((Map<Object, Object>) activeField.get(fixture.shared()));
        activeField.set(fixture.shared(), active);
        for (int drain = 0; drain < 5; drain++) assertThat(fixture.shared().resolvePost(fixture.level())).isZero();
        assertThat(domains.lookups).hasValue(0);
        assertThat(active.visits).hasValue(0);
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(before - 7L);

        CraftingRuntime other = new CraftingRuntime(fixture.controller(), fixture.controller().componentRuntime());
        other.bindRecipeLane("post-install", () -> { });
        assertThat(fixture.shared().reserveExecution(other, "post-install")).isTrue();
        assertThat(other.start(fixture.recipe(), 1L).isCrafting()).isTrue();
        assertThat(fixture.shared().install(other.ensureInstalledProgram(),
                fixture.shared().controllerPermit(fixture.controller().getBlockPos()))).isTrue();
        assertThat(other.prepareInstalledTick(fixture.shared().executionRoundId())).isTrue();
        long afterStart = fixture.input().energyStorage().getAmountAsLong();
        assertThat(fixture.shared().resolvePost(fixture.level())).isEqualTo(1);
        assertThat(other.tickCount()).isEqualTo(1);
        assertThat(fixture.runtime().tickCount()).isEqualTo(1);
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(afterStart - 7L);
        assertThat(fixture.shared().resolvePost(fixture.level())).isZero();
        assertThat(active.visits).hasValue(0);
    }

    @Test
    void post_reconciles_a_topology_change_even_after_all_candidates_were_consumed() {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 40);
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.shared().markControllerEligible(fixture.controller(), fixture.level().getGameTime());
        fixture.runtime().prepareInstalledTick(fixture.shared().executionRoundId());
        assertThat(fixture.shared().resolvePost(fixture.level())).isEqualTo(1);
        long afterPaid = fixture.input().energyStorage().getAmountAsLong();
        StructureClaimRegistry.get(fixture.level()).release(fixture.controller().getBlockPos());
        assertThat(fixture.shared().resolvePost(fixture.level())).isZero();
        assertThat(fixture.program().current()).isFalse();
        assertThat(fixture.shared().receiptsForTesting(fixture.runtime(), "base")).singleElement()
                .satisfies(receipt -> assertThat(receipt.terminal()).isTrue());
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(afterPaid);
    }

    @Test
    void installed_candidates_are_consumed_with_linear_controller_lookup_work() throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 100);
        List<CraftingRuntime> lanes = new ArrayList<>(List.of(fixture.runtime()));
        for (int index = 1; index < 32; index++) {
            CraftingRuntime runtime = new CraftingRuntime(fixture.controller(), fixture.controller().componentRuntime());
            String name = "lane-" + index;
            runtime.bindRecipeLane(name, () -> { });
            assertThat(fixture.shared().reserveExecution(runtime, name)).isTrue();
            assertThat(runtime.start(fixture.recipe(), 1L).isCrafting()).isTrue();
            assertThat(fixture.shared().install(runtime.ensureInstalledProgram(),
                    fixture.shared().controllerPermit(fixture.controller().getBlockPos()))).isTrue();
            lanes.add(runtime);
        }
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.shared().markControllerEligible(fixture.controller(), fixture.level().getGameTime());
        for (CraftingRuntime lane : lanes) assertThat(lane.prepareInstalledTick(fixture.shared().executionRoundId())).isTrue();
        Field domainsField = SharedIoCoordinator.class.getDeclaredField("domains");
        domainsField.setAccessible(true);
        Map<?, ?> buckets = (Map<?, ?>) domainsField.get(fixture.shared());
        Object bucket = buckets.values().iterator().next();
        AtomicInteger reads = new AtomicInteger();
        CountingCandidateList<Object> pending = countCandidateList(bucket, "pendingInstalled", reads);
        Field controllersField = bucket.getClass().getDeclaredField("installedByController");
        controllersField.setAccessible(true);
        Object candidates = ((Map<?, ?>) controllersField.get(bucket)).values().iterator().next();
        CountingCandidateList<Object> ordered = countCandidateList(candidates, "ordered", reads);
        AtomicInteger laneKeyReads = new AtomicInteger();
        countCandidateList(candidates, "orderedLaneIds", laneKeyReads);
        CountingIterationSet<Object> executionChanges = countSetIterations(fixture.shared(), "changedExecutionDomains");
        CountingIterationSet<Object> requestChanges = countSetIterations(fixture.shared(), "changedRequestDomains");
        fixture.input().energyStorage().setAmount(7L * lanes.size() * 4);
        long before = fixture.input().energyStorage().getAmountAsLong();

        assertThat(fixture.shared().resolvePost(fixture.level())).isEqualTo(lanes.size());
        for (CraftingRuntime lane : lanes) assertThat(lane.tickCount()).isEqualTo(1);
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(before - 7L * lanes.size());
        assertThat(reads.get()).isLessThanOrEqualTo(12 * lanes.size());
        assertThat(laneKeyReads.get()).isLessThanOrEqualTo(2 * lanes.size());
        int consumedLookups = reads.get();
        assertThat(fixture.shared().resolvePost(fixture.level())).isZero();
        assertThat(reads).hasValue(consumedLookups);
        int controllerRebuilds = pending.rebuilds.get();
        int laneRebuilds = ordered.rebuilds.get();
        for (int round = 0; round < 3; round++) {
            RuntimeTestFixtures.advanceGameTime(fixture.level());
            RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
            fixture.shared().markControllerEligible(fixture.controller(), fixture.level().getGameTime());
            for (CraftingRuntime lane : lanes) assertThat(lane.prepareInstalledTick(fixture.shared().executionRoundId())).isTrue();
            reads.set(0);
            laneKeyReads.set(0);
            assertThat(fixture.shared().resolvePost(fixture.level())).isEqualTo(lanes.size());
            assertThat(reads.get()).isLessThanOrEqualTo(12 * lanes.size());
            assertThat(laneKeyReads.get()).isLessThanOrEqualTo(2 * lanes.size());
            for (CraftingRuntime lane : lanes) assertThat(lane.tickCount()).isEqualTo(round + 2);
            assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(before - 7L * lanes.size() * (round + 2));
        }
        assertThat(pending.rebuilds).hasValue(controllerRebuilds);
        assertThat(ordered.rebuilds).hasValue(laneRebuilds);
        assertThat(executionChanges.iterators).hasValue(0);
        assertThat(requestChanges.iterators).hasValue(0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void batch_completion_or_sleep_does_not_rebuild_remaining_candidates_per_lane(boolean sleep) throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, sleep ? 40 : 2);
        List<CraftingRuntime> lanes = new ArrayList<>(List.of(fixture.runtime()));
        for (int index = 1; index < 32; index++) {
            CraftingRuntime lane = new CraftingRuntime(fixture.controller(), fixture.controller().componentRuntime());
            String name = "lane-" + index;
            lane.bindRecipeLane(name, () -> { });
            assertThat(fixture.shared().reserveExecution(lane, name)).isTrue();
            assertThat(lane.start(fixture.recipe(), 1L).isCrafting()).isTrue();
            assertThat(fixture.shared().install(lane.ensureInstalledProgram(),
                    fixture.shared().controllerPermit(fixture.controller().getBlockPos()))).isTrue();
            lanes.add(lane);
        }
        Field domainsField = SharedIoCoordinator.class.getDeclaredField("domains");
        domainsField.setAccessible(true);
        Object bucket = ((Map<?, ?>) domainsField.get(fixture.shared())).values().iterator().next();
        AtomicInteger reads = new AtomicInteger();
        CountingCandidateList<Object> controllers = countCandidateList(bucket, "pendingInstalled", reads);
        Field controllersField = bucket.getClass().getDeclaredField("installedByController");
        controllersField.setAccessible(true);
        Object controller = ((Map<?, ?>) controllersField.get(bucket)).values().iterator().next();
        CountingCandidateList<Object> ordered = countCandidateList(controller, "ordered", reads);
        fixture.input().energyStorage().setAmount(sleep ? 0L : 14L * lanes.size());
        for (int tick = 0; tick < (sleep ? 1 : 2); tick++) {
            RuntimeTestFixtures.advanceGameTime(fixture.level());
            RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
            fixture.shared().markControllerEligible(fixture.controller(), fixture.level().getGameTime());
            for (CraftingRuntime lane : lanes) lane.prepareInstalledTick(fixture.shared().executionRoundId());
            reads.set(0);
            assertThat(fixture.shared().resolvePost(fixture.level())).isEqualTo(sleep ? 0 : lanes.size());
            assertThat(reads.get()).isLessThanOrEqualTo(12 * lanes.size());
        }
        assertThat(controllers.rebuilds).hasValue(1);
        assertThat(ordered.rebuilds).hasValue(1);
        for (int index = 0; index < lanes.size(); index++) {
            CraftingRuntime lane = lanes.get(index);
            assertThat(lane.active()).isEqualTo(sleep);
            if (sleep) assertThat(lane.tickCount()).isZero();
            assertThat(fixture.shared().receiptsForTesting(lane, index == 0 ? "base" : "lane-" + index))
                    .singleElement().satisfies(receipt -> assertThat(receipt.terminal()).isEqualTo(!sleep));
        }
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isZero();
        assertThat(fixture.shared().resolvePost(fixture.level())).isZero();
        if (sleep) {
            fixture.input().energyStorage().setAmount(7L * lanes.size());
            RuntimeTestFixtures.advanceGameTime(fixture.level());
            RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
            fixture.shared().markControllerEligible(fixture.controller(), fixture.level().getGameTime());
            for (CraftingRuntime lane : lanes) lane.prepareInstalledTick(fixture.shared().executionRoundId());
            assertThat(fixture.shared().resolvePost(fixture.level())).isEqualTo(lanes.size());
            assertThat(fixture.input().energyStorage().getAmountAsLong()).isZero();
            for (CraftingRuntime lane : lanes) assertThat(lane.tickCount()).isEqualTo(1);
        }
    }

    @Test
    void ordinal_lookups_reuse_positions_but_follow_external_cursor_changes() throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 100);
        CraftingRuntime second = attachSecondInstalledController(fixture);
        Object bucket = schedulingBucket(fixture.shared());
        invokeScheduling(bucket, "preparePending", new Class<?>[]{long.class, long.class},
                fixture.shared().executionRoundId(), fixture.level().getGameTime());
        AtomicInteger controllerReads = new AtomicInteger();
        countCandidateList(bucket, "pendingInstalled", controllerReads);
        SharedIoCoordinator.LaneKey cursor = new SharedIoCoordinator.LaneKey(fixture.controller().getBlockPos(), "base");
        assertThat(invokeScheduling(bucket, "afterController", new Class<?>[]{SharedIoCoordinator.LaneKey.class}, cursor))
                .isEqualTo(1);
        controllerReads.set(0);
        for (int repeat = 0; repeat < 16; repeat++) {
            assertThat(invokeScheduling(bucket, "afterController", new Class<?>[]{SharedIoCoordinator.LaneKey.class}, cursor))
                    .isEqualTo(1);
        }
        assertThat(controllerReads).hasValue(0);
        SharedIoCoordinator.LaneKey removedCursor = new SharedIoCoordinator.LaneKey(new BlockPos(5, 0, 0), "missing");
        assertThat(invokeScheduling(bucket, "afterController", new Class<?>[]{SharedIoCoordinator.LaneKey.class}, removedCursor))
                .isEqualTo(1);
        assertThat(invokeScheduling(bucket, "afterController", new Class<?>[]{SharedIoCoordinator.LaneKey.class},
                new SharedIoCoordinator.LaneKey(second.controller().getBlockPos(), "base"))).isEqualTo(2);
    }

    @Test
    void live_slot_binding_is_cleared_without_erasing_its_terminal_receipt() throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 1);
        Object slot = ((Map<?, ?>) schedulingField(fixture.shared(), "activeExecutions")).get(fixture.runtime());
        assertThat(schedulingField(slot, "controller")).isSameAs(fixture.controller());
        assertThat(schedulingField(slot, "acceptedOwner")).isSameAs(fixture.runtime());
        completeInstalledFixture(fixture);
        assertThat(schedulingField(slot, "controller")).isNull();
        assertThat(schedulingField(slot, "pendingController")).isNull();
        assertThat(schedulingField(slot, "acceptedOwner")).isNull();
        assertThat(fixture.shared().receiptsForTesting(fixture.runtime(), "base"))
                .singleElement().satisfies(receipt -> assertThat(receipt.terminal()).isTrue());
    }

    @Test
    void ordinary_request_count_tracks_actual_removal_and_retained_failures() throws Exception {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(8, 8);
        var domain = domain(A);
        AtomicBoolean succeeds = new AtomicBoolean();
        var request = tick(domain, A, 1L, succeeds::get, () -> true, () -> 1L);
        coordinator.enqueue(request);
        coordinator.enqueue(request);
        Object bucket = schedulingBucket(coordinator);
        assertThat(schedulingField(bucket, "requestCount")).isEqualTo(2);
        invokeScheduling(bucket, "remove", new Class<?>[]{SharedIoCoordinator.Request.class}, request);
        var absent = tick(domain, A, 1L, () -> true, () -> true, () -> 1L);
        invokeScheduling(bucket, "remove", new Class<?>[]{SharedIoCoordinator.Request.class}, absent);
        assertThat(schedulingField(bucket, "requestCount")).isEqualTo(1);
        assertThat(coordinator.resolve(domain)).isZero();
        assertThat(schedulingField(bucket, "requestCount")).isEqualTo(1);
        succeeds.set(true);
        assertThat(coordinator.resolve(domain)).isEqualTo(1);
        invokeScheduling(bucket, "remove", new Class<?>[]{SharedIoCoordinator.Request.class}, request);
        assertThat(schedulingField(bucket, "requestCount")).isEqualTo(0);
        assertThat(coordinator.pendingRequestCountForTesting()).isZero();
    }

    @Test
    void controller_ordinal_is_invalidated_by_real_membership_rebuilds() throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 100);
        CraftingRuntime second = attachSecondInstalledController(fixture);
        Object bucket = schedulingBucket(fixture.shared());
        Object slot = ((Map<?, ?>) schedulingField(fixture.shared(), "activeExecutions")).get(second);
        SharedIoCoordinator.LaneKey cursor = new SharedIoCoordinator.LaneKey(second.controller().getBlockPos(), "base");
        Class<?>[] lookup = {SharedIoCoordinator.LaneKey.class};
        prepareSchedulingBucket(bucket, 1L);
        assertThat(invokeScheduling(bucket, "afterController", lookup, cursor)).isEqualTo(2);
        invokeScheduling(bucket, "removeInstalled", new Class<?>[]{slot.getClass()}, slot);
        prepareSchedulingBucket(bucket, 2L);
        assertThat(invokeScheduling(bucket, "afterController", lookup, cursor)).isEqualTo(1);
        invokeScheduling(bucket, "putInstalled", new Class<?>[]{slot.getClass()}, slot);
        prepareSchedulingBucket(bucket, 3L);
        assertThat(invokeScheduling(bucket, "afterController", lookup, cursor)).isEqualTo(2);
    }

    @Test
    void lane_ordinals_match_key_order_through_random_membership_and_cursor_changes() throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 100, 0L);
        List<Object> slots = new ArrayList<>();
        for (String name : List.of("lane-1", "lane-10", "lane-2", "factory-0", "z")) {
            CraftingRuntime lane = installSchedulingLane(fixture, name);
            slots.add(((Map<?, ?>) schedulingField(fixture.shared(), "activeExecutions")).get(lane));
        }
        Object bucket = schedulingBucket(fixture.shared());
        prepareSchedulingBucket(bucket, 1L);
        Object controller = schedulingField(slots.getFirst(), "pendingController");
        Random random = new Random(7261L);
        String cursor = "lane-2";
        for (int step = 0; step < 600; step++) {
            if (step % 3 == 0) {
                Object slot = slots.get(random.nextInt(slots.size()));
                String action = random.nextBoolean() ? "putInstalled" : "removeInstalled";
                invokeScheduling(bucket, action, new Class<?>[]{slot.getClass()}, slot);
                prepareSchedulingBucket(bucket, step + 2L);
            }
            if (step % 4 == 0) cursor = "lane-" + random.nextInt(14);
            if (step % 17 == 0) cursor = null;
            List<?> keys = new ArrayList<>(((Map<?, ?>) schedulingField(controller, "lanes")).keySet());
            int expected = 0;
            if (cursor != null) for (Object key : keys) if (((String) key).compareTo(cursor) <= 0) expected++;
            assertThat(invokeScheduling(controller, "after", new Class<?>[]{String.class}, cursor))
                    .as("upper bound after membership/cursor change %s", step).isEqualTo(expected);
        }
    }

    @Test
    void dirty_membership_does_not_prime_an_old_selected_position() throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 100, 0L);
        CraftingRuntime lane = installSchedulingLane(fixture, "lane-2");
        Object slot = ((Map<?, ?>) schedulingField(fixture.shared(), "activeExecutions")).get(lane);
        Object bucket = schedulingBucket(fixture.shared());
        prepareSchedulingBucket(bucket, 1L);
        Object controller = schedulingField(slot, "pendingController");
        invokeScheduling(controller, "after", new Class<?>[]{String.class}, "base");
        installSchedulingLane(fixture, "lane-10");
        invokeScheduling(bucket, "markInstalledAttempt", new Class<?>[]{slot.getClass()}, slot);
        assertThat(schedulingField(controller, "tickLaneCursor")).isEqualTo("base");
        prepareSchedulingBucket(bucket, 2L);
        assertThat(invokeScheduling(controller, "after", new Class<?>[]{String.class}, "lane-2")).isEqualTo(3);
    }

    @Test
    void ordinary_attempt_and_commit_cursors_override_warmed_installed_ordinals() throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 100);
        CraftingRuntime second = attachSecondInstalledController(fixture);
        Object bucket = schedulingBucket(fixture.shared());
        prepareSchedulingBucket(bucket, 1L);
        Object slot = ((Map<?, ?>) schedulingField(fixture.shared(), "activeExecutions")).get(fixture.runtime());
        invokeScheduling(bucket, "markInstalledAttempt", new Class<?>[]{slot.getClass()}, slot);
        Class<?> type = Class.forName(SharedIoCoordinator.class.getName() + "$RequestType");
        Object tick = type.getEnumConstants()[1];
        SharedIoCoordinator.LaneKey other = new SharedIoCoordinator.LaneKey(second.controller().getBlockPos(), "ordinary");
        invokeScheduling(bucket, "markAttempted", new Class<?>[]{type, SharedIoCoordinator.LaneKey.class}, tick, other);
        Object cursor = ((Map<?, ?>) schedulingField(bucket, "cursors")).get(tick);
        assertThat(invokeScheduling(bucket, "afterController", new Class<?>[]{SharedIoCoordinator.LaneKey.class}, cursor))
                .isEqualTo(2);
        var request = tick(fixture.controller().resourceDomain(), fixture.controller().getBlockPos(),
                1L, () -> true, () -> true, () -> 1L);
        invokeScheduling(bucket, "markCommitted", new Class<?>[]{SharedIoCoordinator.Request.class}, request);
        cursor = ((Map<?, ?>) schedulingField(bucket, "cursors")).get(tick);
        assertThat(invokeScheduling(bucket, "afterController", new Class<?>[]{SharedIoCoordinator.LaneKey.class}, cursor))
                .isEqualTo(1);
    }

    @Test
    void retained_live_request_then_empty_queue_preserve_installed_payment_and_progress() throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 40, 7L, 2);
        AtomicBoolean succeeds = new AtomicBoolean();
        AtomicInteger attempts = new AtomicInteger();
        fixture.shared().enqueue(new SharedIoCoordinator.TickRequest(fixture.controller().resourceDomain(),
                new SharedIoCoordinator.LaneKey(fixture.controller().getBlockPos(), ""), 1L, 0L,
                () -> { attempts.incrementAndGet(); return succeeds.get(); }, () -> true, () -> 1L, () -> 0L));
        Object bucket = schedulingBucket(fixture.shared());
        long before = fixture.input().energyStorage().getAmountAsLong();
        for (int tick = 1; tick <= 2; tick++) {
            succeeds.set(tick == 2);
            RuntimeTestFixtures.advanceGameTime(fixture.level());
            RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
            tickInstalledController(fixture.runtime(), fixture);
            assertThat(fixture.shared().resolvePost(fixture.level())).isEqualTo(tick);
            assertThat(attempts).hasValue(tick);
            assertThat(schedulingField(bucket, "requestCount")).isEqualTo(tick == 1 ? 1 : 0);
            assertThat(fixture.runtime().tickCount()).isEqualTo(tick);
            assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(before - 7L * tick);
            assertThat(fixture.shared().resolvePost(fixture.level())).isZero();
            assertThat(fixture.runtime().tickCount()).isEqualTo(tick);
            assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(before - 7L * tick);
        }
    }

    private CraftingRuntime installSchedulingLane(InstalledFixture fixture, String name) {
        CraftingRuntime lane = new CraftingRuntime(fixture.controller(), fixture.controller().componentRuntime());
        lane.bindRecipeLane(name, () -> { });
        assertThat(fixture.shared().reserveExecution(lane, name)).isTrue();
        assertThat(lane.start(fixture.recipe(), 1L).isCrafting()).isTrue();
        assertThat(fixture.shared().install(lane.ensureInstalledProgram(),
                fixture.shared().controllerPermit(fixture.controller().getBlockPos()))).isTrue();
        return lane;
    }

    private static void prepareSchedulingBucket(Object bucket, long time) throws Exception {
        invokeScheduling(bucket, "preparePending", new Class<?>[]{long.class, long.class}, time, time);
    }

    private static Object schedulingBucket(SharedIoCoordinator coordinator) throws Exception {
        return ((Map<?, ?>) schedulingField(coordinator, "domains")).values().iterator().next();
    }

    private static Object schedulingField(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static Object invokeScheduling(Object owner, String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = owner.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(owner, arguments);
    }

    /** Counts actual candidate index reads while retaining its ordering. @author howxu <dev@howxu.cn> */
    private static final class CountingTreeMap<K, V> extends TreeMap<K, V> {
        private final AtomicInteger lookups;
        private CountingTreeMap(Comparator<? super K> comparator, AtomicInteger lookups) {
            super(comparator);
            this.lookups = lookups;
        }
        @Override public V get(Object key) {
            lookups.incrementAndGet();
            return super.get(key);
        }
    }

    /** Counts candidate reads and membership-buffer rebuilds. @author howxu <dev@howxu.cn> */
    private static final class CountingCandidateList<E> extends ArrayList<E> {
        private final AtomicInteger reads;
        private final AtomicInteger rebuilds = new AtomicInteger();
        private CountingCandidateList(List<E> initial, AtomicInteger reads) { super(initial); this.reads = reads; }
        @Override public E get(int index) { reads.incrementAndGet(); return super.get(index); }
        @Override public void clear() { rebuilds.incrementAndGet(); super.clear(); }
    }

    @SuppressWarnings("unchecked")
    private static CountingCandidateList<Object> countCandidateList(Object owner, String name, AtomicInteger reads) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        var counted = new CountingCandidateList<>((List<Object>) field.get(owner), reads);
        field.set(owner, counted);
        return counted;
    }

    /** Measures actual iterator construction on the live indexes. @author howxu <dev@howxu.cn> */
    private static final class CountingIterationSet<E> extends HashSet<E> {
        private final AtomicInteger iterators = new AtomicInteger();
        private CountingIterationSet(Set<E> initial) { super(initial); }
        @Override public Iterator<E> iterator() { iterators.incrementAndGet(); return super.iterator(); }
    }

    @SuppressWarnings("unchecked")
    private static CountingIterationSet<Object> countSetIterations(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        var counted = new CountingIterationSet<>((Set<Object>) field.get(owner));
        field.set(owner, counted);
        return counted;
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void stable_qualification_does_not_scan_lanes_or_dirty_cached_post(MachineWorkMode mode) throws Exception {
        InstalledFixture fixture = installedFixture(mode, 40);
        Field field = SharedIoCoordinator.class.getDeclaredField("executionsByController");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var controllers = (Long2ObjectOpenHashMap<Set<Object>>) field.get(fixture.shared());
        var lanes = new CountingIterationSet<>(controllers.get(fixture.controller().getBlockPos().asLong()));
        controllers.put(fixture.controller().getBlockPos().asLong(), lanes);
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.shared().markControllerEligible(fixture.controller(), fixture.level().getGameTime());
        fixture.shared().markControllerEligible(fixture.controller(), fixture.level().getGameTime());
        assertThat(lanes.iterators).hasValue(0);
        fixture.runtime().prepareInstalledTick(fixture.shared().executionRoundId());
        if (mode == MachineWorkMode.SYNC) assertThat(fixture.shared().executeInstalled(fixture.program(), fixture.level())).isTrue();
        fixture.shared().resolvePost(fixture.level());
        long after = fixture.input().energyStorage().getAmountAsLong();
        CountingDomainMap domains = countDomainLookups(StructureClaimRegistry.get(fixture.level()));
        fixture.shared().markControllerEligible(fixture.controller(), fixture.level().getGameTime());
        assertThat(fixture.shared().resolvePost(fixture.level())).isZero();
        assertThat(lanes.iterators).hasValue(0);
        assertThat(domains.lookups).hasValue(0);
        assertThat(fixture.runtime().tickCount()).isEqualTo(1);
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(after);
    }

    @ParameterizedTest
    @EnumSource(value = MachineWorkMode.class, names = {"SEMI_SYNC", "ASYNC"})
    void stable_thread_tick_reuses_the_binding_without_repeating_admission_or_live_domain_queries(MachineWorkMode mode) throws Exception {
        InstalledFixture fixture = installedFixture(mode, 40);
        MachineRecipeThread thread = new MachineRecipeThread(fixture.controller(), fixture.runtime());
        Field field = SharedIoCoordinator.class.getDeclaredField("executionsByController");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var controllers = (Long2ObjectOpenHashMap<Set<Object>>) field.get(fixture.shared());
        var lanes = new CountingIterationSet<>(controllers.get(fixture.controller().getBlockPos().asLong()));
        controllers.put(fixture.controller().getBlockPos().asLong(), lanes);
        CountingDomainMap domains = countDomainLookups(StructureClaimRegistry.get(fixture.level()));
        long revision = fixture.shared().installedRevision(fixture.runtime(), "base");
        long afterStart = fixture.input().energyStorage().getAmountAsLong();
        for (int tick = 1; tick <= 5; tick++) {
            RuntimeTestFixtures.advanceGameTime(fixture.level());
            RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
            fixture.shared().markControllerEligible(fixture.controller(), fixture.level().getGameTime());
            domains.lookups.set(0);
            thread.tick();
            assertThat(domains.lookups.get()).isLessThanOrEqualTo(4);
            assertThat(lanes.iterators).hasValue(0);
            assertThat(fixture.shared().installedRevision(fixture.runtime(), "base")).isEqualTo(revision);
            assertThat(fixture.shared().resolvePost(fixture.level())).isEqualTo(1);
            assertThat(fixture.runtime().installedProgram()).isSameAs(fixture.program());
            assertThat(fixture.runtime().tickCount()).isEqualTo(tick);
            assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(afterStart - 7L * tick);
        }
    }

    @Test
    void refresh_only_accepts_a_live_existing_binding_and_the_original_controller_permit() {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 40);
        long revision = fixture.shared().installedRevision(fixture.runtime(), "base");
        var permit = fixture.shared().controllerPermit(fixture.controller().getBlockPos());
        assertThat(fixture.shared().refreshExecution(fixture.program(), permit)).isTrue();
        assertThat(fixture.shared().installedRevision(fixture.runtime(), "base")).isEqualTo(revision);
        assertThat(fixture.shared().refreshExecution(fixture.program(),
                new SharedIoCoordinator().controllerPermit(fixture.controller().getBlockPos()))).isFalse();

        CraftingRuntime lane = new CraftingRuntime(fixture.controller(), fixture.controller().componentRuntime());
        lane.bindRecipeLane("uninstalled", () -> { });
        assertThat(lane.start(fixture.recipe(), 1L).isCrafting()).isTrue();
        assertThat(fixture.shared().refreshExecution(lane.ensureInstalledProgram(), permit)).isFalse();
        fixture.controller().componentRuntime().replaceComponents(List.of());
        long before = fixture.input().energyStorage().getAmountAsLong();
        assertThat(fixture.shared().refreshExecution(fixture.program(), permit)).isFalse();
        assertThat(fixture.shared().executeInstalled(fixture.level())).isZero();
        assertThat(fixture.runtime().tickCount()).isZero();
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(before);
    }

    @Test
    void finish_only_gate_rejects_a_running_lane_without_reading_live_domains() throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 40);
        CountingDomainMap domains = countDomainLookups(StructureClaimRegistry.get(fixture.level()));
        assertThat(fixture.program().finishAttemptReady()).isFalse();
        assertThat(domains.lookups).hasValue(0);
        // The live seam still sees invalidation; only the impossible finish phase was excluded.
        fixture.controller().componentRuntime().replaceComponents(List.of());
        assertThat(fixture.program().current()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = MachineWorkMode.class, names = {"SEMI_SYNC", "ASYNC"})
    void late_ticker_restores_rejected_candidates_without_replaying_paid_lanes(MachineWorkMode mode) {
        InstalledFixture fixture = installedFixture(mode, 40);
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.runtime().prepareInstalledTick(fixture.shared().executionRoundId());
        long before = fixture.input().energyStorage().getAmountAsLong();
        assertThat(fixture.shared().resolvePost(fixture.level())).isZero();
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(before);
        fixture.shared().markControllerEligible(fixture.controller(), fixture.level().getGameTime());
        assertThat(fixture.shared().resolvePost(fixture.level())).isEqualTo(1);
        fixture.shared().markControllerEligible(fixture.controller(), fixture.level().getGameTime());
        assertThat(fixture.shared().resolvePost(fixture.level())).isZero();
        assertThat(fixture.runtime().tickCount()).isEqualTo(1);
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(before - 7L);
    }

    @ParameterizedTest
    @EnumSource(value = MachineWorkMode.class, names = {"SEMI_SYNC", "ASYNC"})
    void cancellation_during_a_drain_does_not_skip_the_next_lane(MachineWorkMode mode) {
        InstalledFixture fixture = installedFixture(mode, 40);
        List<CraftingRuntime> added = new ArrayList<>();
        for (String name : List.of("middle", "z-last")) {
            CraftingRuntime lane = new CraftingRuntime(fixture.controller(), fixture.controller().componentRuntime());
            lane.bindRecipeLane(name, () -> { });
            assertThat(fixture.shared().reserveExecution(lane, name)).isTrue();
            assertThat(lane.start(fixture.recipe(), 1L).isCrafting()).isTrue();
            assertThat(fixture.shared().install(lane.ensureInstalledProgram(),
                    fixture.shared().controllerPermit(fixture.controller().getBlockPos()))).isTrue();
            added.add(lane);
        }
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.shared().markControllerEligible(fixture.controller(), fixture.level().getGameTime());
        fixture.runtime().prepareInstalledTick(fixture.shared().executionRoundId());
        for (CraftingRuntime lane : added) lane.prepareInstalledTick(fixture.shared().executionRoundId());
        fixture.shared().enqueue(new SharedIoCoordinator.TickRequest(fixture.controller().resourceDomain(),
                new SharedIoCoordinator.LaneKey(fixture.controller().getBlockPos(), ""), 1L, 0L,
                () -> { added.getFirst().installedProgram().cancel(); return true; },
                () -> true, () -> 1L, () -> 0L));
        long before = fixture.input().energyStorage().getAmountAsLong();

        assertThat(fixture.shared().resolvePost(fixture.level())).isEqualTo(3);
        assertThat(fixture.runtime().tickCount()).isEqualTo(1);
        assertThat(added.getFirst().tickCount()).isZero();
        assertThat(added.getLast().tickCount()).isEqualTo(1);
        assertThat(fixture.shared().receiptsForTesting(added.getFirst(), "middle")).singleElement()
                .satisfies(receipt -> assertThat(receipt.terminal()).isTrue());
        assertThat(fixture.shared().resolvePost(fixture.level())).isZero();
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(before - 14L);
    }

    @ParameterizedTest
    @EnumSource(value = MachineWorkMode.class, names = {"SEMI_SYNC", "ASYNC"})
    void retired_lane_rename_before_install_preserves_the_budgeted_successor_order(MachineWorkMode mode) {
        InstalledFixture fixture = installedFixture(mode, 40, 7L, 2);
        List<CraftingRuntime> added = new ArrayList<>();
        for (String name : List.of("middle", "z-old", "z-other")) {
            CraftingRuntime lane = new CraftingRuntime(fixture.controller(), fixture.controller().componentRuntime());
            lane.bindRecipeLane(name, () -> { });
            assertThat(fixture.shared().reserveExecution(lane, name)).isTrue();
            assertThat(lane.start(fixture.recipe(), 1L).isCrafting()).isTrue();
            assertThat(fixture.shared().install(lane.ensureInstalledProgram(),
                    fixture.shared().controllerPermit(fixture.controller().getBlockPos()))).isTrue();
            added.add(lane);
        }
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.shared().markControllerEligible(fixture.controller(), fixture.level().getGameTime());
        fixture.runtime().prepareInstalledTick(fixture.shared().executionRoundId());
        for (CraftingRuntime lane : added) lane.prepareInstalledTick(fixture.shared().executionRoundId());
        CraftingRuntime replacement = new CraftingRuntime(fixture.controller(), fixture.controller().componentRuntime());
        replacement.bindRecipeLane("a-new", () -> { });
        fixture.shared().enqueue(new SharedIoCoordinator.TickRequest(fixture.controller().resourceDomain(),
                new SharedIoCoordinator.LaneKey(fixture.controller().getBlockPos(), "base"), 1L, 0L,
                () -> {
                    fixture.shared().forgetExecutionLane(added.get(1), "z-old");
                    assertThat(fixture.shared().reserveExecution(replacement, "a-new")).isTrue();
                    return true;
                }, () -> true, () -> 1L, () -> 0L));
        long before = fixture.input().energyStorage().getAmountAsLong();

        assertThat(fixture.shared().resolvePost(fixture.level())).isEqualTo(2);
        assertThat(fixture.runtime().tickCount()).isZero();
        assertThat(added.getFirst().tickCount()).isEqualTo(1);
        assertThat(added.getLast().tickCount()).isZero();
        assertThat(fixture.shared().receiptsForTesting(added.get(1), "z-old")).singleElement()
                .satisfies(receipt -> assertThat(receipt.terminal()).isTrue());
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(before - 7L);
        assertThat(fixture.shared().resolvePost(fixture.level())).isZero();
    }

    /** Observes full active-record scans without changing identity lookup. @author howxu <dev@howxu.cn> */
    private static final class CountingIterationMap<K, V> extends IdentityHashMap<K, V> {
        private final AtomicInteger visits = new AtomicInteger();
        private CountingIterationMap(Map<K, V> initial) { super(initial); }
        @Override public Collection<V> values() {
            Collection<V> values = super.values();
            return new AbstractCollection<>() {
                @Override public int size() { return values.size(); }
                @Override public Iterator<V> iterator() {
                    Iterator<V> iterator = values.iterator();
                    return new Iterator<>() {
                        @Override public boolean hasNext() { return iterator.hasNext(); }
                        @Override public V next() {
                            visits.incrementAndGet();
                            return iterator.next();
                        }
                    };
                }
            };
        }
    }

    @Test
    void independent_domains_do_not_rescan_exhausted_domains_for_each_remaining_lane() throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 100);
        List<CraftingRuntime> runtimes = new ArrayList<>(List.of(fixture.runtime()));
        List<EnergyInputHatchBlockEntity> inputs = new ArrayList<>(List.of(fixture.input()));
        for (int index = 1; index <= 16; index++) {
            BlockPos position = new BlockPos(index * 8, 0, 0);
            var controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), position);
            var input = RuntimeTestFixtures.energyInput(position.offset(1, 0, 0));
            RuntimeTestFixtures.formStructureWithComponents(controller, new DynamicMachine(MMCR.id("test_cube"),
                    "independent domain", new BlockArray(Map.of())), input);
            installedLevels.add((ServerLevel) controller.getLevel());
            RuntimeTestFixtures.replaceBlockEntity(fixture.controller(), controller);
            RuntimeTestFixtures.replaceBlockEntity(fixture.controller(), input);
            fixture.level().setBlock(position, controller.getBlockState(), 3);
            fixture.level().setBlock(input.getBlockPos(), input.getBlockState(), 3);
            controller.componentRuntime().replaceComponents(List.of(new ProcessingComponent(
                    new MachineComponent(input.kind(), input.ioType()), input, input.getBlockPos(), input.getBlockPos(), (String) null)));
            RuntimeTestFixtures.republish(controller);
            assertThat(StructureClaimRegistry.get(fixture.level()).claim(position, List.of()).accepted()).isTrue();
            fixture.shared().markControllerEligible(controller, fixture.level().getGameTime());
            input.energyStorage().setAmount(1_000L);
            CraftingRuntime runtime = RuntimeTestFixtures.craftingRuntime(controller);
            assertThat(fixture.shared().reserveExecution(runtime, "base")).isTrue();
            assertThat(runtime.start(fixture.recipe(), 1L).isCrafting()).isTrue();
            assertThat(fixture.shared().install(runtime.ensureInstalledProgram(), fixture.shared().controllerPermit(position))).isTrue();
            runtimes.add(runtime);
            inputs.add(input);
        }
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        for (CraftingRuntime runtime : runtimes) {
            fixture.shared().markControllerEligible(runtime.controller(), fixture.level().getGameTime());
            assertThat(runtime.prepareInstalledTick(fixture.shared().executionRoundId())).isTrue();
        }
        var backlogDomain = fixture.controller().resourceDomain();
        AtomicInteger unexpectedCommits = new AtomicInteger();
        for (int index = 0; index < 32; index++) {
            fixture.shared().enqueue(tick(backlogDomain, new BlockPos(256 + index, 0, 0), 1L,
                    () -> { unexpectedCommits.incrementAndGet(); return true; }, () -> true, () -> 1L));
        }
        Field domainsField = SharedIoCoordinator.class.getDeclaredField("domains");
        domainsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        TreeMap<Object, Object> original = (TreeMap<Object, Object>) domainsField.get(fixture.shared());
        AtomicInteger reads = new AtomicInteger();
        var counted = new CountingTreeMap<Object, Object>(original.comparator(), reads);
        counted.putAll(original);
        domainsField.set(fixture.shared(), counted);
        List<Long> before = inputs.stream().map(input -> input.energyStorage().getAmountAsLong()).toList();

        assertThat(fixture.shared().resolvePost(fixture.level())).isEqualTo(runtimes.size());
        for (int index = 0; index < runtimes.size(); index++) {
            assertThat(runtimes.get(index).tickCount()).isEqualTo(1);
            assertThat(inputs.get(index).energyStorage().getAmountAsLong()).isEqualTo(before.get(index) - 7L);
        }
        assertThat(reads.get()).isLessThanOrEqualTo(4 * runtimes.size());
        assertThat(unexpectedCommits).hasValue(0);
        assertThat(fixture.shared().pendingRequestCountForTesting()).isEqualTo(32);
    }

    @Test
    void sleeping_lane_domain_invalidation_publishes_terminal_while_the_level_is_frozen() {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 40);
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.shared().markControllerEligible(fixture.controller(), fixture.level().getGameTime());
        fixture.runtime().prepareInstalledTick(fixture.shared().executionRoundId());
        fixture.input().energyStorage().setAmount(0L);
        long paidAttempts = fixture.program().ledger().state().paidAttempts();
        assertThat(fixture.shared().resolvePost(fixture.level())).isZero();
        assertThat(fixture.shared().receiptsForTesting(fixture.runtime(), "base")).singleElement()
                .satisfies(receipt -> assertThat(receipt.terminal()).isFalse());
        StructureClaimRegistry.get(fixture.level()).release(fixture.controller().getBlockPos());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), false);
        assertThat(fixture.shared().resolvePost(fixture.level())).isZero();
        assertThat(fixture.shared().receiptsForTesting(fixture.runtime(), "base")).singleElement()
                .satisfies(receipt -> {
                    assertThat(receipt.terminal()).isTrue();
                    assertThat(receipt.paidAttempts()).isEqualTo(paidAttempts);
                });
        assertThat(fixture.program().current()).isFalse();
        assertThat(fixture.shared().resolvePost(fixture.level())).isZero();
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isZero();
    }

    @Test
    void cached_post_still_publishes_a_paid_lane_cancellation_without_resource_replay() {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 40);
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.shared().markControllerEligible(fixture.controller(), fixture.level().getGameTime());
        fixture.runtime().prepareInstalledTick(fixture.shared().executionRoundId());
        assertThat(fixture.shared().resolvePost(fixture.level())).isEqualTo(1);
        long afterPaid = fixture.input().energyStorage().getAmountAsLong();
        long paidAttempts = fixture.program().ledger().state().paidAttempts();
        fixture.program().cancel();
        assertThat(fixture.shared().resolvePost(fixture.level())).isZero();
        assertThat(fixture.shared().receiptsForTesting(fixture.runtime(), "base")).singleElement()
                .satisfies(receipt -> {
                    assertThat(receipt.terminal()).isTrue();
                    assertThat(receipt.paidAttempts()).isEqualTo(paidAttempts);
                });
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(afterPaid);
    }

    @Test
    void cached_post_observes_confirmed_native_output_payment_but_not_simulation_as_terminal() {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 40, 7L, 1, true);
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.shared().markControllerEligible(fixture.controller(), fixture.level().getGameTime());
        fixture.runtime().prepareInstalledTick(fixture.shared().executionRoundId());
        assertThat(fixture.shared().resolvePost(fixture.level())).isEqualTo(1);
        long afterPaid = fixture.input().energyStorage().getAmountAsLong();
        long paidAttempts = fixture.program().ledger().state().paidAttempts();
        var beforeOutput = fixture.program().ledger().state();
        assertThat(fixture.output().itemHandler().insertItem(0, new ItemStack(Items.IRON_NUGGET), true).isEmpty()).isTrue();
        assertThat(fixture.output().itemHandler().amount(0)).isZero();
        assertThat(fixture.program().ledger().state()).isEqualTo(beforeOutput);
        assertThat(fixture.shared().resolvePost(fixture.level())).isZero();
        assertThat(fixture.shared().receiptsForTesting(fixture.runtime(), "base")).singleElement()
                .satisfies(receipt -> assertThat(receipt.terminal()).isFalse());
        assertThat(fixture.program().current()).isTrue();
        assertThat(fixture.output().itemHandler().insertItem(0, new ItemStack(Items.IRON_NUGGET), false).isEmpty()).isTrue();
        fixture.program().ledger().markOutputPaid();
        assertThat(fixture.shared().resolvePost(fixture.level())).isZero();
        Receipt terminal = fixture.shared().receiptsForTesting(fixture.runtime(), "base").getFirst();
        assertThat(terminal.terminal()).isTrue();
        assertThat(terminal.paidAttempts()).isEqualTo(paidAttempts);
        assertThat(fixture.program().current()).isFalse();
        assertThat(fixture.shared().acknowledgeReceipt(new Acknowledgement(terminal.identity(), terminal.installedRevision(),
                terminal.sequence()))).isTrue();
        assertThat(fixture.shared().receiptsForTesting(fixture.runtime(), "base")).isEmpty();
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(afterPaid);
        assertThat(fixture.shared().resolvePost(fixture.level())).isZero();
        assertThat(fixture.output().itemHandler().amount(0)).isEqualTo(1L);
    }

    @Test
    void unattempted_lane_still_validates_when_another_lane_already_paid_this_tick() throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 40);
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.controller().serverTick();
        fixture.shared().resolve(fixture.level());
        assertThat(fixture.runtime().tickCount()).isEqualTo(1);
        CraftingRuntime other = new CraftingRuntime(fixture.controller(), fixture.controller().componentRuntime());
        other.bindRecipeLane("unattempted", () -> { });
        assertThat(fixture.shared().reserveExecution(other, "unattempted")).isTrue();
        assertThat(other.start(fixture.recipe(), 1L).isCrafting()).isTrue();
        assertThat(fixture.shared().install(other.ensureInstalledProgram(),
                fixture.shared().controllerPermit(fixture.controller().getBlockPos()))).isTrue();
        assertThat(other.prepareInstalledTick(fixture.shared().executionRoundId())).isTrue();
        long afterStarts = fixture.input().energyStorage().getAmountAsLong();
        var domain = fixture.controller().resourceDomain();
        CountingDomainMap domains = countDomainLookups(StructureClaimRegistry.get(fixture.level()));
        CountingLookupMap<BlockPos, BlockEntity> world = countLookups(fixture.level(), "blockEntities");

        assertThat(resolveInstalledDomain(fixture, domain)).isEqualTo(1);

        assertThat(domains.lookups.get()).isGreaterThan(0);
        assertThat(world.lookups.get()).isGreaterThan(0);
        assertThat(other.tickCount()).isEqualTo(1);
        assertThat(fixture.runtime().tickCount()).isEqualTo(1);
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(afterStarts - 7L);
    }

    @ParameterizedTest
    @EnumSource(value = RecipeCommitLedger.Phase.class, names = {"READY", "ERROR_PAUSED"})
    void invalid_unattempted_execution_is_cancelled_before_phase_gates(RecipeCommitLedger.Phase phase) throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 40);
        if (phase == RecipeCommitLedger.Phase.ERROR_PAUSED) fixture.program().ledger().pauseAfterCommitFailure();
        fixture.program().cancel();
        long before = fixture.input().energyStorage().getAmountAsLong();
        long paidAttempts = fixture.program().ledger().state().paidAttempts();
        Field remaining = SharedIoCoordinator.class.getDeclaredField("remainingRequests");
        remaining.setAccessible(true);
        int budget = remaining.getInt(fixture.shared());

        assertThat(fixture.shared().resolve(fixture.level())).isZero();

        assertThat(fixture.shared().receiptsForTesting(fixture.runtime(), "base")).singleElement()
                .satisfies(receipt -> {
                    assertThat(receipt.terminal()).isTrue();
                    assertThat(receipt.paidAttempts()).isEqualTo(paidAttempts);
                });
        assertThat(remaining.getInt(fixture.shared())).isEqualTo(budget);
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(before);
    }

    @Test
    void output_paid_terminates_a_slot_before_the_same_tick_attempt_fence() {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 40);
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.controller().serverTick();
        fixture.shared().resolve(fixture.level());
        assertThat(fixture.runtime().tickCount()).isEqualTo(1);
        long afterPaid = fixture.input().energyStorage().getAmountAsLong();
        long paidAttempts = fixture.program().ledger().state().paidAttempts();
        fixture.program().ledger().markOutputPaid();

        assertThat(fixture.shared().resolve(fixture.level())).isZero();

        assertThat(fixture.program().current()).isFalse();
        assertThat(fixture.shared().receiptsForTesting(fixture.runtime(), "base")).singleElement()
                .satisfies(receipt -> {
                    assertThat(receipt.terminal()).isTrue();
                    assertThat(receipt.paidAttempts()).isEqualTo(paidAttempts);
                });
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(afterPaid);
    }

    @Test
    void paid_lane_does_not_hide_a_same_tick_domain_removal() {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 40);
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.controller().serverTick();
        fixture.shared().resolve(fixture.level());
        long afterPaid = fixture.input().energyStorage().getAmountAsLong();
        long paidAttempts = fixture.program().ledger().state().paidAttempts();
        StructureClaimRegistry.get(fixture.level()).release(fixture.controller().getBlockPos());

        assertThat(fixture.shared().resolve(fixture.level())).isZero();

        assertThat(fixture.program().current()).isFalse();
        assertThat(fixture.shared().receiptsForTesting(fixture.runtime(), "base")).singleElement()
                .satisfies(receipt -> {
                    assertThat(receipt.terminal()).isTrue();
                    assertThat(receipt.paidAttempts()).isEqualTo(paidAttempts);
                });
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(afterPaid);
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void paid_lane_cancellation_publishes_terminal_facts_in_a_frozen_same_tick(MachineWorkMode mode) throws Exception {
        InstalledFixture fixture = installedFixture(mode, 40);
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.controller().serverTick();
        fixture.shared().resolve(fixture.level());
        assertThat(fixture.runtime().tickCount()).isEqualTo(1);
        long afterPaid = fixture.input().energyStorage().getAmountAsLong();
        long paidAttempts = fixture.program().ledger().state().paidAttempts();
        fixture.program().cancel();
        RuntimeTestFixtures.beginLevelTick(fixture.level(), false);
        var domain = fixture.controller().resourceDomain();

        if (mode == MachineWorkMode.SYNC) {
            assertThat(fixture.shared().executeInstalled(fixture.program(), fixture.level())).isFalse();
        }
        assertThat(resolveInstalledDomain(fixture, domain)).isZero();
        assertThat(fixture.shared().receiptsForTesting(fixture.runtime(), "base")).singleElement()
                .satisfies(receipt -> {
                    assertThat(receipt.terminal()).isTrue();
                    assertThat(receipt.paidAttempts()).isEqualTo(paidAttempts);
                });
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(afterPaid);
    }

    private int resolveInstalledDomain(InstalledFixture fixture, StructureClaimRegistry.ResourceDomain domain) throws Exception {
        // Exercise the live arbitration loop separately from its mandatory topology reconciliation.
        var method = SharedIoCoordinator.class.getDeclaredMethod("resolveKnownDomains", Map.class, boolean.class, ServerLevel.class);
        method.setAccessible(true);
        return (int) method.invoke(fixture.shared(), Map.of(domain.id(), domain), true, fixture.level());
    }

    @SuppressWarnings("unchecked")
    private static CountingDomainMap countDomainLookups(StructureClaimRegistry registry) throws Exception {
        Field field = StructureClaimRegistry.class.getDeclaredField("domainsByController");
        field.setAccessible(true);
        CountingDomainMap counted = new CountingDomainMap((Long2ObjectOpenHashMap<StructureClaimRegistry.ResourceDomain>) field.get(registry));
        field.set(registry, counted);
        return counted;
    }

    /** Counts live packed-position domain queries. @author howxu <dev@howxu.cn> */
    private static final class CountingDomainMap extends Long2ObjectOpenHashMap<StructureClaimRegistry.ResourceDomain> {
        private final AtomicInteger lookups = new AtomicInteger();

        private CountingDomainMap(Long2ObjectOpenHashMap<StructureClaimRegistry.ResourceDomain> initial) { super(initial); }

        @Override public StructureClaimRegistry.ResourceDomain get(long key) {
            lookups.incrementAndGet();
            return super.get(key);
        }
    }

    @SuppressWarnings("unchecked")
    private static <K, V> CountingLookupMap<K, V> countLookups(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        CountingLookupMap<K, V> counted = new CountingLookupMap<>((Map<K, V>) field.get(target));
        field.set(target, counted);
        return counted;
    }

    /** Observes real registry/world reads without replacing their lookup results. @author howxu <dev@howxu.cn> */
    private static final class CountingLookupMap<K, V> extends HashMap<K, V> {
        private final AtomicInteger lookups = new AtomicInteger();

        private CountingLookupMap(Map<K, V> initial) { super(initial); }

        @Override public V get(Object key) {
            lookups.incrementAndGet();
            return super.get(key);
        }
    }

    @Test
    void original_terminal_slots_backpressure_the_third_start_and_only_matching_ack_releases_one() {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 1);
        completeInstalledFixture(fixture);
        Receipt first = fixture.shared().receiptsForTesting(fixture.runtime(), "base").getFirst();
        assertThat(first.terminal()).isTrue();
        assertThat(fixture.shared().reserveExecution(fixture.runtime(), "base")).isTrue();
        fixture.runtime().start(fixture.recipe(), 1L);
        InstalledRecipeProgram second = fixture.runtime().ensureInstalledProgram();
        assertThat(fixture.shared().install(second, fixture.shared().controllerPermit(fixture.controller().getBlockPos()))).isTrue();
        long afterSecondStart = fixture.input().energyStorage().getAmountAsLong();
        fixture.shared().executeInstalled(fixture.level());
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(afterSecondStart);
        completeInstalledFixture(fixture);
        assertThat(fixture.shared().receiptsForTesting(fixture.runtime(), "base")).hasSize(2);
        assertThat(fixture.shared().reserveExecution(fixture.runtime(), "base")).isFalse();
        assertThat(fixture.shared().acknowledgeReceipt(new Acknowledgement(first.identity(),
                first.installedRevision(), first.sequence() - 1L))).isFalse();
        assertThat(fixture.shared().acknowledgeReceipt(new Acknowledgement(first.identity(),
                first.installedRevision(), first.sequence()))).isTrue();
        assertThat(fixture.shared().reserveExecution(fixture.runtime(), "base")).isTrue();
        assertThat(fixture.shared().acknowledgeReceipt(new Acknowledgement(first.identity(),
                first.installedRevision(), first.sequence()))).isFalse();
        assertThat(fixture.shared().receiptsForTesting(fixture.runtime(), "base")).singleElement()
                .satisfies(receipt -> assertThat(receipt.identity()).isEqualTo(second.identity()));
    }

    @Test
    void terminal_drain_uses_the_controlled_executor_and_unblocks_slots_without_worker_world_access() {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 1);
        completeInstalledFixture(fixture);
        MachineAsyncCoordinator async = MachineAsyncCoordinator.get(fixture.level());
        async.pumpReceiptDrains();
        assertThat(fixture.workers()).hasSize(1);
        fixture.workers().removeFirst().run();
        fixture.shared().drainReceiptAcknowledgements(fixture.level());
        assertThat(fixture.shared().receiptsForTesting(fixture.runtime(), "base")).isEmpty();
        assertThat(fixture.shared().reserveExecution(fixture.runtime(), "base")).isTrue();
    }

    @Test
    void healthy_receipt_worker_with_workset_one_allows_two_independent_active_controllers() {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 40, 7L, 16, false, 1);
        var otherInput = RuntimeTestFixtures.energyInput(new BlockPos(21, 0, 0));
        otherInput.energyStorage().setAmount(1_000L);
        CraftingRuntime other = attachInstalledController(fixture, new BlockPos(20, 0, 0), otherInput, false);
        assertThat(other.controller().resourceDomain()).isNotEqualTo(fixture.controller().resourceDomain());
        long firstStart = fixture.input().energyStorage().getAmountAsLong();
        long otherStart = otherInput.energyStorage().getAmountAsLong();
        MachineAsyncCoordinator async = MachineAsyncCoordinator.get(fixture.level());
        for (int tick = 1; tick <= 3; tick++) {
            RuntimeTestFixtures.advanceGameTime(fixture.level());
            RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
            fixture.controller().serverTick();
            other.controller().serverTick();
            fixture.shared().executeInstalled(fixture.level());
            async.pumpReceiptDrains();
            while (!fixture.workers().isEmpty()) fixture.workers().removeFirst().run();
            async.pumpMainThreadSteps();
            fixture.shared().drainReceiptAcknowledgements(fixture.level());
            assertThat(fixture.runtime().active()).isTrue();
            assertThat(other.active()).isTrue();
            assertThat(fixture.runtime().tickCount()).isEqualTo(tick);
            assertThat(other.tickCount()).isEqualTo(tick);
            assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(firstStart - 7L * tick);
            assertThat(otherInput.energyStorage().getAmountAsLong()).isEqualTo(otherStart - 7L * tick);
        }
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void error_paused_cancel_and_terminal_ack_reset_the_reused_slot_without_resetting_the_lane_fence(
            MachineWorkMode mode) throws Exception {
        InstalledFixture fixture = installedFixture(mode, 40);
        AtomicBoolean failNotification = new AtomicBoolean(true);
        Field notification = LongEnergyStorage.class.getDeclaredField("onChange");
        notification.setAccessible(true);
        var storage = fixture.input().energyStorage();
        Runnable original = (Runnable) notification.get(storage);
        notification.set(storage, (Runnable) () -> {
            original.run();
            if (failNotification.get()) throw new IllegalStateException("paid tick notification");
        });
        long beforeFailure = storage.getAmountAsLong();
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.controller().serverTick();
        fixture.shared().executeInstalled(fixture.level());
        assertThat(fixture.runtime().ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.ERROR_PAUSED);
        assertThat(fixture.program().program().requiresMainThreadPreparation()).isFalse();
        long afterFailure = storage.getAmountAsLong();
        assertThat(afterFailure).isEqualTo(beforeFailure - 7L);
        assertThat(fixture.runtime().tickCount()).isZero();
        fixture.runtime().invalidate();
        fixture.shared().forgetExecutionLane(fixture.runtime(), "base");
        Receipt terminal = fixture.shared().receiptsForTesting(fixture.runtime(), "base").getFirst();
        assertThat(terminal.terminal()).isTrue();
        assertThat(fixture.shared().acknowledgeReceipt(new Acknowledgement(terminal.identity(),
                terminal.installedRevision() - 1L, terminal.sequence()))).isFalse();
        assertThat(fixture.shared().acknowledgeReceipt(new Acknowledgement(terminal.identity(),
                terminal.installedRevision(), terminal.sequence() - 1L))).isFalse();
        MachineAsyncCoordinator async = MachineAsyncCoordinator.get(fixture.level());
        async.pumpReceiptDrains();
        while (!fixture.workers().isEmpty()) fixture.workers().removeFirst().run();
        fixture.shared().drainReceiptAcknowledgements(fixture.level());
        assertThat(fixture.shared().receiptsForTesting(fixture.runtime(), "base")).isEmpty();
        failNotification.set(false);
        MachineRecipe replacementRecipe = RecipeTestSupport.create(MMCR.id("after_error_pause"), MMCR.id("test_cube"),
                40, List.of(new EnergyRequirement(7L)), List.of());
        assertThat(fixture.runtime().start(replacementRecipe, 1L).isCrafting()).isTrue();
        InstalledRecipeProgram replacement = fixture.runtime().ensureInstalledProgram();
        assertThat(replacement.identity()).isNotEqualTo(terminal.identity());
        assertThat(fixture.shared().install(replacement,
                fixture.shared().controllerPermit(fixture.controller().getBlockPos()))).isTrue();
        long afterStart = storage.getAmountAsLong();
        assertThat(afterStart).isEqualTo(afterFailure);
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true); // New round, same gameTime.
        fixture.controller().serverTick();
        fixture.shared().executeInstalled(fixture.level());
        assertThat(storage.getAmountAsLong()).isEqualTo(afterStart);
        assertThat(fixture.runtime().tickCount()).isZero();
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.controller().serverTick();
        fixture.shared().executeInstalled(fixture.level());
        assertThat(fixture.runtime().tickCount()).isEqualTo(1);
        assertThat(storage.getAmountAsLong()).isEqualTo(afterStart - 7L);
        assertThat(fixture.shared().acknowledgeReceipt(new Acknowledgement(terminal.identity(),
                terminal.installedRevision(), terminal.sequence()))).isFalse();
        assertThat(replacement.current()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void installed_finish_retry_uses_the_original_deadline_without_empty_charges_or_final_repayment(MachineWorkMode mode) throws Exception {
        InstalledFixture fixture = installedFixture(mode, 1, 7L, 1, true);
        ItemStack filler = new ItemStack(Items.IRON_INGOT);
        filler.set(DataComponents.MAX_STACK_SIZE, 64);
        for (int slot = 0; slot < fixture.output().itemHandler().size(); slot++) {
            assertThat(fixture.output().itemHandler().forceInsert(slot, filler, 64L, false)).isEqualTo(64L);
        }
        long afterStart = fixture.input().energyStorage().getAmountAsLong();
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.controller().serverTick();
        fixture.shared().executeInstalled(fixture.level());
        assertThat(fixture.runtime().finishPending()).isTrue();
        assertNoWorkerResourceTasks(fixture);
        long after = fixture.input().energyStorage().getAmountAsLong();
        assertThat(after).isEqualTo(afterStart - 7L);
        long paidAttempts = fixture.program().ledger().state().paidAttempts();
        fixture.output().itemHandler().forceExtract(0, fixture.output().itemHandler().amount(0), false);
        Field remaining = SharedIoCoordinator.class.getDeclaredField("remainingRequests");
        remaining.setAccessible(true);
        for (int tick = 1; tick < 10; tick++) {
            RuntimeTestFixtures.advanceGameTime(fixture.level());
            RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
            assertThat(fixture.program().finishAttemptReady()).isFalse();
            fixture.controller().serverTick();
            assertThat(fixture.shared().executeInstalled(fixture.program(), fixture.level())).isFalse();
            assertThat(fixture.shared().executeInstalled(fixture.level())).isZero();
            assertThat(remaining.getInt(fixture.shared())).isEqualTo(1);
            assertThat(fixture.runtime().finishPending()).isTrue();
            assertThat(fixture.output().itemHandler().amount(0)).isZero();
        }
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        assertThat(fixture.program().finishAttemptReady()).isTrue();
        fixture.controller().serverTick();
        fixture.shared().executeInstalled(fixture.level());
        assertThat(fixture.runtime().active()).isFalse();
        assertThat(remaining.getInt(fixture.shared())).isZero();
        assertThat(fixture.program().ledger().state().paidAttempts()).isEqualTo(paidAttempts);
        assertThat(fixture.output().itemHandler().resource(0).is(Items.IRON_NUGGET)).isTrue();
        assertThat(fixture.output().itemHandler().amount(0)).isEqualTo(1L);
        assertThat(fixture.shared().executeInstalled(fixture.level())).isZero();
        assertThat(fixture.shared().executeInstalled(fixture.program(), fixture.level())).isFalse();
        assertThat(fixture.output().itemHandler().amount(0)).isEqualTo(1L);
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(after);
        assertNoWorkerResourceTasks(fixture);
    }

    @Test
    void delayed_cancel_revision_and_sequence_cannot_close_the_current_execution() {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 40);
        long revision = fixture.shared().installedRevision(fixture.runtime(), "base");
        ExecutionIdentity identity = fixture.program().identity();
        assertThat(fixture.shared().cancelExecution(identity, revision - 1L, 2L)).isFalse();
        assertThat(fixture.shared().cancelExecution(identity, revision, 1L)).isFalse();
        assertThat(fixture.shared().cancelExecution(new ExecutionIdentity(identity.controllerInstance(),
                identity.lifecycleEpoch(), identity.laneId(), identity.executionId() + 1L), revision, 2L)).isFalse();
        assertThat(fixture.program().current()).isTrue();
        assertThat(fixture.shared().acknowledgeReceipt(new Acknowledgement(identity, revision, 0L))).isFalse();
        assertThat(fixture.shared().cancelExecution(identity, revision, 2L)).isTrue();
        assertThat(fixture.program().current()).isFalse();
        assertThat(fixture.shared().cancelExecution(identity, revision, 3L)).isFalse();
    }

    @Test
    void unsafe_fixture_runs_real_capability_invalidation_when_controller_is_removed() {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 40);
        AtomicInteger invalidations = new AtomicInteger();
        ICapabilityInvalidationListener listener = () -> {
            invalidations.incrementAndGet();
            return false;
        };
        fixture.level().registerCapabilityListener(fixture.controller().getBlockPos(), listener);
        fixture.controller().setRemoved();
        assertThat(invalidations).hasValue(1);
        long before = fixture.input().energyStorage().getAmountAsLong();
        fixture.shared().executeInstalled(fixture.level());
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(before);
        assertThat(fixture.program().current()).isFalse();
    }

    @Test
    void semi_sync_to_sync_same_tick_keeps_paid_facts_and_cannot_attempt_continuous_resources_twice() {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 40);
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.controller().serverTick();
        fixture.shared().executeInstalled(fixture.level());
        long after = fixture.input().energyStorage().getAmountAsLong();
        long paidAttempts = fixture.program().ledger().state().paidAttempts();
        assertThat(fixture.runtime().tickCount()).isEqualTo(1);
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.SYNC);
        fixture.controller().serverTick();
        fixture.shared().executeInstalled(fixture.level());
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(after);
        assertThat(fixture.runtime().tickCount()).isEqualTo(1);
        assertThat(fixture.controller().activeWorkMode()).isEqualTo(MachineWorkMode.SYNC);
        assertThat(fixture.shared().receiptsForTesting(fixture.runtime(), "base"))
                .anySatisfy(receipt -> {
                    assertThat(receipt.identity()).isEqualTo(fixture.program().identity());
                    assertThat(receipt.terminal()).isTrue();
                    assertThat(receipt.paidAttempts()).isEqualTo(paidAttempts);
                });
    }

    @Test
    void failing_shared_controller_yields_the_original_budget_to_the_other_real_store_binding() throws Exception {
        InstalledFixture fixture = installedFixture(MachineWorkMode.SEMI_SYNC, 40, 14L);
        CraftingRuntime secondRuntime = attachSecondInstalledController(fixture);
        MachineControllerBlockEntity second = secondRuntime.controller();
        fixture.input().energyStorage().setAmount(7L);
        Field remaining = SharedIoCoordinator.class.getDeclaredField("remainingRequests");
        remaining.setAccessible(true);
        for (int tick = 0; tick < 2; tick++) {
            RuntimeTestFixtures.advanceGameTime(fixture.level());
            RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
            fixture.controller().serverTick();
            second.serverTick();
            remaining.setInt(fixture.shared(), 1);
            fixture.shared().executeInstalled(fixture.level());
            if (tick == 0) {
                assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(7L);
                assertThat(fixture.runtime().tickCount()).isZero();
            }
        }
        assertThat(secondRuntime.tickCount()).isEqualTo(1);
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isZero();
        assertThat(fixture.runtime().tickCount()).isZero();
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void shared_budget_rotates_before_be_order_can_starve_the_later_controller(MachineWorkMode mode) {
        InstalledFixture fixture = installedFixture(mode, 40, 7L, 1);
        CraftingRuntime secondRuntime = attachSecondInstalledController(fixture);
        long afterStarts = fixture.input().energyStorage().getAmountAsLong();
        for (int tick = 1; tick <= 4; tick++) {
            RuntimeTestFixtures.advanceGameTime(fixture.level());
            RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
            tickInstalledController(fixture.runtime(), fixture);
            tickInstalledController(secondRuntime, fixture);
            if (mode != MachineWorkMode.SYNC) fixture.shared().resolvePost(fixture.level());
            assertThat(fixture.runtime().tickCount() + secondRuntime.tickCount()).isEqualTo(tick);
            assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(afterStarts - tick * 7L);
            fixture.shared().executeInstalled(fixture.level());
            assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(afterStarts - tick * 7L);
        }
        assertThat(fixture.runtime().tickCount()).isEqualTo(2);
        assertThat(secondRuntime.tickCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(value = MachineWorkMode.class, names = {"SEMI_SYNC", "ASYNC"})
    void invalid_candidate_is_terminated_without_consuming_the_valid_lanes_only_budget(MachineWorkMode mode) {
        InstalledFixture fixture = installedFixture(mode, 40, 7L, 1);
        CraftingRuntime second = attachSecondInstalledController(fixture);
        ExecutionIdentity invalid = fixture.runtime().installedProgram().identity();
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        tickInstalledController(fixture.runtime(), fixture);
        tickInstalledController(second, fixture);
        fixture.controller().componentRuntime().replaceComponents(List.of());
        long before = fixture.input().energyStorage().getAmountAsLong();

        assertThat(fixture.shared().resolvePost(fixture.level())).isEqualTo(1);

        assertThat(fixture.runtime().tickCount()).isZero();
        assertThat(second.tickCount()).isEqualTo(1);
        assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(before - 7L);
        assertThat(fixture.shared().receiptsForTesting(fixture.runtime(), "base"))
                .filteredOn(receipt -> receipt.identity().equals(invalid)).singleElement()
                .satisfies(receipt -> assertThat(receipt.terminal()).isTrue());
        assertThat(fixture.shared().resolvePost(fixture.level())).isZero();
    }

    private CraftingRuntime attachSecondInstalledController(InstalledFixture fixture) {
        return attachInstalledController(fixture, new BlockPos(10, 0, 0), fixture.input(), true);
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void budget_rotates_domains_equally_despite_two_controllers_in_one_domain(MachineWorkMode mode) {
        InstalledFixture fixture = installedFixture(mode, 40, 7L, 1);
        CraftingRuntime second = attachSecondInstalledController(fixture);
        var otherInput = RuntimeTestFixtures.energyInput(new BlockPos(21, 0, 0));
        otherInput.energyStorage().setAmount(1_000L);
        CraftingRuntime third = attachInstalledController(fixture, new BlockPos(20, 0, 0), otherInput, false);
        assertThat(second.controller().resourceDomain()).isEqualTo(fixture.controller().resourceDomain());
        assertThat(third.controller().resourceDomain()).isNotEqualTo(fixture.controller().resourceDomain());
        long sharedStart = fixture.input().energyStorage().getAmountAsLong();
        long otherStart = otherInput.energyStorage().getAmountAsLong();
        for (int tick = 1; tick <= 8; tick++) {
            RuntimeTestFixtures.advanceGameTime(fixture.level());
            RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
            tickInstalledController(fixture.runtime(), fixture);
            tickInstalledController(second, fixture);
            tickInstalledController(third, fixture);
            if (mode != MachineWorkMode.SYNC) fixture.shared().resolvePost(fixture.level());
            int sharedTicks = fixture.runtime().tickCount() + second.tickCount();
            assertThat(sharedTicks + third.tickCount()).isEqualTo(tick);
            assertThat(Math.abs(sharedTicks - third.tickCount())).isLessThanOrEqualTo(1);
            assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(sharedStart - 7L * sharedTicks);
            assertThat(otherInput.energyStorage().getAmountAsLong()).isEqualTo(otherStart - 7L * third.tickCount());
            assertThat(fixture.shared().executeInstalled(fixture.level())).isZero();
            assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(sharedStart - 7L * sharedTicks);
            assertThat(otherInput.energyStorage().getAmountAsLong()).isEqualTo(otherStart - 7L * third.tickCount());
        }
        assertThat(fixture.runtime().tickCount()).isEqualTo(2);
        assertThat(second.tickCount()).isEqualTo(2);
        assertThat(third.tickCount()).isEqualTo(4);
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void one_and_four_ready_lanes_use_all_four_budget_slots_and_rotate_lanes(MachineWorkMode mode) {
        InstalledFixture fixture = installedFixture(mode, 40, 7L, 4);
        CraftingRuntime second = attachSecondInstalledController(fixture);
        List<CraftingRuntime> lanes = new ArrayList<>(List.of(second));
        for (int index = 1; index <= 3; index++) {
            CraftingRuntime lane = new CraftingRuntime(second.controller(), second.controller().componentRuntime());
            String laneId = "test-" + index;
            lane.bindRecipeLane(laneId, () -> { });
            assertThat(fixture.shared().reserveExecution(lane, laneId)).isTrue();
            lane.start(second.activeRecipe().getRecipe(), 1L);
            assertThat(lane.active()).isTrue();
            assertThat(fixture.shared().install(lane.ensureInstalledProgram(),
                    fixture.shared().controllerPermit(second.controller().getBlockPos()))).isTrue();
            lanes.add(lane);
        }
        long afterStarts = fixture.input().energyStorage().getAmountAsLong();
        for (int tick = 1; tick <= 4; tick++) {
            RuntimeTestFixtures.advanceGameTime(fixture.level());
            RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
            tickInstalledController(fixture.runtime(), fixture);
            tickInstalledController(second, fixture);
            for (CraftingRuntime lane : lanes) {
                if (mode == MachineWorkMode.SYNC) fixture.shared().executeInstalled(lane.installedProgram(), fixture.level());
                else lane.prepareInstalledTick(fixture.shared().executionRoundId());
            }
            if (mode != MachineWorkMode.SYNC) fixture.shared().resolvePost(fixture.level());
            assertThat(fixture.runtime().tickCount() + lanes.stream().mapToInt(CraftingRuntime::tickCount).sum())
                    .isEqualTo(tick * 4);
            assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(afterStarts - tick * 28L);
            assertThat(fixture.shared().executeInstalled(fixture.level())).isZero();
            for (CraftingRuntime lane : lanes) {
                assertThat(fixture.shared().executeInstalled(lane.installedProgram(), fixture.level())).isFalse();
            }
            assertThat(fixture.input().energyStorage().getAmountAsLong()).isEqualTo(afterStarts - tick * 28L);
        }
        assertThat(fixture.runtime().tickCount()).isEqualTo(4);
        assertThat(lanes).allSatisfy(lane -> assertThat(lane.tickCount()).isEqualTo(3));
    }

    private CraftingRuntime attachInstalledController(InstalledFixture fixture, BlockPos pos,
                                                       EnergyInputHatchBlockEntity input, boolean sharedStore) {
        MachineControllerBlockEntity second = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), pos);
        RuntimeTestFixtures.formStructure(second, new DynamicMachine(MMCR.id("test_cube"),
                "second shared installed fixture", new BlockArray(Map.of())));
        ServerLevel temporaryLevel = (ServerLevel) second.getLevel();
        installedLevels.add(temporaryLevel);
        RuntimeTestFixtures.attachLevel(second, fixture.level());
        RuntimeTestFixtures.replaceBlockEntity(second, second);
        fixture.level().setBlock(second.getBlockPos(), second.getBlockState(), 3);
        if (!sharedStore) {
            RuntimeTestFixtures.replaceBlockEntity(second, input);
            fixture.level().setBlock(input.getBlockPos(), input.getBlockState(), 3);
        }
        second.componentRuntime().replaceComponents(List.of(new ProcessingComponent(
                new MachineComponent(input.kind(), input.ioType()), input,
                input.getBlockPos(), input.getBlockPos(), (String) null)));
        RuntimeTestFixtures.republish(second);
        StructureClaimRegistry registry = StructureClaimRegistry.get(fixture.level());
        var claim = new StructureClaimRegistry.Claim(input.getBlockPos(), ComponentClaimPolicy.SHARED_SERIALIZED);
        if (sharedStore) assertThat(registry.claim(fixture.controller().getBlockPos(), List.of(claim)).accepted()).isTrue();
        assertThat(registry.claim(second.getBlockPos(), sharedStore ? List.of(claim) : List.of()).accepted()).isTrue();
        // Topology changes retire the old binding. Reinstall before the measured round,
        // without ticking either recipe or moving the attempt cursor during fixture setup.
        if (!fixture.controller().resourceDomain().equals(fixture.runtime().resourceDomain())) {
            fixture.shared().releaseExecutionReservation(fixture.runtime(), "base");
            fixture.runtime().rebindCurrentVersions();
            assertThat(fixture.shared().reserveExecution(fixture.runtime(), "base")).isTrue();
            assertThat(fixture.shared().install(fixture.runtime().ensureInstalledProgram(),
                    fixture.shared().controllerPermit(fixture.controller().getBlockPos()))).isTrue();
        }
        fixture.shared().markControllerEligible(second, fixture.level().getGameTime());
        CraftingRuntime secondRuntime = RuntimeTestFixtures.craftingRuntime(second);
        MachineRecipe secondRecipe = RecipeTestSupport.create(MMCR.id("second_shared_installed"), MMCR.id("test_cube"),
                40, List.of(new EnergyRequirement(RecipeModifier.IOType.INPUT, 7L)), List.of());
        secondRuntime.start(secondRecipe, 1L);
        assertThat(secondRuntime.active()).isTrue();
        InstalledRecipeProgram secondProgram = secondRuntime.ensureInstalledProgram();
        assertThat(fixture.shared().install(secondProgram, fixture.shared().controllerPermit(second.getBlockPos()))).isTrue();
        return secondRuntime;
    }

    private void tickInstalledController(CraftingRuntime runtime, InstalledFixture fixture) {
        // Unit-test the installed BE boundary. A shared SYNC controller's full serverTick
        // also queues legacy TickRequests, which are a different arbitration path.
        fixture.shared().markControllerEligible(runtime.controller(), fixture.level().getGameTime());
        if (runtime.controller().activeWorkMode() == MachineWorkMode.SYNC) {
            fixture.shared().executeInstalled(runtime.installedProgram(), fixture.level());
        } else {
            runtime.prepareInstalledTick(fixture.shared().executionRoundId());
        }
    }

    private void assertNoWorkerResourceTasks(InstalledFixture fixture) {
        try {
            Field field = MachineAsyncCoordinator.class.getDeclaredField("tasks");
            field.setAccessible(true);
            // Compilation/resource continuations register TaskKeys; receipt drains do not.
            assertThat((Map<?, ?>) field.get(MachineAsyncCoordinator.get(fixture.level()))).isEmpty();
            Field drainsField = MachineAsyncCoordinator.class.getDeclaredField("receiptDrains");
            drainsField.setAccessible(true);
            Map<?, ?> drains = (Map<?, ?>) drainsField.get(MachineAsyncCoordinator.get(fixture.level()));
            int queuedReceipts = 0;
            for (Object drain : drains.values()) {
                Field queuedField = drain.getClass().getDeclaredField("queued");
                queuedField.setAccessible(true);
                if (((AtomicBoolean) queuedField.get(drain)).get()) queuedReceipts++;
            }
            assertThat(fixture.workers()).hasSize(queuedReceipts);
            assertThat(fixture.shared().pendingTickWorkCountForTesting()).isZero();
            assertThat(fixture.shared().submittedTickWorkCountForTesting()).isZero();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect worker resource tasks", exception);
        }
    }

    private void completeInstalledFixture(InstalledFixture fixture) {
        RuntimeTestFixtures.advanceGameTime(fixture.level());
        RuntimeTestFixtures.beginLevelTick(fixture.level(), true);
        fixture.controller().serverTick();
        fixture.runtime().prepareInstalledTick(fixture.shared().executionRoundId());
        fixture.shared().executeInstalled(fixture.level());
        assertThat(fixture.runtime().active()).isFalse();
    }

    private InstalledFixture installedFixture(MachineWorkMode mode, int duration) {
        return installedFixture(mode, duration, 7L);
    }

    private InstalledFixture installedFixture(MachineWorkMode mode, int duration, long energy) {
        return installedFixture(mode, duration, energy, 0);
    }

    private InstalledFixture installedFixture(MachineWorkMode mode, int duration, long energy, int requestBudget) {
        return installedFixture(mode, duration, energy, requestBudget, false);
    }

    private InstalledFixture installedFixture(MachineWorkMode mode, int duration, long energy, int requestBudget,
                                                boolean finishOutput) {
        return installedFixture(mode, duration, energy, requestBudget, finishOutput, 8);
    }

    private InstalledFixture installedFixture(MachineWorkMode mode, int duration, long energy, int requestBudget,
                                                boolean finishOutput, int worksetEntries) {
        RecipeRegistry.clearForTesting();
        ConfigTestSupport.setMachineWorkMode(mode);
        var input = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = finishOutput ? RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0)) : null;
        var controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructureWithComponents(controller, new DynamicMachine(MMCR.id("test_cube"),
                "installed shared fixture", new BlockArray(Map.of())), input);
        List<ProcessingComponent> components = new ArrayList<>(List.of(new ProcessingComponent(
                new MachineComponent(input.kind(), input.ioType()), input, input.getBlockPos(), input.getBlockPos(), (String) null)));
        if (output != null) {
            RuntimeTestFixtures.replaceBlockEntity(controller, output);
            controller.getLevel().setBlock(output.getBlockPos(), output.getBlockState(), 3);
            components.add(new ProcessingComponent(new MachineComponent(output.kind(), output.ioType()), output,
                    output.getBlockPos(), output.getBlockPos(), (String) null));
        }
        controller.componentRuntime().replaceComponents(components);
        RuntimeTestFixtures.republish(controller);
        var level = (ServerLevel) controller.getLevel();
        installedLevels.add(level);
        if (requestBudget > 0) installControlledShared(level, new SharedIoCoordinator(requestBudget, worksetEntries));
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of())).satisfies(result ->
                assertThat(result.accepted()).isTrue());
        ArrayDeque<Runnable> workers = new ArrayDeque<>();
        installControlledAsync(level, workers);
        RuntimeTestFixtures.beginLevelTick(level, true);
        controller.serverTick();
        // Finish the fixture's initial empty-catalog search before measuring installed work.
        MachineAsyncCoordinator async = MachineAsyncCoordinator.get(level);
        while (!workers.isEmpty()) {
            workers.removeFirst().run();
            async.pumpMainThreadSteps();
        }
        CraftingRuntime runtime = RuntimeTestFixtures.craftingRuntime(controller);
        var shared = SharedIoCoordinator.get(level);
        input.energyStorage().setAmount(1_000L);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("installed_shared_" + mode.name().toLowerCase()),
                MMCR.id("test_cube"), duration,
                energy == 0L ? List.of() : List.of(new EnergyRequirement(RecipeModifier.IOType.INPUT, energy)), finishOutput
                        ? List.of(new MachineOutput.ItemOutput(new ItemStack(Items.IRON_NUGGET), 1.0F)) : List.of());
        assertThat(shared.reserveExecution(runtime, "base")).isTrue();
        runtime.start(recipe, 1L);
        assertThat(runtime.active()).isTrue();
        InstalledRecipeProgram program = runtime.ensureInstalledProgram();
        assertThat(program).isNotNull();
        assertThat(shared.install(program, shared.controllerPermit(controller.getBlockPos()))).isTrue();
        return new InstalledFixture(controller, input, level, runtime, shared, program, recipe, workers, output);
    }

    @SuppressWarnings("unchecked")
    private static void installControlledAsync(ServerLevel level, ArrayDeque<Runnable> workers) {
        try {
            Field field = MachineAsyncCoordinator.class.getDeclaredField("COORDINATORS");
            field.setAccessible(true);
            ((Map<ServerLevel, MachineAsyncCoordinator>) field.get(null)).put(level,
                    MachineAsyncCoordinator.forTesting(workers::addLast));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to install controlled receipt worker", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static void installControlledShared(ServerLevel level, SharedIoCoordinator coordinator) {
        try {
            Field field = SharedIoCoordinator.class.getDeclaredField("COORDINATORS");
            field.setAccessible(true);
            ((Map<ServerLevel, SharedIoCoordinator>) field.get(null)).put(level, coordinator);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to install bounded shared fixture", exception);
        }
    }

    /**
     * Attached runtime and controlled executor for real-resource scheduling regressions.
     * @author howxu <dev@howxu.cn>
     */
    private record InstalledFixture(MachineControllerBlockEntity controller, EnergyInputHatchBlockEntity input,
                                     ServerLevel level, CraftingRuntime runtime, SharedIoCoordinator shared,
                                     InstalledRecipeProgram program, MachineRecipe recipe, ArrayDeque<Runnable> workers,
                                     ItemOutputBusBlockEntity output) { }

    @Test
    void controller_permit_rejects_freeze_old_round_and_wrong_game_time() {
        var permit = new SharedIoCoordinator.ControllerPermit();
        assertThat(permit.eligible(1L, 40L, true)).isFalse();
        permit.markEligible(1L, 40L);

        assertThat(permit.eligible(1L, 40L, true)).isTrue();
        assertThat(permit.eligible(2L, 40L, true)).isFalse();
        assertThat(permit.eligible(1L, 41L, true)).isFalse();
        assertThat(permit.eligible(1L, 40L, false)).isFalse();
    }

    @Test
    void execution_round_advances_even_when_game_time_does_not() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        var permit = coordinator.controllerPermit(A);
        coordinator.markControllerEligible(A, 40L);
        assertThat(permit.eligible(coordinator.executionRoundId(), 40L, true)).isFalse();
        long first = coordinator.beginExecutionRound(40L, true);
        coordinator.markControllerEligible(A, 40L);
        assertThat(permit.eligible(first, 40L, true)).isTrue();

        long frozen = coordinator.beginExecutionRound(40L, false);
        coordinator.markControllerEligible(A, 40L);
        assertThat(frozen).isGreaterThan(first);
        assertThat(permit.eligible(frozen, 40L, true)).isFalse();
        long resumed = coordinator.beginExecutionRound(40L, true);
        assertThat(permit.eligible(resumed, 40L, true)).isFalse();
        coordinator.markControllerEligible(A, 41L);
        assertThat(permit.eligible(resumed, 40L, true)).isFalse();
        coordinator.markControllerEligible(A, 40L);
        assertThat(permit.eligible(resumed, 40L, true)).isTrue();
    }

    @Test
    void real_ticker_game_time_can_advance_after_pre_without_advancing_the_round() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        long round = coordinator.beginExecutionRound(40L, true);

        coordinator.markControllerEligible(A, 41L);
        var permit = coordinator.controllerPermit(A);

        assertThat(coordinator.executionRoundId()).isEqualTo(round);
        assertThat(permit.eligible(round, 40L, true)).isFalse();
        assertThat(permit.eligible(round, 41L, true)).isTrue();
    }

    @Test
    void qualified_work_left_by_the_budget_cannot_consume_using_an_old_or_frozen_permit() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(1, 8);
        StructureClaimRegistry.ResourceDomain domain = domain(A, B);
        AtomicInteger consumed = new AtomicInteger();
        long round = coordinator.beginExecutionRound(40L, true);
        coordinator.markControllerEligible(A, 40L);
        coordinator.markControllerEligible(B, 40L);
        var waitingPermit = coordinator.controllerPermit(B);
        coordinator.enqueue(tick(domain, A, 1L, () -> { consumed.incrementAndGet(); return true; },
                () -> true, () -> 1L));
        coordinator.enqueue(tick(domain, B, 1L, () -> { consumed.incrementAndGet(); return true; },
                () -> waitingPermit.eligible(coordinator.executionRoundId(), 40L, true), () -> 1L));
        coordinator.resolve(domain);
        assertThat(consumed).hasValue(1);
        assertThat(waitingPermit.eligible(round, 40L, true)).isTrue();
        assertThat(coordinator.pendingRequestCountForTesting()).isEqualTo(1);

        long frozen = coordinator.beginExecutionRound(40L, false);
        coordinator.markControllerEligible(B, 40L);
        coordinator.resolve(domain);
        coordinator.resolve(domain);
        assertThat(waitingPermit.eligible(frozen, 40L, true)).isFalse();
        assertThat(consumed).hasValue(1);
        assertThat(coordinator.pendingRequestCountForTesting()).isEqualTo(1);

        // Resuming replenishes the budget, but without a new ticker the old permit is rejected.
        coordinator.beginExecutionRound(41L, true);
        coordinator.resolve(domain);
        assertThat(consumed).hasValue(1);
        assertThat(coordinator.pendingRequestCountForTesting()).isZero();
    }

    @Test
    void cancelled_controller_does_not_reuse_its_previous_permit() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        long round = coordinator.beginExecutionRound(40L, true);
        coordinator.markControllerEligible(A, 40L);
        var previous = coordinator.controllerPermit(A);

        coordinator.cancel(A);
        var replacement = coordinator.controllerPermit(A);

        assertThat(replacement).isNotSameAs(previous);
        assertThat(previous.eligible(round, 40L, true)).isFalse();
        assertThat(replacement.eligible(round, 40L, true)).isFalse();
    }

    @Test
    void request_budget_round_robins_between_domains() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(2, 8);
        StructureClaimRegistry.ResourceDomain first = domain(1L, A);
        StructureClaimRegistry.ResourceDomain second = domain(2L, B);
        List<String> committed = new ArrayList<>();
        coordinator.enqueue(tick(first, A, 1L, () -> { committed.add("A1"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(tick(first, A, 1L, () -> { committed.add("A2"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(tick(second, B, 1L, () -> { committed.add("B1"); return true; }, () -> true, () -> 1L));

        coordinator.beginLevelTick(10L);
        coordinator.resolveKnownDomainsForTesting(Map.of(first.id(), first, second.id(), second));

        assertThat(committed).containsExactly("A1", "B1");
    }

    @Test
    void domain_rotation_skips_empty_domains_and_rotates_shared_lanes() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(1, 8);
        StructureClaimRegistry.ResourceDomain first = domain(1L, A);
        StructureClaimRegistry.ResourceDomain shared = domain(3L, B, C);
        StructureClaimRegistry.ResourceDomain last = domain(5L, A);
        Map<Long, StructureClaimRegistry.ResourceDomain> known = Map.of(1L, first, 3L, shared, 5L, last);
        List<String> committed = new ArrayList<>();
        coordinator.enqueue(tick(first, A, 1L, () -> { committed.add("first"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(tick(shared, B, 1L, () -> { committed.add("shared-B"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(tick(shared, C, 1L, () -> { committed.add("shared-C"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(tick(last, A, 1L, () -> { committed.add("last"); return true; }, () -> true, () -> 1L));

        for (long gameTime = 1L; gameTime <= 4L; gameTime++) {
            coordinator.beginLevelTick(gameTime);
            assertThat(coordinator.resolveKnownDomainsForTesting(known)).isEqualTo(1);
        }

        assertThat(committed).containsExactly("first", "shared-B", "last", "shared-C");
        assertThat(coordinator.pendingRequestCountForTesting()).isZero();
    }

    @Test
    void selection_uses_live_topology_but_defers_callback_created_domains() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(8, 8);
        StructureClaimRegistry.ResourceDomain first = domain(1L, A);
        StructureClaimRegistry.ResourceDomain removed = domain(2L, B);
        StructureClaimRegistry.ResourceDomain third = domain(3L, C);
        StructureClaimRegistry.ResourceDomain added = domain(4L, B);
        Map<Long, StructureClaimRegistry.ResourceDomain> known = Map.of(1L, first, 2L, removed, 3L, third, 4L, added);
        List<String> committed = new ArrayList<>();
        coordinator.enqueue(tick(first, A, 1L, () -> {
            committed.add("first");
            coordinator.cancel(B);
            coordinator.enqueue(tick(added, B, 1L, () -> { committed.add("added"); return true; }, () -> true, () -> 1L));
            coordinator.enqueue(finish(first, A, 1L, () -> { committed.add("successor"); return true; }, () -> true, () -> 1L));
            return true;
        }, () -> true, () -> 1L));
        coordinator.enqueue(tick(removed, B, 1L, () -> { committed.add("removed"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(tick(third, C, 1L, () -> { committed.add("third"); return true; }, () -> true, () -> 1L));

        assertThat(coordinator.resolveKnownDomainsForTesting(known)).isEqualTo(3);
        assertThat(committed).containsExactly("first", "third", "successor");
        assertThat(coordinator.pendingRequestCountForTesting()).isEqualTo(1);
        assertThat(coordinator.resolveKnownDomainsForTesting(known)).isEqualTo(1);
        assertThat(committed).containsExactly("first", "third", "successor", "added");
    }

    @Test
    void reentrant_resolution_updates_cursor_and_shares_the_remaining_budget() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(3, 8);
        StructureClaimRegistry.ResourceDomain first = domain(1L, A);
        StructureClaimRegistry.ResourceDomain second = domain(2L, B);
        StructureClaimRegistry.ResourceDomain third = domain(3L, C);
        Map<Long, StructureClaimRegistry.ResourceDomain> known = Map.of(1L, first, 2L, second, 3L, third);
        List<String> committed = new ArrayList<>();
        coordinator.enqueue(tick(first, A, 1L, () -> {
            committed.add("first");
            assertThat(coordinator.resolve(second)).isEqualTo(1);
            return true;
        }, () -> true, () -> 1L));
        coordinator.enqueue(tick(first, A, 1L, () -> { committed.add("first-again"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(tick(second, B, 1L, () -> { committed.add("second"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(tick(third, C, 1L, () -> { committed.add("third"); return true; }, () -> true, () -> 1L));

        assertThat(coordinator.resolveKnownDomainsForTesting(known)).isEqualTo(2);
        assertThat(committed).containsExactly("first", "second", "third");
        assertThat(coordinator.resolveKnownDomainsForTesting(known)).isZero();
        coordinator.beginLevelTick(1L);
        assertThat(coordinator.resolveKnownDomainsForTesting(known)).isEqualTo(1);
        assertThat(committed).containsExactly("first", "second", "third", "first-again");
    }

    @Test
    void deleted_cursor_wraps_and_failed_requests_retry_only_on_the_next_resolution() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(8, 8);
        StructureClaimRegistry.ResourceDomain first = domain(1L, A);
        StructureClaimRegistry.ResourceDomain last = domain(3L, C);
        Map<Long, StructureClaimRegistry.ResourceDomain> known = Map.of(1L, first, 3L, last);
        List<String> attempts = new ArrayList<>();
        AtomicBoolean succeeds = new AtomicBoolean();
        coordinator.enqueue(tick(last, C, 1L, () -> { attempts.add("last"); return false; }, () -> true, () -> 1L));
        assertThat(coordinator.resolveKnownDomainsForTesting(known)).isZero();
        coordinator.cancel(C);
        coordinator.enqueue(tick(first, A, 1L, () -> { attempts.add("first"); return succeeds.get(); }, () -> true, () -> 1L));
        coordinator.beginLevelTick(1L);
        assertThat(coordinator.resolveKnownDomainsForTesting(known)).isZero();
        assertThat(attempts).containsExactly("last", "first");
        succeeds.set(true);
        assertThat(coordinator.resolveKnownDomainsForTesting(known)).isEqualTo(1);
        assertThat(attempts).containsExactly("last", "first", "first");
    }

    @Test
    void generation_and_unknown_domain_invalidation_preserve_cursor_order() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(8, 8);
        StructureClaimRegistry.ResourceDomain old = domain(3L, C);
        StructureClaimRegistry.ResourceDomain replacement = new StructureClaimRegistry.ResourceDomain(3L, 2L, Set.of(C));
        StructureClaimRegistry.ResourceDomain unknown = domain(1L, A);
        AtomicInteger discards = new AtomicInteger();
        List<String> committed = new ArrayList<>();
        coordinator.enqueue(tick(old, C, 1L, () -> false,
                () -> { discards.incrementAndGet(); return true; }, () -> 1L));
        assertThat(coordinator.resolve(old)).isZero();
        discards.set(0);
        coordinator.enqueue(tick(unknown, A, 1L, () -> { committed.add("unknown"); return true; },
                () -> { discards.incrementAndGet(); return true; }, () -> 1L));
        coordinator.enqueue(tick(replacement, C, 1L, () -> { committed.add("replacement"); return true; },
                () -> true, () -> 1L));

        assertThat(coordinator.resolveKnownDomainsForTesting(Map.of(3L, replacement))).isEqualTo(1);
        assertThat(committed).containsExactly("replacement");
        assertThat(discards).hasValue(2);
        assertThat(coordinator.pendingRequestCountForTesting()).isZero();
        assertThat(coordinator.indexedControllerCountForTesting()).isZero();
    }

    @Test
    void resolving_many_requests_does_not_reenumerate_domain_keys_per_request() throws Exception {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(512, 8);
        DomainTraversalMap<Object, Object> domains = new DomainTraversalMap<>();
        Field field = SharedIoCoordinator.class.getDeclaredField("domains");
        field.setAccessible(true);
        field.set(coordinator, domains);
        Map<Long, StructureClaimRegistry.ResourceDomain> known = new HashMap<>();
        AtomicInteger commits = new AtomicInteger();
        for (int index = 0; index < 64; index++) {
            BlockPos owner = new BlockPos(index, 64, 0);
            StructureClaimRegistry.ResourceDomain domain = domain(index + 1L, owner);
            known.put(domain.id(), domain);
            for (int request = 0; request < 8; request++) {
                coordinator.enqueue(tick(domain, owner, 1L,
                        () -> { commits.incrementAndGet(); return true; }, () -> true, () -> 1L));
            }
        }

        assertThat(coordinator.resolveKnownDomainsForTesting(known)).isEqualTo(512);
        assertThat(commits).hasValue(512);
        // Stale-domain filtering may enumerate the keys once, but request selection must not
        // repeat a full topology traversal/materialization for each of the 512 commits.
        assertThat(domains.keyVisits).isLessThanOrEqualTo(64);
        assertThat(coordinator.pendingRequestCountForTesting()).isZero();
    }

    /**
     * Counts full domain-key enumeration independently of request validation/commit work.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class DomainTraversalMap<K, V> extends TreeMap<K, V> {
        private int keyVisits;

        @Override
        public Set<K> keySet() {
            Set<K> keys = super.keySet();
            return new AbstractSet<>() {
                @Override
                public Iterator<K> iterator() {
                    Iterator<K> iterator = keys.iterator();
                    return new Iterator<>() {
                        @Override
                        public boolean hasNext() {
                            return iterator.hasNext();
                        }

                        @Override
                        public K next() {
                            keyVisits++;
                            return iterator.next();
                        }
                    };
                }

                @Override
                public int size() {
                    return keys.size();
                }
            };
        }
    }

    @Test
    void resolving_one_domain_preserves_requests_for_other_domains() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(8, 8);
        StructureClaimRegistry.ResourceDomain first = domain(1L, A);
        StructureClaimRegistry.ResourceDomain second = domain(2L, B);
        List<String> committed = new ArrayList<>();
        coordinator.enqueue(tick(first, A, 1L, () -> { committed.add("first"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(tick(second, B, 1L, () -> { committed.add("second"); return true; }, () -> true, () -> 1L));

        coordinator.resolve(first);
        assertThat(committed).containsExactly("first");
        assertThat(coordinator.pendingRequestCountForTesting()).isEqualTo(1);

        coordinator.resolve(second);
        assertThat(committed).containsExactly("first", "second");
    }

    @Test
    void request_budget_is_shared_within_one_game_time_and_resets_on_the_next() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(1, 8);
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        List<String> committed = new ArrayList<>();
        coordinator.enqueue(tick(domain, A, 1L, () -> { committed.add("first"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(tick(domain, B, 1L, () -> { committed.add("second"); return true; }, () -> true, () -> 1L));

        coordinator.beginLevelTick(10L);
        coordinator.resolveKnownDomainsForTesting(Map.of(domain.id(), domain));
        coordinator.beginLevelTick(10L);
        coordinator.resolveKnownDomainsForTesting(Map.of(domain.id(), domain));
        assertThat(committed).containsExactly("first");

        coordinator.beginLevelTick(11L);
        coordinator.resolveKnownDomainsForTesting(Map.of(domain.id(), domain));
        assertThat(committed).containsExactly("first", "second");
    }

    @Test
    void small_budget_rotates_between_start_tick_and_finish_requests() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(1, 8);
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        List<String> committed = new ArrayList<>();
        coordinator.enqueue(start(domain, A, 1L, 1L, ignored -> 1L,
                ignored -> committed.add("start"), () -> true, () -> 1L));
        coordinator.enqueue(tick(domain, A, 1L,
                () -> { committed.add("tick"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(finish(domain, A, 1L,
                () -> { committed.add("finish"); return true; }, () -> true, () -> 1L));

        for (long gameTime = 1L; gameTime <= 3L; gameTime++) {
            coordinator.beginLevelTick(gameTime);
            coordinator.resolveKnownDomainsForTesting(Map.of(domain.id(), domain));
        }

        assertThat(committed).containsExactly("start", "tick", "finish");
    }

    @Test
    void generation_change_discards_the_old_bucket_and_owner_index() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(8, 8);
        StructureClaimRegistry.ResourceDomain oldDomain = new StructureClaimRegistry.ResourceDomain(1L, 1L, Set.of(A));
        StructureClaimRegistry.ResourceDomain newDomain = new StructureClaimRegistry.ResourceDomain(1L, 2L, Set.of(A));
        AtomicInteger validations = new AtomicInteger();
        coordinator.enqueue(tick(oldDomain, A, 1L, () -> true,
                () -> { validations.incrementAndGet(); return false; }, () -> 1L));

        coordinator.beginLevelTick(1L);
        coordinator.resolveKnownDomainsForTesting(Map.of(newDomain.id(), newDomain));

        assertThat(validations).hasValue(1);
        assertThat(coordinator.pendingRequestCountForTesting()).isZero();
        assertThat(coordinator.indexedControllerCountForTesting()).isZero();
    }

    @Test
    void cancelling_a_controller_only_removes_its_indexed_requests() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator(8, 8);
        StructureClaimRegistry.ResourceDomain domain = domain(A, B);
        List<String> committed = new ArrayList<>();
        coordinator.enqueue(tick(domain, A, 1L, () -> { committed.add("A"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(tick(domain, B, 1L, () -> { committed.add("B"); return true; }, () -> true, () -> 1L));

        coordinator.cancel(A);
        coordinator.beginLevelTick(1L);
        coordinator.resolveKnownDomainsForTesting(Map.of(domain.id(), domain));

        assertThat(committed).containsExactly("B");
        assertThat(coordinator.pendingRequestCountForTesting()).isZero();
    }

    @Test
    void domain_tick_workset_reserves_a_shared_snapshot_in_lane_order() {
        var capabilityId = MMCR.id("energy");
        AsyncCapabilitySnapshot snapshot = new AsyncCapabilitySnapshot.Scalar(capabilityId, 10L, 10L, 10L);
        AsyncRequirementPlanner.Capability capability = new AsyncRequirementPlanner.Capability(
                new AsyncCapabilityPlanner.Scalar(capabilityId), snapshot, Set.of(IOType.INPUT));
        AsyncRequirementPlanner.Requirement requirement = new AsyncRequirementPlanner.Requirement(0, 6L,
                IOType.INPUT, List.of(new AsyncCapabilityRequest.Scalar(capabilityId, 1L, 6L, false)));
        AsyncRequirementPlanner.PreparedPlan plan = new AsyncRequirementPlanner.PreparedPlan(
                List.of(requirement), List.of(capability), List.of());

        List<AsyncRequirementPlanner.PlanResult> results = SharedIoCoordinator.planTickWorksetForTesting(
                List.of(plan, plan));

        assertThat(results.get(0).operations()).hasSize(1);
        assertThat(results.get(0).mainThreadRequirements()).isEmpty();
        assertThat(results.get(1).operations()).isEmpty();
        assertThat(results.get(1).mainThreadRequirements()).containsExactly(0);
    }

    @Test
    void cancelling_a_controller_discards_its_pending_domain_tick_work() throws Exception {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A, B);
        ServerLevel level = allocate(ServerLevel.class);
        AtomicInteger discarded = new AtomicInteger();
        AsyncRequirementPlanner.PreparedPlan plan = new AsyncRequirementPlanner.PreparedPlan(
                List.of(), List.of(), List.of());

        coordinator.enqueueTickWork(level, domain,
                new MachineAsyncCoordinator.TaskKey(A, 1L), plan, ignored -> true, discarded::incrementAndGet);
        coordinator.enqueueTickWork(level, domain,
                new MachineAsyncCoordinator.TaskKey(B, 1L), plan, ignored -> true, () -> { });

        coordinator.cancel(A);

        assertThat(discarded).hasValue(1);
    }

    @Test
    void failed_domain_tick_workset_discards_every_entry_and_releases_the_submission() {
        SharedIoCoordinator sharedIo = new SharedIoCoordinator();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        AtomicInteger discarded = new AtomicInteger();

        sharedIo.submitTickWorksetForTesting(coordinator, ignored -> {
            throw new IllegalStateException("planned failure");
        }, 2, discarded::incrementAndGet);
        coordinator.completeTick();

        assertThat(discarded).hasValue(2);
        assertThat(sharedIo.submittedTickWorkCountForTesting()).isZero();
    }

    @Test
    void resolving_while_a_domain_workset_is_in_flight_keeps_that_workset_current() throws Exception {
        SharedIoCoordinator sharedIo = new SharedIoCoordinator();
        ArrayDeque<Runnable> workers = new ArrayDeque<>();
        MachineAsyncCoordinator async = MachineAsyncCoordinator.forTesting(workers::add);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), A);
        RuntimeTestFixtures.formStructure(controller, new DynamicMachine(MMCR.id("test_cube"),
                "in-flight shared workset", new BlockArray(Map.of())));
        ServerLevel level = (ServerLevel) controller.getLevel();
        installedLevels.add(level);
        StructureClaimRegistry registry = StructureClaimRegistry.get(level);
        assertThat(registry.claim(A, List.of()).accepted()).isTrue();
        StructureClaimRegistry.ResourceDomain domain = registry.domainFor(A);
        AtomicInteger committed = new AtomicInteger();
        AtomicInteger discarded = new AtomicInteger();

        sharedIo.enqueueTickWorkForTesting(domain, new MachineAsyncCoordinator.TaskKey(A, 1L),
                ignored -> committed.incrementAndGet(), discarded::incrementAndGet);
        sharedIo.submitPendingTickWorksetForTesting(domain, async);
        sharedIo.resolve(level);

        workers.removeFirst().run();
        async.completeTick();

        assertThat(committed).hasValue(1);
        assertThat(discarded).hasValue(0);
    }

    @Test
    void failed_entry_commit_is_discarded_without_blocking_later_entries() {
        SharedIoCoordinator sharedIo = new SharedIoCoordinator();
        MachineAsyncCoordinator coordinator = MachineAsyncCoordinator.forTesting(Runnable::run);
        AtomicInteger firstDiscarded = new AtomicInteger();
        AtomicInteger secondCommitted = new AtomicInteger();

        sharedIo.submitTickWorksetForTesting(coordinator, List.of(
                ignored -> { throw new IllegalStateException("commit failure"); },
                ignored -> secondCommitted.incrementAndGet()),
                List.of(firstDiscarded::incrementAndGet, () -> { }));
        coordinator.completeUntilIdleForTesting(() -> 0);

        assertThat(firstDiscarded).hasValue(1);
        assertThat(secondCommitted).hasValue(1);
        assertThat(sharedIo.submittedTickWorkCountForTesting()).isZero();
    }

    @Test
    void domain_tick_worksets_are_batched_and_serialized() {
        SharedIoCoordinator sharedIo = new SharedIoCoordinator(8, 2);
        MachineAsyncCoordinator async = MachineAsyncCoordinator.forTesting(Runnable::run);
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        var key = new MachineAsyncCoordinator.TaskKey(A, 1L);
        for (int index = 0; index < 5; index++) {
            sharedIo.enqueueTickWorkForTesting(domain, key, ignored -> { }, () -> { });
        }

        sharedIo.submitPendingTickWorksetForTesting(domain, async);
        assertThat(sharedIo.submittedTickWorkEntryCountsForTesting()).containsExactly(2);
        assertThat(sharedIo.pendingTickWorkCountForTesting()).isEqualTo(3);
        sharedIo.submitPendingTickWorksetForTesting(domain, async);
        assertThat(sharedIo.submittedTickWorkEntryCountsForTesting()).containsExactly(2);

        async.completeUntilIdleForTesting(() -> 0);
        sharedIo.submitPendingTickWorksetForTesting(domain, async);
        assertThat(sharedIo.submittedTickWorkEntryCountsForTesting()).containsExactly(2);
        async.completeUntilIdleForTesting(() -> 0);
        sharedIo.submitPendingTickWorksetForTesting(domain, async);
        assertThat(sharedIo.submittedTickWorkEntryCountsForTesting()).containsExactly(1);
        async.completeUntilIdleForTesting(() -> 0);

        assertThat(sharedIo.pendingTickWorkCountForTesting()).isZero();
        assertThat(sharedIo.submittedTickWorkCountForTesting()).isZero();
    }

    @Test
    void failed_workset_releases_the_domain_for_the_next_batch() {
        SharedIoCoordinator sharedIo = new SharedIoCoordinator(8, 2);
        MachineAsyncCoordinator async = MachineAsyncCoordinator.forTesting(Runnable::run);
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        var key = new MachineAsyncCoordinator.TaskKey(A, 1L);
        AtomicInteger discarded = new AtomicInteger();
        AtomicInteger committed = new AtomicInteger();
        sharedIo.enqueueTickWorkForTesting(domain, key,
                ignored -> { throw new IllegalStateException("commit failure"); }, discarded::incrementAndGet);
        sharedIo.enqueueTickWorkForTesting(domain, key, ignored -> committed.incrementAndGet(), () -> { });
        sharedIo.enqueueTickWorkForTesting(domain, key, ignored -> committed.incrementAndGet(), () -> { });

        sharedIo.submitPendingTickWorksetForTesting(domain, async);
        async.completeUntilIdleForTesting(() -> 0);
        sharedIo.submitPendingTickWorksetForTesting(domain, async);
        async.completeUntilIdleForTesting(() -> 0);

        assertThat(discarded).hasValue(1);
        assertThat(committed).hasValue(2);
        assertThat(sharedIo.pendingTickWorkCountForTesting()).isZero();
    }

    @Test
    void later_batch_plans_from_the_snapshot_committed_by_the_previous_batch() {
        var capabilityId = MMCR.id("energy");
        AsyncCapabilitySnapshot snapshot = new AsyncCapabilitySnapshot.Scalar(capabilityId, 10L, 10L, 10L);
        AsyncRequirementPlanner.Capability capability = new AsyncRequirementPlanner.Capability(
                new AsyncCapabilityPlanner.Scalar(capabilityId), snapshot, Set.of(IOType.INPUT));
        AsyncRequirementPlanner.Requirement requirement = new AsyncRequirementPlanner.Requirement(0, 6L,
                IOType.INPUT, List.of(new AsyncCapabilityRequest.Scalar(capabilityId, 1L, 6L, false)));
        AsyncRequirementPlanner.PreparedPlan plan = new AsyncRequirementPlanner.PreparedPlan(
                List.of(requirement), List.of(capability), List.of());
        SharedIoCoordinator sharedIo = new SharedIoCoordinator(8, 1);
        MachineAsyncCoordinator async = MachineAsyncCoordinator.forTesting(Runnable::run);
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        var key = new MachineAsyncCoordinator.TaskKey(A, 1L);
        List<AsyncRequirementPlanner.PlanResult> committed = new ArrayList<>();
        sharedIo.enqueueTickWorkForTesting(domain, key, plan, committed::add, () -> { });
        sharedIo.enqueueTickWorkForTesting(domain, key, plan, committed::add, () -> { });

        sharedIo.submitPendingTickWorksetForTesting(domain, async);
        async.completeUntilIdleForTesting(() -> 0);
        sharedIo.submitPendingTickWorksetForTesting(domain, async);
        async.completeUntilIdleForTesting(() -> 0);

        assertThat(committed.get(0).operations()).hasSize(1);
        assertThat(committed.get(1).operations()).isEmpty();
        assertThat(committed.get(1).mainThreadRequirements()).containsExactly(0);
    }

    @Test
    void rejected_workset_result_does_not_rebase_the_next_batch_as_if_resources_were_consumed() throws Exception {
        var capabilityId = MMCR.id("energy");
        AsyncCapabilitySnapshot snapshot = new AsyncCapabilitySnapshot.Scalar(capabilityId, 10L, 10L, 10L);
        AsyncRequirementPlanner.Capability capability = new AsyncRequirementPlanner.Capability(
                new AsyncCapabilityPlanner.Scalar(capabilityId), snapshot, Set.of(IOType.INPUT));
        AsyncRequirementPlanner.Requirement requirement = new AsyncRequirementPlanner.Requirement(0, 6L,
                IOType.INPUT, List.of(new AsyncCapabilityRequest.Scalar(capabilityId, 1L, 6L, false)));
        AsyncRequirementPlanner.PreparedPlan plan = new AsyncRequirementPlanner.PreparedPlan(
                List.of(requirement), List.of(capability), List.of());
        SharedIoCoordinator sharedIo = new SharedIoCoordinator(8, 1);
        MachineAsyncCoordinator async = MachineAsyncCoordinator.forTesting(Runnable::run);
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        var key = new MachineAsyncCoordinator.TaskKey(A, 1L);
        List<AsyncRequirementPlanner.PlanResult> committed = new ArrayList<>();
        sharedIo.enqueueTickWork(allocate(ServerLevel.class), domain, key, plan, intent -> {
            assertThat(intent.operations()).hasSize(1);
            return false;
        }, () -> { });
        sharedIo.enqueueTickWorkForTesting(domain, key, plan, committed::add, () -> { });

        sharedIo.submitPendingTickWorksetForTesting(domain, async);
        async.completeUntilIdleForTesting(() -> 0);
        sharedIo.submitPendingTickWorksetForTesting(domain, async);
        async.completeUntilIdleForTesting(() -> 0);

        assertThat(committed).singleElement().satisfies(intent -> {
            assertThat(intent.operations()).hasSize(1);
            assertThat(intent.mainThreadRequirements()).isEmpty();
        });
        assertThat(sharedIo.submittedTickWorkCountForTesting()).isZero();
    }

    @Test
    void startRequestsUseRotatingOrderAndMayReceivePartialParallelism() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A, B);
        List<String> committed = new ArrayList<>();

        coordinator.enqueue(start(domain, A, 17L, 8,
                maximum -> Math.min(maximum, 8), granted -> committed.add("A:" + granted), () -> true, () -> 17L));
        coordinator.enqueue(start(domain, B, 21L, 8,
                maximum -> Math.min(maximum, 2), granted -> committed.add("B:" + granted), () -> true, () -> 21L));

        coordinator.resolve(domain);

        assertThat(committed).containsExactly("A:8", "B:2");
        assertThat(coordinator.nextStartLane(domain.id()))
                .isEqualTo(new SharedIoCoordinator.LaneKey(B, "base"));
    }

    @Test
    void invalid_request_runs_its_validator_once_per_resolution() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        AtomicInteger validations = new AtomicInteger();

        coordinator.enqueue(tick(domain, A, 1L, () -> true,
                () -> {
                    validations.incrementAndGet();
                    return false;
                }, () -> 1L));

        coordinator.resolve(domain);

        assertThat(validations).hasValue(1);
    }

    @Test
    void finish_spawned_start_runs_its_validator_once_per_resolution() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        AtomicInteger validations = new AtomicInteger();
        coordinator.enqueue(finish(domain, A, 1L, () -> {
            coordinator.enqueue(start(domain, A, 1L, 1L, ignored -> 1L, ignored -> { },
                    () -> {
                        validations.incrementAndGet();
                        return true;
                    }, () -> 1L));
            return true;
        }, () -> true, () -> 1L));

        coordinator.resolve(domain);

        assertThat(validations).hasValue(1);
    }

    @Test
    void round_robin_moves_past_the_last_base_lane_of_the_same_controller() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        List<String> committed = new ArrayList<>();

        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain,
                new SharedIoCoordinator.LaneKey(A, "base"), 1L, 0L, 1,
                ignored -> 1, ignored -> committed.add("base"), () -> true, () -> 1L, () -> 0L));
        coordinator.resolve(domain);
        committed.clear();
        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain,
                new SharedIoCoordinator.LaneKey(A, "base"), 1L, 0L, 1,
                ignored -> 1, ignored -> committed.add("base"), () -> true, () -> 1L, () -> 0L));
        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain,
                new SharedIoCoordinator.LaneKey(A, "factory-0"), 1L, 0L, 1,
                ignored -> 1, ignored -> committed.add("factory-0"), () -> true, () -> 1L, () -> 0L));

        coordinator.resolve(domain);

        assertThat(committed).containsExactly("factory-0", "base");
    }

    @Test
    void round_robin_rotates_between_all_lanes_of_the_same_controller() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        List<String> committed = new ArrayList<>();

        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain,
                new SharedIoCoordinator.LaneKey(A, "factory-0"), 1L, 0L, 1,
                ignored -> 1, ignored -> committed.add("factory-0"), () -> true, () -> 1L, () -> 0L));
        coordinator.resolve(domain);
        committed.clear();
        for (String laneId : List.of("base", "factory-0", "factory-1")) {
            coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain,
                    new SharedIoCoordinator.LaneKey(A, laneId), 1L, 0L, 1,
                    ignored -> 1, ignored -> committed.add(laneId), () -> true, () -> 1L, () -> 0L));
        }

        coordinator.resolve(domain);

        assertThat(committed).containsExactly("factory-1", "base", "factory-0");
    }

    @Test
    void start_tick_and_finish_requests_use_independent_cursors() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A, B, C);
        List<String> committed = new ArrayList<>();

        coordinator.enqueue(start(domain, A, 1L, 1,
                ignored -> 1, ignored -> committed.add("initial-start"), () -> true, () -> 1L));
        coordinator.enqueue(tick(domain, B, 1L,
                () -> { committed.add("initial-tick"); return true; }, () -> true, () -> 1L));
        coordinator.enqueue(finish(domain, C, 1L,
                () -> { committed.add("initial-finish"); return true; }, () -> true, () -> 1L));
        coordinator.resolve(domain);
        committed.clear();

        enqueueAllRequestTypes(coordinator, domain, committed);
        coordinator.resolve(domain);

        assertThat(committed).containsExactly(
                "start:B", "tick:C", "finish:A",
                "start:C", "tick:A", "finish:B",
                "start:A", "tick:B", "finish:C");
    }

    @Test
    void cancelling_a_controller_discards_its_start_tick_and_finish_requests() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A, B);
        List<String> committed = new ArrayList<>();

        coordinator.enqueue(start(domain, A, 1L, 1, ignored -> 1, ignored -> committed.add("start"), () -> true, () -> 1L));
        coordinator.enqueue(tick(domain, A, 1L, () -> {
            committed.add("tick");
            return true;
        }, () -> true, () -> 1L));
        coordinator.enqueue(finish(domain, A, 1L, () -> {
            committed.add("finish");
            return true;
        }, () -> true, () -> 1L));
        coordinator.enqueue(tick(domain, B, 1L, () -> {
            committed.add("other-controller");
            return true;
        }, () -> true, () -> 1L));

        coordinator.cancel(A);
        coordinator.resolve(domain);

        assertThat(committed).containsExactly("other-controller");
    }

    @Test
    void finite_shared_energy_rotates_to_the_lane_that_can_finish_the_next_tick() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A, B);
        AtomicInteger energy = new AtomicInteger(20);
        List<String> advanced = new ArrayList<>();

        enqueueEnergyTick(coordinator, domain, A, energy, advanced);
        enqueueEnergyTick(coordinator, domain, B, energy, advanced);
        coordinator.resolve(domain);
        energy.addAndGet(15);
        enqueueEnergyTick(coordinator, domain, A, energy, advanced);
        enqueueEnergyTick(coordinator, domain, B, energy, advanced);
        coordinator.resolve(domain);

        assertThat(advanced).containsExactly("A", "B");
    }

    @Test
    void finish_commit_can_install_a_replacement_start_without_running_its_tick() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        SharedIoCoordinator.LaneKey lane = new SharedIoCoordinator.LaneKey(A, "base");
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger ticks = new AtomicInteger();

        coordinator.enqueue(new SharedIoCoordinator.FinishRequest(domain, lane, 1L, 0L, () -> {
            coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain, lane, 1L, 0L, 1,
                    ignored -> 1, ignored -> starts.incrementAndGet(), () -> true, () -> 1L, () -> 0L));
            coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, lane, 1L, 0L,
                    () -> { ticks.incrementAndGet(); return true; }, () -> true, () -> 1L, () -> 0L));
            return true;
        }, () -> true, () -> 1L, () -> 0L));

        coordinator.resolve(domain);

        assertThat(starts).hasValue(1);
        assertThat(ticks).hasValue(0);
    }

    @Test
    void tick_commit_can_enqueue_finish_for_the_same_domain_pass() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        SharedIoCoordinator.LaneKey lane = new SharedIoCoordinator.LaneKey(A, "base");
        List<String> committed = new ArrayList<>();

        coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, lane, 1L, 0L, () -> {
            committed.add("tick");
            coordinator.enqueue(new SharedIoCoordinator.FinishRequest(domain, lane, 1L, 0L,
                    () -> { committed.add("finish"); return true; }, () -> true, () -> 1L, () -> 0L));
            return true;
        }, () -> true, () -> 1L, () -> 0L));

        coordinator.resolve(domain);

        assertThat(committed).containsExactly("tick", "finish");
    }

    @Test
    void staleStructureAndStateVersionsNeverInvokeTransactions() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        AtomicLong structureVersion = new AtomicLong(1L);
        AtomicLong stateVersion = new AtomicLong(1L);
        AtomicBoolean invoked = new AtomicBoolean();

        coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, lane(A), 1L, 1L,
                () -> {
                    invoked.set(true);
                    return true;
                }, () -> true, structureVersion::get, stateVersion::get));
        structureVersion.incrementAndGet();

        coordinator.resolve(domain);

        assertThat(invoked).isFalse();
    }

    @Test
    void valid_request_is_checked_once_immediately_before_commit() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        AtomicInteger validations = new AtomicInteger();

        coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, lane(A), 1L, 0L,
                () -> true, () -> {
                    validations.incrementAndGet();
                    return true;
                }, () -> 1L, () -> 0L));

        coordinator.resolve(domain);

        assertThat(validations).hasValue(1);
    }

    @Test
    void catalog_change_before_shared_start_commit_never_runs_runtime_or_resource_transaction() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        AtomicLong catalogVersion = new AtomicLong(1L);
        AtomicInteger runtimeStarts = new AtomicInteger();
        AtomicInteger extractedInputs = new AtomicInteger();

        coordinator.enqueue(new SharedIoCoordinator.StartRequest(domain, lane(A), 1L, 0L, 1,
                ignored -> {
                    runtimeStarts.incrementAndGet();
                    extractedInputs.incrementAndGet();
                    return 1;
                }, ignored -> { }, () -> true, () -> 1L, () -> 0L,
                1L, catalogVersion::get, () -> { }));
        catalogVersion.set(2L);

        coordinator.resolve(domain);

        assertThat(runtimeStarts.get()).isZero();
        assertThat(extractedInputs.get()).isZero();
    }

    @Test
    void catalog_change_before_shared_tick_or_finish_commit_never_runs_transactions() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        AtomicLong catalogVersion = new AtomicLong(1L);
        AtomicInteger committed = new AtomicInteger();

        coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, lane(A), 1L, 0L,
                () -> {
                    committed.incrementAndGet();
                    return true;
                }, () -> true, () -> 1L, () -> 0L, 1L, catalogVersion::get, () -> { }));
        coordinator.enqueue(new SharedIoCoordinator.FinishRequest(domain, lane(A), 1L, 0L,
                () -> {
                    committed.incrementAndGet();
                    return true;
                }, () -> true, () -> 1L, () -> 0L, 1L, catalogVersion::get, () -> { }));
        catalogVersion.incrementAndGet();

        coordinator.resolve(domain);

        assertThat(committed).hasValue(0);
    }

    @Test
    void stateVersionInvalidationAlsoDiscardsPendingRequests() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        AtomicLong stateVersion = new AtomicLong(1L);
        AtomicBoolean invoked = new AtomicBoolean();

        coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, lane(A), 1L, 1L,
                () -> {
                    invoked.set(true);
                    return true;
                }, () -> true, () -> 1L, stateVersion::get));
        stateVersion.incrementAndGet();

        coordinator.resolve(domain);

        assertThat(invoked).isFalse();
    }

    @Test
    void finishRequestCanInstallAReplacementStartInTheSameDomainPass() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        AtomicInteger starts = new AtomicInteger();

        coordinator.enqueue(finish(domain, A, 1L, () -> {
            coordinator.enqueue(start(domain, A, 1L, 1,
                    ignored -> 1, ignored -> starts.incrementAndGet(), () -> true, () -> 1L));
            return true;
        }, () -> true, () -> 1L));

        coordinator.resolve(domain);

        assertThat(starts).hasValue(1);
    }

    @Test
    void resolve_reports_completed_work_even_when_a_replacement_request_remains_pending() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        coordinator.enqueue(finish(domain, A, 1L, () -> {
            coordinator.enqueue(start(domain, A, 1L, 1, ignored -> 0, ignored -> { }, () -> true, () -> 1L));
            return true;
        }, () -> true, () -> 1L));

        assertThat(coordinator.resolve(domain)).isEqualTo(1);
    }

    @Test
    void invalidValidatorPreventsAStaleLaneFromCommittingAfterEarlierLaneChangesState() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A, B);
        AtomicBoolean valid = new AtomicBoolean(true);
        AtomicBoolean staleInvoked = new AtomicBoolean();
        List<String> committed = new ArrayList<>();

        coordinator.enqueue(tick(domain, A, 1L,
                () -> {
                    committed.add("A");
                    valid.set(false);
                    return true;
                }, valid::get, () -> 1L));
        coordinator.enqueue(tick(domain, B, 1L,
                () -> {
                    staleInvoked.set(true);
                    committed.add("B");
                    return true;
                }, valid::get, () -> 1L));

        coordinator.resolve(domain);

        assertThat(committed).containsExactly("A");
        assertThat(staleInvoked).isFalse();
    }

    @Test
    void resolver_discards_a_partitioned_request_after_its_lifecycle_epoch_changes() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A, B);
        AtomicLong lifecycleEpoch = new AtomicLong(1L);
        AtomicBoolean staleInvoked = new AtomicBoolean();

        coordinator.enqueue(tick(domain, A, 1L, () -> {
            lifecycleEpoch.incrementAndGet();
            return true;
        }, () -> true, () -> 1L));
        long requestEpoch = lifecycleEpoch.get();
        coordinator.enqueue(tick(domain, B, 1L, () -> {
            staleInvoked.set(true);
            return true;
        }, () -> lifecycleEpoch.get() == requestEpoch, () -> 1L));

        coordinator.resolve(domain);

        assertThat(staleInvoked).isFalse();
    }

    @Test
    void validity_filtering_preserves_stage_order_when_requests_are_partitioned() {
        SharedIoCoordinator coordinator = new SharedIoCoordinator();
        StructureClaimRegistry.ResourceDomain domain = domain(A);
        AtomicBoolean invalid = new AtomicBoolean(false);
        List<String> committed = new ArrayList<>();

        coordinator.enqueue(start(domain, A, 1L, 1,
                ignored -> 1, ignored -> committed.add("start"), () -> true, () -> 1L));
        coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, lane(A), 1L, 0L,
                () -> {
                    invalid.set(true);
                    return true;
                }, () -> false, () -> 1L, () -> 0L));
        coordinator.enqueue(finish(domain, A, 1L,
                () -> {
                    committed.add("finish");
                    return true;
                }, () -> true, () -> 1L));

        coordinator.resolve(domain);

        assertThat(committed).containsExactly("start", "finish");
        assertThat(invalid).isFalse();
    }

    private static StructureClaimRegistry.ResourceDomain domain(BlockPos... positions) {
        return domain(1L, positions);
    }

    private static StructureClaimRegistry.ResourceDomain domain(long id, BlockPos... positions) {
        return new StructureClaimRegistry.ResourceDomain(id, 1L, Set.of(positions));
    }

    private static SharedIoCoordinator.LaneKey lane(BlockPos position) {
        return new SharedIoCoordinator.LaneKey(position, "base");
    }

    private static void enqueueAllRequestTypes(SharedIoCoordinator coordinator,
                                                StructureClaimRegistry.ResourceDomain domain,
                                                List<String> committed) {
        for (BlockPos pos : List.of(A, B, C)) {
            String lane = pos.equals(A) ? "A" : pos.equals(B) ? "B" : "C";
            SharedIoCoordinator.LaneKey laneKey = lane(pos);
            coordinator.enqueue(start(domain, pos, 1L, 1,
                    ignored -> 1, ignored -> committed.add("start:" + lane), () -> true, () -> 1L));
            coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, laneKey, 1L, 0L,
                    () -> { committed.add("tick:" + lane); return true; }, () -> true, () -> 1L, () -> 0L));
            coordinator.enqueue(new SharedIoCoordinator.FinishRequest(domain, laneKey, 1L, 0L,
                    () -> { committed.add("finish:" + lane); return true; }, () -> true, () -> 1L, () -> 0L));
        }
    }

    private static void enqueueEnergyTick(SharedIoCoordinator coordinator,
                                          StructureClaimRegistry.ResourceDomain domain,
                                          BlockPos position, AtomicInteger energy, List<String> advanced) {
        String lane = position.equals(A) ? "A" : "B";
        coordinator.enqueue(new SharedIoCoordinator.TickRequest(domain, lane(position), 1L, 0L,
                () -> {
                    if (energy.get() < 15) return false;
                    energy.addAndGet(-15);
                    advanced.add(lane);
                    return true;
                }, () -> true, () -> 1L, () -> 0L));
    }

    private static SharedIoCoordinator.StartRequest start(StructureClaimRegistry.ResourceDomain domain,
                                                           BlockPos position, long structureVersion,
                                                            long maximumParallelism,
                                                            LongUnaryOperator transaction,
                                                            LongConsumer committer,
                                                           BooleanSupplier validator,
                                                           LongSupplier structureVersionSupplier) {
        return new SharedIoCoordinator.StartRequest(domain, lane(position), structureVersion, 0L,
                maximumParallelism, transaction, committer, validator, structureVersionSupplier, () -> 0L);
    }

    private static SharedIoCoordinator.TickRequest tick(StructureClaimRegistry.ResourceDomain domain,
                                                         BlockPos position, long structureVersion,
                                                         BooleanSupplier transaction,
                                                         BooleanSupplier validator,
                                                         LongSupplier structureVersionSupplier) {
        return new SharedIoCoordinator.TickRequest(domain, lane(position), structureVersion, 0L,
                transaction, validator, structureVersionSupplier, () -> 0L);
    }

    private static SharedIoCoordinator.FinishRequest finish(StructureClaimRegistry.ResourceDomain domain,
                                                             BlockPos position, long structureVersion,
                                                             BooleanSupplier transaction,
                                                             BooleanSupplier validator,
                                                             LongSupplier structureVersionSupplier) {
        return new SharedIoCoordinator.FinishRequest(domain, lane(position), structureVersion, 0L,
                transaction, validator, structureVersionSupplier, () -> 0L);
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (T) ((sun.misc.Unsafe) field.get(null)).allocateInstance(type);
    }
}
