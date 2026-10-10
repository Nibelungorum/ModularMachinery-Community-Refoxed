package cn.howxu.mmcr.internal.runtime.ui;

import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot;
import cn.howxu.mmcr.api.data.view.DataValue;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.definition.TickBehavior;
import cn.howxu.mmcr.api.presentation.ComponentSnapshots;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.internal.runtime.ControllerRecipePresentation;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextSnapshot;
import cn.howxu.mmcr.internal.runtime.ControllerSyncRuntime;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Reconstructible owned values shared by the UI API and its wire codec.
 * @author howxu <dev@howxu.cn>
 */
public record ControllerUiSnapshotData(UUID sessionId, long revision, boolean ready,
                                       ResourceKey<Level> dimension, BlockPos controllerPos,
                                       HeaderData header, List<LaneData> laneData)
        implements ControllerUiSnapshot {
    /** The empty lane ID denotes controller-global text, as in the existing text payload. */
    public static final String GLOBAL_TEXT_LANE = "";

    public ControllerUiSnapshotData {
        Objects.requireNonNull(sessionId, "sessionId");
        if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
        Objects.requireNonNull(dimension, "dimension");
        controllerPos = Objects.requireNonNull(controllerPos, "controllerPos").immutable();
        Objects.requireNonNull(header, "header");
        laneData = List.copyOf(laneData);
        Set<String> ids = new HashSet<>();
        Set<Integer> indexes = new HashSet<>();
        for (LaneData lane : laneData) {
            if (!ids.add(lane.id()) || !indexes.add(lane.index())) {
                throw new IllegalArgumentException("Duplicate lane identity");
            }
        }
        if (header.kind() == Kind.TICK && !laneData.isEmpty()) {
            throw new IllegalArgumentException("Tick controllers do not have recipe lanes");
        }
    }

    /** One-shot capture. Repeated server captures should use a session-owned {@link CaptureCache}. */
    public static ControllerUiSnapshotData capture(UUID sessionId, long revision, ResourceKey<Level> dimension,
                                                   BlockPos controllerPos, ControllerRuntimeSnapshot runtime,
                                                   @Nullable Identifier recipePoolId, boolean hasDataStorage,
                                                   List<Identifier> recipePools,
                                                   Map<String, ControllerScreenTextSnapshot> laneText) {
        return new CaptureCache().capture(sessionId, revision, dimension, controllerPos, runtime,
                recipePoolId, hasDataStorage, recipePools, laneText);
    }

    /** Structural equality includes all presentation fields except revision and lane tick. */
    public boolean sameShape(ControllerUiSnapshotData other) {
        if (other == null || !sessionId.equals(other.sessionId) || ready != other.ready
                || !dimension.equals(other.dimension) || !controllerPos.equals(other.controllerPos)
                || !header.equals(other.header) || laneData.size() != other.laneData.size()) return false;
        for (int i = 0; i < laneData.size(); i++) {
            if (!laneData.get(i).sameShape(other.laneData.get(i))) return false;
        }
        return true;
    }

    /**
     * Atomically merges a matching delta. Invalid/stale deltas throw; the caller must request a full baseline.
     * The envelope's fullBaselineRevision must be checked by the session before calling this method.
     */
    public ControllerUiSnapshotData withProgress(long revision, List<LaneProgress> progress) {
        if (revision <= this.revision) throw new IllegalArgumentException("Progress revision must advance");
        Map<String, LaneProgress> updates = new LinkedHashMap<>();
        for (LaneProgress value : progress) {
            if (updates.put(value.laneId(), value) != null) throw new IllegalArgumentException("Duplicate progress lane");
        }
        List<LaneData> lanes = new ArrayList<>(laneData.size());
        for (LaneData lane : laneData) {
            LaneProgress value = updates.remove(lane.id());
            if (value == null) {
                lanes.add(lane);
            } else {
                if (!Objects.equals(value.recipeId(), lane.recipeId().orElse(null))
                        || value.totalTick() != lane.totalTick() || value.parallelism() != lane.parallelism()) {
                    throw new IllegalArgumentException("Progress does not match the full lane baseline");
                }
                lanes.add(lane.withTick(value.tick()));
            }
        }
        if (!updates.isEmpty()) throw new IllegalArgumentException("Unknown progress lane");
        return new ControllerUiSnapshotData(sessionId, revision, ready, dimension, controllerPos, header, lanes);
    }

    public Identifier machineId() { return header.machineId(); }
    public Kind kind() { return header.kind(); }
    public Role role() { return header.role(); }
    public Component machineName() { return header.machineName(); }
    public boolean formed() { return header.formed(); }
    public boolean active() { return header.active(); }
    public boolean redstonePaused() { return header.redstonePaused(); }
    public int installedModuleCount() { return header.installedModuleCount(); }
    public Optional<Identifier> connectedHostId() { return Optional.ofNullable(header.connectedHost()); }
    public int matchedStage() { return header.matchedStage(); }
    public int stageCount() { return header.stageCount(); }
    public List<Identifier> foundLevelIds() { return header.foundLevelIds(); }
    public int parallelSlots() { return header.parallelSlots(); }
    public long maxParallelism() { return header.maxParallelism(); }
    public int threadLimit() { return header.threadLimit(); }
    public int activeThreadCount() { return header.activeThreadCount(); }
    public List<Identifier> recipePoolIds() { return header.recipePoolIds(); }
    public Optional<Identifier> currentRecipePoolId() { return Optional.ofNullable(header.currentRecipePool()); }
    public Optional<ExecutionStatus> failure() { return Optional.ofNullable(header.runtimeFailure()); }
    public boolean hasDataStorage() { return header.hasDataStorage(); }
    public Map<String, DataValue> dataStorageValues() { return header.dataStorageValues(); }
    public List<TextLine> lines() { return copyLines(header.textLines()); }
    public List<Lane> lanes() { return laneData.stream().<Lane>map(value -> value).toList(); }

    /** Owned controller fields, all of which belong to the full snapshot shape.
     * @author howxu <dev@howxu.cn> */
    public record HeaderData(Identifier machineId, Kind kind, Role role, Component machineName,
                             boolean formed, boolean active, boolean redstonePaused, int installedModuleCount,
                             @Nullable Identifier connectedHost, int matchedStage, int stageCount,
                             List<Identifier> foundLevelIds, int parallelSlots, long maxParallelism,
                             int threadLimit, int activeThreadCount, List<Identifier> recipePoolIds,
                             @Nullable Identifier currentRecipePool, @Nullable ExecutionStatus runtimeFailure,
                             boolean hasDataStorage, Map<String, DataValue> dataStorageValues,
                             List<TextLineData> textLines) {
        public HeaderData {
            Objects.requireNonNull(machineId, "machineId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(role, "role");
            machineName = copyComponent(Objects.requireNonNull(machineName, "machineName"));
            foundLevelIds = List.copyOf(foundLevelIds);
            recipePoolIds = List.copyOf(recipePoolIds);
            dataStorageValues = Map.copyOf(dataStorageValues);
            textLines = List.copyOf(textLines);
            // Lowering factory capacity retains active lanes until completion; these counts are independent.
            if (installedModuleCount < 0 || matchedStage < 0 || stageCount < 1 || parallelSlots < 0
                    || maxParallelism < 1 || threadLimit < 0 || activeThreadCount < 0) {
                throw new IllegalArgumentException("Invalid controller UI fields");
            }
        }
        @Override public Component machineName() { return copyComponent(machineName); }
    }

    /** Owned static lane fields and the independently replaceable progress tick.
     * @author howxu <dev@howxu.cn> */
    public record LaneData(String id, int index, boolean base, boolean core, boolean active,
                           @Nullable Identifier currentRecipe, int tick, int totalTick, long parallelism,
                           @Nullable ExecutionStatus runtimeFailure, List<TextLineData> textLines,
                           RecipeData recipe) implements Lane {
        public LaneData {
            if (id == null || id.isBlank() || index < 0) throw new IllegalArgumentException("Invalid lane identity");
            validateProgress(tick, totalTick, parallelism);
            textLines = List.copyOf(textLines);
            Objects.requireNonNull(recipe, "recipe");
        }
        public Optional<Identifier> recipeId() { return Optional.ofNullable(currentRecipe); }
        public Optional<ExecutionStatus> failure() { return Optional.ofNullable(runtimeFailure); }
        public List<TextLine> lines() { return copyLines(textLines); }
        private LaneData withTick(int tick) {
            return new LaneData(id, index, base, core, active, currentRecipe, tick, totalTick, parallelism,
                    runtimeFailure, textLines, recipe);
        }
        public boolean sameShape(LaneData other) {
            return other != null && id.equals(other.id) && index == other.index && base == other.base
                    && core == other.core && active == other.active && Objects.equals(currentRecipe, other.currentRecipe)
                    && totalTick == other.totalTick && parallelism == other.parallelism
                    && Objects.equals(runtimeFailure, other.runtimeFailure) && textLines.equals(other.textLines)
                    && recipe.equals(other.recipe);
        }
    }

    /** Reconstructible static recipe values; outputs retain independent expected amounts.
     * @author howxu <dev@howxu.cn> */
    public record RecipeData(List<OutputData> outputData, long energyInputPerTick, long energyOutputPerTick,
                             double heatOutputPerTick, int durationTicks, long parallelism)
            implements RecipePresentation {
        public RecipeData {
            outputData = List.copyOf(outputData);
            if (energyInputPerTick < 0 || energyOutputPerTick < 0 || !Double.isFinite(heatOutputPerTick)
                    || heatOutputPerTick < 0 || durationTicks < 0 || parallelism < 0) {
                throw new IllegalArgumentException("Invalid UI recipe presentation");
            }
        }
        public List<Output> outputs() { return outputData.stream().<Output>map(value -> value).toList(); }
        private static RecipeData capture(ControllerRecipePresentation source, RegistryAccess registries) {
            return new RecipeData(source.outputs().stream()
                    .map(value -> new OutputData(value.output(), value.amount(), registries)).toList(),
                    source.energyInputPerTick(), source.energyOutputPerTick(), source.heatOutputPerTick(),
                    source.durationTicks(), source.parallelism());
        }
    }

    /** Freezes each output with its canonical codec and independently decodes every resource read.
     * @author howxu <dev@howxu.cn> */
    public static final class OutputData implements Output {
        private final long amount;
        private final byte[] frozenResource;
        private final RecipeSyncCodec<MachineOutput> codec;
        private final Identifier kindId;
        private final RegistryAccess registries;

        public OutputData(MachineOutput resource, long amount) {
            this(resource, amount, RegistryAccess.EMPTY);
        }

        @SuppressWarnings("unchecked")
        public OutputData(MachineOutput resource, long amount, RegistryAccess registries) {
            if (amount < 0) throw new IllegalArgumentException("Expected amount must not be negative");
            this.amount = amount;
            this.registries = Objects.requireNonNull(registries, "registries");
            var type = OutputRegistry.canonicalType(Objects.requireNonNull(resource, "resource").outputType());
            if (type == null) throw new IllegalArgumentException("Output type must be registered canonically");
            kindId = type.id();
            // Stack.copy() and author-provided copiers may share arbitrary mutable component values.
            codec = (RecipeSyncCodec<MachineOutput>) type.syncCodec();
            int limit = Math.min(512 * 1024, codec.maxPayloadSize());
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                    Unpooled.buffer(Math.min(256, limit), limit), registries);
            try {
                codec.validate(resource);
                codec.encode(buffer, resource);
                frozenResource = new byte[buffer.readableBytes()];
                buffer.readBytes(frozenResource);
            } finally {
                buffer.release();
            }
        }
        public MachineOutput resource() {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.copiedBuffer(frozenResource), registries);
            try {
                MachineOutput value = codec.decode(buffer);
                if (buffer.isReadable() || value == null || !kindId.equals(value.outputType().id())
                        || !OutputRegistry.isCanonical(value)) {
                    throw new IllegalArgumentException("Output decoder did not reconstruct the owned value");
                }
                codec.validate(value);
                return value;
            } finally {
                buffer.release();
            }
        }
        public long amount() { return amount; }
        @Override public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof OutputData other) || amount != other.amount) return false;
            return kindId.equals(other.kindId) && Arrays.equals(frozenResource, other.frozenResource);
        }
        @Override public int hashCode() {
            return Long.hashCode(amount);
        }
    }

    /** Component ownership is enforced both on construction and on every read.
     * @author howxu <dev@howxu.cn> */
    public record TextLineData(Identifier id, TextLine.Scope scope, Component text) implements TextLine {
        public TextLineData {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(scope, "scope");
            text = copyComponent(Objects.requireNonNull(text, "text"));
        }
        @Override public Component text() { return copyComponent(text); }
        public TextLineData copy() { return new TextLineData(id, scope, text); }
    }

    /** Delta fields validated before any lane is replaced.
     * @author howxu <dev@howxu.cn> */
    public record LaneProgress(String laneId, @Nullable Identifier recipeId, int tick, int totalTick, long parallelism) {
        public LaneProgress {
            if (laneId == null || laneId.isBlank()) throw new IllegalArgumentException("Invalid progress lane ID");
            validateProgress(tick, totalTick, parallelism);
        }
    }

    private static void validateProgress(int tick, int totalTick, long parallelism) {
        if (tick < 0 || totalTick < 0 || tick > totalTick || parallelism < 0) {
            throw new IllegalArgumentException("Invalid lane progress");
        }
    }

    private static List<TextLine> copyLines(List<TextLineData> lines) {
        return lines.stream().<TextLine>map(TextLineData::copy).toList();
    }

    private static Component copyComponent(Component source) {
        return ComponentSnapshots.copy(source);
    }

    /**
     * Main-thread, session-owned projection cache. It retains only the current global text and current lanes.
     * Close the session with {@link #clear()}; published data never references this cache or live runtime values.
     *
     * @author howxu <dev@howxu.cn>
     */
    public static final class CaptureCache {
        private final RegistryAccess registries;
        private @Nullable UUID session;
        private @Nullable HeaderData header;
        private @Nullable HeaderKey headerKey;
        private @Nullable TextProjection globalText;
        private Map<String, LaneProjection> lanes = Map.of();
        private @Nullable Map<String, cn.howxu.mmcr.api.data.DataValue> storageSource;
        private Map<String, DataValue> storageValues = Map.of();

        public CaptureCache() { this(RegistryAccess.EMPTY); }

        public CaptureCache(RegistryAccess registries) {
            this.registries = Objects.requireNonNull(registries, "registries");
        }

        public ControllerUiSnapshotData capture(UUID sessionId, long revision, ResourceKey<Level> dimension,
                                                BlockPos controllerPos, ControllerRuntimeSnapshot runtime,
                                                @Nullable Identifier recipePoolId, boolean hasDataStorage,
                                                List<Identifier> recipePools,
                                                Map<String, ControllerScreenTextSnapshot> laneText) {
            Objects.requireNonNull(runtime, "runtime");
            if (!sessionId.equals(session)) {
                clear();
                session = sessionId;
            }
            Machine machine = runtime.structure().machine() == null
                    ? runtime.structure().configuredMachine() : runtime.structure().machine();
            Identifier machineId = runtime.machineId().isEmpty()
                    ? Objects.requireNonNull(machine, "Configured machine identity").registryName()
                    : Identifier.parse(runtime.machineId());
            Kind kind = machine != null && machine.behavior() instanceof TickBehavior ? Kind.TICK
                    : runtime.factoryControllerPresent() ? Kind.FACTORY : Kind.NORMAL;
            Role role = switch (runtime.controllerRole()) {
                case 0 -> Role.NORMAL; case 1 -> Role.HOST; case 2 -> Role.MODULE;
                default -> throw new IllegalArgumentException("Invalid controller role");
            };
            ControllerSyncRuntime sync = new ControllerSyncRuntime();
            boolean active = sync.active(runtime);
            globalText = textProjection(laneText.get(GLOBAL_TEXT_LANE), globalText);
            Map<String, LaneProjection> currentLanes = new LinkedHashMap<>();
            List<LaneData> data = new ArrayList<>();
            if (kind == Kind.FACTORY) {
                for (var lane : runtime.factory().presentationLanes()) {
                    Identifier recipeId = lane.recipeId().isEmpty() ? null : Identifier.parse(lane.recipeId());
                    LaneProjection projection = laneProjection(lane.laneId(), recipeId, lane.presentation(),
                            laneText.get(lane.laneId()));
                    currentLanes.put(lane.laneId(), projection);
                    data.add(new LaneData(lane.laneId(), lane.index(), lane.baseThread(), lane.coreThread(), lane.active(),
                            recipeId, lane.tick(), lane.totalTick(), lane.parallelism(), lane.failure(),
                            projection.text.lines, projection.recipe));
                }
            } else if (kind == Kind.NORMAL) {
                var crafting = runtime.crafting();
                LaneProjection projection = laneProjection("base", crafting.recipeId(), runtime.recipePresentation(),
                        laneText.get("base"));
                currentLanes.put("base", projection);
                data.add(new LaneData("base", 0, true, false, active && crafting.recipeId() != null,
                        crafting.recipeId(), crafting.tick(), crafting.totalTick(), crafting.parallelism(),
                        crafting.failure(), projection.text.lines, projection.recipe));
            }
            lanes = Map.copyOf(currentLanes);
            if (storageSource != runtime.dataStorageValues()) {
                storageSource = runtime.dataStorageValues();
                Map<String, DataValue> owned = new LinkedHashMap<>();
                storageSource.forEach((key, value) -> owned.put(key, DataValue.fromInternal(value)));
                storageValues = Map.copyOf(owned);
            }
            ExecutionStatus failure = runtime.factory().failure() == null
                    ? runtime.crafting().failure() : runtime.factory().failure();
            boolean paused = runtime.crafting().status().isPaused() || runtime.factory().paused();
            int threadLimit = kind == Kind.FACTORY ? runtime.factory().laneLimit() : kind == Kind.TICK ? 0 : 1;
            int activeThreads = kind == Kind.FACTORY ? runtime.factory().activeLaneCount()
                    : kind == Kind.NORMAL && active && runtime.crafting().recipeId() != null ? 1 : 0;
            HeaderKey nextKey = new HeaderKey(runtime.structure(), machineId, runtime.machineName(), kind, role,
                    active, paused, runtime.installedModuleCount(), runtime.moduleConnectionStatus(),
                    runtime.foundLevelIds(), runtime.parallelControllerCount(), runtime.maxParallelism(),
                    threadLimit, activeThreads, List.copyOf(recipePools), recipePoolId, failure,
                    hasDataStorage, storageValues, globalText.lines);
            if (!nextKey.equals(headerKey)) {
                Component name = machine == null ? Component.translatable(runtime.machineName()) : machine.displayName();
                header = new HeaderData(machineId, kind, role, name, runtime.structure().formed(), active,
                        paused, runtime.installedModuleCount(), runtime.moduleConnectionStatus().connectedHostId(),
                        runtime.structure().matchedStage(), machine == null ? 1 : Math.max(1, machine.structureStages().size()),
                        runtime.foundLevelIds().stream().map(Identifier::parse).toList(), runtime.parallelControllerCount(),
                        runtime.maxParallelism(), threadLimit, activeThreads, recipePools, recipePoolId, failure,
                        hasDataStorage, storageValues, globalText.lines);
                headerKey = nextKey;
            }
            return new ControllerUiSnapshotData(sessionId, revision, true, dimension, controllerPos, header, data);
        }

        private LaneProjection laneProjection(String id, @Nullable Identifier recipeId,
                                              ControllerRecipePresentation presentation,
                                              @Nullable ControllerScreenTextSnapshot text) {
            LaneProjection previous = lanes.get(id);
            boolean sameRecipe = previous != null && Objects.equals(previous.recipeId, recipeId);
            RecipeData owned = sameRecipe && previous.source == presentation
                    ? previous.recipe : RecipeData.capture(presentation, registries);
            TextProjection ownedText = textProjection(text, sameRecipe ? previous.text : null);
            return new LaneProjection(recipeId, presentation, owned, ownedText);
        }

        private static TextProjection textProjection(@Nullable ControllerScreenTextSnapshot source,
                                                      @Nullable TextProjection previous) {
            long revision = source == null ? -1 : source.revision();
            if (previous != null && previous.revision == revision) return previous;
            List<TextLineData> lines = source == null ? List.of() : source.lines().stream().map(line ->
                    new TextLineData(line.lineId(), switch (line.scope()) {
                        case CONTROLLER -> TextLine.Scope.CONTROLLER; case OPERATION -> TextLine.Scope.OPERATION;
                    }, line.text())).toList();
            // Stream.toList() is not reused by List.copyOf(); normalize once before caching the owned list.
            return new TextProjection(revision, List.copyOf(lines));
        }

        public void clear() {
            session = null;
            header = null;
            headerKey = null;
            globalText = null;
            lanes = Map.of();
            storageSource = null;
            storageValues = Map.of();
        }
    }

    /** Current text revision and its owned values.
     * @author howxu <dev@howxu.cn> */
    private record TextProjection(long revision, List<TextLineData> lines) {}

    /** Only the current runtime presentation identity is retained for a lane.
     * @author howxu <dev@howxu.cn> */
    private record LaneProjection(@Nullable Identifier recipeId, ControllerRecipePresentation source,
                                    RecipeData recipe, TextProjection text) {}

    /** Current static inputs allow progress captures to skip component copying and stage projection entirely.
     * @author howxu <dev@howxu.cn> */
    private record HeaderKey(StructureSnapshot structure, Identifier machineId, String machineName, Kind kind, Role role,
                              boolean active, boolean paused, int modules, ModuleConnectionStatus connection,
                              List<String> levels, int parallelSlots, long maxParallelism, int threadLimit,
                              int activeThreads, List<Identifier> pools, @Nullable Identifier pool,
                              @Nullable ExecutionStatus failure, boolean hasStorage,
                              Map<String, DataValue> storage, List<TextLineData> lines) {}
}
