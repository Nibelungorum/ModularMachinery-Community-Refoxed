MMCREvents.server(event => {
    const api = event.getAPI()

    // Store the structure in a variable so it can be used for advanced features.
    const structure = event.createStructure("mmcr_kubejs:kubejs_alloy_furnace")

    // Define and build the machine structure.
    structure
        .pattern(['DXD', 'XIX', 'XXX'])
        .pattern(['XMX', 'I I', 'XMX'])
        .pattern(['DXD', 'XCX', 'XXX'])
        .set('X', api.block('minecraft:bricks'))
        .set('D', api.anyOf(
            api.block('minecraft:bricks'),
            api.anyOfUpgradeBus()
        ))
        .set('M', api.block('minecraft:blast_furnace'))
        .modifier('M', api.modifierUse(
            'mmcr_kubejs:alloy_furnace_emerald_speedup',
            api.block('minecraft:emerald_block')
        ))
        .modifier('M', api.modifierUse(
            'mmcr_kubejs:alloy_furnace_lapis_doubling',
            api.block('minecraft:lapis_block')
        ))
        .set('I', api.anyOf(api.block('mmcr:item_input_bus'), api.block('mmcr:item_output_bus'), api.block('mmcr:energy_input_hatch')))
        .controller('C')
        .build()

    // See server_scripts/recipe/A_Modifier_Machine.js for the recipe definitions.
})
