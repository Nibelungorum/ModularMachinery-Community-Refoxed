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
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.appliedflux.loaded.capability.FluxEnergyInputCapability;
import cn.howxu.mmcr.compat.appliedflux.loaded.storage.FluxEnergyBuffer;
import cn.howxu.mmcr.compat.appliedflux.loaded.FluxEnergyTicker;
import appeng.api.networking.ticking.TickRateModulation;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** Real native Flux boundaries exercised through runtime payment, persistence and installed grants.
 * @author howxu <dev@howxu.cn>
 */
class FluxRuntimeBoundaryTest {
    private static final ResourceLocation MACHINE = MMCR.id("test_cube");
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
    @ValueSource(booleans = {false, true})
    void bulk_resource_start_with_flux_prefetch_advances_without_quarantine_or_replay(boolean pattern) {
        Fixture fixture = new Fixture(2, false);
        LongItemStorage input = new LongItemStorage(2, Long.MAX_VALUE, null);
        input.setContents(0, new ItemStack(Items.IRON_INGOT), 1001L);
        input.setContents(1, new ItemStack(Items.GOLD_INGOT), 10001L);
        LongFluidStorage fluids = new LongFluidStorage(Long.MAX_VALUE, null);
        fluids.setContents(0, new FluidStack(Fluids.WATER, 1), 10001L);
        var components = new ArrayList<>(fixture.controller.componentRuntime().components());
        components.add(new ProcessingComponent(null, new TestCapabilityHost(List.of(new ItemBusCapability(input, IOType.INPUT),
                new FluidHatchCapability(fluids, IOType.INPUT))),
                new BlockPos(4, 0, 0), BlockPos.ZERO, (String) null));
        fixture.controller.componentRuntime().replaceComponents(components);
        RuntimeTestFixtures.republish(fixture.controller);
        List<MachineRequirement> requirements = new ArrayList<>(fixture.recipe.requirements());
        requirements.add(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT),
                1000, ItemStack.EMPTY));
        requirements.add(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.GOLD_INGOT),
                10000, ItemStack.EMPTY));
        requirements.add(new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER),
                10000, FluidStack.EMPTY));
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("bulk_resource_flux_start"), MACHINE, 2,
                requirements, List.of(new ItemStack(Items.IRON_NUGGET)));
        RecipeRegistry.registerStatic(recipe);

        if (pattern) {
            var prepared = fixture.runtime.preparePatternStart(recipe, 1L, List.of());
            assertThat(prepared).isNotNull();
            assertThat(fixture.runtime.reservePatternStart()).isTrue();
            assertThat(fixture.runtime.commitPatternStart(prepared)).isTrue();
        } else {
            fixture.runtime.start(recipe, 1L);
        }
        assertThat(fixture.runtime.active()).isTrue();
        assertThat(fixture.runtime.failure()).isNull();
        assertThat(fixture.runtime.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.READY);
        assertThat(input.amount(0)).isEqualTo(1L);
        assertThat(input.amount(1)).isEqualTo(1L);
        assertThat(fluids.amount(0)).isEqualTo(1L);
        fixture.assertReservations(4L, 6L);

        assertThat(fixture.attempt(MachineWorkMode.SYNC).advance()).isTrue();
        assertThat(fixture.runtime.tickCount()).isEqualTo(1);
        fixture.assertReservations(2L, 3L);
        assertThat(input.amount(0)).isEqualTo(1L);
        assertThat(input.amount(1)).isEqualTo(1L);
        assertThat(fluids.amount(0)).isEqualTo(1L);
        assertThat(fixture.first.networkCommits).isEqualTo(1);
        assertThat(fixture.second.networkCommits).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void start_notification_cancel_releases_only_the_old_paid_prepared_once(boolean pattern, boolean throwAfterCancel) {
        Fixture fixture = new Fixture(2);
        fixture.second.notification.set(() -> {
            fixture.runtime.invalidate();
            if (throwAfterCancel) throw new IllegalStateException("cancelled start notification");
        });

        assertThat(fixture.start(pattern)).isEqualTo(pattern);
        assertThat(fixture.runtime.active()).isFalse();
        fixture.assertReleased(4L, 6L);
        assertThat(fixture.first.network.amount()).isEqualTo(96L);
        assertThat(fixture.second.network.amount()).isEqualTo(94L);
        assertThat(fixture.first.networkCommits).isEqualTo(1);
        assertThat(fixture.second.networkCommits).isEqualTo(1);
        assertThat(save(fixture.runtime).getBoolean("active")).isFalse();
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
        fixture.second.notification.set(() -> {
            fixture.runtime.invalidate();
            assertThat(fixture.runtime.start(fixture.recipe, 1L).isCrafting()).isTrue();
            replacementPayment.set(fixture.runtime.ledger());
            if (throwAfterReplacement) throw new IllegalStateException("old start notification");
        });

        boolean started = fixture.start(pattern);
        if (pattern) assertThat(started).isTrue();
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
        fixture.second.notification.set(() -> { throw new IllegalStateException("valid start notification"); });
        assertThat(fixture.start(pattern)).isTrue();
        assertThat(fixture.runtime.active()).isTrue();
        assertThat(fixture.runtime.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.ERROR_PAUSED);
        assertThat(fixture.first.buffer.reserved()).isEqualTo(4L);
        assertThat(fixture.second.buffer.reserved()).isEqualTo(6L);
        assertThat(fixture.first.releaseCalls).isZero();
        assertThat(fixture.second.releaseCalls).isZero();
        assertThat(fixture.first.networkCommits).isEqualTo(1);
        assertThat(fixture.second.networkCommits).isEqualTo(1);
        assertThat(fixture.first.network.amount()).isEqualTo(96L);
        assertThat(fixture.second.network.amount()).isEqualTo(94L);
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
        fixture.second.notification.set(() -> {
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
            assertThat(fixture.ordinary.getAmountAsLong()).isEqualTo(7L * tick);
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
        assertThat(fixture.output.itemHandler().amount(0)).isEqualTo(1L);
        fixture.assertReleased(0L, 0L);
        assertThat(fixture.first.releaseCalls).isZero();
        assertThat(fixture.second.releaseCalls).isZero();
        assertThat(fixture.first.networkCommits).isEqualTo(1);
        assertThat(fixture.second.networkCommits).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"1,SYNC", "2,SYNC", "1,SEMI_SYNC", "2,SEMI_SYNC", "1,ASYNC", "2,ASYNC"})
    void ordinary_notification_save_throw_keeps_prefetch_credit_and_blocks_grant_and_finish(
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
        fixture.assertUncertainWithoutGrant();
        assertThat(fixture.ordinary.getAmountAsLong()).isEqualTo(7L);
        fixture.assertReservations(0L, 5L * (duration - 1L));
        assertThat(fixture.first.consumeCalls).isEqualTo(1);
        assertThat(fixture.second.consumeCalls).isEqualTo(1);
        assertThat(saved.get().getCompound("runtime").getCompound("execution_commit")
                .getString("phase")).isEqualTo("UNCERTAIN");
        fixture.assertNoReplay(fixture.runtime);
        CraftingRuntime restored = fixture.restore(saved.get());
        fixture.assertNoReplay(restored);
        assertThat(installed.advance()).isFalse();
        restored.invalidate();
        fixture.assertReleased(0L, 5L * (duration - 1L));
    }

    @ParameterizedTest
    @CsvSource({"1,SYNC", "2,SYNC", "1,SEMI_SYNC", "2,SEMI_SYNC", "1,ASYNC", "2,ASYNC"})
    void native_reservation_rejection_saves_successful_key_credit_and_retry_pays_only_the_remainder(int duration, MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        Fixture fixture = new Fixture(duration);
        assertThat(fixture.start(false)).isTrue();
        fixture.second.rejectConsumption = true;
        var installed = fixture.attempt(mode);
        assertThat(installed.advance()).isFalse();
        assertThat(fixture.runtime.tickCount()).isZero();
        assertThat(fixture.ordinary.getAmountAsLong()).isZero();
        fixture.assertReservations(0L, 3L * duration);
        assertThat(save(fixture.runtime).getCompound("recipe").getCompound("data")
                .getLong("prefetched_energy_tick_consumed")).isEqualTo(2L * duration);
        assertThat(fixture.first.consumeCalls).isEqualTo(1);
        assertThat(fixture.second.consumeCalls).isEqualTo(1);
        fixture.second.rejectConsumption = false;
        CraftingRuntime restored = fixture.restore(fixture.saveAll());
        LevelStub.setGameTime(fixture.controller.getLevel(), 2L);
        var retry = restored.ensureInstalledProgram();
        if (mode != MachineWorkMode.SYNC) assertThat(restored.prepareInstalledTick(2L)).isTrue();
        assertThat(retry.tryAttempt(2L, 2L)).isTrue();
        assertThat(retry.advance()).isTrue();
        assertThat(fixture.first.consumeCalls).isEqualTo(1);
        assertThat(fixture.second.consumeCalls).isEqualTo(2);
        assertThat(fixture.ordinary.getAmountAsLong()).isEqualTo(7L);
        assertThat(retry.tryAttempt(3L, 2L)).isFalse();
        restored.invalidate();
        fixture.assertReleased(0L, 5L * (duration - 1L));
    }

    @ParameterizedTest
    @CsvSource({"1,SYNC", "2,SYNC", "1,SEMI_SYNC", "2,SEMI_SYNC", "1,ASYNC", "2,ASYNC"})
    void settled_native_prefetch_notification_save_throw_restores_credit_and_retry_does_not_reconsume(
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
        assertThat(fixture.runtime.tickCount()).isZero();
        assertThat(fixture.runtime.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.READY);
        long secondRemaining = 5L * (duration - 1L);
        assertThat(fixture.ordinary.getAmountAsLong()).isZero();
        fixture.assertReservations(0L, secondRemaining);
        assertThat(fixture.first.consumeCalls).isEqualTo(1);
        assertThat(fixture.second.consumeCalls).isEqualTo(1);
        CompoundTag runtimeSave = saved.get().getCompound("runtime");
        assertThat(allocations(runtimeSave)).isEqualTo(Map.of("first", 0L, "second", secondRemaining));
        assertThat(runtimeSave.getCompound("execution_commit").getString("phase"))
                .isEqualTo("READY");
        CraftingRuntime restored = fixture.restore(saved.get());
        LevelStub.setGameTime(fixture.controller.getLevel(), 2L);
        var retry = restored.ensureInstalledProgram();
        if (mode != MachineWorkMode.SYNC) assertThat(restored.prepareInstalledTick(2L)).isTrue();
        assertThat(retry.tryAttempt(2L, 2L)).isTrue();
        assertThat(retry.advance()).isTrue();
        assertThat(fixture.first.consumeCalls).isEqualTo(1);
        assertThat(fixture.second.consumeCalls).isEqualTo(1);
        assertThat(fixture.ordinary.getAmountAsLong()).isEqualTo(7L);
        assertThat(retry.tryAttempt(3L, 2L)).isFalse();
        assertThat(restored.installCompiledProgram(compilation.identity(), compilation.compile())).isNull();
        restored.invalidate();
        fixture.assertReleased(0L, secondRemaining);
        assertThat(fixture.first.releaseCalls).isZero();
        assertThat(fixture.second.releaseCalls).isEqualTo(duration == 1 ? 0 : 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void uncertain_start_checkpoint_restores_fresh_native_ports_and_releases_the_owned_prefix_once(boolean secondKey) {
        Fixture original = new Fixture(2);
        AtomicReference<CompoundTag> checkpoint = new AtomicReference<>();
        ObservedFlux boundary = secondKey ? original.second : original.first;
        boundary.notification.set(() -> {
            checkpoint.set(original.saveAll());
            throw new IllegalStateException("checkpoint inside native start reservation");
        });
        original.start(false);
        CompoundTag saved = checkpoint.get();
        assertThat(saved).isNotNull();
        assertThat(CraftingRuntime.requiresTopologyForLoad(saved.getCompound("runtime"))).isTrue();
        assertThat(allocations(saved.getCompound("runtime")).get("first")).isEqualTo(4L);
        assertThat(allocations(saved.getCompound("runtime")).getOrDefault("second", 0L)).isEqualTo(secondKey ? 6L : 0L);

        Fixture fresh = new Fixture(2, false);
        CraftingRuntime restored = fresh.restore(saved);
        fresh.assertNoReplay(restored);
        assertThat(fresh.first.buffer.reserved()).isEqualTo(4L);
        assertThat(fresh.second.buffer.reserved()).isEqualTo(secondKey ? 6L : 0L);
        restored.invalidate();
        restored.invalidate();
        fresh.assertReleased(4L, secondKey ? 6L : 0L);
        assertThat(fresh.first.releaseCalls).isEqualTo(1);
        assertThat(fresh.second.releaseCalls).isEqualTo(secondKey ? 1 : 0);
        assertThat(fresh.first.networkCommits).isZero();
        assertThat(fresh.second.networkCommits).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void restored_payment_is_visible_during_owner_publication_save_and_pause(boolean waitingFinish) {
        Fixture original = new Fixture(waitingFinish ? 1 : 2);
        assertThat(original.start(false)).isTrue();
        if (waitingFinish) {
            for (int slot = 0; slot < original.output.itemHandler().size(); slot++) {
                original.output.itemHandler().setContents(slot, new ItemStack(Items.COBBLESTONE), 64L);
            }
            assertThat(original.attempt(MachineWorkMode.SYNC).advance()).isTrue();
            assertThat(original.runtime.finishPending()).isTrue();
        } else {
            original.ordinaryNotification.set(() -> { throw new IllegalStateException("native ordinary notification"); });
            assertThat(original.attempt(MachineWorkMode.SYNC).advance()).isFalse();
        }
        CompoundTag checkpoint = original.saveAll();
        RecipeCommitLedger.State expected = original.runtime.ledger().state();
        Fixture fresh = new Fixture(waitingFinish ? 1 : 2, false);
        AtomicReference<CompoundTag> reentrantSave = new AtomicReference<>();
        AtomicReference<RecipeCommitLedger> observedPayment = new AtomicReference<>();
        // Include a zero-balance published key: finish ownership publication must also see the final ledger.
        fresh.first.ownerNotification.set(owner -> {
            assertThat(owner.ledger().state()).isEqualTo(expected);
            observedPayment.set(owner.ledger());
            owner.pause();
            reentrantSave.set(save(owner));
            owner.tick();
            assertThat(owner.ledger().state()).isEqualTo(expected);
        });
        CraftingRuntime restored = fresh.restore(checkpoint);
        assertThat(observedPayment.get()).isSameAs(restored.ledger());
        assertThat(reentrantSave.get()).isNotNull();
        assertThat(restored.ledger().state()).isEqualTo(expected);
        long ordinary = fresh.ordinary.getAmountAsLong();
        var secondReload = new CraftingRuntime(fresh.controller, fresh.controller.componentRuntime());
        secondReload.load(reentrantSave.get(), null, LOOKUP);
        assertThat(secondReload.ledger().state()).isEqualTo(expected);
        secondReload.tick();
        assertThat(fresh.ordinary.getAmountAsLong()).isEqualTo(ordinary);
        assertThat(fresh.first.consumeCalls).isZero();
        assertThat(fresh.second.consumeCalls).isZero();
        if (waitingFinish) assertThat(secondReload.finishPending()).isTrue();
        else fresh.assertNoReplay(secondReload);
    }

    @ParameterizedTest
    @EnumSource(MachineWorkMode.class)
    void native_prefetch_owner_replacement_stops_the_old_debit_loop_at_each_key(MachineWorkMode mode) {
        ConfigTestSupport.setMachineWorkMode(mode);
        for (boolean lastKey : new boolean[] {false, true}) {
            Fixture fixture = new Fixture(2, !lastKey);
            assertThat(fixture.start(false)).isTrue();
            RecipeCommitLedger oldPayment = fixture.runtime.ledger();
            AtomicReference<RecipeCommitLedger> replacementPayment = new AtomicReference<>();
            AtomicReference<Map<String, Long>> replacementAllocations = new AtomicReference<>();
            (lastKey ? fixture.second : fixture.first).ownerNotification.set(owner -> {
                owner.invalidate();
                assertThat(owner.start(fixture.recipe, 1L).isCrafting()).isTrue();
                replacementPayment.set(owner.ledger());
                replacementAllocations.set(allocations(save(owner)));
            });
            assertThat(fixture.attempt(mode).advance()).isFalse();
            assertThat(fixture.runtime.active()).isTrue();
            assertThat(fixture.runtime.ledger()).isSameAs(replacementPayment.get()).isNotSameAs(oldPayment);
            assertThat(fixture.runtime.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.READY);
            assertThat(fixture.runtime.failure()).isNull();
            assertThat(fixture.runtime.tickCount()).isZero();
            assertThat(fixture.first.consumeCalls).isEqualTo(1);
            assertThat(fixture.second.consumeCalls).isEqualTo(lastKey ? 1 : 0);
            assertThat(allocations(save(fixture.runtime))).isEqualTo(replacementAllocations.get())
                    .isEqualTo(Map.of("first", 4L, "second", 6L));
            assertThat(save(fixture.runtime).getCompound("recipe").getCompound("data")
                    .getLong("prefetched_energy_tick_consumed")).isZero();
            assertThat(fixture.ordinary.getAmountAsLong()).isZero();
            assertThat(fixture.first.buffer.reserved()).isEqualTo(4L);
            assertThat(fixture.second.buffer.reserved()).isEqualTo(6L);
        }
    }

    private static CompoundTag save(CraftingRuntime runtime) {
        var output = new CompoundTag();
        runtime.save(output, LOOKUP);
        return output;
    }

    @ParameterizedTest
    @CsvSource({"save,false", "save,true", "throw,false", "throw,true", "cancel,false", "cancel,true",
            "cancel-throw,false", "cancel-throw,true"})
    void native_appflux_buffer_notification_publishes_owned_checkpoint_before_save_throw_or_cancel(
            String action, boolean pattern) {
        Fixture original = new Fixture(2, true, 10_000L, 15_000L);
        AtomicReference<CompoundTag> checkpoint = new AtomicReference<>();
        original.first.storageNotification.set(() -> {
            assertThat(original.first.buffer.amount()).isEqualTo(20_000L);
            assertThat(original.first.buffer.reserved()).isEqualTo(20_000L);
            assertThat(original.first.buffer.idleExcess()).isZero();
            checkpoint.set(original.saveAll());
            assertThat(allocations(checkpoint.get().getCompound("runtime"))).containsEntry("first", 20_000L);
            if (action.startsWith("cancel")) original.runtime.invalidate();
            if (action.endsWith("throw")) throw new IllegalStateException("native buffer onChange");
        });
        // The callback belongs to the actual local storage, before delegate.commit() can return.
        original.start(pattern);
        assertThat(checkpoint.get()).isNotNull();
        assertThat(checkpoint.get().getLong("first_network") + checkpoint.get().getCompound("first").getLong("amount"))
                .isEqualTo(40_000L);
        assertThat(checkpoint.get().getLong("second_network") + checkpoint.get().getCompound("second").getLong("amount"))
                .isEqualTo(60_000L);
        assertThat(original.first.networkCommits).isEqualTo(1);
        if (!action.equals("save")) assertThat(original.second.networkCommits).isZero();
        if (action.startsWith("cancel")) {
            assertThat(original.runtime.active()).isFalse();
            assertThat(original.first.buffer.reserved()).isZero();
            assertThat(original.first.buffer.idleExcess()).isEqualTo(20_000L);
            assertThat(original.first.releaseCalls).isEqualTo(1);
            original.runtime.invalidate();
            assertThat(original.first.releaseCalls).isEqualTo(1);
        } else if (action.equals("throw")) {
            assertThat(original.first.buffer.reserved()).isEqualTo(20_000L);
            original.assertNoReplay(original.runtime);
        }

        Fixture fresh = new Fixture(2, false, 10_000L, 15_000L);
        CraftingRuntime restored = fresh.restore(checkpoint.get());
        fresh.assertNoReplay(restored);
        assertThat(fresh.first.buffer.reserved()).isEqualTo(20_000L);
        assertThat(fresh.second.buffer.amount()).isZero();
        restored.invalidate();
        restored.invalidate();
        fresh.assertReleased(20_000L, 0L);
        assertThat(fresh.first.releaseCalls).isEqualTo(1);
        assertThat(fresh.second.releaseCalls).isZero();
        assertThat(FluxEnergyTicker.input(true, FluxEnergyBuffer.IDLE_DELAY_TICKS, fresh.first.buffer, null))
                .isEqualTo(TickRateModulation.SLOWER);
        assertThat(fresh.first.buffer.isIdleReady()).isTrue();
        assertThat(fresh.first.networkCommits).isZero();
        assertThat(fresh.second.networkCommits).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"throw", "replacement", "replacement-throw"})
    void continuation_zero_notification_cleans_detached_old_reservations_once_without_touching_replacement(String action) {
        Fixture fixture = new Fixture(2);
        assertThat(fixture.start(false)).isTrue();
        RecipeCommitLedger oldPayment = fixture.runtime.ledger();
        AtomicReference<RecipeCommitLedger> replacementPayment = new AtomicReference<>();
        fixture.first.continuationNotification.set(owner -> {
            if (action.startsWith("replacement")) {
                assertThat(owner.start(fixture.recipe, 1L).isCrafting()).isTrue();
                replacementPayment.set(owner.ledger());
            }
            if (action.endsWith("throw")) throw new IllegalStateException("continuation zero notification");
        });
        try {
            fixture.runtime.invalidate();
        } catch (IllegalStateException exception) {
            // Propagation is permitted only after cleanup; swallowing the notification is not the assertion.
            assertThat(exception).hasMessage("continuation zero notification");
        }
        assertThat(fixture.first.releaseCalls).isEqualTo(1);
        assertThat(fixture.second.releaseCalls).isEqualTo(1);
        assertThat(fixture.first.released).isEqualTo(4L);
        assertThat(fixture.second.released).isEqualTo(6L);
        if (action.startsWith("replacement")) {
            assertThat(fixture.runtime.active()).isTrue();
            assertThat(fixture.runtime.ledger()).isSameAs(replacementPayment.get()).isNotSameAs(oldPayment);
            assertThat(fixture.runtime.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.READY);
            assertThat(fixture.runtime.ledger().state().paidAttempts()).isEqualTo(1L);
            assertThat(fixture.runtime.failure()).isNull();
            assertThat(fixture.runtime.tickCount()).isZero();
            assertThat(allocations(save(fixture.runtime))).isEqualTo(Map.of("first", 4L, "second", 6L));
            assertThat(save(fixture.runtime).getCompound("recipe").getCompound("data")
                    .getLong("prefetched_energy_tick_consumed")).isZero();
            assertThat(fixture.first.consumeCalls).isZero();
            assertThat(fixture.second.consumeCalls).isZero();
            assertThat(fixture.first.buffer.reserved()).isEqualTo(4L);
            assertThat(fixture.second.buffer.reserved()).isEqualTo(6L);
            assertThat(fixture.first.buffer.idleExcess()).isEqualTo(4L);
            assertThat(fixture.second.buffer.idleExcess()).isEqualTo(6L);
            assertThat(fixture.first.network.amount() + fixture.first.buffer.amount()).isEqualTo(100L);
            assertThat(fixture.second.network.amount() + fixture.second.buffer.amount()).isEqualTo(100L);
            fixture.runtime.invalidate();
            fixture.runtime.invalidate();
            fixture.assertReleased(8L, 12L);
            assertThat(fixture.first.releaseCalls).isEqualTo(2);
            assertThat(fixture.second.releaseCalls).isEqualTo(2);
        } else {
            assertThat(fixture.runtime.active()).isFalse();
            fixture.runtime.invalidate();
            fixture.assertReleased(4L, 6L);
            assertThat(fixture.first.releaseCalls).isEqualTo(1);
            assertThat(fixture.second.releaseCalls).isEqualTo(1);
        }
        assertThat(fixture.ordinary.getAmountAsLong()).isZero();
    }

    private static Map<String, Long> allocations(CompoundTag saved) {
        Map<String, Long> result = new LinkedHashMap<>();
        var entries = saved.getCompound("recipe").getCompound("data")
                .getList("prefetched_energy_allocations", 10);
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag allocation = entries.getCompound(index);
            result.put(allocation.getString("key"), allocation.getLong("amount"));
        }
        return result;
    }

    /** Typed hosts keep real capability facets and storage identities at the runtime boundary.
     * @author howxu <dev@howxu.cn>
     */
    private static final class Fixture {
        private final AtomicReference<Runnable> ordinaryNotification = new AtomicReference<>();
        private final LongEnergyStorage ordinary = new LongEnergyStorage(100L, 100L,
                () -> notifyOnce(ordinaryNotification));
        private final ObservedFlux first;
        private final ObservedFlux second;
        private final ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(3, 0, 0));
        private final MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MACHINE, output);
        private final CraftingRuntime runtime;
        private final MachineRecipe recipe;

        private Fixture(int duration) {
            this(duration, true);
        }

        private Fixture(int duration, boolean registerRecipe) {
            this(duration, registerRecipe, 2L, 3L);
        }

        private Fixture(int duration, boolean registerRecipe, long firstPerTick, long secondPerTick) {
            first = new ObservedFlux("first", firstPerTick * duration);
            second = new ObservedFlux("second", secondPerTick * duration);
            var components = new ArrayList<>(controller.componentRuntime().components());
            components.add(new ProcessingComponent(null, new TestCapabilityHost(List.of(first, second,
                    new EnergyHatchCapability(ordinary, IOType.OUTPUT))), BlockPos.ZERO, BlockPos.ZERO, (String) null));
            controller.componentRuntime().replaceComponents(components);
            RuntimeTestFixtures.republish(controller);
            runtime = RuntimeTestFixtures.craftingRuntime(controller);
            recipe = RecipeTestSupport.create(MMCR.id("flux_boundary_" + duration), MACHINE, duration,
                    List.of(new EnergyRequirement(firstPerTick), new EnergyRequirement(secondPerTick),
                            new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 7L)),
                    List.of(new ItemStack(Items.IRON_NUGGET)));
            if (registerRecipe) RecipeRegistry.registerStatic(recipe);
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

        private void assertUncertainWithoutGrant() {
            assertThat(runtime.tickCount()).isZero();
            assertThat(runtime.finishPending()).isFalse();
            assertThat(runtime.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.ERROR_PAUSED);
            assertThat(runtime.ledger().state().paidAttempts()).isEqualTo(1L);
            assertThat(runtime.ledger().state().finalTickPaid()).isFalse();
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
            long ordinaryAmount = ordinary.getAmountAsLong();
            long firstAmount = first.buffer.amount();
            long secondAmount = second.buffer.amount();
            int firstCalls = first.consumeCalls;
            int secondCalls = second.consumeCalls;
            int firstNetworkCalls = first.networkCommits;
            int secondNetworkCalls = second.networkCommits;
            assertThat(owner.active()).isTrue();
            assertThat(paid.phase()).isEqualTo(RecipeCommitLedger.Phase.ERROR_PAUSED);
            owner.resume();
            LevelStub.setGameTime(controller.getLevel(), 2L);
            owner.tick();
            owner.finish();
            assertThat(owner.tickCount()).isZero();
            assertThat(owner.finishPending()).isFalse();
            assertThat(owner.ledger().state()).isEqualTo(paid);
            assertThat(ordinary.getAmountAsLong()).isEqualTo(ordinaryAmount);
            assertThat(first.buffer.amount()).isEqualTo(firstAmount);
            assertThat(second.buffer.amount()).isEqualTo(secondAmount);
            assertThat(first.consumeCalls).isEqualTo(firstCalls);
            assertThat(second.consumeCalls).isEqualTo(secondCalls);
            assertThat(output.itemHandler().amount(0)).isZero();
            assertThat(first.networkCommits).isEqualTo(firstNetworkCalls);
            assertThat(second.networkCommits).isEqualTo(secondNetworkCalls);
        }

        private CompoundTag saveAll() {
            var output = new CompoundTag();
            output.put("runtime", save(runtime));
            var firstTag = new CompoundTag();
            var secondTag = new CompoundTag();
            first.delegate.save(firstTag, LOOKUP);
            second.delegate.save(secondTag, LOOKUP);
            output.put("first", firstTag);
            output.put("second", secondTag);
            output.putLong("ordinary", ordinary.getAmountAsLong());
            output.putLong("first_network", first.network.amount());
            output.putLong("second_network", second.network.amount());
            return output;
        }

        private CraftingRuntime restore(CompoundTag saved) {
            first.delegate.load(saved.getCompound("first"), LOOKUP);
            second.delegate.load(saved.getCompound("second"), LOOKUP);
            ordinary.setAmount(saved.getLong("ordinary"));
            first.network.setAmount(saved.getLong("first_network"));
            second.network.setAmount(saved.getLong("second_network"));
            var restored = new CraftingRuntime(controller, controller.componentRuntime());
            restored.load(saved.getCompound("runtime"), null, LOOKUP);
            assertThat(restored.active()).isTrue();
            assertThat(allocations(save(restored))).isEqualTo(allocations(saved.getCompound("runtime")));
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
        private final AtomicReference<Runnable> storageNotification = new AtomicReference<>();
        private final AtomicReference<Consumer<CraftingRuntime>> ownerNotification = new AtomicReference<>();
        private final AtomicReference<Consumer<CraftingRuntime>> continuationNotification = new AtomicReference<>();
        private final FluxEnergyBuffer buffer = new FluxEnergyBuffer(() -> notifyOnce(storageNotification));
        private final LongValueStorage network;
        private final FluxEnergyInputCapability delegate;
        private final FluxEnergyInputCapability missing = new FluxEnergyInputCapability(new FluxEnergyBuffer(), "missing");
        private int networkCommits;
        private int consumeCalls;
        private int releaseCalls;
        private long released;
        private boolean rejectConsumption;

        private ObservedFlux(String key, long exactAmount) {
            long capacity = Math.max(100L, 2L * exactAmount);
            network = new LongValueStorage(capacity, capacity, null);
            network.setAmount(capacity);
            delegate = new FluxEnergyInputCapability(buffer, key, requested -> {
                if (requested != exactAmount) return Optional.empty();
                return Optional.of(() -> {
                    networkCommits++;
                    assertThat(network.extract(requested, false)).isEqualTo(requested);
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
        @Override public Optional<PrefetchPlan> planPrefetch(long amount) {
            return delegate.planPrefetch(amount).map(plan -> new PrefetchPlan(plan.amount(), () -> {
                CapabilityResult result = plan.operation().commit();
                if (result.success()) notifyOnce(notification);
                return result;
            }, plan.committedAmount()));
        }
        @Override public void restoreReservation(long amount) { delegate.restoreReservation(amount); }
        @Override public long releaseReservation(long amount) {
            releaseCalls++;
            long result = delegate.releaseReservation(amount);
            released += result;
            return result;
        }
        @Override public CapabilityResult consumeReservation(long amount) {
            consumeCalls++;
            if (rejectConsumption) return missing.consumeReservation(1L);
            return delegate.consumeReservation(amount);
        }
        @Override public void onRecipeReservationChanged(Object owner, long remaining, Runnable cancelOwner) {
            Consumer<CraftingRuntime> callback = ownerNotification.getAndSet(null);
            if (callback != null) callback.accept((CraftingRuntime) owner);
            // Observe the real runtime's already-published per-key native payment credit.
            if (consumeCalls > 0) notifyOnce(notification);
        }
        @Override public void onRecipeContinuationRequested(Object owner, long amount) {
            if (amount != 0L) return;
            Consumer<CraftingRuntime> callback = continuationNotification.getAndSet(null);
            if (callback != null) callback.accept((CraftingRuntime) owner);
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
