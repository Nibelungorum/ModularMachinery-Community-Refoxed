package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.config.CommonConfig;
import cn.howxu.mmcr.internal.menu.MachineControllerMenu;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.ControllerRecipePresentation;
import cn.howxu.mmcr.internal.runtime.ControllerSyncRuntime;
import cn.howxu.mmcr.internal.runtime.MachineStateSnapshot;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.sync.FailureStatusCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Client-bound machine presentation state projected from one published runtime snapshot.
 *
 * @author howxu <dev@howxu.cn>
 */
public record PktMachineStatePayload(BlockPos pos, String recipeName, boolean formed, boolean active,
                                     List<String> foundLevelIds, String machineId, int controllerRole, int installedModuleCount,
                                     boolean moduleConnected, String connectedHostId,
                                      CraftingStatus.Status craftingStatus, String craftingMessage,
                                      ExecutionStatus failure, boolean structureAreaLoaded, boolean redstonePaused,
                                       int tick, int totalTick, long parallelism, long maxParallelism,
                                      boolean factoryControllerPresent, int factoryThreadCount,
                                       int activeFactoryThreadCount, int parallelControllerCount,
                                         long maxParallelControllerCount, Map<String, DataValue> dataStorageValues,
                                        int matchedStage, int stageCount,
                                         ControllerRecipePresentation recipePresentation,
                                         String recipePoolId)
        implements CustomPacketPayload {
    public static final int MAX_LEVEL_SNAPSHOTS = 1024;
    public static final int MAX_FAILURE_DETAIL_ENTRIES = FailureStatusCodec.MAX_DETAILS;
    public static final int MAX_INSTALLED_MODULES = 1024;
    static final int MAX_STRING_LENGTH = 256;
    private static final ControllerSyncRuntime SYNC_RUNTIME = new ControllerSyncRuntime();

    public PktMachineStatePayload {
        pos = pos == null ? BlockPos.ZERO : pos.immutable();
        foundLevelIds = List.copyOf(foundLevelIds == null ? List.of() : foundLevelIds);
        machineId = machineId == null ? "" : machineId;
        connectedHostId = connectedHostId == null ? "" : connectedHostId;
        craftingStatus = craftingStatus == null ? CraftingStatus.Status.IDLE : craftingStatus;
        craftingMessage = craftingMessage == null ? "" : craftingMessage;
        dataStorageValues = Map.copyOf(dataStorageValues == null ? Map.of() : dataStorageValues);
        recipePresentation = recipePresentation == null ? ControllerRecipePresentation.empty() : recipePresentation;
        recipePoolId = recipePoolId == null ? "" : recipePoolId;
        if (installedModuleCount < 0 || installedModuleCount > maxInstalledModules()
                || tick < 0 || totalTick < 0 || tick > totalTick || parallelism < 0 || maxParallelism < 1
                || factoryThreadCount < 0 || activeFactoryThreadCount < 0 || parallelControllerCount < 0
                || maxParallelControllerCount < 0
                || matchedStage < 0 || stageCount < 1) {
            throw new IllegalArgumentException("Invalid machine presentation progress");
        }
    }

    public PktMachineStatePayload(BlockPos pos, String recipeName, boolean formed, boolean active,
                                  List<String> foundLevelIds, String machineId, int controllerRole, int installedModuleCount,
                                  boolean moduleConnected, String connectedHostId, CraftingStatus.Status craftingStatus,
                                  String craftingMessage, ExecutionStatus failure, boolean structureAreaLoaded,
                                  boolean redstonePaused, int tick, int totalTick, long parallelism,
                                  long maxParallelism, boolean factoryControllerPresent, int factoryThreadCount,
                                  int activeFactoryThreadCount, int parallelControllerCount,
                                  long maxParallelControllerCount, Map<String, DataValue> dataStorageValues,
                                  int matchedStage, int stageCount) {
        this(pos, recipeName, formed, active, foundLevelIds, machineId,
                controllerRole, installedModuleCount, moduleConnected, connectedHostId, craftingStatus,
                craftingMessage, failure, structureAreaLoaded, redstonePaused, tick, totalTick, parallelism,
                maxParallelism, factoryControllerPresent, factoryThreadCount, activeFactoryThreadCount,
                parallelControllerCount, maxParallelControllerCount, dataStorageValues, matchedStage,
                stageCount, ControllerRecipePresentation.empty(), "");
    }

    public PktMachineStatePayload(BlockPos pos, String recipeName, boolean formed, boolean active,
                                  List<String> foundLevelIds, String machineId, int controllerRole, int installedModuleCount,
                                  boolean moduleConnected, String connectedHostId, CraftingStatus.Status craftingStatus,
                                  String craftingMessage, ExecutionStatus failure, boolean structureAreaLoaded,
                                  boolean redstonePaused, int tick, int totalTick, long parallelism,
                                  long maxParallelism, boolean factoryControllerPresent, int factoryThreadCount,
                                  int activeFactoryThreadCount, int parallelControllerCount,
                                  long maxParallelControllerCount, Map<String, DataValue> dataStorageValues,
                                  int matchedStage, int stageCount, String recipePoolId) {
        this(pos, recipeName, formed, active, foundLevelIds, machineId,
                controllerRole, installedModuleCount, moduleConnected, connectedHostId, craftingStatus,
                craftingMessage, failure, structureAreaLoaded, redstonePaused, tick, totalTick, parallelism,
                maxParallelism, factoryControllerPresent, factoryThreadCount, activeFactoryThreadCount,
                parallelControllerCount, maxParallelControllerCount, dataStorageValues, matchedStage,
                stageCount, ControllerRecipePresentation.empty(), recipePoolId);
    }

    public static PktMachineStatePayload from(BlockPos pos, ControllerRuntimeSnapshot runtime) {
        return from(pos, runtime, null);
    }

    public static PktMachineStatePayload from(BlockPos pos, ControllerRuntimeSnapshot runtime,
                                              ResourceLocation recipePoolId) {
        MachineStateSnapshot machineState = recipePoolId == null
                ? SYNC_RUNTIME.machineState(runtime) : SYNC_RUNTIME.machineState(runtime, recipePoolId);
        return new PktMachineStatePayload(pos, machineState.activeRecipe(), machineState.formed(), machineState.active(),
                machineState.foundLevelIds(), machineState.machineId(), machineState.controllerRole(), machineState.installedModuleCount(),
                machineState.moduleConnected(), machineState.connectedHostId(), machineState.craftingStatus(),
                machineState.craftingMessage(), machineState.failure(), machineState.structureAreaLoaded(),
                machineState.redstonePaused(),
                machineState.tick(), machineState.totalTick(), machineState.parallelism(), machineState.maxParallelism(),
                machineState.factoryControllerPresent(), machineState.factoryThreadCount(),
                machineState.activeFactoryThreadCount(), machineState.parallelControllerCount(),
                 machineState.maxParallelControllerCount(),
                 runtime.dataStorageValues(),
                  machineState.matchedStage(), machineState.stageCount(), machineState.recipePresentation(),
                machineState.recipePoolId());
    }

    public static boolean stateChanged(PktMachineStatePayload current, PktMachineStatePayload previous) {
        return !current.pos.equals(previous.pos)
                || current.formed != previous.formed
                || current.active != previous.active
                || !Objects.equals(current.recipeName, previous.recipeName)
                || !current.foundLevelIds.equals(previous.foundLevelIds)
                || !current.machineId.equals(previous.machineId)
                || current.controllerRole != previous.controllerRole
                || current.installedModuleCount != previous.installedModuleCount
                || current.moduleConnected != previous.moduleConnected
                || !current.connectedHostId.equals(previous.connectedHostId)
                || current.craftingStatus != previous.craftingStatus
                || !current.craftingMessage.equals(previous.craftingMessage)
                || !Objects.equals(current.failure, previous.failure)
                || current.structureAreaLoaded != previous.structureAreaLoaded
                || current.redstonePaused != previous.redstonePaused
                || current.tick != previous.tick
                || current.totalTick != previous.totalTick
                || current.parallelism != previous.parallelism
                || current.maxParallelism != previous.maxParallelism
                || current.factoryControllerPresent != previous.factoryControllerPresent
                || current.factoryThreadCount != previous.factoryThreadCount
                || current.activeFactoryThreadCount != previous.activeFactoryThreadCount
                || current.parallelControllerCount != previous.parallelControllerCount
                || current.maxParallelControllerCount != previous.maxParallelControllerCount
                || !current.dataStorageValues.equals(previous.dataStorageValues)
                || current.matchedStage != previous.matchedStage
                || current.stageCount != previous.stageCount
                || !current.recipePresentation.equals(previous.recipePresentation)
                || !current.recipePoolId.equals(previous.recipePoolId);
    }

    public static final Type<PktMachineStatePayload> TYPE = new Type<>(MMCR.id("machine_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PktMachineStatePayload> STREAM_CODEC =
            StreamCodec.of(PktMachineStatePayload::write, PktMachineStatePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, PktMachineStatePayload payload) {
        if (payload.foundLevelIds.size() > maxLevelSnapshots()) {
            throw new IllegalArgumentException("Invalid machine level count: " + payload.foundLevelIds.size());
        }
        buf.writeBlockPos(payload.pos);
        buf.writeUtf(payload.recipeName, maxStringLength());
        buf.writeBoolean(payload.formed);
        buf.writeBoolean(payload.active);
        buf.writeVarInt(payload.foundLevelIds.size());
        for (String id : payload.foundLevelIds) buf.writeUtf(id, maxStringLength());
        buf.writeUtf(payload.machineId, maxStringLength());
        buf.writeVarInt(payload.controllerRole);
        buf.writeVarInt(payload.installedModuleCount);
        buf.writeBoolean(payload.moduleConnected);
        buf.writeUtf(payload.connectedHostId, maxStringLength());
        buf.writeVarInt(payload.craftingStatus.ordinal());
        buf.writeUtf(payload.craftingMessage, maxStringLength());
        FailureStatusCodec.write(buf, payload.failure);
        buf.writeBoolean(payload.structureAreaLoaded);
        buf.writeBoolean(payload.redstonePaused);
        buf.writeVarInt(payload.tick);
        buf.writeVarInt(payload.totalTick);
         buf.writeLong(payload.parallelism);
         buf.writeLong(payload.maxParallelism);
        buf.writeBoolean(payload.factoryControllerPresent);
        buf.writeVarInt(payload.factoryThreadCount);
        buf.writeVarInt(payload.activeFactoryThreadCount);
        buf.writeVarInt(payload.parallelControllerCount);
         buf.writeLong(payload.maxParallelControllerCount);
        DataValuePayloadCodec.writeMap(buf, payload.dataStorageValues);
        buf.writeVarInt(payload.matchedStage);
        buf.writeVarInt(payload.stageCount);
        ControllerRecipePresentation.write(buf, payload.recipePresentation);
        buf.writeUtf(payload.recipePoolId, maxStringLength());
    }

    private static PktMachineStatePayload read(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String recipeName = buf.readUtf(maxStringLength());
        boolean formed = buf.readBoolean();
        boolean active = buf.readBoolean();
        int levelCount = buf.readVarInt();
        if (levelCount < 0 || levelCount > maxLevelSnapshots()) throw new IllegalArgumentException("Invalid machine level count");
        List<String> foundLevelIds = new ArrayList<>(levelCount);
        for (int i = 0; i < levelCount; i++) foundLevelIds.add(buf.readUtf(maxStringLength()));
        String machineId = buf.readUtf(maxStringLength());
        int controllerRole = buf.readVarInt();
        int installedModuleCount = buf.readVarInt();
        if (installedModuleCount < 0 || installedModuleCount > maxInstalledModules()) {
            throw new IllegalArgumentException("Invalid installed module count");
        }
        boolean moduleConnected = buf.readBoolean();
        String connectedHostId = buf.readUtf(maxStringLength());
        CraftingStatus.Status status = readEnum(CraftingStatus.Status.values(), buf.readVarInt(), "crafting status");
        String craftingMessage = buf.readUtf(maxStringLength());
        ExecutionStatus failure = FailureStatusCodec.read(buf);
        boolean structureAreaLoaded = buf.readBoolean();
        boolean redstonePaused = buf.readBoolean();
        return new PktMachineStatePayload(pos, recipeName, formed, active, foundLevelIds,
                machineId, controllerRole, installedModuleCount,
                moduleConnected, connectedHostId, status, craftingMessage, failure, structureAreaLoaded,
                redstonePaused,
                 buf.readVarInt(), buf.readVarInt(), buf.readLong(), buf.readLong(),
                 buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readLong(),
                DataValuePayloadCodec.readMap(buf),
                 buf.readVarInt(), buf.readVarInt(), ControllerRecipePresentation.read(buf),
                buf.readUtf(maxStringLength()));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.player();
            if (player == null) return;
            var blockEntity = player.level().getBlockEntity(pos);
            boolean menuMatches = player.containerMenu instanceof MachineControllerMenu menu
                    && menu.controllerPos().equals(pos);
            if (blockEntity instanceof MachineControllerBlockEntity controller) {
                controller.applyClientState(recipeName, formed, active, foundLevelIds,
                        machineId.isEmpty() ? null : ResourceLocation.parse(machineId), controllerRole, installedModuleCount,
                        moduleConnected, connectedHostId.isEmpty() ? null : ResourceLocation.parse(connectedHostId),
                        new CraftingStatus(craftingStatus, craftingMessage), failure, structureAreaLoaded,
                        tick, totalTick, parallelism, maxParallelism, dataStorageValues);
            }
            if (menuMatches) {
                MachineControllerMenu menu = (MachineControllerMenu) player.containerMenu;
                menu.applyClientSnapshot(this);
            }
        });
    }

    private static <T> T readEnum(T[] values, int ordinal, String name) {
        if (ordinal < 0 || ordinal >= values.length) throw new IllegalArgumentException("Invalid " + name + ": " + ordinal);
        return values[ordinal];
    }

    private static int maxLevelSnapshots() {
        return CommonConfig.valueOrDefault(CommonConfig.MACHINE_STATE_MAX_LEVEL_SNAPSHOTS, MAX_LEVEL_SNAPSHOTS);
    }

    private static int maxInstalledModules() {
        return CommonConfig.valueOrDefault(CommonConfig.MACHINE_STATE_MAX_INSTALLED_MODULES, MAX_INSTALLED_MODULES);
    }

    static int maxStringLength() {
        return CommonConfig.valueOrDefault(CommonConfig.MAX_STRING_LENGTH, MAX_STRING_LENGTH);
    }
}
