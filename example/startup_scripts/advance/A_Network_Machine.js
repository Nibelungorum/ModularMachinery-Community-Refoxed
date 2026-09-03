MMCREvents.startup(event => {
    const api = MMCR.getAPI()
    const RecipeIO = api.recipeIO()

    const ExplosionInteraction = Java.loadClass("net.minecraft.world.level.Level$ExplosionInteraction")

    const PRODUCER_ID = "mmcr_kubejs:kubejs_network_producer_machine"
    const CENTER_ID = "mmcr_kubejs:kubejs_network_center_machine"
    const REPORT_POWER = "mmcr_kubejs:report_power"

    const waterRequirement = api.fluidInputRequirement("minecraft:water", 100)

    const machine_producer = event
        .createMachine(PRODUCER_ID)
        .displayNameKey("machine.mmcr_kubejs.kubejs_network_producer_machine")
        .appearance("minecraft:white_wool")
        .networkInterface(1, 1) // allow 1 network port and max 1 connections
        .allowNetworkMachine(CENTER_ID) // allow connect to network center machine

    machine_producer
        .tickBehavior(behavior => behavior
            .serverTick(ctx => {
                const storage = ctx.dataStorage()
                if (storage == null) return

                let power = storage.get("power").flatMap(v => v.asInt()).orElse(20) // some data type named power?
                let dry_sec = storage.get("dry_sec").flatMap(v => v.asInt()).orElse(0) // if the machine has been long without cool down, it will explosed
                let feOk = true

                // consume fe
                const energyPlan = ctx.ioPlan()
                energyPlan.addInput(api.energyRequirement(RecipeIO.INPUT, 100))
                if (!energyPlan.commit().successful()) {
                    feOk = false
                }

                if (feOk && ctx.isDue(20)) {
                    // consume water, every 20 ticks
                    const waterPlan = ctx.ioPlan()
                    waterPlan.addInput(waterRequirement)
                    const waterSim = waterPlan.simulate()

                    if (waterSim.inputsSatisfied()) {
                        waterPlan.commit()
                        power = 20
                        dry_sec = 0
                    } else {
                        power = 10
                        dry_sec = dry_sec + 1
                    }

                    // update the power and dry_sec
                    storage.set("power", api.dataValue(power))
                    storage.set("dry_sec", api.dataValue(dry_sec))

                    // if it has been dry for 30 secends, explode
                    if (dry_sec >= 30) {
                        const level = ctx.level()
                        const pos = ctx.controllerPos()
                        level.explode(
                            null,
                            pos.getX() + 0.5,
                            pos.getY() + 0.5,
                            pos.getZ() + 0.5,
                            4.0,
                            false,
                            ExplosionInteraction.BLOCK
                        )
                        return
                    }

                    const interfaces = api.networkInterfaces(ctx)
                    const iface = interfaces != null && !interfaces.isEmpty() ? interfaces.get(0) : null
                    if (iface != null) {
                        const connections = iface.connections()
                        const target = connections != null && !connections.isEmpty() ? connections.get(0) : null
                        if (target != null) {
                            // send the network center that I can provide 20 powers
                            // make sure you have done enough null check
                            api.sendRequest(iface, target, REPORT_POWER, {
                                power: power
                            })
                        }
                    }
                }

                // then it's better to add some infomation on the UI and Jade display if u like
                // This will do every tick, so we needn't CONTROLLER instead of OPERATION
                const powerId = api.id("mmcr_kubejs:producer_power")
                const waterId = api.id("mmcr_kubejs:producer_water")
                const feId = api.id("mmcr_kubejs:producer_fe")

                ctx.screenText().append(api.screenScope().OPERATION, powerId, Text.literal("Computing Power: " + power + " tfps"))
                ctx.screenText().append(api.screenScope().OPERATION, waterId, Text.literal(
                    dry_sec === 0
                        ? "Water: OK"
                        : "Water: DRY (overflow in " + Math.max(0, 30 - dry_sec) + " sec)"
                ))
                ctx.screenText().append(api.screenScope().OPERATION, feId, Text.literal(
                    feOk ? "Energy: OK" : "Energy: LOW"
                ))

                ctx.jadeText().append(powerId, Text.literal(power + " tfps"))
                ctx.jadeText().append(waterId, Text.literal(
                    dry_sec === 0 ? "Water OK" : "Water DRY"
                ))
            })
        )

    machine_producer.register()

    const machine_center = event
        .createMachine(CENTER_ID)
        .displayNameKey("machine.mmcr_kubejs.kubejs_network_center_machine")
        .appearance("minecraft:black_wool")
        .networkInterface(1, 16) // one interface but 16 connections
        .allowNetworkMachine(PRODUCER_ID)
        // register a request porcess for REPORT_POWER id
        .requestProcess(REPORT_POWER, (body, request, senderStorage, receiverStorage) => {
            if (receiverStorage == null) return
            // the power value that producer produced(what a sentence)
            const reported = body.get("power").flatMap(v => v.asInt()).orElse(0)
            // set it's unique name, here you can use hash
            const key = "power_" + request.peer().hash()
            receiverStorage.set(key, api.dataValue(reported))
        })

    machine_center
        .tickBehavior(behavior => behavior
            .serverTick(ctx => {
                const storage = ctx.dataStorage()
                if (storage == null) return

                const feId = api.id("mmcr_kubejs:center_fe")

                const energyPlan = ctx.ioPlan()
                energyPlan.addInput(api.energyRequirement(RecipeIO.INPUT, 200))
                const energyOk = energyPlan.commit().successful()
                // consume FE

                if (ctx.isDue(20)) {
                    let count = 0
                    const connectedHashes = new Set()
                    const interfaces = api.networkInterfaces(ctx)
                    const iface = interfaces != null && !interfaces.isEmpty() ? interfaces.get(0) : null
                    if (iface != null) {
                        for (const target of iface.connections()) {
                            count = count + 1
                            connectedHashes.add(target.hash())
                        }
                    }
                    storage.set("producer_count", api.dataValue(count))

                    // clean the offline producers
                    for (const key of storage.values().keySet()) {
                        if (typeof key === "string" && key.startsWith("power_")) {
                            const h = Number(key.substring("power_".length))
                            if (!connectedHashes.has(h)) storage.remove(key)
                        }
                    }
                }

                // compute all the powers
                let total = 0
                for (const [key, value] of storage.values().entrySet()) {
                    if (typeof key === "string" && key.startsWith("power_")) {
                        total = total + value.asInt().orElse(0)
                    }
                }

                // some information display
                const count = storage.get("producer_count").flatMap(v => v.asInt()).orElse(0)

                const powerId = api.id("mmcr_kubejs:center_power")
                const countId = api.id("mmcr_kubejs:center_count")

                ctx.screenText().append(api.screenScope().OPERATION, powerId, Text.literal("Total Power: " + total + " tfps"))
                ctx.screenText().append(api.screenScope().OPERATION, countId, Text.literal("Connected Devices: " + count))
                ctx.screenText().append(api.screenScope().OPERATION, feId, Text.literal(
                    energyOk ? "Energy: OK" : "Energy: LOW"
                ))

                ctx.jadeText().append(powerId, Text.literal("Total Power: " + total + " tfps"))
                ctx.jadeText().append(countId, Text.literal("Connected Devices: " + count + " producers"))
            })
        )

    machine_center.register()
    // so just a few codes, you can create a powerful power system!
})
