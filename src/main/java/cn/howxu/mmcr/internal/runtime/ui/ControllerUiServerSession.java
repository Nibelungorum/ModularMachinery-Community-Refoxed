package cn.howxu.mmcr.internal.runtime.ui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Kind;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.ServerContext;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.StateRegistration;
import cn.howxu.mmcr.api.controller.ui.UiProtocolRegistration.Status;
import cn.howxu.mmcr.internal.network.ui.ControllerUiPayloadCodec;
import cn.howxu.mmcr.internal.network.ui.ControllerUiRequestDispatcher.RequestWindow;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiCustomStatePayload;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiProgressPayload;
import cn.howxu.mmcr.internal.network.ui.PktControllerUiSnapshotPayload;
import cn.howxu.mmcr.api.machine.definition.TickBehavior;
import cn.howxu.mmcr.api.machine.definition.MachineBehaviorContext;
import cn.howxu.mmcr.internal.menu.ControllerMenuOpenData;
import cn.howxu.mmcr.internal.menu.ControllerUiMenu;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextSnapshot;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextState;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Main-thread lifecycle and owned snapshot projection for one actual controller menu.
 * @author howxu <dev@howxu.cn>
 */
public final class ControllerUiServerSession {
    private static final UiProtocolRegistration EMPTY_PROTOCOLS = new UiProtocolRegistration(List.of());
    private static @Nullable UiProtocolRegistration installedProtocols;
    private static boolean runtimeMenusCreated;

    static { EMPTY_PROTOCOLS.freeze(); }

    /** Task5 installs the event's authoritative core once, before any runtime controller menu exists. */
    public static synchronized void installProtocols(UiProtocolRegistration protocols) {
        Objects.requireNonNull(protocols, "protocols");
        if (installedProtocols != null || runtimeMenusCreated) {
            throw new IllegalStateException("Controller UI protocols must be installed once before runtime menus");
        }
        protocols.freeze();
        installedProtocols = protocols;
    }

    public static synchronized UiProtocolRegistration currentProtocols() {
        return installedProtocols == null ? EMPTY_PROTOCOLS : installedProtocols;
    }

    private final AbstractContainerMenu menu;
    private final MachineControllerBlockEntity owner;
    private final ServerPlayer player;
    private final ServerLevel level;
    private final ControllerMenuOpenData openData;
    private final MachineBinding machineBinding;
    private final UiProtocolRegistration protocols;
    private @Nullable ControllerUiSnapshotData.CaptureCache captureCache;
    private Map<String, ControllerScreenTextSnapshot> textSnapshots = Map.of();
    private @Nullable ControllerUiSnapshotData lastSnapshot;
    private boolean active;
    private boolean closed;
    private long revision;
    private final RequestWindow requests = new RequestWindow();
    private @Nullable ControllerUiSnapshotData sentSnapshot;
    private long fullBaselineRevision;
    private final Map<Identifier, ProviderState> providerStates = new LinkedHashMap<>();

    /** Construction binds identities but does not capture, activate, or send a baseline. */
    public ControllerUiServerSession(AbstractContainerMenu menu, MachineControllerBlockEntity owner, ServerPlayer player) {
        this.menu = Objects.requireNonNull(menu, "menu");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.player = Objects.requireNonNull(player, "player");
        this.level = (ServerLevel) Objects.requireNonNull(owner.getLevel(), "owner level");
        this.openData = ((ControllerUiMenu) menu).uiOpenData();
        this.machineBinding = MachineBinding.bind(owner.runtimeSnapshot().structure(), physicalMachineId());
        synchronized (ControllerUiServerSession.class) {
            runtimeMenusCreated = true;
            protocols = currentProtocols();
        }
    }

    public AbstractContainerMenu menu() { return menu; }
    public MachineControllerBlockEntity owner() { return owner; }
    public ServerPlayer player() { return player; }
    public ControllerMenuOpenData openData() { return openData; }
    public UiProtocolRegistration protocols() { return protocols; }
    public boolean active() { return active && !closed; }

    public @Nullable Status consumeRequest(long requestId) {
        return requests.consume(requestId, level.getGameTime());
    }

    /** One viewer's built-in and custom-state delivery, on Open, menu broadcast and after requests. */
    public void broadcastChanges() {
        if (!active()) return;
        pollSnapshot().ifPresent(this::broadcastSnapshot);
        if (!active()) return;
        for (var capability : protocols.capabilities(openData.machineId())) {
            if (!active()) return;
            if (capability.state()) protocols.state(openData.machineId(), capability.id()).ifPresent(this::broadcastState);
        }
    }

