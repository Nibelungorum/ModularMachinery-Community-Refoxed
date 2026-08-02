package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
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

    private static DynamicOps<JsonElement> jsonOps() {
        return RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }
}
