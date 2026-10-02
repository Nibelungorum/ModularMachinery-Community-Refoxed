package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.internal.capability.NativeStackSync;
import cn.howxu.mmcr.test.TestBootstrap;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AE2InventoryAdapterTest {
    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void item_handler_simulates_and_executes_against_ae2_inventory() {
        GenericStackInv inventory = inventory(Set.of(AEKeyType.items()));
        IItemHandler items = AE2NativeAdapters.items(inventory);
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 4);

        assertThat(items.insertItem(0, iron, true).isEmpty()).isTrue();
        assertThat(items.getStackInSlot(0).isEmpty()).isTrue();
        assertThat(items.insertItem(0, iron, false).isEmpty()).isTrue();

        ItemStack stored = items.getStackInSlot(0);
        assertThat(ItemStack.isSameItemSameComponents(stored, iron)).isTrue();
        assertThat(stored.getCount()).isEqualTo(iron.getCount());
        assertThat(((NativeStackSync.Item) items).amount(0)).isEqualTo(4L);
    }

    @Test
    void fluid_handler_simulates_and_executes_against_ae2_inventory() {
        GenericStackInv inventory = inventory(Set.of(AEKeyType.fluids()));
        IFluidHandler fluids = AE2NativeAdapters.fluids(inventory);
        FluidStack water = new FluidStack(Fluids.WATER, 1_000);

        assertThat(fluids.fill(water, IFluidHandler.FluidAction.SIMULATE)).isEqualTo(1_000);
        assertThat(fluids.getFluidInTank(0).isEmpty()).isTrue();
        assertThat(fluids.fill(water, IFluidHandler.FluidAction.EXECUTE)).isEqualTo(1_000);

        FluidStack stored = fluids.getFluidInTank(0);
        assertThat(FluidStack.isSameFluidSameComponents(stored, water)).isTrue();
        assertThat(stored.getAmount()).isEqualTo(water.getAmount());
        assertThat(((NativeStackSync.Fluid) fluids).amount(0)).isEqualTo(1_000L);
    }

    @Test
    void handler_rejects_resources_outside_the_inventory_type_filter() {
        IItemHandler items = AE2NativeAdapters.items(inventory(Set.of(AEKeyType.fluids())));
        IFluidHandler fluids = AE2NativeAdapters.fluids(inventory(Set.of(AEKeyType.items())));

        ItemStack rejected = items.insertItem(0, new ItemStack(Items.IRON_INGOT, 1), true);
        assertThat(ItemStack.isSameItemSameComponents(rejected, new ItemStack(Items.IRON_INGOT))).isTrue();
        assertThat(rejected.getCount()).isEqualTo(1);
        assertThat(fluids.fill(new FluidStack(Fluids.WATER, 1_000), IFluidHandler.FluidAction.SIMULATE)).isZero();
    }

    @Test
    void native_sync_exposes_complete_long_item_and_fluid_amounts() {
        long amount = (long) Integer.MAX_VALUE + 42L;
        IItemHandler items = AE2NativeAdapters.items(longInventory(Set.of(AEKeyType.items())));
        IFluidHandler fluids = AE2NativeAdapters.fluids(longInventory(Set.of(AEKeyType.fluids())));

        ((NativeStackSync.Item) items).setContents(0, new ItemStack(Items.IRON_INGOT), amount);
        ((NativeStackSync.Fluid) fluids).setContents(0, new FluidStack(Fluids.WATER, 1), amount);

        assertThat(((NativeStackSync.Item) items).amount(0)).isEqualTo(amount);
        assertThat(((NativeStackSync.Fluid) fluids).amount(0)).isEqualTo(amount);
        assertThat(items.getStackInSlot(0).getCount()).isEqualTo(Integer.MAX_VALUE);
        assertThat(fluids.getFluidInTank(0).getAmount()).isEqualTo(Integer.MAX_VALUE);

        PlanningReservations reservations = new PlanningReservations();
        assertThat(reservations.reserveItemExtract(items, 0, new ItemStack(Items.IRON_INGOT), amount)).isTrue();
        assertThat(reservations.reserveFluidExtract(fluids, 0, new FluidStack(Fluids.WATER, 1), amount)).isTrue();
        assertThat(reservations.itemAmount(items, 0)).isZero();
        assertThat(reservations.fluidAmount(fluids, 0)).isZero();
    }

    @Test
    void empty_sync_projections_preserve_other_resource_families() {
        GenericStackInv inventory = inventory(Set.of(AEKeyType.items(), AEKeyType.fluids()));
        NativeStackSync.Item items = (NativeStackSync.Item) AE2NativeAdapters.items(inventory);
        NativeStackSync.Fluid fluids = (NativeStackSync.Fluid) AE2NativeAdapters.fluids(inventory);
        fluids.setContents(0, new FluidStack(Fluids.WATER, 1), 1_000L);
        items.setContents(0, ItemStack.EMPTY, 0L);
        assertThat(fluids.amount(0)).isEqualTo(1_000L);
        assertThat(items.isSyncCapacityValid(0, new ItemStack(Items.IRON_INGOT),
                inventory.getMaxAmount(AEItemKey.of(Items.IRON_INGOT)))).isTrue();
        items.setContents(0, new ItemStack(Items.IRON_INGOT), 4L);
        fluids.setContents(0, FluidStack.EMPTY, 0L);
        assertThat(items.amount(0)).isEqualTo(4L);
        assertThat(fluids.isSyncCapacityValid(0, new FluidStack(Fluids.WATER, 1),
                inventory.getMaxAmount(AEFluidKey.of(Fluids.WATER)))).isTrue();
        items.setContents(0, ItemStack.EMPTY, 0L);
        assertThat(inventory.isEmpty()).isTrue();
    }

    private static GenericStackInv inventory(Set<AEKeyType> types) {
        return new GenericStackInv(types, null, GenericStackInv.Mode.STORAGE, 1);
    }

    private static GenericStackInv longInventory(Set<AEKeyType> types) {
        return new GenericStackInv(types, null, GenericStackInv.Mode.STORAGE, 1) {
            @Override
            public long getMaxAmount(AEKey key) {
                return getCapacity(key.getType());
            }
        };
    }
}
