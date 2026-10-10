package cn.howxu.mmcr.publicapi.ui;

import cn.howxu.mmcr.internal.api.facade.ui.UiProtocolAdapters;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.ApiStatus;

/** MMCR-produced typed request descriptor; codecs encode pure protocol data.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface UiRequestType<Q, R> {
    static <Q, R> UiRequestType<Q, R> of(Identifier id, int version,
            StreamCodec<RegistryFriendlyByteBuf, Q> requestCodec,
            StreamCodec<RegistryFriendlyByteBuf, R> responseCodec) {
        return UiProtocolAdapters.requestType(id, version, requestCodec, responseCodec);
    }

    Identifier id();
    int version();
}
