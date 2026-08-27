package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.publicapi.controller.ControllerRuntimeContext;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenText;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextHandler;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * KubeJS-facing controller screen text runtime context.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ControllerScreenTextEventJS implements KubeEvent {
    private final ControllerRuntimeContext context;

    ControllerScreenTextEventJS(ControllerRuntimeContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    public Identifier machineId() {
        return context.machineId();
    }

    public BlockPos controllerPos() {
        return context.controllerPos();
    }

    public void append(String scope, String lineId, Component text) {
        textHandle().append(parseScope(scope), parseNamespacedIdentifier(lineId, "lineId"), requireText(text));
    }

    public void appendTranslatable(String scope, String lineId, String key, Object... args) {
        requireText(key, "key");
        if (args == null) throw new IllegalArgumentException("args must not be null");
        textHandle().append(parseScope(scope), parseNamespacedIdentifier(lineId, "lineId"),
                Component.translatable(key, args));
    }

    public void remove(String scope, String lineId) {
        textHandle().remove(parseScope(scope), parseNamespacedIdentifier(lineId, "lineId"));
    }

    static Identifier parseIdentifier(String value, String name) {
        requireText(value, name);
        try {
            return Identifier.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid " + name + ": " + value, exception);
        }
    }

    static ControllerScreenTextHandler handler(Consumer<ControllerScreenTextEventJS> handler) {
        if (handler == null) throw new IllegalArgumentException("handler must not be null");
        return context -> handler.accept(new ControllerScreenTextEventJS(context));
    }

    private static ControllerScreenTextScope parseScope(String value) {
        requireText(value, "scope");
        return switch (value) {
            case "controller" -> ControllerScreenTextScope.CONTROLLER;
            case "operation" -> ControllerScreenTextScope.OPERATION;
            default -> throw new IllegalArgumentException("Unknown controller screen text scope: " + value);
        };
    }

    private static Identifier parseNamespacedIdentifier(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be null or blank");
        int separator = value.indexOf(':');
        if (separator <= 0) throw new IllegalArgumentException(name + " must have a namespace: " + value);
        if (separator == value.length() - 1) throw new IllegalArgumentException(name + " must have a path: " + value);
        return parseIdentifier(value, name);
    }

    private static Component requireText(Component text) {
        if (text == null) throw new IllegalArgumentException("text must not be null");
        return text;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be null or blank");
        return value;
    }

    private ControllerScreenText textHandle() {
        return context.screenText();
    }
}
