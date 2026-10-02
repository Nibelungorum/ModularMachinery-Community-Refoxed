package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.compat.ars_nouveau.SourceViewFacet;
import cn.howxu.mmcr.compat.ars_nouveau.SourcePortMenu;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.ModUIs;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import io.netty.buffer.Unpooled;
import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises neutral source menu synchronization and the read-only screen lifecycle.
 *
 * @author howxu <dev@howxu.cn>
 */
class SourceHatchScreenTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        if (ModUIs.SOURCE_PORT != null) {
            setField(ModUIs.SOURCE_PORT, "holder", Holder.direct(new MenuType<>(
                    (id, inventory) -> new SourcePortMenu(id, inventory, BlockPos.ZERO), FeatureFlags.VANILLA_SET)));
        }
    }

    @Test
    void clientMenuDisplaysOnlyTheSynchronizedRealAmountsAndExactTooltip() {
        SourcePortMenu menu = new SourcePortMenu(1, new Inventory(null), new BlockPos(3, 4, 5));
        sync(menu, 5_000_000_007L, 9_000_000_019L);

        assertThat(menu.owner()).isNull();
        assertThat(menu.pos()).isEqualTo(new BlockPos(3, 4, 5));
        assertThat(menu.storedSource()).isEqualTo(5_000_000_007L);
        assertThat(menu.sourceCapacity()).isEqualTo(9_000_000_019L);
        assertThat(contents(SourceHatchScreen.amountLine(menu)).getKey()).isEqualTo("gui.mmcr.source.amount");
        assertThat(contents(SourceHatchScreen.amountLine(menu)).getArgs()).containsExactly("5.00G", "9.00G");
        assertThat(contents(SourceHatchScreen.tooltipLines(menu).getFirst()).getKey()).isEqualTo("gui.mmcr.source.exact");
        assertThat(contents(SourceHatchScreen.tooltipLines(menu).getFirst()).getArgs())
                .containsExactly("5,000,000,007 / 9,000,000,019");
        assertThat(SourceHatchScreen.filledHeight(menu)).isEqualTo(34);

        sync(menu, 7L, 0L);
        assertThat(contents(SourceHatchScreen.amountLine(menu)).getArgs()).containsExactly("7", "0");
        assertThat(contents(SourceHatchScreen.tooltipLines(menu).getFirst()).getArgs()).containsExactly("7 / 0");
        assertThat(SourceHatchScreen.filledHeight(menu)).isZero();
    }

    @Test
    void serverMenuKeepsTheCachedNeutralFacetAndTracksItsLiveStorage() {
        SourceProbePort owner = new SourceProbePort();
        SourcePortMenu menu = new SourcePortMenu(1, new Inventory(null), owner);
        owner.source.amount = 347L;
        owner.source.capacity = 731L;

        assertThat(menu.owner()).isSameAs(owner);
        assertThat(menu.storedSource()).isEqualTo(347L);
        assertThat(menu.sourceCapacity()).isEqualTo(731L);
        owner.source.amount = 19L;
        assertThat(menu.storedSource()).isEqualTo(19L);
        assertThat(owner.snapshotReads).isEqualTo(1);
        assertThat(menu.stillValid(null)).isFalse();
        assertThat(menu.quickMoveStack(null, 0).isEmpty()).isTrue();
    }

    @Test
    void clientFactoryReadsThePortPositionWithoutResolvingAnOwner() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            BlockPos pos = new BlockPos(-23, 17, 91);
            buffer.writeBlockPos(pos);
            SourcePortMenu menu = SourcePortMenu.clientOpen(2, new Inventory(null), buffer);

            assertThat(menu.pos()).isEqualTo(pos);
            assertThat(menu.owner()).isNull();
            assertThat(menu.stillValid(null)).isTrue();
            assertThat(menu.quickMoveStack(null, 0).isEmpty()).isTrue();
        } finally {
            buffer.release();
        }
    }

    @Test
    void sourceFillRemainsEmptyOrClampedAtTheBarBounds() {
        SourcePortMenu menu = new SourcePortMenu(1, new Inventory(null), BlockPos.ZERO);
        sync(menu, 0L, 1_000L);
        assertThat(SourceHatchScreen.filledHeight(menu)).isZero();
        sync(menu, 1L, Long.MAX_VALUE);
        assertThat(SourceHatchScreen.filledHeight(menu)).isEqualTo(1);
        sync(menu, Long.MAX_VALUE, Long.MAX_VALUE);
        assertThat(SourceHatchScreen.filledHeight(menu)).isEqualTo(61);
        sync(menu, 20L, 10L);
        assertThat(SourceHatchScreen.filledHeight(menu)).isEqualTo(61);
    }

    @Test
    void initializationAndResizeNeverCreateIoPageOrSidebarControls() throws Exception {
        SourceHatchScreen screen = screenFixture();
        screen.init();

        assertThat(screen.children()).isEmpty();
        assertThat(screen.supportsAutoIOControlPage()).isFalse();
        assertThat(screen.portSlotCount()).isZero();
        assertThat(screen.texture(false)).isEqualTo(MMCR.id("textures/gui/guibar.png"));
        assertThat(screen.texture(true)).isEqualTo(screen.texture(false));

        screen.resize(null, 640, 480);
        assertThat(screen.children()).isEmpty();
        assertThat(screen.autoIOPage).isFalse();
    }

    private static void sync(SourcePortMenu menu, long amount, long capacity) {
        menu.setData(0, (int) amount);
        menu.setData(1, (int) (amount >>> 32));
        menu.setData(2, (int) capacity);
        menu.setData(3, (int) (capacity >>> 32));
    }

    private static TranslatableContents contents(Component line) {
        return (TranslatableContents) line.getContents();
    }

    private static SourceHatchScreen screenFixture() throws Exception {
        // The ordinary test has no running graphical client, as in the existing screen fixtures.
        SourceHatchScreen screen = (SourceHatchScreen) unsafe().allocateInstance(SourceHatchScreen.class);
        Minecraft minecraft = (Minecraft) unsafe().allocateInstance(Minecraft.class);
        setField(minecraft, "lastInputType", InputType.NONE);
        setField(screen, "minecraft", minecraft);
        setField(screen, "menu", new SourcePortMenu(1, new Inventory(null), BlockPos.ZERO));
        setField(screen, "imageWidth", 176);
        setField(screen, "imageHeight", 166);
        setField(screen, "width", 320);
        setField(screen, "height", 240);
        setField(screen, "children", new ArrayList<>());
        setField(screen, "renderables", new ArrayList<>());
        setField(screen, "narratables", new ArrayList<>());
        return screen;
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class MutableSource implements SourceViewFacet {
        private long amount;
        private long capacity;

        @Override public long amount() { return amount; }
        @Override public long capacity() { return capacity; }
        @Override public Object queryIdentity() { return this; }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class SourceProbePort extends IOPortBlockEntity {
        private final MutableSource source = new MutableSource();
        private int snapshotReads;

        private SourceProbePort() {
            super(ModBlockEntities.BES.get("item_input_bus").get(), BlockPos.ZERO,
                    ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
        }

        @Override public IOType ioType() { return IOType.INPUT; }
        @Override public IOPortKind kind() { return PortKinds.ITEM_INPUT; }

        @Override
        public CapabilitySnapshot capabilitySnapshot() {
            snapshotReads++;
            return new CapabilitySnapshot(List.of(), List.of(source));
        }
    }
}
