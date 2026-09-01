package cn.howxu.mmcr.test.capability;

/**
 * Registry-independent resource identity used by capability contract tests.
 *
 * @author howxu <dev@howxu.cn>
 */
public record TestResource(String id) {
    public TestResource {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
    }
}
