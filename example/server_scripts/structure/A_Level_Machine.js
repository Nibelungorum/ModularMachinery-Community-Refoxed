MMCREvents.server(event => {
    const api = event.getAPI()
    const structure = event.createStructure("mmcr_kubejs:kubejs_thermal_smelting_furnace")

    structure
        .pattern(['AAA','XXX','XXX','AAA'])
        .pattern(['AAA','X X','X X','ADA'])
        .pattern(['ABA','XXX','XXX','AAA'])
        .set('X',api.levelSlot("mmcr_kubejs:thermal_smelting_coil")) // call levelSlot and everything solved
        .set('A',api.anyOf(
            structure.anyOfItemInput(),
            structure.anyOfItemOutput(),
            structure.anyOfEnergyInput(),
            structure.anyOfEnergyInput(),
            structure.parallelControllers(),
            api.block('minecraft:smooth_basalt')
        ))
        .set('D',api.block('minecraft:reinforced_deepslate'))
        .controller('B')
        // Do not forget this
        .build()

    // Move to server_scripts/recipe/A_Level_Machine.js see how to create some special recipes
})