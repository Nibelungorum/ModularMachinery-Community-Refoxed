package cn.howxu.mmcr.mixin.compat.appliedenergistics2;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternInterfaceCraftingMachine;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternProviderLogicBatchAccess;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.tile.PatternInterfaceHost;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Batches only processing patterns selected for an MMCR pattern-provider host.
 *
 * @author howxu <dev@howxu.cn>
 */
@Mixin(CraftingCpuLogic.class)
public abstract class CraftingCpuLogicMixin {
    @Shadow private ExecutingCraftingJob job;
    @Shadow @Final private ListCraftingInventory inventory;
    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE", target =
            "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean mmcr$pushBatchPattern(ICraftingProvider provider, IPatternDetails details, KeyCounter[] inputs,
                                          Operation<Boolean> original,
                                          @Local(argsOnly = true) IEnergyService energyService,
                                          @Local(argsOnly = true) Level level,
                                          @Local(ordinal = 0) KeyCounter expectedOutputs,
                                          @Local(ordinal = 1) KeyCounter expectedContainerItems) {
        PatternInterfaceCraftingMachine batchMachine = mmcr$batchMachine(provider, details);
        if (batchMachine == null) return original.call(provider, details, inputs);
        ExecutingCraftingJobTaskProgressAccessor progress = (ExecutingCraftingJobTaskProgressAccessor)
                ((ExecutingCraftingJobAccessor) job).mmcr$tasks().get(details);
        long batchSize = Math.min(progress.mmcr$value(), batchMachine.maxBatchSize(details));
        if (batchSize < 1L) return false;
        if (batchSize == 1L) return original.call(provider, details, inputs);

        KeyCounter extraOutputs = new KeyCounter();
        KeyCounter extraContainerItems = new KeyCounter();
        KeyCounter[] extraInputs = mmcr$extractInputs(details, batchSize - 1L, level, extraOutputs, extraContainerItems);
        if (extraInputs == null) return false;
        KeyCounter[] combined = new KeyCounter[inputs.length];
        for (int index = 0; index < inputs.length; index++) {
            combined[index] = new KeyCounter();
            combined[index].addAll(inputs[index]);
            combined[index].addAll(extraInputs[index]);
        }
        double patternPower = CraftingCpuHelper.calculatePatternPower(combined);
        if (energyService.extractAEPower(patternPower, Actionable.SIMULATE, PowerMultiplier.CONFIG) < patternPower - 0.01
                || !batchMachine.pushBatchPattern(details, combined, batchSize, null)) {
            CraftingCpuHelper.reinjectPatternInputs(inventory, extraInputs);
            return false;
        }

        // AE2 accounts for the first operation after this call returns successfully.
        energyService.extractAEPower(patternPower - CraftingCpuHelper.calculatePatternPower(inputs),
                Actionable.MODULATE, PowerMultiplier.CONFIG);
        expectedOutputs.addAll(extraOutputs);
        expectedContainerItems.addAll(extraContainerItems);
        progress.mmcr$setValue(progress.mmcr$value() - (batchSize - 1L));
        return true;
    }

    @Unique
    private PatternInterfaceCraftingMachine mmcr$batchMachine(ICraftingProvider provider,
                                                              IPatternDetails details) {
        if (!(details instanceof AEProcessingPattern) || !(provider instanceof PatternProviderLogic logic)) {
            return null;
        }
        PatternInterfaceHost host = ((PatternProviderLogicBatchAccess) logic).mmcr$patternInterfaceHost();
        if (host == null) return null;
        ICraftingMachine machine = host.craftingMachine();
        return machine instanceof PatternInterfaceCraftingMachine batchMachine ? batchMachine : null;
    }

    @Unique
    private KeyCounter[] mmcr$extractInputs(IPatternDetails details, long operations, Level level,
                                            KeyCounter expectedOutputs, KeyCounter expectedContainerItems) {
        KeyCounter[] combined = null;
        for (long operation = 0; operation < operations; operation++) {
            KeyCounter[] extracted = CraftingCpuHelper.extractPatternInputs(details, inventory, level,
                    expectedOutputs, expectedContainerItems);
            if (extracted == null) {
                if (combined != null) CraftingCpuHelper.reinjectPatternInputs(inventory, combined);
                return null;
            }
            if (combined == null) {
                combined = extracted;
            } else {
                for (int index = 0; index < combined.length; index++) combined[index].addAll(extracted[index]);
            }
        }
        return combined;
    }
}
