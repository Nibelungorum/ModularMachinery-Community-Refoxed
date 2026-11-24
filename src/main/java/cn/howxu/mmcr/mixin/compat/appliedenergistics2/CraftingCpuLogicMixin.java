package cn.howxu.mmcr.mixin.compat.appliedenergistics2;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.pattern.AEProcessingPattern;
import cn.howxu.mmcr.api.compat.appliedenergistics2.BulkPatternProvider;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.NativeCpuBulkBatch;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

/** Scales one successful standard push; AE still performs the dispatch and owns its job and waiting inventory.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
@Mixin(CraftingCpuLogic.class)
public abstract class CraftingCpuLogicMixin {
    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean mmcr$pushBatch(ICraftingProvider provider, IPatternDetails pattern, KeyCounter[] unit,
                                   Operation<Boolean> original, @Local(argsOnly = true) IEnergyService energy,
                                   @Local Map.Entry<?, ?> task, @Local LocalDoubleRef patternPower,
                                   @Local(ordinal = 0) KeyCounter outputs, @Local(ordinal = 1) KeyCounter containers) {
        // Subclasses can have their own payment/return contracts (e.g. provider-only overloaded patterns).
        if (pattern.getClass() != AEProcessingPattern.class) return original.call(provider, pattern, unit);
        var bulk = BulkPatternProvider.find(provider).orElse(null);
        if (bulk == null) return original.call(provider, pattern, unit);
        var progress = (CraftingTaskProgressAccessor) task.getValue();
        if (progress.mmcr$remaining() <= 1) return original.call(provider, pattern, unit);

        var cpu = (CraftingCpuLogic) (Object) this;
        double power = patternPower.get();
        long copies = NativeCpuBulkBatch.push(cpu.getInventory(), unit, progress.mmcr$remaining(), power,
                ae -> energy.extractAEPower(ae, Actionable.SIMULATE, PowerMultiplier.CONFIG),
                outputs, containers, cpu::getWaitingFor, cap -> bulk.suggestPatternCopies(pattern, unit, cap),
                inputs -> original.call(provider, pattern, inputs));
        if (copies > 1) {
            patternPower.set(power * copies);
            // AE handles the final copy, increments pushedPatterns once and runs its usual completion branch.
            progress.mmcr$remaining(progress.mmcr$remaining() - (copies - 1));
        }
        return copies != 0;
    }
}
