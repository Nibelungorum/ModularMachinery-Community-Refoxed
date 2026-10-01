package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.internal.api.facade.recipe.ComponentAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.ModifierAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.RecipeAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.RequirementAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.OutputAdapters;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.api.capability.plan.CraftingPlan;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.internal.api.facade.recipe.PlanningAdapters;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.publicapi.recipe.IoValues;
import cn.howxu.mmcr.publicapi.recipe.Outputs;
import cn.howxu.mmcr.publicapi.recipe.OutputExtension;
import cn.howxu.mmcr.publicapi.recipe.OutputKinds;
import cn.howxu.mmcr.publicapi.recipe.extension.*;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementExtension;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementExecution;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementKinds;
import cn.howxu.mmcr.publicapi.recipe.requirement.RequirementSpec;
import cn.howxu.mmcr.publicapi.recipe.requirement.Requirements;
import cn.howxu.mmcr.publicapi.recipe.component.ComponentConstraints;
import cn.howxu.mmcr.publicapi.recipe.component.TextMatchMode;
import cn.howxu.mmcr.publicapi.runtime.ItemOutputView;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.recipe.component.ComponentConditions;
import cn.howxu.mmcr.publicapi.recipe.modifier.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Behavioral contracts of the scenario-oriented recipe facade. @author howxu <dev@howxu.cn> */
class PublicRecipeFacadeContractTest {
    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); RequirementHandlerRegistry.registerBuiltIns(); }

    @Test
    void builder_keeps_canonical_requirement_order_and_does_not_duplicate_explicit_io() {
        var level = Requirements.level(id("tier"), id("basic"));
        assertThat(level.kindId()).isNotEqualTo(level.typeId());
        var requirement = Requirements.item(IoDirection.INPUT, Ingredient.of(Items.IRON_INGOT), 2, ItemStack.EMPTY,
                1F, List.of("input-tag"), ComponentConstraints.EMPTY, 0.5F);
        var draft = Recipes.recipe(id("recipe"));
        assertThat(draft.recipePool(id("pool"))).isSameAs(draft);
        var spec = draft.requirement(requirement).inputEnergy((long) Integer.MAX_VALUE + 10)
                .outputItem(Items.GOLD_INGOT, 3).requirement(level).stageRequirement(2)
                .requiredHost(id("host")).requiredHost(id("host")).modifier(id("modifier")).build();
        assertThat(spec.requirements()).hasSize(5);
        assertThat(spec.itemInputs()).hasSize(1);
        assertThat(spec.itemInputs().getFirst().consumeChance()).isEqualTo(0.5F);
        assertThat(spec.energyInputs().getFirst().fePerTick()).isEqualTo((long) Integer.MAX_VALUE + 10);
        assertThat(spec.requiredHostIds()).containsExactly(id("host"));
        assertThat(spec.requirements().getFirst().tags()).containsExactly("input-tag");
        assertThat(RecipeAdapters.unwrap(RecipeAdapters.wrap(RecipeAdapters.unwrap(spec)))).isSameAs(RecipeAdapters.unwrap(spec));
        assertThatThrownBy(() -> Recipes.recipe(id("missing_pool")).build()).isInstanceOf(IllegalStateException.class);
    }
    @Test
    void requirement_overloads_preserve_lenient_vs_strict_semantics_and_copy_tags() {
        var ingredient = Ingredient.of(Items.IRON_INGOT);
        var lenient = Requirements.item(IoDirection.INPUT, ingredient, 1, ItemStack.EMPTY,
                Float.NaN, List.of("tag"), ComponentConstraints.EMPTY, 2F);
        assertThat(lenient.chance()).isEqualTo(1F);
        assertThat(lenient.consumeChance()).isEqualTo(1F);
        assertThat(lenient.copy().tags()).containsExactly("tag");
        assertThatThrownBy(() -> Requirements.item(IoDirection.INPUT, ingredient, 1, ItemStack.EMPTY,
                Float.NaN, ComponentConstraints.EMPTY, 2F)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Requirements.energy(0).fePerTick()).isZero();
        assertThatThrownBy(() -> IoValues.energyRate(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Requirements.stage(IoDirection.OUTPUT, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThat(RequirementAdapters.unwrap(RequirementAdapters.wrap(RequirementAdapters.unwrap(lenient)))).isSameAs(RequirementAdapters.unwrap(lenient));
    }
    @Test
    void output_and_runtime_stack_getters_cannot_mutate_the_recipe_and_conversion_preserves_tags() {
        var stack = new ItemStack(Items.IRON_INGOT, 4);
        var output = Outputs.item(stack, 0.5F);
        stack.setCount(20);
        output.stack().setCount(30);
        assertThat(output.stack().getCount()).isEqualTo(4);
        assertThat(output.amount()).isEqualTo(4L);
        var requirement = Outputs.toRequirement(output, List.of("output-tag"));
        assertThat(requirement.tags()).containsExactly("output-tag");
        assertThat(Outputs.matchesOutputRequirement(output, requirement)).isTrue();
        var recipe = new MachineRecipe(id("runtime"), id("pool"), 10, List.of(),
                List.of(OutputAdapters.unwrap(output)), List.of(), 0, 1, false, true, false, Set.of());
        var view = RecipeAdapters.wrap(recipe);
        ((ItemOutputView) view.outputs().getFirst()).stack().setCount(99);
        assertThat(((ItemOutputView) view.outputs().getFirst()).stack().getCount()).isEqualTo(4);
        assertThat(RecipeAdapters.unwrap(view)).isSameAs(recipe);
        assertThatThrownBy(() -> view.outputs().clear()).isInstanceOf(UnsupportedOperationException.class);
        var replacement = output.withChance(1F);
        assertThat(replacement.chance()).isEqualTo(1F);
        assertThat(output.chance()).isEqualTo(0.5F);
        var fluidStack = new FluidStack(Fluids.WATER, 1000);
        var fluidOutput = Outputs.fluid(fluidStack, 1F);
        fluidStack.setAmount(2000);
        fluidOutput.stack().setAmount(3000);
        assertThat(fluidOutput.amount()).isEqualTo(1000L);
        assertThat(fluidOutput.stack().getAmount()).isEqualTo(1000);
    }
    @Test
    void public_authored_extensions_traverse_copy_codecs_sync_output_conversion_and_core_transaction_execution() {
        try (var requirementsScope = RequirementHandlerRegistry.openTestScope(); var outputsScope = OutputRegistry.openTestScope()) {
            var journal = new JournalValue();
            var kind = RequirementKinds.register(extension(id("custom_requirement"), journal));
            var value = new ExtensionValue(IoDirection.OUTPUT, 3, 0.5F, List.of("tag"));
            var requirement = Requirements.extension(kind, value);
            var copy = requirement.copy();
            assertThat(kind.payload(copy)).contains(value);
            assertThat(RequirementAdapters.unwrap(copy).type()).isSameAs(RequirementAdapters.unwrap(requirement).type());
            var core = RequirementAdapters.unwrap(requirement);
            var encoded = MachineRequirement.CODEC.encodeStart(JsonOps.INSTANCE, core).getOrThrow();
            var decoded = MachineRequirement.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
            assertThat(decoded.type()).isSameAs(core.type());
            assertThat(kind.payload(RequirementAdapters.wrap(decoded))).contains(value);
            assertThatThrownBy(() -> IoValues.customIo(kind.id(), IoDirection.INPUT, encoded)).isInstanceOf(IllegalArgumentException.class);
            var customIo = IoValues.customIo(kind.id(), IoDirection.OUTPUT, encoded);
            customIo.payload().getAsJsonObject().addProperty("amount", 999);
            assertThat(customIo.payload().getAsJsonObject().get("amount").getAsInt()).isEqualTo(3);
            var wrongDiscriminator = encoded.deepCopy();
            wrongDiscriminator.getAsJsonObject().addProperty("type", id("other").toString());
            assertThat(core.type().codec().codec().parse(JsonOps.INSTANCE, wrongDiscriminator).error()).isPresent();
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                var synced = syncRoundTrip(core, buffer);
                assertThat(synced.type()).isSameAs(core.type());
                assertThat(kind.payload(RequirementAdapters.wrap(synced))).contains(value);
            } finally { buffer.release(); }
            var outputKind = OutputKinds.register(new OutputExtension<ExtensionValue>() {
                public ResourceLocation id() { return PublicRecipeFacadeContractTest.id("custom_output"); }
                public MapCodec<ExtensionValue> codec() { return ExtensionValue.CODEC; }
                public ExtensionValue copy(ExtensionValue v) { return v.copy(); }
                public float chance(ExtensionValue v) { return v.chance(); }
                public long amount(ExtensionValue v) { return v.amount(); }
                public ExtensionValue withChance(ExtensionValue v, float chance) { return new ExtensionValue(v.io(), v.amount(), chance, v.tags()); }
                public ExtensionValue applyModifiers(ExtensionValue v, List<RecipeAdjustmentSpec> adjustments) { return v.copy(); }
                public RequirementSpec toRequirement(ExtensionValue v, List<String> tags) { return Requirements.extension(kind, new ExtensionValue(IoDirection.OUTPUT, v.amount(), v.chance(), tags)); }
                public boolean matchesRequirement(RequirementSpec v) { return kind.payload(v).filter(p -> p.io() == IoDirection.OUTPUT).isPresent(); }
                public Optional<ExtensionValue> fromRequirement(RequirementSpec v) { return kind.payload(v).filter(p -> p.io() == IoDirection.OUTPUT); }
            });
            var output = Outputs.extension(outputKind, value);
            var outputCopy = output.copy();
            assertThat(outputKind.payload(outputCopy)).contains(value);
            assertThat(OutputAdapters.unwrap(outputCopy).outputType()).isSameAs(OutputAdapters.unwrap(output).outputType());
            var outputJson = MachineOutput.CODEC.encodeStart(JsonOps.INSTANCE, OutputAdapters.unwrap(output)).getOrThrow();
            var decodedOutput = MachineOutput.CODEC.parse(JsonOps.INSTANCE, outputJson).getOrThrow();
            assertThat(outputKind.payload(OutputAdapters.wrap(decodedOutput))).contains(value);
            var declaration = Recipes.recipe(id("custom_recipe")).recipePool(id("pool"))
                    .custom(IoValues.customIo(outputKind.id(), IoDirection.OUTPUT, outputJson)).build();
            assertThat(declaration.customOutputs()).hasSize(1);
            assertThat(declaration.requirements()).isEmpty();
            var executionRequirement = Outputs.toRequirement(output, List.of("converted-tag"));
            assertThat(executionRequirement.tags()).containsExactly("converted-tag");
            assertThat(Outputs.fromRequirement(executionRequirement).flatMap(outputKind::payload).orElseThrow().tags()).containsExactly("converted-tag");
            var planned = corePlan(RequirementAdapters.unwrap(executionRequirement), new PlanningContext(2, 0));
            assertThat(journal.value).isZero();
            var materialized = planned.materialize(1, new PlanningReservations(), null);
            assertThat(new CraftingPlan(List.of(materialized), 1, Map.of(0, IOType.OUTPUT)).commit()).isTrue();
            assertThat(journal.value).isEqualTo(3);
            var blocked = PlanningAdapters.wrap(List.of(), new PlanningContext(1, 1));
            var status = blocked.failure(id("failed"), id("source"), Map.of("reason", "operation_failed_without_status"));
            var failure = PlanningAdapters.unwrap(blocked.prepared(1, List.of(() -> OperationResult.failure(status))));
            assertThat(new CraftingPlan(List.of(planned.materialize(1, new PlanningReservations(), null), failure), 1,
                    Map.of(0, IOType.OUTPUT, 1, IOType.OUTPUT)).commit()).isFalse();
            assertThat(journal.value).isEqualTo(6);
        }
    }
    @Test
    void extension_registration_rejects_reserved_duplicate_stale_and_unbounded_types() {
        try (var scope = RequirementHandlerRegistry.openTestScope()) {
            var journal = new JournalValue();
            var first = RequirementKinds.register(extension(id("duplicate"), journal));
            assertThatThrownBy(() -> RequirementKinds.register(extension(id("duplicate"), journal))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> RequirementKinds.register(extension(ResourceLocation.parse("minecraft:item"), journal))).isInstanceOf(IllegalArgumentException.class);
            var unbounded = new RequirementExtension<ExtensionValue>() {
                public ResourceLocation id() { return PublicRecipeFacadeContractTest.id("unbounded"); }
                public MapCodec<ExtensionValue> codec() { return ExtensionValue.CODEC; }
                public IoDirection io(ExtensionValue v) { return v.io(); }
                public List<String> tags(ExtensionValue v) { return v.tags(); }
                public ExtensionValue copy(ExtensionValue v) { return v.copy(); }
                public RequirementExecution<ExtensionValue> execution() { return (v, ctx) -> ctx.prepared(1, List.of()); }
                public SyncPayloadCodec<ExtensionValue> syncCodec() { return new SyncPayloadCodec<>() {
                    public int maxPayloadSize() { return Integer.MAX_VALUE; }
                    public void encode(RegistryFriendlyByteBuf b, ExtensionValue v) {}
                    public ExtensionValue decode(RegistryFriendlyByteBuf b) { throw new UnsupportedOperationException(); }
                    public void validate(ExtensionValue v) {}
                }; }
            };
            assertThatThrownBy(() -> RequirementKinds.register(unbounded)).isInstanceOf(IllegalArgumentException.class);
            try (var replacementScope = RequirementHandlerRegistry.openTestScope()) {
                RequirementKinds.register(extension(id("duplicate"), journal));
                assertThatThrownBy(() -> Requirements.extension(first, new ExtensionValue(IoDirection.INPUT, 1, 1F, List.of()))).isInstanceOf(IllegalArgumentException.class);
            }
        }
    }
    @Test
    void output_registration_rejects_duplicate_serialized_ids_and_preserves_unsupported_execution_boundary() {
        try (var scope = OutputRegistry.openTestScope()) {
            var registered = OutputKinds.register(nonExecutableOutput(id("custom"), "custom_serialized"));
            assertThatThrownBy(() -> OutputKinds.register(nonExecutableOutput(id("another"), "custom_serialized"))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> OutputKinds.register(nonExecutableOutput(ResourceLocation.parse("mmcr:item"), "other"))).isInstanceOf(IllegalArgumentException.class);
            var output = Outputs.extension(registered, new ExtensionValue(IoDirection.OUTPUT, 3, 1F, List.of()));
            assertThat(Outputs.tryToRequirement(output, List.of())).isEmpty();
            assertThatThrownBy(() -> Outputs.toRequirement(output, List.of())).isInstanceOf(IllegalStateException.class);
            var encoded = MachineOutput.CODEC.encodeStart(JsonOps.INSTANCE, OutputAdapters.unwrap(output)).getOrThrow();
            encoded.getAsJsonObject().addProperty("type", "unknown_serialized");
            assertThat(MachineOutput.CODEC.parse(JsonOps.INSTANCE, encoded).error()).isPresent();
        }
    }
    private static OutputExtension<ExtensionValue> nonExecutableOutput(ResourceLocation id, String serializedId) {
        return new OutputExtension<>() {
            public ResourceLocation id() { return id; }
            public String serializedId() { return serializedId; }
            public MapCodec<ExtensionValue> codec() { return ExtensionValue.CODEC; }
            public ExtensionValue copy(ExtensionValue v) { return v.copy(); }
            public float chance(ExtensionValue v) { return v.chance(); }
            public long amount(ExtensionValue v) { return v.amount(); }
            public ExtensionValue withChance(ExtensionValue v, float chance) { return new ExtensionValue(v.io(), v.amount(), chance, v.tags()); }
            public ExtensionValue applyModifiers(ExtensionValue v, List<RecipeAdjustmentSpec> adjustments) { return v.copy(); }
            public RequirementSpec toRequirement(ExtensionValue v, List<String> tags) { throw new IllegalStateException("No execution requirement"); }
            public boolean matchesRequirement(RequirementSpec v) { return false; }
        };
    }
    private static RequirementExtension<ExtensionValue> extension(ResourceLocation typeId, JournalValue journal) {
        return new RequirementExtension<>() {
            public ResourceLocation id() { return typeId; }
            public MapCodec<ExtensionValue> codec() { return ExtensionValue.CODEC; }
            public IoDirection io(ExtensionValue v) { return v.io(); }
            public List<String> tags(ExtensionValue v) { return v.tags(); }
            public ExtensionValue copy(ExtensionValue v) { return v.copy(); }
            public RequirementExecution<ExtensionValue> execution() { return (v, ctx) -> ctx.prepared(ctx.requestedParallelism(), List.of(operation(v.amount(), ctx.requestedParallelism(), journal))); }
        };
    }
    private static RecipeOperation operation(int amount, long parallelism, JournalValue journal) {
        return new RecipeOperation() {
            public OperationResult commit() { journal.add(Math.toIntExact(amount * parallelism)); return OperationResult.successful(); }
            public RecipeOperation forParallelism(long value) { return operation(amount, value, journal); }
        };
    }
    @SuppressWarnings("unchecked")
    private static <R extends MachineRequirement> RequirementPlan corePlan(MachineRequirement requirement, PlanningContext context) {
        return ((RequirementType<R>) requirement.type()).handler().plan((R) requirement, List.of(), context);
    }
    @SuppressWarnings("unchecked")
    private static <R extends MachineRequirement> MachineRequirement syncRoundTrip(MachineRequirement requirement, RegistryFriendlyByteBuf buffer) {
        var type = (RequirementType<R>) requirement.type();
        type.syncCodec().encode(buffer, (R) requirement);
        return type.syncCodec().decode(buffer);
    }
    private record ExtensionValue(IoDirection io, int amount, float chance, List<String> tags) {
        private static final MapCodec<ExtensionValue> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.xmap(IoDirection::valueOf, IoDirection::name).fieldOf("io").forGetter(ExtensionValue::io),
                Codec.INT.fieldOf("amount").forGetter(ExtensionValue::amount),
                Codec.FLOAT.fieldOf("chance").forGetter(ExtensionValue::chance),
                Codec.STRING.listOf().fieldOf("tags").forGetter(ExtensionValue::tags)
        ).apply(instance, ExtensionValue::new));
        ExtensionValue { tags = List.copyOf(tags); }
        ExtensionValue copy() { return new ExtensionValue(io, amount, chance, tags); }
    }
    private static final class JournalValue {
        private int value;
        void add(int amount) { value += amount; }
    }
    @Test
    void deferred_extension_planning_uses_real_capabilities_and_shared_reservations_without_committing_during_simulation() {
        var storage = new LongEnergyStorage(100, 100, null);
        storage.setAmount(12);
        var capability = new EnergyHatchCapability(storage, cn.howxu.mmcr.util.IOType.INPUT);
        var context = new PlanningContext(3, 0);
        var planning = PlanningAdapters.wrap(List.of(capability), context);
        var access = planning.capabilities().getFirst();
        var view = access.longValues().orElseThrow();
        var status = planning.failure(id("not_enough_energy"), id("source"), Map.of("reason", "missing_energy"));
        var plan = PlanningAdapters.unwrap(planning.deferred(3, (parallelism, reservations) -> {
            long amount = 4 * parallelism;
            if (!reservations.reserveValue(view, amount, false)) return new OperationBatch(List.of(), status);
            return new OperationBatch(List.of(access.prepareValue(IoDirection.INPUT, parallelism, amount, false)), null);
        }, (parallelism, reservations) -> new ReservationCheck(
                reservations.reserveValue(view, 4 * parallelism, false) ? null : status, null)));
        var shared = new PlanningReservations();
        assertThat(plan.reserve(2, shared)).isNull();
        assertThat(plan.reserve(2, shared)).isNotNull();
        assertThat(storage.getAmountAsLong()).isEqualTo(12);
        var materialized = plan.materialize(2, new PlanningReservations(), null);
        assertThat(storage.getAmountAsLong()).isEqualTo(12);
        assertThat(new CraftingPlan(List.of(materialized), 2, Map.of(0, IOType.INPUT)).commit()).isTrue();
        assertThat(storage.getAmountAsLong()).isEqualTo(4);
    }
    @Test
    void wakeups_use_typed_resource_changes_and_reject_unrelated_resource_classes() {
        var expected = new ItemStack(Items.IRON_INGOT);
        var wakeup = PlanningAdapters.wakeup(new ResourceWakeupSpec<>(Set.of(id("missing_item")),
                ResourceWakeupSpec.Reason.INPUT_AVAILABLE, ItemStack.class,
                change -> ItemStack.isSameItemSameComponents(change.resource(), expected) && change.reason() == ResourceWakeupSpec.Reason.INPUT_AVAILABLE));
        assertThat(wakeup.matcher().test(expected)).isTrue();
        assertThat(wakeup.matcher().test(new ItemStack(Items.GOLD_INGOT))).isFalse();
        assertThat(wakeup.matcher().test("foreign")).isFalse();
    }
    @Test
    void text_modes_distinguish_style_and_exact_values_are_defensive_copies() {
        var plain = ComponentConditions.text("name", TextMatchMode.PLAIN);
        var full = ComponentConditions.text("name", TextMatchMode.FULL);
        var styled = Component.literal("name").withStyle(ChatFormatting.RED);
        var candidate = new Dynamic<>(JsonOps.INSTANCE, ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, styled).getOrThrow());
        assertThat(plain.matches(candidate)).isTrue();
        assertThat(full.matches(candidate)).isFalse();
        var json = JsonParser.parseString("{\"value\":1}");
        var exact = ComponentConditions.exact(json);
        json.getAsJsonObject().addProperty("value", 2);
        exact.value().convert(JsonOps.INSTANCE).getValue().getAsJsonObject().addProperty("value", 3);
        assertThat(exact.matches(json("{\"value\":1}"))).isTrue();
        var constraints = ComponentConstraints.ofTypes(Map.of(DataComponents.CUSTOM_NAME, plain));
        var stack = new ItemStack(Items.IRON_INGOT);
        constraints.applyTo(stack);
        assertThat(stack.get(DataComponents.CUSTOM_NAME).getString()).isEqualTo("name");
        assertThat(constraints.exactPatch()).isEmpty();
        assertThatThrownBy(() -> IoValues.itemOutput(stack, constraints)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void wrapping_preserves_identity_and_list_matching_consumes_each_candidate_once() {
        var exact = ComponentConditions.exact(JsonParser.parseString("1"));
        assertThat(ComponentAdapters.unwrap(ComponentAdapters.wrap(ComponentAdapters.unwrap(exact)))).isSameAs(ComponentAdapters.unwrap(exact));
        var condition = ComponentConditions.list(List.of(ComponentConditions.range(1, 3), exact));
        assertThat(condition.matches(json("[1,2]"))).isTrue();
        assertThat(condition.matches(json("[1]"))).isFalse();
        assertThat(ComponentConditions.map(Map.of("x", exact)).matches(json("{\"x\":1,\"extra\":2}"))).isTrue();
    }
    @Test
    void scheduler_modifiers_are_not_raw_recipe_adjustments_and_invalid_scopes_are_rejected() {
        var parallelism = Modifiers.numeric("parallelism", ModifierScope.MACHINE, 2, ModifierOperation.ADD, false);
        var factory = Modifiers.numeric("factory_threads", ModifierScope.MACHINE, 2, ModifierOperation.ADD, false);
        var recipe = Modifiers.numeric("recipe_threads", ModifierScope.RECIPE, 2, ModifierOperation.ADD, false);
        var flag = Modifiers.parallelized(false);
        var bundle = Modifiers.bundle(parallelism, factory, recipe, flag);
        assertThat(bundle.modifiers()).hasSize(4);
        assertThat(Modifiers.recipeModifiers(bundle.modifiers())).isEmpty();
        assertThat(ModifierAdapters.unwrap(ModifierAdapters.wrap(ModifierAdapters.unwrap(bundle)))).isSameAs(ModifierAdapters.unwrap(bundle));
        assertThatThrownBy(() -> Modifiers.numeric("parallelism", ModifierScope.INPUT, 2, ModifierOperation.ADD, false)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void adjustments_use_core_aggregate_arithmetic_and_smart_mapping() {
        var add = Modifiers.recipe("", IoDirection.INPUT, 3, ModifierOperation.ADD, false);
        var multiply = Modifiers.recipe("energy", IoDirection.INPUT, 2, ModifierOperation.MULTIPLY, false);
        var zero = Modifiers.recipe("energy", IoDirection.INPUT, 0, ModifierOperation.DIVIDE, false);
        assertThat(Modifiers.apply(List.of(multiply, zero, add), "energy", IoDirection.INPUT, 5F, false)).isEqualTo(16F);
        assertThat(Modifiers.apply(List.of(add), "energy", IoDirection.OUTPUT, 5F, false)).isEqualTo(5F);
        assertThat(Modifiers.apply(List.of(add), "energy", IoDirection.INPUT, 5F, true)).isEqualTo(5F);
        var smart = Modifiers.smartEnergy("heat", 0, 10, 1, 3, ModifierOperation.MULTIPLY);
        assertThat(smart.toModifier(5).value()).isEqualTo(2D);
        assertThat(smart.mappedValue(20)).isEqualTo(3F);
    }
    private static Dynamic<?> json(String value) { return new Dynamic<>(JsonOps.INSTANCE, JsonParser.parseString(value)); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("recipe_facade_test", path); }
}
