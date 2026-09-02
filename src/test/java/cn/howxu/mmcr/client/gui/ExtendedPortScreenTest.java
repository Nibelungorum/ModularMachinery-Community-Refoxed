package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.FluidStorageEntry;
import cn.howxu.mmcr.internal.network.PktPortStorageSyncPayload.ItemStorageEntry;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.core.Holder;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import sun.misc.Unsafe;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Extended port text screen behavior tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class ExtendedPortScreenTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bindDeferredHolder(NeoForgeMod.WATER_TYPE,
                new FluidType(FluidType.Properties.create().descriptionId("block.minecraft.water")));
    }

    @Test
    void empty_extended_item_and_fluid_storage_have_one_logical_line() {
        assertThat(ExtendedItemScreen.displayLines(List.of())).hasSize(1);
        assertThat(ExtendedFluidScreen.displayLines(List.of())).hasSize(1);
    }

    @Test
    void combined_storage_has_one_logical_line_per_section_and_entry() {
        ItemStorageEntry firstItem = new ItemStorageEntry(0, ItemResource.of(Items.IRON_INGOT), 12L, 64L);
        ItemStorageEntry secondItem = new ItemStorageEntry(1, ItemResource.of(Items.GOLD_INGOT), 34L, 64L);
        FluidStorageEntry fluid = new FluidStorageEntry(0, FluidResource.of(Fluids.WATER), 56L, 78L);

        assertThat(ExtendedCombinedScreen.displayLines(List.of(firstItem, secondItem), List.of(fluid)))
                .hasSize(5)
                .extracting(Component::getString)
                .containsExactly("gui.mmcr.port.items", "12 " + firstItem.resource().getHoverName().getString(),
                        "34 " + secondItem.resource().getHoverName().getString(), "gui.mmcr.port.fluids",
                        "56 " + fluid.resource().getHoverName().getString());
    }

    @Test
    void extended_text_pages_use_the_shared_scrollable_text_base() {
        assertThat(AbstractScrollableTextScreen.class).isAssignableFrom(ExtendedItemScreen.class);
        assertThat(AbstractScrollableTextScreen.class).isAssignableFrom(ExtendedFluidScreen.class);
        assertThat(AbstractScrollableTextScreen.class).isAssignableFrom(ExtendedCombinedScreen.class);
    }

    @Test
    void auto_io_page_does_not_consume_text_scroll_and_resets_offset_when_toggled() throws Exception {
        ScrollProbeScreen screen = ScrollProbeScreen.create();
        Button pageButton = screen.createPageButton();

        boolean textScrollConsumed = screen.mouseScrolled(20, 30, 0, -1);
        int offsetAfterTextScroll = screen.firstLine();
        pageButton.onPress(null);
        int offsetAfterOpeningAutoIO = screen.firstLine();
        boolean autoIOScrollConsumed = screen.mouseScrolled(20, 30, 0, -1);
        int offsetAfterAutoIOScroll = screen.firstLine();
        pageButton.onPress(null);
        int offsetAfterReturningToText = screen.firstLine();
        boolean reopenedTextScrollConsumed = screen.mouseScrolled(20, 30, 0, -1);
        int reopenedTextOffset = screen.firstLine();

        assertThat(textScrollConsumed).isTrue();
        assertThat(offsetAfterTextScroll).isEqualTo(1);
        assertThat(offsetAfterOpeningAutoIO).isZero();
        assertThat(autoIOScrollConsumed).isFalse();
        assertThat(offsetAfterAutoIOScroll).isZero();
        assertThat(offsetAfterReturningToText).isZero();
        assertThat(reopenedTextScrollConsumed).isTrue();
        assertThat(reopenedTextOffset).isEqualTo(1);
    }

    @Test
    void empty_extended_item_storage_renders_a_light_green_empty_state() {
        assertThat(ExtendedItemScreen.displayLines(List.of(
                new ItemStorageEntry(0, ItemResource.EMPTY, 0L, 64L))))
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.getString()).isEqualTo("gui.mmcr.port.empty");
                    assertThat(line.getStyle().getColor().getValue()).isEqualTo(ChatFormatting.GREEN.getColor());
                });
    }

    @Test
    void empty_extended_combined_storage_uses_grouped_empty_states() {
        List<Component> lines = ExtendedCombinedScreen.displayLines(List.of(), List.of());
        assertThat(lines)
                .extracting(Component::getString)
                .containsExactly("gui.mmcr.port.items gui.mmcr.port.empty", "gui.mmcr.port.fluids gui.mmcr.port.empty");
        assertThat(lines.getFirst().getSiblings().getLast().getStyle().getColor().getValue())
                .isEqualTo(ChatFormatting.GREEN.getColor());
    }

    @Test
    void combined_storage_groups_content_and_marks_empty_group() {
        ItemStorageEntry item = new ItemStorageEntry(0, ItemResource.of(Items.IRON_INGOT),
                1_200_123_543_243L, Long.MAX_VALUE);

        assertThat(ExtendedCombinedScreen.displayLines(List.of(item), List.of()))
                .extracting(Component::getString)
                .containsExactly("gui.mmcr.port.items", "1.20T " + item.resource().getHoverName().getString(),
                        "gui.mmcr.port.fluids gui.mmcr.port.empty");
    }

    @Test
    void extended_item_lines_use_normalized_quantity_and_component_resource_name() {
        ItemStorageEntry entry = new ItemStorageEntry(0, ItemResource.of(Items.IRON_INGOT),
                1_200_123_543_243L, Long.MAX_VALUE);

        assertThat(ExtendedItemScreen.displayLines(List.of(entry)).getFirst().getString())
                .isEqualTo("1.20T " + entry.resource().getHoverName().getString());
        assertThat(ExtendedItemScreen.tooltipLines(entry)).extracting(component -> component.getString())
                .containsExactly(entry.resource().getHoverName().getString(),
                        "1,200,123,543,243 / 9,223,372,036,854,775,807");
    }

    @Test
    void extended_fluid_lines_use_normalized_quantity_and_exact_hover_values() {
        FluidStorageEntry entry = new FluidStorageEntry(0, FluidResource.of(Fluids.WATER),
                5_000_000_000L, Long.MAX_VALUE);

        assertThat(ExtendedFluidScreen.displayLines(List.of(entry)).getFirst().getString())
                .isEqualTo("5.00G " + entry.resource().getHoverName().getString());
        assertThat(ExtendedFluidScreen.tooltipLines(entry)).extracting(component -> component.getString())
                .containsExactly(entry.resource().getHoverName().getString(),
                        "5,000,000,000 / 9,223,372,036,854,775,807 mB");
    }

    @Test
    void extended_screens_use_the_large_controller_texture() {
        assertThat(ExtendedItemScreen.TEXTURE_PATH).isEqualTo("textures/gui/guicontroller_large.png");
        assertThat(ExtendedFluidScreen.TEXTURE_PATH).isEqualTo("textures/gui/guicontroller_large.png");
        assertThat(ExtendedCombinedScreen.TEXTURE_PATH).isEqualTo("textures/gui/guicontroller_large.png");
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

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        Field field = null;
        while (type != null && field == null) {
            try {
                field = type.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        if (field == null) throw new NoSuchFieldException(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * Minimal screen fixture for exercising the shared text viewport and Auto IO page transition.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class ScrollProbeScreen extends AbstractPortScreen<ItemBusMenu> {
        private ScrollProbeScreen() {
            super(null, null, Component.empty(), 213);
        }

        private static ScrollProbeScreen create() throws Exception {
            ScrollProbeScreen screen = (ScrollProbeScreen) unsafe().allocateInstance(ScrollProbeScreen.class);
            setField(screen, "font", unsafe().allocateInstance(Font.class));
            setField(screen, "imageHeight", 213);
            setField(screen, "leftPos", 0);
            setField(screen, "topPos", 0);
            setField(screen, "renderables", new ArrayList<>());
            setField(screen, "children", new ArrayList<>());
            setField(screen, "narratables", new ArrayList<>());
            setField(screen, "autoIOSideButtons", new EnumMap<>(Direction.class));
            return screen;
        }

        private Button createPageButton() throws Exception {
            Method method = AbstractPortScreen.class.getDeclaredMethod("createAutoIOPageButton",
                    int.class, int.class, Identifier.class);
            method.setAccessible(true);
            return (Button) method.invoke(this, 0, 0, BuiltinCapabilityDefinitions.ITEM_TYPE.id());
        }

        private int firstLine() {
            return firstVisibleTextLine();
        }

        @Override
        protected BlockPos portPos() {
            return BlockPos.ZERO;
        }

        @Override
        protected IOType ownerIOType() {
            return IOType.INPUT;
        }

        @Override
        protected int portSlotCount() {
            return 0;
        }

        @Override
        protected Identifier texture(boolean autoIOPage) {
            return MMCR.id("textures/gui/test.png");
        }

        @Override
        protected int scrollableTextLineCount() {
            return 30;
        }
    }
}
