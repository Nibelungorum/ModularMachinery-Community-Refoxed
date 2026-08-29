MMCREvents.startup(event => {

    const distillation_tower = event
        .createMachine("mmcr_kubejs:kubejs_recipe_ticker")
        .displayNameKey("machine.mmcr_kubejs.kubejs_recipe_ticker")
        .recipeFamily("mmcr_kubejs:kubejs_recipe_ticker")
        .appearance("minecraft:green_terracotta")
        .expandableStructure(true)

    distillation_tower.register()
})
