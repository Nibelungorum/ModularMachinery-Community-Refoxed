package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemBusBlockEntityTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void save_normalizes_direct_item_holder_without_losing_components() {
        var bus = itemInputBus();
        var stack = new ItemStack(Holder.direct(Items.IRON_SWORD, DataComponentMap.EMPTY), 1);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Migrated sword"));
        bus.getItemStackHandler(null).setStackInSlot(0, stack);

        save(bus);

        var normalized = bus.getItemStackHandler(null).getStackInSlot(0);
        assertThat(normalized.typeHolder().unwrapKey()).isPresent();
        assertThat(normalized.getCount()).isEqualTo(1);
        assertThat(normalized.get(DataComponents.CUSTOM_NAME)).isEqualTo(Component.literal("Migrated sword"));
    }

    @Test
    void handler_respects_item_max_stack_size() {
        var handler = itemInputBus().getItemStackHandler(null);
        int itemMax = Items.SNOWBALL.getDefaultInstance().getMaxStackSize();

        ItemStack remainder = handler.insertItem(0, new ItemStack(Items.SNOWBALL, itemMax * 2), false);

        assertThat(remainder.getCount()).isEqualTo(itemMax);
        assertThat(handler.getStackInSlot(0).getCount()).isEqualTo(itemMax);
    }

    @Test
    void setter_rejects_over_capacity_without_clearing_existing_contents() {
        var handler = itemInputBus().getItemStackHandler(null);
        ItemStack existing = ironStack(5);
        handler.setStackInSlot(0, existing);
        int overCapacity = handler.getSlotLimit(0) + 1;

        assertThatThrownBy(() -> handler.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, overCapacity)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(handler.getStackInSlot(0).getItem()).isEqualTo(existing.getItem());
        assertThat(handler.getStackInSlot(0).getCount()).isEqualTo(existing.getCount());
    }

    @Test
    void malformed_items_input_preserves_existing_contents() {
        var handler = itemInputBus().getItemStackHandler(null);
        ItemStack existing = ironStack(5);
        handler.setStackInSlot(0, existing);
        CompoundTag malformed = new CompoundTag();
        malformed.putString("Items", "not an item list");

        handler.deserialize(TagValueInput.create(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()), malformed));

        assertThat(handler.getStackInSlot(0).getItem()).isEqualTo(existing.getItem());
        assertThat(handler.getStackInSlot(0).getCount()).isEqualTo(existing.getCount());
    }

    @Test
    void long_item_drop_is_bounded_at_1024_legal_stacks_per_slot() {
        ExtendedItemBusBlockEntity bus = (ExtendedItemBusBlockEntity) ModBlockEntities.BES
                .get("extended_item_input_bus_basic").get().create(
                        BlockPos.ZERO, ModBlocks.BLOCKS.get("extended_item_input_bus_basic").get().defaultBlockState());
        ItemResource resource = ItemResource.of(Items.IRON_INGOT);
        long stackLimit = resource.getMaxStackSize();
        long amount = stackLimit * 1025L;
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(bus.itemStorage().insert(0, resource, amount, transaction)).isEqualTo(amount);
            transaction.commit();
        }

        List<ItemStack> dropped = new ArrayList<>();
        ItemBusBlockEntity.dropItemResources(bus.itemStorage(), dropped::add);

        assertThat(dropped).hasSize(1024);
        assertThat(dropped).allSatisfy(stack -> assertThat(stack.getCount()).isLessThanOrEqualTo((int) stackLimit));
        assertThat(dropped.stream().mapToLong(ItemStack::getCount).sum()).isEqualTo(stackLimit * 1024L);
        assertThat(bus.itemStorage().amount(0)).isZero();
    }

    private static ItemInputBusBlockEntity itemInputBus() {
        return (ItemInputBusBlockEntity) ModBlockEntities.BES.get("item_input_bus").get().create(
                BlockPos.ZERO, ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState());
    }

    private static void save(ItemBusBlockEntity bus) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(Stream.empty()));
        bus.saveAdditional(output);
    }

    private static ItemStack ironStack(int count) {
        ItemStack stack = new ItemStack(Items.IRON_INGOT, count);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        return stack;
    }
}
