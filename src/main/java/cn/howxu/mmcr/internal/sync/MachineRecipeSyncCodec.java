package cn.howxu.mmcr.internal.sync;

import cn.howxu.mmcr.api.recipe.LevelRequirement;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Network codec for server-authored runtime machine recipes.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeSyncCodec {

    private static final int MAX_REQUIREMENTS = 4096;
    private static final int MAX_MODIFIERS = 1024;
    private static final int MAX_LEVEL_REQUIREMENTS = 1024;
    private static final int MAX_REQUIRED_HOSTS = 1024;
    private static final int MAX_TAGS = 1024;

    private MachineRecipeSyncCodec() {
    }

    public static void encode(RegistryFriendlyByteBuf buf, MachineRecipe value) {
        Identifier.STREAM_CODEC.encode(buf, value.id());
        Identifier.STREAM_CODEC.encode(buf, value.machineId());
        buf.writeVarInt(value.tickTime());
        writeRequirements(buf, value.requirements());
        writeModifiers(buf, value.modifiers());
        buf.writeVarInt(value.priority());
        buf.writeVarInt(value.maxThreads());
        buf.writeBoolean(value.doesCancelRecipeOnPerTickFailure());
        buf.writeBoolean(value.isParallelized());
        writeLevelRequirements(buf, value.levelRequirements());
        buf.writeBoolean(value.allowPartialOutputs());
        writeRequiredHosts(buf, value.requiredHostIds());
    }

    public static MachineRecipe decode(RegistryFriendlyByteBuf buf) {
        Identifier id = Identifier.STREAM_CODEC.decode(buf);
        Identifier machineId = Identifier.STREAM_CODEC.decode(buf);
        int tickTime = buf.readVarInt();
        List<MachineRequirement> requirements = readRequirements(buf);
        List<RecipeModifier> modifiers = readModifiers(buf);
        int priority = buf.readVarInt();
        int maxThreads = buf.readVarInt();
        boolean cancelIfPerTickFails = buf.readBoolean();
        boolean parallelized = buf.readBoolean();
        List<LevelRequirement> levels = readLevelRequirements(buf);
        boolean allowPartialOutputs = buf.readBoolean();
        Set<Identifier> hosts = readRequiredHosts(buf);
        return new MachineRecipe(id, machineId, tickTime, List.of(), List.of(), modifiers, priority, maxThreads,
                cancelIfPerTickFails, List.of(), requirements, parallelized, levels, allowPartialOutputs, hosts);
    }

    private static void writeRequirements(RegistryFriendlyByteBuf buf, List<MachineRequirement> values) {
        buf.writeVarInt(values.size());
        for (MachineRequirement value : values) {
            writeRequirement(buf, value);
        }
    }

    private static List<MachineRequirement> readRequirements(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        checkSize(count, MAX_REQUIREMENTS, "requirement");
        List<MachineRequirement> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(readRequirement(buf));
        }
        return List.copyOf(values);
    }

    private static void writeRequirement(RegistryFriendlyByteBuf buf, MachineRequirement value) {
        switch (value) {
            case ItemRequirement item -> {
                buf.writeEnum(RequirementKind.ITEM);
                buf.writeEnum(item.io());
                writeStringList(buf, item.tags());
                if (item.io() == RecipeModifier.IOType.INPUT) {
                    writeJson(buf, Ingredient.CODEC.encodeStart(buf.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                            item.item()).getOrThrow());
                    buf.writeVarInt(item.count());
                    writeJsonWithRegistryCodec(buf, DataComponentPredicateSet.CODEC, item.components());
                    buf.writeFloat(item.consumeChance());
                } else {
                    writeJsonWithRegistryCodec(buf, ItemStack.CODEC, item.stack(buf.registryAccess().createSerializationContext(JsonOps.INSTANCE)));
                    buf.writeFloat(item.chance());
                }
            }
            case FluidRequirement fluid -> {
                buf.writeEnum(RequirementKind.FLUID);
                buf.writeEnum(fluid.io());
                writeStringList(buf, fluid.tags());
                if (fluid.io() == RecipeModifier.IOType.INPUT) {
                    writeJsonWithRegistryCodec(buf, FluidIngredient.CODEC, fluid.fluid());
                    buf.writeVarInt(fluid.amount());
                } else {
                    writeJsonWithRegistryCodec(buf, FluidStack.CODEC, fluid.stack());
                    buf.writeFloat(fluid.chance());
                }
            }
            case EnergyRequirement energy -> {
                buf.writeEnum(RequirementKind.ENERGY);
                buf.writeEnum(energy.io());
                writeStringList(buf, energy.tags());
                buf.writeVarInt(energy.fePerTick());
            }
            case SmartInterfaceRequirement smartInterface -> {
                buf.writeEnum(RequirementKind.SMART_INTERFACE);
                buf.writeEnum(smartInterface.io());
                ByteBufCodecs.STRING_UTF8.encode(buf, smartInterface.interfaceType());
                buf.writeFloat(smartInterface.minValue());
                buf.writeFloat(smartInterface.maxValue());
            }
        }
    }

    private static MachineRequirement readRequirement(RegistryFriendlyByteBuf buf) {
        return switch (buf.readEnum(RequirementKind.class)) {
            case ITEM -> {
                RecipeModifier.IOType io = buf.readEnum(RecipeModifier.IOType.class);
                List<String> tags = readStringList(buf, MAX_TAGS, "tag");
                if (io == RecipeModifier.IOType.INPUT) {
                    Ingredient ingredient = Ingredient.CODEC.parse(buf.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                            normalizeIngredient(readJson(buf))).getOrThrow();
                    int count = buf.readVarInt();
                    DataComponentPredicateSet components = readJsonWithRegistryCodec(buf, DataComponentPredicateSet.CODEC);
                    yield new ItemRequirement(io, ingredient, count, ItemStack.EMPTY, 1F, tags, components, buf.readFloat());
                }
                yield new ItemRequirement(io, null, 0, readJsonWithRegistryCodec(buf, ItemStack.CODEC), buf.readFloat(), tags);
            }
            case FLUID -> {
                RecipeModifier.IOType io = buf.readEnum(RecipeModifier.IOType.class);
                List<String> tags = readStringList(buf, MAX_TAGS, "tag");
                if (io == RecipeModifier.IOType.INPUT) {
                    FluidIngredient ingredient = readJsonWithRegistryCodec(buf, FluidIngredient.CODEC);
                    yield new FluidRequirement(io, ingredient, buf.readVarInt(), FluidStack.EMPTY, tags);
                }
                yield new FluidRequirement(io, null, 0, readJsonWithRegistryCodec(buf, FluidStack.CODEC), buf.readFloat(), tags);
            }
            case ENERGY -> {
                RecipeModifier.IOType io = buf.readEnum(RecipeModifier.IOType.class);
                List<String> tags = readStringList(buf, MAX_TAGS, "tag");
                yield new EnergyRequirement(io, buf.readVarInt(), tags);
            }
            case SMART_INTERFACE -> new SmartInterfaceRequirement(buf.readEnum(RecipeModifier.IOType.class),
                    ByteBufCodecs.STRING_UTF8.decode(buf), buf.readFloat(), buf.readFloat());
        };
    }

    private static void writeModifiers(RegistryFriendlyByteBuf buf, List<RecipeModifier> values) {
        buf.writeVarInt(values.size());
        for (RecipeModifier value : values) {
            writeJsonWithRegistryCodec(buf, RecipeModifier.CODEC, value);
        }
    }

    private static List<RecipeModifier> readModifiers(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        checkSize(count, MAX_MODIFIERS, "modifier");
        List<RecipeModifier> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(readJsonWithRegistryCodec(buf, RecipeModifier.CODEC));
        }
        return List.copyOf(values);
    }

    private static void writeLevelRequirements(RegistryFriendlyByteBuf buf, List<LevelRequirement> values) {
        buf.writeVarInt(values.size());
        for (LevelRequirement value : values) {
            writeJsonWithRegistryCodec(buf, LevelRequirement.CODEC, value);
        }
    }

    private static List<LevelRequirement> readLevelRequirements(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        checkSize(count, MAX_LEVEL_REQUIREMENTS, "level requirement");
        List<LevelRequirement> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(readJsonWithRegistryCodec(buf, LevelRequirement.CODEC));
        }
        return List.copyOf(values);
    }

    private static void writeRequiredHosts(RegistryFriendlyByteBuf buf, Set<Identifier> values) {
        buf.writeVarInt(values.size());
        for (Identifier value : values) {
            Identifier.STREAM_CODEC.encode(buf, value);
        }
    }

    private static Set<Identifier> readRequiredHosts(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        checkSize(count, MAX_REQUIRED_HOSTS, "required host");
        Set<Identifier> values = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            values.add(Identifier.STREAM_CODEC.decode(buf));
        }
        return java.util.Collections.unmodifiableSet(values);
    }

    private static void checkSize(int size, int max, String label) {
        if (size < 0 || size > max) throw new IllegalArgumentException("Invalid " + label + " count: " + size);
    }

    private static void writeStringList(RegistryFriendlyByteBuf buf, List<String> values) {
        buf.writeVarInt(values.size());
        for (String value : values) {
            ByteBufCodecs.STRING_UTF8.encode(buf, value);
        }
    }

    private static List<String> readStringList(RegistryFriendlyByteBuf buf, int max, String label) {
        int count = buf.readVarInt();
        checkSize(count, max, label);
        List<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(ByteBufCodecs.STRING_UTF8.decode(buf));
        }
        return List.copyOf(values);
    }

    private static void writeJson(RegistryFriendlyByteBuf buf, JsonElement value) {
        ByteBufCodecs.STRING_UTF8.encode(buf, value.toString());
    }

    private static JsonElement readJson(RegistryFriendlyByteBuf buf) {
        return JsonParser.parseString(ByteBufCodecs.STRING_UTF8.decode(buf));
    }

    private static <T> void writeJsonWithRegistryCodec(RegistryFriendlyByteBuf buf, Codec<T> codec, T value) {
        var result = codec.encodeStart(buf.registryAccess().createSerializationContext(JsonOps.INSTANCE), value);
        writeJson(buf, result.getOrThrow(message -> new EncoderException("Failed to encode: " + message + " " + value)));
    }

    private static <T> T readJsonWithRegistryCodec(RegistryFriendlyByteBuf buf, Codec<T> codec) {
        var result = codec.parse(buf.registryAccess().createSerializationContext(JsonOps.INSTANCE), readJson(buf));
        return result.getOrThrow(message -> new DecoderException("Failed to decode json: " + message));
    }

    private static JsonElement normalizeIngredient(JsonElement value) {
        if (!value.isJsonPrimitive()) return value;
        var array = new com.google.gson.JsonArray();
        array.add(value);
        return array;
    }

    private enum RequirementKind {
        ITEM,
        FLUID,
        ENERGY,
        SMART_INTERFACE
    }
}
