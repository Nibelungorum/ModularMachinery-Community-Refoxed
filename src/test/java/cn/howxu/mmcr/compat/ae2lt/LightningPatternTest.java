package cn.howxu.mmcr.compat.ae2lt;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.compat.ae2lt.loaded.LoadedLightningBridge;
import cn.howxu.mmcr.compat.appliedenergistics2.AE2TestFixtures;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static cn.howxu.mmcr.api.compat.ae2lt.LightningTier.EXTREME_HIGH_VOLTAGE;
import static cn.howxu.mmcr.api.compat.ae2lt.LightningTier.HIGH_VOLTAGE;
import static org.assertj.core.api.Assertions.assertThat;

/** Native shared request inventories retain one owner for all lightning tiers and resource families.
 * @author howxu <dev@howxu.cn>
 */
class LightningPatternTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        AE2TestFixtures.ensureAE2KeyTypesInitialized();
    }

    @Test
    void mixedLaneQuotasDebitOneSharedOwnerWithoutClearingCallerCounters() {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var water = AEFluidKey.of(Fluids.WATER);
        var high = LoadedLightningBridge.key(HIGH_VOLTAGE);
        var extreme = LoadedLightningBridge.key(EXTREME_HIGH_VOLTAGE);
        KeyCounter holder = new KeyCounter();
        holder.add(iron, 12L);
        holder.add(water, 4_000L);
        holder.add(high, 8_589_934_588L);
        holder.add(extreme, 22L);
        var owner = AE2NativeAdapters.requestInventory(new KeyCounter[]{holder});
        var first = AE2NativeAdapters.requestInventoryView(owner, 1L, 2L);
        var second = AE2NativeAdapters.requestInventoryView(owner, 1L, 2L);
        for (var lane : new GenericStackInv[]{first, second}) {
            extract(lane, iron, 1L);
            extract(lane, water, 500L);
            extract(lane, high, 2_147_483_647L);
            extract(lane, extreme, 3L);
        }
        assertThat(owner.getAvailableStacks().get(iron)).isEqualTo(10L);
        assertThat(owner.getAvailableStacks().get(water)).isEqualTo(3_000L);
        assertThat(owner.getAvailableStacks().get(high)).isEqualTo(4_294_967_294L);
        assertThat(owner.getAvailableStacks().get(extreme)).isEqualTo(16L);
        assertThat(holder.get(high)).isEqualTo(8_589_934_588L);
        assertThat(holder.get(extreme)).isEqualTo(22L);
    }

    @Test
    void partialLightningSimulationPreservesBothExactLongBalances() {
        var high = LoadedLightningBridge.key(HIGH_VOLTAGE);
        var extreme = LoadedLightningBridge.key(EXTREME_HIGH_VOLTAGE);
        KeyCounter holder = new KeyCounter();
        holder.add(high, 4_294_967_294L);
        holder.add(extreme, 8_589_934_588L);
        var owner = AE2NativeAdapters.requestInventory(new KeyCounter[]{holder});
        int highSlot = slot(owner, high);
        assertThat(owner.extract(highSlot, high, 2_147_483_647L, Actionable.SIMULATE)).isEqualTo(2_147_483_647L);
        assertThat(owner.getAmount(highSlot)).isEqualTo(4_294_967_294L);
        assertThat(owner.getAmount(slot(owner, extreme))).isEqualTo(8_589_934_588L);
        extract(owner, high, 2_147_483_647L);
        assertThat(owner.getAmount(highSlot)).isEqualTo(2_147_483_647L);
        assertThat(owner.getAmount(slot(owner, extreme))).isEqualTo(8_589_934_588L);
        assertThat(holder.get(high)).isEqualTo(4_294_967_294L);
    }

    @Test
    void lightningOutputPatternMatchesOnlySameTierAndAmount() throws Exception {
        var method = MachineControllerBlockEntity.class.getDeclaredMethod("samePatternOutput",
                MachineOutput.class, MachineOutput.class);
        method.setAccessible(true);
        var expected = new LightningOutput(HIGH_VOLTAGE, 17L, 1F);
        assertThat(method.invoke(null, expected, new LightningOutput(HIGH_VOLTAGE, 17L, 1F))).isEqualTo(true);
        assertThat(method.invoke(null, expected, new LightningOutput(EXTREME_HIGH_VOLTAGE, 17L, 1F))).isEqualTo(false);
        assertThat(method.invoke(null, expected, new LightningOutput(HIGH_VOLTAGE, 18L, 1F))).isEqualTo(false);
        var bridge = new LoadedLightningBridge();
        var high = LoadedLightningBridge.key(HIGH_VOLTAGE);
        assertThat(bridge.patternOutput(high, 17L)).contains(expected);
        assertThat(bridge.patternOutput(high, 2_147_483_648L)).isEmpty();
        assertThat(bridge.patternOutput(AEItemKey.of(Items.IRON_INGOT), 17L)).isEmpty();
    }

    private static void extract(GenericStackInv inventory, AEKey key, long amount) {
        assertThat(inventory.extract(slot(inventory, key), key, amount, Actionable.MODULATE)).isEqualTo(amount);
    }

    private static int slot(GenericStackInv inventory, AEKey key) {
        for (int slot = 0; slot < inventory.size(); slot++) if (key.equals(inventory.getKey(slot))) return slot;
        throw new AssertionError("Missing request resource: " + key);
    }
}
