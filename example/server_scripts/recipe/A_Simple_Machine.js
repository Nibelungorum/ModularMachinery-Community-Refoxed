// Define a recipe for the blast furnace machine.
// ServerEvents.recipes is data-driven and supports hot reload.

ServerEvents.recipes( event => {
    // Register a standard recipe object with event.custom().
    event.custom({
        type: 'mmcr:machine_recipe', // Required recipe type.
        recipe_pool: 'mmcr_kubejs:kubejs_blast_furnace', // Required recipe pool identifier.
        tick_time: 100, // Processing time in ticks; must be greater than 0.
        parallelized: true, // Allow this recipe to use parallel controllers.
        // Optional: allows recipes with empty inputs or outputs.
        requirements: [
            {
                type: 'minecraft:item', // Requirement type; fluid is also supported.
                io: 'input', // Input or output direction.
                item: { item: 'minecraft:iron_ingot' }, // 1.21.1 ingredients use { item: id } or { tag: id }.
                count: 1
            },
            {
                type: 'minecraft:item',
                io: 'output',
                    stack: { // Outputs must use a stack object for items and fluids.
                    id: 'minecraft:iron_nugget',
                    count: 10
                }
            },
            {
                type: 'neoforge:energy',
                io: 'input', // This can also be set to output.
                fe_per_tick: 1 // Consume 1 FE per tick.
            }
        ]
    }).id('mmcr_kubejs:blast_furnace_1') // Optional recipe ID provided by KubeJS.

    event.custom({
        type: 'mmcr:machine_recipe',
        recipe_pool: 'mmcr_kubejs:kubejs_blast_furnace',
        tick_time: 100,
        requirements: [
            {
                type: 'minecraft:item',
                io: 'input',
                item: { item: 'minecraft:gold_ingot' },
                count: 1
            },
            {
                type: 'minecraft:item',
                io: 'output',
                stack: {
                    id: 'minecraft:gold_nugget',
                    count: 10
                }
            },
            {
                type: 'neoforge:energy',
                io: 'input',
                fe_per_tick: 1
            }
        ]
    })

    // The first machine recipe is complete.
})
