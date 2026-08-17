package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.autoio.AutoIOConfig;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static ItemInputBusBlockEntity itemInputBus() {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            ItemInputBusBlockEntity bus = (ItemInputBusBlockEntity) unsafe.allocateInstance(ItemInputBusBlockEntity.class);
            setField(BlockEntity.class, bus, "type", null);
            setField(BlockEntity.class, bus, "worldPosition", BlockPos.ZERO);
            setField(BlockEntity.class, bus, "blockState", Blocks.CHEST.defaultBlockState());
            setField(ItemInputBusBlockEntity.class, bus, "kind", PortKinds.ITEM_INPUT);
            setField(ItemBusBlockEntity.class, bus, "handler", new ItemStackHandler(1));
            setField(IOPortBlockEntity.class, bus, "autoIOConfig", new AutoIOConfig());
            setField(IOPortBlockEntity.class, bus, "autoIOCacheDirty", true);
            setField(LinkedAppearanceBlockEntity.class, bus, "appearanceBaseTexture", MMCR.id("block/basic_casing"));
            setField(LinkedAppearanceBlockEntity.class, bus, "linkedControllers", new java.util.TreeMap<>(BlockPos::compareTo));
            setField(LinkedAppearanceBlockEntity.class, bus, "controllerLinkCheckCounter", 0);
            return bus;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate item input bus", e);
        }
    }

    private static void save(ItemBusBlockEntity bus) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                HolderLookup.Provider.create(java.util.stream.Stream.empty()));
        bus.saveAdditional(output);
    }

    private static void setField(Class<?> type, Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
