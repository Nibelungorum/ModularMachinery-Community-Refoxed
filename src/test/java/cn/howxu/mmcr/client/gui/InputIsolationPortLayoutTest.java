package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.internal.autoio.AutoIOAction;
import cn.howxu.mmcr.internal.capability.BuiltinCapabilityDefinitions;
import cn.howxu.mmcr.internal.network.PktAutoIOConfigPayload;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.internal.tile.CombinedPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.internal.menu.AbstractMachineMenu;
import cn.howxu.mmcr.internal.menu.InputIsolationMenuState;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Isolation is limited to the port main page and compacts after visible navigation rows.
 * @author howxu <dev@howxu.cn>
 */
class InputIsolationPortLayoutTest {
    private Unsafe unsafe;
    private Minecraft previous;

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @BeforeEach
    void client() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        unsafe = (Unsafe) field.get(null);
        Field instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        previous = (Minecraft) instance.get(null);
        instance.set(null, unsafe.allocateInstance(Minecraft.class));
    }

    @AfterEach
    void restoreClient() throws Exception {
        Field instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, previous);
    }

    @Test
    void isolation_hides_on_io_page_and_restores_compacted_main_page_position() throws Exception {
        for (int navigationCount : List.of(0, 1, 2)) {
            FixtureScreen screen = screen(navigationCount);
            var update = AbstractPortScreen.class.getDeclaredMethod("updateAutoIOWidgets");
            update.setAccessible(true);
            for (boolean ioPage : List.of(false, true, false)) {
                screen.autoIOPage = ioPage;
                update.invoke(screen);
                IsolationToggleButton isolation = (IsolationToggleButton) getField(screen, "isolationButton");
                boolean activePage = navigationCount > 0 && ioPage;
                assertThat(isolation.visible).isEqualTo(!activePage);
                assertThat(isolation.active).isEqualTo(!activePage);
                assertThat(isolation.getX()).isEqualTo(32);
                assertThat(isolation.getY()).isEqualTo(41 + navigationCount * 18);
                Button secondary = (Button) getField(screen, "secondaryAutoIOPageButton");
                assertThat(secondary.visible).isEqualTo(navigationCount > 1 && !activePage);
                assertThat(screen.getMenu().inputIsolation().enabled()).isFalse();
            }
            screen.getMenu().inputIsolation().dataSlot().set(3);
            update.invoke(screen);
            assertThat(screen.getMenu().inputIsolation().enabled()).isTrue();
            screen.getMenu().inputIsolation().dataSlot().set(0);
            update.invoke(screen);
            assertThat(((IsolationToggleButton) getField(screen, "isolationButton")).visible).isFalse();
        }
    }

    @Test
    void late_owner_enables_navigation_and_replacement_closes_the_old_page() throws Exception {
        var screen = screen(0);
        var update = AbstractPortScreen.class.getDeclaredMethod("updateAutoIOWidgets");
        update.setAccessible(true);
        var page = (Button) getField(screen, "autoIOPageButton");
        update.invoke(screen);
        assertThat(page.visible).isFalse();
        screen.port = RuntimeTestFixtures.itemInput(BlockPos.ZERO);
        update.invoke(screen);
        assertThat(page.visible).isTrue();
        screen.autoIOPage = true;
        screen.port = RuntimeTestFixtures.itemInput(BlockPos.ZERO);
        update.invoke(screen);
        assertThat(screen.autoIOPage).isFalse();
    }

    @Test
    void third_live_capability_is_addressable_and_its_removal_prevents_stale_requests() throws Exception {
        var screen = screen(1);
        var capabilities = new ArrayList<MachineCapability>(screen.port.capabilities());
        capabilities.addAll(RuntimeTestFixtures.fluidInput(BlockPos.ZERO).capabilities());
        capabilities.addAll(RuntimeTestFixtures.energyInput(BlockPos.ZERO).capabilities());
        screen.port = new ItemInputBusBlockEntity(BlockPos.ZERO, screen.port.getBlockState()) {
            @Override public CapabilitySnapshot capabilitySnapshot() { return new CapabilitySnapshot(capabilities); }
        };
        var update = AbstractPortScreen.class.getDeclaredMethod("updateAutoIOWidgets");
        update.setAccessible(true);
        update.invoke(screen);
        var ids = screen.port.configurableAutoIOIds();
        screen.pressPageButton(ids, true);
        screen.pressPageButton(ids, true);
        screen.pressPageButton(ids, false);
        assertThat(screen.configRequest(AutoIOAction.SET_SIDE, Direction.NORTH, false)).contains(
                new PktAutoIOConfigPayload(BlockPos.ZERO, BuiltinCapabilityDefinitions.ENERGY_TYPE.id(),
                        AutoIOAction.SET_SIDE, Direction.NORTH, false));
        capabilities.removeLast();
        assertThat(screen.configRequest(AutoIOAction.SET_ENABLED, null, true)).isEmpty();
        update.invoke(screen);
        assertThat(screen.autoIOPage).isFalse();
    }

    private FixtureScreen screen(int navigationCount) throws Exception {
        var screen = (FixtureScreen) unsafe.allocateInstance(FixtureScreen.class);
        var state = InputIsolationMenuState.forPort(null);
        state.dataSlot().set(1);
        setField(screen, "menu", new FixtureMenu(state));
        setField(screen, "leftPos", 50);
        setField(screen, "topPos", 40);
        setField(screen, "autoIOSideButtons", new EnumMap<Direction, Button>(Direction.class));
        setField(screen, "hiddenSlotPositions", new ArrayList<>());
        setField(screen, "relocatedPlayerSlotPositions", new ArrayList<>());
        setField(screen, "isolationButton", new IsolationToggleButton(0, 0, Component.empty(), state::enabled, ignored -> {}));
        setField(screen, "autoIOPageButton", button());
        setField(screen, "secondaryAutoIOPageButton", button());
        screen.port = navigationCount == 0 ? null : navigationCount == 1 ? RuntimeTestFixtures.itemInput(BlockPos.ZERO)
                : new CombinedPortBlockEntity(BlockPos.ZERO, ModBlocks.BLOCKS.get("combined_input_basic").get().defaultBlockState());
        return screen;
    }

    private static StyledButton button() {
        return new StyledButton(0, 0, 16, 16, Component.empty(), ignored -> {});
    }

    private static Field field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    private static void setField(Object target, String name, Object value) throws Exception { field(target, name).set(target, value); }
    private static Object getField(Object target, String name) throws Exception { return field(target, name).get(target); }

    /** @author howxu <dev@howxu.cn> */
    private static final class FixtureScreen extends AbstractPortScreen<FixtureMenu> {
        private FixtureScreen(FixtureMenu menu, Inventory inventory) { super(menu, inventory, Component.empty(), 166); }
        @Override protected BlockPos portPos() { return BlockPos.ZERO; }
        @Override protected IOType ownerIOType() { return IOType.INPUT; }
        @Override protected int portSlotCount() { return 0; }
        @Override protected ResourceLocation texture(boolean autoIOPage) { return null; }
        @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) { }
        private IOPortBlockEntity port;
        @Override IOPortBlockEntity portEntity() { return port; }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class FixtureMenu extends AbstractMachineMenu {
        private FixtureMenu(InputIsolationMenuState state) { super(null, 1); bindInputIsolation(state); }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) { return true; }
    }
}
