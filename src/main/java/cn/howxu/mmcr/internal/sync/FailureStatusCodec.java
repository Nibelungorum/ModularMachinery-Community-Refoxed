package cn.howxu.mmcr.internal.sync;

import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.capability.status.FailureReasonRegistry;
import cn.howxu.mmcr.api.capability.status.FailureTrace;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.config.CommonConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Encodes the complete execution failure status for runtime persistence and synchronization.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class FailureStatusCodec {
    public static final int MAX_TRACE_FRAMES = 16;
    public static final int MAX_DETAILS = 64;
    public static final int MAX_STRING_LENGTH = 256;

    private FailureStatusCodec() {
    }

    public static void write(CompoundTag output, @Nullable ExecutionStatus status) {
        Objects.requireNonNull(output, "output");
        output.putBoolean("present", status != null);
        if (status == null) return;

        validateStatus(status);
        putResourceLocation(output, "id", status.id());
        output.putInt("severity", status.severity().ordinal());
        putResourceLocation(output, "source", status.source());

        CompoundTag occurrenceOutput = new CompoundTag();
        output.put("occurrence", occurrenceOutput);
        FailureOccurrence occurrence = status.failure();
        occurrenceOutput.putBoolean("present", occurrence != null);
        if (occurrence == null) return;

        FailureReason reason = occurrence.reason();
        occurrenceOutput.putBoolean("has_reason", reason != null);
        if (reason != null) putResourceLocation(occurrenceOutput, "reason_id", reason.id());

        List<FailureTrace.Frame> frames = occurrence.trace().frames();
        checkCount(frames.size(), MAX_TRACE_FRAMES, "trace frame");
        ListTag trace = new ListTag();
        occurrenceOutput.put("trace", trace);
        for (FailureTrace.Frame frameValue : frames) {
            if (frameValue == null) throw new IllegalArgumentException("trace frame must not be null");
            CompoundTag frame = new CompoundTag();
            trace.add(frame);
            putResourceLocation(frame, "source", frameValue.source());
            frame.putInt("phase", frameValue.phase().ordinal());
            frame.putBoolean("has_recipe", frameValue.recipeId() != null);
            if (frameValue.recipeId() != null) putResourceLocation(frame, "recipe_id", frameValue.recipeId());
            frame.putBoolean("has_requirement", frameValue.requirementIndex() != null);
            if (frameValue.requirementIndex() != null) frame.putInt("requirement_index", frameValue.requirementIndex());
        }

        Map<String, String> details = occurrence.details();
        checkCount(details.size(), maxDetails(), "failure detail");
        ListTag detailList = new ListTag();
        occurrenceOutput.put("details", detailList);
        for (Map.Entry<String, String> entry : details.entrySet()) {
            CompoundTag detail = new CompoundTag();
            detailList.add(detail);
            putString(detail, "key", entry.getKey());
            putString(detail, "value", entry.getValue());
        }
    }

    public static @Nullable ExecutionStatus read(CompoundTag input) {
        Objects.requireNonNull(input, "input");
        if (!input.getBoolean("present")) return null;

        ResourceLocation id = getResourceLocation(input, "id");
        StatusSeverity severity = getEnum(StatusSeverity.values(),
                input.contains("severity") ? input.getInt("severity") : -1, "failure severity");
        ResourceLocation source = getResourceLocation(input, "source");
        CompoundTag occurrenceInput = input.getCompound("occurrence");
        if (!occurrenceInput.getBoolean("present")) {
            return new ExecutionStatus(id, severity, source, (FailureOccurrence) null);
        }

        ResourceLocation reasonId = occurrenceInput.getBoolean("has_reason")
                ? getResourceLocation(occurrenceInput, "reason_id") : null;
        FailureReason reason = reasonId == null ? null : resolveReason(reasonId);

        ListTag traceInputs = occurrenceInput.getList("trace", Tag.TAG_COMPOUND);
        int traceCount = count(traceInputs, MAX_TRACE_FRAMES, "trace frame");
        List<FailureTrace.Frame> frames = new ArrayList<>(traceCount);
        for (int index = 0; index < traceInputs.size(); index++) {
            frames.add(readFrame(traceInputs.getCompound(index)));
        }

        ListTag detailInputs = occurrenceInput.getList("details", Tag.TAG_COMPOUND);
        int detailCount = count(detailInputs, maxDetails(), "failure detail");
        Map<String, String> details = new LinkedHashMap<>(detailCount);
        for (int index = 0; index < detailInputs.size(); index++) {
            CompoundTag detailInput = detailInputs.getCompound(index);
            String key = getString(detailInput, "key");
            String value = getString(detailInput, "value");
            details.put(key, value);
        }
        addUnknownReasonDetail(reasonId, details);

        return new ExecutionStatus(id, severity, source,
                new FailureOccurrence(reason, new FailureTrace(frames), details));
    }

    public static void write(RegistryFriendlyByteBuf buffer, @Nullable ExecutionStatus status) {
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeBoolean(status != null);
        if (status == null) return;

        validateStatus(status);
        writeResourceLocation(buffer, status.id(), "status id");
        buffer.writeVarInt(status.severity().ordinal());
        writeResourceLocation(buffer, status.source(), "status source");

        FailureOccurrence occurrence = status.failure();
        buffer.writeBoolean(occurrence != null);
        if (occurrence == null) return;

        FailureReason reason = occurrence.reason();
        buffer.writeBoolean(reason != null);
        if (reason != null) writeResourceLocation(buffer, reason.id(), "failure reason");

        List<FailureTrace.Frame> frames = occurrence.trace().frames();
        checkCount(frames.size(), MAX_TRACE_FRAMES, "trace frame");
        buffer.writeVarInt(frames.size());
        for (FailureTrace.Frame frame : frames) {
            if (frame == null) throw new IllegalArgumentException("trace frame must not be null");
            writeResourceLocation(buffer, frame.source(), "trace source");
            buffer.writeVarInt(frame.phase().ordinal());
            buffer.writeBoolean(frame.recipeId() != null);
            if (frame.recipeId() != null) writeResourceLocation(buffer, frame.recipeId(), "trace recipe");
            buffer.writeBoolean(frame.requirementIndex() != null);
            if (frame.requirementIndex() != null) buffer.writeVarInt(frame.requirementIndex());
        }

        Map<String, String> details = occurrence.details();
        checkCount(details.size(), maxDetails(), "failure detail");
        buffer.writeVarInt(details.size());
        for (Map.Entry<String, String> entry : details.entrySet()) {
            writeString(buffer, entry.getKey(), "failure detail key");
            writeString(buffer, entry.getValue(), "failure detail value");
        }
    }

    public static @Nullable ExecutionStatus read(RegistryFriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        try {
            if (!buffer.readBoolean()) return null;

            ResourceLocation id = readResourceLocation(buffer, "status id");
            StatusSeverity severity = readEnum(buffer, StatusSeverity.values(), "failure severity");
            ResourceLocation source = readResourceLocation(buffer, "status source");
            if (!buffer.readBoolean()) return new ExecutionStatus(id, severity, source, (FailureOccurrence) null);

            ResourceLocation reasonId = buffer.readBoolean() ? readResourceLocation(buffer, "failure reason") : null;
            FailureReason reason = reasonId == null ? null : resolveReason(reasonId);

            int traceCount = readCount(buffer, MAX_TRACE_FRAMES, "trace frame");
            List<FailureTrace.Frame> frames = new ArrayList<>(traceCount);
            for (int index = 0; index < traceCount; index++) {
                ResourceLocation frameSource = readResourceLocation(buffer, "trace source");
                FailurePhase phase = readEnum(buffer, FailurePhase.values(), "failure phase");
                ResourceLocation recipeId = buffer.readBoolean() ? readResourceLocation(buffer, "trace recipe") : null;
                Integer requirementIndex = buffer.readBoolean() ? buffer.readVarInt() : null;
                frames.add(new FailureTrace.Frame(frameSource, phase, recipeId, requirementIndex));
            }

            int detailCount = readCount(buffer, maxDetails(), "failure detail");
            Map<String, String> details = new LinkedHashMap<>(detailCount);
            for (int index = 0; index < detailCount; index++) {
                details.put(readString(buffer, "failure detail key"),
                        readString(buffer, "failure detail value"));
            }
            addUnknownReasonDetail(reasonId, details);
            return new ExecutionStatus(id, severity, source,
                    new FailureOccurrence(reason, new FailureTrace(frames), details));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid failure status", exception);
        }
    }

    private static FailureTrace.Frame readFrame(CompoundTag input) {
        ResourceLocation source = getResourceLocation(input, "source");
        FailurePhase phase = getEnum(FailurePhase.values(),
                input.contains("phase") ? input.getInt("phase") : -1, "failure phase");
        ResourceLocation recipeId = input.getBoolean("has_recipe")
                ? getResourceLocation(input, "recipe_id") : null;
        Integer requirementIndex = input.getBoolean("has_requirement")
                ? input.getInt("requirement_index") : null;
        return new FailureTrace.Frame(source, phase, recipeId, requirementIndex);
    }

    private static FailureReason resolveReason(ResourceLocation reasonId) {
        FailureReason resolved = FailureReasonRegistry.resolve(reasonId);
        return resolved == null ? BuiltinFailureReasons.UNKNOWN : resolved;
    }

    private static void addUnknownReasonDetail(@Nullable ResourceLocation reasonId, Map<String, String> details) {
        if (reasonId != null && FailureReasonRegistry.find(reasonId) == null) {
            details.putIfAbsent("raw_reason_id", reasonId.toString());
        }
    }

    private static void validateStatus(ExecutionStatus status) {
        Objects.requireNonNull(status.id(), "status id");
        Objects.requireNonNull(status.severity(), "failure severity");
        Objects.requireNonNull(status.source(), "status source");
        FailureOccurrence occurrence = status.failure();
        if (occurrence == null) return;
        Objects.requireNonNull(occurrence.trace(), "failure trace");
        Objects.requireNonNull(occurrence.details(), "failure details");
        if (occurrence.reason() != null) {
            Objects.requireNonNull(occurrence.reason().id(), "failure reason id");
        }
    }

    private static void putResourceLocation(CompoundTag output, String name, ResourceLocation value) {
        putString(output, name, value.toString());
    }

    private static ResourceLocation getResourceLocation(CompoundTag input, String name) {
        return parseResourceLocation(getString(input, name), name);
    }

    private static ResourceLocation parseResourceLocation(String value, String name) {
        try {
            return ResourceLocation.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid " + name + " identifier: " + value, exception);
        }
    }

    private static void putString(CompoundTag output, String name, String value) {
        checkString(value, name);
        output.putString(name, value);
    }

    private static String getString(CompoundTag input, String name) {
        String value = input.getString(name);
        checkString(value, name);
        return value;
    }

    private static void writeResourceLocation(RegistryFriendlyByteBuf buffer, ResourceLocation value, String name) {
        writeString(buffer, value.toString(), name);
    }

    private static ResourceLocation readResourceLocation(RegistryFriendlyByteBuf buffer, String name) {
        return parseResourceLocation(readString(buffer, name), name);
    }

    private static void writeString(RegistryFriendlyByteBuf buffer, String value, String name) {
        checkString(value, name);
        buffer.writeUtf(value, maxStringLength());
    }

    private static String readString(RegistryFriendlyByteBuf buffer, String name) {
        try {
            String value = buffer.readUtf(maxStringLength());
            checkString(value, name);
            return value;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid " + name, exception);
        }
    }

    private static void checkString(String value, String name) {
        if (value == null || value.length() > maxStringLength()) {
            throw new IllegalArgumentException("Invalid " + name + " length");
        }
    }

    private static void checkCount(int count, int maximum, String name) {
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid " + name + " count: " + count);
    }

    private static int count(ListTag values, int maximum, String name) {
        checkCount(values.size(), maximum, name);
        return values.size();
    }

    private static int readCount(RegistryFriendlyByteBuf buffer, int maximum, String name) {
        int count = buffer.readVarInt();
        checkCount(count, maximum, name);
        return count;
    }

    private static <T> T getEnum(T[] values, int ordinal, String name) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid " + name + ": " + ordinal);
        }
        return values[ordinal];
    }

    private static <T> T readEnum(RegistryFriendlyByteBuf buffer, T[] values, String name) {
        return getEnum(values, buffer.readVarInt(), name);
    }

    private static int maxDetails() {
        return CommonConfig.valueOrDefault(CommonConfig.FAILURE_MAX_DETAILS, MAX_DETAILS);
    }

    private static int maxStringLength() {
        return CommonConfig.valueOrDefault(CommonConfig.MAX_STRING_LENGTH, MAX_STRING_LENGTH);
    }
}
