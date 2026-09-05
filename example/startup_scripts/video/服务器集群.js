MMCREvents.startup(event => {
    const api = MMCR.getAPI()
    const RecipeIO = api.recipeIO()
    const ExplosionInteraction = Java.loadClass("net.minecraft.world.level.Level$ExplosionInteraction")
    const PRODUCER_ID = "kubejs:server"
    const CENTER_ID = "kubejs:center"
    const REPORT_POWER = "kubejs:report_power"
    const waterRequirement = api.fluidInputRequirement("minecraft:water", 100)

    const machine_producer = event
        .createMachine(PRODUCER_ID)
        .displayNameKey("machine.kuebjs.server")
        .appearance("mekanism:block_tin")
        .networkInterface(1, 1)
        .allowNetworkMachine(CENTER_ID)

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
                let powerPublished = power
                let dryPublished = dry_sec
                let shouldReport = false
                if (ctx.isDue(20)) {
                    let waterPlan = ctx.ioPlan()
                    waterPlan.addInput(waterRequirement)
                    let waterSim = waterPlan.simulate()
                    let hasWater = waterSim.inputsSatisfied()
                    if (feOk && hasWater && waterPlan.commit().successful()) {
                        power = 20
                        dry_sec = 0
                    } else {
                        power = 10
                        if (feOk) {
                            dry_sec = dry_sec + 1
                            hasWater = false
                        }
                    }
                    storage.set("has_water", api.dataValue(hasWater))
                    storage.set("power", api.dataValue(power))
                    storage.set("dry_sec", api.dataValue(dry_sec))
                    powerPublished = power
                    dryPublished = dry_sec
                    if (dry_sec >= 30) {
                        let level = ctx.level()
                        let pos = ctx.controllerPos()
                        // level.explode(
                        //     null,
                        //     pos.getX() + 0.5,
                        //     pos.getY() + 0.5,
                        //     pos.getZ() + 0.5,
                        //     4.0,
                        //     false,
                        //     ExplosionInteraction.BLOCK
                        // )
                        // dry_sec = 0
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
                let powerId = api.id("kubejs:producer_power")
                let waterId = api.id("kubejs:producer_water")
                let feId = api.id("kubejs:producer_fe")
                ctx.screenText().append(api.screenScope().OPERATION, powerId, Text.literal("算力产出: " + powerPublished + " FLOPS"))

                let has_water = storage.get("has_water").flatMap(v => v.asBoolean()).orElse(false)
                if(!has_water){
                    ctx.screenText().append(api.screenScope().OPERATION, waterId, Text.literal(
                        "冷却剂: 不足"
                    ))
                }else{
                    ctx.screenText().append(api.screenScope().OPERATION, waterId, Text.literal(
                    dryPublished === 0
                        ? "冷却剂: 正常"
                        : "冷却剂: 不足 (将在" + Math.max(0, 30 - dryPublished) + " 秒后超载)"
                ))
                }

                ctx.screenText().append(api.screenScope().OPERATION, feId, Text.literal(
                    feOk ? "能量输入: 正常" : "能量输入: 低"
                ))
                ctx.jadeText().append(powerId, Text.literal("算力产出: " + powerPublished + " FLOPS"))
                if (!has_water) {
                    ctx.jadeText().append(waterId, Text.literal(
                        "冷却剂: 不足"
                    ))
                }else{
                    ctx.jadeText().append(waterId, Text.literal(
                        dryPublished === 0 ? "冷却剂: 正常" : "冷却剂: 不足"
                    ))
                }
            })
        )

    machine_producer.register()

    const machine_center = event
        .createMachine(CENTER_ID)
        .displayNameKey("machine.kubejs.center")
        .appearance("mekanism:superheating_element")
        .networkInterface(1, 16)
        .allowNetworkMachine(PRODUCER_ID)
        .requestProcess(REPORT_POWER, (body, request, senderStorage, receiverStorage) => {
            if (receiverStorage == null) return
            let reported = Number(body.get("power").flatMap(v => v.asDouble()).orElse(0))
            let key = "power_" + request.peer().hash()
            receiverStorage.set(key, api.dataValue(reported))
        })

    machine_center
        .tickBehavior(behavior => behavior
            .serverTick(ctx => {
                let storage = ctx.dataStorage()
                if (storage == null) return
                let feId = api.id("kubejs:center_fe")
                let energyPlan = ctx.ioPlan()
                energyPlan.addInput(api.energyRequirement(RecipeIO.INPUT, 200))
                let energySim = energyPlan.simulate()
                let energyOk = energySim.energySatisfied() && energyPlan.commit().successful()
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
                let count = liveCount

                let powerId = api.id("kuebjs:center_power")
                let countId = api.id("kuebjs:center_count")

                ctx.screenText().append(api.screenScope().OPERATION, powerId, Text.literal("总算力: " + total + " FLOPS"))
                ctx.screenText().append(api.screenScope().OPERATION, countId, Text.literal("在线设备: " + count))
                ctx.screenText().append(api.screenScope().OPERATION, feId, Text.literal(
                    energyOk ? "能量输入: 正常" : "能量输入: 低"
                ))

                ctx.jadeText().append(powerId, Text.literal("总算力: " + total + " FLOPS"))
                ctx.jadeText().append(countId, Text.literal("在线设备: " + count))
            })
        )

    machine_center.register()
})
