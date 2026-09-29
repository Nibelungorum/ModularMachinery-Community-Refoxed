package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.FluidStorageEntry;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.ItemStorageEntry;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.Items;
import net.minecraft.core.Holder;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
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
                .containsExactly(BuiltinCapabilityDefinitions.ITEM_TYPE.id(), BuiltinCapabilityDefinitions.FLUID_TYPE.id());
    }

    @Test
    void extended_combined_lines_keep_item_section_before_fluid_section() {
        ItemStorageEntry item = new ItemStorageEntry(0, Items.IRON_INGOT.getDefaultInstance(), 12L, 64L);
        FluidStorageEntry fluid = new FluidStorageEntry(0, new net.neoforged.neoforge.fluids.FluidStack(Fluids.WATER, 1), 34L, 56L);

        assertThat(ExtendedCombinedScreen.displayLines(List.of(item), List.of(fluid)))
                .extracting(component -> component.getString())
                .containsExactly("gui.mmcr.port.items", item.amount() + " " + item.resource().getHoverName().getString(),
                        "gui.mmcr.port.fluids",
                        fluid.amount() + " " + fluid.resource().getHoverName().getString());
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
