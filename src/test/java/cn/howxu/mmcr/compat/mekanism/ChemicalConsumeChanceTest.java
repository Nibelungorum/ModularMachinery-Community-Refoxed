package cn.howxu.mmcr.compat.mekanism;

import cn.howxu.mmcr.api.capability.CapabilityDirections;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.CapabilityView;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.compat.mekanism.MekanismFailureReasons;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandler;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedChemicalRequirement;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedMekanismBridge;
import cn.howxu.mmcr.test.TestBootstrap;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
