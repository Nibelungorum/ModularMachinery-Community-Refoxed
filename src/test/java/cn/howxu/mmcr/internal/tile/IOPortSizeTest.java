package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class IOPortSizeTest {

    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void itemBusUsesKindSlotCount() {
        ItemBusBlockEntity tiny = itemBus("item_input_bus_tiny");
        ItemBusBlockEntity normal = itemBus("item_input_bus");
        ItemBusBlockEntity ludicrous = itemBus("item_output_bus_ludicrous");

        assertThat(tiny.getItemStackHandler(null).getSlots()).isEqualTo(1);
        assertThat(normal.getItemStackHandler(null).getSlots()).isEqualTo(6);
        assertThat(ludicrous.getItemStackHandler(null).getSlots()).isEqualTo(32);
    }

    @Test
    void fluidHatchUsesKindCapacity() {
        FluidHatchBlockEntity tiny = fluidHatch("fluid_input_hatch_tiny");
        FluidHatchBlockEntity normal = fluidHatch("fluid_input_hatch");
        FluidHatchBlockEntity vacuum = fluidHatch("fluid_output_hatch_vacuum");

        assertThat(tank(tiny).getCapacity()).isEqualTo(100);
        assertThat(tank(normal).getCapacity()).isEqualTo(1000);
        assertThat(tank(vacuum).getCapacity()).isEqualTo(32000);
    }

    @Test
    void energyHatchUsesKindCapacityAndTransfer() {
        EnergyHatchBlockEntity tiny = energyHatch("energy_input_hatch_tiny");
        EnergyHatchBlockEntity normal = energyHatch("energy_input_hatch");
        EnergyHatchBlockEntity ultimate = energyHatch("energy_output_hatch_ultimate");

        assertThat(storage(tiny).getMaxEnergyStored()).isEqualTo(2048);
        assertThat(storage(normal).getMaxEnergyStored()).isEqualTo(8192);
        assertThat(storage(ultimate).getMaxEnergyStored()).isEqualTo(2097152);
    }

    @Test
    void energyHatchClampsLoadedEnergyToCapacity() {
        EnergyHatchBlockEntity hatch = energyHatch("energy_input_hatch");
        storage(hatch).receiveEnergy(8192, false);
        setEnergy(storage(hatch), 10000);

        assertThat(hatch.getEnergyStorage(null).getEnergyStored()).isEqualTo(storage(hatch).getMaxEnergyStored());
    }

    @Test
    void energyHatchDoesNotExtractMoreThanCapacityWhenOverfilled() {
        EnergyHatchBlockEntity hatch = energyHatch("energy_output_hatch");
        storage(hatch).receiveEnergy(8192, false);
        setEnergy(storage(hatch), 10000);

        assertThat(hatch.getEnergyStorage(null).extractEnergy(20000, true)).isEqualTo(8192);
    }

    @Test
    void energyHatchExtractsTransferLimitWhenNormallyFilled() {
        EnergyHatchBlockEntity hatch = energyHatch("energy_output_hatch");
        storage(hatch).receiveEnergy(8192, false);

        assertThat(hatch.getEnergyStorage(null).extractEnergy(20000, true)).isEqualTo(512);
    }

    @Test
    void fluidHatchReportsNoMoreFluidThanCapacityWhenOverfilled() {
        FluidHatchBlockEntity hatch = fluidHatch("fluid_input_hatch");
        tank(hatch).fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), FluidTank.FluidAction.EXECUTE);
        setFluidAmount(tank(hatch), 2000);

        assertThat(hatch.getFluidHandler(null).getFluidInTank(0).getAmount()).isEqualTo(tank(hatch).getCapacity());
    }

    @Test
    void fluidHatchAllowsFullCapacityFillAndDrain() {
        FluidHatchBlockEntity hatch = fluidHatch("fluid_input_hatch");

        assertThat(tank(hatch).fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 2000),
                FluidTank.FluidAction.EXECUTE)).isEqualTo(1000);
        assertThat(tank(hatch).drain(2000, FluidTank.FluidAction.SIMULATE).getAmount()).isEqualTo(1000);
    }

    @Test
    void itemBusCachesInventoryEmptyState() {
        ItemBusBlockEntity bus = itemBus("item_output_bus");

        assertThat(bus.isInventoryEmpty()).isTrue();

        bus.getItemStackHandler(null).setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        assertThat(bus.isInventoryEmpty()).isFalse();

        bus.getItemStackHandler(null).setStackInSlot(0, ItemStack.EMPTY);
        assertThat(bus.isInventoryEmpty()).isTrue();
    }

    @Test
    void fluidHatchCachesTankEmptyState() {
        FluidHatchBlockEntity hatch = fluidHatch("fluid_output_hatch");

        assertThat(hatch.isTankEmpty()).isTrue();

        tank(hatch).fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 100), FluidTank.FluidAction.EXECUTE);
        assertThat(hatch.isTankEmpty()).isFalse();

        tank(hatch).drain(100, FluidTank.FluidAction.EXECUTE);
        assertThat(hatch.isTankEmpty()).isTrue();
    }

    @Test
    void autoIoTransferLimitUsesPortSizeNotFullCapacity() {
        FluidHatchBlockEntity vacuum = fluidHatch("fluid_output_hatch_vacuum");
        EnergyHatchBlockEntity ultimate = energyHatch("energy_output_hatch_ultimate");

        assertThat(vacuum.autoIoTransferLimit()).isLessThan(tank(vacuum).getCapacity());
        assertThat(ultimate.autoIoTransferLimit()).isEqualTo(kind("energy_output_hatch_ultimate").energyHatchSize().orElseThrow().transfer());
    }

    private static ItemBusBlockEntity itemBus(String id) {
        return (ItemBusBlockEntity) ModBlockEntities.BES.get(id).get().create(BlockPos.ZERO, state(id));
    }

    private static FluidHatchBlockEntity fluidHatch(String id) {
        return (FluidHatchBlockEntity) ModBlockEntities.BES.get(id).get().create(BlockPos.ZERO, state(id));
    }

    private static EnergyHatchBlockEntity energyHatch(String id) {
        return (EnergyHatchBlockEntity) ModBlockEntities.BES.get(id).get().create(BlockPos.ZERO, state(id));
    }

    private static BlockState state(String id) {
        kind(id);
        return ModBlocks.BLOCKS.get(id).get().defaultBlockState();
    }

    private static cn.howxu.mmcr.internal.port.IOPortKind kind(String id) {
        return PortKinds.all().stream()
                .filter(kind -> kind.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static FluidTank tank(FluidHatchBlockEntity hatch) {
        return hatch.getFluidTank(null);
    }

    private static EnergyStorage storage(EnergyHatchBlockEntity hatch) {
        return hatch.getMutableEnergyStorage(null);
    }

    private static void setEnergy(EnergyStorage storage, int energy) {
        try {
            Field energyField = EnergyStorage.class.getDeclaredField("energy");
            energyField.setAccessible(true);
            energyField.setInt(storage, energy);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to set energy", e);
        }
    }

    private static void setFluidAmount(FluidTank tank, int amount) {
        tank.getFluid().setAmount(amount);
    }
}
