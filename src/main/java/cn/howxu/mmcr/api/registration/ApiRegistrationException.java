package cn.howxu.mmcr.api.registration;

/** Exception raised when a public startup declaration violates the registration lifecycle.
 * @author howxu <dev@howxu.cn>
 */
public final class ApiRegistrationException extends IllegalStateException {
    public ApiRegistrationException(String message) {
        super(message);
    }

    public ApiRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
