package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class FluidRequirementConsumeChanceTest {
    private static FluidIngredient water;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        water = FluidIngredient.of(Fluids.WATER);
    }

    @Test
    void codec_round_trips_consume_chance() {
        FluidRequirement original = newRequirement(RecipeModifier.IOType.INPUT, 0.25F);

        var encoded = FluidRequirement.CODEC.codec().encodeStart(jsonOps(), original).getOrThrow();
        var decoded = FluidRequirement.CODEC.codec().parse(jsonOps(), encoded).getOrThrow();

        assertThat(decoded.consumeChance()).isEqualTo(0.25F);
    }

    @Test
    void fluid_ingredient_round_trips_consume_chance() {
        var fluid = new MachineIngredient.FluidIngredient(water, 1000, 0.25F);
        var encoded = MachineIngredient.CODEC.encodeStart(jsonOps(), fluid).getOrThrow();
        var json = encoded.getAsJsonObject();
        assertThat(json.get("consume_chance").getAsFloat()).isEqualTo(0.25F);

        var decoded = MachineIngredient.CODEC.parse(jsonOps(), encoded).getOrThrow();
        assertThat(((MachineIngredient.FluidIngredient) decoded).consumeChance()).isEqualTo(0.25F);
    }
    @Test
    void codec_defaults_consume_chance_to_one_when_missing() {
        FluidRequirement original = new FluidRequirement(RecipeModifier.IOType.INPUT, water, 1000,
                FluidStack.EMPTY, 1F, of());

        var encoded = FluidRequirement.CODEC.codec().encodeStart(jsonOps(), original).getOrThrow();
        var json = encoded.getAsJsonObject();
        json.remove("consume_chance");

        var decoded = FluidRequirement.CODEC.codec().parse(jsonOps(), json).getOrThrow();
        assertThat(decoded.consumeChance()).isEqualTo(1F);
    }

    @Test
    void copy_preserves_consume_chance() {
        FluidRequirement original = newRequirement(RecipeModifier.IOType.INPUT, 0.5F);
        assertThat(FluidRequirement.copyForTest(original).consumeChance()).isEqualTo(0.5F);
    }

    @Test
    void handler_returns_no_extract_when_consume_chance_is_zero() {
        LongFluidStorage storage = new LongFluidStorage(2_000L, null);
        storage.setFluid(new FluidStack(Fluids.WATER, 1_000));
        FluidHatchCapability capability = new FluidHatchCapability(storage, IOType.INPUT);
        FluidRequirement requirement = new FluidRequirement(RecipeModifier.IOType.INPUT, water, 1_000,
                FluidStack.EMPTY, 1F, of(), 0F);
        FluidRequirementHandler handler = new FluidRequirementHandler();

        RequirementPlan plan = handler.plan(requirement, List.of(capability), emptyContext());

        assertThat(plan.successful()).isTrue();
        assertThat(plan.maxParallelism()).isEqualTo(1L);
        RequirementPlan materialized = plan.materialize(1L, new PlanningReservations(), null);
        assertThat(materialized.operations()).isEmpty();
    }

    @Test
    void handler_uses_consume_profile_when_consume_chance_is_partial() {
        LongFluidStorage storage = new LongFluidStorage(2_000L, null);
        storage.setFluid(new FluidStack(Fluids.WATER, 1_000));
        FluidHatchCapability capability = new FluidHatchCapability(storage, IOType.INPUT);
        FluidRequirement requirement = new FluidRequirement(RecipeModifier.IOType.INPUT, water, 1_000,
                FluidStack.EMPTY, 1F, of(), 0.5F);
        FluidRequirementHandler handler = new FluidRequirementHandler();

        RequirementPlan plan = handler.plan(requirement, List.of(capability), emptyContext());

        assertThat(plan.successful()).isTrue();
        assertThat(plan.maxParallelism()).isEqualTo(1L);
    }

    @Test
    void partial_consumption_reuses_the_same_decisions_for_reservations_materialization_and_retry() {
        LongFluidStorage storage = new LongFluidStorage(3_200L, null);
        storage.setFluid(new FluidStack(Fluids.WATER, 3_200));
        FluidHatchCapability capability = new FluidHatchCapability(storage, IOType.INPUT);
        FluidRequirement requirement = new FluidRequirement(RecipeModifier.IOType.INPUT, water, 100,
                FluidStack.EMPTY, 1F, of(), 0.5F);
        RequirementPlan plan = new FluidRequirementHandler().plan(requirement, List.of(capability),
                new PlanningContext(32L, 0));
        assertThat(plan.successful()).isTrue();

        PlanningReservations reserved = new PlanningReservations();
        assertThat(plan.reserve(32L, reserved)).isNull();
        long remaining = reserved.fluidAmount(storage, 0);
        assertThat(storage.getAmountAsLong()).isEqualTo(3_200L);

        for (int attempt = 0; attempt < 3; attempt++) {
            PlanningReservations materializedReservations = new PlanningReservations();
            RequirementPlan materialized = plan.materialize(32L, materializedReservations, null);
            assertThat(materialized.successful()).isTrue();
            assertThat(materializedReservations.fluidAmount(storage, 0)).isEqualTo(remaining);
            assertThat(storage.getAmountAsLong()).isEqualTo(3_200L);
        }

        RequirementPlan materialized = plan.materialize(32L, new PlanningReservations(), null);
        for (CapabilityOperation operation : materialized.operations()) {
            assertThat(operation.commit().success()).isTrue();
        }
        assertThat(storage.getAmountAsLong()).isEqualTo(remaining);
    }

    @Test
    void zero_consumption_still_requires_live_fluid_to_be_present() {
        LongFluidStorage storage = new LongFluidStorage(2_000L, null);
        FluidHatchCapability capability = new FluidHatchCapability(storage, IOType.INPUT);
        FluidRequirement requirement = newRequirement(RecipeModifier.IOType.INPUT, 0F);
        FluidRequirementHandler handler = new FluidRequirementHandler();

        assertThat(handler.plan(requirement, List.of(capability), emptyContext()).successful()).isFalse();
        storage.setFluid(new FluidStack(Fluids.WATER, 1_000));
        RequirementPlan plan = handler.plan(requirement, List.of(capability), emptyContext());
        assertThat(plan.successful()).isTrue();
        assertThat(plan.materialize(1L, new PlanningReservations(), null).operations()).isEmpty();
        assertThat(storage.getAmountAsLong()).isEqualTo(1_000L);
        storage.clearContent();
        assertThat(handler.plan(requirement, List.of(capability), emptyContext()).successful()).isFalse();
    }

    private static FluidRequirement newRequirement(RecipeModifier.IOType io, float consumeChance) {
        return new FluidRequirement(io, water, 1000,
                FluidStack.EMPTY, 1F, of(), consumeChance);
    }

    private static PlanningContext emptyContext() {
        return new PlanningContext(1L, 0);
    }

    // Helper to keep List.of(...) call sites tidy.
    private static <T> List<T> of(T... values) { return List.of(values); }

    private static DynamicOps<JsonElement> jsonOps() {
        return RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }
}
