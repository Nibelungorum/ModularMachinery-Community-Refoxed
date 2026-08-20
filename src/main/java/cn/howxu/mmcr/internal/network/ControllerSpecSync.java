package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author howxu <dev@howxu.cn>
 */
public final class ControllerSpecSync {
    private ControllerSpecSync() {
    }

    public static Map<Identifier, MachineControllerSpec> createSnapshot() {
        Map<Identifier, MachineControllerSpec> snapshot = new LinkedHashMap<>();
        MachineRegistry.effectiveSnapshot().forEach((id, machine) -> {
            MachineControllerSpec spec = machine.controller();
            if (spec != null) {
                validate(id, spec);
                snapshot.put(id, spec);
            }
        });
        return Map.copyOf(snapshot);
    }

    public static Map<Identifier, MachineAppearanceSpec> createAppearanceSnapshot() {
        Map<Identifier, MachineAppearanceSpec> snapshot = new LinkedHashMap<>();
        MachineRegistry.effectiveSnapshot().forEach((id, machine) -> {
            if (machine.controller() != null) {
                snapshot.put(id, machine.appearance());
            }
        });
        return Map.copyOf(snapshot);
    }

    private static void validate(Identifier machineId, MachineControllerSpec spec) {
        if (spec == null || !MachineControllerSpec.defaultsFor(machineId).id().equals(spec.id())) {
            throw new IllegalStateException("Controller spec key does not match spec id: " + machineId);
        }
    }

    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new PktControllerSpecsPayload(createSnapshot()));
        PacketDistributor.sendToPlayer(player, new PktMachineAppearancePayload(createAppearanceSnapshot()));
    }

    public static void sendToAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTo(player);
        }
    }
}
