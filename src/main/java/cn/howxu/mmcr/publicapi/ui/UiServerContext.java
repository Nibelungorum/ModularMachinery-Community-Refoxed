package cn.howxu.mmcr.publicapi.ui;

import cn.howxu.mmcr.publicapi.behavior.MachineContext;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.ApiStatus;

/** Server-thread callback context; do not retain it for asynchronous world access.
 * The machine's data storage may be unavailable.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface UiServerContext {
    ServerPlayer player();
    MachineContext machine();
    Optional<String> laneId();
}
