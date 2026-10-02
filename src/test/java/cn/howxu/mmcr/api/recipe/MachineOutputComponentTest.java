package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks declaration preservation without bootstrapping a live enchantment registry.
 * @author howxu <dev@howxu.cn>
 */
class MachineOutputComponentTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void materializedMutableComponentsAreOwnedByEachOutputResolution() {
        var codec = Codec.PASSTHROUGH.xmap(value -> value.convert(JsonOps.INSTANCE).getValue().getAsJsonObject(),
                value -> new Dynamic<>(JsonOps.INSTANCE, value));
        var type = DataComponentType.<JsonObject>builder().persistent(codec).build();
        JsonObject source = new JsonObject();
        JsonObject nested = new JsonObject();
        nested.addProperty("value", "original");
        source.add("nested", nested);
        var components = new DataComponentPredicateSet(Map.of(type, ComponentPredicate.exact(source)));
        var requirement = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                new ItemStack(Items.DIAMOND), 1F, components, 1F);

        ItemStack first = requirement.resolvedStack();
        ItemStack second = requirement.resolvedStack();
        first.get(type).getAsJsonObject("nested").addProperty("value", "mutated output");
        first.setCount(64);

        assertThat(second.get(type)).isEqualTo(source);
        assertThat(second.getCount()).isEqualTo(1);
        assertThat(requirement.resolvedStack().get(type)).isEqualTo(source);
        assertThat(components.matches(first)).isFalse();
        assertThat(components.matches(second)).isTrue();
        assertThat(requirement.stack().get(type)).isNull();
    }

    @Test
    void materializedExactAndTextNamesPreserveColorAndOwnership() {
        Component name = Component.literal("Colored output").withStyle(ChatFormatting.RED);
        var predicates = List.of(
                ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE,
                        DataComponents.CUSTOM_NAME.codec().encodeStart(JsonOps.INSTANCE, name).getOrThrow())),
                ComponentPredicate.text(name, ComponentPredicate.TextMode.FULL));

        for (ComponentPredicate predicate : predicates) {
            var components = new DataComponentPredicateSet(Map.of(DataComponents.CUSTOM_NAME, predicate));
            var requirement = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                    new ItemStack(Items.DIAMOND), 1F, components, 1F);
            ItemStack first = requirement.resolvedStack();
            assertThat(first.get(DataComponents.CUSTOM_NAME)).isEqualTo(name);
            first.set(DataComponents.CUSTOM_NAME, Component.literal("Changed").withStyle(ChatFormatting.BLUE));

            ItemStack second = requirement.resolvedStack();
            assertThat(second.get(DataComponents.CUSTOM_NAME)).isEqualTo(name);
            assertThat(second.get(DataComponents.CUSTOM_NAME).getStyle().getColor()).isEqualTo(name.getStyle().getColor());
            assertThat(components.matches(second)).isTrue();
            assertThat(requirement.stack().get(DataComponents.CUSTOM_NAME)).isNull();
        }
    }

    @Test
    void outputConversionCopyAndCodecPreserveUnresolvedEnchantmentDeclarations() {
        JsonObject enchantments = new JsonObject();
        JsonObject levels = new JsonObject();
        levels.addProperty("minecraft:sharpness", 4);
        enchantments.add("levels", levels);
        var components = new DataComponentPredicateSet(Map.of(DataComponents.ENCHANTMENTS,
                ComponentPredicate.exact(new Dynamic<>(JsonOps.INSTANCE, enchantments))));
        var requirement = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                new ItemStack(Items.DIAMOND), 1F, components, 1F);

        MachineOutput output = OutputRegistry.fromRequirement(requirement);
        MachineOutput copy = MachineOutput.copyOf(output).withChance(0.5F);
        MachineOutput decoded = MachineOutput.CODEC.parse(JsonOps.INSTANCE,
                MachineOutput.CODEC.encodeStart(JsonOps.INSTANCE, copy).getOrThrow()).getOrThrow();
        var restored = (ItemRequirement) OutputRegistry.toRequirement(decoded, List.of());

        assertThat(restored.chance()).isEqualTo(0.5F);
        assertThat(DataComponentPredicateSet.CODEC.encodeStart(JsonOps.INSTANCE, restored.components()).getOrThrow())
                .isEqualTo(DataComponentPredicateSet.CODEC.encodeStart(JsonOps.INSTANCE, components).getOrThrow());
        assertThat(OutputRegistry.matchesOutputRequirement(output, requirement)).isTrue();
        assertThat(OutputRegistry.matchesOutputRequirement(decoded,
                new ItemRequirement(requirement.io(), null, 0, requirement.stack(), 0.5F, components, 1F))).isTrue();
        assertThat(OutputRegistry.matchesOutputRequirement(new MachineOutput.ItemOutput(requirement.stack(), 1F), requirement))
                .isFalse();
    }
}
