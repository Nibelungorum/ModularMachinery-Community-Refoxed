package cn.howxu.mmcr.publicapi.machine;

import cn.howxu.mmcr.publicapi.network.NetworkSettings;
import cn.howxu.mmcr.publicapi.network.RequestHandler;
import cn.howxu.mmcr.publicapi.network.FailureHandler;
import cn.howxu.mmcr.publicapi.recipe.modifier.SmartModifierSpec;
import cn.howxu.mmcr.publicapi.runtime.RecipeFailureMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** Read-only core declaration view; consumers must not implement it. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface MachineSpec {
    ResourceLocation id();
    List<ResourceLocation> recipePoolIds();
    ResourceLocation recipePoolId();
    String displayNameKey();
    ControllerOptions.View controller();
    AppearanceOptions.View appearance();
    FactoryOptions.View factory();
    MachineKind role();
    Set<ResourceLocation> acceptedModuleIds();
    NetworkSettings networkInterface();
    long maxParallelism();
    int maxParallelAmount();
    boolean parallelizable();
    boolean allowModifiers();
    boolean allowMultithreading();
    boolean shareSmartInterfaces();
    RecipeFailureMode failureAction();
    Map<String, SmartInterfaceSpec> smartInterfaceTypes();
    List<SmartModifierSpec> smartInterfaceModifiers();
    @Nullable ResourceLocation runningSoundId();
    @Nullable ResourceLocation finishSoundId();
    Optional<RecipeHooksView> recipeHooks();
    Optional<TickHooksView> tickHooks();
    Set<ResourceLocation> requestProcessorIds();
    Set<ResourceLocation> requestFailureIds();
    Map<ResourceLocation, RequestHandler> requestProcessors();
    Map<ResourceLocation, FailureHandler> requestFailures();

    /** Actual registered recipe hook presence, including explicit empty hooks. @author howxu <dev@howxu.cn> */
    @ApiStatus.NonExtendable
    interface RecipeHooksView {
        boolean hasIdleStart();
        boolean hasIdleEnd();
        boolean hasBeforeStart();
        boolean hasRecipeTick();
        boolean hasBeforeFinish();
        boolean hasPreServerTick();
        boolean hasPostServerTick();
    }

    /** Actual registered tick hook presence. @author howxu <dev@howxu.cn> */
    @ApiStatus.NonExtendable
    interface TickHooksView {
        boolean hasServerTick();
    }
}
