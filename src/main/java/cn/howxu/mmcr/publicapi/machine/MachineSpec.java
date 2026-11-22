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
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** Read-only core declaration view; consumers must not implement it. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface MachineSpec {
    Identifier id();
    List<Identifier> recipePoolIds();
    Identifier recipePoolId();
    String displayNameKey();
    ControllerOptions.View controller();
    AppearanceOptions.View appearance();
    FactoryOptions.View factory();
    MachineKind role();
    Set<Identifier> acceptedModuleIds();
    NetworkSettings networkInterface();
    long maxParallelism();
    int maxParallelAmount();
    boolean parallelizable();
    Set<Identifier> allowedModifierIds();
    boolean allowMultithreading();
    boolean shareSmartInterfaces();
    RecipeFailureMode failureAction();
    Map<String, SmartInterfaceSpec> smartInterfaceTypes();
    List<SmartModifierSpec> smartInterfaceModifiers();
    @Nullable Identifier runningSoundId();
    @Nullable Identifier finishSoundId();
    Optional<RecipeHooksView> recipeHooks();
    Optional<TickHooksView> tickHooks();
    Set<Identifier> requestProcessorIds();
    Set<Identifier> requestFailureIds();
    Map<Identifier, RequestHandler> requestProcessors();
    Map<Identifier, FailureHandler> requestFailures();

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
