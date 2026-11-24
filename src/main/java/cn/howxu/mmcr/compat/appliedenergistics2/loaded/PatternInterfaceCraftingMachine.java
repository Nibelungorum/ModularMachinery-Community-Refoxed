package cn.howxu.mmcr.compat.appliedenergistics2.loaded;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.List;

/** Accepts an entire standard delivery. Recipe starts are never performed on the provider's call stack.
 * @author howxu <dev@howxu.cn>
 */
public final class PatternInterfaceCraftingMachine implements ICraftingMachine {
    private final PatternInterfaceBlockEntity host;
    private boolean accepting;

    public PatternInterfaceCraftingMachine(PatternInterfaceBlockEntity host) { this.host = host; }

    @Override public PatternContainerGroup getCraftingMachineInfo() { return PatternContainerGroup.nothing(); }

    // A false here would let PatternProviderLogic fall back to generic adjacent inventories.
    @Override public boolean acceptsPlans() { return true; }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs, Direction ejectionDirection) {
        if (accepting || !host.acceptingHostedInputs() || !host.hostedPatterns().canAccept(pattern.getDefinition())) return false;
        accepting = true;
        try {
            if (!validInputs(pattern, inputs, host.getLevel())) return false;
            var hints = outputs(pattern.getOutputs());
            if (hints.isEmpty() || !host.hostedDispatcher().hasLane(pattern.getDefinition(), hints)) return false;
            var inventory = host.hostedPatterns();
            long revision = inventory.revision();
            try (var tx = Transaction.openRoot()) {
                if (!inventory.accept(pattern, inputs, host.getLevel().getGameTime(), tx)) return false;
                tx.commit();
                return true;
            } catch (RuntimeException failure) {
                // Root notification failure is not a rejected delivery: the CPU must not retry paid inputs.
                return revision != inventory.revision();
            }
        } finally {
            accepting = false;
        }
    }

    static boolean validInputs(IPatternDetails pattern, KeyCounter[] inputs, Level level) {
        if (!pattern.supportsPushInputsToExternalInventory() || inputs == null || inputs.length == 0) return false;
        var expected = pattern.getInputs();
        if (inputs.length != expected.length) return false;
        boolean any = false;
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] == null) return false;
            for (var entry : inputs[i]) {
                if (entry.getLongValue() <= 0 || !AE2ResourceFamilies.supportsKey(entry.getKey())
                        || !expected[i].isValid(entry.getKey(), level)) return false;
                any = true;
            }
        }
        return any;
    }

    static List<MachineOutput> outputs(List<GenericStack> stacks) {
        List<MachineOutput> hints = new ArrayList<>();
        for (GenericStack stack : stacks) {
            if (stack == null || stack.amount() <= 0) return List.of();
            var hint = AE2ResourceFamilies.patternOutput(new GenericStack(stack.what(), 1)).orElse(null);
            if (hint == null) return List.of();
            hints.add(hint);
        }
        return List.copyOf(hints);
    }
}
