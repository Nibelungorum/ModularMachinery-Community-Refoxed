// Here we will introduce the level system and shared structure system

MMCREvents.startup(event => {

    // If you see it in game, you will find this is one gt machine uhh
    const builder = event
        .createMachine("mmcr_kubejs:kubejs_thermal_smelting_furnace")
        .displayNameKey("machine.mmcr_kubejs.kubejs_thermal_smelting_furnace")
        .recipeFamily("mmcr_kubejs:kubejs_thermal_smelting_furnace")
        .appearance("minecraft:smooth_basalt")

    builder.register()

    // Then we should register level type here
    // you must be sure that level type register is prior to level
    // when used in recipe and structure, you needn't care this
    event
        .createLevelType("mmcr_kubejs:thermal_smelting_coil") // type register name
        .displayName('level.mmcr_kubejs.thermal_smelting_coil') // type display name
        .register() // register

    // then create level
    event.createLevel("mmcr_kubejs:thermal_smelting_coil_iron") // level register name
        .type("mmcr_kubejs:thermal_smelting_coil") // level type
        .priority(0) // prority, iron usually be the lowest
        .state('minecraft:iron_block') // the block state, it's difficulty to get block state in kubejs, so just use block id
        .modifier({ // the level can provide some modifiers, they are work as same as single block modifier
            durationMultiplier: 0.95,
            energyMultiplier: 0.95,
            parallelismBonus: 4
        })
        .register() // then register

    event.createLevel("mmcr_kubejs:thermal_smelting_coil_gold")
        .type("mmcr_kubejs:thermal_smelting_coil")
        .priority(1)
        .state('minecraft:gold_block')
        .modifier({
            durationMultiplier: 0.85,
            energyMultiplier: 0.85,
            parallelismBonus: 6
        })
        .register()

    event.createLevel("mmcr_kubejs:thermal_smelting_coil_diamond")
        .type("mmcr_kubejs:thermal_smelting_coil")
        .priority(2)
        .state('minecraft:diamond_block')
        .modifier({
            durationMultiplier: 0.75,
            energyMultiplier: 0.75,
            parallelismBonus: 6
        })
        .register()

    // Move to server_scripts/structure/A_Level_Machine.js see how to use level for your machine
})