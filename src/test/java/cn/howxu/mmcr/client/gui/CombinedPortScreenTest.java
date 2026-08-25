package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
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
