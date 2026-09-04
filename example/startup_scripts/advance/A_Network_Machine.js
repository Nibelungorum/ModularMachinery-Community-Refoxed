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
                let storage = ctx.dataStorage()
                if (storage == null) return

                let power = Number(storage.get("power").flatMap(v => v.asDouble()).orElse(0))
                let dry_sec = Number(storage.get("dry_sec").flatMap(v => v.asDouble()).orElse(0))
                let feOk = true

                let energyPlan = ctx.ioPlan()
                energyPlan.addInput(api.energyRequirement(RecipeIO.INPUT, 100))
                let energySim = energyPlan.simulate()
                if (!energySim.energySatisfied() || !energyPlan.commit().successful()) {
                    feOk = false
                }
                // Skip downstream updates while the structure is unpowered so the UI shows the
                // last computed power and the dry countdown keeps its persisted value.
                let powerPublished = power
                let dryPublished = dry_sec
                let shouldReport = false

                if (ctx.isDue(20)) {
                    let waterPlan = ctx.ioPlan()
                    waterPlan.addInput(waterRequirement)
                    let waterSim = waterPlan.simulate()

                    if (feOk && waterSim.inputsSatisfied()) {
                        waterPlan.commit()
                        power = 20
                        dry_sec = 0
                    } else if (!feOk) {
                        // No energy -> the machine never recovers; do not advance or reset the timer.
                        power = 10
                    } else {
                        power = 10
                        dry_sec = dry_sec + 1
                    }

                    storage.set("power", api.dataValue(power))
                    storage.set("dry_sec", api.dataValue(dry_sec))
                    powerPublished = power
                    dryPublished = dry_sec

                    if (dry_sec >= 30) {
                        let level = ctx.level()
                        let pos = ctx.controllerPos()
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

                    shouldReport = feOk
                }

                if (shouldReport) {
                    let interfaces = api.networkInterfaces(ctx)
                    let iface = interfaces != null && !interfaces.isEmpty() ? interfaces.get(0) : null
                    if (iface != null) {
                        let connections = iface.connections()
                        let target = connections != null && !connections.isEmpty() ? connections.get(0) : null
                        if (target != null) {
                            api.sendRequest(iface, target, REPORT_POWER, {
                                power: powerPublished
                            })
                        }
                    }
                }

                let powerId = api.id("mmcr_kubejs:producer_power")
                let waterId = api.id("mmcr_kubejs:producer_water")
                let feId = api.id("mmcr_kubejs:producer_fe")

                ctx.screenText().append(api.screenScope().OPERATION, powerId, Text.literal("Computing Power: " + powerPublished + " tfps"))
                ctx.screenText().append(api.screenScope().OPERATION, waterId, Text.literal(
                    dryPublished === 0
                        ? "Water: OK"
                        : "Water: DRY (overflow in " + Math.max(0, 30 - dryPublished) + " sec)"
                ))
                ctx.screenText().append(api.screenScope().OPERATION, feId, Text.literal(
                    feOk ? "Energy: OK" : "Energy: LOW"
                ))

                ctx.jadeText().append(powerId, Text.literal(powerPublished + " tfps"))
                ctx.jadeText().append(waterId, Text.literal(
                    dryPublished === 0 ? "Water OK" : "Water DRY"
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
            let reported = Number(body.get("power").flatMap(v => v.asDouble()).orElse(0))
            // set it's unique name, here you can use hash
            let key = "power_" + request.peer().hash()
            receiverStorage.set(key, api.dataValue(reported))
        })

    machine_center
        .tickBehavior(behavior => behavior
            .serverTick(ctx => {
                let storage = ctx.dataStorage()
                if (storage == null) return

                let feId = api.id("mmcr_kubejs:center_fe")

                let energyPlan = ctx.ioPlan()
                energyPlan.addInput(api.energyRequirement(RecipeIO.INPUT, 200))
                let energySim = energyPlan.simulate()
                let energyOk = energySim.energySatisfied() && energyPlan.commit().successful()
                // consume FE

                // Re-derive the live producer count every tick so the UI reflects the actual
                // network connections even when storage has not been refreshed recently.
                let liveCount = 0
                let connectedHashes = new Set()
                let interfaces = api.networkInterfaces(ctx)
                let iface = interfaces != null && !interfaces.isEmpty() ? interfaces.get(0) : null
                if (iface != null) {
                    for (let target of iface.connections()) {
                        liveCount = liveCount + 1
                        connectedHashes.add(String(target.hash()))
                    }
                }
                // Remove reports whose producer is no longer connected. Collect keys first
                // because removing while iterating the Java map is unsafe.
                let staleKeys = []
                if (iface != null) {
                    for (let key of storage.values().keySet()) {
                        let keyString = key.toString()
                        if (keyString.startsWith("power_")
                                && !connectedHashes.has(keyString.substring("power_".length))) {
                            staleKeys.push(key)
                        }
                    }
                }
                for (let key of staleKeys) storage.remove(key)

                // compute all the powers
                let total = 0
                let valueIter = storage.values().entrySet().iterator()
                while (valueIter.hasNext()) {
                    let entry = valueIter.next()
                    let key = entry.getKey()
                    let value = entry.getValue()
                    if (key.toString().startsWith("power_")) {
                        total = total + value.asDouble().orElse(0)
                    }
                }

                // some information display
                let count = liveCount

                let powerId = api.id("mmcr_kubejs:center_power")
                let countId = api.id("mmcr_kubejs:center_count")

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
