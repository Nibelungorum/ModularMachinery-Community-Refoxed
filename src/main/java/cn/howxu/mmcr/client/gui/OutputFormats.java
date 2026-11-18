package cn.howxu.mmcr.client.gui;

import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.internal.api.facade.recipe.OutputAdapters;
import cn.howxu.mmcr.publicapi.recipe.OutputKind;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/** Client registration for addon output text and optional item icons, shared by controller and Jade.
 * @author howxu <dev@howxu.cn>
 */
public final class OutputFormats {
    private OutputFormats() {}
    private static final Map<OutputType<?>, BiFunction<MachineOutput, Long, Line>> FORMATS = new IdentityHashMap<>();

    public record Line(Component text, List<Component> tooltip, ItemStack icon) {
        public Line { tooltip = List.copyOf(tooltip); icon = icon.copy(); }
        @Override public ItemStack icon() { return icon.copy(); }
    }

    public static synchronized <T> void register(OutputKind<T> kind, BiFunction<T, Long, Line> format) {
        var type = OutputRegistry.typeFor(kind.id());
        if (type == null) throw new IllegalArgumentException("Unregistered output " + kind.id());
        if (FORMATS.putIfAbsent(type, (output, amount) -> kind.payload(OutputAdapters.wrap(output))
                .map(value -> format.apply(value, amount)).orElse(null)) != null)
            throw new IllegalArgumentException("Duplicate output formatter " + kind.id());
    }

    public static synchronized Line line(MachineOutput output, long amount) {
        var format = FORMATS.get(output.outputType());
        return format == null ? null : format.apply(output, amount);
    }
}
