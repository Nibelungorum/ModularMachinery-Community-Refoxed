package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies machine-specific JEI type identities.
 *
 * @author howxu <dev@howxu.cn>
 */
class JeiMachineRecipeTypesTest {

    @Test
    void machineTypesAreStableAndDistinct() {
        var blastFurnace = JeiMachineRecipeTypes.forMachine(MMCR.id("blast_furnace"));
        var alloyFurnace = JeiMachineRecipeTypes.forMachine(MMCR.id("alloy_furnace"));

        assertThat(blastFurnace).isSameAs(JeiMachineRecipeTypes.forMachine(MMCR.id("blast_furnace")));
        assertThat(blastFurnace).isNotEqualTo(alloyFurnace);
        assertThat(blastFurnace.getUid()).isEqualTo(MMCR.id("machine_recipe/blast_furnace"));
    }
}
