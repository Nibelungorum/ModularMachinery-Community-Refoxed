package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.helpers.InventoryAction;
import appeng.helpers.IPriorityHost;
import appeng.helpers.InterfaceLogicHost;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.InterfaceMenu;
import appeng.menu.implementations.SetStockAmountMenu;
import appeng.menu.implementations.PriorityMenu;
import appeng.menu.locator.MenuLocators;
import appeng.menu.slot.FakeSlot;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.compat.appliedenergistics2.AE2Bridge;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.AE2InterfaceMenu;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.AE2MenuTypes;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.InterfaceStorageSlot;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.menu.PatternInterfaceMenu;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.AsyncOutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.InputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.OutputInterfaceBlockEntity;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.StockingInterfaceBlockEntity;
import cn.howxu.mmcr.compat.extendedae.loaded.menu.ExtendedAEMenuTypes;
import cn.howxu.mmcr.compat.extendedae.loaded.menu.ExtendedInterfaceMenu;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import com.glodblock.github.extendedae.client.ExSemantics;
import com.glodblock.github.extendedae.common.EAESingletons;
import com.glodblock.github.extendedae.common.tileentities.TileExInterface;
import com.glodblock.github.extendedae.container.ContainerExInterface;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Actual menu actions against native inventories, including packet bypasses and conservation.
 *
 * @author howxu <dev@howxu.cn>
 */
public class InterfaceMenuGameTest {
    public void ae2HostsOpenAndReturnOwnTypes(GameTestHelper helper) {
        hostsOpenAndReturnOwnTypes(helper, List.of(
                new MenuRoute("ae2_me_input_interface", AE2MenuTypes.INTERFACE, AE2InterfaceMenu.class, "ae2_interface"),
                new MenuRoute("ae2_me_stocking_input_interface", AE2MenuTypes.INTERFACE, AE2InterfaceMenu.class, "ae2_interface"),
                new MenuRoute("ae2_me_output_interface", AE2MenuTypes.INTERFACE, AE2InterfaceMenu.class, "ae2_interface"),
                new MenuRoute("ae2_me_async_output_interface", AE2MenuTypes.INTERFACE, AE2InterfaceMenu.class, "ae2_interface"),
                new MenuRoute("ae2_me_pattern_interface", AE2MenuTypes.PATTERN, PatternInterfaceMenu.class, "ae2_pattern_interface")));
    }

    public void extendedHostsOpenAndReturnOwnTypes(GameTestHelper helper) {
        hostsOpenAndReturnOwnTypes(helper, List.of(
                new MenuRoute("eae_me_extended_input_interface", ExtendedAEMenuTypes.INTERFACE, ExtendedInterfaceMenu.class, "eae_interface"),
                new MenuRoute("eae_me_extended_stocking_input_interface", ExtendedAEMenuTypes.INTERFACE, ExtendedInterfaceMenu.class, "eae_interface"),
                new MenuRoute("eae_me_extended_output_interface", ExtendedAEMenuTypes.INTERFACE, ExtendedInterfaceMenu.class, "eae_interface"),
                new MenuRoute("eae_me_oversize_input_interface", ExtendedAEMenuTypes.OVERSIZE, ExtendedInterfaceMenu.class, "eae_oversize_interface"),
                new MenuRoute("eae_me_oversize_output_interface", ExtendedAEMenuTypes.OVERSIZE, ExtendedInterfaceMenu.class, "eae_oversize_interface"),
                new MenuRoute("eae_me_extended_pattern_interface", ExtendedAEMenuTypes.PATTERN, PatternInterfaceMenu.class, "eae_pattern_interface")));
    }

    private static void hostsOpenAndReturnOwnTypes(GameTestHelper helper, List<MenuRoute> routes) {
        for (int i = 0; i < routes.size(); i++) {
            MenuRoute route = routes.get(i);
            BlockPos pos = new BlockPos(i * 2, 0, 0);
            helper.setBlock(pos, ModBlocks.BLOCKS.get(route.blockId()).get().defaultBlockState());
            IOPortBlockEntity host = helper.getBlockEntity(pos);
            var player = player(helper);
            var type = route.type();
            helper.assertTrue(AE2Bridge.get().openMenu(player, helper.getLevel(), helper.absolutePos(pos)),
                    "Bridge opens the real interface host: " + route.blockId());
            var main = (AEBaseMenu) player.containerMenu;
            helper.assertTrue(main.getType() == type && main.getTarget() == host
                             && main.getClass() == route.menuClass()
                             && MMCR.id(route.menuId()).equals(BuiltInRegistries.MENU.getKey(main.getType())),
                    "Bridge uses the independently specified factory, registration and target for " + route.blockId());
            var locator = MenuLocators.forBlockEntity(host);
            var priorityHost = (IPriorityHost) host;
            MenuOpener.open(PriorityMenu.TYPE, player, locator);
            PriorityMenu priority = (PriorityMenu) player.containerMenu;
            priority.setPriority(43);
            priorityHost.returnToMainMenu(player, priority);
            helper.assertTrue(player.containerMenu.getType() == type
                             && player.containerMenu.getClass() == route.menuClass()
                            && ((AEBaseMenu) player.containerMenu).getTarget() == host && priorityHost.getPriority() == 43,
                    "Priority sub-menu returns to the specified own factory and located host for " + route.blockId());
        }
        helper.succeed();
    }

