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
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
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
        TagValueOutput serialized = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, lookup);

        TagValueOutput withoutRegistryContext = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, lookup);
        assertThatCode(() -> active.serialize(withoutRegistryContext)).doesNotThrowAnyException();
        assertThat(withoutRegistryContext.buildResult().getBooleanOr("has_recipe_definition", false)).isFalse();
        assertThatCode(() -> active.serialize(serialized, lookup)).doesNotThrowAnyException();
        assertThat(serialized.buildResult().getBooleanOr("has_recipe_definition", false)).isTrue();
        assertThat(serialized.buildResult().getIntOr("recipe_definition_version", -1)).isEqualTo(3);
        ActiveMachineRecipe.LoadResult loaded = ActiveMachineRecipe.load(
                TagValueInput.create(ProblemReporter.DISCARDING, lookup, serialized.buildResult()));
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
        TagValueOutput serialized = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, lookup);
        new ActiveMachineRecipe(recipe).serialize(serialized, lookup);
        var legacyData = serialized.buildResult();
        legacyData.putInt("recipe_definition_version", 2);

        assertThat(ActiveMachineRecipe.load(TagValueInput.create(ProblemReporter.DISCARDING, lookup,
                legacyData)).successful()).isFalse();
    }

    @Test
    void pool_scoped_load_does_not_resolve_a_same_id_recipe_from_another_pool() {
        ResourceLocation recipeId = MMCR.id("pool_scoped_active_recipe");
        ResourceLocation foreignPool = MMCR.id("foreign_active_pool");
        RuntimeTestFixtures.registerRecipePool(foreignPool);
        MachineRecipe foreign = new MachineRecipe(recipeId, foreignPool, 20, List.of(), List.of(),
                List.of(), 0, 1, false, false, false, Set.of());
        RecipeRegistry.replaceDynamic(Map.of(recipeId, foreign));
        TagValueOutput serialized = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        try {
            new ActiveMachineRecipe(foreign).serialize(serialized);

            assertThat(ActiveMachineRecipe.loadForPool(TagValueInput.create(ProblemReporter.DISCARDING,
                    HolderLookup.Provider.create(Stream.empty()), serialized.buildResult()),
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
        TagValueOutput serialized = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, lookup);

        try {
            ActiveMachineRecipe.LoadResult loaded = ActiveMachineRecipe.loadForPool(
                    TagValueInput.create(ProblemReporter.DISCARDING, lookup, serialized.buildResult()),
                    MMCR.id("test_cube"));

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
        TagValueOutput serialized = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, lookup);

        try {
            RecipeRegistry.replaceDynamic(Map.of(recipeId, recipe));
            new ActiveMachineRecipe(recipe).serialize(serialized, lookup);
            RecipeRegistry.replaceDynamic(Map.of());

            assertThat(ActiveMachineRecipe.loadForPool(TagValueInput.create(ProblemReporter.DISCARDING, lookup,
                    serialized.buildResult()), poolId).successful()).isFalse();

            RecipeRegistry.replaceDynamic(Map.of(recipeId, recipe));

            assertThat(ActiveMachineRecipe.loadForPool(TagValueInput.create(ProblemReporter.DISCARDING, lookup,
                    serialized.buildResult()), poolId).recipe()).isNotNull();
        } finally {
            RecipeRegistry.replaceDynamic(Map.of());
        }
    }

    private static HolderLookup.Provider registryProvider() {
        MappedRegistry<Enchantment> enchantments = new MappedRegistry<>(Registries.ENCHANTMENT, Lifecycle.stable());
        VanillaRegistries.createLookup().lookupOrThrow(Registries.ENCHANTMENT).listElements()
                .forEach(holder -> Registry.register(enchantments, holder.key().identifier(), holder.value()));
        enchantments.freeze();
        List<Registry<?>> registries = new ArrayList<>();
        RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
                .registries().forEach(entry -> registries.add(entry.value()));
        registries.add(enchantments);
        return new RegistryAccess.ImmutableRegistryAccess(registries);
    }
}
