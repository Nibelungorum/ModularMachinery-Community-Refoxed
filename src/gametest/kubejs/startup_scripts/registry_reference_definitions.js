// priority: -100
// Definitions deliberately run after references in a different file and separate callbacks.
MMCREvents.startup(event => {
    const api = event.getAPI()
    const registry = Java.loadClass('cn.howxu.mmcr.api.registration.StructureRegistration').current()
    if (!registry.levelDeclarations().containsKey(api.id('mmcr_test:kubejs_reference_coil')) ||
        !registry.levelDeclarations().containsKey(api.id('mmcr_test:kubejs_reference_horizontal_log'))) {
        throw new Error('Both pending levels must be collected before their type definition')
    }
    event.createLevelType('mmcr_test:kubejs_reference_coil')
        .displayNameKey('level.mmcr_test.kubejs_reference_coil').register()
})

MMCREvents.startup(event => {
    const api = event.getAPI()
    const registry = Java.loadClass('cn.howxu.mmcr.api.registration.StructureRegistration').current()
    if (!api.id('mmcr_test:kubejs_reference_speed').equals(
        registry.modifierItemIds().get(api.id('kubejs:mmcr_reference_upgrade')))) {
        throw new Error('The item binding must be collected before its modifier definition')
    }
    event.registerModifier('mmcr_test:kubejs_reference_speed', api.modifierDefinition([
        api.modifier('duration', 'input', 0.5, 'multiply', false)
    ]))
})
