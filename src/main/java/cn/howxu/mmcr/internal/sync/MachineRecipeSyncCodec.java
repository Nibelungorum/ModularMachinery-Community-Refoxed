package cn.howxu.mmcr.internal.sync;

import cn.howxu.mmcr.api.recipe.LevelRequirement;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.FluidRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import io.netty.buffer.Unpooled;
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
import java.util.Collections;

/**
 * Network codec for server-authored runtime machine recipes.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class MachineRecipeSyncCodec {

    private static final int MAX_REQUIREMENTS = 4096;
    private static final int MAX_OUTPUTS = 4096;
    private static final int MAX_MODIFIERS = 1024;
    private static final int MAX_LEVEL_REQUIREMENTS = 1024;
    private static final int MAX_REQUIRED_HOSTS = 1024;
    private static final int MAX_TAGS = 1024;
    private static final int MAX_STACK_COUNT = 65536;
    private static final int MAX_ITEM_COUNT = 1_000_000;
    private static final int MAX_FLUID_AMOUNT = 10_000_000;
    private static final int MAX_ENERGY_PER_TICK = 10_000_000;
    private static final int FORMAT_MARKER = -1;
    private static final int FORMAT_VERSION = 1;

    private MachineRecipeSyncCodec() {
    }

    public static void encode(RegistryFriendlyByteBuf buf, MachineRecipe value) {
        buf.writeVarInt(FORMAT_MARKER);
        buf.writeVarInt(FORMAT_VERSION);
        Identifier.STREAM_CODEC.encode(buf, value.id());
        Identifier.STREAM_CODEC.encode(buf, value.machineId());
        buf.writeVarInt(value.tickTime());
        writeRequirements(buf, value.requirements());
        writeOutputs(buf, value.machineOutputs());
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
        int start = buf.readerIndex();
        int marker = buf.readVarInt();
        if (marker != FORMAT_MARKER) {
            buf.readerIndex(start);
            return decodeLegacy(buf);
        }
        int version = buf.readVarInt();
        if (version != FORMAT_VERSION) throw new DecoderException("Unsupported machine recipe sync version: " + version);
        return decodeCurrent(buf);
    }

    private static MachineRecipe decodeCurrent(RegistryFriendlyByteBuf buf) {
        Identifier id = Identifier.STREAM_CODEC.decode(buf);
        Identifier machineId = Identifier.STREAM_CODEC.decode(buf);
        int tickTime = buf.readVarInt();
        List<MachineRequirement> requirements = readRequirements(buf);
        List<MachineOutput> outputs = readOutputs(buf);
        List<RecipeModifier> modifiers = readModifiers(buf);
        int priority = buf.readVarInt();
        int maxThreads = buf.readVarInt();
        boolean cancelIfPerTickFails = buf.readBoolean();
        boolean parallelized = buf.readBoolean();
        List<LevelRequirement> levels = readLevelRequirements(buf);
        boolean allowPartialOutputs = buf.readBoolean();
        Set<Identifier> hosts = readRequiredHosts(buf);
        MachineRecipe recipe = MachineRecipe.fromCanonical(id, machineId, tickTime, requirements, outputs, modifiers,
                priority, maxThreads, cancelIfPerTickFails, parallelized, levels, allowPartialOutputs, hosts);
        RecipeRegistry.validateClientSnapshot(java.util.Map.of(id, recipe));
        return recipe;
    }

    private static MachineRecipe decodeLegacy(RegistryFriendlyByteBuf buf) {
        Identifier id = Identifier.STREAM_CODEC.decode(buf);
        Identifier machineId = Identifier.STREAM_CODEC.decode(buf);
        int tickTime = buf.readVarInt();
        List<MachineRequirement> requirements = readLegacyRequirements(buf);
        List<RecipeModifier> modifiers = readModifiers(buf);
        int priority = buf.readVarInt();
        int maxThreads = buf.readVarInt();
        boolean cancelIfPerTickFails = buf.readBoolean();
        boolean parallelized = buf.readBoolean();
        List<LevelRequirement> levels = readLevelRequirements(buf);
        boolean allowPartialOutputs = buf.readBoolean();
        Set<Identifier> hosts = readRequiredHosts(buf);
        MachineRecipe recipe = new MachineRecipe(id, machineId, tickTime, List.of(), List.of(), modifiers, priority,
                maxThreads, cancelIfPerTickFails, List.of(), requirements, parallelized, levels,
                allowPartialOutputs, hosts);
        RecipeRegistry.validateClientSnapshot(java.util.Map.of(id, recipe));
        return recipe;
    }

    private static void writeRequirements(RegistryFriendlyByteBuf buf, List<MachineRequirement> values) {
        checkSize(values.size(), MAX_REQUIREMENTS, "requirement");
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
        RequirementType<?> type = RequirementHandlerRegistry.canonicalType(value.type());
        if (type == null || type != value.type() || RequirementHandlerRegistry.handlerFor(type) == null) {
            throw new IllegalArgumentException("Requirement type is not registered canonically: " + value.type().id());
        }
        writeTyped(buf, type.id(), type.syncCodec(), value, "requirement");
    }

    private static MachineRequirement readRequirement(RegistryFriendlyByteBuf buf) {
        Identifier typeId = Identifier.STREAM_CODEC.decode(buf);
        int payloadSize = readPayloadSize(buf, "requirement");
        RequirementType<?> type = RequirementHandlerRegistry.typeFor(typeId);
        if (type == null || RequirementHandlerRegistry.handlerFor(type) == null) {
            throw new DecoderException("Unknown machine requirement type: " + typeId);
        }
        MachineRequirement requirement = readTyped(buf, payloadSize, type.syncCodec(), "requirement");
        if (requirement == null || requirement.type() != type || requirement.io() == null) {
            throw new DecoderException("Decoded requirement does not match registered type: " + typeId);
        }
        return requirement;
    }

    private static List<MachineRequirement> readLegacyRequirements(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        checkSize(count, MAX_REQUIREMENTS, "requirement");
        List<MachineRequirement> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) values.add(readLegacyRequirement(buf));
        return List.copyOf(values);
    }

    private static MachineRequirement readLegacyRequirement(RegistryFriendlyByteBuf buf) {
        return switch (buf.readVarInt()) {
            case 0 -> {
                RecipeModifier.IOType io = buf.readEnum(RecipeModifier.IOType.class);
                List<String> tags = readStringList(buf, MAX_TAGS, "tag");
                if (io == RecipeModifier.IOType.INPUT) {
                    Ingredient ingredient = Ingredient.CODEC.parse(buf.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                            normalizeIngredient(readJson(buf))).getOrThrow(message -> new DecoderException(
                                    "Failed to decode legacy item ingredient: " + message));
                    int count = buf.readVarInt();
                    checkRange(count, 1, MAX_ITEM_COUNT, "item count");
                    DataComponentPredicateSet components = readJsonWithRegistryCodec(buf, DataComponentPredicateSet.CODEC);
                    yield new ItemRequirement(io, ingredient, count, ItemStack.EMPTY, 1F, tags, components, buf.readFloat());
                }
                ItemStack stack = readJsonWithRegistryCodec(buf, ItemStack.CODEC);
                checkStackCount(stack);
                yield new ItemRequirement(io, null, 0, stack, buf.readFloat(), tags);
            }
            case 1 -> {
                RecipeModifier.IOType io = buf.readEnum(RecipeModifier.IOType.class);
                List<String> tags = readStringList(buf, MAX_TAGS, "tag");
                if (io == RecipeModifier.IOType.INPUT) {
                    FluidIngredient ingredient = readJsonWithRegistryCodec(buf, FluidIngredient.CODEC);
                    int amount = buf.readVarInt();
                    checkRange(amount, 1, MAX_FLUID_AMOUNT, "fluid amount");
                    yield new FluidRequirement(io, ingredient, amount, FluidStack.EMPTY, tags);
                }
                FluidStack stack = readJsonWithRegistryCodec(buf, FluidStack.CODEC);
                checkFluidAmount(stack);
                yield new FluidRequirement(io, null, 0, stack, buf.readFloat(), tags);
            }
            case 2 -> {
                RecipeModifier.IOType io = buf.readEnum(RecipeModifier.IOType.class);
                List<String> tags = readStringList(buf, MAX_TAGS, "tag");
                int fePerTick = buf.readVarInt();
                checkRange(fePerTick, 1, MAX_ENERGY_PER_TICK, "energy rate");
                yield new EnergyRequirement(io, fePerTick, tags);
            }
            case 3 -> new SmartInterfaceRequirement(buf.readEnum(RecipeModifier.IOType.class),
                    ByteBufCodecs.STRING_UTF8.decode(buf), buf.readFloat(), buf.readFloat());
            default -> throw new DecoderException("Unknown legacy machine requirement kind");
        };
    }

    private static void writeOutputs(RegistryFriendlyByteBuf buf, List<MachineOutput> values) {
        checkSize(values.size(), MAX_OUTPUTS, "output");
        buf.writeVarInt(values.size());
        for (MachineOutput value : values) writeOutput(buf, value);
    }

    private static List<MachineOutput> readOutputs(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        checkSize(count, MAX_OUTPUTS, "output");
        List<MachineOutput> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) values.add(readOutput(buf));
        return List.copyOf(values);
    }

    private static void writeOutput(RegistryFriendlyByteBuf buf, MachineOutput value) {
        OutputType<?> type = OutputRegistry.canonicalType(value.outputType());
        if (type == null) {
            throw new IllegalArgumentException("Output type is not registered canonically: " + value.outputType().id());
        }
        writeTyped(buf, type.id(), type.syncCodec(), value, "output");
    }

    private static MachineOutput readOutput(RegistryFriendlyByteBuf buf) {
        Identifier typeId = Identifier.STREAM_CODEC.decode(buf);
        int payloadSize = readPayloadSize(buf, "output");
        OutputType<?> type = OutputRegistry.typeFor(typeId);
        if (type == null) throw new DecoderException("Unknown machine output type: " + typeId);
        MachineOutput output = readTyped(buf, payloadSize, type.syncCodec(), "output");
        if (output == null || output.outputType() != type) {
            throw new DecoderException("Decoded output does not match registered type: " + typeId);
        }
        return output;
    }

    private static void writeTyped(RegistryFriendlyByteBuf buf, Identifier typeId, RecipeSyncCodec<?> codec,
                                   Object value, String label) {
        writeTypedUnchecked(buf, typeId, codec, value, label);
    }

    @SuppressWarnings("unchecked")
    private static <T> void writeTypedUnchecked(RegistryFriendlyByteBuf buf, Identifier typeId, RecipeSyncCodec<?> codec,
                                                Object value, String label) {
        RecipeSyncCodec<T> typedCodec = (RecipeSyncCodec<T>) codec;
        RegistryFriendlyByteBuf payload = new RegistryFriendlyByteBuf(Unpooled.buffer(), buf.registryAccess());
        try {
            T typedValue = (T) value;
            typedCodec.validate(typedValue);
            typedCodec.encode(payload, typedValue);
            int size = payload.writerIndex();
            if (size < 0 || size > RecipeSyncCodec.DEFAULT_MAX_PAYLOAD_SIZE || size > typedCodec.maxPayloadSize()) {
                throw new IllegalArgumentException("Invalid " + label + " payload size: " + size);
            }
            Identifier.STREAM_CODEC.encode(buf, typeId);
            buf.writeVarInt(size);
            buf.writeBytes(payload, 0, size);
        } finally {
            payload.release();
        }
    }

    private static <T> T readTyped(RegistryFriendlyByteBuf buf, int payloadSize, RecipeSyncCodec<T> codec,
                                   String label) {
        checkPayloadSize(payloadSize, codec.maxPayloadSize(), label);
        RegistryFriendlyByteBuf payload = new RegistryFriendlyByteBuf(buf.readSlice(payloadSize), buf.registryAccess());
        try {
            T value = codec.decode(payload);
            codec.validate(value);
            if (payload.isReadable()) throw new IllegalArgumentException("Unread " + label + " payload bytes");
            return value;
        } catch (DecoderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DecoderException("Invalid " + label + " payload", exception);
        }
    }

    private static int readPayloadSize(RegistryFriendlyByteBuf buf, String label) {
        int size = buf.readVarInt();
        checkPayloadSize(size, RecipeSyncCodec.DEFAULT_MAX_PAYLOAD_SIZE, label);
        return size;
    }

    private static void checkPayloadSize(int size, int typeMaximum, String label) {
        if (size < 0 || size > RecipeSyncCodec.DEFAULT_MAX_PAYLOAD_SIZE || size > typeMaximum) {
            throw new DecoderException("Invalid " + label + " payload size: " + size);
        }
    }

    private static void writeModifiers(RegistryFriendlyByteBuf buf, List<RecipeModifier> values) {
        checkSize(values.size(), MAX_MODIFIERS, "modifier");
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
        checkSize(values.size(), MAX_LEVEL_REQUIREMENTS, "level requirement");
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
        checkSize(values.size(), MAX_REQUIRED_HOSTS, "required host");
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
        return Collections.unmodifiableSet(values);
    }

    private static void checkSize(int size, int max, String label) {
        if (size < 0 || size > max) throw new IllegalArgumentException("Invalid " + label + " count: " + size);
    }

    private static List<String> readStringList(RegistryFriendlyByteBuf buf, int max, String label) {
        int count = buf.readVarInt();
        checkSize(count, max, label);
        List<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) values.add(ByteBufCodecs.STRING_UTF8.decode(buf));
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
        return value;
    }

    private static void checkStackCount(ItemStack stack) {
        if (stack.getCount() <= 0 || stack.getCount() > MAX_STACK_COUNT) {
            throw new DecoderException("Invalid item stack count: " + stack.getCount());
        }
    }

    private static void checkFluidAmount(FluidStack stack) {
        if (stack.getAmount() <= 0 || stack.getAmount() > MAX_FLUID_AMOUNT) {
            throw new DecoderException("Invalid fluid amount: " + stack.getAmount());
        }
    }

    private static void checkRange(int value, int minimum, int maximum, String label) {
        if (value < minimum || value > maximum) {
            throw new DecoderException("Invalid " + label + ": " + value);
        }
    }

}
