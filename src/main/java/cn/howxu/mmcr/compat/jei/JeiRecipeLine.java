package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementType;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** A metadata row with optional JEI indexing, supplied by the integration that owns its representation.
 * @author howxu <dev@howxu.cn>
 */
public interface JeiRecipeLine {
    void draw(GuiGraphicsExtractor graphics, int x, int y, int width);
    default Optional<Component> tooltip() { return Optional.empty(); }
    default void index(IRecipeLayoutBuilder builder) {}

    final class Registry {
        private static final Map<RequirementType<?>, Function<MachineRequirement, JeiRecipeLine>> providers = new IdentityHashMap<>();
        private Registry() {}
        public static <R extends MachineRequirement> void register(RequirementType<R> type, Class<R> payload,
                                                                   Function<R, JeiRecipeLine> provider) {
            if (providers.putIfAbsent(type, value -> provider.apply(payload.cast(value))) != null) {
                throw new IllegalStateException("Duplicate JEI metadata provider for " + type.id());
            }
        }
        public static boolean contains(MachineRequirement requirement) { return providers.containsKey(requirement.type()); }
        public static List<JeiRecipeLine> lines(MachineRecipeDisplay display) {
            return display.recipe().runtimeRequirements().stream().filter(Registry::contains)
                    .sorted(Comparator.comparingInt(r -> r.io().isInput() ? 0 : 1))
                    .map(r -> providers.get(r.type()).apply(r)).toList();
        }
    }
}
