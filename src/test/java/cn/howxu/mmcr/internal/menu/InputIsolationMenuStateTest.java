package cn.howxu.mmcr.internal.menu;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import sun.misc.Unsafe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Menu synchronization and authorization at the actual settings boundary.
 * @author howxu <dev@howxu.cn>
 */
class InputIsolationMenuStateTest {
    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void repeated_expected_values_are_idempotent_and_server_data_cannot_write_the_owner() throws Exception {
        var port = input();
        Level level = LevelStub.createWithBlockEntities(List.of(port));
        port.setLevel(level);
        var state = InputIsolationMenuState.forPort(port);
        var menu = new FixtureMenu(state);
        TestPlayer player = player(level, menu);

        assertThat(state.supported()).isTrue();
        assertThat(state.enabled()).isFalse();
        state.dataSlot().set(3);
        assertThat(state.enabled()).isFalse();
        assertThat(menu.clickMenuButton(player, InputIsolationMenuState.ENABLE)).isTrue();
        assertThat(menu.clickMenuButton(player, InputIsolationMenuState.ENABLE)).isTrue();
        assertThat(port.isInputIsolated()).isTrue();
        assertThat(LevelStub.sentBlockUpdates(level)).isEqualTo(1);
        assertThat(menu.clickMenuButton(player, InputIsolationMenuState.DISABLE)).isTrue();
        assertThat(menu.clickMenuButton(player, InputIsolationMenuState.DISABLE)).isTrue();
        assertThat(port.isInputIsolated()).isFalse();
        assertThat(LevelStub.sentBlockUpdates(level)).isEqualTo(2);
        assertThat(menu.clickMenuButton(player, 9003)).isFalse();
    }

    @Test
    void stale_menu_spectator_invalid_menu_different_world_and_replaced_owner_cannot_change_settings() throws Exception {
        var port = input();
        Level level = LevelStub.createWithBlockEntities(List.of(port));
        port.setLevel(level);
        var state = InputIsolationMenuState.forPort(port);
        var menu = new FixtureMenu(state);
        TestPlayer player = player(level, menu);

        player.containerMenu = new FixtureMenu(InputIsolationMenuState.forPort(null));
        assertThat(state.handleClick(menu, player, InputIsolationMenuState.ENABLE)).isFalse();
        player.containerMenu = menu;
        player.mode = GameType.SPECTATOR;
        assertThat(state.handleClick(menu, player, InputIsolationMenuState.ENABLE)).isFalse();
        player.mode = GameType.SURVIVAL;
        menu.valid = false;
        assertThat(state.handleClick(menu, player, InputIsolationMenuState.ENABLE)).isFalse();
        menu.valid = true;
        setLevel(player, LevelStub.createWithBlockEntities(List.of()));
        assertThat(state.handleClick(menu, player, InputIsolationMenuState.ENABLE)).isFalse();
        setLevel(player, level);
        LevelStub.putBlockEntity(level, input());
        assertThat(state.handleClick(menu, player, InputIsolationMenuState.ENABLE)).isFalse();
        assertThat(port.isInputIsolated()).isFalse();
        assertThat(LevelStub.sentBlockUpdates(level)).isZero();
    }

    @Test
    void output_ports_and_unattached_owners_reject_isolation_commands() throws Exception {
        var output = new ItemOutputBusBlockEntity(BlockPos.ZERO,
                ModBlocks.BLOCKS.get("item_output_bus").get().defaultBlockState());
        Level level = LevelStub.createWithBlockEntities(List.of(output));
        output.setLevel(level);
        var state = InputIsolationMenuState.forPort(output);
        var menu = new FixtureMenu(state);
        assertThat(state.supported()).isFalse();
        assertThat(menu.clickMenuButton(player(level, menu), InputIsolationMenuState.ENABLE)).isFalse();
        assertThat(output.isInputIsolated()).isFalse();
        assertThat(InputIsolationMenuState.forPort(input()).handleClick(menu, null, InputIsolationMenuState.ENABLE)).isFalse();
    }

    @Test
    void client_state_only_uses_menu_sync_even_when_a_local_block_entity_exists() throws Exception {
        var localPort = input();
        var state = InputIsolationMenuState.forPort(localPort);
        Level clientLevel = LevelStub.createWithBlockEntities(List.of(localPort));
        Field clientSide = Level.class.getDeclaredField("isClientSide");
        clientSide.setAccessible(true);
        clientSide.set(clientLevel, true);
        localPort.setLevel(clientLevel);
        localPort.setInputIsolated(true);

        assertThat(InputIsolationMenuState.forPort(localPort).supported()).isFalse();
        assertThat(state.supported()).isFalse();
        assertThat(state.enabled()).isFalse();
        state.dataSlot().set(1);
        assertThat(state.supported()).isTrue();
        assertThat(state.enabled()).isFalse();
        state.dataSlot().set(3);
        localPort.setInputIsolated(false);
        assertThat(state.enabled()).isTrue();
        var menu = new FixtureMenu(state);
        assertThat(menu.clickMenuButton(null, InputIsolationMenuState.DISABLE)).isFalse();
        assertThat(state.enabled()).isTrue();
    }

    @Test
    void full_isolation_changes_only_the_controller_and_keeps_the_port_setting() throws Exception {
        var controller = TestBootstrap.newController();
        var port = new ItemInputBusBlockEntity(BlockPos.ZERO.above(),
                ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        Level level = LevelStub.createWithBlockEntities(List.of(controller, port));
        controller.setLevel(level);
        port.setLevel(level);
        port.setInputIsolated(true);
        var state = InputIsolationMenuState.forController(controller);
        var menu = new FixtureMenu(state);
        TestPlayer player = player(level, menu);
        assertThat(menu.clickMenuButton(player, InputIsolationMenuState.ENABLE)).isTrue();
        assertThat(controller.isFullyInputIsolated()).isTrue();
        assertThat(menu.clickMenuButton(player, InputIsolationMenuState.DISABLE)).isTrue();
        assertThat(controller.isFullyInputIsolated()).isFalse();
        assertThat(port.isInputIsolated()).isTrue();
    }

    private static ItemInputBusBlockEntity input() {
        return new ItemInputBusBlockEntity(BlockPos.ZERO,
                ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
    }

    private static TestPlayer player(Level level, FixtureMenu menu) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var player = (TestPlayer) ((Unsafe) field.get(null)).allocateInstance(TestPlayer.class);
        player.mode = GameType.SURVIVAL;
        player.containerMenu = menu;
        setLevel(player, level);
        return player;
    }

    private static void setLevel(Player player, Level level) throws Exception {
        Field field = Entity.class.getDeclaredField("level");
        field.setAccessible(true);
        field.set(player, level);
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class TestPlayer extends Player {
        private GameType mode;
        private TestPlayer(Level level) { super(level, BlockPos.ZERO, 0, null); }
        @Override public boolean isSpectator() { return mode == GameType.SPECTATOR; }
        @Override public boolean isCreative() { return mode == GameType.CREATIVE; }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class FixtureMenu extends AbstractMachineMenu {
        private boolean valid = true;
        private FixtureMenu(InputIsolationMenuState state) {
            super(null, 1);
            bindInputIsolation(state);
        }
        @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) { return valid; }
    }
}
