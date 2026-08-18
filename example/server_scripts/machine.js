// KubeJS recreation of the development defaults. It deliberately does not read mmcr:* definitions.
MMCREvents.server(function(event) {
  var api = event.api()
  var LinkedHashMap = Java.loadClass('java.util.LinkedHashMap')
  var NS = 'mmcr_kubejs'
  function cloneId(path) { return NS + ':kubejs_' + path }
  function controllerBlock(path) { return api.block('mmcr:kubejs_' + path + '_controller') }

function ports(io, category) {
  var tiers = category === 'fluid'
    ? ['tiny', 'small', 'normal', 'reinforced', 'big', 'huge', 'ludicrous', 'vacuum']
    : category === 'energy'
    ? ['tiny', 'small', 'normal', 'reinforced', 'big', 'huge', 'ludicrous', 'ultimate']
    : ['tiny', 'small', 'normal', 'reinforced', 'big', 'huge', 'ludicrous']
  var family = category === 'item' ? 'bus' : 'hatch'
  var predicates = []
  for (var tierIndex = 0; tierIndex < tiers.length; tierIndex++) {
    var tier = tiers[tierIndex]
    predicates.push(api.block('mmcr:' + category + '_' + io + '_' + family + (tier === 'normal' ? '' : '_' + tier)))
  }
  return api.anyOf.apply(api, predicates)
}
function allPorts() {
  return api.anyOf(ports('input', 'item'), ports('output', 'item'), ports('input', 'fluid'),
    ports('output', 'fluid'), ports('input', 'energy'), ports('output', 'energy'))
}
function allPortsExceptTinyOutput() {
  return api.anyOf(ports('input', 'item'), ports('output', 'item'), ports('input', 'fluid'),
    ports('output', 'fluid'), ports('input', 'energy'), ports('output', 'energy'))
}
function parallelControllers() {
  return api.anyOf(api.block('mmcr:parallel_controller_normal'), api.block('mmcr:parallel_controller_plus'),
    api.block('mmcr:parallel_controller_reinforced'), api.block('mmcr:parallel_controller_pro'),
    api.block('mmcr:parallel_controller_elite'), api.block('mmcr:parallel_controller_fantasy'),
    api.block('mmcr:parallel_controller_max'), api.block('mmcr:parallel_controller_ultimate'))
}
function parallelSlots(base) { return api.anyOf(base, allPorts(), parallelControllers()) }
function pattern(slices, keys, controller) {
  var entries = new LinkedHashMap()
  var centerHeight = slices[0].length
  var centerWidth = slices[0][0].length
  var controllerPos = null
  for (var z = 0; z < slices.length; z++) {
    var slice = slices[z]
    var height = slice.length
    for (var row = 0; row < height; row++) {
      var line = slice[row]
      var width = line.length
      for (var column = 0; column < width; column++) {
        var symbol = line[column]
        var predicate = keys[symbol]
        if (predicate === undefined) continue
        var pos = api.pos(column - Math.floor(centerWidth / 2), row - Math.floor(centerHeight / 2), z - Math.floor(slices.length / 2))
        entries.put(pos, predicate)
        if (symbol === controller) controllerPos = pos
      }
    }
  }
  if (controllerPos === null || (controllerPos.x === 0 && controllerPos.y === 0 && controllerPos.z === 0)) return api.blockArray(entries)
  var normalized = new LinkedHashMap()
  var iterator = entries.entrySet().iterator()
  while (iterator.hasNext()) {
    var entry = iterator.next()
    var entryPos = entry.getKey()
    var entryPredicate = entry.getValue()
    normalized.put(api.pos(entryPos.x - controllerPos.x, entryPos.y - controllerPos.y, entryPos.z - controllerPos.z), entryPredicate)
  }
  return api.blockArray(normalized)
}
function full(path, array, tiers) {
  if (tiers === undefined) tiers = []
  event.createStructure(cloneId(path))
    .fullStructure(array, api.portRequirements({}), api.portTierRequirements(tiers), [], new LinkedHashMap(), new LinkedHashMap()).build()
}
function json(value) {
  return JsonIO.parseRaw(JSON.stringify(value))
}

var itemIn = ports('input', 'item')
var itemOut = ports('output', 'item')
var fluidIn = ports('input', 'fluid')
var fluidOut = ports('output', 'fluid')
var energyIn = ports('input', 'energy')
var energyOut = ports('output', 'energy')
var casing = api.block('mmcr:basic_casing')
var factory = api.block('mmcr:factory_controller')
var smart = api.block('mmcr:smart_interface')

var io = allPorts()
full('blast_furnace', pattern([
  ['AXA', 'XIX', 'XXX'], ['XXX', 'I I', 'XBX'], ['AXA', 'XCX', 'XXX']
], {
  X: api.anyOf(casing, io), A: parallelSlots(casing), B: api.anyOf(casing, io, factory),
  C: controllerBlock('blast_furnace'), I: api.anyOf(itemIn, itemOut, fluidIn, fluidOut, energyIn, energyOut)
}, 'C'), ['energy_input_hatch>=ludicrous', 'item_input_bus>=normal', 'item_output_bus>=tiny'])

var alloyModifiers = new LinkedHashMap()
var alloyModifierPositions = [api.pos(0, -1, -1), api.pos(0, 1, -1)]
for (var alloyModifierIndex = 0; alloyModifierIndex < alloyModifierPositions.length; alloyModifierIndex++) {
  var pos = alloyModifierPositions[alloyModifierIndex]
  alloyModifiers.put(pos, [
    api.singleBlockModifier('alloy_furnace_diamond_speedup', pos, api.block('minecraft:diamond_block'), [api.modifier('duration', 'input', 0.5, 'multiply', false)], '钻石块：配方时间折半', Item.of('minecraft:diamond_block')),
    api.singleBlockModifier('alloy_furnace_gold_doubling', pos, api.block('minecraft:gold_block'), [api.modifier('item', 'output', 2, 'multiply', false)], '金块：产物数量翻倍', Item.of('minecraft:gold_block'))
  ])
}
event.createStructure(cloneId('alloy_furnace')).fullStructure(pattern([
  ['XXX', 'XIX', 'XXX'], ['XMX', 'I I', 'XMX'], ['XXX', 'XCX', 'XXX']
], { X: api.block('minecraft:bricks'), I: api.anyOf(itemIn, itemOut, energyIn), M: api.block('minecraft:blast_furnace'), C: controllerBlock('alloy_furnace') }, 'C'), api.portRequirements({}), api.portTierRequirements([]), [], alloyModifiers, new LinkedHashMap()).build()

full('cracker', pattern([
  ['AAA', 'AAA', 'AAA'], ['XBX', 'B B', 'XBX'], ['XDX', 'D D', 'XDX'], ['XEX', 'ECE', 'XEX']
], { X: api.block('minecraft:polished_diorite'), A: api.block('minecraft:polished_andesite'), B: api.anyOf(itemIn, itemOut, fluidOut, energyIn, api.block('minecraft:weathered_copper')), D: api.block('minecraft:blue_ice'), E: api.block('minecraft:weathered_copper'), C: controllerBlock('cracker') }, 'C'), ['fluid_output_hatch>=huge', 'energy_input_hatch>=reinforced', 'item_input_bus>=normal', 'item_output_bus>=tiny'])

var reactorPort = api.anyOf(api.block('minecraft:blue_ice'), itemIn, itemOut, fluidIn, fluidOut, energyOut)
full('reactor', pattern([
  ['  AAAAA  ','         ','         ','         ','         ','         ','         ','         '], [' AAXXXAA ','   DDD   ','         ','         ','         ','         ','         ','         '], ['AAXXXXXAA','  EFFFE  ','  EFFFE  ','  EFFFE  ','  JJJJJ  ','         ','         ','         '], ['AXXXXXXXA',' DFGHGFD ','  FGHGF  ','  FGHGF  ','  JXXXJ  ','   KKK   ','         ','         '], ['AXXXXXXXA',' DFHXHFD ','  FHXHF  ','  FHXHF  ','  JXXXJ  ','   KLK   ','    L    ','    M    '], ['AXXXXXXXA',' DFGHGFD ','  FGHGF  ','  FGHGF  ','  JXXXJ  ','   KKK   ','         ','         '], ['AAXXXXXAA','  EFFFE  ','  EFFFE  ','  EFFFE  ','  JJJJJ  ','         ','         ','         '], [' AAXXXAA ','   DID   ','         ','         ','         ','         ','         ','         '], ['  AAAAA  ','         ','         ','         ','         ','         ','         ','         ']
], { X: api.block('minecraft:blue_ice'), A: api.block('minecraft:deepslate_brick_stairs'), D: reactorPort, E: api.block('minecraft:polished_deepslate'), F: api.block('minecraft:black_stained_glass'), G: api.block('oritech:uranium'), H: api.block('oritech:energite'), I: controllerBlock('reactor'), J: api.block('minecraft:polished_deepslate_stairs'), K: api.block('minecraft:deepslate_brick_slab'), L: api.block('minecraft:deepslate_tiles'), M: api.block('minecraft:oxidized_lightning_rod') }, 'I'))

var thermalA = api.anyOf(api.block('minecraft:smooth_basalt'), itemIn, itemOut, energyIn, factory, parallelControllers())
var thermalPattern = pattern([['AAA','XXX','XXX','AAA'], ['AAA','X X','X X','ADA'], ['ABA','XXX','XXX','AAA']], { X: api.anyOf(api.block('minecraft:copper_block'), api.block('minecraft:iron_block'), api.block('minecraft:gold_block'), api.block('minecraft:diamond_block')), A: thermalA, B: controllerBlock('thermal_smelting_furnace'), D: api.block('minecraft:reinforced_deepslate') }, 'B')
var thermalLevels = new LinkedHashMap()
var thermalSlices = [['AAA','XXX','XXX','AAA'], ['AAA','X X','X X','ADA'], ['ABA','XXX','XXX','AAA']]
// Normalize each coil position against the centered pattern and controller B offset.
for (var z = 0; z < thermalSlices.length; z++) for (var row = 0; row < 4; row++) for (var column = 0; column < 3; column++) {
  if (thermalSlices[z][row][column] === 'X') thermalLevels.put(api.pos(column - 1, row - 2, z - 1), api.id('mmcr:thermal_smelting_coil'))
}
event.createStructure(cloneId('thermal_smelting_furnace')).fullStructure(thermalPattern, api.portRequirements({}), api.portTierRequirements(['item_input_bus>=tiny', 'item_output_bus>=tiny', 'energy_input_hatch>=tiny']), [], new LinkedHashMap(), thermalLevels).build()

var purpurB = api.anyOf(api.block('minecraft:purpur_pillar'), itemIn, itemOut, energyIn, factory, smart, parallelControllers())
full('purpur_furnace', pattern([
  [' AAAAA ','       ','       ','       ','  GGG  ','       ','       ','       '], ['AAXXXAA','  BBB  ','  EEE  ','  FFF  ',' GBBBG ',' HHHHH ','       ','       '], ['AXXXXXA',' B   B ',' E   E ',' F   F ','GB   BG',' HXXXH ','  GBG  ','   I   '], ['AXXXXXA',' B   B ',' E   E ',' F   F ','GB   BG',' HX XH ','  B B  ','  I I  '], ['AXXXXXA',' B   B ',' E   E ',' F   F ','GB   BG',' HXXXH ','  GBG  ','   I   '], ['AAXXXAA','  BDB  ','  EEE  ','  FFF  ',' GBBBG ',' HHHHH ','       ','       '], [' AAAAA ','       ','       ','       ','  GGG  ','       ','       ','       ']
], { X: api.block('minecraft:end_stone_bricks'), A: api.block('minecraft:end_stone_brick_stairs'), B: purpurB, D: controllerBlock('purpur_furnace'), E: api.block('minecraft:purple_terracotta'), F: api.block('minecraft:purpur_block'), G: api.block('minecraft:end_stone_brick_slab'), H: api.block('minecraft:purpur_stairs'), I: api.block('minecraft:purpur_slab') }, 'D'), ['item_input_bus>=tiny', 'item_output_bus>=tiny', 'energy_input_hatch>=tiny'])

var towerA = api.anyOf(api.block('minecraft:deepslate_bricks'), allPortsExceptTinyOutput())
var towerC = api.anyOf(api.block('minecraft:deepslate_bricks'), api.block('mmcr:item_output_bus_tiny'))
function tower(rows) { return pattern(rows, { X: api.block('minecraft:polished_blackstone'), A: towerA, B: api.block('minecraft:polished_blackstone_bricks'), C: towerC, D: api.block('minecraft:gilded_blackstone'), E: controllerBlock('distillation_tower') }, 'E') }
var towerTiers = ['item_input_bus>=tiny', 'item_output_bus>=tiny', 'energy_input_hatch>=tiny']
var towerBuilder = event.createStructure(cloneId('distillation_tower'))
towerBuilder.fullStructure(tower([['  XXX  ','  AAA  ','       ','       '],[' XXXXX ',' B   B ','  ACA  ','       '],['XXXXXXX','A     A',' B   B ','  DDD  '],['XXXXXXX','A     A',' B   B ','  DDD  '],['XXXXXXX','A     A',' B   B ','  DDD  '],[' XXXXX ',' B   B ','  BBB  ','       '],['  XXX  ','  BEB  ','       ','       ']]), api.portRequirements({}), api.portTierRequirements(towerTiers), [], new LinkedHashMap(), new LinkedHashMap())
towerBuilder.fullStructure(tower([['  XXX  ','  AAA  ','       ','       ','       '],[' XXXXX ',' B   B ','  ACA  ','  ACA  ','       '],['XXXXXXX','A     A',' B   B ',' B   B ','  DDD  '],['XXXXXXX','A     A',' B   B ',' B   B ','  DDD  '],['XXXXXXX','A     A',' B   B ',' B   B ','  DDD  '],[' XXXXX ',' B   B ','  BBB  ','  BBB  ','       '],['  XXX  ','  BEB  ','       ','       ','       ']]), api.portRequirements({}), api.portTierRequirements(towerTiers), [], new LinkedHashMap(), new LinkedHashMap())
towerBuilder.fullStructure(tower([['  XXX  ','  AAA  ','       ','       ','       ','       '],[' XXXXX ',' B   B ','  ACA  ','  ACA  ','  ACA  ','       '],['XXXXXXX','A     A',' B   B ',' B   B ',' B   B ','  DDD  '],['XXXXXXX','A     A',' B   B ',' B   B ',' B   B ','  DDD  '],['XXXXXXX','A     A',' B   B ',' B   B ',' B   B ','  DDD  '],[' XXXXX ',' B   B ','  BBB  ','  BBB  ','  BBB  ','       '],['  XXX  ','  BEB  ','       ','       ','       ','       ']]), api.portRequirements({}), api.portTierRequirements(towerTiers), [], new LinkedHashMap(), new LinkedHashMap()).build()

var ecoA = api.anyOf(api.block('minecraft:resin_bricks'), allPorts())
var ecoBuilder = event.createStructure(cloneId('eco_matrix'))
var ecoWidths = [3, 4, 5]
for (var ecoWidthIndex = 0; ecoWidthIndex < ecoWidths.length; ecoWidthIndex++) {
  var width = ecoWidths[ecoWidthIndex]
  var x = 'X'.repeat(width)
  var a = 'A'.repeat(width)
  var middle = 'A' + ' '.repeat(width - 2) + 'A'
  var controller = 'AB' + 'A'.repeat(width - 2)
  ecoBuilder.fullStructure(pattern([[x, a, x], [x, middle, x], [x, controller, x]], { X: api.block('minecraft:sea_lantern'), A: ecoA, B: controllerBlock('eco_matrix') }, 'B'), api.portRequirements({}), api.portTierRequirements(['energy_input_hatch>=tiny']), [], new LinkedHashMap(), new LinkedHashMap())
}
ecoBuilder.build()

var elevatorD = api.anyOf(api.block('minecraft:smooth_quartz'), itemIn, energyIn)
full('space_elevator', pattern([
  ['        X        ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '],['       XXX       ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '],['      XXXXX      ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '],['     XXAAAXX     ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '],['    XXXAAAXXX    ','        B        ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '],['   XXXXAAAXXXX   ','                 ','                 ','                 ','                 ','        X        ','                 ','                 ','                 ','                 ','                 ','                 '],['  XXXXXXXXXXXXX  ','                 ','                 ','                 ','        X        ','       XXX       ','                 ','                 ','                 ','                 ','                 ','                 '],[' XXAAAXXXXXAAAXX ','       XXX       ','       DDD       ','       XXX       ','       XXX       ','      XXXXX      ','       XXX       ','       X X       ','                 ','                 ','                 ','                 '],['XXXAAAXXXXXAAAXXX','    B  X X  B    ','       D D       ','       X X       ','      XX XX      ','     XXX XXX     ','       XXX       ','        X        ','        X        ','        X        ','        X        ','        X        '],[' XXAAAXXXXXAAAXX ','       XXX       ','       DED       ','       XXX       ','       XXX       ','      XXXXX      ','       XXX       ','       X X       ','                 ','                 ','                 ','                 '],['  XXXXXXXXXXXXX  ','                 ','                 ','                 ','        X        ','       XXX       ','                 ','                 ','                 ','                 ','                 '],['   XXXXXXXXXXX   ','                 ','                 ','                 ','                 ','        X        ','                 ','                 ','                 ','                 ','                 '],['    XXXXXXXXX    ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '],['     XXXXXXX     ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '],['      XXXXX      ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '],['       XXX       ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '],['        X        ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ']
], { X: api.block('minecraft:smooth_quartz'), A: api.block('minecraft:amethyst_block'), B: api.coupler(), D: elevatorD, E: controllerBlock('space_elevator') }, 'E'), ['item_input_bus>=tiny', 'energy_input_hatch>=tiny'])

  var reassemblerB = api.anyOf(api.block('minecraft:gold_block'), itemIn, itemOut, energyIn)
  full('space_reassembler', pattern([['AAA','XBX','XBX','XDX'],['AAA','BEB','B B','DDD'],['AAA','XFX','XBX','XDX']], { X: api.block('minecraft:quartz_pillar'), A: api.block('minecraft:amethyst_block'), B: reassemblerB, D: api.block('minecraft:glass'), E: api.coupler(), F: controllerBlock('space_reassembler') }, 'F'), ['item_input_bus>=tiny', 'item_output_bus>=tiny', 'energy_input_hatch>=tiny'])
})

