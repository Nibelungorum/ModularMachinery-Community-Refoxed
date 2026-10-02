package cn.howxu.mmcr.api.recipe.component;

import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.DelegatingOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.function.Predicate;

import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import static org.assertj.core.api.Assertions.assertThat;

class ComponentPredicateTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void textPredicatesMatchStructuredAndFlatRepresentationsWithoutComponentTypeExceptions() {
        Component name = Component.literal("Colored output").withStyle(ChatFormatting.RED);
        Component wrongColor = Component.literal("Colored output").withStyle(ChatFormatting.BLUE);
        Component wrongText = Component.literal("Different output").withStyle(ChatFormatting.RED);
        var full = ComponentPredicate.text(name, ComponentPredicate.TextMode.FULL);
        var plain = ComponentPredicate.text(name, ComponentPredicate.TextMode.PLAIN);

        for (Codec<Component> codec : List.of(ComponentSerialization.CODEC, ComponentSerialization.FLAT_CODEC)) {
            var type = DataComponentType.<Component>builder().persistent(codec).build();
            assertThat(ComponentPredicates.matches(type, name, full, JsonOps.INSTANCE)).isTrue();
            assertThat(ComponentPredicates.matches(type, wrongColor, full, JsonOps.INSTANCE)).isFalse();
            assertThat(ComponentPredicates.matches(type, wrongText, full, JsonOps.INSTANCE)).isFalse();
            assertThat(ComponentPredicates.matches(type, wrongColor, plain, JsonOps.INSTANCE)).isTrue();
            assertThat(ComponentPredicates.matches(type, wrongText, plain, JsonOps.INSTANCE)).isFalse();
        }
        assertThat(full.matches(new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive(7)))).isFalse();
        assertThat(full.matches(new Dynamic<>(JsonOps.INSTANCE, new JsonObject()))).isFalse();
    }

    @Test
    void textMatchingPreservesLiteralQuotesAndJsonTextAccordingToTheDeclaredCodec() {
        for (Codec<Component> codec : List.of(ComponentSerialization.CODEC, ComponentSerialization.FLAT_CODEC)) {
            var type = DataComponentType.<Component>builder().persistent(codec).build();
            for (ComponentPredicate.TextMode mode : ComponentPredicate.TextMode.values()) {
                Component quoted = Component.literal("\"Quartz\"");
                var quotedPredicate = ComponentPredicate.text(quoted, mode);
                var unquotedPredicate = ComponentPredicate.text("Quartz", mode);
                assertThat(ComponentPredicates.matches(type, quoted, quotedPredicate, JsonOps.INSTANCE)).isTrue();
                assertThat(ComponentPredicates.matches(type, quoted, unquotedPredicate, JsonOps.INSTANCE)).isFalse();
                assertThat(ComponentPredicates.matches(type, Component.literal("Quartz"), quotedPredicate, JsonOps.INSTANCE)).isFalse();

                Component jsonText = Component.literal("{\"text\":\"Quartz\"}");
                var jsonPredicate = ComponentPredicate.text(jsonText, mode);
                assertThat(ComponentPredicates.matches(type, jsonText, jsonPredicate, JsonOps.INSTANCE)).isTrue();
                assertThat(ComponentPredicates.matches(type, jsonText, unquotedPredicate, JsonOps.INSTANCE)).isFalse();
            }
        }
        var scalar = DataComponentType.<Integer>builder().persistent(Codec.INT).build();
        assertThat(ComponentPredicates.matches(scalar, 7,
                ComponentPredicate.text("7", ComponentPredicate.TextMode.PLAIN), JsonOps.INSTANCE)).isFalse();
    }

    @Test
    void failedDecodeDoesNotRetryIdenticalOpsOrApplyOuterFallback() {
        var codec = new CountingCodec(ops -> false);
        var type = DataComponentType.<Integer>builder().persistent(codec).build();
        var predicate = ComponentPredicate.exact(new JsonPrimitive(7));
        var predicates = new DataComponentPredicateSet(Map.of(type, predicate));
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);

        assertThat(ComponentPredicates.exactValue(type, predicate, stack)).isNull();
        assertThat(codec.decodeOps).containsExactly(JsonOps.INSTANCE);
        codec.decodeOps.clear();
        assertThat(ComponentPredicates.exactValue(type, predicate, stack, JsonOps.INSTANCE)).isNull();
        assertThat(codec.decodeOps).containsExactly(JsonOps.INSTANCE);
        codec.decodeOps.clear();
        predicates.applyTo(stack);
        assertThat(stack.get(type)).isNull();
        assertThat(codec.decodeOps).containsExactly(JsonOps.INSTANCE);
        codec.decodeOps.clear();
        assertThat(predicates.exactPatch()).isEmpty();
        assertThat(codec.decodeOps).containsExactly(JsonOps.INSTANCE);
        codec.decodeOps.clear();
        assertThat(ComponentPredicates.matches(type, 7, predicate, JsonOps.INSTANCE)).isTrue();
        assertThat(codec.decodeOps).containsExactly(JsonOps.INSTANCE);
        assertThat(codec.encodes).isEqualTo(1);
        codec.decodeOps.clear();
        assertThat(ComponentPredicates.matches(type, 8, predicate, JsonOps.INSTANCE)).isFalse();
        assertThat(codec.decodeOps).containsExactly(JsonOps.INSTANCE);
    }

    @Test
    void differentOverrideOpsFallsBackToOriginalOpsForDecodeAndMatch() {
        var overrideOps = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.EMPTY);
        var codec = new CountingCodec(ops -> {
            if (ops == overrideOps) throw new IllegalStateException("Missing registry context");
            return ops == JsonOps.INSTANCE;
        });
        var type = DataComponentType.<Integer>builder().persistent(codec).build();
        var predicate = ComponentPredicate.exact(new JsonPrimitive(7));

        assertThat(ComponentPredicates.exactValue(type, predicate, ItemStack.EMPTY, overrideOps)).isEqualTo(7);
        assertThat(codec.decodeOps).containsExactly(overrideOps, JsonOps.INSTANCE);
        codec.decodeOps.clear();
        assertThat(ComponentPredicates.matches(type, 7, predicate, overrideOps)).isTrue();
        assertThat(codec.decodeOps).containsExactly(overrideOps, JsonOps.INSTANCE);
        assertThat(codec.encodes).isZero();
        codec.decodeOps.clear();
        assertThat(ComponentPredicates.matches(type, 8, predicate, overrideOps)).isFalse();
        assertThat(codec.decodeOps).containsExactly(overrideOps, JsonOps.INSTANCE);
        assertThat(codec.encodes).isZero();
    }

    @Test
    void successfulOverrideDecodeDoesNotTryOriginalOps() {
        var overrideOps = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.EMPTY);
        var codec = new CountingCodec(ops -> ops == overrideOps);
        var type = DataComponentType.<Integer>builder().persistent(codec).build();
        var predicate = ComponentPredicate.exact(new JsonPrimitive(7));

        assertThat(ComponentPredicates.exactValue(type, predicate, ItemStack.EMPTY, overrideOps)).isEqualTo(7);
        assertThat(codec.decodeOps).containsExactly(overrideOps);
        codec.decodeOps.clear();
        assertThat(ComponentPredicates.matches(type, 7, predicate, overrideOps)).isTrue();
        assertThat(codec.decodeOps).containsExactly(overrideOps);
        assertThat(codec.encodes).isZero();
    }

    @Test
    void failedOverrideAndOriginalDecodeStillSkipApplyAndUseEncodedComparison() {
        var overrideOps = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.EMPTY);
        var codec = new CountingCodec(ops -> false);
        var type = DataComponentType.<Integer>builder().persistent(codec).build();
        var predicate = ComponentPredicate.exact(new JsonPrimitive(7));
        var predicates = new DataComponentPredicateSet(Map.of(type, predicate));
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);

        assertThat(ComponentPredicates.exactValue(type, predicate, stack, overrideOps)).isNull();
        assertThat(codec.decodeOps).containsExactly(overrideOps, JsonOps.INSTANCE);
        codec.decodeOps.clear();
        predicates.applyTo(stack, overrideOps);
        assertThat(stack.get(type)).isNull();
        assertThat(codec.decodeOps).containsExactly(overrideOps, JsonOps.INSTANCE);
        codec.decodeOps.clear();
        assertThat(ComponentPredicates.matches(type, 7, predicate, overrideOps)).isTrue();
        assertThat(codec.decodeOps).containsExactly(overrideOps, JsonOps.INSTANCE);
        codec.decodeOps.clear();
        assertThat(ComponentPredicates.matches(type, 8, predicate, overrideOps)).isFalse();
        assertThat(codec.decodeOps).containsExactly(overrideOps, JsonOps.INSTANCE);
        assertThat(codec.encodes).isEqualTo(2);
        codec.decodeOps.clear();
        codec.rejectEncoding = true;
        assertThat(ComponentPredicates.matches(type, 7, predicate, overrideOps)).isFalse();
        assertThat(codec.decodeOps).containsExactly(overrideOps, JsonOps.INSTANCE);
        assertThat(codec.encodes).isEqualTo(3);
    }

    @Test
    void decodeAndMatchTakeOneDefensiveSnapshotAcrossFallbacks() {
        var originalOps = new SnapshotCountingOps();
        var overrideOps = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.EMPTY);
        var predicate = ComponentPredicate.exact(new Dynamic<>(originalOps, new JsonPrimitive(7)));
        var codec = new CountingCodec(ops -> ops == originalOps);
        var type = DataComponentType.<Integer>builder().persistent(codec).build();

        originalOps.snapshots = 0;
        assertThat(ComponentPredicates.exactValue(type, predicate, ItemStack.EMPTY)).isEqualTo(7);
        assertThat(originalOps.snapshots).isEqualTo(1);
        originalOps.snapshots = 0;
        assertThat(ComponentPredicates.exactValue(type, predicate, ItemStack.EMPTY, overrideOps)).isEqualTo(7);
        assertThat(originalOps.snapshots).isEqualTo(1);
        originalOps.snapshots = 0;
        assertThat(ComponentPredicates.matches(type, 7, predicate, overrideOps)).isTrue();
        assertThat(originalOps.snapshots).isEqualTo(1);

        var failingCodec = new CountingCodec(ops -> false);
        var failingType = DataComponentType.<Integer>builder().persistent(failingCodec).build();
        originalOps.snapshots = 0;
        assertThat(ComponentPredicates.exactValue(failingType, predicate, ItemStack.EMPTY, overrideOps)).isNull();
        assertThat(originalOps.snapshots).isEqualTo(1);
        originalOps.snapshots = 0;
        assertThat(ComponentPredicates.matches(failingType, 7, predicate, overrideOps)).isTrue();
        assertThat(originalOps.snapshots).isEqualTo(1);
        assertThat(failingCodec.decodeOps).containsExactly(overrideOps, originalOps, overrideOps, originalOps);
        assertThat(failingCodec.encodes).isEqualTo(1);
        originalOps.snapshots = 0;
        assertThat(ComponentPredicates.matches(failingType, 8, predicate, originalOps)).isFalse();
        assertThat(originalOps.snapshots).isEqualTo(1);
    }

    @Test
    void exactJsonAccessorAndSourceMutationsDoNotChangeSubsequentMatching() {
        JsonObject source = new JsonObject();
        JsonObject nested = new JsonObject();
        nested.addProperty("value", "original");
        source.add("nested", nested);
        JsonObject expected = source.deepCopy();
        var predicate = (ComponentPredicate.Exact) ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE, source));

        nested.addProperty("value", "source mutation");
        predicate.value().convert(JsonOps.INSTANCE).getValue().getAsJsonObject()
                .getAsJsonObject("nested").addProperty("value", "accessor mutation");

        assertThat(predicate.matches(new Dynamic<>(JsonOps.INSTANCE, expected))).isTrue();
        assertThat(predicate.matches(new Dynamic<>(JsonOps.INSTANCE, source))).isFalse();
        assertThat(predicate.value().convert(JsonOps.INSTANCE).getValue()).isEqualTo(expected);
    }

    @Test
    void listMatchesWhenGreedyFirstCandidateWouldBlockAnotherRequirement() {
        JsonArray values = new JsonArray();
        values.add(1);
        values.add(2);
        ComponentPredicate predicate = ComponentPredicate.list(List.of(
                ComponentPredicate.range(1, 2),
                ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive(1)))
        ));

        assertThat(predicate.matches(new Dynamic<>(JsonOps.INSTANCE, values))).isTrue();
    }

    @Test
    void exactPredicateExportsCustomNamePatch() {
        var predicates = new DataComponentPredicateSet(Map.of(
                DataComponents.CUSTOM_NAME,
                ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE,
                        DataComponents.CUSTOM_NAME.codec().encodeStart(JsonOps.INSTANCE,
                                Component.literal("Required")).getOrThrow()))));

        assertThat(predicates.exactPatch()).isPresent();
        assertThat(predicates.exactPatch().orElseThrow().get(DataComponents.CUSTOM_NAME).orElseThrow())
                .isEqualTo(Component.literal("Required"));
    }

    @Test
    void nonExactPredicateCannotExportPatch() {
        var predicates = new DataComponentPredicateSet(Map.of(
                DataComponents.MAX_STACK_SIZE,
                ComponentPredicate.range(1, 4)));

        assertThat(predicates.exactPatch()).isEmpty();
        assertThat(predicates.hasNonExactValues()).isTrue();
    }

    @Test
    void exactPredicateMatchesAndExportsEnchantmentComponents() {
        var lookup = VanillaRegistries.createLookup();
        var sharpness = lookup.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(
                Registries.ENCHANTMENT, ResourceLocation.parse("minecraft:sharpness")));
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(sharpness, 2);
        sword.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        var encoded = new JsonObject();
        encoded.addProperty("minecraft:sharpness", 2);
        var predicates = new DataComponentPredicateSet(Map.of(
                DataComponents.ENCHANTMENTS,
                ComponentPredicate.exact(new Dynamic<>(RegistryOps.create(JsonOps.INSTANCE, lookup), encoded))));

        assertThat(predicates.matches(sword, RegistryOps.create(JsonOps.INSTANCE, lookup))).isTrue();
        DataComponentPatch patch = predicates.exactPatch().orElseThrow();
        assertThat(patch.get(DataComponents.ENCHANTMENTS).orElseThrow().getLevel(sharpness)).isEqualTo(2);
    }

    @Test
    void applyToMaterializesStandardJsonComponentsOnTargetStack() {
        var lookup = VanillaRegistries.createLookup();
        var sharpness = lookup.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(
                Registries.ENCHANTMENT, ResourceLocation.parse("minecraft:sharpness")));
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        var enchantments = new JsonObject();
        enchantments.addProperty("minecraft:sharpness", 2);
        var predicates = new DataComponentPredicateSet(Map.of(
                DataComponents.ENCHANTMENTS,
                ComponentPredicate.exact(new Dynamic<>(RegistryOps.create(JsonOps.INSTANCE, lookup), enchantments)),
                DataComponents.REPAIR_COST,
                ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive(1)))));

        predicates.applyTo(sword);

        assertThat(sword.get(DataComponents.ENCHANTMENTS).getLevel(sharpness)).isEqualTo(2);
        assertThat(sword.get(DataComponents.REPAIR_COST)).isEqualTo(1);
    }

    @Test
    void matchesStandardJsonComponentsAgainstRuntimeStackValues() {
        var lookup = VanillaRegistries.createLookup();
        var sharpness = lookup.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(
                Registries.ENCHANTMENT, ResourceLocation.parse("minecraft:sharpness")));
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(sharpness, 2);
        sword.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        sword.set(DataComponents.REPAIR_COST, 1);

        var expectedEnchantments = new JsonObject();
        expectedEnchantments.addProperty("minecraft:sharpness", 2);
        var predicates = new DataComponentPredicateSet(Map.of(
                DataComponents.ENCHANTMENTS,
                ComponentPredicate.exact(new Dynamic<>(RegistryOps.create(JsonOps.INSTANCE, lookup), expectedEnchantments)),
                DataComponents.REPAIR_COST,
                ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive(1)))));

        assertThat(predicates.matches(sword, RegistryOps.create(JsonOps.INSTANCE, lookup))).isTrue();
    }

    @Test
    void registryAwareMatchingDecodesPlainJsonEnchantmentPredicates() {
        var lookup = VanillaRegistries.createLookup();
        var enchantments = lookup.lookupOrThrow(Registries.ENCHANTMENT);
        var sharpness = enchantments.getOrThrow(ResourceKey.create(
                Registries.ENCHANTMENT, ResourceLocation.parse("minecraft:sharpness")));
        var unbreaking = enchantments.getOrThrow(ResourceKey.create(
                Registries.ENCHANTMENT, ResourceLocation.parse("minecraft:unbreaking")));
        var expectedEnchantments = new JsonObject();
        expectedEnchantments.addProperty("minecraft:sharpness", 2);
        var predicates = new DataComponentPredicateSet(Map.of(
                DataComponents.ENCHANTMENTS,
                ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE, expectedEnchantments))));

        ItemStack sharpnessTwoSword = new ItemStack(Items.DIAMOND_SWORD);
        sharpnessTwoSword.set(DataComponents.ENCHANTMENTS, enchantments(sharpness, 2));
        ItemStack sharpnessOneSword = new ItemStack(Items.DIAMOND_SWORD);
        sharpnessOneSword.set(DataComponents.ENCHANTMENTS, enchantments(sharpness, 1));
        ItemStack unbreakingTwoSword = new ItemStack(Items.DIAMOND_SWORD);
        unbreakingTwoSword.set(DataComponents.ENCHANTMENTS, enchantments(unbreaking, 2));

        assertThat(predicates.matches(sharpnessTwoSword, RegistryOps.create(JsonOps.INSTANCE, lookup))).isTrue();
        assertThat(predicates.matches(sharpnessOneSword, RegistryOps.create(JsonOps.INSTANCE, lookup))).isFalse();
        assertThat(predicates.matches(unbreakingTwoSword, RegistryOps.create(JsonOps.INSTANCE, lookup))).isFalse();
    }

    @Test
    void matchesRejectsWrongRuntimeComponentValues() {
        var lookup = VanillaRegistries.createLookup();
        var sharpness = lookup.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(
                Registries.ENCHANTMENT, ResourceLocation.parse("minecraft:sharpness")));
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(sharpness, 1);
        sword.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        sword.set(DataComponents.REPAIR_COST, 1);

        var expectedEnchantments = new JsonObject();
        expectedEnchantments.addProperty("minecraft:sharpness", 2);
        var predicates = new DataComponentPredicateSet(Map.of(
                DataComponents.ENCHANTMENTS,
                ComponentPredicate.exact(new Dynamic<>(RegistryOps.create(JsonOps.INSTANCE, lookup), expectedEnchantments)),
                DataComponents.REPAIR_COST,
                ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive(1)))));

        assertThat(predicates.matches(sword)).isFalse();
    }

    private static ItemEnchantments enchantments(Holder<Enchantment> enchantment,
            int level) {
        var mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(enchantment, level);
        return mutable.toImmutable();
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class CountingCodec implements Codec<Integer> {
        private final Predicate<DynamicOps<?>> accepts;
        private final List<DynamicOps<?>> decodeOps = new ArrayList<>();
        private int encodes;
        private boolean rejectEncoding;

        private CountingCodec(Predicate<DynamicOps<?>> accepts) {
            this.accepts = accepts;
        }

        @Override
        public <T> DataResult<Pair<Integer, T>> decode(DynamicOps<T> ops, T input) {
            decodeOps.add(ops);
            return accepts.test(ops) ? Codec.INT.decode(ops, input) : DataResult.error(() -> "Rejected ops");
        }

        @Override
        public <T> DataResult<T> encode(Integer input, DynamicOps<T> ops, T prefix) {
            encodes++;
            if (rejectEncoding) return DataResult.error(() -> "Rejected encoding");
            return Codec.INT.encode(input, ops, prefix);
        }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class SnapshotCountingOps extends DelegatingOps<JsonElement> {
        private int snapshots;

        private SnapshotCountingOps() {
            super(JsonOps.INSTANCE);
        }

        @Override
        public JsonElement createByte(byte value) {
            // JsonOps converts the exact value 7 to a byte when creating its snapshot;
            // Codec.INT decoding only reads it, and encoding delegates createInt directly.
            snapshots++;
            return super.createByte(value);
        }
    }

}
