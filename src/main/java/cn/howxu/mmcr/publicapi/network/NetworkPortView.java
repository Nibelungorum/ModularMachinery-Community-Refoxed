package cn.howxu.mmcr.publicapi.network;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.ApiStatus;
import java.util.List;

/** MMCR-produced live network port reference. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface NetworkPortView {
    BlockPos position();
    List<NodeView> connections();
}
