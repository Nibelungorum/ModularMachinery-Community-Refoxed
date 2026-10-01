package cn.howxu.mmcr.publicapi.presentation;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/** Live Jade handle supplied by MMCR, a no-op when unavailable.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface JadeText {
    void append(ResourceLocation lineId, Component text);
    void appendAfter(ResourceLocation lineId, ResourceLocation afterLineId, Component text);
    void replace(ResourceLocation lineId, Component text);
    void remove(ResourceLocation lineId);
    void clear();
}
