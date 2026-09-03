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
        .networkInterface(1, 8)
        .allowNetworkMachine(CENTER_ID)

    machine_producer
        .tickBehavior(behavior => behavior
            .serverTick(ctx => {
                const storage = ctx.dataStorage()
                if (storage == null) return

                let power = storage.get("power").flatMap(v => v.asInt()).orElse(20)
                let dryTicks = storage.get("dry_ticks").flatMap(v => v.asInt()).orElse(0)
                let feOk = true

                const energyPlan = ctx.ioPlan()
                energyPlan.addInput(api.energyRequirement(RecipeIO.INPUT, 100))
                if (!energyPlan.commit().successful()) {
                    feOk = false
                }

                if (feOk && ctx.isDue(20)) {
                    const waterPlan = ctx.ioPlan()
                    waterPlan.addInput(waterRequirement)
                    const waterSim = waterPlan.simulate()

                    if (waterSim.inputsSatisfied()) {
                        waterPlan.commit()
                        power = 20
                        dryTicks = 0
                    } else {
                        power = 10
                        dryTicks = dryTicks + 20
                    }

                    storage.set("power", api.dataValue(power))
                    storage.set("dry_ticks", api.dataValue(dryTicks))

                    if (dryTicks >= 600) {
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
                    for (let i = 0; i < interfaces.size(); i++) {
                        const iface = interfaces.get(i)
                        const connections = iface.connections()
                        for (let j = 0; j < connections.size(); j++) {
                            api.sendRequest(iface, connections.get(j), REPORT_POWER, {
                                power: power
                            })
                        }
                    }
                }

                const powerId = api.id("mmcr_kubejs:producer_power")
                const waterId = api.id("mmcr_kubejs:producer_water")
                const feId = api.id("mmcr_kubejs:producer_fe")

                ctx.screenText().append(api.screenScope().OPERATION, powerId, Text.literal("Computing Power: " + power + " tfps"))
                ctx.screenText().append(api.screenScope().OPERATION, waterId, Text.literal(
                    dryTicks === 0
                        ? "Water: OK"
                        : "Water: DRY (overflow in " + Math.max(0, Math.floor((600 - dryTicks) / 20)) + " cycles)"
                ))
                ctx.screenText().append(api.screenScope().OPERATION, feId, Text.literal(
                    feOk ? "Energy: OK" : "Energy: LOW"
                ))

                ctx.jadeText().append(powerId, Text.literal(power + " tfps"))
                ctx.jadeText().append(waterId, Text.literal(
                    dryTicks === 0 ? "Water OK" : "Water DRY"
                ))
            })
        )

    machine_producer.register()

    const machine_center = event
        .createMachine(CENTER_ID)
        .displayNameKey("machine.mmcr_kubejs.kubejs_network_center_machine")
        .appearance("minecraft:black_wool")
        .networkInterface(1, 16)
        .allowNetworkMachine(PRODUCER_ID)
        .requestProcess(REPORT_POWER, (body, request, senderStorage, receiverStorage) => {
            if (receiverStorage == null) return
            const reported = body.get("power").flatMap(v => v.asInt()).orElse(0)
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

                if (ctx.isDue(20)) {
                    const interfaces = api.networkInterfaces(ctx)
                    let count = 0
                    for (let i = 0; i < interfaces.size(); i++) {
                        count = count + interfaces.get(i).connections().size()
                    }
                    storage.set("producer_count", api.dataValue(count))

                    const connectedHashes = new Set()
                    for (let i = 0; i < interfaces.size(); i++) {
                        const connections = interfaces.get(i).connections()
                        for (let j = 0; j < connections.size(); j++) {
                            connectedHashes.add(connections.get(j).hash())
                        }
                    }

                    const stale = []
                    storage.values().forEach((key, value) => {
                        if (typeof key === "string" && key.startsWith("power_")) {
                            const suffix = key.substring("power_".length)
                            const h = Number(suffix)
                            if (!connectedHashes.has(h)) stale.push(key)
                        }
                    })
                    for (let i = 0; i < stale.length; i++) {
                        storage.remove(stale[i])
                    }
                }

                let total = 0
                storage.values().forEach((key, value) => {
                    if (typeof key === "string" && key.startsWith("power_")) {
                        total = total + value.asInt().orElse(0)
                    }
                })

                const count = storage.get("producer_count").flatMap(v => v.asInt()).orElse(0)

                const powerId = api.id("mmcr_kubejs:center_power")
                const countId = api.id("mmcr_kubejs:center_count")

                ctx.screenText().append(api.screenScope().OPERATION, powerId, Text.literal("Total Power: " + total + " tfps"))
                ctx.screenText().append(api.screenScope().OPERATION, countId, Text.literal("Connected Producers: " + count))
                ctx.screenText().append(api.screenScope().OPERATION, feId, Text.literal(
                    energyOk ? "Energy: OK" : "Energy: LOW"
                ))

                ctx.jadeText().append(powerId, Text.literal(total + " tfps"))
                ctx.jadeText().append(countId, Text.literal(count + " producers"))
            })
        )

    machine_center.register()
})
