package cn.howxu.mmcr.publicapi.structure.level;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/** Factory-owned level category view; the input name is snapshotted by the core declaration.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface LevelTypeSpec {
    ResourceLocation id();
    /** Returns an independent Component snapshot, including siblings and supported nested contents.
     * Changes require a new declaration; editing this result does not edit the registered type.
     */
    Component displayName();
}
