package cn.howxu.mmcr;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.HostedPatternInventory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** @author HowXu {@literal <dev@howxu.cn>} */
public final class HostedPatternInventoryGameTest {
    public static void atomicGatePaymentAndReload(GameTestHelper helper) {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var namedStack = new ItemStack(Items.IRON_INGOT);
        namedStack.set(DataComponents.CUSTOM_NAME, Component.literal("retained"));
        var named = AEItemKey.of(namedStack);
        var encoded = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(iron, 1)),
                List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)));
        var pattern = PatternDetailsHelper.decodePattern(encoded, helper.getLevel());
        var key = pattern.getDefinition();
        var inventory = new HostedPatternInventory(() -> {});
        var raw = new KeyCounter();
        raw.add(named, 2_147_483_648L);
        helper.assertTrue(inventory.accept(pattern, new KeyCounter[]{raw}, 100, Actionable.SIMULATE), "Provisional delivery");
        helper.assertTrue(raw.get(named) == 2_147_483_648L && inventory.isEmpty(), "Simulation leaves both owners unchanged");
        inventory.accept(pattern, new KeyCounter[]{raw}, 100, Actionable.MODULATE);
        helper.assertTrue(!inventory.canAccept(key) && raw.isEmpty(), "First delivery closes admission");
        var firstSave = new CompoundTag();
        inventory.write(firstSave, helper.getLevel().registryAccess(), 101);
        var reloadedFirst = new HostedPatternInventory(() -> {});
        reloadedFirst.read(firstSave, helper.getLevel().registryAccess());
        helper.assertTrue(!reloadedFirst.canAccept(key) && reloadedFirst.group(key).materials().get(named) == 2_147_483_648L,
                "Reload cannot turn a first delivery into permission to accept a second one");
        inventory.eligibility(inventory.group(key), true, 102);
        raw.add(iron, 3);
        inventory.accept(pattern, new KeyCounter[]{raw}, 106, Actionable.MODULATE);
        var group = inventory.group(key);
        var debit = new KeyCounter();
        debit.add(iron, 2);
        inventory.consume(group, debit, Actionable.SIMULATE);
        helper.assertTrue(inventory.group(key) == group, "Simulating payment retains the original group");
        inventory.consume(group, debit, Actionable.MODULATE);
        var tag = new CompoundTag();
        inventory.write(tag, helper.getLevel().registryAccess(), 107);
        var restored = new HostedPatternInventory(() -> {});
        restored.read(tag, helper.getLevel().registryAccess());
        restored.rebaseClock(900);
        var remainder = restored.group(key);
        helper.assertTrue(restored.canAccept(key) && remainder.materials().get(iron) == 1L
                && remainder.materials().get(named) == 2_147_483_648L && remainder.eligibleAge(900) == 5,
                "Only actual debit is removed; component identity, validation and active waiting age survive reload");
        helper.succeed();
    }
}