    private void broadcastSnapshot(ControllerUiSnapshotData next) {
        try {
            if (sentSnapshot == null || !next.sameShape(sentSnapshot)) {
                var packet = new PktControllerUiSnapshotPayload(menu.containerId, openData.sessionId(), next.revision(), next);
                // Fail synchronously here, not later in the connection's network encoder.
                ControllerUiPayloadCodec.encodeBounded(PktControllerUiSnapshotPayload.STREAM_CODEC, packet,
                        level.registryAccess(), ControllerUiPayloadCodec.SNAPSHOT_LIMIT);
                player.connection.send(new ClientboundCustomPayloadPacket(packet));
                fullBaselineRevision = next.revision();
            } else {
                List<ControllerUiSnapshotData.LaneProgress> progress = new ArrayList<>();
                for (int i = 0; i < next.laneData().size(); i++) {
                    var lane = next.laneData().get(i);
                    if (lane.tick() != sentSnapshot.laneData().get(i).tick()) {
                        progress.add(new ControllerUiSnapshotData.LaneProgress(lane.id(), lane.currentRecipe(),
                                lane.tick(), lane.totalTick(), lane.parallelism()));
                    }
                }
                player.connection.send(new ClientboundCustomPayloadPacket(new PktControllerUiProgressPayload(
                        menu.containerId, openData.sessionId(), next.revision(), fullBaselineRevision, progress)));
            }
            sentSnapshot = next;
        } catch (RuntimeException exception) {
            MMCR.LOG.error("Controller UI snapshot failed machine={} session={} revision={}",
                    openData.machineId(), openData.sessionId(), next.revision(), exception);
        }
    }

    private <T> void broadcastState(StateRegistration<T> registration) {
        if (!validate()) return;
        Identifier id = registration.type().id();
        ProviderState state = providerStates.computeIfAbsent(id, ignored -> new ProviderState());
        byte[] bytes = state.capture(registration, this::providerContext, level.registryAccess(),
                (providerRevision, exception) -> logProviderFailure(id, providerRevision, exception));
        if (bytes == null) return;
        long providerRevision = state.observedRevision;
        try {
            byte[] owned = state.bytes != null && Arrays.equals(state.bytes, bytes) ? state.bytes : bytes.clone();
            var packet = new PktControllerUiCustomStatePayload(menu.containerId, openData.sessionId(), id,
                    registration.type().version(), providerRevision, owned);
            player.connection.send(new ClientboundCustomPayloadPacket(packet));
            state.bytes = owned;
            state.successfulRevision = providerRevision;
        } catch (Exception exception) {
            logProviderFailure(id, providerRevision, exception);
        }
    }

    private @Nullable ProviderContext providerContext() {
        if (!active()) return null;
        ControllerRuntimeSnapshot runtime = owner.runtimeSnapshot();
        if (!validateRuntime(runtime)) return null;
        return new ProviderContext(new ServerContext(player, owner.controllerUiBehaviorContext(runtime), Optional.empty()),
                runtime);
    }

    private void logProviderFailure(Identifier id, @Nullable Long providerRevision, Exception exception) {
        MMCR.LOG.error("Controller UI state failed machine={} state={} session={} revision={}",
                openData.machineId(), id, openData.sessionId(), providerRevision, exception);
    }

    /** Only the current observed revision and last successful owned value; never shared across viewers.
     * @author howxu <dev@howxu.cn>
     */
    static final class ProviderState {
        private @Nullable Long observedRevision;
        private long successfulRevision;
        private byte @Nullable [] bytes;
        private boolean revisionQueryFailed;

        <T> byte @Nullable [] capture(StateRegistration<T> registration, Supplier<@Nullable ProviderContext> current,
                                     RegistryAccess registries, BiConsumer<@Nullable Long, Exception> failure) {
            ProviderContext before;
            long providerRevision;
            try {
                before = current.get();
                if (before == null) return null;
                providerRevision = registration.provider().revision(before.context());
                if (providerRevision < 0) throw new IllegalArgumentException("Negative controller UI provider revision");
                revisionQueryFailed = false;
            } catch (Exception exception) {
                if (!revisionQueryFailed) failure.accept(null, exception);
                revisionQueryFailed = true;
                return null;
            }
            try {
                // Revision is author code: it may close the menu or rebind storage/structure without changing machineId.
                ProviderContext capture = current.get();
                if (capture == null || !before.sameBinding(capture)) return null;
                if (Objects.equals(observedRevision, providerRevision)) return null;
                // Only a capture on a still-current binding consumes this revision, including failed author code.
                Long previousRevision = observedRevision;
                observedRevision = providerRevision;
                T value = registration.provider().snapshot(capture.context());
                ProviderContext afterCapture = current.get();
                if (afterCapture == null || !capture.sameBinding(afterCapture)) {
                    observedRevision = previousRevision;
                    return null;
                }
                byte[] bytes = ControllerUiPayloadCodec.encodeBounded(registration.type().codec(), value,
                        registries, ControllerUiPayloadCodec.STATE_LIMIT);
                ProviderContext afterEncoding = current.get();
                if (afterEncoding == null || !capture.sameBinding(afterEncoding)) {
                    observedRevision = previousRevision;
                    return null;
                }
                return bytes;
            } catch (Exception exception) {
                failure.accept(providerRevision, exception);
                return null;
            }
        }
    }

