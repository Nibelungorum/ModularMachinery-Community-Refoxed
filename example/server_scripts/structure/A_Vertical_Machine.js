MMCREvents.server(event => {
    const api = event.getAPI()
    const structure = event.createStructure("mmcr_kubejs:kubejs_cracker")

    structure
        // Structures follow the same basic layout, but machine settings can change their behavior.
        .pattern(['AAA', 'AAA', 'AAA'])
        .pattern(['XBX', 'B B', 'XBX'])
        .pattern(['XDX', 'D D', 'XDX'])
        .pattern(['XEX', 'ECE', 'XEX'])
        .set('X', api.block('minecraft:polished_diorite')) // Regular block.
        .set('A', api.block('minecraft:polished_andesite')) // Regular block.
        .set('D', api.block('minecraft:blue_ice'))
        .set('E', api.block('minecraft:mossy_cobblestone'))
        // Previous examples used fixed ports. A const structure also provides convenient port predicates.
        .set('B', api.anyOf(
            structure.anyOfItemInput(),
            structure.anyOfItemOutput(),
            structure.anyOfFluidOutput(),
            structure.anyOfEnergyInput()
        ))
        .controller('C')
        // Set the required port counts. The structure will not form when these requirements are not met.
        // A number is an exact count; an array specifies the minimum and maximum.
        .portRequirements(api.portRequirements({
            item_input_bus: 1,
            item_output_bus: 1,
            fluid_output_hatch: 1,
            energy_input_hatch: [1,3] // Allow one to three energy input hatches.
        }))
        // Set port tier requirements using a readable string format.
        .portTierRequirements(api.portTierRequirements([
            'item_input_bus>=normal',
            'item_output_bus>=normal',
            'energy_input_hatch>=normal'
        ]))

        // Build the structure.
        .build()

    // See server_scripts/recipe/A_Vertical_Machine.js for the recipe definitions.
})
