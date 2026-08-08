package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
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
        setEnergy(storage(hatch), 100000);

        assertThat(hatch.getEnergyStorage(null).getEnergyStored()).isEqualTo(storage(hatch).getMaxEnergyStored());
    }

    @Test
    void energyHatchDoesNotExtractMoreThanCapacityWhenOverfilled() {
        EnergyHatchBlockEntity hatch = energyHatch("energy_output_hatch");
        storage(hatch).receiveEnergy(8192, false);
        setEnergy(storage(hatch), 100000);

        assertThat(hatch.getEnergyStorage(null).extractEnergy(20000, true)).isEqualTo(storage(hatch).getMaxEnergyStored());
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
        setFluidAmount(tank(hatch), 8000);

        assertThat(hatch.getFluidHandler(null).getFluidInTank(0).getAmount()).isEqualTo(tank(hatch).getCapacity());
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
        PortKinds.all().stream()
                .filter(kind -> kind.id().equals(id))
                .findFirst()
                .orElseThrow();
        return ModBlocks.BLOCKS.get(id).get().defaultBlockState();
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
