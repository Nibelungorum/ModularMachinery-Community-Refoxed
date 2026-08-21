// Here we create a recipe for our blast furnace machine
// We use ServerEvents.recipes, which means it was drived by data, and supporting hot reload

ServerEvents.recipes( event => {
    // So boring a register, just use event.custom and register a standard object
    event.custom({
        type: 'mmcr:machine_recipe', // // MUST it must be mmcr:machine_recipe
        machine: 'mmcr_kubejs:kubejs_blast_furnace', // MUST your machine
        tick_time: 100, // MUST the time the recipe cost, int, and must > 0, at least 1
        inputs: [ // OPTIONAL, uhh there are empty input and empty output recipe
            {
                item: 'minecraft:iron_ingot', // you can use direct item
                count: 1 // the ItemStack size
            }
        ],
        outputs: [
            {
                id: 'minecraft:iron_nugget',
                count: 10 // wow, one more iron_nugget because we use mmcr !
            }
        ],
        energy_per_tick: 1 // the energy every tick cost
    }).id('mmcr_kubejs:blast_furnace_1') // the recipe id, optional
})