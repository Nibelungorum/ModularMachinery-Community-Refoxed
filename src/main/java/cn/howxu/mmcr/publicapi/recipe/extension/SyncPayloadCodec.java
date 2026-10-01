package cn.howxu.mmcr.publicapi.recipe.extension;
import net.minecraft.network.RegistryFriendlyByteBuf;
/** Open bounded payload serialization SPI. The core enforces the declared byte limit.
 * @author howxu <dev@howxu.cn> */
public interface SyncPayloadCodec<T> {
    void encode(RegistryFriendlyByteBuf buffer, T value);
    T decode(RegistryFriendlyByteBuf buffer);
    int maxPayloadSize();
    void validate(T value);
}
