package cn.howxu.mmcr.publicapi.recipe.extension;
import org.jetbrains.annotations.Nullable;
/** Callback result of a shared reservation simulation. @author howxu <dev@howxu.cn> */
public record ReservationCheck(@Nullable PlanStatus failure, @Nullable OutputSimulationView outputSimulation) {}
