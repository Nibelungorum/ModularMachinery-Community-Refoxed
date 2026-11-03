package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.storage.LongValueStorage;
import cn.howxu.mmcr.api.capability.storage.FloatValueStorage;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.IntegrationTypeHelper;
import cn.howxu.mmcr.api.recipe.MachineComponent;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.InputScope;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.compat.ae2lt.LightningBridgeBootstrap;
import cn.howxu.mmcr.compat.ae2lt.LightningRecipeTypes;
import cn.howxu.mmcr.compat.ae2lt.LightningRequirement;
import cn.howxu.mmcr.compat.ae2lt.loaded.LightningCapability;
import cn.howxu.mmcr.compat.appliedflux.loaded.capability.FluxEnergyInputCapability;
import cn.howxu.mmcr.compat.appliedflux.loaded.storage.FluxEnergyBuffer;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.capability.SmartInterfaceCapability;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.storage.LongResourceStorage;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.function.LongFunction;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.util.ProblemReporter;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Controller admission and reservation boundary tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class PatternStartReservationTest {
    private static final BlockPos PATTERN_PORT = new BlockPos(3, 0, 0);

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        TestBootstrap.bootstrapCapabilities();
        TestBootstrap.registerType(new LevelType(MMCR.id("missing_coil"), Component.empty()));
        TestBootstrap.registerLevel(new MachineLevel(MMCR.id("missing_level"), MMCR.id("missing_coil"), 1,
                new BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()), ItemStack.EMPTY, ModifierDefinition.EMPTY));
    }

    @AfterEach
    void cleanup() {
        RecipeRegistry.clearForTesting();
    }

    @Test
    void inactive_or_unlinked_controllers_reject_admission() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe recipe = recipe("reservation_inactive", List.of());
        RecipeRegistry.registerStatic(recipe);

        assertThat(controller.reservePatternStart(PATTERN_PORT, List.of(), List.of()).status())
                .isEqualTo(PatternStartReservation.Status.UNAVAILABLE);

        formForPattern(controller, false);
        assertThat(controller.reservePatternStart(PATTERN_PORT, List.of(), List.of()).status())
                .isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void pattern_request_cannot_top_up_materials_from_another_input_group(boolean isolatedHost) {
        ItemInputBusBlockEntity host = RuntimeTestFixtures.itemInput(PATTERN_PORT);
        ItemInputBusBlockEntity other = RuntimeTestFixtures.itemInput(new BlockPos(4, 0, 0));
        host.setInputIsolated(isolatedHost);
        other.setInputIsolated(!isolatedHost);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), host, other);
        controller.componentRuntime().replaceLinkedPortPositions(Set.of(PATTERN_PORT));
        setItem(other.itemStorage(), 0, stack(Items.IRON_INGOT, 2));
        var requested = new LongResourceStorage<>(ItemResource.class, 1, 64L, ItemResource::isEmpty, () -> { });
        setItem(requested, 0, stack(Items.IRON_INGOT, 1));
        MachineRecipe recipe = recipe("pattern_request_group_boundary", List.of(input(Items.IRON_INGOT, 2)));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(runtime.preparePatternStart(recipe, 1L,
                List.of(new ItemBusCapability(requested, IOType.INPUT)), PATTERN_PORT)).isNull();
        assertThat(requested.amount(0)).isEqualTo(1L);
        assertThat(other.itemStorage().amount(0)).isEqualTo(2L);

        setItem(requested, 0, stack(Items.IRON_INGOT, 2));
        var prepared = runtime.preparePatternStart(recipe, 1L,
                List.of(new ItemBusCapability(requested, IOType.INPUT)), PATTERN_PORT);
        assertThat(prepared).isNotNull();
        assertThat(prepared.plan().inputScope().kind()).isEqualTo(isolatedHost
                ? InputScope.Kind.ISOLATED : InputScope.Kind.ORDINARY);
        assertThat(prepared.plan().inputScope().sources()).containsExactly(PATTERN_PORT);
        assertThat(runtime.reservePatternStart()).isTrue();
        assertThat(runtime.commitPatternStart(prepared)).isTrue();
        assertThat(requested.amount(0)).isZero();
        assertThat(other.itemStorage().amount(0)).isEqualTo(2L);
        assertThat(runtime.activeInputScope()).isEqualTo(prepared.plan().inputScope());
    }

    @Test
    void unpaid_pattern_plan_is_rejected_when_input_routing_changes() {
        ItemInputBusBlockEntity host = RuntimeTestFixtures.itemInput(PATTERN_PORT);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), host);
        controller.componentRuntime().replaceLinkedPortPositions(Set.of(PATTERN_PORT));
        var requested = new LongResourceStorage<>(ItemResource.class, 1, 64L, ItemResource::isEmpty, () -> { });
        setItem(requested, 0, stack(Items.IRON_INGOT, 1));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        var prepared = runtime.preparePatternStart(recipe("pattern_request_stale_scope", List.of(input(Items.IRON_INGOT, 1))),
                1L, List.of(new ItemBusCapability(requested, IOType.INPUT)), PATTERN_PORT);
        assertThat(prepared).isNotNull();
        assertThat(runtime.reservePatternStart()).isTrue();
        controller.setFullyInputIsolated(true);
        assertThat(runtime.commitPatternStart(prepared)).isFalse();
        assertThat(runtime.active()).isFalse();
        assertThat(requested.amount(0)).isEqualTo(1L);
    }

    @ParameterizedTest
    @CsvSource({"false, false", "true, false", "true, true"})
    void paid_pattern_lightning_is_not_requested_again_during_execution_or_reload(boolean reload, boolean saveDuringCommit) {
        try (var requirements = RequirementHandlerRegistry.openTestScope(); var outputs = OutputRegistry.openTestScope()) {
            LightningBridgeBootstrap.installForTesting(LightningBridgeBootstrap.selectForTesting(true));
            LightningRecipeTypes.register();
            var host = RuntimeTestFixtures.itemInput(PATTERN_PORT);
            var controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), host);
            controller.componentRuntime().replaceLinkedPortPositions(Set.of(PATTERN_PORT));
            var lightning = new LongResourceStorage<>(LightningTier.class, 2, 2000L, ignored -> false, null);
            lightning.setContents(0, LightningTier.HIGH_VOLTAGE, 1000L);
            lightning.setContents(1, LightningTier.EXTREME_HIGH_VOLTAGE, 100L);
            var items = new LongResourceStorage<>(ItemResource.class, 1, 64L, ItemResource::isEmpty, null);
            setItem(items, 0, stack(Items.IRON_INGOT, 1));
            var recipe = MachineRecipe.fromCanonical(MMCR.id("paid_lightning_pattern"), MMCR.id("test_cube"), 20,
                    List.of(input(Items.IRON_INGOT, 1),
                            LightningRequirement.input(LightningTier.HIGH_VOLTAGE, 1000L),
                            LightningRequirement.input(LightningTier.EXTREME_HIGH_VOLTAGE, 100L)),
                    List.of(), List.of(), 0, 1, false, false, false, Set.of());
            RecipeRegistry.registerStatic(recipe);
            var runtime = new CraftingRuntime(controller, controller.componentRuntime());
            var prepared = runtime.preparePatternStart(recipe, 1L, List.of(
                    new ItemBusCapability(items, IOType.INPUT), new LightningCapability(lightning, IOType.INPUT)), PATTERN_PORT);
            assertThat(prepared).isNotNull();
            assertThat(runtime.reservePatternStart()).isTrue();
            var lookup = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, lookup);
            var admitting = runtime;
            assertThat(runtime.commitPatternStart(prepared, transaction -> {
                if (saveDuringCommit) admitting.save(saved);
            })).isTrue();
            assertThat(lightning.amount(0)).isZero();
            assertThat(lightning.amount(1)).isZero();
            assertThat(items.amount(0)).isZero();
            assertThat(runtime.activeRecipe().inputConsumptionPlan().isValidFor(recipe.runtimeRequirements())).isTrue();
            if (reload) {
                if (!saveDuringCommit) runtime.save(saved);
                runtime = new CraftingRuntime(controller, controller.componentRuntime());
                runtime.load(TagValueInput.create(ProblemReporter.DISCARDING, lookup, saved.buildResult()), controller.resourceDomain());
                assertThat(runtime.active()).isTrue();
            }
            assertThat(runtime.installedTickRequirements()).isEmpty();
            if (saveDuringCommit) {
                assertThat(runtime.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.ERROR_PAUSED);
                assertThat(runtime.tick().isCrafting()).isFalse();
                assertThat(runtime.tickCount()).isZero();
                assertThat(runtime.failure()).isNotNull();
            } else {
                assertThat(runtime.tick().isCrafting()).isTrue();
                assertThat(runtime.tickCount()).isEqualTo(1);
                assertThat(runtime.failure()).isNull();
            }
        } finally {
            LightningBridgeBootstrap.resetForTesting();
        }
    }

    static Stream<Arguments> patternCallbackChanges() {
        return Stream.of(false, true).flatMap(factory -> Stream.of("full", "masked_local_roundtrip", "unlink",
                "linked_roundtrip", "capability", "catalog", "module_roundtrip", "lifecycle")
                .flatMap(change -> Stream.of(false, true).map(pending -> Arguments.of(factory, change, pending))));
    }

    @ParameterizedTest
    @MethodSource("patternCallbackChanges")
    void pattern_before_start_only_recaptures_pure_isolation_changes(boolean factory, String change, boolean pending) throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            AtomicReference<PaidPatternFixture> owner = new AtomicReference<>();
            AtomicInteger callbacks = new AtomicInteger();
            RecipeBehavior behavior = RecipeBehavior.builder().beforeStart(context -> {
                callbacks.incrementAndGet();
                PaidPatternFixture fixture = owner.get();
                MachineControllerBlockEntity controller = fixture.controller;
                controller.setFullyInputIsolated(true);
                var components = controller.componentRuntime();
                switch (change) {
                    case "masked_local_roundtrip" -> {
                        ItemInputBusBlockEntity host = (ItemInputBusBlockEntity) components.components().stream()
                                .filter(component -> PATTERN_PORT.equals(component.getPos())).findFirst().orElseThrow().getContainer();
                        host.setInputIsolated(true);
                        controller.onInputIsolationChanged();
                        host.setInputIsolated(false);
                        controller.onInputIsolationChanged();
                    }
                    case "unlink" -> components.replaceLinkedPortPositions(Set.of());
                    case "linked_roundtrip" -> {
                        var linked = components.linkedPortPositions();
                        components.replaceLinkedPortPositions(Set.of());
                        components.replaceLinkedPortPositions(linked);
                    }
                    case "capability" -> {
                        ItemInputBusBlockEntity replacement = RuntimeTestFixtures.itemInput(PATTERN_PORT);
                        replacement.setLevel(controller.getLevel());
                        components.replaceComponents(components.components().stream().map(component ->
                                PATTERN_PORT.equals(component.getPos()) ? new ProcessingComponent(
                                        new MachineComponent(replacement.kind(), replacement.ioType()), replacement,
                                        PATTERN_PORT, PATTERN_PORT, (String) null) : component).toList());
                    }
                    case "catalog" -> RecipeRegistry.clearForTesting();
                    case "module_roundtrip" -> {
                        var status = components.moduleConnectionStatus();
                        int installed = components.installedModuleCount();
                        components.replaceModuleConnectionState(ModuleConnectionStatus.connected(MMCR.id("pattern_callback_host")), 1);
                        components.replaceModuleConnectionState(status, installed);
                    }
                    case "lifecycle" -> controller.setMachine(null);
                }
            }).build();
            PaidPatternFixture fixture = new PaidPatternFixture("pattern_callback_fence", behavior);
            owner.set(fixture);
            if (change.equals("masked_local_roundtrip")) fixture.controller.setFullyInputIsolated(true);
            List<MachineCapability> request = List.of(new ItemBusCapability(fixture.inputs, IOType.INPUT));
            PatternStartReservation admission;
            CraftingRuntime runtime = fixture.runtime;
            if (factory) {
                FactoryRuntime lanes = new FactoryRuntime();
                lanes.ensureBaseLane(fixture.controller);
                var lane = pending ? lanes.reserveExactPatternStart(fixture.recipe, 1L, request, PATTERN_PORT)
                        : lanes.reservePatternStart(fixture.recipe, 1L, request, PATTERN_PORT);
                admission = lane == null ? PatternStartReservation.unavailable() : reservation(fixture.recipe, lanes, lane);
                if (lane != null) runtime = lane.runtime();
            } else {
                admission = pending ? fixture.controller.reserveExactPatternStart(PATTERN_PORT, fixture.recipe.id(), List.of(), 1L, request)
                        : fixture.controller.reservePatternStart(PATTERN_PORT, List.of(), request);
            }
            assertThat(callbacks).hasValue(1);
            if (change.equals("full") || change.equals("masked_local_roundtrip")) {
                assertThat(admission.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
                assertThat(admission.commit()).isTrue();
                assertThat(runtime.activeInputScope()).isEqualTo(new InputScope(InputScope.Kind.ISOLATED, List.of(PATTERN_PORT)));
                assertThat(fixture.inputs.amount(0)).isEqualTo(1L);
                assertThat(fixture.network.amount()).isEqualTo(96L);
                assertThat(fixture.buffer.reserved()).isEqualTo(4L);
            } else {
                assertThat(admission.status()).isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
                assertThat(runtime.active()).isFalse();
                assertThat(fixture.inputs.amount(0)).isEqualTo(2L);
                assertThat(fixture.network.amount()).isEqualTo(100L);
                assertThat(fixture.buffer.amount()).isZero();
                assertThat(fixture.buffer.reserved()).isZero();
                assertThat(fixture.networkCommits).hasValue(0);
            }
        }
    }

    @Test
    void normal_controller_reserves_one_slot_and_releases_it_on_rollback() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        formForPattern(controller, true);
        MachineRecipe recipe = recipe("reservation_normal", List.of());
        RecipeRegistry.registerStatic(recipe);

        PatternStartReservation first = controller.reservePatternStart(PATTERN_PORT, List.of(), List.of());
        assertThat(first.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
        assertThat(controller.reservePatternStart(PATTERN_PORT, List.of(), List.of()).status())
                .isEqualTo(PatternStartReservation.Status.UNAVAILABLE);

        first.rollback();
        assertThat(first.rolledBack()).isTrue();
        PatternStartReservation retried = controller.reservePatternStart(PATTERN_PORT, List.of(), List.of());
        assertThat(retried.commit()).isTrue();
        assertThat(retried.status()).isEqualTo(PatternStartReservation.Status.COMMITTED);
        assertThat(controller.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());
    }

    @Test
    void exactStartRejectsAvailablePrefixUntilTheCompleteRequestFits() {
        PendingStartFixture fixture = new PendingStartFixture("pending_prefix", RecipeBehavior.defaults(),
                List.of(input(Items.IRON_INGOT, 1), new EnergyRequirement(1L)));
        var request = fixture.view(10L, stack(Items.IRON_INGOT, 10));
        PatternStartReservation rejected = fixture.controller.reserveExactPatternStart(PATTERN_PORT, fixture.recipe.id(), List.of(),
                10L, request);
        assertThat(rejected.status()).isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
        assertThat(fixture.plannedCopies).containsExactly(10L);
        assertThat(fixture.views.getLast().amount(0)).isEqualTo(10L);
        assertThat(fixture.energy.energyStorage().getAmountAsLong()).isEqualTo(3L);
        fixture.energy.energyStorage().setAmount(10L);
        PatternStartReservation reservation = fixture.controller.reserveExactPatternStart(PATTERN_PORT, fixture.recipe.id(), List.of(),
                10L, request);
        assertThat(reservation.commit()).isTrue();
        assertThat(fixture.runtime.parallelism()).isEqualTo(10L);
        assertThat(fixture.views.getLast().amount(0)).isZero();
    }

    @Test
    void alternativeInputsPayTheWholeExactRequest() {
        AtomicInteger beforeStart = new AtomicInteger();
        PendingStartFixture fixture = new PendingStartFixture("pending_alternative",
                RecipeBehavior.builder().beforeStart(context -> beforeStart.incrementAndGet()).build(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.OAK_PLANKS, Items.BIRCH_PLANKS),
                        2, ItemStack.EMPTY), new EnergyRequirement(1L)));
        PatternStartReservation reservation = fixture.controller.reserveExactPatternStart(PATTERN_PORT, fixture.recipe.id(), List.of(),
                3L, fixture.view(3L, stack(Items.OAK_PLANKS, 3), stack(Items.BIRCH_PLANKS, 3)));

        assertThat(beforeStart).hasValue(1);
        assertThat(reservation.parallelism()).isEqualTo(3L);
        assertThat(fixture.plannedCopies).containsExactly(3L);
        assertThat(fixture.views.getLast().amount(0)).isEqualTo(3L);
        assertThat(fixture.views.getLast().amount(1)).isEqualTo(3L);
        assertThat(reservation.commit()).isTrue();
        assertThat(fixture.views.getLast().amount(0)).isZero();
        assertThat(fixture.views.getLast().amount(1)).isZero();
    }

    @Test
    void exactStartOnlyPreparesTheBoundRecipeAndKeepsItsRequestView() {
        PendingStartFixture fixture = new PendingStartFixture("pending_candidate_success", RecipeBehavior.defaults(),
                List.of(input(Items.IRON_INGOT, 1), new EnergyRequirement(1L)));
        MachineRecipe blocked = MachineRecipe.fromCanonical(MMCR.id("pending_candidate_blocked"), MMCR.id("test_cube"),
                20, List.of(input(Items.GOLD_INGOT, 1)), List.of(), List.of(), Integer.MIN_VALUE, 2,
                false, true, false, Set.of());
        RecipeRegistry.clearForTesting();
        RecipeRegistry.registerStatic(blocked);
        RecipeRegistry.registerStatic(fixture.recipe);
        assertThat(fixture.controller.recipesForMachine().getFirst()).isEqualTo(blocked);

        PatternStartReservation reservation = fixture.controller.reserveExactPatternStart(PATTERN_PORT, fixture.recipe.id(), List.of(),
                3L, fixture.view(3L, stack(Items.IRON_INGOT, 3)));

        assertThat(reservation.recipe()).isSameAs(fixture.recipe);
        assertThat(fixture.plannedCopies).containsExactly(3L);
        assertThat(reservation.parallelism()).isEqualTo(3L);
        assertThat(reservation.commit()).isTrue();
        assertThat(fixture.views.getLast().amount(0)).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exactRejectionDoesNotFallbackOrRepeatBeforeStart(boolean materialLimited) {
        AtomicInteger beforeStart = new AtomicInteger();
        PendingStartFixture fixture = new PendingStartFixture("pending_bounded",
                RecipeBehavior.builder().beforeStart(context -> {
                    beforeStart.incrementAndGet();
                    context.setDuration(7);
                }).build(), List.of(input(Items.IRON_INGOT, 1), new EnergyRequirement(1L)));
        PatternStartReservation reservation = fixture.controller.reserveExactPatternStart(PATTERN_PORT, fixture.recipe.id(), List.of(),
                10L, fixture.view(10L, stack(Items.IRON_INGOT, materialLimited ? 2 : 10)));

        assertThat(beforeStart).hasValue(1);
        assertThat(fixture.plannedCopies).containsExactly(10L);
        assertThat(fixture.views.getFirst().amount(0)).isEqualTo(materialLimited ? 2L : 10L);
        assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
        assertThat(reservation.parallelism()).isZero();
        assertThat(fixture.runtime.pendingPatternRecipeId()).isNull();
        assertThat(fixture.controller.reserveExactPatternStart(PATTERN_PORT, fixture.recipe.id(), List.of(), 1L,
                fixture.view(1L, stack(Items.IRON_INGOT, 1))).commit()).isTrue();
        assertThat(beforeStart).hasValue(2);
        assertThat(fixture.runtime.totalTick()).isEqualTo(7);
    }

    @Test
    void exactFactoryStartReservesOneLaneAndCountsOtherReservations() {
        PendingStartFixture fixture = new PendingStartFixture("pending_factory", RecipeBehavior.defaults(),
                List.of(input(Items.IRON_INGOT, 1), new EnergyRequirement(1L)));
        FactoryRuntime factory = new FactoryRuntime();
        factory.ensureBaseLane(fixture.controller);
        factory.setLaneLimit(3);
        var request = fixture.view(3L, stack(Items.IRON_INGOT, 3));
        var first = factory.reserveExactPatternStart(fixture.recipe, 3L, request, PATTERN_PORT);
        assertThat(first).isNotNull();
        assertThat(first.laneId()).isEqualTo("base");
        assertThat(first.preparedStart().plan().parallelism()).isEqualTo(3L);
        assertThat(factory.laneCount()).isEqualTo(1);
        var second = factory.reserveExactPatternStart(fixture.recipe, 3L, request, PATTERN_PORT);
        assertThat(second).isNotNull();
        assertThat(second.laneId()).isNotEqualTo(first.laneId());
        int attempts = fixture.plannedCopies.size();
        assertThat(factory.reserveExactPatternStart(fixture.recipe, 3L, request, PATTERN_PORT)).isNull();
        assertThat(fixture.plannedCopies).hasSize(attempts);
        factory.releasePatternStart(first);
        factory.releasePatternStart(second);
    }

    @Test
    void blockedExactFactoryPreparesOnlyOneApplicableIdleLane() {
        AtomicInteger beforeStart = new AtomicInteger();
        Identifier blockedId = MMCR.id("pending_factory_blocked_once");
        PendingStartFixture fixture = new PendingStartFixture("pending_factory_blocked_once",
                RecipeBehavior.builder().beforeStart(context -> {
                    if (context.recipeId().equals(blockedId)) {
                        beforeStart.incrementAndGet();
                        context.cancel();
                    }
                }).build(), List.of());
        FactoryRuntime factory = new FactoryRuntime();
        factory.ensureBaseLane(fixture.controller);
        factory.setLaneLimit(32);
        MachineRecipe warmup = MachineRecipe.fromCanonical(MMCR.id("pending_factory_warmup"), MMCR.id("test_cube"),
                20, List.of(), List.of(), List.of(), 0, 0, false, true, false, Set.of());
        var reservations = factory.reservePatternStarts(warmup, 160L, List.of(), PATTERN_PORT);
        assertThat(reservations).hasSize(16);
        reservations.forEach(factory::releasePatternStart);

        assertThat(factory.reserveExactPatternStart(fixture.recipe, 10L, List.of(), PATTERN_PORT)).isNull();

        assertThat(beforeStart).hasValue(1);
        assertThat(factory.laneCount()).isEqualTo(16);
        assertThat(fixture.plannedCopies).isEmpty();
    }

    @Test
    void exactBindingNeverRunsUnrelatedCandidateHooks() {
        List<Identifier> preparedRecipes = new ArrayList<>();
        Identifier viableId = MMCR.id("pending_budget_viable");
        PendingStartFixture fixture = new PendingStartFixture("pending_budget_viable",
                RecipeBehavior.builder().beforeStart(context -> {
                    preparedRecipes.add(context.recipeId());
                    if (!context.recipeId().equals(viableId)) context.cancel();
                }).build(), List.of(input(Items.IRON_INGOT, 1), new EnergyRequirement(1L)));
        for (int index = 0; index < 9; index++) {
            RecipeRegistry.registerStatic(MachineRecipe.fromCanonical(MMCR.id("pending_budget_blocked_" + index),
                    MMCR.id("test_cube"), 20, List.of(), List.of(), List.of(), -100 + index, 2,
                    false, true, false, Set.of()));
        }
        PatternStartReservation admitted = fixture.controller.reserveExactPatternStart(PATTERN_PORT, fixture.recipe.id(),
                List.of(), 3L, fixture.view(3L, stack(Items.IRON_INGOT, 3)));
        assertThat(admitted.recipe()).isSameAs(fixture.recipe);
        assertThat(preparedRecipes).containsExactly(viableId);
        assertThat(admitted.commit()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"false,close", "false,rollback", "false,commit", "false,cancel",
            "true,close", "true,rollback", "true,commit", "true,cancel"})
    void exactFluxPrefetchRejectsOversizeThenConservesOwnershipOnExplicitRetry(boolean materialLimited,
                                                                                String outcome) throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            AtomicInteger beforeStart = new AtomicInteger();
            PendingStartFixture fixture = new PendingStartFixture("pending_flux_fallback",
                    RecipeBehavior.builder().beforeStart(context -> {
                        beforeStart.incrementAndGet();
                        context.setDuration(2);
                    }).build(), List.of(input(Items.IRON_INGOT, 1), new EnergyRequirement(1L)));
            LongValueStorage network = new LongValueStorage(3L, 3L, null);
            network.setAmount(3L);
            AtomicReference<Runnable> notification = new AtomicReference<>();
            FluxEnergyBuffer buffer = new FluxEnergyBuffer(() -> {
                Runnable action = notification.getAndSet(null);
                if (action != null) action.run();
            });
            List<Long> prefetchAttempts = new ArrayList<>();
            var flux = new FluxEnergyInputCapability(buffer, "pending-limited", requested -> {
                prefetchAttempts.add(requested);
                if (requested > network.amount()) return Optional.empty();
                return Optional.of(transaction -> {
                    assertThat(network.extract(requested, transaction)).isEqualTo(requested);
                    return CapabilityResult.successful();
                });
            });
            var components = new ArrayList<>(fixture.controller.componentRuntime().components().stream()
                    .filter(component -> component.getContainer() != fixture.energy).toList());
            components.add(new ProcessingComponent(null, new PatternFluxHost(flux), BlockPos.ZERO, BlockPos.ZERO,
                    (String) null));
            fixture.controller.componentRuntime().replaceComponents(components);
            formForPattern(fixture.controller, true);
            LongFunction<List<MachineCapability>> views = copies -> fixture.view(copies,
                    stack(Items.IRON_INGOT, materialLimited && copies == 10L ? 3 : (int) copies));
            PatternStartReservation oversized = fixture.controller.reserveExactPatternStart(PATTERN_PORT, fixture.recipe.id(), List.of(),
                    10L, views.apply(10L));
            assertThat(oversized.status()).isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
            assertThat(network.amount()).isEqualTo(3L);
            assertThat(buffer.reserved()).isZero();
            PatternStartReservation reservation = fixture.controller.reserveExactPatternStart(PATTERN_PORT, fixture.recipe.id(), List.of(),
                    1L, views.apply(1L));

            assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
            assertThat(reservation.parallelism()).isEqualTo(1L);
            assertThat(beforeStart).hasValue(2);
            assertThat(fixture.plannedCopies).containsExactly(10L, 1L);
            assertThat(prefetchAttempts).containsExactlyElementsOf(materialLimited ? List.of(6L, 2L) : List.of(20L, 2L));
            assertThat(network.amount()).isEqualTo(3L);
            assertThat(buffer.amount()).isZero();
            assertThat(buffer.reserved()).isZero();
            if (outcome.equals("close")) {
                reservation.close();
            } else if (outcome.equals("rollback")) {
                assertThat(reservation.commit(transaction -> {
                    assertThat(network.amount()).isEqualTo(1L);
                    assertThat(buffer.reserved()).isEqualTo(2L);
                    throw new IllegalStateException("abort pending Flux payment");
                })).isFalse();
            } else {
                if (outcome.equals("cancel")) notification.set(fixture.runtime::invalidate);
                assertThat(reservation.commit()).isTrue();
                assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.COMMITTED);
                assertThat(fixture.views.getLast().amount(0)).isZero();
                assertThat(network.amount()).isEqualTo(1L);
                assertThat(buffer.amount()).isEqualTo(2L);
                assertThat(buffer.reserved()).isEqualTo(outcome.equals("cancel") ? 0L : 2L);
                fixture.runtime.invalidate();
                reservation.close();
                assertThat(buffer.reserved()).isZero();
                assertThat(buffer.idleExcess()).isEqualTo(2L);
                assertThat(network.amount() + buffer.amount()).isEqualTo(3L);
            }
            if (outcome.equals("close") || outcome.equals("rollback")) {
                assertThat(reservation.rolledBack()).isTrue();
                assertThat(fixture.views.getLast().amount(0)).isEqualTo(1L);
                assertThat(network.amount()).isEqualTo(3L);
                assertThat(buffer.amount()).isZero();
                assertThat(buffer.reserved()).isZero();
                assertThat(fixture.runtime.pendingPatternRecipeId()).isNull();
                var retried = fixture.controller.reserveExactPatternStart(PATTERN_PORT, fixture.recipe.id(), List.of(), 1L, views.apply(1L));
                assertThat(retried.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
                retried.close();
            }
            assertThat(fixture.views.getFirst().amount(0)).isEqualTo(materialLimited ? 3L : 10L);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exactStartRollbackReleasesSlot(boolean factoryLane) {
        PendingStartFixture fixture = new PendingStartFixture("pending_rollback", RecipeBehavior.defaults(),
                List.of(input(Items.IRON_INGOT, 1), new EnergyRequirement(1L)));
        FactoryRuntime factory = new FactoryRuntime();
        factory.ensureBaseLane(fixture.controller);
        Supplier<PatternStartReservation> reserve = () -> {
            if (!factoryLane) return fixture.controller.reserveExactPatternStart(PATTERN_PORT, fixture.recipe.id(), List.of(), 3L,
                    fixture.view(3L, stack(Items.IRON_INGOT, 3)));
            var lane = factory.reserveExactPatternStart(fixture.recipe, 3L,
                    fixture.view(3L, stack(Items.IRON_INGOT, 3)), PATTERN_PORT);
            return lane == null ? PatternStartReservation.unavailable() : reservation(fixture.recipe, factory, lane);
        };
        PatternStartReservation first = reserve.get();
        assertThat(first.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
        assertThat(reserve.get().status()).isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
        first.close();
        PatternStartReservation retried = reserve.get();
        assertThat(retried.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
        var paidView = fixture.views.getLast();
        assertThat(retried.commit(transaction -> { throw new IllegalStateException("rollback pending prefix"); }))
                .isFalse();
        assertThat(retried.rolledBack()).isTrue();
        assertThat(paidView.amount(0)).isEqualTo(3L);
        assertThat(fixture.energy.energyStorage().getAmountAsLong()).isEqualTo(3L);
        assertThat(reserve.get().commit()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"catalog", "linked", "structure"})
    void exactStartRejectsStalePreparation(String change) {
        PendingStartFixture fixture = new PendingStartFixture("pending_stale", RecipeBehavior.defaults(),
                List.of(input(Items.IRON_INGOT, 1), new EnergyRequirement(1L)));
        PatternStartReservation reservation = fixture.controller.reserveExactPatternStart(PATTERN_PORT, fixture.recipe.id(), List.of(),
                3L, fixture.view(3L, stack(Items.IRON_INGOT, 3)));
        assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
        switch (change) {
            case "catalog" -> RecipeRegistry.clearForTesting();
            case "linked" -> fixture.controller.componentRuntime().replaceLinkedPortPositions(Set.of());
            case "structure" -> fixture.controller.setFormed(false);
        }
        assertThat(reservation.commit()).isFalse();
        assertThat(fixture.views.getLast().amount(0)).isEqualTo(3L);
        assertThat(fixture.runtime.active()).isFalse();
    }

    @Test
    void effectivePatternRecipeDoesNotPrepareOrRunBeforeStart() {
        AtomicInteger beforeStart = new AtomicInteger();
        PendingStartFixture fixture = new PendingStartFixture("pending_effective",
                RecipeBehavior.builder().beforeStart(context -> {
                    beforeStart.incrementAndGet();
                    context.setDuration(1);
                }).build(), List.of());
        fixture.controller.componentRuntime().replaceModifiers(Map.of("effective", List.of(
                MachineModifier.numeric("parallelism", "machine", 9D, "add", false),
                MachineModifier.numeric("duration", "input", 0.2D, "multiply", false))));
        var effective = fixture.controller.effectivePatternRecipe(fixture.recipe);
        assertThat(effective).isNotNull();
        assertThat(effective.duration()).isEqualTo(4);
        assertThat(effective.parallelismLimit()).isEqualTo(10L);
        assertThat(effective.recipeThreadLimit()).isEqualTo(2);
        assertThat(beforeStart).hasValue(0);
        assertThat(fixture.runtime.pendingPatternRecipeId()).isNull();
        var reservation = fixture.controller.reserveExactPatternStart(PATTERN_PORT, fixture.recipe.id(), List.of(), 1L,
                fixture.view(1L));
        assertThat(reservation.commit()).isTrue();
        assertThat(beforeStart).hasValue(1);
        assertThat(fixture.controller.effectivePatternRecipe(fixture.recipe)).isNotNull();
    }

    @Test
    void successful_linked_controller_admissions_rotate_without_reordering_the_provider_group() {
        MachineControllerBlockEntity first = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineControllerBlockEntity second = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        formForPattern(first, true);
        formForPattern(second, true);
        MachineRecipe recipe = recipe("reservation_rotation", List.of());
        RecipeRegistry.registerStatic(recipe);
        AtomicInteger cursor = new AtomicInteger();

        PatternStartReservation firstStart = MachineControllerBlockEntity.reserveNextPatternStart(
                List.of(first, second), cursor, PATTERN_PORT, List.of(), List.of());
        assertThat(firstStart.commit()).isTrue();
        assertThat(cursor).hasValue(1);
        PatternStartReservation secondStart = MachineControllerBlockEntity.reserveNextPatternStart(
                List.of(first, second), cursor, PATTERN_PORT, List.of(), List.of());

        assertThat(firstStart.recipe()).isEqualTo(recipe);
        assertThat(secondStart.commit()).isTrue();
        assertThat(first.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());
        assertThat(second.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());
    }

    @Test
    void linked_controller_batch_reserves_every_controller_before_advancing_rotation() {
        MachineControllerBlockEntity first = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineControllerBlockEntity second = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        formForPattern(first, true);
        formForPattern(second, true);
        MachineRecipe recipe = recipe("reservation_batch_rotation", List.of());
        RecipeRegistry.registerStatic(recipe);
        AtomicInteger cursor = new AtomicInteger();

        PatternStartBatchReservation batch = MachineControllerBlockEntity.reserveNextPatternStarts(
                List.of(first, second), cursor, PATTERN_PORT, List.of(), 2L, ignored -> List.of());

        assertThat(cursor).hasValue(0);
        assertThat(batch.parallelism()).isEqualTo(2L);
        assertThat(batch.commit()).isTrue();
        assertThat(cursor).hasValue(0);
        assertThat(first.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());
        assertThat(second.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());
    }

    @Test
    void linked_rotation_skips_an_unavailable_candidate_before_committing_the_next_controller() {
        MachineControllerBlockEntity unavailable = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineControllerBlockEntity available = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        formForPattern(available, true);
        MachineRecipe recipe = recipe("reservation_skip_unavailable", List.of());
        RecipeRegistry.registerStatic(recipe);
        AtomicInteger cursor = new AtomicInteger();

        PatternStartReservation reservation = MachineControllerBlockEntity.reserveNextPatternStart(
                List.of(unavailable, available), cursor, PATTERN_PORT, List.of(), List.of());

        assertThat(reservation.commit()).isTrue();
        assertThat(available.runtimeSnapshot().crafting().recipeId()).isEqualTo(recipe.id());
        assertThat(cursor).hasValue(0);
    }

    @Test
    void output_matching_selects_only_the_compatible_recipe() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        formForPattern(controller, true);
        MachineRecipe incompatible = recipe("reservation_output_incompatible", List.of(output(Items.GOLD_NUGGET, 1)));
        MachineRecipe compatible = recipe("reservation_output_compatible", List.of(output(Items.IRON_NUGGET, 1)));
        RecipeRegistry.registerStatic(incompatible);
        RecipeRegistry.registerStatic(compatible);

        PatternStartReservation reservation = controller.reservePatternStart(PATTERN_PORT,
                List.of(new MachineOutput.ItemOutput(stack(Items.IRON_NUGGET, 1), 1F)), List.of());

        assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
        assertThat(reservation.recipe()).isEqualTo(compatible);
        reservation.rollback();
    }

    @Test
    void output_matching_rejects_differences_in_amount_components_and_fluid() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        formForPattern(controller, true);
        ItemStack componentOutput = stack(Items.IRON_NUGGET, 1);
        componentOutput.set(DataComponents.CUSTOM_NAME, Component.literal("recipe"));
        MachineRecipe exact = recipeWithOutputs("reservation_output_strict", List.of(
                new MachineOutput.ItemOutput(componentOutput, 1F),
                new MachineOutput.FluidOutput(new FluidStack(Fluids.WATER, 1_000), 1F)));
        RecipeRegistry.registerStatic(exact);

        assertThat(controller.reservePatternStart(PATTERN_PORT,
                List.of(new MachineOutput.ItemOutput(stack(Items.IRON_NUGGET, 2), 1F),
                        new MachineOutput.FluidOutput(new FluidStack(Fluids.WATER, 1_000), 1F)), List.of()).status())
                .isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
        assertThat(controller.reservePatternStart(PATTERN_PORT,
                List.of(new MachineOutput.ItemOutput(stack(Items.IRON_NUGGET, 1), 1F),
                        new MachineOutput.FluidOutput(new FluidStack(Fluids.WATER, 1_000), 1F)), List.of()).status())
                .isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
        assertThat(controller.reservePatternStart(PATTERN_PORT,
                List.of(new MachineOutput.ItemOutput(componentOutput, 1F),
                        new MachineOutput.FluidOutput(new FluidStack(Fluids.WATER, 500), 1F)), List.of()).status())
                .isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
    }

    @Test
    void output_matching_accepts_a_probabilistic_recipe_output() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        formForPattern(controller, true);
        MachineRecipe recipe = recipeWithOutputs("reservation_output_probability", List.of(
                new MachineOutput.ItemOutput(stack(Items.GOLD_NUGGET, 1), 0.5F)));
        RecipeRegistry.registerStatic(recipe);

        PatternStartReservation reservation = controller.reservePatternStart(PATTERN_PORT,
                List.of(new MachineOutput.ItemOutput(stack(Items.GOLD_NUGGET, 1), 1F)), List.of());

        assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
        reservation.rollback();
    }

    @Test
    void output_matching_uses_the_runtime_modifier_context() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        formForPattern(controller, true);
        controller.componentRuntime().replaceModifiers(Map.of("runtime", List.of(
                MachineModifier.numeric("output", "output", 2D, "multiply", false))));
        MachineRecipe recipe = recipeWithOutputs("reservation_output_context",
                List.of(new MachineOutput.ItemOutput(stack(Items.IRON_NUGGET, 1), 1F)));
        RecipeRegistry.registerStatic(recipe);

        PatternStartReservation reservation = controller.reservePatternStart(PATTERN_PORT,
                List.of(new MachineOutput.ItemOutput(stack(Items.IRON_NUGGET, 2), 1F)), List.of());

        assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
        reservation.rollback();
    }

    @Test
    void redstone_paused_controller_rejects_admission() {
        MachineControllerBlockEntity controller = factoryController();
        controller.componentRuntime().replaceLinkedPortPositions(Set.of(PATTERN_PORT));
        RuntimeTestFixtures.republish(controller);
        RecipeRegistry.registerStatic(recipe("reservation_redstone_paused", List.of()));
        RuntimeTestFixtures.setDirectSignal(controller.getLevel(), controller.getBlockPos(), 15);
        controller.tickRuntimeWork((ServerLevel) controller.getLevel(), controller.getBlockPos());

        assertThat(controller.isRedstonePaused()).isTrue();
        assertThat(controller.reservePatternStart(PATTERN_PORT, List.of(), List.of()).status())
                .isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
    }

    @Test
    void ordinary_start_cannot_preempt_a_live_reservation() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("reservation_ordinary_preemption", List.of());
        CraftingRuntime.PreparedStart prepared = runtime.preparePatternStart(recipe, 1L, List.of());

        assertThat(prepared).isNotNull();
        assertThat(runtime.reservePatternStart()).isTrue();
        assertThat(runtime.start(recipe, 1).isCrafting()).isFalse();
        assertThat(runtime.commitPatternStart(prepared)).isTrue();
    }

    @Test
    void request_and_linked_input_ports_are_planned_and_committed_together() {
        ItemInputBusBlockEntity ordinaryInput = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), ordinaryInput);
        formForPattern(controller, true);
        setItem(ordinaryInput.itemStorage(), 0, stack(Items.IRON_INGOT, 1));
        LongResourceStorage<ItemResource> requestStorage = new LongResourceStorage<>(ItemResource.class, 1, 64,
                ItemResource::isEmpty, null);
        requestStorage.setContents(0, ItemResource.of(stack(Items.IRON_INGOT, 1)), 1);
        MachineCapability request = new ItemBusCapability(requestStorage, IOType.INPUT);
        MachineRecipe recipe = recipe("reservation_atomic_inputs", List.of(input(Items.IRON_INGOT, 2)));
        RecipeRegistry.registerStatic(recipe);

        PatternStartReservation reservation = controller.reservePatternStart(PATTERN_PORT, List.of(), List.of(request));

        assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
        assertThat(reservation.commit()).isTrue();
        assertThat(ordinaryInput.itemStorage().amount(0)).isZero();
        assertThat(requestStorage.amount(0)).isZero();
    }

    @Test
    void failed_request_plan_leaves_both_resources_and_the_normal_slot_available() {
        ItemInputBusBlockEntity ordinaryInput = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), ordinaryInput);
        formForPattern(controller, true);
        setItem(ordinaryInput.itemStorage(), 0, stack(Items.IRON_INGOT, 1));
        LongResourceStorage<ItemResource> requestStorage = new LongResourceStorage<>(ItemResource.class, 1, 64,
                ItemResource::isEmpty, null);
        requestStorage.setContents(0, ItemResource.of(stack(Items.IRON_INGOT, 1)), 1);
        MachineCapability request = new ItemBusCapability(requestStorage, IOType.INPUT);
        RecipeRegistry.registerStatic(recipe("reservation_insufficient_request", List.of(input(Items.IRON_INGOT, 3))));

        assertThat(controller.reservePatternStart(PATTERN_PORT, List.of(), List.of(request)).status())
                .isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
        assertThat(ordinaryInput.itemStorage().amount(0)).isEqualTo(1L);
        assertThat(requestStorage.amount(0)).isEqualTo(1L);

        MachineRecipe viable = recipe("reservation_request_retry", List.of(input(Items.IRON_INGOT, 2)));
        RecipeRegistry.registerStatic(viable);
        PatternStartReservation retried = controller.reservePatternStart(PATTERN_PORT, List.of(), List.of(request));
        assertThat(retried.recipe()).isEqualTo(viable);
        retried.rollback();
    }

    @Test
    void factory_reserves_a_concrete_idle_lane_until_commit_or_rollback() {
        MachineControllerBlockEntity controller = factoryController();
        formForPattern(controller, true);
        assertThat(controller.hasFactoryController()).isTrue();
        assertThat(controller.runtimeSnapshot().linkedPortPositions()).contains(PATTERN_PORT);
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("reservation_factory"), MMCR.id("reservation_factory"),
                20, List.of(), List.of());
        RecipeRegistry.registerStatic(recipe);

        PatternStartReservation first = controller.reservePatternStart(PATTERN_PORT, List.of(), List.of());
        assertThat(first.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
        assertThat(first.laneId()).isEqualTo("base");
        assertThat(controller.reservePatternStart(PATTERN_PORT, List.of(), List.of()).status())
                .isEqualTo(PatternStartReservation.Status.UNAVAILABLE);

        first.rollback();
        PatternStartReservation retried = controller.reservePatternStart(PATTERN_PORT, List.of(), List.of());
        assertThat(retried.commit()).isTrue();
        assertThat(retried.laneId()).isEqualTo("base");
    }

    @Test
    void factory_reserves_all_idle_lanes_for_one_pattern_batch() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime factory = new FactoryRuntime();
        factory.ensureBaseLane(controller);
        factory.setLaneLimit(2);
        MachineRecipe recipe = recipe("reservation_factory_batch", List.of());

        List<FactoryRuntime.PatternLane> lanes = factory.reservePatternStarts(recipe, 2L, List.of());

        assertThat(lanes).extracting(FactoryRuntime.PatternLane::laneId)
                .containsExactly("base", "factory-0");
    }

    @Test
    void batch_commit_starts_every_reserved_lane() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime factory = new FactoryRuntime();
        factory.ensureBaseLane(controller);
        factory.setLaneLimit(2);
        MachineRecipe recipe = recipe("reservation_factory_batch_commit", List.of());
        List<PatternStartReservation> reservations = factory.reservePatternStarts(recipe, 2L, List.of()).stream()
                .map(lane -> reservation(recipe, factory, lane)).toList();

        PatternStartBatchReservation batch = PatternStartBatchReservation.reserved(reservations);

        assertThat(batch.commit()).isTrue();
        assertThat(factory.activeRuntimes()).hasSize(2);
    }

    @Test
    void factory_scheduler_cannot_preempt_a_live_reservation() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime factory = new FactoryRuntime();
        factory.ensureBaseLane(controller);
        MachineRecipe recipe = recipe("reservation_factory_preemption", List.of());
        FactoryRuntime.PatternLane lane = factory.reservePatternStart(recipe, 1L, List.of());
        assertThat(lane).isNotNull();
        PatternStartReservation reservation = reservation(recipe, factory, lane);
        factory.tick(List.of(recipe), 1, 0L);

        assertThat(factory.activeRuntimes()).isEmpty();
        assertThat(reservation.commit()).isTrue();
    }

    @Test
    void cancelling_factory_async_state_releases_pattern_reservations_and_prevents_commit() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime factory = new FactoryRuntime();
        factory.ensureBaseLane(controller);
        MachineRecipe recipe = recipe("reservation_factory_lifecycle_cancel", List.of());
        FactoryRuntime.PatternLane lane = factory.reservePatternStart(recipe, 1L, List.of());
        assertThat(lane).isNotNull();
        PatternStartReservation reservation = reservation(recipe, factory, lane);

        factory.cancelAsyncState();

        assertThat(reservation.commit()).isFalse();
        assertThat(factory.reservePatternStart(recipe, 1L, List.of())).isNotNull();
    }

    @Test
    void lowering_factory_limit_keeps_existing_live_reservations() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        FactoryRuntime factory = new FactoryRuntime();
        factory.ensureBaseLane(controller);
        factory.setLaneLimit(2);
        MachineRecipe recipe = recipe("reservation_factory_removal", List.of());
        FactoryRuntime.PatternLane base = factory.reservePatternStart(recipe, 1L, List.of());
        FactoryRuntime.PatternLane removable = factory.reservePatternStart(recipe, 1L, List.of());
        assertThat(base).isNotNull();
        assertThat(removable).isNotNull();
        PatternStartReservation removedReservation = reservation(recipe, factory, removable);
        PatternStartReservation baseReservation = reservation(recipe, factory, base);
        factory.setLaneLimit(1);
        assertThat(removedReservation.commit()).isTrue();

        factory.clear();
        assertThat(baseReservation.commit()).isFalse();
    }

    @Test
    void caller_owned_notification_throw_reports_payment_and_keeps_the_real_allocation() throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            PaidPatternFixture fixture = new PaidPatternFixture("pattern_paid_throw");
            PatternStartReservation reservation = fixture.reserve();
            RecipeCommitLedger payment = fixture.runtime.ledger();
            fixture.notification.set(() -> { throw new IllegalStateException("root notification"); });

            assertThat(reservation.commit()).isTrue();
            assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.COMMITTED);
            assertThat(reservation.rolledBack()).isFalse();
            assertThat(fixture.afterCommit).hasValue(1);
            fixture.assertPausedPayment(payment);
            assertThat(reservation.commit()).isFalse();
            reservation.close();
            fixture.assertPausedPayment(payment);
            fixture.runtime.invalidate();
            assertThat(fixture.buffer.reserved()).isZero();
            assertThat(fixture.buffer.idleExcess()).isEqualTo(4L);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void caller_owned_cancelled_payment_is_terminal_without_activation_or_after_commit(boolean notifyThrow) throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            PaidPatternFixture fixture = new PaidPatternFixture("pattern_paid_cancel");
            PatternStartReservation reservation = fixture.reserve();
            RecipeCommitLedger payment = fixture.runtime.ledger();
            fixture.notification.set(() -> {
                fixture.runtime.invalidate();
                if (notifyThrow) throw new IllegalStateException("cancelled root notification");
            });

            assertThat(reservation.commit()).isTrue();
            assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.COMMITTED);
            assertThat(reservation.rolledBack()).isFalse();
            assertThat(payment.state().paidAttempts()).isEqualTo(1L);
            assertThat(fixture.runtime.active()).isFalse();
            assertThat(fixture.afterCommit).hasValue(0);
            reservation.close();
            assertThat(reservation.commit()).isFalse();
            fixture.assertCancelledPayment();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void old_paid_continuation_does_not_release_a_reentrant_new_pending_pattern(boolean notifyThrow) throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            PaidPatternFixture fixture = new PaidPatternFixture("pattern_paid_reentrant");
            PatternStartReservation old = fixture.reserve();
            AtomicReference<PatternStartReservation> replacement = new AtomicReference<>();
            AtomicReference<RecipeCommitLedger> replacementPayment = new AtomicReference<>();
            fixture.notification.set(() -> {
                fixture.runtime.invalidate();
                replacement.set(fixture.reserve());
                replacementPayment.set(fixture.runtime.ledger());
                if (notifyThrow) throw new IllegalStateException("old root notification");
            });

            assertThat(old.commit()).isTrue();
            old.close();
            fixture.assertPendingReplacement(replacement.get(), replacementPayment.get());
            assertThat(fixture.afterCommit).hasValue(0);
            assertThat(replacement.get().commit()).isTrue();
            fixture.assertReplacementPaid();
        }
    }

    @Test
    void unpaid_caller_write_failure_rolls_back_the_real_input_and_flux_journals() throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            PaidPatternFixture fixture = new PaidPatternFixture("pattern_unpaid_failure");
            PatternStartReservation reservation = fixture.reserve();
            RecipeCommitLedger payment = fixture.runtime.ledger();

            assertThat(reservation.commit(transaction -> { throw new IllegalStateException("before root commit"); }))
                    .isFalse();
            assertThat(reservation.rolledBack()).isTrue();
            assertThat(payment.state().paidAttempts()).isZero();
            assertThat(fixture.inputs.amount(0)).isEqualTo(2L);
            assertThat(fixture.network.amount()).isEqualTo(100L);
            assertThat(fixture.buffer.amount()).isZero();
            assertThat(fixture.buffer.reserved()).isZero();
            assertThat(fixture.afterCommit).hasValue(0);
            assertThat(fixture.reserve().commit()).isTrue();
        }
    }

    static Stream<Arguments> finalPatternWriteChanges() {
        return Stream.of(false, true).flatMap(direct -> Stream.of("full", "local", "lifecycle", "ledger")
                .map(change -> Arguments.of(direct, change)));
    }

    @ParameterizedTest
    @MethodSource("finalPatternWriteChanges")
    void final_caller_write_invalidates_unpaid_joint_inputs_and_prefetches(boolean direct, String change) throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            PaidPatternFixture fixture = new PaidPatternFixture("pattern_final_write", true);
            RecipeCommitLedger payment;
            if (direct) {
                var prepared = fixture.runtime.preparePatternStart(fixture.recipe, 1L,
                        List.of(new ItemBusCapability(fixture.inputs, IOType.INPUT)), PATTERN_PORT);
                assertThat(prepared).isNotNull();
                assertThat(fixture.runtime.reservePatternStart()).isTrue();
                payment = fixture.runtime.ledger();
                assertThat(fixture.runtime.commitPatternStart(prepared, transaction -> {
                    fixture.assertProvisionalJointPayment(payment);
                    fixture.changeUnpaidFence(change);
                })).isFalse();
            } else {
                PatternStartReservation reservation = fixture.reserve();
                payment = fixture.runtime.ledger();
                assertThat(reservation.commit(transaction -> {
                    fixture.assertProvisionalJointPayment(payment);
                    fixture.changeUnpaidFence(change);
                })).isFalse();
                assertThat(reservation.rolledBack()).isTrue();
                assertThat(reservation.commit()).isFalse();
            }
            fixture.assertUnpaidJointRollback(payment);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void root_notification_isolation_change_keeps_paid_joint_scope(boolean direct) throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            PaidPatternFixture fixture = new PaidPatternFixture("pattern_final_paid_scope", true);
            fixture.notification.set(() -> fixture.controller.setFullyInputIsolated(true));
            if (direct) {
                var prepared = fixture.runtime.preparePatternStart(fixture.recipe, 1L,
                        List.of(new ItemBusCapability(fixture.inputs, IOType.INPUT)), PATTERN_PORT);
                assertThat(prepared).isNotNull();
                assertThat(fixture.runtime.reservePatternStart()).isTrue();
                assertThat(fixture.runtime.commitPatternStart(prepared)).isTrue();
            } else {
                assertThat(fixture.reserve().commit()).isTrue();
                assertThat(fixture.afterCommit).hasValue(1);
            }
            assertThat(fixture.controller.isFullyInputIsolated()).isTrue();
            assertThat(fixture.runtime.active()).isTrue();
            assertThat(fixture.runtime.activeInputScope()).isEqualTo(new InputScope(InputScope.Kind.ORDINARY,
                    List.of(fixture.physicalInput.getBlockPos(), PATTERN_PORT)));
            assertThat(fixture.runtime.ledger().state().paidAttempts()).isEqualTo(1L);
            assertThat(fixture.inputs.amount(0)).isZero();
            assertThat(fixture.physicalInput.itemStorage().amount(0)).isZero();
            assertThat(fixture.network.amount()).isEqualTo(96L);
            assertThat(fixture.buffer.amount()).isEqualTo(4L);
            assertThat(fixture.buffer.reserved()).isEqualTo(4L);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void factory_old_paid_release_keeps_reentrant_lane_reservation_accounting(boolean notifyThrow) throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            PaidPatternFixture fixture = new PaidPatternFixture("factory_pattern_reentrant");
            FactoryRuntime factory = new FactoryRuntime();
            factory.ensureBaseLane(fixture.controller);
            List<MachineCapability> capabilities = List.of(new ItemBusCapability(fixture.inputs, IOType.INPUT));
            FactoryRuntime.PatternLane oldLane = factory.reservePatternStart(fixture.recipe, 1L, capabilities, PATTERN_PORT);
            assertThat(oldLane).isNotNull();
            PatternStartReservation old = reservation(fixture.recipe, factory, oldLane)
                    .afterCommit(fixture.afterCommit::incrementAndGet);
            AtomicReference<FactoryRuntime.PatternLane> newLane = new AtomicReference<>();
            AtomicReference<PatternStartReservation> replacement = new AtomicReference<>();
            AtomicReference<RecipeCommitLedger> payment = new AtomicReference<>();
            fixture.notification.set(() -> {
                factory.cancelAsyncState();
                newLane.set(factory.reservePatternStart(fixture.recipe, 1L, capabilities, PATTERN_PORT));
                assertThat(newLane.get()).isNotNull();
                replacement.set(reservation(fixture.recipe, factory, newLane.get())
                        .afterCommit(fixture.afterCommit::incrementAndGet));
                payment.set(newLane.get().runtime().ledger());
                if (notifyThrow) throw new IllegalStateException("factory old root notification");
            });

            assertThat(old.commit()).isTrue();
            old.close();
            assertThat(oldLane.runtime()).isSameAs(newLane.get().runtime());
            assertThat(newLane.get().runtime().pendingPatternRecipeId()).isEqualTo(fixture.recipe.id());
            assertThat(newLane.get().runtime().ledger()).isSameAs(payment.get());
            assertThat(payment.get().state().paidAttempts()).isZero();
            assertThat(payment.get().state().phase()).isEqualTo(RecipeCommitLedger.Phase.READY);
            assertThat(factory.reservePatternStart(fixture.recipe, 1L, capabilities, PATTERN_PORT)).isNull();
            assertThat(fixture.afterCommit).hasValue(0);
            fixture.assertCancelledPayment();
            assertThat(replacement.get().commit()).isTrue();
            assertThat(newLane.get().runtime().active()).isTrue();
            assertThat(fixture.inputs.amount(0)).isZero();
            assertThat(fixture.network.amount()).isEqualTo(92L);
            assertThat(fixture.buffer.reserved()).isEqualTo(4L);
            assertThat(fixture.afterCommit).hasValue(1);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void same_reservation_nested_commit_is_rejected_through_writes_and_root_notification(boolean notifyThrow) throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            PaidPatternFixture fixture = new PaidPatternFixture("pattern_attempt_guard");
            PatternStartReservation reservation = fixture.reserve();
            AtomicInteger nestedWrites = new AtomicInteger();
            AtomicInteger notifications = new AtomicInteger();
            Runnable nestedCommit = () -> assertThat(reservation.commit(transaction -> nestedWrites.incrementAndGet()))
                    .isFalse();
            fixture.notification.set(() -> {
                notifications.incrementAndGet();
                nestedCommit.run();
                if (notifyThrow) throw new IllegalStateException("guarded root notification");
            });

            assertThat(reservation.commit(transaction -> nestedCommit.run())).isTrue();
            assertThat(notifications).hasValue(1);
            assertThat(nestedWrites).hasValue(0);
            assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.COMMITTED);
            assertThat(reservation.rolledBack()).isFalse();
            if (notifyThrow) fixture.assertPausedPayment(fixture.runtime.ledger());
            else fixture.assertSinglePayment();
            reservation.close();
            assertThat(reservation.commit()).isFalse();
            assertThat(fixture.networkCommits).hasValue(1);
            assertThat(fixture.afterCommit).hasValue(1);

            fixture.runtime.invalidate();
            assertThat(fixture.reserve().commit()).isTrue();
            assertThat(fixture.runtime.ledger().state().paidAttempts()).isEqualTo(1L);
            assertThat(fixture.inputs.amount(0)).isZero();
            assertThat(fixture.network.amount()).isEqualTo(92L);
            assertThat(fixture.networkCommits).hasValue(2);
            assertThat(fixture.buffer.reserved()).isEqualTo(4L);
            assertThat(fixture.afterCommit).hasValue(2);
        }
    }

    @Test
    void exactBatchCannotStartAPrefixOrSeveralLanes() throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            var fixture = new PendingStartFixture("exact_prefix", RecipeBehavior.defaults(),
                    List.of(input(Items.IRON_INGOT, 1), new EnergyRequirement(1L)), 256L);
            var materials = new LongResourceStorage<>(ItemResource.class, 1, 1024L, ItemResource::isEmpty, null);
            materials.setContents(0, ItemResource.of(Items.IRON_INGOT), 256L);
            List<MachineCapability> capabilities = List.of(new ItemBusCapability(materials, IOType.INPUT));
            fixture.energy.energyStorage().setAmount(255L);
            assertThat(fixture.runtime.prepareExactPatternStart(fixture.recipe, 256L, capabilities, PATTERN_PORT)).isNull();
            assertThat(fixture.runtime.pendingPatternRecipeId()).isNull();
            assertThat(materials.amount(0)).isEqualTo(256L);
            assertThat(fixture.energy.energyStorage().getAmountAsLong()).isEqualTo(255L);
            fixture.energy.energyStorage().setAmount(256L);
            var factory = new FactoryRuntime();
            factory.ensureBaseLane(fixture.controller);
            factory.setLaneLimit(4);
            var recipe = MachineRecipe.fromCanonical(MMCR.id("exact_one_recipe_thread"), MMCR.id("test_cube"), 20,
                    List.of(input(Items.IRON_INGOT, 1), new EnergyRequirement(1L)), List.of(), List.of(),
                    0, 1, false, true, false, Set.of());
            RecipeRegistry.registerStatic(recipe);
            var lane = factory.reserveExactPatternStart(recipe, 256L, capabilities, PATTERN_PORT);
            assertThat(lane).isNotNull();
            assertThat(factory.reserveExactPatternStart(recipe, 256L, capabilities, PATTERN_PORT)).isNull();
            var reservation = reservation(recipe, factory, lane);
            assertThat(reservation.parallelism()).isEqualTo(256L);
            assertThat(reservation.commit()).isTrue();
            assertThat(factory.activeRuntimes()).containsExactly(lane.runtime());
            assertThat(materials.amount(0)).isZero();
            assertThat(fixture.energy.energyStorage().getAmountAsLong()).isEqualTo(256L);
            assertThat(lane.runtime().requiresPatternRequest()).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(floats = {0F, 0.5F, 1F})
    void probabilisticOutputPromisesAcceptUnchangedProbabilitiesButRejectCallbackChanges(float chance) {
        List<MachineOutput> expected = List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT, 3), chance),
                new MachineOutput.FluidOutput(new FluidStack(Fluids.LAVA, 100), chance));
        List<MachineOutput> same = List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT, 3), chance),
                new MachineOutput.FluidOutput(new FluidStack(Fluids.LAVA, 100), chance));
        assertThat(CraftingRuntime.samePromisedOutputs(expected, same, 4L)).isTrue();
        for (int index = 0; index < expected.size(); index++) {
            var changed = new ArrayList<>(same);
            changed.set(index, same.get(index).withChance(chance == 1F ? 0.5F : 1F));
            assertThat(CraftingRuntime.samePromisedOutputs(expected, changed, 4L)).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"key", "amount", "components", "chance"})
    void beforeStartChangesPromisedOutputsRejectsWithoutPayment(String change) throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            AtomicInteger hooks = new AtomicInteger();
            var behavior = RecipeBehavior.builder().beforeStart(context -> {
                hooks.incrementAndGet();
                ItemStack output = new ItemStack(change.equals("key") ? Items.DIAMOND : Items.GOLD_INGOT,
                        change.equals("amount") ? 2 : 1);
                if (change.equals("components")) output.set(DataComponents.CUSTOM_NAME, Component.literal("changed"));
                context.setOutputs(List.of(new MachineOutput.ItemOutput(output, change.equals("chance") ? 0.5F : 1F)));
            }).build();
            var fixture = new PendingStartFixture("exact_changed_promise_" + change, behavior,
                    List.of(input(Items.IRON_INGOT, 1)), 256L);
            var materials = new LongResourceStorage<>(ItemResource.class, 1, 1024L, ItemResource::isEmpty, null);
            materials.setContents(0, ItemResource.of(Items.IRON_INGOT), 256L);
            var recipe = MachineRecipe.fromCanonical(MMCR.id("exact_promise_" + change), MMCR.id("test_cube"), 20,
                    List.of(input(Items.IRON_INGOT, 1),
                            new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new ItemStack(Items.GOLD_INGOT))),
                    List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT), 1F)),
                    List.of(), 0, 1, false, true, false, Set.of());
            RecipeRegistry.registerStatic(recipe);
            assertThat(fixture.controller.reserveExactPatternStart(PATTERN_PORT, recipe.id(), recipe.runtimeMachineOutputs(),
                    256L, List.of(new ItemBusCapability(materials, IOType.INPUT))).status())
                    .isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
            assertThat(hooks).hasValue(1);
            assertThat(materials.amount(0)).isEqualTo(256L);
            assertThat(fixture.runtime.pendingPatternRecipeId()).isNull();
            assertThat(fixture.runtime.ledger().state().paidAttempts()).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"level", "stage", "callback_level", "callback_stage", "cancel"})
    void batchDeliveryCannotBypassStaticConditionsOrCallbackCancellation(String condition) throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            MachineRequirement guard = condition.endsWith("level")
                    ? new LevelRequirement(MMCR.id("missing_coil"), MMCR.id("missing_level"))
                    : new StageRequirement(2);
            var requirements = new ArrayList<MachineRequirement>(List.of(input(Items.IRON_INGOT, 1)));
            if (!condition.startsWith("callback_") && !condition.equals("cancel")) requirements.add(guard);
            var behavior = RecipeBehavior.builder().beforeStart(context -> {
                if (condition.startsWith("callback_")) {
                    var modified = new ArrayList<>(context.requirements());
                    modified.add(guard);
                    context.setRequirements(modified);
                }
                if (condition.equals("cancel")) context.cancel();
            }).build();
            var fixture = new PendingStartFixture("guarded_batch_" + condition, behavior, requirements, 4L);
            var materials = new LongResourceStorage<>(ItemResource.class, 1, 64L, ItemResource::isEmpty, null);
            materials.setContents(0, ItemResource.of(Items.IRON_INGOT), 4L);
            var reservation = fixture.controller.reserveExactPatternStart(PATTERN_PORT, fixture.recipe.id(), List.of(),
                    4L, List.of(new ItemBusCapability(materials, IOType.INPUT)));
            assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
            assertThat(materials.amount(0)).isEqualTo(4L);
            assertThat(fixture.runtime.pendingPatternRecipeId()).isNull();
            assertThat(fixture.runtime.active()).isFalse();
        }
    }

    @Test
    void batchUsesActualBeforeStartInputDemandWithoutAcceptingAPrefix() throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            var fixture = new PendingStartFixture("batch_changed_input", RecipeBehavior.builder().beforeStart(context ->
                    context.replaceExactItemInputCount(Items.IRON_INGOT, 1, 2)).build(),
                    List.of(input(Items.IRON_INGOT, 1), new StageRequirement(1)), 4L);
            var materials = new LongResourceStorage<>(ItemResource.class, 1, 64L, ItemResource::isEmpty, null);
            materials.setContents(0, ItemResource.of(Items.IRON_INGOT), 4L);
            List<MachineCapability> capabilities = List.of(new ItemBusCapability(materials, IOType.INPUT));
            assertThat(fixture.controller.reserveExactPatternStart(PATTERN_PORT, fixture.recipe.id(), List.of(),
                    4L, capabilities).status()).isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
            assertThat(materials.amount(0)).isEqualTo(4L);
            materials.setContents(0, ItemResource.of(Items.IRON_INGOT), 8L);
            try (var reservation = fixture.controller.reserveExactPatternStart(PATTERN_PORT, fixture.recipe.id(), List.of(),
                    4L, capabilities)) {
                assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
                assertThat(reservation.parallelism()).isEqualTo(4L);
                assertThat(reservation.commit()).isTrue();
            }
            assertThat(materials.amount(0)).isZero();
            assertThat(fixture.runtime.parallelism()).isEqualTo(4L);
        }
    }

    @Test
    void smartConditionRejectsBulkDeliveryUntilItsActualValueMatches() throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            var fixture = new PendingStartFixture("batch_smart_condition", RecipeBehavior.defaults(), List.of(
                    input(Items.IRON_INGOT, 1), SmartInterfaceRequirement.input("temperature", 10F, 20F)), 4L);
            var values = new FloatValueStorage();
            values.set("temperature", 5F);
            var smart = new PatternFluxHost(RuntimeTestFixtures.itemInput(new BlockPos(5, 0, 0)),
                    new SmartInterfaceCapability(values, IOType.INPUT));
            smart.setLevel(fixture.controller.getLevel());
            var components = new ArrayList<>(fixture.controller.componentRuntime().components());
            components.add(new ProcessingComponent(null, smart, smart.getBlockPos(), smart.getBlockPos(), List.of()));
            fixture.controller.componentRuntime().replaceComponents(components);
            RuntimeTestFixtures.republish(fixture.controller);
            var materials = new LongResourceStorage<>(ItemResource.class, 1, 64L, ItemResource::isEmpty, null);
            materials.setContents(0, ItemResource.of(Items.IRON_INGOT), 4L);
            List<MachineCapability> capabilities = List.of(new ItemBusCapability(materials, IOType.INPUT));
            assertThat(fixture.controller.reserveExactPatternStart(PATTERN_PORT, fixture.recipe.id(), List.of(),
                    4L, capabilities).status()).isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
            assertThat(materials.amount(0)).isEqualTo(4L);
            values.set("temperature", 15F);
            try (var reservation = fixture.controller.reserveExactPatternStart(PATTERN_PORT, fixture.recipe.id(), List.of(),
                    4L, capabilities)) {
                assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
                assertThat(reservation.parallelism()).isEqualTo(4L);
                assertThat(reservation.commit()).isTrue();
            }
            assertThat(materials.amount(0)).isZero();
            assertThat(values.value("temperature")).contains(15F);
        }
    }

    @ParameterizedTest
    @CsvSource({"0,false,false", "1,false,false", "1,true,false", "2,false,false", "4,false,false", "4,true,false", "4,false,true", "4,true,true"})
    void requestSuppliedRetainedRecipeQuantitySkipsContinuousChecksAfterReload(int requestCatalysts,
                                                                              boolean saveDuringCommit, boolean overlaps) throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            var catalystItem = overlaps ? Items.IRON_INGOT : Items.DIAMOND;
            var catalyst = new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(catalystItem), 1,
                    ItemStack.EMPTY, 1F, List.of(), DataComponentPredicateSet.EMPTY, 0F);
            var fixture = new PendingStartFixture("exact_retained_request", RecipeBehavior.defaults(),
                    List.of(input(Items.IRON_INGOT, overlaps ? 2 : 1), catalyst), 4L);
            var physical = RuntimeTestFixtures.itemInput(new BlockPos(5, 0, 0));
            physical.setLevel(fixture.controller.getLevel());
            setItem(physical.itemStorage(), 0, stack(catalystItem, 4));
            var components = new ArrayList<>(fixture.controller.componentRuntime().components());
            components.add(new ProcessingComponent(new MachineComponent(physical.kind(), physical.ioType()), physical,
                    physical.getBlockPos(), physical.getBlockPos(), (String) null));
            fixture.controller.componentRuntime().replaceComponents(components);
            formForPattern(fixture.controller, true);
            var request = new LongResourceStorage<>(ItemResource.class, 2, 64L, ItemResource::isEmpty, null);
            request.setContents(0, ItemResource.of(Items.IRON_INGOT), 4L);
            request.setContents(1, ItemResource.of(Items.DIAMOND), overlaps ? 0 : requestCatalysts);
            var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, fixture.controller.getLevel().registryAccess());
            try (var reservation = fixture.controller.reserveExactPatternStart(PATTERN_PORT, fixture.recipe.id(), List.of(),
                    4L, List.of(new ItemBusCapability(request, IOType.INPUT)))) {
                assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
                assertThat(reservation.commit(transaction -> {
                    if (saveDuringCommit) fixture.runtime.save(saved);
                })).isTrue();
            }
            assertThat(request.amount(0)).isZero();
            assertThat(request.amount(1)).isEqualTo(overlaps ? 0 : requestCatalysts);
            assertThat(physical.itemStorage().amount(0)).isEqualTo(overlaps ? 0L : 4L);
            if (!saveDuringCommit) fixture.runtime.save(saved);
            // AE owns the unconsumed request again; it is not a persistent physical input source.
            request.setContents(1, ItemResource.of(Items.DIAMOND), 0L);
            try (var transaction = Transaction.openRoot()) {
                if (!overlaps) assertThat(physical.itemStorage().extract(0, physical.itemStorage().resource(0), 4L, transaction)).isEqualTo(4L);
                transaction.commit();
            }
            var restored = new CraftingRuntime(fixture.controller, fixture.controller.componentRuntime());
            restored.load(TagValueInput.create(ProblemReporter.DISCARDING, fixture.controller.getLevel().registryAccess(), saved.buildResult()),
                    fixture.controller.resourceDomain());
            assertThat(restored.active()).isTrue();
            restored.rebindCurrentVersions();
            boolean validatedRequest = requestCatalysts >= 1 && !overlaps;
            if (validatedRequest) assertThat(restored.installedTickRequirements()).isEmpty();
            else assertThat(restored.installedTickRequirements()).isNotEmpty();
            LevelStub.setGameTime(fixture.controller.getLevel(), 1L);
            restored.tick();
            assertThat(restored.tickCount()).isEqualTo(validatedRequest && !saveDuringCommit ? 1 : 0);
            if (saveDuringCommit) assertThat(restored.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.ERROR_PAUSED);
            if (validatedRequest && !saveDuringCommit) assertThat(restored.failure()).isNull();
            else assertThat(restored.failure()).isNotNull();
        }
    }

    @Test
    void exactPaidReloadRetainsLongBatchAndSourceConstraint() throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            var fixture = new PendingStartFixture("exact_paid_reload", RecipeBehavior.defaults(),
                    List.of(input(Items.IRON_INGOT, 2)), Integer.MAX_VALUE);
            long total = Math.multiplyExact(2L, Integer.MAX_VALUE);
            var materials = new LongResourceStorage<>(ItemResource.class, 1, Long.MAX_VALUE, ItemResource::isEmpty, null);
            materials.setContents(0, ItemResource.of(Items.IRON_INGOT), total);
            var reservation = fixture.controller.reserveExactPatternStart(PATTERN_PORT, fixture.recipe.id(), List.of(),
                    Integer.MAX_VALUE, List.of(new ItemBusCapability(materials, IOType.INPUT)));
            assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
            assertThat(reservation.commit()).isTrue();
            assertThat(materials.amount(0)).isZero();
            var lookup = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, lookup);
            fixture.runtime.save(output);
            fixture.controller.componentRuntime().replaceModifiers(Map.of());
            var restored = new CraftingRuntime(fixture.controller, fixture.controller.componentRuntime());
            restored.load(TagValueInput.create(ProblemReporter.DISCARDING, lookup, output.buildResult()),
                    fixture.controller.resourceDomain());
            assertThat(restored.active()).isTrue();
            assertThat(restored.parallelism()).isEqualTo((long) Integer.MAX_VALUE);
            assertThat(restored.requiresPatternRequest()).isTrue();
            assertThat(restored.activeRecipe().inputConsumptionPlan().isValidFor(restored.activeRecipe().effectiveRequirements())).isTrue();
            assertThat(materials.amount(0)).isZero();
        }
    }

    @Test
    void secondIdenticalBatchUsesTheSelectedLanesLastRecipe() throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            AtomicInteger hooks = new AtomicInteger();
            var fixture = new PendingStartFixture("exact_last_recipe", RecipeBehavior.builder()
                    .beforeStart(context -> hooks.incrementAndGet()).build(), List.of(input(Items.IRON_INGOT, 1)), 256L);
            var materials = new LongResourceStorage<>(ItemResource.class, 1, 1024L, ItemResource::isEmpty, null);
            materials.setContents(0, ItemResource.of(Items.IRON_INGOT), 512L);
            List<MachineCapability> capabilities = List.of(new ItemBusCapability(materials, IOType.INPUT));
            var first = fixture.controller.reserveExactPatternStart(PATTERN_PORT, fixture.recipe.id(), List.of(), 256L, capabilities);
            assertThat(first.recipeSelection()).isEqualTo(PatternStartReservation.RecipeSelection.CATALOG);
            assertThat(first.commit()).isTrue();
            assertThat(materials.amount(0)).isEqualTo(256L);
            for (int tick = 0; tick < fixture.recipe.tickTime(); tick++) {
                LevelStub.setGameTime(fixture.controller.getLevel(), tick + 1L);
                fixture.runtime.tick();
            }
            fixture.runtime.finish();
            assertThat(fixture.runtime.active()).isFalse();
            var second = fixture.controller.reserveExactPatternStart(PATTERN_PORT, fixture.recipe.id(), List.of(), 256L, capabilities);
            assertThat(second.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
            assertThat(second.recipeSelection()).isEqualTo(PatternStartReservation.RecipeSelection.LAST_RECIPE);
            assertThat(second.commit()).isTrue();
            assertThat(hooks).hasValue(2);
            assertThat(materials.amount(0)).isZero();
            assertThat(fixture.runtime.ledger().state().paidAttempts()).isEqualTo(1L);
        }
    }

    @Test
    void callbackRemovalAndPrefetchFinishFailureCannotLeakOwnership() throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            AtomicReference<MachineControllerBlockEntity> owner = new AtomicReference<>();
            AtomicInteger hooks = new AtomicInteger();
            var removed = new PaidPatternFixture("exact_callback_removal", RecipeBehavior.builder().beforeStart(context -> {
                hooks.incrementAndGet();
                owner.get().setRemoved();
            }).build());
            owner.set(removed.controller);
            assertThat(removed.controller.reserveExactPatternStart(PATTERN_PORT, removed.recipe.id(), List.of(), 1L,
                    List.of(new ItemBusCapability(removed.inputs, IOType.INPUT))).status())
                    .isEqualTo(PatternStartReservation.Status.UNAVAILABLE);
            assertThat(hooks).hasValue(1);
            assertThat(removed.inputs.amount(0)).isEqualTo(2L);
            assertThat(removed.network.amount()).isEqualTo(100L);
            assertThat(removed.buffer.reserved()).isZero();
            assertThat(removed.runtime.pendingPatternRecipeId()).isNull();

            var paid = new PaidPatternFixture("exact_prefetch_notification");
            paid.inputs.setContents(0, ItemResource.of(Items.IRON_INGOT), 1L);
            paid.notification.set(() -> { throw new IllegalStateException("root Flux notification"); });
            var reservation = paid.controller.reserveExactPatternStart(PATTERN_PORT, paid.recipe.id(), List.of(), 1L,
                    List.of(new ItemBusCapability(paid.inputs, IOType.INPUT)));
            assertThat(reservation.commit()).isTrue();
            assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.COMMITTED);
            assertThat(paid.inputs.amount(0)).isZero();
            assertThat(paid.runtime.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.ERROR_PAUSED);
            assertThat(paid.runtime.requiresPatternRequest()).isTrue();
            reservation.close();
            paid.runtime.invalidate();
            assertThat(paid.buffer.reserved()).isZero();
            paid.inputs.setContents(0, ItemResource.of(Items.IRON_INGOT), 1L);
            var retry = paid.controller.reserveExactPatternStart(PATTERN_PORT, paid.recipe.id(), List.of(), 1L,
                    List.of(new ItemBusCapability(paid.inputs, IOType.INPUT)));
            assertThat(retry.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
            retry.close();
        }
    }

    /** Pending views use real input routing and a physical energy limit, never a mocked plan.
     * @author howxu <dev@howxu.cn>
     */
    private static final class PendingStartFixture {
        final EnergyInputHatchBlockEntity energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        final MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), energy);
        final CraftingRuntime runtime;
        final MachineRecipe recipe;
        final List<Long> plannedCopies = new ArrayList<>();
        final List<LongResourceStorage<ItemResource>> views = new ArrayList<>();

        PendingStartFixture(String name, RecipeBehavior behavior, List<MachineRequirement> requirements) {
            this(name, behavior, requirements, 10L);
        }

        PendingStartFixture(String name, RecipeBehavior behavior, List<MachineRequirement> requirements, long maxParallelism) {
            Machine original = controller.currentStructureSnapshot().configuredMachine();
            controller.setMachine(new Machine() {
                @Override public Identifier registryName() { return original.registryName(); }
                @Override public BlockArray pattern() { return original.pattern(); }
                @Override public MachineControllerSpec controller() { return original.controller(); }
                @Override public RecipeBehavior behavior() { return behavior; }
                @Override public boolean parallelizable() { return true; }
                @Override public long maxParallelism() { return maxParallelism; }
            });
            controller.componentRuntime().replaceModifiers(Map.of("parallelism", List.of(
                    MachineModifier.numeric("parallelism", "machine", maxParallelism - 1D, "add", false))));
            energy.energyStorage().setAmount(3L);
            formForPattern(controller, true);
            runtime = RuntimeTestFixtures.craftingRuntime(controller);
            recipe = MachineRecipe.fromCanonical(MMCR.id(name), MMCR.id("test_cube"), 20, requirements, List.of(),
                    List.of(), 0, 2, false, true, false, Set.of());
            RecipeRegistry.registerStatic(recipe);
        }

        List<MachineCapability> view(long copies, ItemStack... inputs) {
            plannedCopies.add(copies);
            var storage = new LongResourceStorage<>(ItemResource.class, Math.max(1, inputs.length), 64L,
                    ItemResource::isEmpty, null);
            for (int slot = 0; slot < inputs.length; slot++) {
                ItemStack stack = inputs[slot];
                if (!stack.isEmpty()) storage.setContents(slot, ItemResource.of(stack), stack.getCount());
            }
            views.add(storage);
            return List.of(new ItemBusCapability(storage, IOType.INPUT));
        }
    }

    /** Shared real caller-owned payment boundary for single and multi-reservation tests.
     * @author howxu <dev@howxu.cn>
     */
    static final class PaidPatternFixture {
        final AtomicReference<Runnable> notification = new AtomicReference<>();
        final AtomicReference<Runnable> prefetchWrite = new AtomicReference<>();
        final AtomicInteger afterCommit = new AtomicInteger();
        final AtomicInteger networkCommits = new AtomicInteger();
        final FluxEnergyBuffer buffer = new FluxEnergyBuffer(() -> {
            Runnable action = notification.getAndSet(null);
            if (action != null) action.run();
        });
        final LongValueStorage network = new LongValueStorage(100L, 100L, null);
        final LongResourceStorage<ItemResource> inputs = new LongResourceStorage<>(ItemResource.class, 1, 64,
                ItemResource::isEmpty, null);
        final MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        final ItemInputBusBlockEntity physicalInput = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        final CraftingRuntime runtime;
        final MachineRecipe recipe;

        PaidPatternFixture(String name) {
            this(name, null);
        }

        PaidPatternFixture(String name, RecipeBehavior behavior) {
            this(name, behavior, false);
        }

        PaidPatternFixture(String name, boolean jointInputs) {
            this(name, null, jointInputs);
        }

        private PaidPatternFixture(String name, RecipeBehavior behavior, boolean jointInputs) {
            network.setAmount(100L);
            int requestAmount = jointInputs ? 1 : 2;
            inputs.setContents(0, ItemResource.of(stack(Items.IRON_INGOT, requestAmount)), requestAmount);
            var flux = new FluxEnergyInputCapability(buffer, "pattern", amount -> Optional.of(transaction -> {
                networkCommits.incrementAndGet();
                assertThat(network.extract(amount, transaction)).isEqualTo(amount);
                Runnable action = prefetchWrite.getAndSet(null);
                if (action != null) action.run();
                return CapabilityResult.successful();
            }));
            if (behavior != null) {
                Machine original = controller.currentStructureSnapshot().configuredMachine();
                controller.setMachine(new Machine() {
                    @Override public Identifier registryName() { return original.registryName(); }
                    @Override public BlockArray pattern() { return original.pattern(); }
                    @Override public MachineControllerSpec controller() { return original.controller(); }
                    @Override public RecipeBehavior behavior() { return behavior; }
                });
            }
            var components = new ArrayList<>(controller.componentRuntime().components());
            if (jointInputs) {
                physicalInput.setLevel(controller.getLevel());
                setItem(physicalInput.itemStorage(), 0, stack(Items.IRON_INGOT, 1));
                components.add(new ProcessingComponent(new MachineComponent(physicalInput.kind(), physicalInput.ioType()),
                        physicalInput, physicalInput.getBlockPos(), physicalInput.getBlockPos(), (String) null));
            }
            components.add(new ProcessingComponent(null, new PatternFluxHost(flux), BlockPos.ZERO, BlockPos.ZERO,
                    (String) null));
            controller.componentRuntime().replaceComponents(components);
            formForPattern(controller, true);
            runtime = RuntimeTestFixtures.craftingRuntime(controller);
            recipe = MachineRecipe.fromCanonical(MMCR.id(name), MMCR.id("test_cube"), 2,
                    List.of(input(Items.IRON_INGOT, jointInputs ? 2 : 1), new EnergyRequirement(2L)), List.of(),
                    List.of(), 0, 1, false, false, false, Set.of());
            RecipeRegistry.registerStatic(recipe);
        }

        PatternStartReservation reserve() {
            PatternStartReservation reservation = controller.reservePatternStart(PATTERN_PORT, List.of(),
                    List.of(new ItemBusCapability(inputs, IOType.INPUT)));
            assertThat(reservation.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
            return reservation.afterCommit(afterCommit::incrementAndGet);
        }

        void changeUnpaidFence(String change) {
            switch (change) {
                case "full" -> controller.setFullyInputIsolated(true);
                case "local" -> {
                    physicalInput.setInputIsolated(true);
                    controller.onInputIsolationChanged();
                }
                case "lifecycle" -> controller.setMachine(null);
                case "ledger" -> runtime.invalidate();
                default -> throw new IllegalArgumentException(change);
            }
        }

        void assertProvisionalJointPayment(RecipeCommitLedger payment) {
            assertThat(payment.state().paidAttempts()).isEqualTo(1L);
            assertThat(inputs.amount(0)).isZero();
            assertThat(physicalInput.itemStorage().amount(0)).isZero();
            assertThat(network.amount()).isEqualTo(96L);
            assertThat(buffer.amount()).isEqualTo(4L);
            assertThat(buffer.reserved()).isEqualTo(4L);
        }

        void assertUnpaidJointRollback(RecipeCommitLedger payment) {
            assertThat(runtime.active()).isFalse();
            assertThat(runtime.pendingPatternRecipeId()).isNull();
            assertThat(payment.state().phase()).isEqualTo(RecipeCommitLedger.Phase.READY);
            assertThat(payment.state().paidAttempts()).isZero();
            assertThat(payment.state().sequence()).isZero();
            assertThat(runtime.ledger().state().paidAttempts()).isZero();
            assertThat(inputs.amount(0)).isEqualTo(1L);
            assertThat(physicalInput.itemStorage().amount(0)).isEqualTo(1L);
            assertThat(network.amount()).isEqualTo(100L);
            assertThat(buffer.amount()).isZero();
            assertThat(buffer.reserved()).isZero();
            assertThat(buffer.idleExcess()).isZero();
            assertThat(afterCommit).hasValue(0);
        }

        void assertPausedPayment(RecipeCommitLedger payment) {
            assertThat(runtime.active()).isTrue();
            assertThat(runtime.ledger()).isSameAs(payment);
            assertThat(payment.state().phase()).isEqualTo(RecipeCommitLedger.Phase.ERROR_PAUSED);
            assertThat(payment.state().paidAttempts()).isEqualTo(1L);
            assertThat(inputs.amount(0)).isEqualTo(1L);
            assertThat(network.amount()).isEqualTo(96L);
            assertThat(buffer.amount()).isEqualTo(4L);
            assertThat(buffer.reserved()).isEqualTo(4L);
            assertAllocation();
            var state = payment.state();
            runtime.resume();
            runtime.tick();
            runtime.finish();
            assertThat(payment.state()).isEqualTo(state);
            assertThat(inputs.amount(0)).isEqualTo(1L);
            assertThat(network.amount()).isEqualTo(96L);
            assertThat(buffer.reserved()).isEqualTo(4L);
        }

        void assertSinglePayment() {
            assertThat(runtime.active()).isTrue();
            assertThat(runtime.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.READY);
            assertThat(runtime.ledger().state().paidAttempts()).isEqualTo(1L);
            assertThat(inputs.amount(0)).isEqualTo(1L);
            assertThat(network.amount()).isEqualTo(96L);
            assertThat(networkCommits).hasValue(1);
            assertThat(buffer.amount()).isEqualTo(4L);
            assertThat(buffer.reserved()).isEqualTo(4L);
            assertThat(buffer.idleExcess()).isZero();
            assertThat(afterCommit).hasValue(1);
            assertAllocation();
        }

        void assertCancelledPayment() {
            assertThat(inputs.amount(0)).isEqualTo(1L);
            assertThat(network.amount()).isEqualTo(96L);
            assertThat(buffer.amount()).isEqualTo(4L);
            assertThat(buffer.reserved()).isZero();
            assertThat(buffer.idleExcess()).isEqualTo(4L);
        }

        void assertPendingReplacement(PatternStartReservation replacement, RecipeCommitLedger payment) {
            assertThat(replacement.status()).isEqualTo(PatternStartReservation.Status.RESERVED);
            assertThat(replacement.rolledBack()).isFalse();
            assertThat(runtime.active()).isFalse();
            assertThat(runtime.pendingPatternRecipeId()).isEqualTo(recipe.id());
            assertThat(runtime.ledger()).isSameAs(payment);
            assertThat(payment.state().phase()).isEqualTo(RecipeCommitLedger.Phase.READY);
            assertThat(payment.state().paidAttempts()).isZero();
            assertCancelledPayment();
        }

        void assertReplacementPaid() {
            assertThat(runtime.active()).isTrue();
            assertThat(runtime.ledger().state().phase()).isEqualTo(RecipeCommitLedger.Phase.READY);
            assertThat(runtime.ledger().state().paidAttempts()).isEqualTo(1L);
            assertThat(inputs.amount(0)).isZero();
            assertThat(network.amount()).isEqualTo(92L);
            assertThat(buffer.amount()).isEqualTo(8L);
            assertThat(buffer.reserved()).isEqualTo(4L);
            assertThat(buffer.idleExcess()).isEqualTo(4L);
            assertThat(afterCommit).hasValue(1);
            assertAllocation();
        }

        private void assertAllocation() {
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                    HolderLookup.Provider.create(Stream.empty()));
            runtime.save(output);
            CompoundTag saved = output.buildResult();
            var allocations = saved.getCompoundOrEmpty("recipe").getCompoundOrEmpty("data")
                    .getListOrEmpty("prefetched_energy_allocations");
            assertThat(allocations.size()).isEqualTo(1);
            assertThat(allocations.getCompoundOrEmpty(0).getStringOr("key", "")).isEqualTo("pattern");
            assertThat(allocations.getCompoundOrEmpty(0).getLongOr("amount", -1L)).isEqualTo(4L);
        }
    }

    /** Exposes the real Flux facet through the controller's usual component boundary.
     * @author howxu <dev@howxu.cn>
     */
    private static final class PatternFluxHost extends BlockEntity implements CapabilityHost {
        private final CapabilitySnapshot capabilities;

        private PatternFluxHost(MachineCapability capability) {
            this(RuntimeTestFixtures.energyInput(BlockPos.ZERO), capability);
        }

        private PatternFluxHost(BlockEntity entity, MachineCapability capability) {
            super(entity.getType(), entity.getBlockPos(), entity.getBlockState());
            capabilities = new CapabilitySnapshot(List.of(capability));
        }

        @Override
        public CapabilitySnapshot capabilitySnapshot() { return capabilities; }
    }

    private static void formForPattern(MachineControllerBlockEntity controller, boolean linked) {
        if (linked && controller.componentRuntime().components().stream()
                .noneMatch(component -> PATTERN_PORT.equals(component.getPos()))) {
            ItemInputBusBlockEntity source = RuntimeTestFixtures.itemInput(PATTERN_PORT);
            source.setLevel(controller.getLevel());
            List<ProcessingComponent> components = new ArrayList<>(controller.componentRuntime().components());
            components.add(new ProcessingComponent(new MachineComponent(source.kind(), source.ioType()), source,
                    PATTERN_PORT, PATTERN_PORT, (String) null));
            controller.componentRuntime().replaceComponents(components);
        }
        controller.setFormed(true);
        controller.componentRuntime().replaceLinkedPortPositions(linked ? Set.of(PATTERN_PORT) : Set.of());
        RuntimeTestFixtures.republish(controller);
    }

    private static MachineRecipe recipe(String path, List<ItemRequirement> requirements) {
        return RecipeTestSupport.create(MMCR.id(path), MMCR.id("test_cube"), 20,
                List.of(), List.of(), List.of(), 0, 4, false, List.of(), requirements);
    }

    private static MachineRecipe recipeWithOutputs(String path, List<MachineOutput> outputs) {
        List<MachineRequirement> requirements = outputs.stream()
                .map(output -> OutputRegistry.tryToRequirement(output, List.of()))
                .toList();
        return MachineRecipe.fromCanonical(MMCR.id(path), MMCR.id("test_cube"), 20, requirements, outputs,
                List.of(), 0, 1, false, false, false, Set.of());
    }

    private static PatternStartReservation reservation(MachineRecipe recipe, FactoryRuntime factory,
                                                        FactoryRuntime.PatternLane lane) {
        return PatternStartReservation.reserved(recipe, lane.laneId(), lane.runtime(), lane.preparedStart(),
                () -> factory.releasePatternStart(lane));
    }

    private static ItemRequirement input(Item item, int count) {
        return new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(item), count, ItemStack.EMPTY);
    }

    private static ItemRequirement output(Item item, int count) {
        return new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, stack(item, count),
                1F, List.of(), DataComponentPredicateSet.EMPTY, 1F);
    }

    private static ItemStack stack(Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        return stack;
    }

    private static void setItem(cn.howxu.mmcr.api.capability.storage.ResourceStorage<ItemResource> storage,
                                int slot, ItemStack stack) {
        try (Transaction transaction = Transaction.openRoot()) {
            ItemResource current = storage.resource(slot);
            if (current != null && !current.isEmpty()) storage.extract(slot, current, storage.amount(slot), transaction);
            storage.insert(slot, ItemResource.of(stack), stack.getCount(), transaction);
            transaction.commit();
        }
    }

    private static MachineControllerBlockEntity factoryController() {
        Identifier machineId = MMCR.id("reservation_factory");
        RuntimeTestFixtures.registerRecipePool(machineId);
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        BlockPos schedulerPos = controller.getBlockPos().offset(-1, 0, 0);
        BlockArray pattern = new BlockArray(Map.of(new BlockPos(1, 0, 0),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get())));
        DynamicMachine machine = new DynamicMachine(machineId, "reservation factory", pattern,
                MachineControllerSpec.defaultsFor(machineId), PortRequirementSpec.none(), List.of(), Map.of(),
                1, false, true, 1);
        FactorySchedulerBlockEntity scheduler = new FactorySchedulerBlockEntity(schedulerPos,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());
        RuntimeTestFixtures.formStructureWithComponents(controller, machine, scheduler);
        controller.componentRuntime().replaceComponents(List.of(
                new ProcessingComponent(null, scheduler, scheduler.getBlockPos(), BlockPos.ZERO, (String) null)));
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);
        return controller;
    }

}
