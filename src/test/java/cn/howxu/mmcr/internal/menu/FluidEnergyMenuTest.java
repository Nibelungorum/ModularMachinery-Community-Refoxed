package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.EnergyHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.FluidHatchBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class FluidEnergyMenuTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void fluid_menu_surfaces_variant_capacity() {
        assertThat(FluidHatchMenu.fluidCapacity(fluidHatch("fluid_input_hatch_tiny"))).isEqualTo(100);
        assertThat(FluidHatchMenu.fluidCapacity(fluidHatch("fluid_output_hatch_vacuum"))).isEqualTo(32000);
    }

    @Test
    void energy_menu_surfaces_variant_capacity() {
        assertThat(EnergyHatchMenu.energyCapacity(energyHatch("energy_input_hatch_tiny"))).isEqualTo(2048);
        assertThat(EnergyHatchMenu.energyCapacity(energyHatch("energy_output_hatch_ultimate"))).isEqualTo(2097152);
    }

    private static FluidHatchBlockEntity fluidHatch(String id) {
        return (FluidHatchBlockEntity) kind(id).entityFactory().create(BlockPos.ZERO, ModBlocks.BLOCKS.get(id).get().defaultBlockState());
    }

    private static EnergyHatchBlockEntity energyHatch(String id) {
        return (EnergyHatchBlockEntity) kind(id).entityFactory().create(BlockPos.ZERO, ModBlocks.BLOCKS.get(id).get().defaultBlockState());
    }

    private static IOPortKind kind(String id) {
        return PortKinds.all().stream()
                .filter(kind -> kind.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
