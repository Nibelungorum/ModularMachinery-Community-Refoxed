package cn.howxu.mmcr.compat.mekanism;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.InputRoutingFacet;
import cn.howxu.mmcr.api.recipe.InputScope;
import cn.howxu.mmcr.internal.recipe.RequirementPlanner;
import net.minecraft.core.BlockPos;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.plan.CraftingPlan;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.compat.mekanism.MekanismFailureReasons;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerSupport.Sampling;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedMekanismBridge;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import com.mojang.serialization.JsonOps;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalBuilder;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.chemical.attribute.ChemicalAttributeValidator;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ChemicalConsumeChanceTest {
    @Test
    void loaded_chemical_codec_round_trips_consume_chance() {
        LoadedChemicalRequirement original = new LoadedChemicalRequirement(RecipeModifier.IOType.INPUT,
                ChemicalIngredient.chemical(ResourceLocation.parse("mekanism:oxygen"), 1_000L), 1F, List.of(), 0.25F);

        var encoded = LoadedChemicalRequirement.CODEC.codec().encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        var decoded = LoadedChemicalRequirement.CODEC.codec().parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertThat(decoded.consumeChance()).isEqualTo(0.25F);
    }

    @Test
    void loaded_chemical_codec_defaults_consume_chance_when_missing() {
        LoadedChemicalRequirement original = LoadedChemicalRequirement.input(
                ChemicalIngredient.chemical(ResourceLocation.parse("mekanism:oxygen"), 1_000L));

        var encoded = LoadedChemicalRequirement.CODEC.codec().encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        var json = encoded.getAsJsonObject();
        json.remove("consume_chance");

        var decoded = LoadedChemicalRequirement.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
        assertThat(decoded.consumeChance()).isEqualTo(1F);
    }

    @Test
    void unavailable_chemical_codec_round_trips_consume_chance() {
        var encoded = MekanismRecipeDeclarations.CHEMICAL_CODEC.codec()
                .encodeStart(JsonOps.INSTANCE,
                        new MekanismRecipeDeclarations.UnavailableChemicalRequirement(RecipeModifier.IOType.INPUT,
                                ChemicalIngredient.chemical(ResourceLocation.parse("mekanism:oxygen"), 1_000L), 1F, List.of(), 0.5F))
                .getOrThrow();
        var decoded = MekanismRecipeDeclarations.CHEMICAL_CODEC.codec().parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertThat(decoded.consumeChance()).isEqualTo(0.5F);
    }

    @Test
    void unavailable_chemical_codec_defaults_consume_chance_when_missing() {
        var original = new MekanismRecipeDeclarations.UnavailableChemicalRequirement(RecipeModifier.IOType.INPUT,
                ChemicalIngredient.chemical(ResourceLocation.parse("mekanism:oxygen"), 1_000L), 1F, List.of(), 0.5F);

        var encoded = MekanismRecipeDeclarations.CHEMICAL_CODEC.codec().encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        var json = encoded.getAsJsonObject();
        json.remove("consume_chance");

        var decoded = MekanismRecipeDeclarations.CHEMICAL_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
        assertThat(decoded.consumeChance()).isEqualTo(1F);
    }

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrapCapabilities();
        BuiltinFailureReasons.register();
        MekanismBridgeBootstrap.bootstrap();
    }

    /**
     * The {@link BeforeAll} hook above mutates {@link MekanismAPI#CHEMICAL_REGISTRY} by registering
     * test-only chemicals (see {@link #registerChemical(String)}). {@code MappedRegistry} cannot be
     * unfrozen from outside Mekanism bridge code, so the global registry retains these entries
     * across the test JVM. Tests are isolated by using unique namespaced identifiers
     * ({@code mmcr_test:*}) so collisions with other tests or production code are impossible.
     */
    @AfterAll
    static void documentRegistryLeak() {
        // Intentionally a no-op: see Javadoc above.
    }

    @Test
    void chemical_handler_returns_no_extract_when_consume_chance_is_zero() {
        Holder.Reference<Chemical> chemical = registerChemical("zero_consume");
        FakeChemicalTank tank = new FakeChemicalTank(2_000L, ChemicalAttributeValidator.ALWAYS_ALLOW);
        tank.setStack(new ChemicalStack(chemical, 1_000L));
        FakeChemicalPort port = new FakeChemicalPort(tank, IOType.INPUT);
        LoadedChemicalRequirement requirement = new LoadedChemicalRequirement(RecipeModifier.IOType.INPUT,
                ChemicalIngredient.chemical(chemical.key().location(), 1_000L), 1F, List.of(), 0F);
        RequirementHandler<LoadedChemicalRequirement> handler = LoadedMekanismBridge.chemicalHandler();

        RequirementPlan plan = handler.plan(requirement, List.of(port), emptyContext());

        assertThat(plan.successful()).isTrue();
        assertThat(plan.maxParallelism()).isEqualTo(1L);
        RequirementPlan materialized = plan.materialize(1L, new PlanningReservations(), null);
        assertThat(materialized.operations()).isEmpty();
        assertThat(tank.amount()).isEqualTo(1_000L);
    }

    @Test
    void chemical_handler_uses_consume_profile_when_consume_chance_is_partial() {
        Holder.Reference<Chemical> chemical = registerChemical("partial_consume");
        FakeChemicalTank tank = new FakeChemicalTank(10_000L, ChemicalAttributeValidator.ALWAYS_ALLOW);
        tank.setStack(new ChemicalStack(chemical, 5_000L));
        FakeChemicalPort port = new FakeChemicalPort(tank, IOType.INPUT);
        LoadedChemicalRequirement requirement = new LoadedChemicalRequirement(RecipeModifier.IOType.INPUT,
                ChemicalIngredient.chemical(chemical.key().location(), 1_000L), 1F, List.of(), 0.5F);
        RequirementHandler<LoadedChemicalRequirement> handler = LoadedMekanismBridge.chemicalHandler();
        PlanningContext context = new PlanningContext(10L, 0, false, new PlanningReservations(), Map.of());

        RequirementPlan plan = handler.plan(requirement, List.of(port), context);

        assertThat(plan.successful()).isTrue();
        assertThat(plan.maxParallelism()).isEqualTo(5L);
    }

    @Test
    void partial_consumption_reserves_and_commits_both_real_sampled_outcomes() throws Exception {
        Holder.Reference<Chemical> chemical = registerChemical("sampled_consumption");
        var tank = new FakeChemicalTank(2_000L, ChemicalAttributeValidator.ALWAYS_ALLOW);
        var port = new FakeChemicalPort(tank, IOType.INPUT);
        var requirement = new LoadedChemicalRequirement(RecipeModifier.IOType.INPUT,
                ChemicalIngredient.chemical(chemical.key().location(), 500L), 1F, List.of(), 0.5F);
        var handler = LoadedMekanismBridge.chemicalHandler();
        for (boolean consume : List.of(false, true)) {
            tank.setStack(new ChemicalStack(chemical, 1_000L));
            var context = new PlanningContext(1L, 0, false, new PlanningReservations(), Map.of(),
                    null, null, sampled(consume));
            var plan = handler.plan(requirement, List.of(port), context);
            assertThat(plan.successful()).isTrue();
            assertThat(plan.reserve(1L, new PlanningReservations())).isNull();
            var materialized = plan.materialize(1L, new PlanningReservations(), null);
            assertThat(materialized.successful()).isTrue();
            assertThat(materialized.operations().isEmpty()).isEqualTo(!consume);
            assertThat(new CraftingPlan(List.of(materialized), 1L,
                    Map.of(0, RecipeModifier.IOType.INPUT)).commitInputs()).isTrue();
            assertThat(tank.amount()).isEqualTo(consume ? 500L : 1_000L);
            tank.setEmpty();
            assertThat(handler.plan(requirement, List.of(port), context).successful()).isFalse();
        }
    }

    @Test
    void retained_probabilistic_chemical_is_rechecked_without_a_second_consumption_roll() throws Exception {
        TestBootstrap.bootstrap();
        try (var requirements = RequirementHandlerRegistry.openTestScope();
             var ignored = ServerThreadTestScope.open()) {
            RequirementHandlerRegistry.register(LoadedChemicalRequirement.TYPE);
            var chemical = registerChemical("retained_runtime_condition");
            var tank = new FakeChemicalTank(2_000L, ChemicalAttributeValidator.ALWAYS_ALLOW);
            tank.setStack(new ChemicalStack(chemical, 1_000L));
            var port = new FakeChemicalPort(tank, IOType.INPUT);
            var controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
            var host = new ChemicalConditionHost(new BlockPos(1, 0, 0), port);
            host.setLevel(controller.getLevel());
            controller.componentRuntime().replaceComponents(List.of(new ProcessingComponent(null, host,
                    host.getBlockPos(), host.getBlockPos(), List.of())));
            controller.setFormed(true);
            var runtime = new CraftingRuntime(controller, controller.componentRuntime());
            var requirement = new LoadedChemicalRequirement(RecipeModifier.IOType.INPUT,
                    ChemicalIngredient.chemical(chemical.key().location(), 500L), 1F, List.of(), 0.5F);
            var recipe = MachineRecipe.fromCanonical(MMCR.id("retained_chemical_runtime"), MMCR.id("test_cube"), 2,
                    List.of(requirement), List.of(), List.of(), 0, 1, false, false, false, Set.of());
            var previous = RecipeRegistry.dynamicSnapshot();
            var handler = LoadedMekanismBridge.chemicalHandler();
            var startup = new AtomicBoolean(true);
            var checks = new AtomicInteger();
            var sample = sampled(false);
            var delegateField = LoadedChemicalRequirement.class.getDeclaredField("delegate");
            delegateField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var previousHandler = (RequirementHandler<LoadedChemicalRequirement>) delegateField.get(null);
            LoadedChemicalRequirement.installHandler((value, capabilities, context) -> {
                if (!startup.get()) {
                    assertThat(value.consumeChance()).isZero();
                    checks.incrementAndGet();
                }
                return handler.plan(value, capabilities, new PlanningContext(context.requestedParallelism(),
                        context.requirementIndex(), context.allowPartialOutputs(), context.reservations(),
                        context.outputPolicies(), context.inputRouting(), context.inputScope(),
                        startup.get() ? sample : context.sampling(), context.reservationOwner()));
            });
            try {
                RecipeRegistry.replaceDynamic(Map.of(recipe.id(), recipe));
                assertThat(runtime.start(recipe, 1L).isCrafting()).isTrue();
                startup.set(false);
                tank.setEmpty();
                LevelStub.setGameTime(controller.getLevel(), 1L);
                runtime.tick();
                assertThat(runtime.tickCount()).isZero();
                assertThat(runtime.failure()).isNotNull();
                tank.setStack(new ChemicalStack(chemical, 1_000L));
                for (int tick = 2; tick <= 3; tick++) {
                    LevelStub.setGameTime(controller.getLevel(), tick);
                    runtime.tick();
                }
                assertThat(runtime.finishPending()).isTrue();
                assertThat(runtime.ledger().state().finalTickPaid()).isTrue();
                runtime.finish();
                assertThat(runtime.active()).isFalse();
                assertThat(checks.get()).isGreaterThanOrEqualTo(2);
                assertThat(tank.amount()).isEqualTo(1_000L);
            } finally {
                try { runtime.invalidate(); }
                finally {
                    LoadedChemicalRequirement.installHandler(previousHandler);
                    RecipeRegistry.replaceDynamic(previous);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Sampling sampled(boolean consume) throws ReflectiveOperationException {
        var sample = new Sampling();
        var inputs = Sampling.class.getDeclaredField("inputs");
        inputs.setAccessible(true);
        ((Map<Integer, RequirementHandlerSupport.ConsumeProfile>) inputs.get(sample)).put(0,
                new RequirementHandlerSupport.ConsumeProfile(new boolean[]{consume}, 0.5F));
        return sample;
    }

    /** Native capability owner for the real runtime condition regression.
     * @author howxu <dev@howxu.cn>
     */
    private static final class ChemicalConditionHost extends BlockEntity implements CapabilityHost {
        private final CapabilitySnapshot snapshot;

        ChemicalConditionHost(BlockPos pos, MachineCapability capability) {
            super(ModBlockEntities.BES.get("item_input_bus").get(), pos,
                    ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
            snapshot = new CapabilitySnapshot(List.of(capability));
        }

        @Override public CapabilitySnapshot capabilitySnapshot() { return snapshot; }
    }

    @Test
    void chemical_material_scope_filters_before_handler_aggregation() {
        Holder.Reference<Chemical> chemical = registerChemical("routed_input");
        FakeChemicalTank first = new FakeChemicalTank(2_000, ChemicalAttributeValidator.ALWAYS_ALLOW);
        FakeChemicalTank second = new FakeChemicalTank(2_000, ChemicalAttributeValidator.ALWAYS_ALLOW);
        first.setStack(new ChemicalStack(chemical, 500));
        second.setStack(new ChemicalStack(chemical, 500));
        List<MachineCapability> capabilities = List.of(new FakeChemicalPort(first, IOType.INPUT),
                new FakeChemicalPort(second, IOType.INPUT));
        BlockPos a = new BlockPos(1, 0, 0);
        BlockPos b = new BlockPos(2, 0, 0);
        InputRoutingFacet routing = new InputRoutingFacet(1,
                List.of(new InputRoutingFacet.Source(0, a), new InputRoutingFacet.Source(1, b)),
                List.of(new InputRoutingFacet.Port(a, false), new InputRoutingFacet.Port(b, false)));
        LoadedChemicalRequirement requirement = LoadedChemicalRequirement.input(
                ChemicalIngredient.chemical(chemical.key().location(), 1_000));
        RequirementHandler<LoadedChemicalRequirement> handler = LoadedMekanismBridge.chemicalHandler();
        var ordinary = handler.plan(requirement, RequirementPlanner.matchingCapabilities(requirement, capabilities,
                routing, routing.ordinaryScope()), emptyContext());
        var isolated = handler.plan(requirement, RequirementPlanner.matchingCapabilities(requirement, capabilities,
                routing, new InputScope(InputScope.Kind.ISOLATED, List.of(a))), emptyContext());
        assertThat(ordinary.successful()).isTrue();
        assertThat(ordinary.reserve(1, new PlanningReservations())).isNull();
        assertThat(isolated.successful()).isFalse();
    }

    @Test
    void chemical_handler_apply_modifiers_rewrites_consume_chance_for_input() {
        LoadedChemicalRequirement requirement = new LoadedChemicalRequirement(RecipeModifier.IOType.INPUT,
                ChemicalIngredient.chemical(ResourceLocation.parse("mekanism:oxygen"), 1_000L), 1F, List.of(), 1F);
        RequirementHandler<LoadedChemicalRequirement> handler = LoadedMekanismBridge.chemicalHandler();
        List<RecipeModifier> modifiers = List.of(new RecipeModifier("chemical",
                RecipeModifier.IOType.INPUT, -0.5F, RecipeModifier.Operation.ADD, true));

        LoadedChemicalRequirement modified = handler.applyModifiers(requirement, modifiers);

        assertThat(modified.consumeChance()).isEqualTo(0.5F);
    }

    private static PlanningContext emptyContext() {
        return new PlanningContext(1L, 0, false, new PlanningReservations(), Map.of());
    }

    private static Holder.Reference<Chemical> registerChemical(String path) {
        ResourceKey<Chemical> key = ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME,
                ResourceLocation.fromNamespaceAndPath("mmcr_test", path));
        MappedRegistry<Chemical> registry = (MappedRegistry<Chemical>) MekanismAPI.CHEMICAL_REGISTRY;
        if (registry.get(key) != null) return registry.getHolder(key).orElseThrow();
        registry.unfreeze();
        if (registry.get(MekanismAPI.EMPTY_CHEMICAL_KEY) == null) {
            Registry.registerForHolder(registry, MekanismAPI.EMPTY_CHEMICAL_KEY, MekanismAPI.EMPTY_CHEMICAL);
        }
        Chemical value = new Chemical(ChemicalBuilder.builder()) {
            @Override
            public boolean isRadioactive() {
                return false;
            }
        };
        Registry.registerForHolder(registry, key, value);
        registry.freeze();
        return registry.getHolder(key).orElseThrow();
    }

    private static final class FakeChemicalPort implements LoadedMekanismBridge.ChemicalPort {
        private final IChemicalTank tank;
        private final CapabilityView view;

        private FakeChemicalPort(IChemicalTank tank, IOType ioType) {
            this.tank = tank;
            this.view = new CapabilityView() {
                @Override
                public CapabilityType type() {
                    return new CapabilityType(MekanismRecipeTypes.CHEMICAL);
                }

                @Override
                public CapabilityDirections directions() {
                    return CapabilityDirections.of(ioType);
                }
            };
        }

        @Override
        public IChemicalTank chemicalTank() {
            return tank;
        }

        @Override
        public CapabilityType type() {
            return view.type();
        }

        @Override
        public CapabilityView view() {
            return view;
        }
    }

    private static final class FakeChemicalTank implements IChemicalTank {
        private final long capacity;
        private final ChemicalAttributeValidator attributeValidator;
        private ChemicalStack identity = ChemicalStack.EMPTY;
        private long amount;

        private FakeChemicalTank(long capacity, ChemicalAttributeValidator attributeValidator) {
            this.capacity = capacity;
            this.attributeValidator = attributeValidator;
        }

        @Override
        public ChemicalStack getStack() {
            return identity.isEmpty() ? ChemicalStack.EMPTY : identity.copyWithAmount(amount);
        }

        @Override
        public void setStack(ChemicalStack stack) {
            if (!stack.isEmpty() && !isValid(stack)) throw new IllegalArgumentException("Invalid chemical");
            setStackUnchecked(stack);
        }

        @Override
        public void setStackUnchecked(ChemicalStack stack) {
            identity = stack.isEmpty() ? ChemicalStack.EMPTY : stack.copyWithAmount(1L);
            amount = stack.isEmpty() ? 0L : stack.getAmount();
        }

        @Override
        public long getCapacity() {
            return capacity;
        }

        @Override
        public boolean isValid(ChemicalStack stack) {
            return !stack.isEmpty();
        }

        @Override
        public void onContentsChanged() {
        }

        @Override
        public ChemicalAttributeValidator getAttributeValidator() {
            return attributeValidator;
        }

        private long amount() {
            return amount;
        }
    }
}
