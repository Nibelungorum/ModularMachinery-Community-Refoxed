package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IOPortSizeTest {

    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
        bindVariantBlockEntities();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void bindVariantBlockEntities() throws Exception {
        MappedRegistry registry = (MappedRegistry) BuiltInRegistries.BLOCK_ENTITY_TYPE;
        registry.unfreeze(true);
        for (var kind : PortKinds.all()) {
            String id = kind.id();
            if (!BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(Identifier.fromNamespaceAndPath("mmcr", id))) {
                Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath("mmcr", id),
                        new net.minecraft.world.level.block.entity.BlockEntityType(
                                (net.minecraft.world.level.block.entity.BlockEntityType.BlockEntitySupplier) kind.entityFactory(),
                                ModBlocks.BLOCKS.get(id).get()));
            }
            var value = BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("mmcr", id));
            bind(ModBlockEntities.BES.get(id), value);
        }
        registry.freeze();
    }

    private static void bind(Object deferredHolder, Object value) throws Exception {
        Class<?> type = deferredHolder.getClass();
        java.lang.reflect.Field holder = null;
        while (type != null && holder == null) {
            try {
                holder = type.getDeclaredField("holder");
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        if (holder == null) throw new NoSuchFieldException("holder");
        holder.setAccessible(true);
        holder.set(deferredHolder, net.minecraft.core.Holder.direct(value));
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
}
