// Register a machine with an expandable structure.

MMCREvents.startup(event => {

    const distillation_tower = event
        .createMachine("mmcr_kubejs:kubejs_distillation_tower")
        .displayNameKey("machine.mmcr_kubejs.kubejs_distillation_tower")
        .recipeFamily("mmcr_kubejs:kubejs_distillation_tower")
        .appearance("minecraft:polished_blackstone")
        .expandableStructure(true) // Enable the expandable structure.

    distillation_tower.register()

    // See server_scripts/structure/A_Group_Machine.js for the grouped structure.
})
