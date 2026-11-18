package cn.howxu.mmcr.api.recipe.requirement;

import cn.howxu.mmcr.api.capability.async.AsyncCapabilityRequest;
import cn.howxu.mmcr.api.capability.async.AsyncResourceAction;
import cn.howxu.mmcr.api.capability.async.AsyncResourceValue;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Server-side resource alternatives for a tick. Only the resulting demand crosses to the worker.
 * @author howxu <dev@howxu.cn>
 */
@FunctionalInterface
public interface TickCapture {
    List<AsyncResourceValue> resources(Identifier family, IOType direction);

    record Demand(long amount, List<AsyncCapabilityRequest> requests) {
        public Demand {
            requests = List.copyOf(requests);
        }
    }

    static AsyncCapabilityRequest.Resource request(Identifier family, long parallelism, AsyncResourceValue value,
                                                  long amount, boolean insert) {
        return new AsyncCapabilityRequest.Resource(family, parallelism, List.of(new AsyncResourceAction(value, amount, insert)));
    }
}
