package cn.howxu.mmcr.publicapi.runtime;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** MMCR-provided commit result. @author howxu <dev@howxu.cn> */
@ApiStatus.NonExtendable
public interface IoCommitResult {
    boolean successful();
    @Nullable RuntimeFailure failure();
}
