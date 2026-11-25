package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** @author HowXu {@literal <dev@howxu.cn>} */
class NativeCpuBulkBatchTest {
    @BeforeAll static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    private final AEKey iron = AEItemKey.of(Items.IRON_INGOT), copper = AEItemKey.of(Items.COPPER_INGOT);
    private final ListCraftingInventory cpu = new ListCraftingInventory(key -> {});

    @Test void rejectionRestoresOnlyExtrasAndNextAttemptCanUseTheSameFirstCopy() {
        var slots = new KeyCounter[]{count(iron, 2), count(iron, 1)};
        slots[1].add(copper, 1);
        var output = count(copper, 2);
        var container = count(iron, 1);
        cpu.list.add(iron, 10);
        cpu.list.add(copper, 15);
        for (boolean accept : new boolean[]{false, true}) {
            var copies = NativeCpuBulkBatch.push(cpu, slots, 12, 4, request -> request, output, container,
                    key -> 0, cap -> { assertThat(cap).isEqualTo(4); return cap; }, batch -> {
                        assertThat(batch[0].get(iron)).isEqualTo(8);
                        assertThat(batch[1].get(iron)).isEqualTo(4);
                        assertThat(batch[1].get(copper)).isEqualTo(4);
                        assertThat(cpu.list.get(iron)).isEqualTo(1);
                        batch[0].clear();
                        return accept;
                    });
            if (!accept) {
                assertThat(copies).isZero();
                assertThat(cpu.list.get(iron)).isEqualTo(10);
                assertThat(cpu.list.get(copper)).isEqualTo(15);
                assertThat(slots[0].get(iron)).isEqualTo(2);
                assertThat(output.get(copper)).isEqualTo(2);
                assertThat(container.get(iron)).isOne();
            } else {
                assertThat(copies).isEqualTo(4);
                assertThat(cpu.list.get(copper)).isEqualTo(12);
                assertThat(output.get(copper)).isEqualTo(8);
                assertThat(container.get(iron)).isEqualTo(4);
            }
        }
    }

    @Test void shorterActualExtractionRefundsExactlyWhatWasRemoved() {
        var inventory = new ListCraftingInventory(key -> {}) {
            @Override public long extract(AEKey key, long amount, Actionable mode) {
                return super.extract(key, mode == Actionable.MODULATE ? amount - 1 : amount, mode);
            }
        };
        inventory.list.add(iron, 99);
        var slots = new KeyCounter[]{count(iron, 1)};
        var copies = NativeCpuBulkBatch.push(inventory, slots, 100, 1, request -> request, count(copper, 1),
                new KeyCounter(), key -> 0, cap -> cap, batch -> { throw new AssertionError("Incomplete batch"); });
        assertThat(copies).isZero();
        assertThat(inventory.list.get(iron)).isEqualTo(99);
        assertThat(slots[0].get(iron)).isOne();
    }

    @Test void existingWaitingAndContainerOfTheSameKeyShareOneLongLimit() {
        cpu.list.add(iron, Long.MAX_VALUE);
        var output = count(copper, 3);
        var container = count(copper, 2);
        var copies = NativeCpuBulkBatch.push(cpu, new KeyCounter[]{count(iron, 1)}, Long.MAX_VALUE,
                1, request -> request, output, container, key -> Long.MAX_VALUE - 16, cap -> cap,
                batch -> batch[0].get(iron) == 3);
        assertThat(copies).isEqualTo(3);
        assertThat(output.get(copper) + container.get(copper)).isEqualTo(15);
        assertThat(cpu.list.get(iron)).isEqualTo(Long.MAX_VALUE - 2);
    }

    @Test void tinyChargesDoNotTurnTheSingleCopyToleranceIntoFreeExtraCopies() {
        cpu.list.add(iron, 99);
        var slots = new KeyCounter[]{count(iron, 1)};
        long copies = NativeCpuBulkBatch.push(cpu, slots, 100, 0.004, request -> 0.006, count(copper, 1),
                new KeyCounter(), key -> 0, cap -> cap, batch -> batch == slots);
        assertThat(copies).isOne();
        assertThat(cpu.list.get(iron)).isEqualTo(99);
    }

    @Test void noHeadroomForEvenOneExpectedOutputDoesNotTransferInputs() {
        cpu.list.add(iron, 1);
        var slots = new KeyCounter[]{count(iron, 1)};
        long copies = NativeCpuBulkBatch.push(cpu, slots, 2, 1, request -> request, count(copper, 1),
                new KeyCounter(), key -> Long.MAX_VALUE, cap -> cap, batch -> { throw new AssertionError("No room to wait"); });
        assertThat(copies).isZero();
        assertThat(cpu.list.get(iron)).isOne();
        assertThat(slots[0].get(iron)).isOne();
    }

    @Test void billionsOfIdenticalCopiesUseOneActualWithdrawalOfTheExactVariant() {
        var named = new ItemStack(Items.IRON_INGOT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("selected variant"));
        var selected = AEItemKey.of(named);
        long cap = (long) Integer.MAX_VALUE * 2;
        int[] withdrawals = {0};
        var inventory = new ListCraftingInventory(key -> withdrawals[0]++);
        inventory.list.add(selected, cap - 1);
        inventory.list.add(iron, 200);
        var output = count(copper, 2);
        var copies = NativeCpuBulkBatch.push(inventory, new KeyCounter[]{count(selected, 1)}, Long.MAX_VALUE, 1,
                request -> request, output, new KeyCounter(), key -> 0, n -> n,
                batch -> batch[0].get(selected) == cap && batch[0].get(iron) == 0);
        assertThat(copies).isEqualTo(cap);
        assertThat(withdrawals[0]).isOne();
        assertThat(inventory.list.get(selected)).isZero();
        assertThat(inventory.list.get(iron)).isEqualTo(200);
        assertThat(output.get(copper)).isEqualTo(2 * cap);
    }

    private static KeyCounter count(AEKey key, long n) {
        var counter = new KeyCounter();
        counter.add(key, n);
        return counter;
    }
}
