package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.test.TestBootstrap;
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
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
        root.addProperty("machine", "mmcr:test_cube");
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
        ActiveMachineRecipe.LoadResult loaded = ActiveMachineRecipe.load(
                TagValueInput.create(ProblemReporter.DISCARDING, lookup, serialized.buildResult()));
        assertThat(loaded.successful()).isTrue();
        assertThat(loaded.recipe()).isNotNull();
        assertThat(loaded.recipe().getRecipe().id()).isEqualTo(recipe.id());
        assertThat(loaded.recipe().getRecipe().requirements()).hasSize(1);
    }

    @Test
    void rejects_a_legacy_recipe_definition_fingerprint() {
        HolderLookup.Provider lookup = registryProvider();
        JsonObject root = new JsonObject();
        root.addProperty("id", "mmcr:legacy_recipe_definition");
        root.addProperty("machine", "mmcr:test_cube");
        root.addProperty("tick_time", 20);
        root.add("requirements", new JsonArray());
        MachineRecipe recipe = MachineRecipe.CODEC.codec().parse(
                RegistryOps.create(JsonOps.INSTANCE, lookup), root).getOrThrow();
        TagValueOutput serialized = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, lookup);
        new ActiveMachineRecipe(recipe).serialize(serialized, lookup);
        var legacyData = serialized.buildResult();
        legacyData.putInt("recipe_definition_version", 1);

        assertThat(ActiveMachineRecipe.load(TagValueInput.create(ProblemReporter.DISCARDING, lookup,
                legacyData)).successful()).isFalse();
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
