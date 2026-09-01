package cn.howxu.mmcr.api.capability.sync;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Encodes and dispatches typed capability sync entries.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class CapabilitySyncRegistry {
    public static final int MAX_ENTRIES = 1024;

    private CapabilitySyncRegistry() {
    }

    public static List<CapabilitySyncEntry> encode(CapabilitySnapshot snapshot, RegistryFriendlyByteBuf buffer) {
        List<CapabilitySyncEntry> entries = new ArrayList<>();
        for (int capabilityIndex = 0; capabilityIndex < snapshot.capabilities().size(); capabilityIndex++) {
            MachineCapability capability = snapshot.capabilities().get(capabilityIndex);
            int index = capabilityIndex;
            capability.facet(SyncFacet.class).ifPresent(facet -> {
                RegistryFriendlyByteBuf payload = new RegistryFriendlyByteBuf(Unpooled.buffer(), buffer.registryAccess());
                facet.encode(payload);
                byte[] bytes = new byte[payload.readableBytes()];
                payload.readBytes(bytes);
                entries.add(new CapabilitySyncEntry(capability.type().id(), index, bytes));
            });
        }
        if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many capability sync entries");
        return List.copyOf(entries);
    }

    public static void decode(CapabilitySnapshot snapshot, CapabilitySyncEntry entry, RegistryFriendlyByteBuf buffer) {
        if (entry.capabilityIndex() >= snapshot.capabilities().size()) {
            throw new IllegalArgumentException("Unknown capability sync index");
        }
        MachineCapability capability = snapshot.capabilities().get(entry.capabilityIndex());
        Identifier expectedType = capability.type().id();
        if (!expectedType.equals(entry.typeId())) throw new IllegalArgumentException("Unknown capability sync type");
        SyncFacet facet = capability.facet(SyncFacet.class)
                .orElseThrow(() -> new IllegalArgumentException("Capability does not support sync"));
        facet.decode(new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(entry.payload()), buffer.registryAccess()));
    }

}
