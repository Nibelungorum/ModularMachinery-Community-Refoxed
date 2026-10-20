StartupEvents.registry('item', event => event.create('mmcr_reference_upgrade'))
StartupEvents.registry('block', event => event.create('mmcr_reference_coil'))

MMCREvents.startup(event => {
    const api = event.getAPI()
    event.registerModifier('mmcr_test:kubejs_reference_speed', api.modifierDefinition([
        api.modifier('duration', 'input', 0.5, 'multiply', false)
    ]))
    event.registerModifierItem('kubejs:mmcr_reference_upgrade', 'mmcr_test:kubejs_reference_speed')
    event.registerModifierItem('kubejs:mmcr_reference_coil', 'mmcr_test:kubejs_reference_speed')
    event.createLevelType('mmcr_test:kubejs_reference_coil')
        .displayNameKey('level.mmcr_test.kubejs_reference_coil').register()
    event.createLevel('mmcr_test:kubejs_reference_coil')
        .type('mmcr_test:kubejs_reference_coil').state('kubejs:mmcr_reference_coil').register()
    event.createLevel('mmcr_test:kubejs_reference_horizontal_log')
        .type('mmcr_test:kubejs_reference_coil').priority(1).state('minecraft:oak_log[axis=x]').register()
})
