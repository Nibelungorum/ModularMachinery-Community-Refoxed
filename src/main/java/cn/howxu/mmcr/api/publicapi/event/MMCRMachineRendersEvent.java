package cn.howxu.mmcr.api.publicapi.event;

import cn.howxu.mmcr.api.publicapi.ApiRegistrationException;
import cn.howxu.mmcr.api.publicapi.render.ControllerRenderer;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.Event;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Canonical event for collecting machine controller renderers.
 * @author howxu <dev@howxu.cn>
 */
public class MMCRMachineRendersEvent extends Event {
    private final Set<Identifier> machineIds;
    private final Map<Identifier, ControllerRenderer> renderers = new LinkedHashMap<>();
    private boolean frozen;

    public MMCRMachineRendersEvent(Collection<Identifier> machineIds) {
        if (machineIds == null) throw new ApiRegistrationException("machineIds must not be null");
        LinkedHashSet<Identifier> copied = new LinkedHashSet<>();
        for (Identifier machineId : machineIds) {
            if (machineId == null) throw new ApiRegistrationException("machine id must not be null");
            if (!copied.add(machineId)) {
                throw new ApiRegistrationException("Duplicate machine id: " + machineId);
            }
        }
        this.machineIds = Collections.unmodifiableSet(copied);
    }

    public void register(Identifier machineId, ControllerRenderer renderer) {
        if (frozen) throw new IllegalStateException("Machine renders are frozen");
        if (machineId == null) throw new ApiRegistrationException("machine id must not be null");
        if (renderer == null) throw new ApiRegistrationException("renderer must not be null");
        if (!machineIds.contains(machineId)) {
            throw new ApiRegistrationException("Unknown machine definition: " + machineId);
        }
        if (renderers.putIfAbsent(machineId, renderer) != null) {
            throw new ApiRegistrationException("Duplicate machine renderer: " + machineId);
        }
    }

    public Map<Identifier, ControllerRenderer> renderers() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(renderers));
    }

    public void freeze() {
        frozen = true;
    }
}
