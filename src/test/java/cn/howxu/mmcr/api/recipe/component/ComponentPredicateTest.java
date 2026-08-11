package cn.howxu.mmcr.api.recipe.component;

import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentPredicateTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        Items.DIAMOND_SWORD.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
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
        assertThat(predicates.exactPatch().orElseThrow().getPatch(DataComponents.CUSTOM_NAME).orElseThrow())
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
                Registries.ENCHANTMENT, Identifier.parse("minecraft:sharpness")));
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
        assertThat(patch.getPatch(DataComponents.ENCHANTMENTS).orElseThrow().getLevel(sharpness)).isEqualTo(2);
    }

    @Test
    void applyToMaterializesStandardJsonComponentsOnTargetStack() {
        var lookup = VanillaRegistries.createLookup();
        var sharpness = lookup.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(
                Registries.ENCHANTMENT, Identifier.parse("minecraft:sharpness")));
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
                Registries.ENCHANTMENT, Identifier.parse("minecraft:sharpness")));
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
                Registries.ENCHANTMENT, Identifier.parse("minecraft:sharpness")));
        var unbreaking = enchantments.getOrThrow(ResourceKey.create(
                Registries.ENCHANTMENT, Identifier.parse("minecraft:unbreaking")));
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
                Registries.ENCHANTMENT, Identifier.parse("minecraft:sharpness")));
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

    private static ItemEnchantments enchantments(net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment,
            int level) {
        var mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(enchantment, level);
        return mutable.toImmutable();
    }

}
