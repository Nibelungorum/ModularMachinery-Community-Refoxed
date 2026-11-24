package cn.howxu.mmcr;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.HostedPatternInventory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.transfer.transaction.Transaction;

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
        try (Transaction root = Transaction.openRoot()) {
            helper.assertTrue(inventory.accept(pattern, new KeyCounter[]{raw}, 100, root), "Provisional delivery");
        }
        helper.assertTrue(raw.get(named) == 2_147_483_648L && inventory.isEmpty(), "Abort restores both owners");
        try (Transaction root = Transaction.openRoot()) {
            inventory.accept(pattern, new KeyCounter[]{raw}, 100, root);
            root.commit();
        }
        helper.assertTrue(!inventory.canAccept(key) && raw.isEmpty(), "First delivery closes admission");
        var firstSave = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        inventory.write(firstSave, 101);
        var reloadedFirst = new HostedPatternInventory(() -> {});
        reloadedFirst.read(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), firstSave.buildResult()));
        helper.assertTrue(!reloadedFirst.canAccept(key) && reloadedFirst.group(key).materials().get(named) == 2_147_483_648L,
                "Reload cannot turn a first delivery into permission to accept a second one");
        inventory.eligibility(inventory.group(key), true, 102);
        raw.add(iron, 3);
        try (Transaction root = Transaction.openRoot()) {
            inventory.accept(pattern, new KeyCounter[]{raw}, 106, root);
            root.commit();
        }
        var group = inventory.group(key);
        var debit = new KeyCounter();
        debit.add(iron, 2);
        try (Transaction root = Transaction.openRoot()) { inventory.consume(group, debit, root); }
        helper.assertTrue(inventory.group(key) == group, "Aborting payment restores the original group");
        try (Transaction root = Transaction.openRoot()) {
            inventory.consume(group, debit, root);
            root.commit();
        }
        var tag = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        inventory.write(tag, 107);
        var restored = new HostedPatternInventory(() -> {});
        restored.read(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag.buildResult()));
        restored.rebaseClock(900);
        var remainder = restored.group(key);
        helper.assertTrue(restored.canAccept(key) && remainder.materials().get(iron) == 1L
                && remainder.materials().get(named) == 2_147_483_648L && remainder.eligibleAge(900) == 5,
                "Only actual debit is removed; component identity, validation and active waiting age survive reload");
        helper.succeed();
    }
}
