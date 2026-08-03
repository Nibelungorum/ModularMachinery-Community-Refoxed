package cn.howxu.mmcr.api.machine;

import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * 根据命令输入(machineId 或 null)从机器注册表里挑出要建造的目标机器。
 * <p>{@code isFallback()} 是 true 表示用户没指定,直接挑注册表第一个;false 表示精确指定。
 */
public final class MachineSelector {

    public record Result(Machine machine, boolean isFallback) {}

    private MachineSelector() {}

    public static Result select(Identifier requested, Map<Identifier, Machine> registry) {
        if (requested != null) {
            Machine exact = registry.get(requested);
            return new Result(exact, false);
        }
        if (registry.isEmpty()) return new Result(null, true);
        // 注册表用 LinkedHashMap,顺序与注册顺序一致 —— 这就是机器定义的"默认顺序"。
        return new Result(registry.values().iterator().next(), true);
    }
}
