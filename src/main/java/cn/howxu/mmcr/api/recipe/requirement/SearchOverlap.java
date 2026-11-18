package cn.howxu.mmcr.api.recipe.requirement;

import net.minecraft.resources.Identifier;

import java.util.Set;

/** Input identities in a comparable family; enumerable empty and unenumerable are distinct.
 * @author howxu <dev@howxu.cn>
 */
public record SearchOverlap(Identifier family, Set<Identifier> values, boolean unenumerable) {
    public SearchOverlap { values = Set.copyOf(values); }

    public boolean overlaps(SearchOverlap right) {
        return family.equals(right.family) && (unenumerable || !values.isEmpty() && right.unenumerable
                || values.stream().anyMatch(right.values::contains));
    }
}
