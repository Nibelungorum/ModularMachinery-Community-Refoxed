package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
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
