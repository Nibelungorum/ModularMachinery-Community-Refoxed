package cn.howxu.mmcr.internal.network;

import cn.howxu.mmcr.internal.autoio.AutoIOCapabilityType;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author howxu <dev@howxu.cn>
 */
class PktEjectPortContentsPayloadTest {

    private static final BlockPos PORT_POS = new BlockPos(1, 2, 3);

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        bind(ModUIs.ITEM_BUS, new MenuType<>((containerId, inventory) -> new ItemBusMenu(containerId, inventory, BlockPos.ZERO), FeatureFlags.VANILLA_SET));
    }

    @Test
    void mismatched_menu_position_is_rejected_without_ejecting() throws Exception {
        ProbePort port = inputPort();
        ServerPlayer player = playerWith(port, new ItemBusMenu(1, new Inventory(null, null), BlockPos.ZERO), PORT_POS);

        assertThat(PktEjectPortContentsPayload.ejectOnServer(player, new PktEjectPortContentsPayload(PORT_POS))).isFalse();
        assertThat(port.ejectCalls).isZero();
    }

    @Test
    void wrong_menu_type_is_rejected_without_ejecting() throws Exception {
        ProbePort port = inputPort();
        ServerPlayer player = playerWith(port, new AbstractContainerMenu(null, 1) {
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
            @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
                return ItemStack.EMPTY;
            }
        }, PORT_POS);

        assertThat(PktEjectPortContentsPayload.ejectOnServer(player, new PktEjectPortContentsPayload(PORT_POS))).isFalse();
        assertThat(port.ejectCalls).isZero();
    }

    @Test
    void distant_player_is_rejected_without_ejecting() throws Exception {
        ProbePort port = inputPort();
        ServerPlayer player = playerWith(port, new ItemBusMenu(1, new Inventory(null, null), PORT_POS), new BlockPos(100, 2, 3));

        assertThat(PktEjectPortContentsPayload.ejectOnServer(player, new PktEjectPortContentsPayload(PORT_POS))).isFalse();
        assertThat(port.ejectCalls).isZero();
    }

    @Test
    void output_port_is_rejected_without_ejecting() throws Exception {
        ProbePort port = outputPort();
        ServerPlayer player = playerWith(port, new ItemBusMenu(1, new Inventory(null, null), PORT_POS), PORT_POS);

        assertThat(PktEjectPortContentsPayload.ejectOnServer(player, new PktEjectPortContentsPayload(PORT_POS))).isFalse();
        assertThat(port.ejectCalls).isZero();
    }

    @Test
    void matching_input_menu_ejects_port_contents() throws Exception {
        ProbePort port = inputPort();
        ServerPlayer player = playerWith(port, new ItemBusMenu(1, new Inventory(null, null), PORT_POS), PORT_POS);

        assertThat(PktEjectPortContentsPayload.ejectOnServer(player, new PktEjectPortContentsPayload(PORT_POS))).isTrue();
        assertThat(port.ejectCalls).isEqualTo(1);
    }

    private static ProbePort inputPort() {
        return port(IOType.INPUT, PortKinds.ITEM_INPUT, "item_input_bus");
    }

    private static ProbePort outputPort() {
        return port(IOType.OUTPUT, PortKinds.ITEM_OUTPUT, "item_output_bus");
    }

    private static ProbePort port(IOType ioType, IOPortKind kind, String blockId) {
        return new ProbePort(PORT_POS, ModBlocks.BLOCKS.get(blockId).get().defaultBlockState(), ioType, kind);
    }

    private static ServerPlayer playerWith(ProbePort port, AbstractContainerMenu menu, BlockPos playerPos) throws Exception {
        TestServerLevel level = (TestServerLevel) unsafe().allocateInstance(TestServerLevel.class);
        level.port = port;
        port.setLevel(level);
        ServerPlayer player = (ServerPlayer) unsafe().allocateInstance(ServerPlayer.class);
        setField(Entity.class, player, "level", level);
        setField(Entity.class, player, "position", new Vec3(playerPos.getX(), playerPos.getY(), playerPos.getZ()));
        player.containerMenu = menu;
        return player;
    }

    private static void bind(Object deferredHolder, MenuType<ItemBusMenu> menuType) throws Exception {
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
        holder.set(deferredHolder, Holder.direct(menuType));
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void setField(Class<?> type, Object target, String name, Object value) throws Exception {
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

    private static final class ProbePort extends IOPortBlockEntity {
        private final IOType ioType;
        private final IOPortKind kind;
        private int ejectCalls;

        private ProbePort(BlockPos pos, BlockState state, IOType ioType, IOPortKind kind) {
            super(ModBlockEntities.BES.get(kind.id()).get(), pos, state);
            this.ioType = ioType;
            this.kind = kind;
        }

        @Override public IOType ioType() { return ioType; }
        @Override public IOPortKind kind() { return kind; }
        @Override public AutoIOCapabilityType autoIOCapabilityType() { return AutoIOCapabilityType.ITEM; }
        @Override public boolean ejectContents() {
            ejectCalls++;
            return true;
        }
    }

    private static final class TestServerLevel extends ServerLevel {
        private ProbePort port;

        private TestServerLevel() {
            super(null, null, null, null, Level.OVERWORLD, null, false, 0L, List.of(), false);
        }

        @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
            return PORT_POS.equals(pos) ? port : null;
        }
    }
}
