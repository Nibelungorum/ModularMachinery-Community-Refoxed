package cn.howxu.mmcr.api.render;

import cn.howxu.mmcr.api.registration.ApiRegistrationException;
import cn.howxu.mmcr.api.render.ControllerRenderer;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Canonical collector for machine controller renderers.
 * @author howxu <dev@howxu.cn>
 */
public class RenderRegistration {
    private final Set<ResourceLocation> machineIds;
    private final Map<ResourceLocation, ControllerRenderer> renderers = new LinkedHashMap<>();
    private boolean frozen;

    public RenderRegistration(Collection<ResourceLocation> machineIds) {
        if (machineIds == null) throw new ApiRegistrationException("machineIds must not be null");
        LinkedHashSet<ResourceLocation> copied = new LinkedHashSet<>();
        for (ResourceLocation machineId : machineIds) {
            if (machineId == null) throw new ApiRegistrationException("machine id must not be null");
            if (!copied.add(machineId)) {
                throw new ApiRegistrationException("Duplicate machine id: " + machineId);
            }
        }
        this.machineIds = Collections.unmodifiableSet(copied);
    }

    public void register(ResourceLocation machineId, ControllerRenderer renderer) {
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

    public Map<ResourceLocation, ControllerRenderer> renderers() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(renderers));
    }

    public void freeze() {
        frozen = true;
    }
}
