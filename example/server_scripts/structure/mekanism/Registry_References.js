MMCREvents.server(event => {
    if (!Platform.isLoaded('mekanism')) return
    const api = event.getAPI()

    // Bottom layer: steel casing with one induction cell. Top layer: ports and controller.
    // Replace an M casing with Mekanism structural glass for the speed modifier,
    // or insert the registered upgrade items into a connected upgrade bus.
    event.createStructure('mmcr_kubejs:mekanism_registry_demo')
        .pattern(['MMM', 'MLM', 'MMM'])
        .pattern(['IPI', 'PCP', 'IOI'])
        .set('M', api.block('mekanism:steel_casing'))
        .modifier('M', api.modifierUse('mmcr_kubejs:mek_speed', api.block('mekanism:structural_glass')))
        .set('L', api.levelSlot('mmcr_kubejs:induction_cell'))
        .set('P', api.anyOf(api.block('mekanism:steel_casing'), api.anyOfUpgradeBus()))
        .set('I', api.anyOf(api.anyOfItemInput(), api.anyOfEnergyInput()))
        .set('O', api.anyOfItemOutput())
        .controller('C')
        .build()
})
