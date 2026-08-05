package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.internal.item.MultiblockDetectorItem;
import cn.howxu.mmcr.internal.item.WrenchItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;

public final class ModItems {

    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(MMCR.MODID);

    public static final DeferredHolder<Item, Item> WRENCH =
            REGISTER.register("wrench", WrenchItem::new);

    public static final DeferredHolder<Item, Item> MULTIBLOCK_DETECTOR =
            REGISTER.register("multiblock_detector", MultiblockDetectorItem::new);

    public static final LinkedHashMap<String, DeferredHolder<Item, Item>> ITEMS = new LinkedHashMap<>();

    static {
        ModBlocks.BLOCKS.forEach((name, blockHolder) -> {
            DeferredHolder<Item, Item> itemHolder = REGISTER.register(name, () ->
                    new BlockItem(blockHolder.get(),
                            new Item.Properties().setId(
                                    ResourceKey.create(Registries.ITEM, MMCR.id(name)))));
            ITEMS.put(name, itemHolder);
        });
        ITEMS.put("wrench", WRENCH);
        ITEMS.put("multiblock_detector", MULTIBLOCK_DETECTOR);
    }

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
    }

    private ModItems() {}
}
