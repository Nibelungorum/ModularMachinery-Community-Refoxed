package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.internal.api.facade.structure.StructureAdapters;
import cn.howxu.mmcr.publicapi.structure.PatternDraft;
import cn.howxu.mmcr.publicapi.structure.StructureDraft;
import cn.howxu.mmcr.publicapi.structure.StructureStageDraft;

/** Entry points for structure declarations.
 * @author howxu <dev@howxu.cn>
 */
public final class Structures {
    private Structures() {}
    public static StructureDraft structure() { return StructureAdapters.structure(); }
    public static PatternDraft pattern() { return StructureAdapters.pattern(); }
    public static StructureStageDraft stage() { return StructureAdapters.stage(); }
}
