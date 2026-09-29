package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.api.capability.facet.PersistenceFacet;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests custom facet state without built-in item, fluid, or energy serialization.
 *
 * @author howxu <dev@howxu.cn>
 */
class CapabilityPersistenceTest {
    @Test
    void custom_resource_scalar_and_presentation_state_round_trip_in_named_children() {
        StateFacet source = new StateFacet("custom", 12, 34L, "ready");
        HolderLookup.Provider registries = HolderLookup.Provider.create(Stream.empty());
        CompoundTag output = new CompoundTag();
        CompoundTag state = new CompoundTag();

        source.save(state, registries);
        output.put(source.stateKey(), state);
        StateFacet restored = new StateFacet("custom", 0, 0L, "");
        restored.load(output.getCompound(restored.stateKey()), registries);

        assertThat(restored.resource).isEqualTo(12);
        assertThat(restored.scalar).isEqualTo(34L);
        assertThat(restored.presentation).isEqualTo("ready");
    }

    @Test
    void custom_resource_scalar_and_presentation_state_round_trip_over_sync_codec() {
        StateFacet source = new StateFacet("custom", 12, 34L, "ready");
        StateFacet restored = new StateFacet("custom", 0, 0L, "");
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);

        source.encode(buffer);
        restored.decode(buffer);

        assertThat(restored.resource).isEqualTo(12);
        assertThat(restored.scalar).isEqualTo(34L);
        assertThat(restored.presentation).isEqualTo("ready");
    }

    private static final class StateFacet implements PersistenceFacet, SyncFacet {
        private final String key;
        private int resource;
        private long scalar;
        private String presentation;

        private StateFacet(String key, int resource, long scalar, String presentation) {
            this.key = key;
            this.resource = resource;
            this.scalar = scalar;
            this.presentation = presentation;
        }

        @Override public String stateKey() { return key; }
        @Override public void save(CompoundTag output, HolderLookup.Provider registries) {
            output.putInt("resource", resource);
            output.putLong("scalar", scalar);
            output.putString("presentation", presentation);
        }
        @Override public void load(CompoundTag input, HolderLookup.Provider registries) {
            resource = input.getInt("resource");
            scalar = input.getLong("scalar");
            presentation = input.getString("presentation");
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(resource);
            buffer.writeLong(scalar);
            buffer.writeUtf(presentation);
        }
        @Override public void decode(RegistryFriendlyByteBuf buffer) {
            resource = buffer.readVarInt();
            scalar = buffer.readLong();
            presentation = buffer.readUtf();
        }
    }
}
