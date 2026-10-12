package cn.howxu.mmcr;

import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.component.ComponentPredicate;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.client.recipe.MachineRecipeData;
import cn.howxu.mmcr.compat.emi.EmiFillPreflight;
import cn.howxu.mmcr.internal.menu.ItemBusMenu;
import cn.howxu.mmcr.internal.tile.ItemBusBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Runtime data and server menu assertions; no EMI classes, screen or tick timing.
 * @author howxu <dev@howxu.cn>
 */
public final class ItemBusFillRulesGameTest {
    private ItemBusFillRulesGameTest() { }

    public static void inputFillAllowedOutputFillRejected(GameTestHelper helper) {
        Inventory inventory = new Inventory(helper.makeMockPlayer(GameType.SURVIVAL));
        ItemBusMenu input = menu(helper, inventory, "item_input_bus", new BlockPos(0, 1, 0));
        ItemBusMenu output = menu(helper, inventory, "item_output_bus", new BlockPos(1, 1, 0));
        inventory.setItem(0, new ItemStack(Items.IRON_INGOT, 20));
        MachineRecipeData data = data(helper, new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 20, ItemStack.EMPTY));
        List<ItemStack> desired = List.of(new ItemStack(Items.IRON_INGOT, 20));
        helper.assertTrue(input.getSlot(0).mayPlace(desired.getFirst()), "Actual input slot must allow placement");
        helper.assertTrue(EmiFillPreflight.check(input, data, desired).isEmpty(), "Input bus fill is valid");
        helper.assertTrue(EmiFillPreflight.check(output, data, desired).isPresent(),
                "Output bus fill must be rejected by direction even though manual placement is permitted");
        helper.assertTrue(input.getSlot(0).getItem().isEmpty() && output.getSlot(0).getItem().isEmpty()
                && inventory.getItem(0).getCount() == 20, "Checking either direction must not move items");
        helper.succeed();
    }

    public static void componentStacksAndQuickMoveClearing(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Inventory inventory = new Inventory(player);
        ItemBusMenu menu = menu(helper, inventory, "item_input_bus", new BlockPos(0, 1, 0));
        ItemStack named = new ItemStack(Items.IRON_INGOT, 20);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("fill-component"));
        DataComponentPredicateSet components = new DataComponentPredicateSet(Map.of(
                DataComponents.CUSTOM_NAME, ComponentPredicate.exact(ComponentSerialization.CODEC.encodeStart(
                        helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),
                        Component.literal("fill-component")).getOrThrow())));
        MachineRecipeData data = data(helper, new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT),
                20, ItemStack.EMPTY, 1F, components, 1F));
        menu.owner().nativeItemHandler().setContents(0, named, 20);
        menu.owner().nativeItemHandler().setContents(5, new ItemStack(Items.GOLD_INGOT), 7);
        inventory.setItem(0, named.copy());
        inventory.setItem(1, new ItemStack(Items.IRON_INGOT, 20));
        List<ItemStack> desired = List.of(named.copyWithCount(40));
        helper.assertTrue(EmiFillPreflight.check(menu, data, desired).isEmpty(), "Matching components can supply two batches");
        helper.assertTrue(EmiFillPreflight.check(menu, data, List.of(named.copyWithCount(60))).isPresent(),
                "Plain ingots must not be counted as the named component candidate");
        helper.assertTrue(EmiFillPreflight.check(menu, data, List.of(new ItemStack(Items.IRON_INGOT, 20))).isPresent(),
                "An exact component constraint must reject an unpatched desired stack");
        helper.assertTrue(!menu.quickMoveStack(player, 0).isEmpty() && menu.getSlot(0).getItem().isEmpty(),
                "Native quickMove clears the occupied target before filling");
        helper.assertTrue(!menu.quickMoveStack(player, 5).isEmpty() && menu.getSlot(5).getItem().isEmpty(),
                "Native clearing also evacuates surplus bus slots");
        long matching = inventory.items.stream().filter(stack -> ItemStack.isSameItemSameComponents(stack, named))
                .mapToLong(ItemStack::getCount).sum();
        helper.assertTrue(matching == 40 && inventory.getItem(1).getCount() == 20,
                "QuickMove merges only same-component stacks and preserves the plain stack");
        helper.assertTrue(EmiFillPreflight.check(menu, data, desired).isEmpty(), "Cleared inventory still supplies the validated plan");
        helper.succeed();
    }

    public static void fullInventoryBlocksClearingWithoutMutation(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Inventory inventory = new Inventory(player);
        ItemBusMenu menu = menu(helper, inventory, "item_input_bus", new BlockPos(0, 1, 0));
        for (int i = 0; i < 36; i++) inventory.setItem(i, new ItemStack(Items.IRON_INGOT, 64));
        menu.owner().nativeItemHandler().setContents(5, new ItemStack(Items.GOLD_INGOT), 7);
        MachineRecipeData data = data(helper, new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 20, ItemStack.EMPTY));
        helper.assertTrue(EmiFillPreflight.check(menu, data, List.of(new ItemStack(Items.IRON_INGOT, 20))).isPresent(),
                "Clearing must fit before desired ingredients free inventory slots");
        helper.assertTrue(menu.quickMoveStack(player, 5).isEmpty() && menu.getSlot(5).getItem().getCount() == 7,
                "Real quickMove also cannot evacuate this bus stack");
        helper.assertTrue(inventory.items.stream().allMatch(stack -> stack.is(Items.IRON_INGOT) && stack.getCount() == 64),
                "Rejected fill/quickMove must preserve the full inventory");
        helper.succeed();
    }

    public static void oversizedUnusedBusStackRejectsFillWithoutMutation(GameTestHelper helper) {
        Inventory inventory = new Inventory(helper.makeMockPlayer(GameType.SURVIVAL));
        ItemBusMenu menu = menu(helper, inventory, "item_input_bus", new BlockPos(0, 1, 0));
        int unusedSlot = menu.busSlotCount() - 1;
        inventory.setItem(0, new ItemStack(Items.IRON_INGOT, 20));
        MachineRecipeData data = data(helper, new ItemRequirement(IOType.INPUT, Ingredient.of(Items.IRON_INGOT),
                20, ItemStack.EMPTY));
        List<ItemStack> desired = List.of(new ItemStack(Items.IRON_INGOT, 20));
        helper.assertTrue(menu.owner().nativeItemHandler().insertItem(unusedSlot,
                new ItemStack(Items.ENDER_PEARL, 16), false).isEmpty(), "Ordinary bus accepts the first native pearl stack");
        helper.assertTrue(EmiFillPreflight.check(menu, data, desired).isEmpty(), "A single native pearl stack can be cleared");
        helper.assertTrue(menu.owner().nativeItemHandler().insertItem(unusedSlot,
                new ItemStack(Items.ENDER_PEARL, 16), false).isEmpty(), "Ordinary bus accepts a second native pearl stack");
        ItemStack pearls = menu.getSlot(unusedSlot).getItem();
        helper.assertTrue(pearls.getCount() == 32 && pearls.getCount() > pearls.getMaxStackSize(),
                "The unused ordinary bus slot displays more pearls than one native extraction can remove");
        List<ItemStack> before = menu.slots.stream().map(slot -> slot.getItem().copy()).toList();
        Component error = EmiFillPreflight.check(menu, data, desired).orElseThrow();
        helper.assertTrue(error.getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals("emi.mmcr.transfer.oversized_bus_stack"),
                "Fill must reject the uncleareable unused slot with its concrete localized reason");
        for (int i = 0; i < before.size(); i++) {
            helper.assertTrue(ItemStack.matches(before.get(i), menu.getSlot(i).getItem()),
                    "Rejected fill must preserve every bus and inventory slot");
        }
        helper.assertTrue(menu.owner().nativeItemHandler().amount(unusedSlot) == 32L
                        && menu.getCarried().isEmpty() && desired.getFirst().getCount() == 20,
                "Rejected fill must preserve native pearl stock, cursor and desired quantities");
        helper.succeed();
    }

    private static ItemBusMenu menu(GameTestHelper helper, Inventory inventory, String block, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.BLOCKS.get(block).get().defaultBlockState());
        ItemBusBlockEntity owner = helper.getBlockEntity(pos);
        return new ItemBusMenu(1, inventory, owner);
    }

    private static MachineRecipeData data(GameTestHelper helper, MachineRequirement... inputs) {
        return MachineRecipeData.from(MachineRecipe.fromCanonical(MMCR.id("fill_rules"), MMCR.id("test_cube"),
                20, List.of(inputs), List.of(), List.of(), 0, 1, false, false, false, Set.of()), helper.getLevel().registryAccess());
    }
}
