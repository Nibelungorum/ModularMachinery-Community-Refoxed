// Here we will register two machines which have different structure

MMCREvents.startup(event => {

    const distillation_tower = event
        .createMachine("mmcr_kubejs:kubejs_distillation_tower")
        .displayNameKey("machine.mmcr_kubejs.kubejs_distillation_tower")
        .recipeFamily("mmcr_kubejs:kubejs_distillation_tower")
        .appearance("minecraft:polished_blackstone")
        .expandableStructure(true) // use this make it an expandable machine

    distillation_tower.register()

    const eco_matrix = event
        .createMachine("mmcr_kubejs:kubejs_eco_matrix")
        .displayNameKey("machine.mmcr_kubejs.kubejs_eco_matrix")
        .recipeFamily("mmcr_kubejs:kubejs_eco_matrix")
        .appearance('minecraft:sea_lantern')
        .expandableStructure(true)

    eco_matrix.register()
})