package cn.howxu.mmcr.api.recipe;

import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import com.mojang.serialization.DynamicOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.math.BigDecimal;
import java.util.function.Predicate;

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
        return parse(id, json, registries, machineId -> MachineRegistry.getMachine(machineId) != null
                || MachineDefinitions.containsStatic(machineId));
    }

    public static MachineRecipe parse(Identifier id, JsonElement json, HolderLookup.Provider registries,
                                      Predicate<Identifier> machineExists) {
        if (id == null) throw new IllegalArgumentException("Recipe id must not be null");
        if (json == null || !json.isJsonObject()) fail(id, "$", "recipe must be an object", null);
        var object = json.getAsJsonObject();
        requireType(id, object);

        Identifier machineId = parseIdentifier(id, object, "machine");
        if (!machineExists.test(machineId)) fail(id, "machine", "unknown machine " + machineId, null);
        int tickTime = intField(id, object, "tick_time", true, 0);
        if (tickTime < 1) fail(id, "tick_time", "must be >= 1");

        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        List<MachineIngredient> inputs = parseList(id, object, "inputs", MachineIngredient.CODEC, ops);
        int energyPerTick = intField(id, object, "energy_per_tick", false, 0);
        if (energyPerTick < 0) fail(id, "energy_per_tick", "must be >= 0");
        if (energyPerTick > 0) {
            var withEnergy = new ArrayList<>(inputs);
            withEnergy.add(new MachineIngredient.EnergyIngredient(energyPerTick));
            inputs = List.copyOf(withEnergy);
        }

        List<ItemStack> outputs = parseList(id, object, "outputs",
                ItemStack.CODEC, ops);
        List<FluidStack> fluidOutputs = parseList(id, object, "fluid_outputs", FluidStack.CODEC, ops);
        List<RecipeModifier> modifiers = parseList(id, object, "modifiers", RecipeModifier.CODEC, ops);
        List<MachineRequirement> requirements = parseList(id, object, "requirements", MachineRequirement.CODEC, ops);
        if (!object.has("requirements")) requirements = List.of();
        List<LevelRequirement> levels = parseList(id, object, "level_requirements", LevelRequirement.CODEC, ops);
        Set<Identifier> hosts = new LinkedHashSet<>(parseList(id, object, "required_host_ids", Identifier.CODEC, ops));
        int maxThreads = intField(id, object, "max_threads", false, 1);
        if (maxThreads < 0) fail(id, "max_threads", "must be >= 0");

        if (object.has("requirements") && !requirements.isEmpty()) {
            return new MachineRecipe(id, machineId, tickTime, inputs, outputs, modifiers,
                    intField(id, object, "priority", false, 0), maxThreads,
                    boolField(id, object, "cancelIfPerTickFails", false), fluidOutputs, requirements,
                    boolField(id, object, "parallelized", false), levels,
                    boolField(id, object, "allow_partial_outputs", false), hosts, false);
        }
        return new MachineRecipe(id, machineId, tickTime, inputs, outputs, modifiers,
                intField(id, object, "priority", false, 0), maxThreads,
                boolField(id, object, "cancelIfPerTickFails", false), fluidOutputs, Collections.emptyList(),
                boolField(id, object, "parallelized", false), levels,
                boolField(id, object, "allow_partial_outputs", false), hosts, true);
    }

    public static MachineRecipe normalize(Identifier id, Identifier machineId, int tickTime,
                                          List<MachineIngredient> inputs, List<ItemStack> outputs,
                                          List<RecipeModifier> modifiers, int priority, int maxThreads,
                                          boolean cancelIfPerTickFails, List<FluidStack> fluidOutputs,
                                          List<MachineRequirement> requirements, boolean parallelized,
                                          List<LevelRequirement> levels, boolean allowPartialOutputs,
                                          Set<Identifier> hosts, boolean deriveRequirements,
                                          Predicate<Identifier> machineExists) {
        if (id == null) throw new IllegalArgumentException("Recipe id must not be null");
        if (machineId == null || !machineExists.test(machineId)) {
            throw new RecipeJsonException(id, "machine", "unknown machine " + machineId, null);
        }
        if (tickTime < 1) throw new RecipeJsonException(id, "tick_time", "must be >= 1", null);
        if (maxThreads < 0) throw new RecipeJsonException(id, "max_threads", "must be >= 0", null);
        return new MachineRecipe(id, machineId, tickTime, inputs, outputs, modifiers, priority, maxThreads,
                cancelIfPerTickFails, fluidOutputs, requirements, parallelized, levels, allowPartialOutputs,
                hosts, deriveRequirements);
    }

    private static void requireType(Identifier id, JsonObject object) {
        if (!object.has("type")) {
            fail(id, "type", "expected " + TYPE, null);
        }
        try {
            if (!object.get("type").isJsonPrimitive() || !object.get("type").getAsJsonPrimitive().isString()
                    || !TYPE.toString().equals(object.get("type").getAsString())) {
                fail(id, "type", "expected " + TYPE, null);
            }
        } catch (RuntimeException exception) {
            fail(id, "type", "must be a string", exception);
        }
    }

    private static Identifier parseIdentifier(Identifier id, JsonObject object, String field) {
        if (!object.has(field)) fail(id, field, "is required", null);
        try {
            if (!object.get(field).isJsonPrimitive() || !object.get(field).getAsJsonPrimitive().isString()) {
                fail(id, field, "must be a string", null);
            }
            return Identifier.parse(object.get(field).getAsString());
        } catch (RuntimeException exception) {
            fail(id, field, "invalid identifier", exception);
            throw exception;
        }
    }

    private static int intField(Identifier id, JsonObject object, String field, boolean required, int defaultValue) {
        if (!object.has(field)) {
            if (required) fail(id, field, "is required");
            return defaultValue;
        }
        try {
            if (!object.get(field).isJsonPrimitive() || !object.get(field).getAsJsonPrimitive().isNumber()) {
                fail(id, field, "must be an integer", null);
            }
            var number = object.get(field).getAsJsonPrimitive().getAsNumber();
            var exact = new BigDecimal(number.toString());
            if (exact.stripTrailingZeros().scale() > 0) {
                fail(id, field, "must be an integer", new IllegalArgumentException("fractional number"));
            }
            if (exact.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) < 0
                    || exact.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
                fail(id, field, "must fit in a Java int", new IllegalArgumentException("integer out of range"));
            }
            return exact.intValue();
        } catch (RuntimeException exception) {
            fail(id, field, "must be an integer", exception);
            throw exception;
        }
    }

    private static boolean boolField(Identifier id, JsonObject object, String field, boolean defaultValue) {
        if (!object.has(field)) return defaultValue;
        try {
            if (!object.get(field).isJsonPrimitive() || !object.get(field).getAsJsonPrimitive().isBoolean()) {
                fail(id, field, "must be a boolean", null);
            }
            return object.get(field).getAsBoolean();
        } catch (RuntimeException exception) {
            fail(id, field, "must be a boolean", exception);
            throw exception;
        }
    }

    private static <T> List<T> parseList(Identifier id, JsonObject object, String field, Codec<T> codec,
                                        DynamicOps<JsonElement> ops) {
        if (!object.has(field)) return List.of();
        try {
            if (!object.get(field).isJsonArray()) fail(id, field, "must be an array", new IllegalArgumentException("expected array"));
            var values = new ArrayList<T>();
            var array = object.getAsJsonArray(field);
            for (int index = 0; index < array.size(); index++) {
                int elementIndex = index;
                values.add(codec.parse(ops, array.get(index)).getOrThrow(error ->
                        new RecipeJsonException(id, field + "[" + elementIndex + "]" + errorPath(error), error, new IllegalArgumentException(error))));
            }
            return List.copyOf(values);
        } catch (RecipeJsonException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RecipeJsonException(id, field, exception.getMessage(), exception);
        }
    }

    private static void fail(Identifier id, String path, String message) {
        fail(id, path, message, null);
    }

    private static void fail(Identifier id, String path, String message, Throwable cause) {
        throw new RecipeJsonException(id, path, message, cause);
    }

    private static String errorPath(String error) {
        int separator = error.indexOf(": ");
        return separator < 0 ? "" : "." + error.substring(0, separator);
    }

    /** Structured error raised while decoding a machine recipe JSON document. */
    public static final class RecipeJsonException extends IllegalArgumentException {
        private final Identifier recipeId;
        private final String path;

        public RecipeJsonException(Identifier id, String path, String message) {
            this(id, path, message, null);
        }

        public RecipeJsonException(Identifier id, String path, String message, Throwable cause) {
            super("Recipe " + id + " at " + path + ": " + message, cause);
            this.recipeId = id;
            this.path = path;
        }

        public Identifier recipeId() {
            return recipeId;
        }

        public String path() {
            return path;
        }
    }
}
