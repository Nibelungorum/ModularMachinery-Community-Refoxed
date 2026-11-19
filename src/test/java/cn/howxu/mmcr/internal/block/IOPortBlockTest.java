package cn.howxu.mmcr.internal.block;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.MachinePort;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** @author howxu <dev@howxu.cn> */
class IOPortBlockTest {
    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void live_owner_opens_its_declared_menu_even_with_a_builtin_prefix() {
        var kind = new ProbeKind("item_input_bus_contract_probe");
        var owner = new ProbePort(kind);
        var level = LevelStub.createWithBlockEntities(List.of(owner));
        var provider = IOPortBlock.providerFor(kind, level, BlockPos.ZERO);
        assertThat(provider.createMenu(4, new Inventory(null), null)).isInstanceOfSatisfying(ProbeMenu.class,
                menu -> assertThat(menu.owner).isSameAs(owner));
        assertThat(IOPortBlock.providerFor(new ProbeKind("item_input_bus_other"), level, BlockPos.ZERO)
                .createMenu(5, new Inventory(null), null)).isNull();
        owner.setRemoved();
        assertThat(provider.createMenu(6, new Inventory(null), null)).isNull();
    }

    /** @author howxu <dev@howxu.cn> */
    private record ProbeKind(String id) implements IOPortKind {
        public IOType ioType() { return IOType.INPUT; }
        public PortDefinition definition() { return PortDefinition.of(MMCR.id(id)); }
        public BlockEntityType.BlockEntitySupplier<? extends BlockEntity> entityFactory() { return (p, s) -> new ProbePort(this); }
        public AbstractContainerMenu createPortMenu(int id, Inventory inventory, Level level, BlockPos pos) {
            return new ProbeMenu(id, level.getBlockEntity(pos));
        }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class ProbePort extends BlockEntity implements MachinePort {
        private final IOPortKind kind;
        ProbePort(IOPortKind kind) {
            super(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
            this.kind = kind;
        }
        public IOPortKind kind() { return kind; }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class ProbeMenu extends AbstractContainerMenu {
        final BlockEntity owner;
        ProbeMenu(int id, BlockEntity owner) { super(null, id); this.owner = owner; }
        public boolean stillValid(Player player) { return !owner.isRemoved(); }
        public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    }
}
