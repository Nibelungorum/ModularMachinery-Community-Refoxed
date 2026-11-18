package cn.howxu.mmcr.api.capability.facet;

import java.util.Map;
import cn.howxu.mmcr.api.capability.MachineCapability;
import net.minecraft.resources.ResourceLocation;

/** Resource facts captured on the server thread for input and output wakeups.
 * Output potential is relative: providers without a capacity may use negative occupancy.
 * @author howxu <dev@howxu.cn>
 */
public interface AvailabilityFacet extends CapabilityFacet {
    record Fact(long quantity, double threshold, long outputPotential) {
        public static final Fact EMPTY = new Fact(0, 0, 0);
    }
    Map<ResourceLocation, Fact> availability(ResourceLocation family);
    default Map<ResourceLocation, Fact> availability(String family) { return availability(ResourceLocation.parse(family)); }

    static AvailabilityFacet find(MachineCapability capability) {
        for (var declared : capability.view().facets()) {
            if (AvailabilityFacet.class.isAssignableFrom(declared)) {
                var value = capability.facet(declared).orElse(null);
                if (value instanceof AvailabilityFacet facet) return facet;
            }
        }
        return null;
    }
    default boolean notifyInitialAvailability() { return true; }
}
