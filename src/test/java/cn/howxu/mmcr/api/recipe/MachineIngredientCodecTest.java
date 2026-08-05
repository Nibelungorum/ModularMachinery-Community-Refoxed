package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
class MachineIngredientCodecTest {

    @BeforeAll static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test void itemIngredient_roundtrip() {
        var ing = new MachineIngredient.ItemIngredient(Ingredient.of(Items.IRON_INGOT), 2);
        var json = MachineIngredient.CODEC.encodeStart(jsonOps(), ing).getOrThrow();
        var back = MachineIngredient.CODEC.parse(jsonOps(), json).getOrThrow();
        assertThat(back).isEqualTo(ing);
    }

    @Test void energyIngredient_roundtrip() {
        var ing = new MachineIngredient.EnergyIngredient(80);
        var json = MachineIngredient.CODEC.encodeStart(jsonOps(), ing).getOrThrow();
        var back = MachineIngredient.CODEC.parse(jsonOps(), json).getOrThrow();
        assertThat(back).isEqualTo(ing);
    }

    @Test void fluidIngredient_roundtrip_water() {
        var ing = new MachineIngredient.FluidIngredient(FluidIngredient.of(Fluids.WATER), 1000);
        var json = MachineIngredient.CODEC.encodeStart(jsonOps(), ing).getOrThrow();
        var back = MachineIngredient.CODEC.parse(jsonOps(), json).getOrThrow();
        assertThat(back).isEqualTo(ing);
    }

    @Test void itemRequirement_roundtrip_with_io_and_tag() {
        var requirement = new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 3, net.minecraft.world.item.ItemStack.EMPTY, List.of("north_buses"));

        var json = MachineRequirement.CODEC.encodeStart(jsonOps(), requirement).getOrThrow();
        var back = MachineRequirement.CODEC.parse(jsonOps(), json).getOrThrow();

        assertThat(back).isEqualTo(requirement);
    }

    @Test void fluidRequirement_roundtrip_with_output_io() {
        Fluids.WATER.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        var requirement = new FluidRequirement(RecipeModifier.IOType.OUTPUT, null, 0, new FluidStack(Fluids.WATER, 1000));

        var json = MachineRequirement.CODEC.encodeStart(jsonOps(), requirement).getOrThrow();
        var back = MachineRequirement.CODEC.parse(jsonOps(), json).getOrThrow();

        assertThat(back).isInstanceOfSatisfying(FluidRequirement.class, decoded -> {
            assertThat(decoded.io()).isEqualTo(requirement.io());
            assertThat(decoded.amount()).isEqualTo(requirement.amount());
            assertThat(FluidStack.isSameFluidSameComponents(decoded.stack(), requirement.stack())).isTrue();
            assertThat(decoded.stack().getAmount()).isEqualTo(requirement.stack().getAmount());
            assertThat(decoded.tags()).isEqualTo(requirement.tags());
        });
    }

    @Test void energyRequirement_roundtrip() {
        var requirement = new EnergyRequirement(80);

        var json = MachineRequirement.CODEC.encodeStart(jsonOps(), requirement).getOrThrow();
        var back = MachineRequirement.CODEC.parse(jsonOps(), json).getOrThrow();

        assertThat(back).isEqualTo(requirement);
    }

    private static DynamicOps<JsonElement> jsonOps() {
        return RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }
}
