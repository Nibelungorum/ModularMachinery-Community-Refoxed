package cn.howxu.mmcr.api.network;

/**
 * Reasons a queued machine network request could not be delivered.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum RequestFailureReason {
    SOURCE_INTERFACE_MISSING,
    TARGET_INTERFACE_MISSING,
    TARGET_CHUNK_UNLOADED,
    CONNECTION_MISSING,
    SOURCE_STRUCTURE_INVALID,
    TARGET_STRUCTURE_INVALID,
    HASH_MISMATCH,
    ALLOWLIST_REJECTED,
    TARGET_HANDLER_MISSING,
    UNREACHABLE
}
