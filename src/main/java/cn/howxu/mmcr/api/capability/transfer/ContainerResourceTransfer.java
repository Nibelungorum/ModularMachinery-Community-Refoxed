package cn.howxu.mmcr.api.capability.transfer;

import cn.howxu.mmcr.util.IOType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Moves a carried container's resources in the port's external IO direction.
 * The port handler must already be restricted to the selected tank.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class ContainerResourceTransfer {
    private ContainerResourceTransfer() {
    }

    public static <R extends Resource> int transfer(IOType ioType,
            ResourceHandler<R> container, ResourceHandler<R> port,
            TransactionContext transaction) {
        if (ioType == null || container == null || port == null) return 0;
        return ioType == IOType.INPUT
                ? ResourceHandlerUtil.move(container, port, resource -> true,
                        Integer.MAX_VALUE, transaction)
                : ResourceHandlerUtil.move(port, container, resource -> true,
                        Integer.MAX_VALUE, transaction);
    }
}
