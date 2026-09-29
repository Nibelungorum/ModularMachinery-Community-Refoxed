package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PatternInterfaceAdapterTest {
    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void requestInventoryKeepsAeKeyIdentityAndLongAmount() {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        KeyCounter first = new KeyCounter();
        KeyCounter second = new KeyCounter();
        first.add(iron, (long) Integer.MAX_VALUE + 1L);
        second.add(iron, 17L);

        GenericStackInv request = AE2NativeAdapters.requestInventory(new KeyCounter[]{first, second},
                AEKeyType.items());

        assertThat(request.getKey(0)).isEqualTo(iron);
        assertThat(request.getAmount(0)).isEqualTo((long) Integer.MAX_VALUE + 1L);
        assertThat(request.getKey(1)).isEqualTo(iron);
        assertThat(request.getAmount(1)).isEqualTo(17L);
    }

    @Test
    void cumulativePatternReturnsShareOneSimulatedCapacity() {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        GenericStackInv returns = new GenericStackInv(Set.of(AEKeyType.items()), null,
                GenericStackInv.Mode.STORAGE, 1);
        returns.setCapacity(AEKeyType.items(), 64L);
        GenericStackInv simulated = AE2NativeAdapters.copyForSimulation(returns);
        GenericStackInv first = request(iron, 40L);
        GenericStackInv second = request(iron, 40L);

        assertThat(AE2NativeAdapters.returnRemaining(first, simulated, AEKeyType.items(), IActionSource.empty()))
                .isTrue();
        assertThat(AE2NativeAdapters.returnRemaining(second, simulated, AEKeyType.items(), IActionSource.empty()))
                .isFalse();

        assertThat(simulated.getStack(0)).isEqualTo(new GenericStack(iron, 40L));
        assertThat(returns.isEmpty()).isTrue();
    }

    @Test
    void simulatedReturnCheckDoesNotMutateNativeInventory() {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        GenericStackInv returns = new GenericStackInv(Set.of(AEKeyType.items()), null,
                GenericStackInv.Mode.STORAGE, 1);
        returns.setCapacity(AEKeyType.items(), 64L);

        assertThat(AE2NativeAdapters.canReturn(request(iron, 64L), returns, AEKeyType.items(),
                IActionSource.empty())).isTrue();
        assertThat(returns.isEmpty()).isTrue();
    }

    private static GenericStackInv request(AEItemKey key, long amount) {
        KeyCounter counter = new KeyCounter();
        counter.add(key, amount);
        return AE2NativeAdapters.requestInventory(new KeyCounter[]{counter}, AEKeyType.items());
    }
}
