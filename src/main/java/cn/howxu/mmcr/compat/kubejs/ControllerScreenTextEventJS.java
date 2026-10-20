package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.controller.ControllerRuntimeContext;
import cn.howxu.mmcr.api.controller.ControllerScreenText;
import cn.howxu.mmcr.api.controller.ControllerScreenTextHandler;
import cn.howxu.mmcr.api.controller.ControllerScreenTextScope;
import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.rhino.util.HideFromJS;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

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

    public ResourceLocation machineId() {
        return context.machineId();
    }

    public BlockPos controllerPos() {
        return context.controllerPos();
    }

    public void append(String scope, String lineId, Component text) {
        append(scope, parseNamespacedResourceLocation(lineId, "lineId"), text);
    }

    // Script strings must retain the explicit-namespace validation before ID conversion.
    @HideFromJS
    public void append(String scope, ResourceLocation lineId, Component text) {
        textHandle().append(parseScope(scope), lineId, requireText(text));
    }

    public void appendAfter(String scope, String lineId, String afterLineId, Component text) {
        appendAfter(scope, parseNamespacedResourceLocation(lineId, "lineId"),
                parseNamespacedResourceLocation(afterLineId, "afterLineId"), text);
    }

    @HideFromJS
    public void appendAfter(String scope, ResourceLocation lineId, ResourceLocation afterLineId, Component text) {
        textHandle().appendAfter(parseScope(scope), lineId, afterLineId, requireText(text));
    }

    public void replace(String lineId, Component text) {
        replace(parseNamespacedResourceLocation(lineId, "lineId"), text);
    }

    @HideFromJS
    public void replace(ResourceLocation lineId, Component text) {
        textHandle().replace(lineId, requireText(text));
    }

    public void appendTranslatable(String scope, String lineId, String key, Object... args) {
        appendTranslatable(scope, parseNamespacedResourceLocation(lineId, "lineId"), key, args);
    }

    @HideFromJS
    public void appendTranslatable(String scope, ResourceLocation lineId, String key, Object... args) {
        requireText(key, "key");
        if (args == null) throw new IllegalArgumentException("args must not be null");
        textHandle().append(parseScope(scope), lineId,
                Component.translatable(key, args));
    }

    public void appendAfterTranslatable(String scope, String lineId, String afterLineId, String key, Object... args) {
        appendAfterTranslatable(scope, parseNamespacedResourceLocation(lineId, "lineId"),
                parseNamespacedResourceLocation(afterLineId, "afterLineId"), key, args);
    }

    @HideFromJS
    public void appendAfterTranslatable(String scope, ResourceLocation lineId, ResourceLocation afterLineId, String key, Object... args) {
        requireText(key, "key");
        if (args == null) throw new IllegalArgumentException("args must not be null");
        textHandle().appendAfter(parseScope(scope), lineId, afterLineId, Component.translatable(key, args));
    }

    public void replaceTranslatable(String lineId, String key, Object... args) {
        replaceTranslatable(parseNamespacedResourceLocation(lineId, "lineId"), key, args);
    }

    @HideFromJS
    public void replaceTranslatable(ResourceLocation lineId, String key, Object... args) {
        requireText(key, "key");
        if (args == null) throw new IllegalArgumentException("args must not be null");
        textHandle().replace(lineId, Component.translatable(key, args));
    }

    public void remove(String scope, String lineId) {
        remove(scope, parseNamespacedResourceLocation(lineId, "lineId"));
    }

    @HideFromJS
    public void remove(String scope, ResourceLocation lineId) {
        textHandle().remove(parseScope(scope), lineId);
    }

    static ResourceLocation parseResourceLocation(String value, String name) {
        requireText(value, name);
        ResourceLocation identifier = ResourceLocation.tryParse(value);
        if (identifier == null) throw new IllegalArgumentException("Invalid " + name + ": " + value);
        return identifier;
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

    private static ResourceLocation parseNamespacedResourceLocation(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be null or blank");
        int separator = value.indexOf(':');
        if (separator <= 0) throw new IllegalArgumentException(name + " must have a namespace: " + value);
        if (separator == value.length() - 1) throw new IllegalArgumentException(name + " must have a path: " + value);
        return parseResourceLocation(value, name);
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
