package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.menu.CombinedPortMenu;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.FluidStorageEntry;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.ItemStorageEntry;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.Items;
import net.minecraft.core.Holder;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Combined port GUI behavior tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class CombinedPortScreenTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bindDeferredHolder(NeoForgeMod.WATER_TYPE,
                new FluidType(FluidType.Properties.create().descriptionId("block.minecraft.water")));
    }

    @Test
    void combined_capability_selectors_are_item_then_fluid() {
        assertThat(CombinedPortScreen.capabilityIds())
                .containsExactly(MMCR.id("item"), MMCR.id("fluid"));
    }

    @Test
    void extended_combined_lines_keep_item_section_before_fluid_section() {
        ItemStorageEntry item = new ItemStorageEntry(0, ItemResource.of(Items.IRON_INGOT), 12L, 64L);
        FluidStorageEntry fluid = new FluidStorageEntry(0, FluidResource.of(Fluids.WATER), 34L, 56L);

        assertThat(ExtendedCombinedScreen.displayLines(List.of(item), List.of(fluid)))
                .extracting(component -> component.getString())
                .containsExactly(item.amount() + " " + item.resource().getHoverName().getString(),
                        fluid.amount() + " " + fluid.resource().getHoverName().getString());
    }

    @Test
    void ordinary_combined_layout_keeps_existing_tank_coordinates_and_declares_reserved_slots() {
        CombinedPortScreen.Layout layout = CombinedPortScreen.layout();

        assertThat(layout.firstTankX()).isEqualTo(15);
        assertThat(layout.firstTankY()).isEqualTo(10);
        assertThat(layout.reservedCoordinates()).containsExactly("second_tank", "capability_selector");
    }

    @Test
    void known_one_and_two_tank_layouts_fill_then_draw_the_guitank_frame() {
        FluidStorageEntry first = new FluidStorageEntry(0, FluidResource.of(Fluids.WATER), 30L, 60L);
        FluidStorageEntry second = new FluidStorageEntry(1, FluidResource.of(Fluids.WATER), 10L, 60L);
        CombinedPortMenu.FluidTankLayout firstLayout = new CombinedPortMenu.FluidTankLayout(0, 15, 10);
        CombinedPortMenu.FluidTankLayout secondLayout = new CombinedPortMenu.FluidTankLayout(1, 43, 10);

        assertThat(CombinedPortScreen.tankRenderOperations(List.of(firstLayout), List.of(first)))
                .extracting(operation -> operation.kind())
                .containsExactly(CombinedPortScreen.TankRenderOperation.Kind.FILL,
                        CombinedPortScreen.TankRenderOperation.Kind.FRAME);
        assertThat(CombinedPortScreen.tankRenderOperations(List.of(firstLayout, secondLayout), List.of(first, second)))
                .extracting(operation -> operation.kind())
                .containsExactly(CombinedPortScreen.TankRenderOperation.Kind.FILL,
                        CombinedPortScreen.TankRenderOperation.Kind.FRAME,
                        CombinedPortScreen.TankRenderOperation.Kind.FILL,
                        CombinedPortScreen.TankRenderOperation.Kind.FRAME);

        CombinedPortScreen.TankRenderOperation frame = CombinedPortScreen.tankRenderOperations(
                List.of(firstLayout), List.of(first)).get(1);
        assertThat(frame.x()).isEqualTo(15);
        assertThat(frame.y()).isEqualTo(10);
        assertThat(frame.texture()).isEqualTo(MMCR.id("textures/gui/guitank.png"));
        assertThat(frame.sourceX()).isEqualTo(176);
        assertThat(frame.sourceY()).isZero();
        assertThat(frame.width()).isEqualTo(20);
        assertThat(frame.height()).isEqualTo(61);

        CombinedPortScreen.TankRenderOperation secondFrame = CombinedPortScreen.tankRenderOperations(
                List.of(firstLayout, secondLayout), List.of(first, second)).get(3);
        assertThat(secondFrame.x()).isEqualTo(43);
        assertThat(secondFrame.y()).isEqualTo(10);
    }

    private static void bindDeferredHolder(Object deferredHolder, Object value) throws Exception {
        Class<?> type = deferredHolder.getClass();
        Field holder = null;
        while (type != null && holder == null) {
            try {
                holder = type.getDeclaredField("holder");
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        if (holder == null) throw new NoSuchFieldException("holder");
        holder.setAccessible(true);
        holder.set(deferredHolder, Holder.direct(value));
    }
}
