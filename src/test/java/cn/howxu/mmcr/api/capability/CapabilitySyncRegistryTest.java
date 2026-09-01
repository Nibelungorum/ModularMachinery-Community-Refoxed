package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.sync.CapabilitySyncEntry;
import cn.howxu.mmcr.api.capability.sync.CapabilitySyncRegistry;
import cn.howxu.mmcr.util.IOType;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests typed capability sync dispatch without built-in resource assumptions.
 *
 * @author howxu <dev@howxu.cn>
 */
class CapabilitySyncRegistryTest {
    @Test
    void routes_same_type_entries_by_snapshot_occurrence_index() {
        TestCapability first = new TestCapability(3);
        TestCapability second = new TestCapability(7);
        CapabilitySnapshot source = new CapabilitySnapshot(List.of(first, second));
        RegistryFriendlyByteBuf buffer = buffer();

        List<CapabilitySyncEntry> entries = CapabilitySyncRegistry.encode(source, buffer);
        first.value = 0;
        second.value = 0;
        CapabilitySnapshot target = new CapabilitySnapshot(List.of(first, second));
        for (CapabilitySyncEntry entry : entries) CapabilitySyncRegistry.decode(target, entry, buffer);

        assertThat(entries).extracting(CapabilitySyncEntry::capabilityIndex).containsExactly(0, 1);
        assertThat(first.value).isEqualTo(3);
        assertThat(second.value).isEqualTo(7);
    }

    @Test
    void encodes_empty_and_changed_state_without_reusing_the_previous_payload() {
        TestCapability capability = new TestCapability(0);
        CapabilitySnapshot snapshot = new CapabilitySnapshot(List.of(capability));
        List<CapabilitySyncEntry> empty = CapabilitySyncRegistry.encode(snapshot, buffer());
        capability.value = 9;
        List<CapabilitySyncEntry> changed = CapabilitySyncRegistry.encode(snapshot, buffer());

        assertThat(empty).hasSize(1);
        assertThat(changed.getFirst().payload()).isNotEqualTo(empty.getFirst().payload());
    }

    @Test
    void rejects_unknown_type_and_index() {
        CapabilitySnapshot snapshot = new CapabilitySnapshot(List.of(new TestCapability(1)));

        assertThatThrownBy(() -> CapabilitySyncRegistry.decode(snapshot,
                new CapabilitySyncEntry(Identifier.fromNamespaceAndPath("test", "unknown"), 0, new byte[0]), buffer()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CapabilitySyncRegistry.decode(snapshot,
                new CapabilitySyncEntry(Identifier.fromNamespaceAndPath("test", "scalar"), 1, new byte[0]), buffer()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    private static final class TestCapability implements MachineCapability, SyncFacet {
        private int value;

        private TestCapability(int value) {
            this.value = value;
        }

        @Override public CapabilityType type() { return new CapabilityType(Identifier.fromNamespaceAndPath("test", "scalar")); }
        @Override public IOType ioType() { return IOType.INPUT; }
        @Override public CapabilityView view() {
            return new CapabilityView() {
                @Override public CapabilityType type() { return TestCapability.this.type(); }
                @Override public IOType ioType() { return TestCapability.this.ioType(); }
                @Override public Set<Class<? extends CapabilityFacet>> facets() { return Set.of(SyncFacet.class); }
            };
        }
        @Override public CapabilityOperation prepare(CapabilityRequest request) { return ignored -> CapabilityResult.successful(); }
        @Override public void encode(RegistryFriendlyByteBuf buffer) { buffer.writeVarInt(value); }
        @Override public void decode(RegistryFriendlyByteBuf buffer) { value = buffer.readVarInt(); }
    }
}
