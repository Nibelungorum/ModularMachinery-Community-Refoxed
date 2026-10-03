package cn.howxu.mmcr.compat.fluxnetworks.loaded;

import cn.howxu.mmcr.mixin.compat.fluxnetworks.loaded.TransferHandlerAccessor;
import sonar.fluxnetworks.common.connection.FluxNetwork;
import sonar.fluxnetworks.common.connection.ServerFluxNetwork;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Returns free Point buffers to their source network inside its native allocation cycle.
 * @author howxu <dev@howxu.cn>
 */
public final class FluxNetworkRefunds {
    private static final Map<Integer, WeakHashMap<FluxNetworkInputBlockEntity, Boolean>> PENDING = new HashMap<>();

    private FluxNetworkRefunds() {
    }

    static void track(FluxNetworkInputBlockEntity source) {
        FluxNetworkPointHandler handler = source.getTransferHandler();
        for (int network : handler.bufferNetworks()) {
            if (handler.refundable(network) > 0L) {
                PENDING.computeIfAbsent(network, ignored -> new WeakHashMap<>()).put(source, true);
            }
        }
    }

    static void forget(FluxNetworkInputBlockEntity source) {
        PENDING.values().forEach(sources -> sources.remove(source));
        PENDING.values().removeIf(Map::isEmpty);
    }

    public static void refund(ServerFluxNetwork network) {
        var sources = PENDING.get(network.getNetworkID());
        if (sources == null) return;
        for (FluxNetworkInputBlockEntity source : new ArrayList<>(sources.keySet())) {
            var level = source.getLevel();
            FluxNetworkPointHandler handler = source.getTransferHandler();
            if (source.isRemoved() || level == null || !level.hasChunkAt(source.getBlockPos())
                    || level.getBlockEntity(source.getBlockPos()) != source
                    || handler.refundable(network.getNetworkID()) == 0L) {
                sources.remove(source);
                continue;
            }
            source.checkServerThread();
            // POINT is already sorted by native priority, with network storage last.
            for (var receiver : network.getLogicalDevices(FluxNetwork.POINT)) {
                if (receiver == source || receiver.isRemoved() || receiver.getNetwork() != network) continue;
                long returned = handler.returnToNetwork(network.getNetworkID(), receiver.getTransferHandler().getRequest());
                if (returned > 0L) ((TransferHandlerAccessor) receiver.getTransferHandler()).mmcr$addToBuffer(returned);
                if (handler.refundable(network.getNetworkID()) == 0L) break;
            }
            if (handler.refundable(network.getNetworkID()) == 0L) sources.remove(source);
        }
        if (sources.isEmpty()) PENDING.remove(network.getNetworkID());
    }
}
