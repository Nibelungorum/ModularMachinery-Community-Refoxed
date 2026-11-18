package cn.howxu.mmcr.api.compat.pneumaticcraft;

import cn.howxu.mmcr.api.capability.facet.AvailabilityFacet;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;

import cn.howxu.mmcr.api.capability.plan.CapabilityResult;

/** Neutral access to one physical air handler, without exposing native mutable storage.
 * @author howxu <dev@howxu.cn>
 */
public interface PneumaticAirFacet extends AvailabilityFacet {
    @Override default Map<ResourceLocation, Fact> availability(ResourceLocation family) {
        var air = state();
        return Map.of(family, new Fact(Math.max(0L, air.air()), air.pressure(), air.outputCapacity()));
    }
    @Override default boolean notifyInitialAvailability() { return false; }

    Object queryIdentity();

    AirState state();

    CapabilityResult validate(long amount, boolean insert, float minPressure);

    CapabilityResult apply(long amount, boolean insert, float minPressure);
}
