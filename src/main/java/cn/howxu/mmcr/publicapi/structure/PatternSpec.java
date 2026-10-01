package cn.howxu.mmcr.publicapi.structure;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;

/** Read-only layered pattern produced by the facade; not a consumer SPI.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface PatternSpec {
    List<List<String>> layers();
    Map<Character, BlockCondition> predicates();
    char controllerSymbol();
    int width();
    int height();
    int depth();
}
