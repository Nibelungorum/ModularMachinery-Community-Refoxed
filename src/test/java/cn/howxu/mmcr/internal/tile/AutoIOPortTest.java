package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.internal.event.ModCapabilities;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Final automatic IO port behavior tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class AutoIOPortTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void input_ejection_retries_after_a_missing_target_and_completes_the_transfer() {
        ItemInputBusBlockEntity source = RuntimeTestFixtures.itemInput(BlockPos.ZERO);
        ItemOutputBusBlockEntity target = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        source.getItemStackHandler(null).setStackInSlot(0, stack(3));
        Level level = LevelStub.createWithBlockEntities(List.of(source, target));
        source.setLevel(level);
        target.setLevel(level);

        assertThat(source.ejectContents()).isFalse();
        assertThat(source.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(3);

        LevelStub.setCapability(level, ModCapabilities.ITEM_BLOCK, target.getBlockPos(),
                itemHandler(target, true, false));

        assertThat(source.ejectContents()).isTrue();
        assertThat(source.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        assertThat(target.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(3);
    }

    @Test
    void auto_io_uses_only_enabled_sides_and_publishes_configuration_changes() {
        ItemOutputBusBlockEntity source = RuntimeTestFixtures.itemOutput(BlockPos.ZERO);
        ItemInputBusBlockEntity target = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        source.getItemStackHandler(null).setStackInSlot(0, stack(3));
        Level level = LevelStub.createWithBlockEntities(List.of(source, target));
        source.setLevel(level);
        target.setLevel(level);
        LevelStub.setCapability(level, ModCapabilities.ITEM_BLOCK, target.getBlockPos(),
                itemHandler(target, true, false));
        int updatesBefore = LevelStub.sentBlockUpdates(level);

        source.setAutoIOEnabled(true);
        source.setAllAutoIOSides(false);
        source.setAutoIOSide(Direction.EAST, true);
        for (int tick = 0; tick < 6; tick++) source.serverTick();

        assertThat(source.autoIOConfig().enabled()).isTrue();
        assertThat(source.autoIOConfig().enabledSides()).containsExactly(Direction.EAST);
        assertThat(source.autoIOCandidateCount()).isEqualTo(1);
        assertThat(source.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        assertThat(target.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(3);
        assertThat(LevelStub.sentBlockUpdates(level)).isGreaterThan(updatesBefore);
    }

    @Test
    void full_ejection_moves_real_contents_to_the_available_adjacent_port() {
        ItemInputBusBlockEntity source = RuntimeTestFixtures.itemInput(BlockPos.ZERO);
        ItemOutputBusBlockEntity target = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        source.getItemStackHandler(null).setStackInSlot(0, stack(4));
        Level level = LevelStub.createWithBlockEntities(List.of(source, target));
        source.setLevel(level);
        target.setLevel(level);
        LevelStub.setCapability(level, ModCapabilities.ITEM_BLOCK, target.getBlockPos(),
                itemHandler(target, true, false));

        assertThat(source.ejectContents()).isTrue();
        assertThat(source.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        assertThat(target.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(4);
    }

    @Test
    void output_port_rejects_manual_ejection_without_mutating_contents() {
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(BlockPos.ZERO);
        output.getItemStackHandler(null).setStackInSlot(0, stack(2));

        assertThat(output.ejectContents()).isFalse();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(2);
    }

    private static ItemStack stack(int count) {
        ItemStack stack = new ItemStack(Items.IRON_INGOT, count);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        return stack;
    }

    @SuppressWarnings("unchecked")
    private static ResourceHandler<ItemResource> itemHandler(ItemBusBlockEntity port, boolean canInsert,
                                                              boolean canExtract) {
        try {
            Class<?> type = Class.forName("cn.howxu.mmcr.internal.event.ModCapabilities$ItemStackResourceHandler");
            Constructor<?> constructor = null;
            for (Constructor<?> candidate : type.getDeclaredConstructors()) {
                if (candidate.getParameterCount() == 3) {
                    constructor = candidate;
                    break;
                }
            }
            if (constructor == null) throw new NoSuchMethodException("Item capability adapter constructor");
            constructor.setAccessible(true);
            return (ResourceHandler<ItemResource>) constructor.newInstance(port.getItemStackHandler(null), canInsert,
                    canExtract);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to create the production item capability adapter", exception);
        }
    }
}
