package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineOutputAmount;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.mekanism.MekanismRecipeTypes;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatOutput;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatRequirement;
import cn.howxu.mmcr.internal.sync.MachineRecipeSyncCodec;
import cn.howxu.mmcr.util.SaturatingLong;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime-owned recipe information intended for controller screens.
 *
 * @author howxu <dev@howxu.cn>
 */
public record ControllerRecipePresentation(List<MachineOutputAmount> outputs,
                                            long energyInputPerTick,
                                            long energyOutputPerTick,
                                            double heatOutputPerTick,
                                            int durationTicks,
                                            long parallelism) {
    public static final int MAX_OUTPUTS = 65;

    public ControllerRecipePresentation {
        outputs = List.copyOf(outputs == null ? List.of() : outputs);
        if (energyInputPerTick < 0L || energyOutputPerTick < 0L
                || !Double.isFinite(heatOutputPerTick) || heatOutputPerTick < 0D
                || durationTicks < 0 || parallelism < 0L) {
            throw new IllegalArgumentException("Invalid controller recipe presentation values");
        }
    }

    public ControllerRecipePresentation(List<MachineOutputAmount> outputs, long energyInputPerTick,
                                        long energyOutputPerTick, double heatOutputPerTick) {
        this(outputs, energyInputPerTick, energyOutputPerTick, heatOutputPerTick, 0, 0L);
    }

    public static ControllerRecipePresentation empty() {
        return new ControllerRecipePresentation(List.of(), 0L, 0L, 0D, 0, 0L);
    }

    public static ControllerRecipePresentation from(CraftingRuntime runtime) {
        if (runtime == null || !runtime.active()) return empty();
        long parallelism = runtime.parallelism();
        if (parallelism < 1L) return empty();

        List<MachineOutputAmount> outputs = new ArrayList<>();
        double heat = 0D;
        boolean foundHeatOutput = false;
        for (MachineOutput output : runtime.activeOutputs()) {
            if (output == null) continue;
            if (MekanismRecipeTypes.HEAT.equals(output.outputType().id())) {
                if (output instanceof LoadedHeatOutput(double heat1)) {
                    heat = saturatingAdd(heat, saturatingMultiply(heat1, parallelism));
                    foundHeatOutput = true;
                }
                continue;
            }
            long amount = MachineOutput.scaledAmount(output);
            if (amount <= 0L) continue;
            if (outputs.size() < MAX_OUTPUTS) {
                if (output instanceof MachineOutput.ItemOutput item) {
                    output = new MachineOutput.ItemOutput(item.resolvedStack(), item.chance(), item.components());
                }
                outputs.add(new MachineOutputAmount(output, SaturatingLong.multiply(amount, parallelism)));
            }
        }
        if (!foundHeatOutput) {
            for (MachineRequirement requirement : runtime.activeRequirements()) {
                if (!(requirement instanceof LoadedHeatRequirement loadedHeat)
                        || requirement.io() != RecipeModifier.IOType.OUTPUT) continue;
                heat = saturatingAdd(heat, saturatingMultiply(loadedHeat.heat().value(), parallelism));
            }
        }

        long energyInput = 0L;
        long energyOutput = 0L;
        for (MachineRequirement requirement : runtime.activeRequirements()) {
            if (!(requirement instanceof EnergyRequirement energy)) continue;
            long amount = SaturatingLong.multiply(energy.fePerTick(), parallelism);
            if (energy.io() == RecipeModifier.IOType.OUTPUT) {
                energyOutput = SaturatingLong.add(energyOutput, amount);
            } else {
                energyInput = SaturatingLong.add(energyInput, amount);
            }
        }
        return new ControllerRecipePresentation(outputs, energyInput, energyOutput, heat, runtime.totalTick(), parallelism);
    }

    public static void write(RegistryFriendlyByteBuf buf, ControllerRecipePresentation presentation) {
        ControllerRecipePresentation value = presentation == null ? empty() : presentation;
        if (value.outputs().size() > MAX_OUTPUTS) {
            throw new IllegalArgumentException("Too many controller recipe outputs");
        }
        buf.writeVarInt(value.outputs().size());
        for (MachineOutputAmount output : value.outputs()) {
            buf.writeLong(output.amount());
            MachineRecipeSyncCodec.writeOutput(buf, output.output());
        }
        buf.writeLong(value.energyInputPerTick());
        buf.writeLong(value.energyOutputPerTick());
        buf.writeDouble(value.heatOutputPerTick());
        buf.writeVarInt(value.durationTicks());
        buf.writeVarLong(value.parallelism());
    }

    public static ControllerRecipePresentation read(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_OUTPUTS) {
            throw new IllegalArgumentException("Invalid controller recipe output count: " + count);
        }
        List<MachineOutputAmount> outputs = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            long amount = buf.readLong();
            if (amount < 0L) throw new IllegalArgumentException("Invalid controller recipe output amount");
            outputs.add(new MachineOutputAmount(MachineRecipeSyncCodec.readOutput(buf), amount));
        }
        return new ControllerRecipePresentation(outputs, buf.readLong(), buf.readLong(), buf.readDouble(),
                buf.readVarInt(), buf.readVarLong());
    }

    private static double saturatingMultiply(double value, long multiplier) {
        if (value <= 0D || multiplier <= 0L) return 0D;
        double result = value * multiplier;
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }

    private static double saturatingAdd(double first, double second) {
        double result = first + second;
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }
}
