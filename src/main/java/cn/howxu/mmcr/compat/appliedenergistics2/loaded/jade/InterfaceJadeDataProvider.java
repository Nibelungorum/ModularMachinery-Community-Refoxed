package cn.howxu.mmcr.compat.appliedenergistics2.loaded.jade;

import appeng.api.networking.IGridNode;
import appeng.me.helpers.IGridConnectedBlockEntity;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet;
import cn.howxu.mmcr.api.capability.facet.PresentationFacet.SlotSummary;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.checkerframework.checker.nullness.qual.NonNull;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Synchronizes the AE2 grid-node state for the MMCR-hosted interface.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum InterfaceJadeDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final ResourceLocation UID = MMCR.id("ae2_input_interface_grid");
    static final String STATE = "gridNodeState";
    private static final String OUTPUTS = "outputPresentation";
    private static final String LABEL = "label";
    private static final String VALUE = "value";
    private static final String UNIT = "unit";

    @Override
    public @NonNull ResourceLocation getUid() {
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
                    .flatMap(InterfaceJadeDataProvider::outputDisplays)
                    .forEach(display -> {
                        CompoundTag output = new CompoundTag();
                        output.putString(LABEL, display.labelKey());
                        output.putString("unitKey", display.unitKey());
                        output.putString(VALUE, display.value());
                        output.putString(UNIT, display.unit());
                        outputs.add(output);
                    });
        }
        data.put(OUTPUTS, outputs);
    }

    private static Stream<SlotSummary> outputDisplays(MachineCapability capability) {
        return capability.facet(PresentationFacet.class).stream()
                .flatMap(facet -> facet.outputSlots(capability.view()).stream());
    }

    static List<SlotSummary> outputs(CompoundTag data) {
        List<SlotSummary> outputs = new ArrayList<>();
        ListTag outputTags = data.getList(OUTPUTS, Tag.TAG_COMPOUND);
        for (int index = 0; index < outputTags.size(); index++) {
            CompoundTag output = outputTags.getCompound(index);
            String label = output.getString(LABEL);
            String value = output.getString(VALUE);
            String unit = output.getString(UNIT);
            if (!label.isEmpty() && !value.isEmpty()) {
                outputs.add(new SlotSummary(label, value, unit, output.getString("unitKey")));
            }
        }
        return List.copyOf(outputs);
    }

    private static int state(IGridNode node) {
        if (node == null || !node.isPowered()) return 0;
        if (!node.hasGridBooted()) return 1;
        return node.meetsChannelRequirements() ? 3 : 2;
    }
}
