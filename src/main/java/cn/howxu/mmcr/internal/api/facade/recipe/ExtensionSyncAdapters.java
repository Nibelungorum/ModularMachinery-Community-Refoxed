package cn.howxu.mmcr.internal.api.facade.recipe;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.publicapi.recipe.extension.SyncPayloadCodec;
import java.util.function.Function;
/** Typed payload-to-carrier sync mapping, retaining the core bound validation.
 * @author howxu <dev@howxu.cn> */
final class ExtensionSyncAdapters {
    private ExtensionSyncAdapters() {}
    static <P, C> RecipeSyncCodec<C> adapt(SyncPayloadCodec<P> codec, Function<C, P> payload, Function<P, C> carrier) {
        return RecipeSyncCodec.of(codec.maxPayloadSize(), (buffer, value) -> codec.encode(buffer, payload.apply(value)),
                buffer -> carrier.apply(codec.decode(buffer)), value -> codec.validate(payload.apply(value)));
    }
}
