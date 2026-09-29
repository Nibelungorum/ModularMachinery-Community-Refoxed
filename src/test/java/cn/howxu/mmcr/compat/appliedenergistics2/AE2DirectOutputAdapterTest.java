package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AE2DirectOutputAdapterTest {
    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void directOutputAdapterSimulatesBeforeNetworkModulation() {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        RecordingStorage network = new RecordingStorage(iron, 8L);
        GenericStackInv cache = new GenericStackInv(Set.of(AEKeyType.items()), null,
                GenericStackInv.Mode.STORAGE, 1);
        IItemHandler handler = AE2NativeAdapters.outputItems(cache, () -> network, IActionSource.empty(), () -> {
        });

        assertThat(handler.insertItem(0, iron.toStack(8), false)).isEmpty();

        assertThat(network.amount).isEqualTo(8L);
        assertThat(cache.isEmpty()).isTrue();
        assertThat(network.keys).containsExactly(iron, iron);
        assertThat(network.modes).containsExactly(Actionable.SIMULATE, Actionable.MODULATE);
    }

    private static final class RecordingStorage implements MEStorage {
        private final AEKey acceptedKey;
        private final long capacity;
        private final List<AEKey> keys = new ArrayList<>();
        private final List<Actionable> modes = new ArrayList<>();
        private long amount;

        private RecordingStorage(AEKey acceptedKey, long capacity) {
            this.acceptedKey = acceptedKey;
            this.capacity = capacity;
        }

        @Override
        public long insert(AEKey key, long requested, Actionable mode, IActionSource source) {
            keys.add(key);
            modes.add(mode);
            long inserted = acceptedKey.equals(key) ? Math.min(requested, capacity - amount) : 0L;
            if (mode == Actionable.MODULATE) amount += inserted;
            return inserted;
        }

        @Override
        public Component getDescription() {
            return Component.literal("test");
        }
    }
}
