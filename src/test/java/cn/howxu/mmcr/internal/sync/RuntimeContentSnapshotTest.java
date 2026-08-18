package cn.howxu.mmcr.internal.sync;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeContentSnapshotTest {

    @Test
    void snapshotDefensivelyCopiesAllMaps() {
        Identifier machineId = MMCR.id("alloy_furnace");
        Map<Identifier, MachineStructureDefinition> structures = mapWithOpaqueValue(machineId);
        Map<Identifier, MachineRecipe> recipes = mapWithOpaqueValue(machineId);
        Map<Identifier, MachineControllerSpec> controllerSpecs = mapWithOpaqueValue(machineId);
        Map<Identifier, MachineAppearanceSpec> appearances = mapWithOpaqueValue(machineId);
        RuntimeContentSnapshot snapshot = new RuntimeContentSnapshot(
                structures, recipes, controllerSpecs, appearances, 7L);

        structures.clear();
        recipes.clear();
        controllerSpecs.clear();
        appearances.clear();

        assertThat(snapshot.structures()).containsKey(machineId);
        assertThat(snapshot.recipes()).containsKey(machineId);
        assertThat(snapshot.controllerSpecs()).containsKey(machineId);
        assertThat(snapshot.appearances()).containsKey(machineId);
        assertThatThrownBy(() -> snapshot.structures().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.recipes().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.controllerSpecs().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.appearances().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> Map<Identifier, T> mapWithOpaqueValue(Identifier id) {
        Map<Identifier, T> map = new LinkedHashMap<>();
        map.put(id, (T) new Object());
        return map;
    }
}