ServerEvents.recipes(function(event) {
  var api = mmcrAPI
  var NS = 'mmcr_kubejs'
  function cloneId(path) { return NS + ':kubejs_' + path }
  function json(value) {
    return JsonIO.parseRaw(JSON.stringify(value))
  }
  function recipe(path, machine, ticks, inputs, outputs, options) {
    if (outputs === undefined) outputs = []
    if (options === undefined) options = {}
    var maxThreads = options.maxThreads === undefined ? 1 : options.maxThreads
    var priority = options.priority === undefined ? 0 : options.priority
    var cancel = options.cancel === undefined ? true : options.cancel
    var partial = options.partial === undefined ? false : options.partial
    var requirements = []
    var recipeJson = {
      type: 'mmcr:machine_recipe',
      machine: cloneId(machine),
      tick_time: ticks,
      requirements: requirements,
      max_threads: maxThreads,
      priority: priority,
      cancelIfPerTickFails: cancel,
      allow_partial_outputs: partial,
      parallelized: options.parallelized !== false
    }
    if (options.energy) inputs.push({ type: 'energy', io: 'input', fe_per_tick: options.energy })
    var deferredFluidOutputs = []
    if (options.fluids) for (var fluidOutputIndex = 0; fluidOutputIndex < options.fluids.length; fluidOutputIndex++) deferredFluidOutputs.push(options.fluids[fluidOutputIndex])
    if (options.level) recipeJson.level_requirements = [{ type: options.level[0], level: options.level[1] }]
    if (options.hosts) {
      var hosts = []
      for (var hostIndex = 0; hostIndex < options.hosts.length; hostIndex++) hosts.push(cloneId(options.hosts[hostIndex]))
      recipeJson.required_host_ids = hosts
    }
    if (options.deriveRequirements !== false) {
      for (var inputIndex = 0; inputIndex < inputs.length; inputIndex++) requirements.push(inputToRequirement(inputs[inputIndex]))
    }
    if (options.smart) for (var smartInputIndex = 0; smartInputIndex < options.smart.length; smartInputIndex++) {
      var smartInput = options.smart[smartInputIndex]
      requirements.push({ type: 'smart_interface', io: 'input', interface_type: smartInput[0], min_value: smartInput[1], max_value: smartInput[1] })
    }
    if (options.componentInputs) for (var componentInputIndex = 0; componentInputIndex < options.componentInputs.length; componentInputIndex++) {
      var componentInput = options.componentInputs[componentInputIndex]
      var consumeChance = componentInput.length > 3 ? componentInput[3] : 1
      requirements.push({ type: 'item', io: 'input', item: componentInput[0], count: componentInput[1], components: componentInput[2], consume_chance: consumeChance })
    }
    if (options.requirements) for (var requirementIndex = 0; requirementIndex < options.requirements.length; requirementIndex++) requirements.push(options.requirements[requirementIndex])
    for (var outputIndex = 0; outputIndex < outputs.length; outputIndex++) {
      var output = outputs[outputIndex]
      var outputRequirement = { type: 'item', io: 'output', stack: { id: output.id, count: output.count } }
      if (output.components) outputRequirement.stack.components = output.components
      if (output.chance !== undefined) outputRequirement.chance = output.chance
      requirements.push(outputRequirement)
    }
    for (var deferredFluidOutputIndex = 0; deferredFluidOutputIndex < deferredFluidOutputs.length; deferredFluidOutputIndex++) {
      var deferredFluidOutput = deferredFluidOutputs[deferredFluidOutputIndex]
      requirements.push({ type: 'fluid', io: 'output', stack: { id: deferredFluidOutput[0], amount: deferredFluidOutput[1] } })
    }
    event.custom(recipeJson).id(cloneId(path))
  }
  function inputToRequirement(input) {
    if (input.type === 'item') {
      var itemRequirement = { type: 'item', io: 'input', item: input.item, count: input.count }
      if (input.consume_chance !== undefined) itemRequirement.consume_chance = input.consume_chance
      if (input.components !== undefined) itemRequirement.components = input.components
      return itemRequirement
    }
    if (input.type === 'fluid') return { type: 'fluid', io: 'input', fluid: input.fluid, amount: input.amount }
    if (input.type === 'energy') return { type: 'energy', io: input.io === undefined ? 'input' : input.io, fe_per_tick: input.fe_per_tick }
    throw new Error('Unknown MMCR input type: ' + input.type)
  }
  function item(id, count) { return { type: 'item', item: id, count: count === undefined ? 1 : count } }
  function chance(id, count, value) { return { type: 'item', item: id, count: count, consume_chance: value } }
  function fluid(id, amount) { return { type: 'fluid', fluid: id, amount: amount } }
  function energyOutput(value) { return { type: 'energy', io: 'output', fe_per_tick: value } }
  function standard(machine, first) {
    recipe(first[0], machine, first[1], first[2], first[3], first[4])
    recipe(machine + '_copper_to_nugget', machine, 200, [item('minecraft:copper_ingot')], [{ id: 'minecraft:copper_nugget', count: 1 }], { energy: 2 })
    recipe(machine + '_gold_to_nugget', machine, 200, [item('minecraft:gold_ingot')], [{ id: 'minecraft:gold_nugget', count: 1 }], { energy: 3 })
    recipe(machine + '_multi_item', machine, 200, [item('minecraft:iron_ingot'), item('minecraft:gold_ingot'), item('minecraft:copper_ingot')], [{ id: 'minecraft:diamond', count: 1 }], { energy: 4 })
    recipe(machine + '_multi_output', machine, 200, [item('minecraft:iron_ingot')], [{ id: 'minecraft:iron_nugget', count: 1 }, { id: 'minecraft:gold_nugget', count: 1 }, { id: 'minecraft:copper_nugget', count: 1 }], { energy: 5 })
    recipe(machine + '_water_input', machine, 200, [fluid('minecraft:water', 250)], [{ id: 'minecraft:clay_ball', count: 1 }], { energy: 6 })
    recipe(machine + '_lava_output', machine, 200, [item('minecraft:coal')], [{ id: 'minecraft:redstone', count: 1 }], { energy: 7, fluids: [['minecraft:lava', 250]] })
    recipe(machine + '_water_to_lava', machine, 200, [fluid('minecraft:water', 500)], [{ id: 'minecraft:coal', count: 1 }], { energy: 8, fluids: [['minecraft:lava', 500]] })
    recipe(machine + '_mixed_input', machine, 200, [fluid('minecraft:water', 250), item('minecraft:iron_ingot'), item('minecraft:gold_ingot')], [{ id: 'minecraft:emerald', count: 1 }], { energy: 9 })
    recipe(machine + '_mixed_output', machine, 200, [fluid('minecraft:water', 250), item('minecraft:diamond'), energyOutput(100)], [{ id: 'minecraft:iron_nugget', count: 1 }, { id: 'minecraft:gold_nugget', count: 1 }], { fluids: [['minecraft:lava', 125]] })
  }
  standard('blast_furnace', ['blast_furnace_iron_to_nugget', 200, [item('minecraft:iron_ingot')], [{ id: 'minecraft:iron_nugget', count: 1 }], { energy: 1 }])
  standard('alloy_furnace', ['alloy_furnace_netherite', 100, [item('minecraft:ancient_debris'), item('minecraft:gold_ingot')], [{ id: 'minecraft:netherite_ingot', count: 1 }], { energy: 5 }])
  standard('cracker', ['cracker_coal_lapis', 160, [item('minecraft:coal', 8), item('minecraft:lapis_lazuli')], [{ id: 'minecraft:redstone', count: 4 }], { energy: 100, fluids: [['minecraft:water', 500]] }])
  standard('reactor', ['reactor_diamond_water', 200, [item('minecraft:diamond'), fluid('minecraft:water', 500), energyOutput(100)], [{ id: 'minecraft:coal', count: 1 }], { fluids: [['minecraft:lava', 500]] }])
  function itemInputs(ids) {
    var values = []
    for (var idIndex = 0; idIndex < ids.length; idIndex++) values.push(item('minecraft:' + ids[idIndex]))
    return values
  }
  function itemOutputs(ids) {
    var values = []
    for (var idIndex = 0; idIndex < ids.length; idIndex++) values.push({ id: 'minecraft:' + ids[idIndex], count: 1 })
    return values
  }
  recipe('alloy_furnace_jei_large', 'alloy_furnace', 400, itemInputs(['iron_ingot','gold_ingot','copper_ingot','redstone','lapis_lazuli','coal','diamond','emerald','quartz','amethyst_shard','netherite_scrap','iron_nugget','gold_nugget','copper_block','iron_block','gold_block','redstone_block','lapis_block','diamond_block','emerald_block','quartz_block']), itemOutputs(['iron_nugget','gold_nugget','copper_nugget','redstone','lapis_lazuli','coal','diamond','emerald','quartz','amethyst_shard','netherite_scrap','iron_ingot','gold_ingot','copper_ingot','iron_block','gold_block','copper_block','redstone_block','lapis_block','diamond_block','emerald_block']))
  recipe('alloy_furnace_jei_25x25', 'alloy_furnace', 500, itemInputs(['iron_ingot','gold_ingot','copper_ingot','redstone','lapis_lazuli','coal','diamond','emerald','quartz','amethyst_shard','netherite_scrap','iron_nugget','gold_nugget','copper_block','iron_block','gold_block','redstone_block','lapis_block','diamond_block','emerald_block','quartz_block','coal_block','raw_iron','raw_gold','raw_copper']), itemOutputs(['raw_copper','raw_gold','raw_iron','coal_block','quartz_block','emerald_block','diamond_block','lapis_block','redstone_block','gold_block','iron_block','copper_block','gold_nugget','iron_nugget','netherite_scrap','amethyst_shard','quartz','emerald','diamond','coal','lapis_lazuli','redstone','copper_ingot','gold_ingot','iron_ingot']))
  recipe('thermal_smelting_furnace_coal_iron_to_netherite_scrap', 'thermal_smelting_furnace', 80, [item('minecraft:coal'), item('minecraft:raw_iron')], [{ id: 'minecraft:iron_ingot', count: 1 }], { energy: 200, maxThreads: 4 })
  var thermalRecipes = [['copper','thermal_smelting_coil_copper',120,'raw_copper','copper_ingot',400],['iron','thermal_smelting_coil_iron',160,'iron_ingot','gold_ingot',800],['gold','thermal_smelting_coil_gold',200,'gold_ingot','diamond',1200],['diamond','thermal_smelting_coil_diamond',240,'diamond','netherite_ingot',2000]]
  for (var thermalRecipeIndex = 0; thermalRecipeIndex < thermalRecipes.length; thermalRecipeIndex++) {
    var thermalRecipe = thermalRecipes[thermalRecipeIndex]
    var name = thermalRecipe[0]
    var level = thermalRecipe[1]
    var ticks = thermalRecipe[2]
    var input = thermalRecipe[3]
    var output = thermalRecipe[4]
    var energy = thermalRecipe[5]
    recipe('thermal_smelting_furnace_' + name, 'thermal_smelting_furnace', ticks, [item('minecraft:coal'), item('minecraft:' + input)], [{ id: 'minecraft:' + output, count: 1 }], { energy: energy, maxThreads: 4, level: ['mmcr:thermal_smelting_coil', 'mmcr:' + level] })
  }
  var componentMachines = ['blast_furnace','alloy_furnace','cracker','reactor','thermal_smelting_furnace']
  for (var componentMachineIndex = 0; componentMachineIndex < componentMachines.length; componentMachineIndex++) {
    var machine = componentMachines[componentMachineIndex]
    var prefix = machine + '_component_'
    recipe(prefix + 'chanced_input', machine, 20, [], [{ id: 'minecraft:emerald', count: 1 }], { componentInputs: [['minecraft:diamond', 1, { 'minecraft:custom_name': { text: 'Chance' } }, 0.5]] })
    recipe(prefix + 'non_consumable_input', machine, 20, [], [{ id: 'minecraft:emerald', count: 1 }], { componentInputs: [['minecraft:diamond', 1, { 'minecraft:custom_name': { text: 'Keep' } }, 0]] })
    recipe(prefix + 'non_consumable_sharpness_input', machine, 100, [], [], { componentInputs: [['minecraft:diamond_sword', 1, { 'minecraft:enchantments': { 'minecraft:sharpness': 2 } }, 0]] })
    recipe(prefix + 'enchanted_output', machine, 100, [item('minecraft:iron_sword')], [], { requirements: [{ type: 'item', io: 'output', stack: { id: 'minecraft:iron_sword', count: 1, components: { 'minecraft:enchantments': { 'minecraft:sharpness': 2 }, 'minecraft:repair_cost': 1 } } }] })
    recipe(prefix + 'input_to_plain_output', machine, 20, [], [{ id: 'minecraft:emerald', count: 1 }], { componentInputs: [['minecraft:diamond', 1, { 'minecraft:custom_name': { text: 'Input Only' } }]] })
    recipe(prefix + 'plain_input_to_output', machine, 20, [item('minecraft:iron_ingot')], [{ id: 'minecraft:gold_ingot', count: 1, components: { 'minecraft:custom_name': { text: 'Output Only' } } }])
    recipe(prefix + 'input_to_output', machine, 20, [], [{ id: 'minecraft:gold_ingot', count: 1, components: { 'minecraft:custom_name': { text: 'Output' } } }], { componentInputs: [['minecraft:diamond', 1, { 'minecraft:custom_name': { text: 'Input' } }]] })
    recipe(prefix + 'mixed_inputs', machine, 20, [item('minecraft:iron_ingot')], [{ id: 'minecraft:emerald', count: 1 }], { componentInputs: [['minecraft:diamond', 1, { 'minecraft:custom_name': { text: 'Named' } }]] })
    recipe(prefix + 'mixed_outputs', machine, 20, [item('minecraft:iron_ingot')], [{ id: 'minecraft:gold_ingot', count: 1, components: { 'minecraft:custom_name': { text: 'Named Output' } } }, { id: 'minecraft:emerald', count: 1 }])
    recipe(prefix + 'chanced_outputs', machine, 20, [item('minecraft:iron_ingot')], [], { requirements: [
      { type: 'item', io: 'input', item: 'minecraft:apple', count: 1 },
      { type: 'item', io: 'output', stack: { id: 'minecraft:emerald', count: 1 } },
      { type: 'item', io: 'output', stack: { id: 'minecraft:diamond', count: 1 }, chance: 0.5 },
      { type: 'fluid', io: 'output', stack: { id: 'minecraft:lava', amount: 250 }, chance: 0.25 }
    ], deriveRequirements: false })
    recipe(prefix + 'complex', machine, 20, [item('minecraft:stick'), chance('minecraft:iron_nugget', 1, 0.5), chance('minecraft:gold_nugget', 1, 0.25)], [{ id: 'minecraft:emerald', count: 1 }, { id: 'minecraft:diamond', count: 1, chance: 0.5 }, { id: 'minecraft:redstone', count: 1, chance: 0.25 }])
  }
  recipe('blast_furnace_component_tag_input', 'blast_furnace', 20, [{ type: 'item', item: '#minecraft:logs', count: 1 }], [{ id: 'minecraft:charcoal', count: 1 }])
  recipe('blast_furnace_component_tag_named_input', 'blast_furnace', 20, [], [{ id: 'minecraft:emerald', count: 1 }], { componentInputs: [['#minecraft:planks', 1, { 'minecraft:custom_name': { text: 'Validated' } }, 1]] })
  recipe('blast_furnace_component_tag_enchanted_input', 'blast_furnace', 20, [], [{ id: 'minecraft:diamond', count: 1 }], { componentInputs: [['#minecraft:swords', 1, { 'minecraft:enchantments': { 'minecraft:sharpness': 2 } }, 1]] })
  var purpurRecipes = [['mode_1',200,5,'diamond',2,[['Mode',1]]],['mode_2',200,5,'gold_ingot',4,[['Mode',2]]],['mode_3',200,5,'iron_ingot',8,[['Mode',3]]],['temperature_400',320,3,'apple',8,[['Temperature',400]]],['temperature_1600',240,6,'baked_potato',6,[['Temperature',1600]]],['temperature_3200',160,9,'brick',4,[['Temperature',3200]]],['temperature_6800',60,14,'charcoal',2,[['Temperature',6800]]],['conversion_0',200,2,'stick',1,[['ConversionRate',0]]],['conversion_50',200,6,'bone_meal',4,[['ConversionRate',0.5]]],['conversion_100',200,12,'glowstone_dust',8,[['ConversionRate',1]]],['mode_temperature',120,10,'popped_chorus_fruit',3,[['Mode',2],['Temperature',3200]]],['mode_conversion',200,9,'string',6,[['Mode',3],['ConversionRate',0.75]]],['temperature_conversion',90,15,'clay_ball',5,[['Temperature',5200],['ConversionRate',0.8]]],['mode_temperature_conversion',80,18,'ender_pearl',4,[['Mode',1],['Temperature',5200],['ConversionRate',1]]]]
  for (var purpurRecipeIndex = 0; purpurRecipeIndex < purpurRecipes.length; purpurRecipeIndex++) {
    var purpurRecipe = purpurRecipes[purpurRecipeIndex]
    var name = purpurRecipe[0]
    var ticks = purpurRecipe[1]
    var energy = purpurRecipe[2]
    var output = purpurRecipe[3]
    var count = purpurRecipe[4]
    var smart = purpurRecipe[5]
    recipe('purpur_furnace_' + name, 'purpur_furnace', ticks, [item('minecraft:coal')], [{ id: 'minecraft:' + output, count: count }], { energy: energy, smart: smart })
  }
  var towerRecipes = [['coal','coal','coal','charcoal','gunpowder'],['oak_log','oak_log','charcoal','stick','coal'],['dried_kelp','dried_kelp','kelp','coal','bone_meal']]
  for (var towerRecipeIndex = 0; towerRecipeIndex < towerRecipes.length; towerRecipeIndex++) {
    var towerRecipe = towerRecipes[towerRecipeIndex]
    var name = towerRecipe[0]
    var input = towerRecipe[1]
    var first = towerRecipe[2]
    var second = towerRecipe[3]
    var third = towerRecipe[4]
    recipe('distillation_tower_' + name, 'distillation_tower', 200, [item('minecraft:' + input)], [{ id: 'minecraft:' + first, count: 1 }, { id: 'minecraft:' + second, count: 1 }, { id: 'minecraft:' + third, count: 1 }], { energy: 40, maxThreads: 4, partial: true })
  }
  recipe('eco_matrix_energy_drain', 'eco_matrix', 200, [], [], { energy: 100, cancel: true })
  recipe('space_elevator_thread_dispersal', 'space_elevator', 1000, [chance('mmcr:thread_disperser', 1, 0)], [], { energy: 10000, cancel: true, partial: false })
  recipe('space_reassembler_steak_to_golden_carrot', 'space_reassembler', 600, [item('minecraft:cooked_beef', 4)], [{ id: 'minecraft:golden_carrot', count: 1 }], { energy: 15000, partial: false, hosts: ['space_elevator'] })
  recipe('space_reassembler_water_to_healing', 'space_reassembler', 400, [], [{ id: 'minecraft:potion', count: 1, components: { 'minecraft:potion_contents': { potion: 'minecraft:healing' } } }], { energy: 8000, partial: false, hosts: ['space_elevator'], componentInputs: [['minecraft:potion', 1, { 'minecraft:potion_contents': { potion: 'minecraft:water' } }]] })
  recipe('space_reassembler_water_to_swiftness', 'space_reassembler', 400, [], [{ id: 'minecraft:potion', count: 1, components: { 'minecraft:potion_contents': { potion: 'minecraft:swiftness' } } }], { energy: 8000, partial: false, hosts: ['space_elevator'], componentInputs: [['minecraft:potion', 1, { 'minecraft:potion_contents': { potion: 'minecraft:awkward' } }]] })
})
