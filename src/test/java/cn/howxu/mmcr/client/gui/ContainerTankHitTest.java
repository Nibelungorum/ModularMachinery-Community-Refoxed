package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.internal.menu.CombinedPortMenu;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies full tank frame hit routing for ordinary container interaction.
 *
 * @author howxu <dev@howxu.cn>
 */
class ContainerTankHitTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void single_tanks_accept_the_full_frame_without_resource_fill_inputs() {
        assertThat(FluidHatchScreen.singleTankIndexAt(15, 10)).isZero();
        assertThat(FluidHatchScreen.singleTankIndexAt(34.9, 10)).isZero();
        assertThat(FluidHatchScreen.singleTankIndexAt(15, 70.9)).isZero();
        assertThat(FluidHatchScreen.singleTankIndexAt(34.9, 70.9)).isZero();
        assertThat(ChemicalHatchScreen.singleTankIndexAt(15, 10)).isZero();
        assertThat(ChemicalHatchScreen.singleTankIndexAt(34.9, 10)).isZero();
        assertThat(ChemicalHatchScreen.singleTankIndexAt(15, 70.9)).isZero();
        assertThat(ChemicalHatchScreen.singleTankIndexAt(34.9, 70.9)).isZero();
    }

    @Test
    void single_tanks_reject_fractional_outside_edges_and_exclude_right_and_bottom_edges() {
        assertThat(FluidHatchScreen.singleTankIndexAt(14.9, 20)).isEqualTo(-1);
        assertThat(FluidHatchScreen.singleTankIndexAt(20, 9.9)).isEqualTo(-1);
        assertThat(FluidHatchScreen.singleTankIndexAt(35, 20)).isEqualTo(-1);
        assertThat(FluidHatchScreen.singleTankIndexAt(20, 71)).isEqualTo(-1);
        assertThat(ChemicalHatchScreen.singleTankIndexAt(14.9, 20)).isEqualTo(-1);
        assertThat(ChemicalHatchScreen.singleTankIndexAt(20, 9.9)).isEqualTo(-1);
        assertThat(ChemicalHatchScreen.singleTankIndexAt(35, 20)).isEqualTo(-1);
        assertThat(ChemicalHatchScreen.singleTankIndexAt(20, 71)).isEqualTo(-1);
    }

    @Test
    void second_tank_requires_a_real_layout_and_gaps_do_not_hit_either_tank() {
        var first = new CombinedPortMenu.FluidTankLayout(0, 15, 10);
        var second = new CombinedPortMenu.FluidTankLayout(1, 42, 10);
        List<CombinedPortMenu.FluidTankLayout> layouts = List.of(first, second);

        assertThat(CombinedPortScreen.tankIndexAt(List.of(), 15, 10)).isEqualTo(-1);
        assertThat(CombinedPortScreen.tankIndexAt(List.of(first), 43, 20)).isEqualTo(-1);
        assertThat(CombinedPortScreen.tankIndexAt(layouts, 43, 20)).isEqualTo(1);
        assertThat(CombinedPortScreen.tankIndexAt(layouts, 35, 20)).isEqualTo(-1);
        assertThat(CombinedPortScreen.tankIndexAt(layouts, 36, 20)).isEqualTo(-1);
        assertThat(CombinedPortScreen.tankIndexAt(layouts, 41.9, 20)).isEqualTo(-1);
        assertThat(CombinedPortScreen.tankIndexAt(layouts, 62, 20)).isEqualTo(-1);
    }

    @Test
    void combined_tanks_accept_the_full_frame_and_respect_fractional_edges() {
        List<CombinedPortMenu.FluidTankLayout> layouts =
                List.of(new CombinedPortMenu.FluidTankLayout(0, 15, 10));

        assertThat(CombinedPortScreen.tankIndexAt(layouts, 15, 10)).isZero();
        assertThat(CombinedPortScreen.tankIndexAt(layouts, 34.9, 10)).isZero();
        assertThat(CombinedPortScreen.tankIndexAt(layouts, 15, 70.9)).isZero();
        assertThat(CombinedPortScreen.tankIndexAt(layouts, 34.9, 70.9)).isZero();
        assertThat(CombinedPortScreen.tankIndexAt(layouts, 14.9, 20)).isEqualTo(-1);
        assertThat(CombinedPortScreen.tankIndexAt(layouts, 20, 9.9)).isEqualTo(-1);
        assertThat(CombinedPortScreen.tankIndexAt(layouts, 20, 71)).isEqualTo(-1);
    }

    @Test
    void combined_routing_uses_layout_positions_and_slot_ids_instead_of_list_indices() {
        List<CombinedPortMenu.FluidTankLayout> layouts = List.of(
                new CombinedPortMenu.FluidTankLayout(7, 100, 80),
                new CombinedPortMenu.FluidTankLayout(3, 0, 0));

        assertThat(CombinedPortScreen.tankIndexAt(layouts, 100, 80)).isEqualTo(7);
        assertThat(CombinedPortScreen.tankIndexAt(layouts, 119.9, 140.9)).isEqualTo(7);
        assertThat(CombinedPortScreen.tankIndexAt(layouts, 0, 0)).isEqualTo(3);
        assertThat(CombinedPortScreen.tankIndexAt(layouts, -0.1, 10)).isEqualTo(-1);
        assertThat(CombinedPortScreen.tankIndexAt(layouts, 10, -0.1)).isEqualTo(-1);
        assertThat(CombinedPortScreen.tankIndexAt(layouts, 15, 70)).isEqualTo(-1);
    }
}
