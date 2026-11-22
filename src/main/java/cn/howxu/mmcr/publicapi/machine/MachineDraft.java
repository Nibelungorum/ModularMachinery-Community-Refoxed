package cn.howxu.mmcr.publicapi.machine;

import cn.howxu.mmcr.publicapi.behavior.MachineContext;
import cn.howxu.mmcr.publicapi.behavior.RecipeHooks;
import cn.howxu.mmcr.publicapi.behavior.TickHooks;
import cn.howxu.mmcr.publicapi.network.RequestHandler;
import cn.howxu.mmcr.publicapi.network.FailureHandler;
import cn.howxu.mmcr.publicapi.recipe.modifier.SmartModifierSpec;
import cn.howxu.mmcr.publicapi.runtime.RecipeFailureMode;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/** Producer-created configuration; consumers must not implement it. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface MachineDraft {
    MachineDraft displayNameKey(String key);
    MachineDraft recipePool(ResourceLocation... ids);
    MachineDraft controller(Consumer<ControllerOptions> configure);
    MachineDraft appearance(Consumer<AppearanceOptions> configure);
    MachineDraft factory(Consumer<FactoryOptions> configure);
    MachineDraft recipeBehavior(Consumer<RecipeHooks> configure);
    /**
     * Binds the selected methods to one new instance per controller, shared by all of its factory lanes.
     * For example: {@code recipeBehavior(Heater::new, h -> h.beforeStart(Heater::start).recipeTick(Heater::heat))}.
     * Pausing or reforming the structure retains the instance; unloading, removal or a different machine
     * binding discards it. Persist selected fields explicitly, e.g. via {@link MachineContext#dataStorage()}.
     */
    <T> MachineDraft recipeBehavior(Supplier<T> factory, Consumer<RecipeHooks.ForInstance<T>> configure);
    MachineDraft tickBehavior(Consumer<TickHooks> configure);
    MachineDraft preServerTick(Consumer<MachineContext> callback);
    MachineDraft preServerTick(Supplier<? extends Consumer<MachineContext>> factory);
    MachineDraft postServerTick(Consumer<MachineContext> callback);
    MachineDraft postServerTick(Supplier<? extends Consumer<MachineContext>> factory);
    MachineDraft role(MachineKind kind);
    MachineDraft acceptedModule(ResourceLocation id);
    MachineDraft networkInterface(int maxCount, int maxConnections);
    MachineDraft allowNetworkMachine(ResourceLocation id);
    MachineDraft maxParallelism(long amount);
    MachineDraft parallelizable(boolean enabled);
    /** Allows this registered effect bundle from upgrade items or structure replacements. */
    MachineDraft allowModifier(ResourceLocation id);
    MachineDraft allowMultithreading();
    MachineDraft allowMultithreading(boolean enabled);
    MachineDraft maxParallelAmount(int amount);
    MachineDraft smartInterface(SmartInterfaceSpec type);
    MachineDraft shareSmartInterfaces();
    MachineDraft shareSmartInterfaces(boolean enabled);
    MachineDraft smartInterfaceModifier(SmartModifierSpec modifier);
    MachineDraft runningSound(ResourceLocation id);
    MachineDraft finishSound(ResourceLocation id);
    MachineDraft failureAction(RecipeFailureMode mode);
    MachineDraft requestProcess(ResourceLocation id, RequestHandler handler);
    MachineDraft requestFailed(ResourceLocation id, FailureHandler handler);
    MachineSpec build();
}
