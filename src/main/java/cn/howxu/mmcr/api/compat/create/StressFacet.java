package cn.howxu.mmcr.api.compat.create;

import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.compat.create.StressSession;

/** Main-thread access to a native stress port, without linking optional implementation classes.
 * @author howxu <dev@howxu.cn>
 */
public interface StressFacet extends CapabilityFacet {
    Object networkIdentity();
    StressState state();
    boolean stressEnabled();
    boolean acceptsGeneratedRpm(StressSession session, int requirementIndex, double rpm);
    double ownedActualStress(StressSession session, int requirementIndex);
    /**
     * Exact unrounded base contribution owned by this session/index on this facet, or zero.
     * Native implementations must override using their contribution ledger (input AND output).
     * R9 uses this with state().baseContribution() for entity float headroom; actual stress
     * cannot supply this credit because it is rounded and output ownedActualStress is zero.
     * The default grants no replacement credit, preserving conservative behavior for older facets.
     */
    default double ownedBaseStress(StressSession session, int requirementIndex) {
        return 0D;
    }

    CapabilityResult apply(StressSession session, int requirementIndex, double baseStress, double generatedRpm);
    void release(StressSession session);

    /** Removes exactly one requirement, preserving other requirements owned by the same lane. */
    void release(StressSession session, int requirementIndex);

    /** Releases logical recipe ownership; native outputs may coast briefly after normal completion. */
    default void onRecipeFinished(StressSession session) {
        release(session);
    }
}
