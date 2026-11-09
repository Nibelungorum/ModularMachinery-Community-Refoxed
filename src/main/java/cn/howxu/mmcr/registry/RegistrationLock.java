package cn.howxu.mmcr.registry;

/**
 * Serializes startup controller registration and cross-class registry map traversal.
 * Registry classes must be initialized before acquiring this lock; otherwise a class initializer
 * waiting for the lock can deadlock with a registrant waiting for that class.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class RegistrationLock {
    public static final Object LOCK = new Object();

    private RegistrationLock() {
    }
}
