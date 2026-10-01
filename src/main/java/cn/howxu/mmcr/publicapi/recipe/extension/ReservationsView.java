package cn.howxu.mmcr.publicapi.recipe.extension;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
/** Library-produced view of the core's shared planning reservations.
 * @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface ReservationsView {
    <R> @Nullable R resource(ResourceStore<R> storage, int slot);
    long amount(ResourceStore<?> storage, int slot);
    <R> boolean reserveExtract(ResourceStore<R> storage, int slot, R resource, long amount);
    <R> boolean reserveInsert(ResourceStore<R> storage, int slot, R resource, long amount);
    <R> long outputAvailable(ResourceStore<R> storage, R key, long capacity);
    <R> boolean reserveOutput(ResourceStore<R> storage, R key, long amount);
    long valueAvailable(LongStore storage, boolean insert);
    boolean reserveValue(LongStore storage, long amount, boolean insert);
    boolean reserveValueTotal(LongStore storage, long amount, boolean insert);
}