    /** A callback's actual context and the runtime generation which supplied its structure and IO binding.
     * @author howxu <dev@howxu.cn>
     */
    record ProviderContext(ServerContext context, ControllerRuntimeSnapshot runtime) {
        boolean sameBinding(ProviderContext other) {
            return runtime.structure().equals(other.runtime.structure())
                    && runtime.capabilityVersion() == other.runtime.capabilityVersion()
                    && runtime.modifierVersion() == other.runtime.modifierVersion()
                    && runtime.upgradeContentRevision() == other.runtime.upgradeContentRevision()
                    && runtime.controllerRole() == other.runtime.controllerRole()
                    && runtime.moduleConnectionStatus().equals(other.runtime.moduleConnectionStatus())
                    && Objects.equals(context.machine().machineId(), other.context.machine().machineId())
                    && context.machine().dataStorageForRuntime() == other.context.machine().dataStorageForRuntime();
        }
    }

    /** The first baseline remains queued for pollSnapshot; Open runs after container installation. */
    public void activate(ServerPlayer player) {
        if (closed || active || player != this.player) return;
        if (!boundIdentitiesValid(owner.runtimeSnapshot())) {
            invalidate();
            return;
        }
        active = true;
    }

    /** Common authorization seam for future C2S requests. Token mismatches do not close a newer menu. */
    public boolean validate(ServerPlayer player, int containerId, UUID sessionId) {
        return player == this.player && menu.containerId == containerId && openData.sessionId().equals(sessionId)
                && validate();
    }

    public boolean validate() {
        return active && !closed && validateRuntime(owner.runtimeSnapshot());
    }

    private boolean validateRuntime(ControllerRuntimeSnapshot runtime) {
        if (boundIdentitiesValid(runtime)) return true;
        invalidate();
        return false;
    }

    private boolean boundIdentitiesValid(ControllerRuntimeSnapshot runtime) {
        return !owner.isRemoved() && owner.getLevel() == level && player.level() == level
                && player.containerMenu == menu && level.dimension().equals(openData.dimension())
                && ((ControllerUiMenu) menu).uiOpenData() == openData
                && ((ControllerUiMenu) menu).uiServerSession() == this
                && level.getBlockEntity(openData.pos()) == owner
                && machineBinding.matches(runtime.structure(), physicalMachineId())
                && ControllerMenuOpenData.machineId(owner, runtime).equals(openData.machineId())
                && menu.stillValid(player);
    }

    private Identifier physicalMachineId() {
        return ((MachineControllerBlock) owner.getBlockState().getBlock()).machineId();
    }

    /** Normalize only the initial null configuration to its physical machine, never to a stale matched machine.
     * @author howxu <dev@howxu.cn>
     */
    record MachineBinding(Identifier configuredId, Identifier physicalId) {
        static MachineBinding bind(StructureSnapshot structure, Identifier physicalId) {
            return new MachineBinding(structure.configuredMachine() == null ? physicalId
                    : structure.configuredMachine().registryName(), physicalId);
        }

        boolean matches(StructureSnapshot structure, Identifier physicalId) {
            return equals(bind(structure, physicalId));
        }
    }

    /** Authorize against current semantic lanes, not the opening kind or unrelated factory placeholders. */
    public boolean laneExists(Optional<String> laneId) {
        if (!active()) return false;
        ControllerRuntimeSnapshot runtime = owner.runtimeSnapshot();
        return validateRuntime(runtime) && laneExists(uiRuntime(runtime), laneId);
    }

    static boolean laneExists(ControllerRuntimeSnapshot runtime, Optional<String> laneId) {
        if (laneId.isEmpty()) return true;
        return switch (runtimeKind(runtime)) {
            case NORMAL -> laneId.orElseThrow().equals("base");
            case TICK -> false;
            case FACTORY -> runtime.factory().presentationLanes().stream()
                    // FactoryRuntime.completeThreadSnapshots pads display capacity with idle-*; these have no thread.
                    .anyMatch(lane -> !lane.laneId().startsWith("idle-") && lane.laneId().equals(laneId.orElseThrow()));
        };
    }

