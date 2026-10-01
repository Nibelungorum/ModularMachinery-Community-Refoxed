package cn.howxu.mmcr.publicapi.recipe.extension;
/** Open final-parallelism operation preparation callback. @author howxu <dev@howxu.cn> */
@FunctionalInterface
public interface OperationPlanner { OperationBatch create(long parallelism, ReservationsView reservations); }
