package cn.howxu.mmcr.publicapi.registration;

/** A declaration was rejected by the registration lifecycle or ID validation.
 * @author howxu <dev@howxu.cn>
 */
public final class RegistrationException extends IllegalStateException {
    public RegistrationException(String message) { super(message); }
    public RegistrationException(String message, Throwable cause) { super(message, cause); }
}