    public void mirrorSyncPreservesWrappedAmounts(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("ae2_me_stocking_input_interface").get().defaultBlockState());
        StockingInterfaceBlockEntity host = helper.getBlockEntity(BlockPos.ZERO);
        var storage = host.getInterfaceLogic().getStorage();
        var slot = new InterfaceStorageSlot(storage.createMenuWrapper(), 0, true);
        for (AEKey key : List.of(AEItemKey.of(Items.IRON_INGOT), AEFluidKey.of(Fluids.WATER))) {
            var initial = new GenericStack(key, Integer.MAX_VALUE + 1024L);
            slot.initialize(GenericStack.wrapInItemStack(initial));
            helper.assertTrue(initial.equals(storage.getStack(0)), "Initial mirror sync preserves the key and long amount");
            var delta = new GenericStack(key, Integer.MAX_VALUE + 4096L);
            slot.set(GenericStack.wrapInItemStack(delta));
            helper.assertTrue(delta.equals(storage.getStack(0)), "Delta mirror sync does not truncate or change the key");
            slot.set(ItemStack.EMPTY);
            helper.assertTrue(storage.getStack(0) == null, "Empty delta clears the previous mirror");
        }
        helper.succeed();
    }

    public void outputCacheUsesLocalSlotPolicy(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("ae2_me_output_interface").get().defaultBlockState());
        OutputInterfaceBlockEntity host = helper.getBlockEntity(BlockPos.ZERO);
        var player = player(helper);
        var menu = new AE2InterfaceMenu(AE2MenuTypes.INTERFACE, 0, player.getInventory(), host);
        helper.assertTrue(menu.getType() == AE2MenuTypes.INTERFACE, "Output uses the own menu type");
        exerciseOutput(helper, menu, player, host.getInterfaceLogic().getStorage(),
                menu.getSlots(SlotSemantics.STORAGE).getFirst(), menu.getSlots(SlotSemantics.STORAGE).get(1),
                (FakeSlot) menu.getSlots(SlotSemantics.CONFIG).getFirst(), host.getConfig());
        helper.succeed();
    }

    public void extendedOutputConservesItemsAndFluids(GameTestHelper helper) {
        for (String id : List.of("eae_me_extended_output_interface", "eae_me_oversize_output_interface")) {
            BlockPos pos = id.contains("oversize") ? new BlockPos(2, 0, 0) : BlockPos.ZERO;
            helper.setBlock(pos, ModBlocks.BLOCKS.get(id).get().defaultBlockState());
            OutputInterfaceBlockEntity host = helper.getBlockEntity(pos);
            var player = player(helper);
            var type = id.contains("oversize") ? ExtendedAEMenuTypes.OVERSIZE : ExtendedAEMenuTypes.INTERFACE;
            var menu = new ExtendedInterfaceMenu(type, 0, player.getInventory(), host);
            menu.setPage(1);
            helper.assertTrue(menu.getType() == type, "Extended output uses its correct profile menu");
            var storageSlots = menu.getSlots(ExSemantics.EX_8);
            exerciseOutput(helper, menu, player, host.getInterfaceLogic().getStorage(),
                    storageSlots.get(7), storageSlots.get(8), (FakeSlot) menu.getConfigSlots().get(35), host.getConfig());
        }
        helper.succeed();
    }

    private static void exerciseOutput(GameTestHelper helper, AEBaseMenu menu, ServerPlayer player,
                                       GenericStackInv storage, Slot itemSlot, Slot fluidSlot,
                                       FakeSlot configSlot, GenericStackInv config) {
        int itemIndex = itemSlot.getSlotIndex();
        int fluidIndex = fluidSlot.getSlotIndex();
        storage.setStack(itemIndex, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 4L));
        storage.setStack(fluidIndex, new GenericStack(AEFluidKey.of(Fluids.WATER), 3_000L));

        menu.setCarried(new ItemStack(Items.DIAMOND, 3));
        assertUnchanged(helper, menu, player, storage, () -> menu.clicked(itemSlot.index, 0, ClickType.PICKUP, player));
        assertUnchanged(helper, menu, player, storage, () -> itemSlot.set(Items.DIAMOND.getDefaultInstance()));
        assertUnchanged(helper, menu, player, storage, () -> itemSlot.set(new ItemStack(Items.IRON_INGOT, 8)));
        assertUnchanged(helper, menu, player, storage, () -> menu.setFilter(configSlot.index, menu.getCarried()));
        assertUnchanged(helper, menu, player, storage, () -> configSlot.increase(menu.getCarried()));
        assertUnchanged(helper, menu, player, storage, () -> configSlot.decrease(menu.getCarried()));
        for (InventoryAction action : List.of(InventoryAction.PICKUP_OR_SET_DOWN, InventoryAction.PLACE_SINGLE,
                InventoryAction.SPLIT_OR_PLACE_SINGLE, InventoryAction.EMPTY_ITEM)) {
            assertUnchanged(helper, menu, player, storage, () -> menu.doAction(player, action, configSlot.index, 0));
            helper.assertTrue(config.isEmpty(), "Output fake-slot action cannot create a filter: " + action);
        }
        menu.setCarried(ItemStack.EMPTY);
        assertUnchanged(helper, menu, player, storage, () -> menu.setFilter(configSlot.index, ItemStack.EMPTY));
        helper.assertTrue(config.isEmpty(), "All output config write and clear paths stay locked");

        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
        Slot hotbar = menu.getSlots(SlotSemantics.PLAYER_HOTBAR).getFirst();
        assertUnchanged(helper, menu, player, storage, () -> menu.quickMoveStack(player, hotbar.index));
        assertUnchanged(helper, menu, player, storage, () -> menu.clicked(itemSlot.index, 0, ClickType.SWAP, player));
        player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 3));
        assertUnchanged(helper, menu, player, storage, () -> menu.quickMoveStack(player, hotbar.index));
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
        menu.setCarried(Items.WATER_BUCKET.getDefaultInstance());
        for (InventoryAction action : List.of(InventoryAction.EMPTY_ITEM, InventoryAction.EMPTY_ENTIRE_ITEM)) {
            assertUnchanged(helper, menu, player, storage, () -> menu.doAction(player, action, fluidSlot.index, 0));
        }

        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(itemSlot.index, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(Items.IRON_INGOT) && menu.getCarried().getCount() == 4
                        && storage.getStack(itemIndex) == null,
                "Ordinary pickup transfers output items to the cursor");
        helper.assertTrue(physicalItems(menu, player, storage, Items.IRON_INGOT) == 4L,
                "Ordinary output pickup conserves iron");
        player.getInventory().add(menu.getCarried());
        menu.setCarried(ItemStack.EMPTY);
        storage.setStack(itemIndex, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 5L));
        long ironBefore = physicalItems(menu, player, storage, Items.IRON_INGOT);
        menu.quickMoveStack(player, itemSlot.index);
        helper.assertTrue(storage.getStack(itemIndex) == null
                        && physicalItems(menu, player, storage, Items.IRON_INGOT) == ironBefore,
                "Shift extraction transfers output into player inventory without loss or duplication");

        menu.setCarried(Items.BUCKET.getDefaultInstance());
        long waterBefore = waterAmount(menu, player, storage);
        long bucketsBefore = bucketCount(menu, player);
        menu.doAction(player, InventoryAction.FILL_ITEM, fluidSlot.index, 0);
        helper.assertTrue(menu.getCarried().is(Items.WATER_BUCKET) && storage.getAmount(fluidIndex) == 2_000L,
                "Output FILL_ITEM takes one bucket from the actual fluid cache");
        helper.assertTrue(waterAmount(menu, player, storage) == waterBefore && bucketCount(menu, player) == bucketsBefore,
                "Fluid extraction conserves water and bucket shells");
        player.getInventory().add(menu.getCarried());
        menu.setCarried(new ItemStack(Items.BUCKET, 2));
        waterBefore = waterAmount(menu, player, storage);
        bucketsBefore = bucketCount(menu, player);
        menu.doAction(player, InventoryAction.FILL_ENTIRE_ITEM, fluidSlot.index, 0);
        helper.assertTrue(storage.getStack(fluidIndex) == null, "FILL_ENTIRE_ITEM drains the remaining two buckets");
        helper.assertTrue(waterAmount(menu, player, storage) == waterBefore && bucketCount(menu, player) == bucketsBefore,
                "Bulk fluid extraction conserves both player and cache resources");

        // An existing same-key filter reaches FakeSlot's direct delegate insert/extract paths.
        int configIndex = configSlot.getSlotIndex();
        config.setStack(configIndex, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 7L));
        helper.assertTrue(config.getMode() == GenericStackInv.Mode.CONFIG_STACKS
                        && AEItemKey.of(Items.IRON_INGOT).equals(config.getKey(configIndex))
                        && config.getAmount(configIndex) == 7L,
                "Existing same-key CONFIG_STACKS filter exercises the direct delegate increment/decrement branch");
        menu.setCarried(new ItemStack(Items.IRON_INGOT, 3));
        assertUnchanged(helper, menu, player, storage, () -> configSlot.increase(menu.getCarried()));
        assertUnchanged(helper, menu, player, storage, () -> configSlot.decrease(menu.getCarried()));
        assertUnchanged(helper, menu, player, storage, () -> configSlot.set(menu.getCarried()));
        assertUnchanged(helper, menu, player, storage, () -> menu.setFilter(configSlot.index, menu.getCarried()));
        for (InventoryAction action : List.of(InventoryAction.PICKUP_OR_SET_DOWN, InventoryAction.PLACE_SINGLE,
                InventoryAction.SPLIT_OR_PLACE_SINGLE)) {
            assertUnchanged(helper, menu, player, storage, () -> menu.doAction(player, action, configSlot.index, 0));
        }
        assertUnchanged(helper, menu, player, storage, () -> configSlot.set(ItemStack.EMPTY));
        assertUnchanged(helper, menu, player, storage, configSlot::clearStack);
        assertUnchanged(helper, menu, player, storage, () -> configSlot.initialize(ItemStack.EMPTY));
        assertUnchanged(helper, menu, player, storage, () -> menu.setFilter(configSlot.index, ItemStack.EMPTY));
        menu.setCarried(ItemStack.EMPTY);
        assertUnchanged(helper, menu, player, storage,
                () -> menu.doAction(player, InventoryAction.SPLIT_OR_PLACE_SINGLE, configSlot.index, 0));
        menu.setCarried(Items.WATER_BUCKET.getDefaultInstance());
        assertUnchanged(helper, menu, player, storage,
                () -> menu.doAction(player, InventoryAction.EMPTY_ITEM, configSlot.index, 0));
    }

    public void asyncDisplayCannotMaterializeOutputs(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("ae2_me_async_output_interface").get().defaultBlockState());
        AsyncOutputInterfaceBlockEntity host = helper.getBlockEntity(BlockPos.ZERO);
        var storage = host.getInterfaceLogic().getStorage();
        storage.setStack(0, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 17L));
        storage.setStack(1, new GenericStack(AEFluidKey.of(Fluids.WATER), 3_000L));
        var player = player(helper);
        var menu = new AE2InterfaceMenu(AE2MenuTypes.INTERFACE, 0, player.getInventory(), host);
        var itemSlot = menu.getSlots(SlotSemantics.STORAGE).getFirst();
        var fluidSlot = menu.getSlots(SlotSemantics.STORAGE).get(1);
        assertUnchanged(helper, menu, player, storage, () -> menu.clicked(itemSlot.index, 0, ClickType.PICKUP, player));
        assertUnchanged(helper, menu, player, storage, () -> menu.quickMoveStack(player, itemSlot.index));
        assertUnchanged(helper, menu, player, storage,
                () -> helper.assertTrue(itemSlot.remove(1).isEmpty(), "Async display cannot be removed directly"));
        menu.setCarried(Items.BUCKET.getDefaultInstance());
        for (InventoryAction action : List.of(InventoryAction.FILL_ITEM, InventoryAction.FILL_ENTIRE_ITEM)) {
            assertUnchanged(helper, menu, player, storage, () -> menu.doAction(player, action, fluidSlot.index, 0));
        }
        menu.setCarried(Items.WATER_BUCKET.getDefaultInstance());
        for (InventoryAction action : List.of(InventoryAction.EMPTY_ITEM, InventoryAction.EMPTY_ENTIRE_ITEM)) {
            assertUnchanged(helper, menu, player, storage, () -> menu.doAction(player, action, fluidSlot.index, 0));
        }
        helper.succeed();
    }

    public void extendedPagesDoNotMutateSemanticLists(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("eae_me_extended_input_interface").get().defaultBlockState());
        InputInterfaceBlockEntity host = helper.getBlockEntity(BlockPos.ZERO);
        var player = player(helper);
        var menu = new ExtendedInterfaceMenu(ExtendedAEMenuTypes.INTERFACE, 0, player.getInventory(), host);
        List<SlotSemantic> semantics = List.of(ExSemantics.EX_1, ExSemantics.EX_2, ExSemantics.EX_3,
                ExSemantics.EX_4, ExSemantics.EX_5, ExSemantics.EX_6, ExSemantics.EX_7, ExSemantics.EX_8);
        var before = semantics.stream().map(semantic -> List.copyOf(menu.getSlots(semantic))).toList();
        for (int i = 0; i < 100; i++) menu.showPage(i % 2);
        var after = semantics.stream().map(semantic -> List.copyOf(menu.getSlots(semantic))).toList();
        helper.assertTrue(before.equals(after), "Repeated page rendering preserves semantic membership");
        menu.setPage(1);
        Slot config = menu.getConfigSlots().get(35);
        Slot storageSlot = menu.getSlots(ExSemantics.EX_8).getLast();
        helper.assertTrue(config.isActive() && storageSlot.isActive(), "Page 1 activates config and storage slot 35");
        menu.setFilter(config.index, Items.IRON_INGOT.getDefaultInstance());
        helper.assertTrue(host.getConfig().getKey(35).equals(AEItemKey.of(Items.IRON_INGOT)),
                "Visible page accepts a slot 35 filter");
        host.getInterfaceLogic().getStorage().setStack(35, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 4L));
        menu.quickMoveStack(player, storageSlot.index);
        helper.assertTrue(host.getInterfaceLogic().getStorage().getStack(35) == null
                        && physicalItems(menu, player, host.getInterfaceLogic().getStorage(), Items.IRON_INGOT) == 4L,
                "Visible page extraction conserves slot 35 resources");
        host.getConfig().setStack(35, null);
        host.getInterfaceLogic().getStorage().setStack(35, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 4L));
        menu.setPage(0);
        helper.assertFalse(config.isActive() || storageSlot.isActive(), "Page 0 hides both slot 35 views");
        menu.setCarried(ItemStack.EMPTY);
        assertUnchanged(helper, menu, player, host.getInterfaceLogic().getStorage(),
                () -> menu.clicked(storageSlot.index, 0, ClickType.PICKUP, player));
        assertUnchanged(helper, menu, player, host.getInterfaceLogic().getStorage(),
                () -> menu.quickMoveStack(player, storageSlot.index));
        menu.setCarried(Items.DIAMOND.getDefaultInstance());
        assertUnchanged(helper, menu, player, host.getInterfaceLogic().getStorage(),
                () -> menu.clicked(storageSlot.index, 0, ClickType.PICKUP, player));
        assertUnchanged(helper, menu, player, host.getInterfaceLogic().getStorage(),
                () -> menu.setFilter(config.index, menu.getCarried()));
        assertUnchanged(helper, menu, player, host.getInterfaceLogic().getStorage(),
                () -> menu.doAction(player, InventoryAction.PICKUP_OR_SET_DOWN, config.index, 0));
        helper.assertTrue(host.getConfig().getStack(35) == null, "Hidden config rejects direct filter and action packets");
        host.getInterfaceLogic().getStorage().setStack(35, new GenericStack(AEFluidKey.of(Fluids.WATER), 2_000L));
        menu.setCarried(Items.BUCKET.getDefaultInstance());
        assertUnchanged(helper, menu, player, host.getInterfaceLogic().getStorage(),
                () -> menu.doAction(player, InventoryAction.FILL_ITEM, storageSlot.index, 0));
        menu.setCarried(Items.WATER_BUCKET.getDefaultInstance());
        assertUnchanged(helper, menu, player, host.getInterfaceLogic().getStorage(),
                () -> menu.doAction(player, InventoryAction.EMPTY_ENTIRE_ITEM, storageSlot.index, 0));
        helper.succeed();
    }

    public void extendedPickAllOnlyCollectsCurrentPage(GameTestHelper helper) {
        List<String> ids = List.of("eae_me_extended_input_interface", "eae_me_extended_output_interface",
                "eae_me_oversize_output_interface");
        for (int i = 0; i < ids.size(); i++) {
            BlockPos pos = new BlockPos(i * 2, 0, 0);
            helper.setBlock(pos, ModBlocks.BLOCKS.get(ids.get(i)).get().defaultBlockState());
            var host = (InterfaceLogicHost) helper.getBlockEntity(pos);
            var storage = host.getStorage();
            var type = i == 2 ? ExtendedAEMenuTypes.OVERSIZE : ExtendedAEMenuTypes.INTERFACE;
            for (int page : List.of(0, 1)) {
                var player = player(helper);
                var menu = new ExtendedInterfaceMenu(type, 0, player.getInventory(), host);
                menu.setPage(page);
                storage.setStack(0, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 3L));
                storage.setStack(35, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 4L));
                player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 2));
                menu.setCarried(Items.IRON_INGOT.getDefaultInstance());
                Slot source = menu.getSlots(SlotSemantics.PLAYER_HOTBAR).get(1);
                helper.assertTrue(source.isActive() && !source.hasItem(), "PICKUP_ALL starts at an active empty player slot");
                var stored = snapshot(storage);
                var configured = snapshot(host.getConfig());
                long total = physicalItems(menu, player, storage, Items.IRON_INGOT);
                int activeIndex = page == 0 ? 0 : 35;
                long collected = storage.getAmount(activeIndex) + 3L;
                stored.set(activeIndex, null);

                menu.clicked(source.index, 0, ClickType.PICKUP_ALL, player);

                helper.assertTrue(stored.equals(snapshot(storage)),
                        "PICKUP_ALL collects the active cache and leaves all hidden cache keys and amounts: " + ids.get(i));
                helper.assertTrue(menu.getCarried().is(Items.IRON_INGOT) && menu.getCarried().getCount() == collected
                                && player.getInventory().items.stream().allMatch(ItemStack::isEmpty),
                        "PICKUP_ALL retains native collection from current page and player inventory");
                helper.assertTrue(configured.equals(snapshot(host.getConfig()))
                                && physicalItems(menu, player, storage, Items.IRON_INGOT) == total,
                        "PICKUP_ALL preserves configuration and conserves input/output items across both pages");
            }
        }
        helper.succeed();
    }

    public void extendedQuickCraftResetsOnEffectivePageChange(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("eae_me_extended_input_interface").get().defaultBlockState());
        InputInterfaceBlockEntity host = helper.getBlockEntity(BlockPos.ZERO);
        var storage = host.getStorage();
        for (int targets : List.of(1, 2)) {
            // 0 is the no-effective-change control; 1 is explicit; 2 is observed from another menu.
            for (int change : List.of(0, 1, 2)) {
                storage.clear();
                var player = player(helper);
                var menu = new ExtendedInterfaceMenu(ExtendedAEMenuTypes.INTERFACE, 0, player.getInventory(), host);
                menu.setPage(0);
                var selected = menu.getSlots(ExSemantics.EX_2).subList(0, targets);
                helper.assertFalse(menu.canDragTo(menu.getSlots(ExSemantics.EX_8).getLast()),
                        "Hidden storage cannot be selected for dragging");
                menu.setCarried(new ItemStack(Items.IRON_INGOT, 8));
                beginQuickCraft(menu, player, selected);
                if (change == 0) {
                    menu.setPage(-1); // Clamps to the already visible page, retaining the real drag.
                    menu.broadcastChanges();
                } else if (change == 1) {
                    menu.setPage(1);
                } else {
                    var otherPlayer = player(helper);
                    var other = new ExtendedInterfaceMenu(ExtendedAEMenuTypes.INTERFACE, 1,
                            otherPlayer.getInventory(), host);
                    other.setPage(1);
                    helper.assertTrue(menu.page == 0 && selected.stream().allMatch(Slot::isActive),
                            "The original menu observes a shared host page change at broadcast time");
                    menu.broadcastChanges();
                }

                if (change == 0) {
                    endQuickCraft(menu, player);
                    helper.assertTrue(menu.getCarried().isEmpty()
                                    && selected.stream().allMatch(slot -> storage.getAmount(slot.getSlotIndex()) == 8L / targets)
                                    && physicalItems(menu, player, storage, Items.IRON_INGOT) == 8L,
                            "Unchanged effective page preserves native single/multiple target QUICK_CRAFT");
                } else {
                    helper.assertTrue(menu.page == 1 && selected.stream().noneMatch(Slot::isActive),
                            "Explicit and host-observed changes hide the saved drag targets");
                    assertUnchanged(helper, menu, player, storage, () -> endQuickCraft(menu, player));
                    helper.assertTrue(physicalItems(menu, player, storage, Items.IRON_INGOT) == 8L,
                            "Cancelled single/multiple target drag conserves carried items without writing the hidden page");
                    // A new drag on the new page must still execute normally after cancellation.
                    var active = menu.getSlots(ExSemantics.EX_6).subList(0, targets);
                    beginQuickCraft(menu, player, active);
                    endQuickCraft(menu, player);
                    helper.assertTrue(menu.getCarried().isEmpty()
                                    && active.stream().allMatch(slot -> storage.getAmount(slot.getSlotIndex()) == 8L / targets)
                                    && physicalItems(menu, player, storage, Items.IRON_INGOT) == 8L,
                            "Fresh drag on the new page works and conserves resources after reset");
                }
            }
        }
        helper.succeed();
    }

    private static void beginQuickCraft(ExtendedInterfaceMenu menu, ServerPlayer player, List<Slot> targets) {
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT, player);
        for (Slot target : targets) {
            menu.clicked(target.index, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, player);
        }
    }

    private static void endQuickCraft(ExtendedInterfaceMenu menu, ServerPlayer player) {
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT, player);
    }

    public void extendedPlayerShiftUsesOnlyCurrentPage(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.BLOCKS.get("eae_me_extended_input_interface").get().defaultBlockState());
        InputInterfaceBlockEntity host = helper.getBlockEntity(BlockPos.ZERO);
        var storage = host.getStorage();
        var config = host.getConfig();
        var player = player(helper);
        var menu = new ExtendedInterfaceMenu(ExtendedAEMenuTypes.INTERFACE, 0, player.getInventory(), host);
        menu.setPage(1);
        Slot hotbar = menu.getSlots(SlotSemantics.PLAYER_HOTBAR).getFirst();
        player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 5));
        var stored = snapshot(storage);
        var configured = snapshot(config);
        stored.set(18, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 5L));
        menu.clicked(hotbar.index, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(stored.equals(snapshot(storage)) && configured.equals(snapshot(config))
                        && player.getInventory().items.stream().allMatch(ItemStack::isEmpty)
                        && menu.getCarried().isEmpty() && physicalItems(menu, player, storage, Items.IRON_INGOT) == 5L,
                "Player Shift inserts into the active page only, conserving items and leaving both-page filters alone");

        // Wrapped fluid caches reject item placement, leaving only the current-page ghost fallback.
        for (int i = 18; i < storage.size(); i++) {
            storage.setStack(i, new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000L));
        }
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
        helper.assertTrue(menu.slots.stream().filter(slot -> !menu.isPlayerSideSlot(slot) && slot.isActive())
                        .noneMatch(slot -> slot.mayPlace(player.getInventory().getItem(0))),
                "Ghost fallback fixture has no active real destination for player diamonds");
        stored = snapshot(storage);
        configured = snapshot(config);
        configured.set(18, new GenericStack(AEItemKey.of(Items.DIAMOND), 3L));
        var inventory = player.getInventory().items.stream().map(ItemStack::copy).toList();
        ItemStack carried = menu.getCarried().copy();
        menu.clicked(hotbar.index, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(stored.equals(snapshot(storage)) && configured.equals(snapshot(config)),
                "No-real-target Shift writes only the active filter and never a hidden cache or filter");
        for (int i = 0; i < inventory.size(); i++) {
            helper.assertTrue(ItemStack.matches(inventory.get(i), player.getInventory().getItem(i)),
                    "Ghost fallback leaves real player inventory unchanged at slot " + i);
        }
        helper.assertTrue(ItemStack.matches(carried, menu.getCarried())
                        && physicalItems(menu, player, storage, Items.DIAMOND) == 3L,
                "Ghost fallback copies a filter without consuming or duplicating real items");

        for (int i = 18; i < config.size(); i++) {
            config.setStack(i, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 7L));
        }
        assertUnchanged(helper, menu, player, storage,
                () -> menu.clicked(hotbar.index, 0, ClickType.QUICK_MOVE, player));
        helper.assertTrue(config.getStack(0) == null && config.getStack(17) == null,
                "A full active filter page cannot fall back to the empty hidden page");
        helper.succeed();
    }

    public void nativeAe2InterfaceRemainsEditable(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, AEBlocks.INTERFACE.block().defaultBlockState());
        InterfaceBlockEntity host = helper.getBlockEntity(BlockPos.ZERO);
        var player = player(helper);
        host.openMenu(player, MenuLocators.forBlockEntity(host));
        helper.assertTrue(player.containerMenu.getClass() == InterfaceMenu.class
                        && player.containerMenu.getType() == InterfaceMenu.TYPE,
                "Native AE2 interface keeps its native factory");
        var menu = (InterfaceMenu) player.containerMenu;
        var config = (FakeSlot) menu.getSlots(SlotSemantics.CONFIG).getFirst();
        menu.setFilter(config.index, Items.IRON_INGOT.getDefaultInstance());
        config.increase(Items.IRON_INGOT.getDefaultInstance());
        helper.assertTrue(host.getConfig().getAmount(0) == 2L, "Native AE2 filter and increase still work");
        menu.openSetAmountMenu(0);
        ((SetStockAmountMenu) player.containerMenu).confirm(7);
        helper.assertTrue(host.getConfig().getAmount(0) == 7L && player.containerMenu.getType() == InterfaceMenu.TYPE,
                "Native AE2 amount confirmation keeps its native return route");
        menu = (InterfaceMenu) player.containerMenu;
        menu.setCarried(new ItemStack(Items.IRON_INGOT, 3));
        var slot = menu.getSlots(SlotSemantics.STORAGE).getFirst();
        menu.clicked(slot.index, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && host.getInterfaceLogic().getStorage().getAmount(0) == 3L,
                "Native AE2 cache accepts actual cursor insertion");
        menu.quickMoveStack(player, slot.index);
        helper.assertTrue(host.getInterfaceLogic().getStorage().getStack(0) == null
                        && physicalItems(menu, player, host.getInterfaceLogic().getStorage(), Items.IRON_INGOT) == 3L,
                "Native AE2 cache extraction conserves the inserted resources");
        helper.succeed();
    }

    public void nativeExtendedInterfaceRemainsEditable(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, EAESingletons.EX_INTERFACE.defaultBlockState());
        TileExInterface host = helper.getBlockEntity(BlockPos.ZERO);
        var player = player(helper);
        host.openMenu(player, MenuLocators.forBlockEntity(host));
        helper.assertTrue(player.containerMenu.getClass() == ContainerExInterface.class
                        && player.containerMenu.getType() == ContainerExInterface.TYPE,
                "Native EAE interface keeps its native factory");
        var menu = (ContainerExInterface) player.containerMenu;
        menu.setPage(1);
        menu.showPage(1);
        var config = (FakeSlot) menu.getConfigSlots().get(35);
        menu.setFilter(config.index, Items.IRON_INGOT.getDefaultInstance());
        config.increase(Items.IRON_INGOT.getDefaultInstance());
        helper.assertTrue(host.getConfig().getAmount(35) == 2L, "Native EAE filter editing remains effective");
        menu.openSetAmountMenu(35);
        var amountMenu = (SetStockAmountMenu) player.containerMenu;
        long expected = Math.min(123L, AEItemKey.of(Items.IRON_INGOT).getMaxStackSize());
        helper.assertTrue(expected > amountMenu.getInitialAmount(), "Native EAE quantity confirmation changes the initial filter amount");
        amountMenu.confirm(123);
        helper.assertTrue(new GenericStack(AEItemKey.of(Items.IRON_INGOT), expected).equals(host.getConfig().getStack(35))
                         && player.containerMenu.getType() == ContainerExInterface.TYPE,
                "Native EAE amount page applies its native item limit and keeps its native return route");
        menu = (ContainerExInterface) player.containerMenu;
        menu.showPage(1);
        var slot = menu.getSlots(ExSemantics.EX_8).getLast();
        menu.setCarried(new ItemStack(Items.IRON_INGOT, 3));
        menu.clicked(slot.index, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && host.getInterfaceLogic().getStorage().getAmount(35) == 3L,
                "Native EAE slot 35 still accepts actual insertion");
        menu.quickMoveStack(player, slot.index);
        helper.assertTrue(host.getInterfaceLogic().getStorage().getStack(35) == null
                        && physicalItems(menu, player, host.getInterfaceLogic().getStorage(), Items.IRON_INGOT) == 3L,
                "Native EAE extraction conserves the inserted resource");
        helper.succeed();
    }

    private static void assertUnchanged(GameTestHelper helper, AEBaseMenu menu, ServerPlayer player,
                                        GenericStackInv storage, Runnable operation) {
        List<GenericStack> stored = snapshot(storage);
        GenericStackInv config = ((InterfaceLogicHost) menu.getTarget()).getConfig();
        List<GenericStack> configured = snapshot(config);
        var inventory = player.getInventory().items.stream().map(ItemStack::copy).toList();
        ItemStack carried = menu.getCarried().copy();
        operation.run();
        for (int i = 0; i < storage.size(); i++) {
            helper.assertTrue(Objects.equals(stored.get(i), storage.getStack(i)),
                    "Rejected operation preserves cache key and amount at slot " + i);
        }
        for (int i = 0; i < config.size(); i++) {
            helper.assertTrue(Objects.equals(configured.get(i), config.getStack(i)),
                    "Rejected operation preserves configuration key and amount at slot " + i);
        }
        for (int i = 0; i < inventory.size(); i++) {
            helper.assertTrue(ItemStack.matches(inventory.get(i), player.getInventory().getItem(i)),
                    "Rejected operation preserves player inventory slot " + i);
        }
        helper.assertTrue(ItemStack.matches(carried, menu.getCarried()), "Rejected operation preserves cursor resources");
    }

    private static List<GenericStack> snapshot(GenericStackInv inventory) {
        List<GenericStack> stacks = new ArrayList<>();
        for (int i = 0; i < inventory.size(); i++) stacks.add(inventory.getStack(i));
        return stacks;
    }

    /** @author howxu <dev@howxu.cn> */
    private record MenuRoute(String blockId, MenuType<?> type, Class<? extends AEBaseMenu> menuClass,
                             String menuId) {
    }

    private static long physicalItems(AEBaseMenu menu, ServerPlayer player, GenericStackInv storage,
                                      Item item) {
        long amount = player.getInventory().items.stream().filter(stack -> stack.is(item))
                .mapToLong(ItemStack::getCount).sum();
        if (menu.getCarried().is(item)) amount += menu.getCarried().getCount();
        for (int i = 0; i < storage.size(); i++) {
            if (AEItemKey.of(item).equals(storage.getKey(i))) amount += storage.getAmount(i);
        }
        return amount;
    }

    private static long waterAmount(AEBaseMenu menu, ServerPlayer player, GenericStackInv storage) {
        long water = player.getInventory().items.stream().filter(stack -> stack.is(Items.WATER_BUCKET))
                .mapToLong(stack -> stack.getCount() * 1_000L).sum();
        if (menu.getCarried().is(Items.WATER_BUCKET)) water += menu.getCarried().getCount() * 1_000L;
        for (int i = 0; i < storage.size(); i++) {
            if (AEFluidKey.of(Fluids.WATER).equals(storage.getKey(i))) water += storage.getAmount(i);
        }
        return water;
    }

    private static long bucketCount(AEBaseMenu menu, ServerPlayer player) {
        return player.getInventory().items.stream()
                .filter(stack -> stack.is(Items.BUCKET) || stack.is(Items.WATER_BUCKET)).mapToLong(ItemStack::getCount).sum()
                + (menu.getCarried().is(Items.BUCKET) || menu.getCarried().is(Items.WATER_BUCKET)
                ? menu.getCarried().getCount() : 0L);
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var profile = new GameProfile(UUID.randomUUID(), "interface-menu-test");
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile,
                ClientInformation.createDefault());
        player.connection = new MenuTestConnection(player, profile);
        return player;
    }

    /**
     * Packet sink for a real server-side player and menu without a remote client.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class MenuTestConnection extends ServerGamePacketListenerImpl {
        private MenuTestConnection(ServerPlayer player, GameProfile profile) {
            super(player.level().getServer(), new Connection(PacketFlow.CLIENTBOUND), player,
                    CommonListenerCookie.createInitial(profile, false));
        }

        @Override
        public void send(Packet<?> packet) {
        }

        @Override
        public void send(Packet<?> packet, PacketSendListener listener) {
        }
    }
}
