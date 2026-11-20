package cn.howxu.mmcr.api.machine.definition;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** One registration identity, shared by the callbacks that operate on the same controller-local object.
 * @author howxu <dev@howxu.cn>
 */
final class HookBinding<T> implements Supplier<T> {
    private final Supplier<? extends T> factory;

    HookBinding(Supplier<? extends T> factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    @Override
    public T get() {
        return Objects.requireNonNull(factory.get(), "hook factory returned null");
    }

    T instance(MachineBehaviorContext context) {
        return Objects.requireNonNull(context.controller(), "Instance hook requires a controller").hookInstance(this);
    }

    <C> BiConsumer<MachineBehaviorContext, C> bind(BiConsumer<T, C> callback) {
        Objects.requireNonNull(callback, "callback");
        return (owner, c) -> callback.accept(instance(owner), c);
    }

    static MachineBehavior.MachineCallback machineCallback(Supplier<? extends MachineBehavior.MachineCallback> factory) {
        var binding = new HookBinding<>(factory);
        return context -> binding.instance(context).accept(context);
    }
}