    static Kind runtimeKind(ControllerRuntimeSnapshot runtime) {
        var machine = runtime.structure().machine() == null
                ? runtime.structure().configuredMachine() : runtime.structure().machine();
        return machine != null && machine.behavior() instanceof TickBehavior ? Kind.TICK
                : runtime.factoryControllerPresent() ? Kind.FACTORY : Kind.NORMAL;
    }

    private ControllerRuntimeSnapshot uiRuntime(ControllerRuntimeSnapshot runtime) {
        return runtime.structure().machine() == null && runtime.structure().configuredMachine() == null
                ? physicalMachineProjection(runtime) : runtime;
    }

    public Optional<ControllerUiSnapshotData> pollSnapshot() {
        if (!active || closed) return Optional.empty();
        ControllerRuntimeSnapshot runtime = owner.runtimeSnapshot();
        if (!validateRuntime(runtime)) return Optional.empty();
        Map<String, ControllerScreenTextSnapshot> currentText = new LinkedHashMap<>();
        MachineBehaviorContext context = owner.controllerUiBehaviorContext(runtime);
        if (context.screenText() instanceof ControllerScreenTextState globalText) {
            captureText(currentText, ControllerUiSnapshotData.GLOBAL_TEXT_LANE, globalText);
        }
        runtime = uiRuntime(runtime);
        Kind kind = runtimeKind(runtime);
        if (kind == Kind.FACTORY) {
            for (var lane : runtime.factory().presentationLanes()) {
                captureText(currentText, lane.laneId(), owner.recipeScreenText(lane.laneId()));
            }
        } else if (kind == Kind.NORMAL) {
            captureText(currentText, "base", owner.recipeScreenText("base"));
        }
        textSnapshots = Map.copyOf(currentText);
        if (captureCache == null) captureCache = new ControllerUiSnapshotData.CaptureCache(level.registryAccess());
        ControllerUiSnapshotData next = captureCache.capture(openData.sessionId(), revision + 1,
                openData.dimension(), openData.pos(), runtime, owner.currentRecipePoolId(),
                context.dataStorageForRuntime() != null,
                MachineRegistry.recipePoolsForMachine(openData.machineId()),
                textSnapshots);
        if (lastSnapshot != null && next.sameShape(lastSnapshot)
                && next.laneData().equals(lastSnapshot.laneData())) return Optional.empty();
        revision = next.revision();
        lastSnapshot = next;
        return Optional.of(next);
    }

    /** A just-placed controller can open before its first structure tick has bound the physical machine. */
    private ControllerRuntimeSnapshot physicalMachineProjection(ControllerRuntimeSnapshot runtime) {
        var machine = MachineRegistry.getMachine(openData.machineId());
        StructureSnapshot structure = runtime.structure();
        var configured = new StructureSnapshot(machine, structure.machine(), structure.pattern(), structure.compiledPattern(),
                structure.facing(), structure.rollFacing(), structure.matchedStage(), structure.formed(), structure.version(),
                structure.lastStructureError(), structure.structureMismatchDiagnostic(), structure.lastFormationFailure(),
                structure.dirty(), structure.structureAreaLoaded(), structure.criticalChunks());
        return new ControllerRuntimeSnapshot(configured, runtime.capabilityVersion(), runtime.modifierVersion(),
                runtime.stateVersion(), runtime.foundModifiers(), runtime.foundLevels(), runtime.linkedPortPositions(),
                runtime.moduleConnectionStatus(), runtime.installedModuleCount(), runtime.crafting(), runtime.factory(),
                runtime.componentPresentations(), runtime.capabilityPresentations(), runtime.foundLevelIds(),
                openData.machineId().toString(), machine == null ? runtime.machineName() : machine.displayNameKey(),
                openData.role().ordinal(), runtime.factorySupported(), runtime.factoryControllerPresent(),
                runtime.parallelControllerCount(), runtime.maxParallelControllerCount(), runtime.maxParallelism(),
                runtime.upgradeItems(), runtime.upgradeContentRevision(), runtime.dataStorageValues(), runtime.recipePresentation());
    }

    private void captureText(Map<String, ControllerScreenTextSnapshot> target, String laneId,
                             ControllerScreenTextState state) {
        ControllerScreenTextSnapshot previous = textSnapshots.get(laneId);
        target.put(laneId, previous != null && previous.revision() == state.revision() ? previous : state.snapshot());
    }

    public void close() {
        if (closed) return;
        closed = true;
        active = false;
        lastSnapshot = null;
        sentSnapshot = null;
        providerStates.clear();
        fullBaselineRevision = 0;
        textSnapshots = Map.of();
        if (captureCache != null) {
            captureCache.clear();
            captureCache = null;
        }
    }

    private void invalidate() {
        close();
        if (player.containerMenu == menu) player.closeContainer();
    }
}
