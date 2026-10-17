ServerEvents.recipes(event => {
    event.custom({
        type: 'mmcr:machine_recipe',
        recipe_pool: 'mmcr_kubejs:ae2lt',
        tick_time: 100,
        parallelized: true,
        requirements: [
            { type: 'minecraft:item', io: 'input', item: { item: 'minecraft:iron_ingot' }, count: 1 },
            { type: 'ae2lt:high_voltage', io: 'input', amount: 1000 },
            { type: 'ae2lt:extreme_high_voltage', io: 'input', amount: 100 },
            { type: 'minecraft:item', io: 'output', stack: { id: 'minecraft:gold_ingot', count: 1 } },
            { type: 'ae2lt:high_voltage', io: 'output', amount: 200 },
            { type: 'ae2lt:extreme_high_voltage', io: 'output', amount: 20, chance: 0.5 }
        ]
    }).id('mmcr_kubejs:ae2lt/lightning_example')
})
