package cn.howxu.mmcr.internal.port;

import cn.howxu.mmcr.internal.tile.IOPortBlockEntity;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Optional;

/**
 * 一种 IO 端口类型的协议。新增一种 IO 端口(气体、魔源等)
 * 只需实现本接口并通过 {@link cn.howxu.mmcr.registry.PortKinds#register} 注册。
 */
public interface IOPortKind {

    /** 该 kind 的字符串 id,出现在 block 注册名里,如 "item"/"fluid"/"energy"/"gas"/"mana"。 */
    String id();

    IOType ioType();

    /** 该 kind 对应的 BlockEntity 工厂。Block 注册时由这里创建对应实体。 */
    BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory();

    /** 该 kind 的主 capability 类型(对接 Forge / 第三方 capability)。默认无。 */
    default Optional<Class<?>> primaryCapability() { return Optional.empty(); }

    /** 该 kind 的服务端 tick 钩子,用于 MEK 气体管道分发等。默认无。 */
    default void tick(IOPortBlockEntity be) {}
}
