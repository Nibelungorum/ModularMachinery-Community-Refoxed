package cn.howxu.mmcr.internal.multiblock;

import cn.howxu.mmcr.api.machine.CompiledMachinePattern;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.StructureMatcher;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.ModuleCouplerBlockEntity;
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
        clearHostConnectionsWithSharedInterfaces(level);
        if (coupler.connectedHost().isPresent() || coupler.connectedModule().isPresent()) return;

        List<MachineControllerBlockEntity> hosts = controllersFor(level, couplerPos, true);
        List<MachineControllerBlockEntity> modules = controllersFor(level, couplerPos, false);
        coupler.clearConnection();
        if (hosts.size() != 1 || modules.size() != 1) return;

        MachineControllerBlockEntity host = hosts.getFirst();
        MachineControllerBlockEntity module = modules.getFirst();
        if (!canConnect(level, couplerPos, host, module)) return;
        coupler.setConnection(GlobalPos.of(level.dimension(), host.getBlockPos()), GlobalPos.of(level.dimension(), module.getBlockPos()));
    }

    public static boolean validate(ModuleCouplerBlockEntity coupler) {
        if (coupler == null || !(coupler.getLevel() instanceof ServerLevel level)) return false;
        Optional<GlobalPos> hostPos = coupler.connectedHost();
        Optional<GlobalPos> modulePos = coupler.connectedModule();
        if (hostPos.isPresent() || modulePos.isPresent()) return false;
        List<MachineControllerBlockEntity> hosts = controllersFor(level, coupler.getBlockPos(), true);
        List<MachineControllerBlockEntity> modules = controllersFor(level, coupler.getBlockPos(), false);
        return hosts.size() == 1 && modules.size() == 1 && canConnect(level, coupler.getBlockPos(), hosts.getFirst(), modules.getFirst());
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
        for (BlockPos couplerPos : couplerWorldPositions(controller)) {
            if (level.getBlockEntity(couplerPos) instanceof ModuleCouplerBlockEntity coupler) coupler.clearConnection();
        }
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

    private static boolean canConnect(ServerLevel level, BlockPos couplerPos,
                                      MachineControllerBlockEntity host, MachineControllerBlockEntity module) {
        if (!isFormedHost(host) || !isFormedModule(module)) return false;
        Machine hostMachine = host.getFoundMachine();
        Machine moduleMachine = module.getFoundMachine();
        if (!hostMachine.acceptedModuleIds().contains(moduleMachine.registryName())) return false;
        if (!couplerWorldPositions(host).contains(couplerPos) || !couplerWorldPositions(module).contains(couplerPos)) return false;
        if (!structureMatches(level, host) || !structureMatches(level, module)) return false;
        if (interfacesOverlap(host, module)) return false;
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

    private static void clearHostConnectionsWithSharedInterfaces(ServerLevel level) {
        List<MachineControllerBlockEntity> controllers = StructureClaimRegistry.get(level).claimedControllers().stream()
                .map(level::getBlockEntity)
                .filter(MachineControllerBlockEntity.class::isInstance)
                .map(MachineControllerBlockEntity.class::cast)
                .toList();
        for (MachineControllerBlockEntity host : controllers) {
            if (!isFormedHost(host)) continue;
            for (MachineControllerBlockEntity module : controllers) {
                if (!isFormedModule(module)) continue;
                if (!interfacesOverlap(host, module)) continue;
                clearConnectionsFor(level, host);
                host.releaseStructureClaims();
                host.setFormed(false);
                break;
            }
        }
    }

    private static boolean isFormedHost(MachineControllerBlockEntity controller) {
        Machine machine = controller.getFoundMachine();
        return controller.isFormed() && machine != null && machine.isHost() && compiled(controller) != null && facing(controller) != null;
    }

    private static boolean isFormedModule(MachineControllerBlockEntity controller) {
        Machine machine = controller.getFoundMachine();
        return controller.isFormed() && machine != null && machine.isModule() && compiled(controller) != null && facing(controller) != null;
    }

    private static boolean structureMatches(ServerLevel level, MachineControllerBlockEntity controller) {
        CompiledMachinePattern compiled = compiled(controller);
        Direction facing = facing(controller);
        return compiled != null && facing != null
                && StructureMatcher.matchesCompiled(compiled, facing, level, controller.getBlockPos());
    }

    private static Set<BlockPos> couplerWorldPositions(MachineControllerBlockEntity controller) {
        CompiledMachinePattern compiled = compiled(controller);
        Direction facing = facing(controller);
        if (compiled == null || facing == null) return Set.of();
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (BlockPos relative : compiled.couplerPositions(facing)) positions.add(controller.getBlockPos().offset(relative));
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
        return controller.getFoundCompiledPattern();
    }

    private static Direction facing(MachineControllerBlockEntity controller) {
        return controller.getControllerFacing();
    }
}
