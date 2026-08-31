package cn.howxu.mmcr.api.recipe;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of output types used for persistence and extension dispatch.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class OutputRegistry {
    private static final Map<Identifier, OutputType<?>> TYPES = new ConcurrentHashMap<>();
    private static final Map<String, OutputType<?>> SERIALIZED_TYPES = new ConcurrentHashMap<>();
    private static final Object MUTATION_LOCK = new Object();
    private static final List<Identifier> BUILT_IN_IDS = List.of(MachineOutput.ItemOutput.TYPE.id(), MachineOutput.FluidOutput.TYPE.id());

    private OutputRegistry() {
    }

    public static <O extends MachineOutput> void register(OutputType<O> type) {
        validate(type);
        if (isBuiltIn(type.id())) throw new IllegalArgumentException("built-in output type is reserved: " + type.id());
        registerBuiltIns();
        synchronized (MUTATION_LOCK) {
            if (TYPES.containsKey(type.id())) throw new IllegalArgumentException("Duplicate output type: " + type.id());
            if (SERIALIZED_TYPES.containsKey(type.serializedId())) {
                throw new IllegalArgumentException("Duplicate serialized output type: " + type.serializedId());
            }
            TYPES.put(type.id(), type);
            SERIALIZED_TYPES.put(type.serializedId(), type);
        }
    }

    public static OutputType<?> typeFor(Identifier id) {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        registerBuiltIns();
        return TYPES.get(id);
    }

    public static OutputType<?> canonicalType(OutputType<?> type) {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        OutputType<?> canonical = typeFor(type.id());
        return canonical == type ? canonical : null;
    }

    public static boolean isCanonical(MachineOutput output) {
        return output != null && output.outputType() != null && canonicalType(output.outputType()) != null;
    }

    public static MachineRequirement toRequirement(MachineOutput output, List<String> tags) {
        if (!isCanonical(output)) {
            throw new IllegalArgumentException("Output type is not registered canonically");
        }
        return toRequirement(canonicalType(output.outputType()), output, tags == null ? List.of() : List.copyOf(tags));
    }

    /** Returns an execution requirement when the registered output type supports one. */
    public static MachineRequirement tryToRequirement(MachineOutput output, List<String> tags) {
        try {
            return toRequirement(output, tags);
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    /** Converts a requirement to an output through the registered output type that owns it. */
    public static MachineOutput fromRequirement(MachineRequirement requirement) {
        if (requirement == null) return null;
        registerBuiltIns();
        for (OutputType<?> type : TYPES.values()) {
            MachineOutput output = type.fromRequirement(requirement);
            if (output == null) continue;
            if (output.outputType() != type) {
                throw new IllegalArgumentException("Output factory returned a different type: " + type.id());
            }
            return output;
        }
        return null;
    }

    public static List<ItemStack> itemStacks(List<MachineOutput> outputs) {
        return outputs.stream()
                .filter(MachineOutput.ItemOutput.class::isInstance)
                .map(MachineOutput.ItemOutput.class::cast)
                .map(output -> output.stack().copy())
                .toList();
    }

    public static List<FluidStack> fluidStacks(List<MachineOutput> outputs) {
        return outputs.stream()
                .filter(MachineOutput.FluidOutput.class::isInstance)
                .map(MachineOutput.FluidOutput.class::cast)
                .map(output -> output.stack().copy())
                .toList();
    }

    public static boolean matchesOutputRequirement(MachineRequirement requirement) {
        if (requirement == null) return false;
        registerBuiltIns();
        return TYPES.values().stream().anyMatch(type -> type.matchesRequirement(requirement));
    }

    public static boolean matchesOutputRequirement(MachineOutput output, MachineRequirement requirement) {
        if (output == null || requirement == null) return false;
        OutputType<?> type = canonicalType(output.outputType());
        if (type == null || !type.matchesRequirement(requirement)) return false;
        MachineOutput converted = type.fromRequirement(requirement);
        return converted != null && converted.outputType() == type && output.equals(converted);
    }

    public static void registerBuiltIns() {
        registerBuiltIn(MachineOutput.ItemOutput.TYPE);
        registerBuiltIn(MachineOutput.FluidOutput.TYPE);
    }

    public static TestScope openTestScope() {
        synchronized (MUTATION_LOCK) {
            clearCustomTypes();
        }
        return new TestScope();
    }

    static <T> DataResult<T> encode(MachineOutput output, DynamicOps<T> ops, T prefix) {
        OutputType<?> type = canonicalType(output.outputType());
        if (type == null) return DataResult.error(() -> "Output type is not registered canonically: " + output.outputType().id());
        return encode(type, output, ops, prefix);
    }

    static <T> DataResult<Pair<MachineOutput, T>> decode(DynamicOps<T> ops, T input) {
        return ops.get(input, "type").flatMap(ops::getStringValue).flatMap(serializedId -> {
            OutputType<?> type = typeForSerializedId(serializedId);
            return type == null ? DataResult.error(() -> "Unknown output type: " + serializedId) : decode(type, ops, input);
        }).map(output -> Pair.of(output, input));
    }

    private static OutputType<?> typeForSerializedId(String serializedId) {
        registerBuiltIns();
        return SERIALIZED_TYPES.get(serializedId);
    }

    private static void validate(OutputType<?> type) {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (type.id() == null) throw new IllegalArgumentException("type id must not be null");
        if (type.codec() == null) throw new IllegalArgumentException("type codec must not be null");
        if (type.serializedId() == null || type.serializedId().isBlank()) {
            throw new IllegalArgumentException("serialized type id must not be blank");
        }
    }

    private static void registerBuiltIn(OutputType<?> type) {
        synchronized (MUTATION_LOCK) {
            OutputType<?> existing = TYPES.putIfAbsent(type.id(), type);
            if (existing == null) {
                OutputType<?> serialized = SERIALIZED_TYPES.putIfAbsent(type.serializedId(), type);
                if (serialized != null && serialized != type) {
                    TYPES.remove(type.id(), type);
                    throw new IllegalStateException("Conflicting built-in output type: " + type.id());
                }
            } else if (existing != type) {
                throw new IllegalStateException("Conflicting built-in output type: " + type.id());
            }
        }
    }

    private static void clearCustomTypes() {
        TYPES.keySet().removeIf(id -> !isBuiltIn(id));
        SERIALIZED_TYPES.entrySet().removeIf(entry -> !isBuiltIn(entry.getValue().id()));
    }

    private static boolean isBuiltIn(Identifier id) {
        return BUILT_IN_IDS.contains(id);
    }

    @SuppressWarnings("unchecked")
    private static <O extends MachineOutput, T> DataResult<T> encode(OutputType<?> type, MachineOutput output,
                                                                       DynamicOps<T> ops, T prefix) {
        return ((OutputType<O>) type).codec().codec().encodeStart(ops, (O) output)
                .flatMap(encoded -> ops.mergeToMap(prefix, encoded));
    }

    @SuppressWarnings("unchecked")
    private static <O extends MachineOutput, T> DataResult<O> decode(OutputType<?> type, DynamicOps<T> ops, T input) {
        return ((OutputType<O>) type).codec().codec().parse(ops, input);
    }

    @SuppressWarnings("unchecked")
    private static <O extends MachineOutput> MachineRequirement toRequirement(OutputType<?> type, MachineOutput output,
                                                                               List<String> tags) {
        return ((OutputType<O>) type).toRequirement((O) output, tags);
    }

    public static final class TestScope implements AutoCloseable {
        private boolean closed;

        private TestScope() {
        }

        @Override
        public void close() {
            if (closed) return;
            synchronized (MUTATION_LOCK) {
                clearCustomTypes();
                closed = true;
            }
        }
    }
}
