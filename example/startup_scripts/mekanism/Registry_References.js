// Requires Mekanism. All startup references are IDs: do not use Item.of() or Block.getBlock().
MMCREvents.startup(event => {
    if (!Platform.isLoaded('mekanism')) return
    const api = event.getAPI()

    event.createMachine('mmcr_kubejs:mekanism_registry_demo')
        .displayNameKey('machine.mmcr_kubejs.mekanism_registry_demo')
        .recipePool('mmcr_kubejs:mekanism_registry_demo')
        .appearance('mekanism:steel_casing')
        .allowModifiers()
        .register()

    event.registerModifier('mmcr_kubejs:mek_speed', api.modifierDefinition([
        api.modifier('duration', 'input', 0.5, 'multiply', false)
    ]))
    event.registerModifier('mmcr_kubejs:mek_energy', api.modifierDefinition([
        api.modifier('energy', 'input', 0.5, 'multiply', false)
    ]))
    // Ordinary mod items and block items are both resolved after default components are bound.
    event.registerModifierItem('mekanism:upgrade_speed', 'mmcr_kubejs:mek_speed')
    event.registerModifierItem('mekanism:upgrade_energy', 'mmcr_kubejs:mek_energy')
    event.registerModifierItem('mekanism:steel_casing', 'mmcr_kubejs:mek_energy')
    event.addRecipePoolWorkstation('mmcr_kubejs:mekanism_registry_demo', 'mekanism:steel_casing')

    event.createLevelType('mmcr_kubejs:induction_cell')
        .displayNameKey('level.mmcr_kubejs.induction_cell')
        .register()
    // Level block IDs are resolved after all mods' block registration events.
    event.createLevel('mmcr_kubejs:basic_induction_cell')
        .type('mmcr_kubejs:induction_cell').priority(0)
        .state('mekanism:basic_induction_cell')
        .register()
    event.createLevel('mmcr_kubejs:advanced_induction_cell')
        .type('mmcr_kubejs:induction_cell').priority(1)
        .state('mekanism:advanced_induction_cell')
        .modifier(api.modifierDefinition([
            api.modifier('duration', 'input', 0.75, 'multiply', false)
        ]))
        .register()
})
