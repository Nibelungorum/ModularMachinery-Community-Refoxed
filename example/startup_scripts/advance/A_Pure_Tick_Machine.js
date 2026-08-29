MMCREvents.startup(event => {
    const machine = event
        .createMachine("mmcr_kubejs:kubejs_pure_tick_machine")
        .displayNameKey("machine.mmcr_kubejs.kubejs_pure_tick_machine")
        .recipeFamily("mmcr_kubejs:kubejs_pure_tick_machine") // This will set the JEI recipe page type
        .appearance("minecraft:green_terracotta");

    const api = MMCR.getAPI()
    const Player = Java.loadClass("net.minecraft.world.entity.player.Player")
    const EnergyRequirement = Java.loadClass("cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement") // I do not provide EnergyRequirement directly because its not a widely used api


    machine
        // Here pure tick machine also allowed to use multi thread block and parallelism controller
        // But they do not have any actual usage, only become one type of numbers you can use in your code
        .allowMultithreading()
        .allowParallelism()
        .maxParallelAmount(2147483647)
        // Use tickBehavior will make the machine become a pure tick machine
        .tickBehavior(behavior => behavior
            .serverTick(ctx => {
                // every 40 ticks do a actual tick
                // this is one inner tick timer you can use
                if (!ctx.isDue(40)) return

                let plan_fe = ctx.ioPlan()

                // need 10 FE to start this tick
                plan_fe.addInput(new EnergyRequirement(10))

                const feSimulation = plan_fe.simulate()

                if (!feSimulation.energySatisfied()) {
                    // if fe not enough
                    ctx.screenText().replace(
                        api.id("mmcr_kubejs:fe_status"),
                        Text.literal("FE is needed!")
                    )
                    // direct return
                    return
                }

                // consume 10 FE
                if (!plan_fe.commit().successful()) {
                    ctx.screenText().replace(
                        api.id("mmcr_kubejs:fe_status"),
                        Text.literal("FE consume error!")
                    )
                    return
                }

                // if fe is enough
                ctx.screenText().replace(
                    api.id("mmcr_kubejs:fe_status"),
                    Text.literal("Machine do a run!")
                    // actually, because this is a tick function, you can only see this line for one tick
                )

                // some tricks...summon lighting bolt?
                const level = ctx.level()
                const pos = ctx.controllerPos()
                const area = AABB.of(
                    pos.getX() - 1,
                    level.getMinY(),
                    pos.getZ() - 1,
                    pos.getX() + 2,
                    level.getMaxY() + 1,
                    pos.getZ() + 2
                )
                const players = level.getEntitiesOfClass(Player, area)
                players.forEach(player => {
                    level.spawnLightning(
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        false
                    )
                })

                // Do some io if you like
                const plan = ctx.ioPlan()

                plan.add(
                    api.itemInputRequirement(
                        "minecraft:iron_ingot",
                        1
                    )
                )

                plan.add(
                    api.itemOutputRequirement(
                        "minecraft:gold_nugget",
                        1,
                        1.0
                    )
                )


                // if you want to process some special recipe
                // make a simulate before the actual process start
                const simulation = plan.simulate()

                if (!simulation.inputsSatisfied()) return
                let outputAvailable = true

                simulation.outputs().forEach(output => {
                    if (output.accepted() < output.requested()) {
                        outputAvailable = false
                    }
                })

                if (!outputAvailable) return

                ctx.screenText().replace(
                    api.id("mmcr_kubejs:pure_tick_status"),
                    Text.literal("Iron Ingot inputed")
                    // you can only see this line for one tick also
                )

                // if successed, commit it and we will get what we want
                plan.commit()
            })
        )

    machine.register()

    // register a static text line
    // NOTICE:
    // use static lines and ctx.screenText().replace() always depend on the actual situation
    // the static lines will refresh every client tick and replace is always the last one which is able to cover static lines
    // If you want to make some differences, please use data storage and networks
    // Which will showed in A_Data_Storage_Machine
    event.registerControllerScreenText(
        "mmcr_kubejs:kubejs_pure_tick_machine",
        text => {

            text.append(
                "controller",
                "mmcr_kubejs:fe_status",
                Text.literal("FE is needed!")
            )

            text.append(
                "controller",
                "mmcr_kubejs:pure_tick_status",
                Text.literal("No Ingot input")
            )
        }
    )

    // Actually, you can also use multi thread and smart interface
    // However, they only can provide some custom numbers
})
