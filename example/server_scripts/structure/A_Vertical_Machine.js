MMCREvents.server(event => {
    const api = event.getAPI()
    const structure = event.createStructure("mmcr_kubejs:kubejs_cracker")

    structure
        // You can see how it performs:
        // All structure follow one general structure arrangement, but can perform differently
        .pattern(['AAA', 'AAA', 'AAA'])
        .pattern(['XBX', 'B B', 'XBX'])
        .pattern(['XDX', 'D D', 'XDX'])
        .pattern(['XEX', 'ECE', 'XEX'])
        .set('X', api.block('minecraft:polished_diorite')) // one block
        .set('A', api.block('minecraft:polished_andesite')) // one block
        .set('D', api.block('minecraft:blue_ice'))
        .set('E', api.block('minecraft:mossy_cobblestone'))
        // In the previous example, we always use fixed ports
        // There are some convenient methods you can use if you use "const structure"
        .set('B', api.anyOf(
            structure.anyOfItemInput(),
            structure.anyOfItemOutput(),
            structure.anyOfFluidOutput(),
            structure.anyOfEnergyInput()
        ))
        .controller('C')
        // call this will set a range of port requirement
        // if it is not satisfied, the structure will not form
        // the number is the minium requirement
        .portRequirements(api.portRequirements({
            item_input_bus: 1,
            item_output_bus: 1,
            fluid_output_hatch: 1,
            energy_input_hatch: [1,3] // you can use a range
        }))
        // call this to set the port tier requirement
        // it receives string argument, this is a design for easy usage and readable
        .portTierRequirements(api.portTierRequirements([
            'item_input_bus>=normal',
            'item_output_bus>=normal',
            'energy_input_hatch>=normal'
        ]))

        // Do not forget this
        .build()

    // Move to server_scripts/recipe/A_Vertical_Machine.js see how to create some special recipes
})