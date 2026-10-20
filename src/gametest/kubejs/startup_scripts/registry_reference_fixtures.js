StartupEvents.registry('item', event => event.create('mmcr_reference_upgrade'))
StartupEvents.registry('block', event => event.create('mmcr_reference_coil'))

MMCREvents.startup(event => {
    const api = event.getAPI()
    const horizontalState = api.state('minecraft:oak_log[axis=x]').state()
    const axisProperty = horizontalState.getBlock().getStateDefinition().getProperty('axis')
    if (horizontalState.getValue(axisProperty).name() !== 'X') {
        throw new Error('MMCR state helper did not preserve the horizontal axis')
    }
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
        .type(api.id('mmcr_test:kubejs_reference_coil')).priority(1).state('minecraft:oak_log[axis=x]').register()

    const scope = api.screenScope().CONTROLLER
    event.createMachine(api.id('mmcr_test:kubejs_identifier_overloads'))
        .recipePool('mmcr_test:identifier_pool', 'mmcr_test:identifier_other_pool')
        .recipePool(api.id('mmcr_test:identifier_pool'), api.id('mmcr_test:identifier_other_pool'))
        .controllerTextures('minecraft:block/stone', 'minecraft:block/dirt',
            'minecraft:block/gravel', 'minecraft:block/sand')
        .controllerBaseTexture(api.id('minecraft:block/stone'))
        .recipeBehavior(behavior => behavior
            .beforeStart(ctx => {
                ctx.cancel()
                const text = ctx.machineContext().screenText()
                text.append(scope, 'mmcr_test:warning', Text.translate('message.mmcr_test.warning'))
                text.appendAfter(scope, 'mmcr_test:details', 'mmcr_test:warning',
                    Text.translate('message.mmcr_test.details'))
                text.replace(api.id('mmcr_test:warning'), Text.translate('message.mmcr_test.updated'))
                ctx.machineContext().jadeText().append('mmcr_test:warning', Text.translate('message.mmcr_test.warning'))
            })
            .beforeFinish(ctx => {
                ctx.machineContext().screenText().remove(scope, 'mmcr_test:warning')
                ctx.machineContext().jadeText().remove('mmcr_test:warning')
            }))
        .register()
})
