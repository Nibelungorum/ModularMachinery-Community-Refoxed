package cn.howxu.mmcr.publicapi.runtime;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import java.util.List;

/** MMCR-provided simulation result; input flags alone do not prove overall success.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface IoSimulation {
    boolean inputsSatisfied();
    boolean energySatisfied();
    List<OutputAcceptance> outputs();
    @Nullable RuntimeFailure failure();
}
