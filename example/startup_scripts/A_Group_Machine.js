// Here we will register two machines which have different structure

MMCREvents.startup(event => {

    const distillation_tower = event
        .createMachine("mmcr_kubejs:kubejs_distillation_tower")
        .displayNameKey("machine.mmcr_kubejs.kubejs_distillation_tower")
        .recipeFamily("mmcr_kubejs:kubejs_distillation_tower")
        .appearance("minecraft:polished_blackstone")
        .expandableStructure(true) // use this make it an expandable machine

    distillation_tower.register()

    // Move to server_scripts/structure/A_Group_Machine.js see how to use group for your machine
})