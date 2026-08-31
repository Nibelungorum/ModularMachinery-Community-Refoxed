package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
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
class MachineRequirementCodecTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        Fluids.WATER.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
    }

    @Test
    void codec_round_trips_all_builtin_requirement_types() {
        List<MachineRequirement> requirements = List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 2, ItemStack.EMPTY),
                new FluidRequirement(RecipeModifier.IOType.INPUT, FluidIngredient.of(Fluids.WATER), 250, FluidStack.EMPTY),
                new EnergyRequirement(RecipeModifier.IOType.INPUT, 40),
                SmartInterfaceRequirement.input("mode", 1F, 2F));
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

        for (MachineRequirement requirement : requirements) {
            JsonElement encoded = MachineRequirement.CODEC.encodeStart(ops, requirement).getOrThrow();
            assertThat(MachineRequirement.CODEC.parse(ops, encoded).getOrThrow()).isEqualTo(requirement);
            assertThat(RequirementHandlerRegistry.handlerFor(requirement.type())).isNotNull();
        }
    }

    @Test
    void codec_rejects_an_unknown_requirement_type() {
        JsonObject encoded = new JsonObject();
        encoded.addProperty("type", "mmcr:unknown");

        var result = MachineRequirement.CODEC.parse(JsonOps.INSTANCE, encoded);

        assertThat(result.error()).isPresent();
    }

    @Test
    void test_scope_preserves_builtin_requirement_registrations() {
        RequirementHandlerRegistry.registerBuiltIns();

        try (var ignored = RequirementHandlerRegistry.openTestScope()) {
            assertThat(RequirementHandlerRegistry.typeFor(ItemRequirement.TYPE.id()))
                    .isSameAs(ItemRequirement.TYPE);
            assertThat(RequirementHandlerRegistry.typeFor(EnergyRequirement.TYPE.id()))
                    .isSameAs(EnergyRequirement.TYPE);
        }

        assertThat(RequirementHandlerRegistry.typeFor(ItemRequirement.TYPE.id()))
                .isSameAs(ItemRequirement.TYPE);
    }
}
