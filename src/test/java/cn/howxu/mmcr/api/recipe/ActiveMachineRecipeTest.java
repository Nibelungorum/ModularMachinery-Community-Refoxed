package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Persistence tests for active machine recipes.
 *
 * @author howxu <dev@howxu.cn>
 */
class ActiveMachineRecipeTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void serializing_an_enchanted_output_does_not_crash() {
        HolderLookup.Provider lookup = registryProvider();
        JsonObject root = new JsonObject();
        root.addProperty("id", "mmcr:enchanted_output_persistence");
        root.addProperty("recipe_pool", "mmcr:test_cube");
        root.addProperty("tick_time", 20);
        JsonObject requirement = new JsonObject();
        requirement.addProperty("type", "minecraft:item");
        requirement.addProperty("io", "output");
        JsonObject stack = new JsonObject();
        stack.addProperty("id", "minecraft:diamond_sword");
        JsonObject components = new JsonObject();
        JsonObject enchantments = new JsonObject();
        enchantments.addProperty("minecraft:sharpness", 1);
        components.add("minecraft:enchantments", enchantments);
        stack.add("components", components);
        requirement.add("stack", stack);
        root.add("requirements", new JsonArray());
        root.getAsJsonArray("requirements").add(requirement);
        MachineRecipe recipe = MachineRecipe.CODEC.codec().parse(
                RegistryOps.create(JsonOps.INSTANCE, lookup), root).getOrThrow();
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe);
        CompoundTag serialized = new CompoundTag();

        assertThatCode(() -> active.serialize(serialized, lookup)).doesNotThrowAnyException();
        assertThat(serialized.getBoolean("has_recipe_definition")).isTrue();
        assertThat(serialized.getInt("recipe_definition_version")).isEqualTo(3);
        ActiveMachineRecipe.LoadResult loaded = ActiveMachineRecipe.load(serialized, lookup);
        assertThat(loaded.successful()).isTrue();
        assertThat(loaded.recipe()).isNotNull();
        assertThat(loaded.recipe().getRecipe().id()).isEqualTo(recipe.id());
        assertThat(loaded.recipe().getRecipe().requirements()).hasSize(1);
    }

    @Test
    void rejects_a_previous_recipe_definition_version() {
        HolderLookup.Provider lookup = registryProvider();
        JsonObject root = new JsonObject();
        root.addProperty("id", "mmcr:legacy_recipe_definition");
        root.addProperty("recipe_pool", "mmcr:test_cube");
        root.addProperty("tick_time", 20);
        root.add("requirements", new JsonArray());
        MachineRecipe recipe = MachineRecipe.CODEC.codec().parse(
                RegistryOps.create(JsonOps.INSTANCE, lookup), root).getOrThrow();
        CompoundTag legacyData = new CompoundTag();
        new ActiveMachineRecipe(recipe).serialize(legacyData, lookup);
        legacyData.putInt("recipe_definition_version", 2);

        assertThat(ActiveMachineRecipe.load(legacyData, lookup).successful()).isFalse();
    }

    @Test
    void pool_scoped_load_does_not_resolve_a_same_id_recipe_from_another_pool() {
        ResourceLocation recipeId = MMCR.id("pool_scoped_active_recipe");
        ResourceLocation foreignPool = MMCR.id("foreign_active_pool");
        RuntimeTestFixtures.registerRecipePool(foreignPool);
        MachineRecipe foreign = new MachineRecipe(recipeId, foreignPool, 20, List.of(), List.of(),
                List.of(), 0, 1, false, false, false, Set.of());
        RecipeRegistry.replaceDynamic(Map.of(recipeId, foreign));
        HolderLookup.Provider registries = HolderLookup.Provider.create(Stream.empty());
        CompoundTag serialized = new CompoundTag();
        try {
            new ActiveMachineRecipe(foreign).serialize(serialized, registries);

            assertThat(ActiveMachineRecipe.loadForPool(serialized, registries,
                    MMCR.id("test_cube")).successful()).isFalse();
        } finally {
            RecipeRegistry.replaceDynamic(Map.of());
        }
    }

    @Test
    void pool_scoped_load_rejects_a_missing_recipe_id_without_throwing() {
        HolderLookup.Provider lookup = registryProvider();
        ResourceLocation registeredId = MMCR.id("pool_scoped_missing_id_candidate");
        MachineRecipe registered = new MachineRecipe(registeredId, MMCR.id("test_cube"), 20, List.of(), List.of(),
                List.of(), 0, 1, false, false, false, Set.of());
        RecipeRegistry.replaceDynamic(Map.of(registeredId, registered));
        CompoundTag serialized = new CompoundTag();

        try {
            ActiveMachineRecipe.LoadResult loaded = ActiveMachineRecipe.loadForPool(
                    serialized, lookup, MMCR.id("test_cube"));

            assertThat(loaded.successful()).isFalse();
        } finally {
            RecipeRegistry.replaceDynamic(Map.of());
        }
    }

    @Test
    void embedded_definition_requires_membership_in_the_current_pool_catalog() {
        HolderLookup.Provider lookup = registryProvider();
        ResourceLocation recipeId = MMCR.id("embedded_catalog_membership");
        ResourceLocation poolId = MMCR.id("embedded_catalog_pool");
        RuntimeTestFixtures.registerRecipePool(poolId);
        MachineRecipe recipe = new MachineRecipe(recipeId, poolId, 20, List.of(), List.of(),
                List.of(), 0, 1, false, false, false, Set.of());
        CompoundTag serialized = new CompoundTag();

        try {
            RecipeRegistry.replaceDynamic(Map.of(recipeId, recipe));
            new ActiveMachineRecipe(recipe).serialize(serialized, lookup);
            RecipeRegistry.replaceDynamic(Map.of());

            assertThat(ActiveMachineRecipe.loadForPool(serialized, lookup, poolId).successful()).isFalse();

            RecipeRegistry.replaceDynamic(Map.of(recipeId, recipe));

            assertThat(ActiveMachineRecipe.loadForPool(serialized, lookup, poolId).recipe()).isNotNull();
        } finally {
            RecipeRegistry.replaceDynamic(Map.of());
        }
    }

    private static HolderLookup.Provider registryProvider() {
        MappedRegistry<Enchantment> enchantments = new MappedRegistry<>(Registries.ENCHANTMENT, Lifecycle.stable());
        VanillaRegistries.createLookup().lookupOrThrow(Registries.ENCHANTMENT).listElements()
                .forEach(holder -> Registry.register(enchantments, holder.key().location(), holder.value()));
        enchantments.freeze();
        List<Registry<?>> registries = new ArrayList<>();
        RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
                .registries().forEach(entry -> registries.add(entry.value()));
        registries.add(enchantments);
        return new RegistryAccess.ImmutableRegistryAccess(registries);
    }
}
