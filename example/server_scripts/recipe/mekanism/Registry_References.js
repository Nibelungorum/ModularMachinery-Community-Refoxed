ServerEvents.recipes(event => {
    if (!Platform.isLoaded('mekanism')) return
    event.custom({
        type: 'mmcr:machine_recipe',
        recipe_pool: 'mmcr_kubejs:mekanism_registry_demo',
        tick_time: 100,
        requirements: [
            { type: 'mmcr:level', io: 'input', level_type: 'mmcr_kubejs:induction_cell',
                level: 'mmcr_kubejs:basic_induction_cell' },
            { type: 'minecraft:item', io: 'input', item: { item: 'minecraft:iron_ingot' }, count: 1 },
            { type: 'minecraft:item', io: 'output', stack: { id: 'mekanism:ingot_steel', count: 1 } },
            { type: 'neoforge:energy', io: 'input', fe_per_tick: 20 }
        ]
    }).id('mmcr_kubejs:mekanism_registry_demo/steel')
})
