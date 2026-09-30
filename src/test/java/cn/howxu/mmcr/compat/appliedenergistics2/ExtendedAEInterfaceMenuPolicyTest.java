package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.menu.slot.AppEngSlot;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.ExtendedInputInterfaceKind;
import cn.howxu.mmcr.compat.extendedae.loaded.kind.ExtendedOutputInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.kind.AsyncOutputInterfaceKind;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.AsyncOutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.InputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.util.InterfaceMenuPolicy;
import cn.howxu.mmcr.test.TestBootstrap;
import com.glodblock.github.extendedae.client.ExSemantics;
import com.glodblock.github.extendedae.container.ContainerExInterface;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Constructor;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies EAE interface slots use the shared output menu policy.
 *
 * @author howxu <dev@howxu.cn>
 */
class ExtendedAEInterfaceMenuPolicyTest {
    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
        AE2TestFixtures.ensureAE2KeyTypesInitialized();
        AE2TestFixtures.bindAE2InterfaceItem();
        AE2TestFixtures.bindEntityType(ExtendedOutputInterfaceKind.INSTANCE.id(),
                ExtendedOutputInterfaceKind.INSTANCE.entityFactory());
        AE2TestFixtures.bindEntityType(AsyncOutputInterfaceKind.INSTANCE.id(),
                AsyncOutputInterfaceKind.INSTANCE.entityFactory());
        AE2TestFixtures.bindEntityType(ExtendedInputInterfaceKind.INSTANCE.id(),
                ExtendedInputInterfaceKind.INSTANCE.entityFactory());
    }

    @Test
    void distinguishesOutputStorageWrappersByHostKind() {
        OutputInterfaceBlockEntity output = outputHost();
        AsyncOutputInterfaceBlockEntity asyncOutput = asyncOutputHost();
        InputInterfaceBlockEntity input = inputHost();
        var storageWrapper = output.getInterfaceLogic().getStorage().createMenuWrapper();

        assertThat(InterfaceMenuPolicy.isOutputStorageSlot(output, storageWrapper)).isTrue();
        assertThat(InterfaceMenuPolicy.isExtractableOutputStorageSlot(output, storageWrapper)).isTrue();
        assertThat(InterfaceMenuPolicy.isExtractableOutputStorageSlot(asyncOutput, storageWrapper)).isFalse();
        assertThat(InterfaceMenuPolicy.isOutputStorageSlot(input, storageWrapper)).isFalse();
    }

    @Test
    void recognizesAllExtendedAeStorageSlotGroups() {
        OutputInterfaceBlockEntity output = outputHost();
        Player player = testPlayer();
        Inventory playerInventory = new Inventory(player);
        setPlayerField(player, "inventory", playerInventory);
        ContainerExInterface menu = new ContainerExInterface(ContainerExInterface.TYPE, 0,
                playerInventory, output);

        assertStorageSlotsFollowPolicy(menu, output, ExSemantics.EX_2);
        assertStorageSlotsFollowPolicy(menu, output, ExSemantics.EX_4);
        assertStorageSlotsFollowPolicy(menu, output, ExSemantics.EX_6);
        assertStorageSlotsFollowPolicy(menu, output, ExSemantics.EX_8);
    }

    private static void assertStorageSlotsFollowPolicy(ContainerExInterface menu, OutputInterfaceBlockEntity output,
                                                       appeng.menu.SlotSemantic semantic) {
        assertThat(menu.getSlots(semantic)).isNotEmpty().allSatisfy(slot -> {
            assertThat(slot).isInstanceOf(AppEngSlot.class);
            assertThat(InterfaceMenuPolicy.isExtractableOutputStorageSlot(output,
                    ((AppEngSlot) slot).getInventory())).isTrue();
        });
    }

    private static OutputInterfaceBlockEntity outputHost() {
        return new OutputInterfaceBlockEntity(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState(),
                ExtendedOutputInterfaceKind.INSTANCE);
    }

    private static AsyncOutputInterfaceBlockEntity asyncOutputHost() {
        try {
            Constructor<AsyncOutputInterfaceBlockEntity> constructor = AsyncOutputInterfaceBlockEntity.class
                    .getDeclaredConstructor(BlockPos.class, net.minecraft.world.level.block.state.BlockState.class,
                            cn.howxu.mmcr.internal.port.IOPortKind.class);
            constructor.setAccessible(true);
            return constructor.newInstance(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState(),
                    AsyncOutputInterfaceKind.INSTANCE);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to construct async output interface test host", exception);
        }
    }

    private static InputInterfaceBlockEntity inputHost() {
        return new InputInterfaceBlockEntity(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState(),
                ExtendedInputInterfaceKind.INSTANCE);
    }

    @SuppressWarnings("unchecked")
    private static Player testPlayer() {
        try {
            var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Player) ((sun.misc.Unsafe) field.get(null)).allocateInstance(TestPlayer.class);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to allocate test player", exception);
        }
    }

    private static void setPlayerField(Player player, String name, Object value) {
        try {
            for (Class<?> type = Player.class; type != null; type = type.getSuperclass()) {
                try {
                    var field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    field.set(player, value);
                    return;
                } catch (NoSuchFieldException ignored) {
                }
            }
            throw new NoSuchFieldException(name);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to initialize test player", exception);
        }
    }

    private static final class TestPlayer extends Player {
        private TestPlayer(Level level) {
            super(level, BlockPos.ZERO, 0F, new GameProfile(UUID.randomUUID(), "test"));
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }
    }
}
