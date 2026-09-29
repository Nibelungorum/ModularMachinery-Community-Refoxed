package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AE2NetworkAdapterTest {
    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void itemViewUsesAeKeyIdentityAndActionableModes() {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        FakeMEStorage network = new FakeMEStorage(Map.of(iron, 12L));
        IItemHandler handler = AE2NativeAdapters.networkItems(() -> network, () -> List.of(iron),
                IActionSource.empty());

        ItemStack simulated = handler.extractItem(0, 5, true);
        assertThat(AEItemKey.of(simulated)).isEqualTo(iron);
        assertThat(simulated.getCount()).isEqualTo(5);
        assertThat(network.amount(iron)).isEqualTo(12L);

        ItemStack extracted = handler.extractItem(0, 5, false);
        assertThat(AEItemKey.of(extracted)).isEqualTo(iron);
        assertThat(network.amount(iron)).isEqualTo(7L);
        assertThat(network.modes()).containsExactly(Actionable.SIMULATE, Actionable.MODULATE);
    }

    @Test
    void fluidViewSimulatesWithoutMutatingAndModulatesOnce() {
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        FakeMEStorage network = new FakeMEStorage(Map.of(water, 2_000L));
        IFluidHandler handler = AE2NativeAdapters.networkFluids(() -> network, () -> List.of(water),
                IActionSource.empty());
        FluidStack request = water.toStack(750);

        assertThat(handler.drain(request, IFluidHandler.FluidAction.SIMULATE).getAmount()).isEqualTo(750);
        assertThat(network.amount(water)).isEqualTo(2_000L);
        assertThat(handler.drain(request, IFluidHandler.FluidAction.EXECUTE).getAmount()).isEqualTo(750);
        assertThat(network.amount(water)).isEqualTo(1_250L);
    }

    @Test
    void nativeSyncRetainsLongAmountsBeyondVanillaStackSize() {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        IItemHandler handler = AE2NativeAdapters.networkItems(() -> new FakeMEStorage(Map.of()),
                () -> List.of(iron), IActionSource.empty());
        long amount = (long) Integer.MAX_VALUE + 42L;

        ((NativeStackSync.Item) handler).setContents(0, iron.toStack(1), amount);

        assertThat(((NativeStackSync.Item) handler).amount(0)).isEqualTo(amount);
        assertThat(AEItemKey.of(handler.getStackInSlot(0))).isEqualTo(iron);
    }

    private static final class FakeMEStorage implements MEStorage {
        private final Map<AEKey, Long> amounts = new HashMap<>();
        private final java.util.ArrayList<Actionable> modes = new java.util.ArrayList<>();

        private FakeMEStorage(Map<AEKey, Long> amounts) {
            this.amounts.putAll(amounts);
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            return 0L;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            modes.add(mode);
            long extracted = Math.min(amount, amounts.getOrDefault(key, 0L));
            if (mode == Actionable.MODULATE) amounts.merge(key, -extracted, Long::sum);
            return extracted;
        }

        @Override
        public Component getDescription() {
            return Component.literal("test");
        }

        private long amount(AEKey key) {
            return amounts.getOrDefault(key, 0L);
        }

        private List<Actionable> modes() {
            return List.copyOf(modes);
        }
    }
}
