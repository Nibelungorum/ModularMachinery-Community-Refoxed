package cn.howxu.mmcr.publicapi.recipe.extension;
import org.jetbrains.annotations.ApiStatus;
/** Library-produced output-fit telemetry for a planned operation. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface OutputSimulationView { long requested(); long accepted(); Fit fit(); enum Fit { NONE, PARTIAL, FULL } }
