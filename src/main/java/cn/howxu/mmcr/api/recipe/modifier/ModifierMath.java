package cn.howxu.mmcr.api.recipe.modifier;

/** Aggregated operations on one scalar before target-specific rounding and bounds.
 * @author HowXu {@literal <dev@howxu.cn>}
 */
public record ModifierMath(double flat, double ratio, double factor) {
    public double apply(double base) {
        return multiply(relative(base, add(base, flat), ratio), factor);
    }

    public static double relative(double base, double adjusted, double ratio) {
        return ratio == 0D ? adjusted : add(adjusted, multiply(base, ratio));
    }

    public static double add(double first, double second) {
        double result = first + second;
        if (Double.isFinite(result)) return result;
        return result < 0D ? -Double.MAX_VALUE : Double.MAX_VALUE;
    }

    public static double multiply(double first, double second) {
        double result = first * second;
        if (Double.isFinite(result)) return result;
        if (Double.isNaN(result)) return 0D;
        return Math.copySign(Double.MAX_VALUE, first * Math.signum(second));
    }

    public static double power(double base, long count) {
        double result = 1D;
        while (count > 0L) {
            if ((count & 1L) != 0L) result = multiply(result, base);
            count >>>= 1;
            if (count != 0L) base = multiply(base, base);
        }
        return result;
    }
}
