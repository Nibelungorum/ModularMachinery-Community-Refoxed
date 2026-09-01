package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.api.capability.facet.PersistenceFacet;
import cn.howxu.mmcr.api.capability.facet.SyncFacet;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));

        source.save(output.child(source.stateKey()));
        StateFacet restored = new StateFacet("custom", 0, 0L, "");
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, HolderLookup.Provider.create(Stream.empty()),
                output.buildResult()).childOrEmpty(restored.stateKey()));

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
        @Override public void save(ValueOutput output) {
            output.putInt("resource", resource);
            output.putLong("scalar", scalar);
            output.putString("presentation", presentation);
        }
        @Override public void load(ValueInput input) {
            resource = input.getIntOr("resource", 0);
            scalar = input.getLongOr("scalar", 0L);
            presentation = input.getStringOr("presentation", "");
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
