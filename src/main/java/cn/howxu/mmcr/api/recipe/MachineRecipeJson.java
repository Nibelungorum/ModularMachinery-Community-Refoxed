package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Parses the shared data pack and KubeJS machine recipe JSON contract.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeJson {
    public static final Identifier TYPE = Identifier.fromNamespaceAndPath("mmcr", "machine_recipe");

    private MachineRecipeJson() {
    }

    public static MachineRecipe parse(Identifier id, JsonElement json, HolderLookup.Provider registries) {
        if (id == null) throw new IllegalArgumentException("Recipe id must not be null");
        if (json == null || !json.isJsonObject()) fail(id, "$", "recipe must be an object");
        var object = json.getAsJsonObject();
        requireType(id, object);

        Identifier machineId = parseIdentifier(id, object, "machine");
        if (MachineRegistry.getMachine(machineId) == null) fail(id, "machine", "unknown machine " + machineId);
        int tickTime = intField(id, object, "tick_time", true, 0);
        if (tickTime < 1) fail(id, "tick_time", "must be >= 1");

        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        List<MachineIngredient> inputs = parseList(id, object, "inputs", MachineIngredient.CODEC, ops);
        int energyPerTick = intField(id, object, "energy_per_tick", false, 0);
        if (energyPerTick < 0) fail(id, "energy_per_tick", "must be >= 0");
        if (energyPerTick > 0) {
            var withEnergy = new java.util.ArrayList<>(inputs);
            withEnergy.add(new MachineIngredient.EnergyIngredient(energyPerTick));
            inputs = List.copyOf(withEnergy);
        }

        List<net.minecraft.world.item.ItemStack> outputs = parseList(id, object, "outputs",
                net.minecraft.world.item.ItemStack.CODEC, ops);
        List<FluidStack> fluidOutputs = parseList(id, object, "fluid_outputs", FluidStack.CODEC, ops);
        List<RecipeModifier> modifiers = parseList(id, object, "modifiers", RecipeModifier.CODEC, ops);
        List<MachineRequirement> requirements = parseList(id, object, "requirements", MachineRequirement.CODEC, ops);
        if (!object.has("requirements")) requirements = List.of();
        List<LevelRequirement> levels = parseList(id, object, "level_requirements", LevelRequirement.CODEC, ops);
        Set<Identifier> hosts = Set.copyOf(parseList(id, object, "required_host_ids", Identifier.CODEC, ops));

        if (object.has("requirements")) {
            return new MachineRecipe(id, machineId, tickTime, inputs, outputs, modifiers,
                    intField(id, object, "priority", false, 0), intField(id, object, "max_threads", false, 1),
                    boolField(id, object, "cancelIfPerTickFails", false), fluidOutputs, requirements,
                    boolField(id, object, "parallelized", false), levels,
                    boolField(id, object, "allow_partial_outputs", false), hosts);
        }
        return new MachineRecipe(id, machineId, tickTime, inputs, outputs, modifiers,
                intField(id, object, "priority", false, 0), intField(id, object, "max_threads", false, 1),
                boolField(id, object, "cancelIfPerTickFails", false), fluidOutputs, Collections.emptyList(),
                boolField(id, object, "parallelized", false), levels,
                boolField(id, object, "allow_partial_outputs", false), hosts, true);
    }

    private static void requireType(Identifier id, JsonObject object) {
        if (!object.has("type") || !TYPE.toString().equals(object.get("type").getAsString())) {
            fail(id, "type", "expected " + TYPE);
        }
    }

    private static Identifier parseIdentifier(Identifier id, JsonObject object, String field) {
        if (!object.has(field)) fail(id, field, "is required");
        try {
            return Identifier.parse(object.get(field).getAsString());
        } catch (RuntimeException exception) {
            fail(id, field, "invalid identifier");
            throw exception;
        }
    }

    private static int intField(Identifier id, JsonObject object, String field, boolean required, int defaultValue) {
        if (!object.has(field)) {
            if (required) fail(id, field, "is required");
            return defaultValue;
        }
        try {
            return object.get(field).getAsInt();
        } catch (RuntimeException exception) {
            fail(id, field, "must be an integer");
            throw exception;
        }
    }

    private static boolean boolField(Identifier id, JsonObject object, String field, boolean defaultValue) {
        if (!object.has(field)) return defaultValue;
        try {
            return object.get(field).getAsBoolean();
        } catch (RuntimeException exception) {
            fail(id, field, "must be a boolean");
            throw exception;
        }
    }

    private static <T> List<T> parseList(Identifier id, JsonObject object, String field, Codec<T> codec,
                                        com.mojang.serialization.DynamicOps<JsonElement> ops) {
        if (!object.has(field)) return List.of();
        try {
            return codec.listOf().parse(ops, object.get(field)).getOrThrow(error ->
                    new RecipeJsonException(id, field, error));
        } catch (RecipeJsonException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RecipeJsonException(id, field, exception.getMessage(), exception);
        }
    }

    private static void fail(Identifier id, String path, String message) {
        throw new RecipeJsonException(id, path, message);
    }

    /** Structured error raised while decoding a machine recipe JSON document. */
    public static final class RecipeJsonException extends IllegalArgumentException {
        public RecipeJsonException(Identifier id, String path, String message) {
            super("Recipe " + id + " at " + path + ": " + message);
        }

        public RecipeJsonException(Identifier id, String path, String message, Throwable cause) {
            super("Recipe " + id + " at " + path + ": " + message, cause);
        }
    }
}
