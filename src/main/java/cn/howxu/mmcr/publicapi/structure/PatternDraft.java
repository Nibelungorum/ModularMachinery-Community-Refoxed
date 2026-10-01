package cn.howxu.mmcr.publicapi.structure;

import org.jetbrains.annotations.ApiStatus;

/** Configuration handle supplied by Structures; not implemented by consumers.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface PatternDraft {
    PatternDraft layer(String... rows);
    PatternDraft pattern(String... rows);
    PatternDraft where(char symbol, BlockCondition condition);
    PatternDraft controller(char symbol);
    PatternSpec build();
}
