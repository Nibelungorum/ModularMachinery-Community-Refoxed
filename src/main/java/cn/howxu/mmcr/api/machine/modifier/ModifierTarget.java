package cn.howxu.mmcr.api.machine.modifier;

import java.util.Locale;

/** Declares the property and scope a machine modifier can affect.
 * @author howxu <dev@howxu.cn>
 */
public enum ModifierTarget {
    DURATION("duration", "input", true, false),
    ENERGY("energy", "input", true, false),
    CHEMICAL("chemical", "input", true, true),
    HEAT("heat", "input", true, false),
    OUTPUT("output", "output", true, true),
    PARALLELISM("parallelism", "machine", true, false),
    FACTORY_THREADS("factory_threads", "machine", true, false),
    RECIPE_THREADS("recipe_threads", "recipe", true, false),
    PARALLELIZED("parallelized", "recipe", false, false);

    private final String key;
    private final String scope;
    private final boolean numeric;
    private final boolean supportsChance;

    ModifierTarget(String key, String scope, boolean numeric, boolean supportsChance) {
        this.key = key;
        this.scope = scope;
        this.numeric = numeric;
        this.supportsChance = supportsChance;
    }

    public String key() {
        return key;
    }

    public boolean numeric() {
        return numeric;
    }

    public boolean supportsChance() {
        return supportsChance;
    }

    public static ModifierTarget parse(String target, String scope) {
        String normalizedTarget = normalize(target, "target");
        String normalizedScope = normalize(scope, "scope");
        for (ModifierTarget value : values()) {
            if (value.key.equals(normalizedTarget)) {
                if (!value.scope.equals(normalizedScope)) {
                    throw new IllegalArgumentException("Invalid scope '" + scope + "' for modifier target '" + target + "'");
                }
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown machine modifier target: " + target);
    }

    private static String normalize(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Machine modifier " + name + " must not be blank");
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
