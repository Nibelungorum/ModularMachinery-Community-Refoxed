package cn.howxu.mmcr.client.recipe;

import cn.howxu.mmcr.api.presentation.ComponentSnapshots;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

/** A whole metadata row, including its tooltip and cycling representative icons.
 * @author howxu <dev@howxu.cn>
 */
public final class RecipeMetadataRow {
    private final Supplier<Component> text;
    private final List<Component> tooltip;
    private final List<ItemStack> icons;

    public RecipeMetadataRow(Component text, List<Component> tooltip, List<ItemStack> icons) {
        Component snapshot = ComponentSnapshots.copy(text);
        this.text = () -> snapshot;
        this.tooltip = tooltip.stream().map(ComponentSnapshots::copy).toList();
        this.icons = icons.stream().map(ItemStack::copy).toList();
    }

    private RecipeMetadataRow(Supplier<Component> text) {
        this.text = text;
        tooltip = List.of();
        icons = List.of();
    }

    /** Resolves configuration-dependent labels when drawn, even if a widget caches its rows. */
    public static RecipeMetadataRow deferredText(Supplier<Component> text) { return new RecipeMetadataRow(text); }

    public Component text() { return ComponentSnapshots.copy(text.get()); }
    public List<Component> tooltip() { return tooltip.stream().map(ComponentSnapshots::copy).toList(); }
    public List<ItemStack> icons() { return icons.stream().map(ItemStack::copy).toList(); }
}
