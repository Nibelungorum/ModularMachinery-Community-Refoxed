package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
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
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
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

    @Test
    void outputAdaptersCacheModulateShortfallAfterSuccessfulSimulation() {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        GenericStackInv itemCache = inventory();
        ShortfallStorage itemNetwork = new ShortfallStorage(iron, Long.MAX_VALUE, 3L);
        IItemHandler items = AE2NativeAdapters.outputItems(itemCache, () -> itemNetwork,
                IActionSource.empty(), () -> {});

        assertThat(items.insertItem(0, iron.toStack(8), false)).isEmpty();
        assertThat(itemNetwork.amount).isEqualTo(3L);
        assertThat(itemCache.getAmount(0)).isEqualTo(5L);

        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        GenericStackInv fluidCache = fluidInventory();
        ShortfallStorage fluidNetwork = new ShortfallStorage(water, Long.MAX_VALUE, 400L);
        IFluidHandler fluids = AE2NativeAdapters.outputFluids(fluidCache, () -> fluidNetwork,
                IActionSource.empty(), () -> {});

        assertThat(fluids.fill(new FluidStack(Fluids.WATER, 1_000), IFluidHandler.FluidAction.EXECUTE))
                .isEqualTo(1_000);
        assertThat(fluidNetwork.amount).isEqualTo(400L);
        assertThat(fluidCache.getAmount(0)).isEqualTo(600L);
    }

    @Test
    void flushRetainsCacheOnFailedCommitAndHonorsOperationLimit() {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        GenericStackInv cache = inventory(2, 64L);
        cache.setStack(0, new appeng.api.stacks.GenericStack(iron, 40L));
        cache.setStack(1, new appeng.api.stacks.GenericStack(iron, 40L));
        ShortfallStorage failed = new ShortfallStorage(iron, Long.MAX_VALUE, 0L);

        assertThat(AE2NativeAdapters.flush(cache, () -> failed, IActionSource.empty(),
                AEKeyType.items(), 50L)).isZero();
        assertThat(cache.getAmount(0)).isEqualTo(40L);
        assertThat(cache.getAmount(1)).isEqualTo(40L);

        RecordingStorage network = new RecordingStorage(iron, Long.MAX_VALUE);
        assertThat(AE2NativeAdapters.flush(cache, () -> network, IActionSource.empty(),
                AEKeyType.items(), 50L)).isEqualTo(50L);
        assertThat(cache.getAmount(0)).isZero();
        assertThat(cache.getAmount(1)).isEqualTo(30L);
    }

    @Test
    void flushUsesCommittedAmountAndSupportsNearMaximumLongAmounts() {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        long amount = Long.MAX_VALUE - 1L;
        GenericStackInv cache = inventory(1, Long.MAX_VALUE);
        cache.setStack(0, new appeng.api.stacks.GenericStack(iron, amount));
        ShortfallStorage network = new ShortfallStorage(iron, Long.MAX_VALUE, amount - 1L);

        assertThat(AE2NativeAdapters.flush(cache, () -> network, IActionSource.empty(),
                AEKeyType.items(), amount)).isEqualTo(amount - 1L);
        assertThat(cache.getAmount(0)).isEqualTo(1L);
    }

    private static GenericStackInv inventory() {
        return inventory(1, 64L);
    }

    private static GenericStackInv inventory(int slots, long capacity) {
        GenericStackInv inventory = new GenericStackInv(Set.of(AEKeyType.items()), null,
                GenericStackInv.Mode.STORAGE, slots);
        inventory.setCapacity(AEKeyType.items(), capacity);
        return inventory;
    }

    private static GenericStackInv fluidInventory() {
        GenericStackInv inventory = new GenericStackInv(Set.of(AEKeyType.fluids()), null,
                GenericStackInv.Mode.STORAGE, 1);
        inventory.setCapacity(AEKeyType.fluids(), 10_000L);
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

    private static final class ShortfallStorage implements MEStorage {
        private final AEKey acceptedKey;
        private final long simulated;
        private final long modulated;
        private long amount;

        private ShortfallStorage(AEKey acceptedKey, long simulated, long modulated) {
            this.acceptedKey = acceptedKey;
            this.simulated = simulated;
            this.modulated = modulated;
        }

        @Override
        public long insert(AEKey key, long requested, Actionable mode, IActionSource source) {
            if (!acceptedKey.equals(key)) return 0L;
            long inserted = Math.min(requested, mode == Actionable.SIMULATE ? simulated : modulated);
            if (mode == Actionable.MODULATE) amount += inserted;
            return inserted;
        }

        @Override
        public Component getDescription() {
            return Component.literal("shortfall test");
        }
    }
}
