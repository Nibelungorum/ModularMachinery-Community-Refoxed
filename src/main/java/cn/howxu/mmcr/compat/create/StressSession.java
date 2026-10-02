package cn.howxu.mmcr.compat.create;

import cn.howxu.mmcr.api.compat.create.StressFacet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Persistent main-thread owner of one crafting lane's transient Create contributions.
 * @author howxu <dev@howxu.cn>
 */
public final class StressSession {
    private final List<Tracked> tracked = new ArrayList<>();
    private Integer committingIndex;

    /** Native facets may trust the handler's aggregate network preflight during this main-thread batch. */
    public boolean committing(int requirementIndex) {
        return committingIndex != null && committingIndex == requirementIndex;
    }

    void beginCommit(int requirementIndex) {
        if (committingIndex != null) throw new IllegalStateException("Nested stress contribution commit");
        committingIndex = requirementIndex;
    }

    void endCommit() {
        committingIndex = null;
    }

    public void track(StressFacet facet, RecipeModifier.IOType io) {
        track(facet, -1, io);
    }

    public void track(StressFacet facet, int requirementIndex, RecipeModifier.IOType io) {
        Objects.requireNonNull(facet, "facet");
        Objects.requireNonNull(io, "io");
        if (tracked.stream().noneMatch(value -> value.facet() == facet && value.index() == requirementIndex
                && value.io() == io)) tracked.add(new Tracked(facet, requirementIndex, io));
    }

    public List<StressFacet> facets(int requirementIndex) {
        return tracked.stream().filter(value -> value.index() == requirementIndex).map(Tracked::facet).toList();
    }

    /** Release an entire failed requirement without touching another index in this lane. */
    public void release(int requirementIndex) {
        for (Tracked value : List.copyOf(tracked)) {
            if (value.index() == requirementIndex) {
                value.facet().release(this, requirementIndex);
                tracked.removeIf(candidate -> candidate == value);
            }
        }
    }

    /** Remove superseded locations only after every new allocation has committed. */
    public void retain(int requirementIndex, List<StressFacet> retained) {
        for (Tracked value : List.copyOf(tracked)) {
            if (value.index() == requirementIndex && retained.stream().noneMatch(facet -> facet == value.facet())) {
                value.facet().release(this, requirementIndex);
                tracked.removeIf(candidate -> candidate == value);
            }
        }
    }

    public void releaseOutputs() {
        for (Tracked value : List.copyOf(tracked)) {
            if (value.io() != RecipeModifier.IOType.OUTPUT) continue;
            if (value.index() < 0) value.facet().release(this);
            else value.facet().release(this, value.index());
            tracked.removeIf(candidate -> candidate == value);
        }
    }

    public void releaseAll() {
        for (Tracked value : List.copyOf(tracked)) value.facet().release(this);
        tracked.clear();
    }

    /** Indexed tracking keeps lifecycle cleanup separate from requirement reallocation.
     * @author howxu <dev@howxu.cn>
     */
    private record Tracked(StressFacet facet, int index, RecipeModifier.IOType io) {
    }
}
