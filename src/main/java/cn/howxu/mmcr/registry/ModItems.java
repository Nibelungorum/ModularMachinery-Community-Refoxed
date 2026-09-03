package cn.howxu.mmcr.registry;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.internal.item.InterfaceBlockItem;
import cn.howxu.mmcr.internal.item.KeyCardItem;
import cn.howxu.mmcr.internal.item.MultiblockDetectorItem;
import cn.howxu.mmcr.internal.item.TerminalItem;
import cn.howxu.mmcr.internal.item.ThreadDisperserItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.Map;

public final class ModItems {

    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(MMCR.MODID);

    public static final DeferredHolder<Item, Item> MULTIBLOCK_DETECTOR =
            REGISTER.register("multiblock_detector", MultiblockDetectorItem::new);

    public static final DeferredHolder<Item, Item> THREAD_DISPERSER =
            REGISTER.register("thread_disperser", ThreadDisperserItem::new);

    public static final DeferredHolder<Item, Item> TERMINAL =
            REGISTER.register("terminal", TerminalItem::new);

    public static final DeferredHolder<Item, Item> KEY_CARD =
            REGISTER.register("key_card", KeyCardItem::new);

    public static final DeferredHolder<Item, Item> MODULARIUM =
            REGISTER.register("modularium", id -> new Item(new Item.Properties().setId(
                    ResourceKey.create(Registries.ITEM, id))));

    public static final DeferredHolder<Item, Item> BLUEPRINT =
            REGISTER.register("blueprint", id -> new Item(new Item.Properties().setId(
                    ResourceKey.create(Registries.ITEM, id))));

    public static final LinkedHashMap<String, DeferredHolder<Item, Item>> ITEMS = new LinkedHashMap<>();
    private static Map<Item, Identifier> controllerMachineIds = Map.of();

    static {
        ModBlocks.BLOCKS.forEach((name, blockHolder) -> {
            DeferredHolder<Item, Item> itemHolder = REGISTER.register(name, () ->
                    {
                        Item item = new InterfaceBlockItem(blockHolder.get(),
                                new Item.Properties().setId(
                                        ResourceKey.create(Registries.ITEM, MMCR.id(name))));
                        Identifier machineId = ModBlocks.machineIdForController(blockHolder.get());
                        if (machineId != null) {
                            Map<Item, Identifier> ids = new LinkedHashMap<>(controllerMachineIds);
                            ids.put(item, machineId);
                            controllerMachineIds = Map.copyOf(ids);
                        }
                        return item;
                    });
            ITEMS.put(name, itemHolder);
        });
        ITEMS.put("multiblock_detector", MULTIBLOCK_DETECTOR);
        ITEMS.put("thread_disperser", THREAD_DISPERSER);
        ITEMS.put("terminal", TERMINAL);
        ITEMS.put("key_card", KEY_CARD);
        ITEMS.put("modularium", MODULARIUM);
        ITEMS.put("blueprint", BLUEPRINT);
    }

    public static Identifier machineIdForControllerItem(Item item) {
        return controllerMachineIds.get(item);
    }

    public static void registerMachineControllerItems(Collection<Identifier> machineIds) {
        machineIds.forEach(machineId -> {
            String name = MachineControllerSpec.defaultsFor(machineId).id().getPath();
            if (ITEMS.containsKey(name)) return;
            DeferredHolder<Item, Item> itemHolder = REGISTER.register(name, () ->
                    new InterfaceBlockItem(ModBlocks.controllerFor(machineId).get(),
                            new Item.Properties().setId(ResourceKey.create(Registries.ITEM, MMCR.id(name)))));
            ITEMS.put(name, itemHolder);
        });
    }

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
    }

    private ModItems() {}
}
