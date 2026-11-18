package cn.howxu.mmcr.compat.appliedenergistics2.loaded.jade;

import appeng.api.networking.IGridNode;
import appeng.me.helpers.IGridConnectedBlockEntity;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Synchronizes the AE2 grid-node state for the MMCR-hosted interface.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum InterfaceJadeDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final Identifier UID = MMCR.id("ae2_input_interface_grid");
    static final String STATE = "gridNodeState";
    private static final String OUTPUTS = "outputPresentation";
    private static final String LABEL = "label";
    private static final String VALUE = "value";
    private static final String UNIT = "unit";

    @Override
    public @NonNull Identifier getUid() {
        return UID;
    }

    @Override
    public void appendServerData(@NonNull CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getTarget() instanceof IGridConnectedBlockEntity host)) return;
        data.putByte(STATE, (byte) state(host.getActionableNode()));
        ListTag outputs = new ListTag();
        if (host instanceof CapabilityHost capabilityHost) {
            capabilityHost.capabilities().stream()
                    .filter(capability -> capability.directions().supports(IOType.OUTPUT)
                            && !capability.directions().supports(IOType.INPUT))
                    .flatMap(capability -> capability.facet(PresentationFacet.class).stream()
                            .flatMap(facet -> facet.outputContents(capability.view()).stream()))
                    .forEach(display -> {
                        CompoundTag output = new CompoundTag();
                        output.putString(LABEL, display.label());
                        output.putString(VALUE, display.value());
                        output.putString(UNIT, display.unit());
                        outputs.add(output);
                    });
        }
        data.put(OUTPUTS, outputs);
    }

    static List<OutputPresentation> outputs(CompoundTag data) {
        List<OutputPresentation> outputs = new ArrayList<>();
        for (int index = 0; index < data.getListOrEmpty(OUTPUTS).size(); index++) {
            CompoundTag output = data.getListOrEmpty(OUTPUTS).getCompoundOrEmpty(index);
            String label = output.getStringOr(LABEL, "");
            String value = output.getStringOr(VALUE, "");
            String unit = output.getStringOr(UNIT, "");
            if (!label.isEmpty() && !value.isEmpty()) {
                outputs.add(new OutputPresentation(label, value, unit));
            }
        }
        return List.copyOf(outputs);
    }

    record OutputPresentation(String label, String value, String unit) {
    }

    private static int state(IGridNode node) {
        if (node == null || !node.isPowered()) return 0;
        if (!node.hasGridBooted()) return 1;
        return node.meetsChannelRequirements() ? 3 : 2;
    }
}
