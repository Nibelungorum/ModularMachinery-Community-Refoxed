package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.internal.block.FactorySchedulerBlock;
import cn.howxu.mmcr.internal.item.ThreadDisperserItem;
import cn.howxu.mmcr.internal.tile.FactorySchedulerBlockEntity;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FactorySchedulerRegistrationTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void factory_controller_uses_scheduler_block_and_block_entity_registered() {
        assertThat(ModBlocks.BLOCKS).containsKey("factory_controller");
        assertThat(ModBlocks.BLOCKS).doesNotContainKey("factory_scheduler");
        assertThat(ModBlocks.BLOCKS.get("factory_controller").get()).isInstanceOf(FactorySchedulerBlock.class);
        assertThat(ModItems.ITEMS).containsKey("factory_controller");
        assertThat(ModItems.ITEMS).doesNotContainKey("factory_scheduler");
        assertThat(ModBlockEntities.BES).containsKey("factory_controller");
        assertThat(ModBlockEntities.BES).doesNotContainKey("factory_scheduler");

        BlockEntity entity = ModBlockEntities.BES.get("factory_controller").get().create(
                BlockPos.ZERO,
                ModBlocks.BLOCKS.get("factory_controller").get().defaultBlockState());

        assertThat(entity).isInstanceOf(FactorySchedulerBlockEntity.class);
    }

    @Test
    void thread_disperser_item_is_registered() {
        assertThat(ModItems.ITEMS).containsKey("thread_disperser");
        assertThat(ModItems.THREAD_DISPERSER.get()).isInstanceOf(ThreadDisperserItem.class);
    }

    @Test
    void thread_disperser_tooltip_marks_multithreading() {
        List<Component> tooltip = new ArrayList<>();

        ModItems.THREAD_DISPERSER.get().appendHoverText(
                new ItemStack(ModItems.THREAD_DISPERSER.get()), null, null, tooltip::add, null);

        assertThat(tooltip).containsExactly(Component.translatable("tooltip.mmcr.thread_disperser.multithreading").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
