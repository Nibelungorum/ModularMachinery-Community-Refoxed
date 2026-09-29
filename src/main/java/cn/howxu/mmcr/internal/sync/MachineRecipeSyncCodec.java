package cn.howxu.mmcr.internal.sync;

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
import cn.howxu.mmcr.config.CommonConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import io.netty.buffer.Unpooled;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
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
    private static final int MAX_REQUIRED_HOSTS = 1024;
    private static final int FORMAT_MARKER = -1;
    private static final int FORMAT_VERSION = 3;

    private MachineRecipeSyncCodec() {
    }

    public static void encode(RegistryFriendlyByteBuf buf, MachineRecipe value) {
        buf.writeVarInt(FORMAT_MARKER);
        buf.writeVarInt(FORMAT_VERSION);
        ResourceLocation.STREAM_CODEC.encode(buf, value.id());
        ResourceLocation.STREAM_CODEC.encode(buf, value.recipePoolId());
        buf.writeVarInt(value.tickTime());
        writeRequirements(buf, value.requirements());
        writeOutputs(buf, value.outputsWithoutDerivedRequirements());
        writeModifiers(buf, value.modifiers());
        buf.writeVarInt(value.priority());
        buf.writeVarInt(value.maxThreads());
        buf.writeBoolean(value.doesCancelRecipeOnPerTickFailure());
        buf.writeBoolean(value.isParallelized());
        buf.writeBoolean(value.allowPartialOutputs());
        writeRequiredHosts(buf, value.requiredHostIds());
    }

    public static MachineRecipe decode(RegistryFriendlyByteBuf buf) {
        int marker = buf.readVarInt();
        if (marker != FORMAT_MARKER) {
            throw new DecoderException("Unsupported machine recipe sync format");
        }
        int version = buf.readVarInt();
        if (version != FORMAT_VERSION) throw new DecoderException("Unsupported machine recipe sync version: " + version);
        return decodeCurrent(buf);
    }

    private static MachineRecipe decodeCurrent(RegistryFriendlyByteBuf buf) {
        ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buf);
        ResourceLocation recipePoolId = ResourceLocation.STREAM_CODEC.decode(buf);
        int tickTime = buf.readVarInt();
        List<MachineRequirement> requirements = readRequirements(buf);
        List<MachineOutput> outputs = readOutputs(buf);
        List<RecipeModifier> modifiers = readModifiers(buf);
        int priority = buf.readVarInt();
        int maxThreads = buf.readVarInt();
        boolean cancelIfPerTickFails = buf.readBoolean();
        boolean parallelized = buf.readBoolean();
        boolean allowPartialOutputs = buf.readBoolean();
        Set<ResourceLocation> hosts = readRequiredHosts(buf);
        MachineRecipe recipe = MachineRecipe.fromCanonical(id, recipePoolId, tickTime, requirements, outputs, modifiers,
                priority, maxThreads, cancelIfPerTickFails, parallelized, allowPartialOutputs, hosts);
        RecipeRegistry.validateClientSnapshot(Map.of(id, recipe));
        return recipe;
    }

    private static void writeRequirements(RegistryFriendlyByteBuf buf, List<MachineRequirement> values) {
        checkSize(values.size(), maxRequirements(), "requirement");
        buf.writeVarInt(values.size());
        for (MachineRequirement value : values) {
            writeRequirement(buf, value);
        }
    }

    private static List<MachineRequirement> readRequirements(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        checkSize(count, maxRequirements(), "requirement");
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
        ResourceLocation typeId = ResourceLocation.STREAM_CODEC.decode(buf);
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

    private static void writeOutputs(RegistryFriendlyByteBuf buf, List<MachineOutput> values) {
        checkSize(values.size(), maxOutputs(), "output");
        buf.writeVarInt(values.size());
        for (MachineOutput value : values) writeOutput(buf, value);
    }

    private static List<MachineOutput> readOutputs(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        checkSize(count, maxOutputs(), "output");
        List<MachineOutput> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) values.add(readOutput(buf));
        return List.copyOf(values);
    }

    public static void writeOutput(RegistryFriendlyByteBuf buf, MachineOutput value) {
        OutputType<?> type = OutputRegistry.canonicalType(value.outputType());
        if (type == null) {
            throw new IllegalArgumentException("Output type is not registered canonically: " + value.outputType().id());
        }
        writeTyped(buf, type.id(), type.syncCodec(), value, "output");
    }

    public static MachineOutput readOutput(RegistryFriendlyByteBuf buf) {
        ResourceLocation typeId = ResourceLocation.STREAM_CODEC.decode(buf);
        int payloadSize = readPayloadSize(buf, "output");
        OutputType<?> type = OutputRegistry.typeFor(typeId);
        if (type == null) throw new DecoderException("Unknown machine output type: " + typeId);
        MachineOutput output = readTyped(buf, payloadSize, type.syncCodec(), "output");
        if (output == null || output.outputType() != type) {
            throw new DecoderException("Decoded output does not match registered type: " + typeId);
        }
        return output;
    }

    private static void writeTyped(RegistryFriendlyByteBuf buf, ResourceLocation typeId, RecipeSyncCodec<?> codec,
                                   Object value, String label) {
        writeTypedUnchecked(buf, typeId, codec, value, label);
    }

    @SuppressWarnings("unchecked")
    private static <T> void writeTypedUnchecked(RegistryFriendlyByteBuf buf, ResourceLocation typeId, RecipeSyncCodec<?> codec,
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
            ResourceLocation.STREAM_CODEC.encode(buf, typeId);
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
        checkSize(values.size(), maxModifiers(), "modifier");
        buf.writeVarInt(values.size());
        for (RecipeModifier value : values) {
            writeJsonWithRegistryCodec(buf, RecipeModifier.CODEC, value);
        }
    }

    private static List<RecipeModifier> readModifiers(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        checkSize(count, maxModifiers(), "modifier");
        List<RecipeModifier> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(readJsonWithRegistryCodec(buf, RecipeModifier.CODEC));
        }
        return List.copyOf(values);
    }

    private static void writeRequiredHosts(RegistryFriendlyByteBuf buf, Set<ResourceLocation> values) {
        checkSize(values.size(), maxRequiredHosts(), "required host");
        buf.writeVarInt(values.size());
        for (ResourceLocation value : values) {
            ResourceLocation.STREAM_CODEC.encode(buf, value);
        }
    }

    private static Set<ResourceLocation> readRequiredHosts(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        checkSize(count, maxRequiredHosts(), "required host");
        Set<ResourceLocation> values = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            values.add(ResourceLocation.STREAM_CODEC.decode(buf));
        }
        return Collections.unmodifiableSet(values);
    }

    private static void checkSize(int size, int max, String label) {
        if (size < 0 || size > max) throw new IllegalArgumentException("Invalid " + label + " count: " + size);
    }

    private static int maxRequirements() {
        return CommonConfig.valueOrDefault(CommonConfig.RECIPE_SYNC_MAX_REQUIREMENTS, MAX_REQUIREMENTS);
    }

    private static int maxOutputs() {
        return CommonConfig.valueOrDefault(CommonConfig.RECIPE_SYNC_MAX_OUTPUTS, MAX_OUTPUTS);
    }

    private static int maxModifiers() {
        return CommonConfig.valueOrDefault(CommonConfig.RECIPE_SYNC_MAX_MODIFIERS, MAX_MODIFIERS);
    }

    private static int maxRequiredHosts() {
        return CommonConfig.valueOrDefault(CommonConfig.RECIPE_SYNC_MAX_REQUIRED_HOSTS, MAX_REQUIRED_HOSTS);
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
        if (stack.getCount() <= 0) {
            throw new DecoderException("Invalid item stack count: " + stack.getCount());
        }
    }

    private static void checkFluidAmount(FluidStack stack) {
        if (stack.getAmount() <= 0) {
            throw new DecoderException("Invalid fluid amount: " + stack.getAmount());
        }
    }

    private static void checkRange(int value, int minimum, int maximum, String label) {
        if (value < minimum || value > maximum) {
            throw new DecoderException("Invalid " + label + ": " + value);
        }
    }

}
