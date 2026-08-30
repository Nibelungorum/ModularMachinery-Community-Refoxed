MMCREvents.server(event => {
    const api = event.getAPI()

    // Store the structure in a variable so it can be used for advanced features.
    const structure = event.createStructure("mmcr_kubejs:kubejs_alloy_furnace")

    // Define and build the machine structure.
        structure
            .pattern(['XXX', 'XIX', 'XXX'])
            .pattern(['XMX', 'I I', 'XMX'])
            .pattern(['XXX', 'XCX', 'XXX'])
            .set('X',api.block('minecraft:bricks'))
            .set('M',api.block('minecraft:blast_furnace'))
            .modifier('M', api.modifierUse(
                'mmcr_kubejs:alloy_furnace_diamond_speedup',
                api.block('minecraft:diamond_block')
            ))
            .modifier('M', api.modifierUse(
                'mmcr_kubejs:alloy_furnace_gold_doubling',
                api.block('minecraft:gold_block')
            ))
            .set('I',api.anyOf(api.block('mmcr:item_input_bus'),api.block('mmcr:item_output_bus'),api.block('mmcr:energy_input_hatch')))
            .controller('C')
            .build()

    // See server_scripts/recipe/A_Modifier_Machine.js for the recipe definitions.
})
