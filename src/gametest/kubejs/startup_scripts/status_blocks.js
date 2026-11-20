StartupEvents.registry('block', event => {
    event.create('mmcr_test:status_coil')
        .property(BlockProperties.LIT)
        .defaultState(state => state.set(BlockProperties.LIT, false))
        .tagBlock(['mmcr:status_block'])
    event.create('mmcr_test:passive_coil')
        .property(BlockProperties.LIT)
        .defaultState(state => state.set(BlockProperties.LIT, false))
})

MMCREvents.startup(event => {
    event.createMachine('mmcr_test:status_fixture').recipePool('mmcr_test:status_fixture').register()
})
