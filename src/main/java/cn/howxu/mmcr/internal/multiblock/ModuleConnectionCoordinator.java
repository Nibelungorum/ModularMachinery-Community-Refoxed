package cn.howxu.mmcr.internal.multiblock;

import cn.howxu.mmcr.api.machine.CompiledMachinePattern;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.ModuleCouplerBlockEntity;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Collections;

/**
 * Reconciles host and module controllers that meet at one world-space module coupler.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ModuleConnectionCoordinator {
    private static final int FALLBACK_SCAN_INTERVAL_TICKS = 60;

    private ModuleConnectionCoordinator() {
    }

    public static void refresh(ServerLevel level, BlockPos couplerPos) {
        if (level == null || couplerPos == null || !level.hasChunk(couplerPos.getX() >> 4, couplerPos.getZ() >> 4)) return;
        if (!(level.getBlockEntity(couplerPos) instanceof ModuleCouplerBlockEntity coupler)) return;
        Optional<MachineConnection> existing = existingConnection(level, coupler);

        List<MachineControllerBlockEntity> hosts = controllersFor(level, couplerPos, true);
        List<MachineControllerBlockEntity> modules = controllersFor(level, couplerPos, false);
        coupler.clearConnection();
        if (existing.isPresent()) {
            MachineConnection connection = existing.get();
            refreshRuntimeConnectionState(connection);
            if (interfacesOverlap(connection.host(), connection.module())) {
                invalidateHost(level, connection.host());
                return;
            }
            if (canConnect(level, couplerPos, connection.host(), connection.module())) {
                restoreConnection(level, coupler, connection);
                return;
            }
        }
        if (hosts.size() != 1 || modules.size() != 1) return;

        MachineControllerBlockEntity host = hosts.getFirst();
        MachineControllerBlockEntity module = modules.getFirst();
        if (interfacesOverlap(host, module)) {
            invalidateHost(level, host);
            return;
        }
        if (!canConnect(level, couplerPos, host, module)) return;
        restoreConnection(level, coupler, new MachineConnection(host, module));
    }

    public static boolean validate(ModuleCouplerBlockEntity coupler) {
        if (coupler == null || !(coupler.getLevel() instanceof ServerLevel level)) return false;
        if (!level.hasChunk(coupler.getBlockPos().getX() >> 4, coupler.getBlockPos().getZ() >> 4)) return false;
        Optional<GlobalPos> hostPos = coupler.connectedHost();
        Optional<GlobalPos> modulePos = coupler.connectedModule();
        if (hostPos.isPresent() || modulePos.isPresent()) return false;
        List<MachineControllerBlockEntity> hosts = controllersFor(level, coupler.getBlockPos(), true);
        List<MachineControllerBlockEntity> modules = controllersFor(level, coupler.getBlockPos(), false);
        if (hosts.size() != 1 || modules.size() != 1) return false;
        MachineControllerBlockEntity host = hosts.getFirst();
        MachineControllerBlockEntity module = modules.getFirst();
        return !interfacesOverlap(host, module) && canConnect(level, coupler.getBlockPos(), host, module);
    }

    public static void tick(ServerLevel level) {
        for (BlockPos couplerPos : ModuleConnectionRefreshQueue.drain(level)) refresh(level, couplerPos);
        if (level.getGameTime() % FALLBACK_SCAN_INTERVAL_TICKS != 0) return;
        for (BlockPos couplerPos : registeredCouplers(level)) {
            if (level.hasChunk(couplerPos.getX() >> 4, couplerPos.getZ() >> 4)) refresh(level, couplerPos);
        }
    }

    public static void clearConnectionsFor(ServerLevel level, MachineControllerBlockEntity controller) {
        if (level == null || controller == null) return;
        Set<MachineControllerBlockEntity> affectedControllers = new LinkedHashSet<>();
        affectedControllers.add(controller);
        for (BlockPos couplerPos : couplerWorldPositions(controller)) {
            if (!(level.getBlockEntity(couplerPos) instanceof ModuleCouplerBlockEntity coupler)) continue;
            existingConnection(level, coupler).ifPresent(connection -> {
                affectedControllers.add(connection.host());
                affectedControllers.add(connection.module());
            });
            controllersFor(level, couplerPos, true).forEach(affectedControllers::add);
            controllersFor(level, couplerPos, false).forEach(affectedControllers::add);
            coupler.clearConnection();
        }
        affectedControllers.forEach(MachineControllerBlockEntity::refreshModuleConnectionState);
    }

    public static void enqueueCouplers(ServerLevel level, MachineControllerBlockEntity controller) {
        if (level == null || controller == null) return;
        for (BlockPos couplerPos : couplerWorldPositions(controller)) ModuleConnectionRefreshQueue.enqueue(level, couplerPos);
    }

    public static void enqueueCouplersInChunk(ServerLevel level, ChunkPos chunkPos) {
        if (level == null || chunkPos == null) return;
        for (BlockPos couplerPos : registeredCouplers(level)) {
            if ((couplerPos.getX() >> 4) == chunkPos.x() && (couplerPos.getZ() >> 4) == chunkPos.z()) {
                ModuleConnectionRefreshQueue.enqueue(level, couplerPos);
            }
        }
    }

    public static ModuleConnectionStatus connectionStatus(MachineControllerBlockEntity controller) {
        if (controller == null) return ModuleConnectionStatus.notRequired();
        StructureSnapshot moduleStructure = controller.structureSnapshot();
        Machine moduleMachine = moduleStructure.machine();
        if (moduleMachine == null || !moduleMachine.isModule()) return ModuleConnectionStatus.notRequired();
        if (!(controller.getLevel() instanceof ServerLevel level)) return ModuleConnectionStatus.disconnected();
        GlobalPos expectedModule = GlobalPos.of(level.dimension(), controller.getBlockPos());
        for (BlockPos couplerPos : couplerWorldPositions(controller)) {
            if (!(level.getBlockEntity(couplerPos) instanceof ModuleCouplerBlockEntity coupler)) continue;
            Optional<GlobalPos> hostPos = coupler.connectedHost();
            Optional<GlobalPos> modulePos = coupler.connectedModule();
            if (hostPos.isEmpty() || modulePos.isEmpty() || !expectedModule.equals(modulePos.get())) continue;
            if (!level.dimension().equals(hostPos.get().dimension())) continue;
            BlockEntity hostEntity = level.getBlockEntity(hostPos.get().pos());
            if (!(hostEntity instanceof MachineControllerBlockEntity host)) continue;
            Machine hostMachine = host.structureSnapshot().machine();
            if (hostMachine != null && canConnect(level, couplerPos, host, controller)) {
                return ModuleConnectionStatus.connected(hostMachine.registryName());
            }
        }
        return ModuleConnectionStatus.disconnected();
    }

    public static int installedModuleCount(MachineControllerBlockEntity controller) {
        if (controller == null || !(controller.getLevel() instanceof ServerLevel level)) return 0;
        if (!isFormedHost(controller)) return 0;
        int count = 0;
        for (BlockPos couplerPos : couplerWorldPositions(controller)) {
            if (!level.hasChunk(couplerPos.getX() >> 4, couplerPos.getZ() >> 4)) continue;
            if (!(level.getBlockEntity(couplerPos) instanceof ModuleCouplerBlockEntity coupler)) continue;
            Optional<GlobalPos> hostPos = coupler.connectedHost();
            Optional<GlobalPos> modulePos = coupler.connectedModule();
            if (hostPos.isEmpty() || modulePos.isEmpty()) continue;
            if (!level.dimension().equals(hostPos.get().dimension()) || !level.dimension().equals(modulePos.get().dimension())) continue;
            if (!controller.getBlockPos().equals(hostPos.get().pos())) continue;
            BlockEntity moduleEntity = level.getBlockEntity(modulePos.get().pos());
            if (!(moduleEntity instanceof MachineControllerBlockEntity module)) continue;
            if (canConnect(level, couplerPos, controller, module)) count++;
        }
        return count;
    }

    public static boolean blocksHostFormation(ServerLevel level, Machine hostMachine, CompiledMachinePattern hostPattern,
                                               Direction facing, BlockPos hostPos) {
        if (level == null || hostMachine == null || hostPattern == null || !hostMachine.isHost()) return false;
        Set<BlockPos> hostCouplers = worldPositions(hostPattern.couplerPositions(facing), hostPos);
        Set<BlockPos> hostInterfaces = worldPositions(hostPattern.interfacePositions(facing), hostPos);
        if (hostCouplers.isEmpty() || hostInterfaces.isEmpty()) return false;
        for (BlockPos controllerPos : StructureClaimRegistry.get(level).claimedControllers()) {
            if (!(level.getBlockEntity(controllerPos) instanceof MachineControllerBlockEntity module)
                    || !isFormedModule(module)) continue;
            Machine moduleMachine = module.structureSnapshot().machine();
            if (!hostMachine.acceptedModuleIds().contains(moduleMachine.registryName())) continue;
            Set<BlockPos> moduleCouplers = couplerWorldPositions(module);
            if (Collections.disjoint(hostCouplers, moduleCouplers)) continue;
            for (BlockPos moduleInterface : interfaceWorldPositions(module)) {
                if (hostInterfaces.contains(moduleInterface)) return true;
            }
        }
        return false;
    }

    private static boolean canConnect(ServerLevel level, BlockPos couplerPos,
                                       MachineControllerBlockEntity host, MachineControllerBlockEntity module) {
        if (!isFormedHost(host) || !isFormedModule(module)) return false;
        Machine hostMachine = host.structureSnapshot().machine();
        Machine moduleMachine = module.structureSnapshot().machine();
        if (!hostMachine.acceptedModuleIds().contains(moduleMachine.registryName())) return false;
        if (!couplerWorldPositions(host).contains(couplerPos) || !couplerWorldPositions(module).contains(couplerPos)) return false;
        if (!structureMatches(level, host) || !structureMatches(level, module)) return false;
        return true;
    }

    private static List<MachineControllerBlockEntity> controllersFor(ServerLevel level, BlockPos couplerPos, boolean host) {
        return StructureClaimRegistry.get(level).claimedControllers().stream()
                .map(level::getBlockEntity)
                .filter(MachineControllerBlockEntity.class::isInstance)
                .map(MachineControllerBlockEntity.class::cast)
                .filter(controller -> host ? isFormedHost(controller) : isFormedModule(controller))
                .filter(controller -> couplerWorldPositions(controller).contains(couplerPos))
                .toList();
    }

    private static Set<BlockPos> registeredCouplers(ServerLevel level) {
        Set<BlockPos> couplers = new LinkedHashSet<>();
        for (BlockPos controllerPos : StructureClaimRegistry.get(level).claimedControllers()) {
            if (level.getBlockEntity(controllerPos) instanceof MachineControllerBlockEntity controller) {
                couplers.addAll(couplerWorldPositions(controller));
            }
        }
        return couplers;
    }

    private static Optional<MachineConnection> existingConnection(ServerLevel level, ModuleCouplerBlockEntity coupler) {
        Optional<GlobalPos> hostPos = coupler.connectedHost();
        Optional<GlobalPos> modulePos = coupler.connectedModule();
        if (hostPos.isEmpty() || modulePos.isEmpty()) return Optional.empty();
        if (!level.dimension().equals(hostPos.get().dimension()) || !level.dimension().equals(modulePos.get().dimension())) {
            return Optional.empty();
        }
        BlockEntity host = level.getBlockEntity(hostPos.get().pos());
        BlockEntity module = level.getBlockEntity(modulePos.get().pos());
        if (host instanceof MachineControllerBlockEntity hostController
                && module instanceof MachineControllerBlockEntity moduleController) {
            return Optional.of(new MachineConnection(hostController, moduleController));
        }
        return Optional.empty();
    }

    private static void restoreConnection(ServerLevel level, ModuleCouplerBlockEntity coupler, MachineConnection connection) {
        coupler.setConnection(GlobalPos.of(level.dimension(), connection.host().getBlockPos()),
                GlobalPos.of(level.dimension(), connection.module().getBlockPos()));
        refreshRuntimeConnectionState(connection);
    }

    private static void refreshRuntimeConnectionState(MachineConnection connection) {
        connection.host().refreshModuleConnectionState();
        connection.module().refreshModuleConnectionState();
    }

    private static void invalidateHost(ServerLevel level, MachineControllerBlockEntity host) {
        clearConnectionsFor(level, host);
        host.invalidateFormedStructure();
    }

    private static boolean isFormedHost(MachineControllerBlockEntity controller) {
        StructureSnapshot structure = controller.structureSnapshot();
        Machine machine = structure.machine();
        return structure.formed() && machine != null && machine.isHost()
                && structure.compiledPattern() != null && structure.facing() != null;
    }

    private static boolean isFormedModule(MachineControllerBlockEntity controller) {
        StructureSnapshot structure = controller.structureSnapshot();
        Machine machine = structure.machine();
        return structure.formed() && machine != null && machine.isModule()
                && structure.compiledPattern() != null && structure.facing() != null;
    }

    private static boolean structureMatches(ServerLevel level, MachineControllerBlockEntity controller) {
        CompiledMachinePattern compiled = compiled(controller);
        Direction facing = facing(controller);
        return compiled != null && facing != null
                && StructureMatcher.matchesCompiled(compiled, facing, level, controller.getBlockPos(), compiled.stateSensitive());
    }

    private static Set<BlockPos> couplerWorldPositions(MachineControllerBlockEntity controller) {
        CompiledMachinePattern compiled = compiled(controller);
        Direction facing = facing(controller);
        if (compiled == null || facing == null) return Set.of();
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (BlockPos relative : compiled.couplerPositions(facing)) positions.add(controller.getBlockPos().offset(relative));
        return Set.copyOf(positions);
    }

    private static Set<BlockPos> worldPositions(List<BlockPos> relativePositions, BlockPos controllerPos) {
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (BlockPos relative : relativePositions) positions.add(controllerPos.offset(relative));
        return Set.copyOf(positions);
    }

    private static boolean interfacesOverlap(MachineControllerBlockEntity host, MachineControllerBlockEntity module) {
        Set<BlockPos> hostInterfaces = interfaceWorldPositions(host);
        if (hostInterfaces.isEmpty()) return false;
        for (BlockPos moduleInterface : interfaceWorldPositions(module)) {
            if (hostInterfaces.contains(moduleInterface)) return true;
        }
        return false;
    }

    private static Set<BlockPos> interfaceWorldPositions(MachineControllerBlockEntity controller) {
        CompiledMachinePattern compiled = compiled(controller);
        Direction facing = facing(controller);
        if (compiled == null || facing == null) return Set.of();
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (BlockPos relative : compiled.interfacePositions(facing)) positions.add(controller.getBlockPos().offset(relative));
        return Set.copyOf(positions);
    }

    private static CompiledMachinePattern compiled(MachineControllerBlockEntity controller) {
        return controller.structureSnapshot().compiledPattern();
    }

    private static Direction facing(MachineControllerBlockEntity controller) {
        return controller.structureSnapshot().facing();
    }

    private record MachineConnection(MachineControllerBlockEntity host, MachineControllerBlockEntity module) { }
}
