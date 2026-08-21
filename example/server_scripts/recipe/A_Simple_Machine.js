// Here we create a recipe for our blast furnace machine
// We use ServerEvents.recipes, which means it was drived by data, and supporting hot reload

ServerEvents.recipes( event => {
    // So boring a register, just use event.custom and register a standard object
    event.custom({
        type: 'mmcr:machine_recipe', // // MUST it must be mmcr:machine_recipe
        machine: 'mmcr_kubejs:kubejs_blast_furnace', // MUST your machine
        tick_time: 100, // MUST the time the recipe cost, int, and must > 0, at least 1
        parallelized: true, // allow this recipe to use the machine's parallel controllers
        // Optional, means you can create an empty input and enmpty output recipe
        requirements: [
            {
                type: 'item', // here declare its stack type, you can use fluid also
                io: 'input', // here declare its io type, you can use output to declare it's output
                item: 'minecraft:iron_ingot', // you can use integrate also
                count: 1
            },
            {
                type: 'item',
                io: 'output',
                stack: { // you must use stack for output, both fluid and item
                    id: 'minecraft:iron_nugget',
                    count: 10 // wow, one more iron_nugget because we use mmcr !
                }
            },
            {
                type: 'energy',
                io: 'input', // you can also change to output
                fe_per_tick: 1 // use 1 FE per tick
            }
        ]
    }).id('mmcr_kubejs:blast_furnace_1') // the recipe id, this is an api provided by kubejs, optional

    // you have finished your first machine ! just launch game and see what happen
})
