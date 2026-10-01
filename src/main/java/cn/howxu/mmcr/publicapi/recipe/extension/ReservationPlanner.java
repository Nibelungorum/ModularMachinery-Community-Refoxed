package cn.howxu.mmcr.publicapi.recipe.extension;
/** Open shared-resource simulation callback, without committing any mutations.
 * @author howxu <dev@howxu.cn> */
@FunctionalInterface
public interface ReservationPlanner { ReservationCheck reserve(long parallelism, ReservationsView reservations); }
