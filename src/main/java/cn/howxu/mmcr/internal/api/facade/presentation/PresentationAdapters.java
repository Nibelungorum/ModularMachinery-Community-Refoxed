package cn.howxu.mmcr.internal.api.facade.presentation;

import cn.howxu.mmcr.api.controller.ControllerRuntimeContext;
import cn.howxu.mmcr.api.controller.ControllerScreenText;
import cn.howxu.mmcr.api.controller.ControllerScreenTextRegistry;
import cn.howxu.mmcr.api.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.capability.presentation.CapabilityDisplay;
import cn.howxu.mmcr.api.machine.definition.DisplayStack;
import cn.howxu.mmcr.publicapi.presentation.ControllerText;
import cn.howxu.mmcr.publicapi.presentation.ControllerTextContext;
import cn.howxu.mmcr.publicapi.presentation.ControllerTextHandler;
import cn.howxu.mmcr.publicapi.presentation.ControllerTexts;
import cn.howxu.mmcr.publicapi.presentation.DisplayStackView;
import cn.howxu.mmcr.publicapi.presentation.IoDisplay;
import cn.howxu.mmcr.publicapi.presentation.JadeText;
import cn.howxu.mmcr.publicapi.presentation.TextScope;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.Objects;
import java.util.Optional;

/** Forwards all text operations and registry semantics to authoritative core handles.
 * @author howxu <dev@howxu.cn>
 */
public final class PresentationAdapters {
    private PresentationAdapters() {}
    public static ControllerText wrap(ControllerScreenText value) { return new TextAdapter(Objects.requireNonNull(value)); }
    public static JadeText wrap(cn.howxu.mmcr.api.controller.JadeText value) { return new JadeAdapter(Objects.requireNonNull(value)); }
    public static ControllerTextContext wrap(ControllerRuntimeContext value) { return new ContextAdapter(value); }
    public static IoDisplay wrap(CapabilityDisplay value) { return new DisplayAdapter(value); }
    public static ControllerTexts.Registration register(ResourceLocation id, ControllerTextHandler handler) {
        Objects.requireNonNull(handler, "handler");
        var registration = ControllerScreenTextRegistry.register(id, context -> handler.apply(wrap(context)));
        return registration::unregister;
    }
    private static ControllerScreenTextScope scope(TextScope value) {
        return switch (value) {
            case CONTROLLER -> ControllerScreenTextScope.CONTROLLER;
            case OPERATION -> ControllerScreenTextScope.OPERATION;
        };
    }
    private record TextAdapter(ControllerScreenText delegate) implements ControllerText {
        public void append(TextScope s, ResourceLocation id, Component text) { delegate.append(scope(s), id, text); }
        public void appendAfter(TextScope s, ResourceLocation id, ResourceLocation after, Component text) { delegate.appendAfter(scope(s), id, after, text); }
        public void replace(ResourceLocation id, Component text) { delegate.replace(id, text); }
        public void remove(TextScope s, ResourceLocation id) { delegate.remove(scope(s), id); }
        public void clear(TextScope s) { delegate.clear(scope(s)); }
    }
    private record JadeAdapter(cn.howxu.mmcr.api.controller.JadeText delegate) implements JadeText {
        public void append(ResourceLocation id, Component text) { delegate.append(id, text); }
        public void appendAfter(ResourceLocation id, ResourceLocation after, Component text) { delegate.appendAfter(id, after, text); }
        public void replace(ResourceLocation id, Component text) { delegate.replace(id, text); }
        public void remove(ResourceLocation id) { delegate.remove(id); }
        public void clear() { delegate.clear(); }
    }
    private record ContextAdapter(ControllerRuntimeContext delegate) implements ControllerTextContext {
        public ResourceLocation machineId() { return delegate.machineId(); }
        public BlockPos controllerPos() { return delegate.controllerPos(); }
        public ControllerText screenText() { return wrap(delegate.screenText()); }
    }
    private record IconAdapter(DisplayStack delegate) implements DisplayStackView {
        public ItemStack stack() { return delegate.stack().copy(); }
    }
    private record DisplayAdapter(CapabilityDisplay delegate) implements IoDisplay {
        public String label() { return delegate.label(); }
        public String value() { return delegate.value(); }
        public String unit() { return delegate.unit(); }
        public Optional<DisplayStackView> icon() { return delegate.icon().map(IconAdapter::new); }
    }
}
