package cn.howxu.mmcr.api.presentation;

import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.NbtContents;
import net.minecraft.network.chat.contents.SelectorContents;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Snapshot ownership for serializable component declarations, including nested contents and style.
 * @author howxu <dev@howxu.cn>
 */
public final class ComponentSnapshots {
    private ComponentSnapshots() { }

    public static Component copy(Component value) {
        MutableComponent result = MutableComponent.create(copyContents(value.getContents()));
        Style style = value.getStyle();
        HoverEvent hover = style.getHoverEvent();
        if (hover != null && hover.getAction() == HoverEvent.Action.SHOW_TEXT) {
            // Style's equal-value shortcut would retain the original mutable hover subtree.
            style = style.withHoverEvent(null).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    copy(hover.getValue(HoverEvent.Action.SHOW_TEXT))));
        } else if (hover != null && hover.getAction() == HoverEvent.Action.SHOW_ENTITY) {
            var info = hover.getValue(HoverEvent.Action.SHOW_ENTITY);
            style = style.withHoverEvent(null).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY,
                    new HoverEvent.EntityTooltipInfo(info.type, info.id, info.name.map(ComponentSnapshots::copy))));
        } else if (hover != null && hover.getAction() == HoverEvent.Action.SHOW_ITEM) {
            style = style.withHoverEvent(null).withHoverEvent(HoverEvent.CODEC.parse(JsonOps.INSTANCE,
                    HoverEvent.CODEC.encodeStart(JsonOps.INSTANCE, hover).getOrThrow()).getOrThrow());
        }
        result.setStyle(style);
        value.getSiblings().forEach(sibling -> result.append(copy(sibling)));
        return result;
    }

    private static ComponentContents copyContents(ComponentContents contents) {
        return switch (contents) {
            case TranslatableContents text -> {
                Object[] arguments = text.getArgs().clone();
                for (int index = 0; index < arguments.length; index++) {
                    if (arguments[index] instanceof Component component) arguments[index] = copy(component);
                }
                yield new TranslatableContents(text.getKey(), text.getFallback(), arguments);
            }
            case SelectorContents selector -> new SelectorContents(selector.getPattern(), selector.getSeparator().map(ComponentSnapshots::copy));
            case NbtContents nbt -> new NbtContents(nbt.getNbtPath(), nbt.isInterpreting(),
                    nbt.getSeparator().map(ComponentSnapshots::copy), nbt.getDataSource());
            default -> ComponentSerialization.CODEC.parse(JsonOps.INSTANCE,
                    ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, MutableComponent.create(contents)).getOrThrow())
                    .getOrThrow().getContents();
        };
    }
}
