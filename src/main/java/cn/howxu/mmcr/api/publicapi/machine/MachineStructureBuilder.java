package cn.howxu.mmcr.api.publicapi.machine;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Builder for the structure phase of a public machine declaration.
 * @author howxu <dev@howxu.cn>
 */
public final class MachineStructureBuilder {
    private final List<MachineStructureDefinition.Declaration> declarations = new ArrayList<>();

    private MachineStructureBuilder() {
    }

    public static MachineStructureBuilder structure() {
        return new MachineStructureBuilder();
    }

    public MachineStructureBuilder fullStructure(BlockArray pattern) {
        declarations.add(MachineStructureDefinition.Declaration.full(Objects.requireNonNull(pattern, "pattern")));
        return this;
    }

    public MachineStructureBuilder extension(BlockArray pattern) {
        declarations.add(MachineStructureDefinition.Declaration.extension(Objects.requireNonNull(pattern, "pattern")));
        return this;
    }

    public MachineStructureDefinition build(net.minecraft.resources.Identifier machineId) {
        if (declarations.isEmpty()) throw new IllegalStateException("Main machine structure is required");
        if (declarations.getFirst().kind() != MachineStructureDefinition.Declaration.Kind.FULL) {
            throw new IllegalStateException("Main machine structure is required");
        }
        return new MachineStructureDefinition(machineId, declarations);
    }
}
