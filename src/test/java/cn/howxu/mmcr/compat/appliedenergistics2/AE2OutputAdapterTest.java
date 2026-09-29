package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.internal.capability.NativeStackSync;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AE2OutputAdapterTest {
    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void outputViewSimulatesThenSplitsModulatedInsertBetweenNetworkAndCache() {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        GenericStackInv cache = inventory();
        RecordingStorage network = new RecordingStorage(iron, 3L);
        AtomicInteger changes = new AtomicInteger();
        IItemHandler handler = AE2NativeAdapters.outputItems(cache, () -> network, IActionSource.empty(),
                changes::incrementAndGet);
        ItemStack stack = iron.toStack(8);

        assertThat(handler.insertItem(0, stack, true)).isEmpty();
        assertThat(network.amount()).isZero();
        assertThat(cache.isEmpty()).isTrue();

        assertThat(handler.insertItem(0, stack, false)).isEmpty();
        assertThat(network.amount()).isEqualTo(3L);
        assertThat(cache.getKey(0)).isEqualTo(iron);
        assertThat(cache.getAmount(0)).isEqualTo(5L);
        assertThat(((NativeStackSync.Item) handler).amount(0)).isEqualTo(5L);
        assertThat(changes).hasValue(1);
        assertThat(network.modes()).containsExactly(
                Actionable.SIMULATE, Actionable.SIMULATE, Actionable.MODULATE);
    }

    @Test
    void disconnectedOutputUsesNativeInventoryAndPreservesKeyIdentity() {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        GenericStackInv cache = inventory();
        IItemHandler handler = AE2NativeAdapters.outputItems(cache, () -> null, IActionSource.empty(), () -> {
        });

        assertThat(handler.insertItem(0, iron.toStack(7), false)).isEmpty();

        assertThat(cache.getKey(0)).isEqualTo(iron);
        assertThat(cache.getAmount(0)).isEqualTo(7L);
        assertThat(AEItemKey.of(handler.getStackInSlot(0))).isEqualTo(iron);
    }

    private static GenericStackInv inventory() {
        GenericStackInv inventory = new GenericStackInv(Set.of(AEKeyType.items()), null,
                GenericStackInv.Mode.STORAGE, 1);
        inventory.setCapacity(AEKeyType.items(), 64L);
        return inventory;
    }

    private static final class RecordingStorage implements MEStorage {
        private final AEKey acceptedKey;
        private final long capacity;
        private final List<Actionable> modes = new ArrayList<>();
        private long amount;

        private RecordingStorage(AEKey acceptedKey, long capacity) {
            this.acceptedKey = acceptedKey;
            this.capacity = capacity;
        }

        @Override
        public long insert(AEKey key, long requested, Actionable mode, IActionSource source) {
            modes.add(mode);
            if (!acceptedKey.equals(key)) return 0L;
            long inserted = Math.min(requested, capacity - amount);
            if (mode == Actionable.MODULATE) amount += inserted;
            return inserted;
        }

        @Override
        public Component getDescription() {
            return Component.literal("test");
        }

        private long amount() {
            return amount;
        }

        private List<Actionable> modes() {
            return List.copyOf(modes);
        }
    }
}
