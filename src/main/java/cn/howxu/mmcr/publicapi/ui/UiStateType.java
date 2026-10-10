package cn.howxu.mmcr.publicapi.ui;

import cn.howxu.mmcr.internal.api.facade.ui.UiProtocolAdapters;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/** MMCR-produced descriptor for a latest-value state stream.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface UiStateType<T> {
    static <T> UiStateType<T> of(ResourceLocation id, int version, StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        return UiProtocolAdapters.stateType(id, version, codec);
    }

    ResourceLocation id();
    int version();
}
