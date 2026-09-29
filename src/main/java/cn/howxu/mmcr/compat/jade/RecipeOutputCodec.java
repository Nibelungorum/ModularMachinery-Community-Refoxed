package cn.howxu.mmcr.compat.jade;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineOutputAmount;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Encodes and decodes the list of {@link MachineOutput} the controller exposes to Jade.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeOutputCodec {
    public static final String OUTPUT_KEY = "mmcr_recipe_output";
    private static final String OUTPUT_FIELD = "output";
    private static final String AMOUNT_FIELD = "amount";

    private RecipeOutputCodec() {}

    public static void write(CompoundTag data, List<MachineOutputAmount> outputs) {
        ListTag list = new ListTag();
        for (MachineOutputAmount output : outputs) {
            DataResult<Tag> encoded = MachineOutput.CODEC.encodeStart(NbtOps.INSTANCE,
                    templateForTransport(output.output()));
            encoded.result().ifPresent(tag -> {
                if (tag instanceof CompoundTag compound) {
                    CompoundTag entry = new CompoundTag();
                    entry.put(OUTPUT_FIELD, compound);
                    entry.putLong(AMOUNT_FIELD, output.amount());
                    list.add(entry);
                }
            });
        }
        if (list.isEmpty()) {
            data.remove(OUTPUT_KEY);
            return;
        }
        data.put(OUTPUT_KEY, list);
    }

    public static List<MachineOutputAmount> read(CompoundTag data) {
        if (data == null) return List.of();
        ListTag list = data.getList(OUTPUT_KEY, Tag.TAG_COMPOUND);
        List<MachineOutputAmount> decoded = new ArrayList<>(list.size());
        for (Tag element : list) {
            if (!(element instanceof CompoundTag compound)) continue;
            CompoundTag encodedOutput = compound.contains(OUTPUT_FIELD)
                    ? compound.getCompound(OUTPUT_FIELD) : compound;
            DataResult<MachineOutput> parsed = MachineOutput.CODEC.parse(NbtOps.INSTANCE, encodedOutput);
            parsed.result().ifPresent(output -> decoded.add(new MachineOutputAmount(output,
                    compound.contains(AMOUNT_FIELD) ? compound.getLong(AMOUNT_FIELD)
                            : MachineOutput.scaledAmount(output))));
        }
        return List.copyOf(decoded);
    }

    private static MachineOutput templateForTransport(MachineOutput output) {
        if (output instanceof MachineOutput.ItemOutput(net.minecraft.world.item.ItemStack stack, float chance1)) {
            stack.setCount(1);
            return new MachineOutput.ItemOutput(stack, chance1);
        }
        if (output instanceof MachineOutput.FluidOutput(net.neoforged.neoforge.fluids.FluidStack stack, float chance)) {
            stack.setAmount(1);
            return new MachineOutput.FluidOutput(stack, chance);
        }
        return output;
    }
}
