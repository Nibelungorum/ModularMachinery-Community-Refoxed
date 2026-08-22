MMCREvents.server(event => {
    const api = event.getAPI()
    const structure = event.createStructure("mmcr_kubejs:kubejs_cracker")

    structure
        .set('B', api.anyOf(
            structure.anyOfItemInput(),
            structure.anyOfItemOutput(),
            structure.anyOfFluidOutput(),
            structure.anyOfEnergyInput()
        ))
        .controller('C')

        // Do not forget this
        .build()

    // Move to server_scripts/recipe/A_Level_Machine.js see how to create some special recipes
})