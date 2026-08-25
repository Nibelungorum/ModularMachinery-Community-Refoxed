MMCREvents.server(event => {
    const api = event.getAPI()
    const structure = event.createStructure("mmcr_kubejs:kubejs_thermal_smelting_furnace")

    structure
        .pattern(['AAA','XXX','XXX','AAA'])
        .pattern(['AAA','X X','X X','ADA'])
        .pattern(['ABA','XXX','XXX','AAA'])
        .set('X',api.levelSlot("mmcr_kubejs:thermal_smelting_coil")) // Use a level slot to match the corresponding level block.
        .set('A',api.anyOf(
            api.anyOfItemInput(),
            api.anyOfItemOutput(),
            api.anyOfEnergyInput(),
            api.parallelControllers(), // this is all parallel controller
            api.factoryController(), // this is factory controller block
            api.block('minecraft:smooth_basalt')
        ))
        .set('D',api.block('minecraft:reinforced_deepslate'))
        .controller('B')
        // Build the structure.
        .build()

    // See server_scripts/recipe/A_Level_Machine.js for the recipe definitions.
})
