package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.api.stacks.AEKeyType;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
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

        assertThat(items.insertItem(0, iron, true)).isEmpty();
        assertThat(items.getStackInSlot(0)).isEmpty();
        assertThat(items.insertItem(0, iron, false)).isEmpty();

        assertThat(items.getStackInSlot(0)).isEqualTo(iron);
        assertThat(((NativeStackSync.Item) items).amount(0)).isEqualTo(4L);
    }

    @Test
    void fluid_handler_simulates_and_executes_against_ae2_inventory() {
        GenericStackInv inventory = inventory(Set.of(AEKeyType.fluids()));
        IFluidHandler fluids = AE2NativeAdapters.fluids(inventory);
        FluidStack water = new FluidStack(Fluids.WATER, 1_000);

        assertThat(fluids.fill(water, IFluidHandler.FluidAction.SIMULATE)).isEqualTo(1_000);
        assertThat(fluids.getFluidInTank(0)).isEmpty();
        assertThat(fluids.fill(water, IFluidHandler.FluidAction.EXECUTE)).isEqualTo(1_000);

        assertThat(fluids.getFluidInTank(0)).isEqualTo(water);
        assertThat(((NativeStackSync.Fluid) fluids).amount(0)).isEqualTo(1_000L);
    }

    @Test
    void handler_rejects_resources_outside_the_inventory_type_filter() {
        IItemHandler items = AE2NativeAdapters.items(inventory(Set.of(AEKeyType.fluids())));
        IFluidHandler fluids = AE2NativeAdapters.fluids(inventory(Set.of(AEKeyType.items())));

        assertThat(items.insertItem(0, new ItemStack(Items.IRON_INGOT, 1), true))
                .isEqualTo(new ItemStack(Items.IRON_INGOT, 1));
        assertThat(fluids.fill(new FluidStack(Fluids.WATER, 1_000), IFluidHandler.FluidAction.SIMULATE)).isZero();
    }

    private static GenericStackInv inventory(Set<AEKeyType> types) {
        return new GenericStackInv(types, null, GenericStackInv.Mode.STORAGE, 1);
    }
}
