// KubeJS recreation of the development defaults. It deliberately does not read mmcr:* definitions.
const NS = 'mmcr_kubejs'
const cloneId = path => `${NS}:kubejs_${path}`

const ports = (io, category) => {
  const tiers = category === 'fluid'
    ? ['tiny', 'small', 'normal', 'reinforced', 'big', 'huge', 'ludicrous', 'vacuum']
    : category === 'energy'
    ? ['tiny', 'small', 'normal', 'reinforced', 'big', 'huge', 'ludicrous', 'ultimate']
    : ['tiny', 'small', 'normal', 'reinforced', 'big', 'huge', 'ludicrous']
  const family = category === 'item' ? 'bus' : 'hatch'
  return MMCR_API.anyOf(...tiers.map(tier => MMCR_API.block(`mmcr:${category}_${io}_${family}${tier === 'normal' ? '' : `_${tier}`}`)))
}
const allPorts = () => MMCR_API.anyOf(
  ports('input', 'item'), ports('output', 'item'), ports('input', 'fluid'),
  ports('output', 'fluid'), ports('input', 'energy'), ports('output', 'energy'))
const allPortsExceptTinyOutput = () => MMCR_API.anyOf(
  ports('input', 'item'),
  MMCR_API.anyOf(...['small', 'normal', 'reinforced', 'big', 'huge', 'ludicrous'].map(tier => MMCR_API.block(`mmcr:item_output_bus_${tier}`))),
  ports('input', 'fluid'), ports('output', 'fluid'), ports('input', 'energy'), ports('output', 'energy'))
const parallelSlots = base => MMCR_API.anyOf(base, allPorts(), MMCR_API.block('mmcr:parallel_controller_2x'), MMCR_API.block('mmcr:parallel_controller_4x'), MMCR_API.block('mmcr:parallel_controller_8x'), MMCR_API.block('mmcr:parallel_controller_16x'))
const pattern = (slices, keys, controller) => {
  const entries = new java.util.LinkedHashMap()
  const height = slices[0].length
  const width = slices[0][0].length
  let controllerPos = null
  for (let z = 0; z < slices.length; z++) for (let row = 0; row < height; row++) for (let column = 0; column < width; column++) {
    const symbol = slices[z][row][column]
    const predicate = keys[symbol]
    if (predicate === undefined) continue
    const pos = MMCR_API.pos(column - Math.floor(width / 2), row - Math.floor(height / 2), z - Math.floor(slices.length / 2))
    entries.put(pos, predicate)
    if (symbol === controller) controllerPos = pos
  }
  if (controllerPos === null || (controllerPos.x === 0 && controllerPos.y === 0 && controllerPos.z === 0)) return MMCR_API.blockArray(entries)
  const normalized = new java.util.LinkedHashMap()
  entries.forEach((pos, predicate) => normalized.put(MMCR_API.pos(pos.x - controllerPos.x, pos.y - controllerPos.y, pos.z - controllerPos.z), predicate))
  return MMCR_API.blockArray(normalized)
}
const full = (path, array, tiers = []) => new MMCR_STRUCTURE_BUILDER(cloneId(path))
  .fullStructure(array, MMCR_API.portRequirements({}), MMCR_API.portTierRequirements(tiers), [], new java.util.LinkedHashMap(), new java.util.LinkedHashMap()).build()

const itemIn = ports('input', 'item')
const itemOut = ports('output', 'item')
const fluidIn = ports('input', 'fluid')
const fluidOut = ports('output', 'fluid')
const energyIn = ports('input', 'energy')
const energyOut = ports('output', 'energy')
const casing = MMCR_API.block('mmcr:basic_casing')
const factory = MMCR_API.block('mmcr:factory_controller')
const smart = MMCR_API.block('mmcr:smart_interface')

const io = allPorts()
full('blast_furnace', pattern([
  ['AXA', 'XIX', 'XXX'], ['XXX', 'I I', 'XBX'], ['AXA', 'XCX', 'XXX']
], {
  X: MMCR_API.anyOf(casing, io), A: parallelSlots(casing), B: MMCR_API.anyOf(casing, io, factory),
  C: MMCR_API.block(`${NS}:kubejs_blast_furnace_controller`), I: MMCR_API.anyOf(itemIn, itemOut, fluidIn, fluidOut, energyIn, energyOut)
}, 'C'), ['energy_input_hatch>=ludicrous', 'item_input_bus>=normal', 'item_output_bus>=tiny'])

const alloyModifiers = new java.util.LinkedHashMap()
for (const pos of [MMCR_API.pos(0, -1, -1), MMCR_API.pos(0, 1, -1)]) {
  alloyModifiers.put(pos, [
    MMCR_API.singleBlockModifier('alloy_furnace_diamond_speedup', pos, MMCR_API.block('minecraft:diamond_block'), [MMCR_API.modifier('duration', 'input', 0.5, 'multiply', false)], '钻石块：配方时间折半', Item.of('minecraft:diamond_block')),
    MMCR_API.singleBlockModifier('alloy_furnace_gold_doubling', pos, MMCR_API.block('minecraft:gold_block'), [MMCR_API.modifier('item', 'output', 2, 'multiply', false)], '金块：产物数量翻倍', Item.of('minecraft:gold_block'))
  ])
}
new MMCR_STRUCTURE_BUILDER(cloneId('alloy_furnace')).fullStructure(pattern([
  ['XXX', 'XIX', 'XXX'], ['XMX', 'I I', 'XMX'], ['XXX', 'XCX', 'XXX']
], { X: MMCR_API.block('minecraft:bricks'), I: MMCR_API.anyOf(itemIn, itemOut, energyIn), M: MMCR_API.block('minecraft:blast_furnace'), C: MMCR_API.block(`${NS}:kubejs_alloy_furnace_controller`) }, 'C'), MMCR_API.portRequirements({}), MMCR_API.portTierRequirements([]), [], alloyModifiers, new java.util.LinkedHashMap()).build()

full('cracker', pattern([
  ['AAA', 'AAA', 'AAA'], ['XBX', 'B B', 'XBX'], ['XDX', 'D D', 'XDX'], ['XEX', 'ECE', 'XEX']
], { X: MMCR_API.block('minecraft:polished_diorite'), A: MMCR_API.block('minecraft:polished_andesite'), B: MMCR_API.anyOf(itemIn, itemOut, fluidOut, energyIn, MMCR_API.block('minecraft:weathered_copper')), D: MMCR_API.block('minecraft:blue_ice'), E: MMCR_API.block('minecraft:weathered_copper'), C: MMCR_API.block(`${NS}:kubejs_cracker_controller`) }, 'C'), ['fluid_output_hatch>=huge', 'energy_input_hatch>=reinforced', 'item_input_bus>=normal', 'item_output_bus>=tiny'])

const reactorPort = MMCR_API.anyOf(MMCR_API.block('minecraft:blue_ice'), itemIn, itemOut, fluidIn, fluidOut, energyOut)
full('reactor', pattern([
  ['  AAAAA  ','         ','         ','         ','         ','         ','         ','         '], [' AAXXXAA ','   DDD   ','         ','         ','         ','         ','         ','         '], ['AAXXXXXAA','  EFFFE  ','  EFFFE  ','  EFFFE  ','  JJJJJ  ','         ','         ','         '], ['AXXXXXXXA',' DFGHGFD ','  FGHGF  ','  FGHGF  ','  JXXXJ  ','   KKK   ','         ','         '], ['AXXXXXXXA',' DFHXHFD ','  FHXHF  ','  FHXHF  ','  JXXXJ  ','   KLK   ','    L    ','    M    '], ['AXXXXXXXA',' DFGHGFD ','  FGHGF  ','  FGHGF  ','  JXXXJ  ','   KKK   ','         ','         '], ['AAXXXXXAA','  EFFFE  ','  EFFFE  ','  EFFFE  ','  JJJJJ  ','         ','         ','         '], [' AAXXXAA ','   DID   ','         ','         ','         ','         ','         ','         '], ['  AAAAA  ','         ','         ','         ','         ','         ','         ']
], { X: MMCR_API.block('minecraft:blue_ice'), A: MMCR_API.block('minecraft:deepslate_brick_stairs'), D: reactorPort, E: MMCR_API.block('minecraft:polished_deepslate'), F: MMCR_API.block('minecraft:black_stained_glass'), G: MMCR_API.block('oritech:uranium'), H: MMCR_API.block('oritech:energite'), I: MMCR_API.block(`${NS}:kubejs_reactor_controller`), J: MMCR_API.block('minecraft:polished_deepslate_stairs'), K: MMCR_API.block('minecraft:deepslate_brick_slab'), L: MMCR_API.block('minecraft:deepslate_tiles'), M: MMCR_API.block('minecraft:oxidized_lightning_rod') }, 'I'))

const thermalA = MMCR_API.anyOf(MMCR_API.block('minecraft:smooth_basalt'), itemIn, itemOut, energyIn, factory, MMCR_API.block('mmcr:parallel_controller_2x'), MMCR_API.block('mmcr:parallel_controller_4x'), MMCR_API.block('mmcr:parallel_controller_8x'), MMCR_API.block('mmcr:parallel_controller_16x'))
const thermalPattern = pattern([['AAA','XXX','XXX','AAA'], ['AAA','X X','X X','ADA'], ['ABA','XXX','XXX','AAA']], { X: MMCR_API.anyOf(MMCR_API.block('minecraft:copper_block'), MMCR_API.block('minecraft:iron_block'), MMCR_API.block('minecraft:gold_block'), MMCR_API.block('minecraft:diamond_block')), A: thermalA, B: MMCR_API.block(`${NS}:kubejs_thermal_smelting_furnace_controller`), D: MMCR_API.block('minecraft:reinforced_deepslate') }, 'B')
const thermalLevels = new java.util.LinkedHashMap()
const thermalSlices = [['AAA','XXX','XXX','AAA'], ['AAA','X X','X X','ADA'], ['ABA','XXX','XXX','AAA']]
// Normalize each coil position relative to B at slice 2, row 2, column 1, as BlockArray.Builder does.
for (let z = 0; z < thermalSlices.length; z++) for (let row = 0; row < 4; row++) for (let column = 0; column < 3; column++) {
  if (thermalSlices[z][row][column] === 'X') thermalLevels.put(MMCR_API.pos(column - 1, row - 2, z - 2), MMCR_API.id('mmcr:thermal_smelting_coil'))
}
new MMCR_STRUCTURE_BUILDER(cloneId('thermal_smelting_furnace')).fullStructure(thermalPattern, MMCR_API.portRequirements({}), MMCR_API.portTierRequirements(['item_input_bus>=tiny', 'item_output_bus>=tiny', 'energy_input_hatch>=tiny']), [], new java.util.LinkedHashMap(), thermalLevels).build()

const purpurB = MMCR_API.anyOf(MMCR_API.block('minecraft:purpur_pillar'), itemIn, itemOut, energyIn, factory, smart, MMCR_API.block('mmcr:parallel_controller_2x'), MMCR_API.block('mmcr:parallel_controller_4x'), MMCR_API.block('mmcr:parallel_controller_8x'), MMCR_API.block('mmcr:parallel_controller_16x'))
full('purpur_furnace', pattern([
  [' AAAAA ','       ','       ','       ','  GGG  ','       ','       ','       '], ['AAXXXAA','  BBB  ','  EEE  ','  FFF  ',' GBBBG ',' HHHHH ','       ','       '], ['AXXXXXA',' B   B ',' E   E ',' F   F ','GB   BG',' HXXXH ','  GBG  ','   I   '], ['AXXXXXA',' B   B ',' E   E ',' F   F ','GB   BG',' HX XH ','  B B  ','  I I  '], ['AXXXXXA',' B   B ',' E   E ',' F   F ','GB   BG',' HXXXH ','  GBG  ','   I   '], ['AAXXXAA','  BDB  ','  EEE  ','  FFF  ',' GBBBG ',' HHHHH ','       ','       '], [' AAAAA ','       ','       ','       ','  GGG  ','       ','       ','       ']
], { X: MMCR_API.block('minecraft:end_stone_bricks'), A: MMCR_API.block('minecraft:end_stone_brick_stairs'), B: purpurB, D: MMCR_API.block(`${NS}:kubejs_purpur_furnace_controller`), E: MMCR_API.block('minecraft:purple_terracotta'), F: MMCR_API.block('minecraft:purpur_block'), G: MMCR_API.block('minecraft:end_stone_brick_slab'), H: MMCR_API.block('minecraft:purpur_stairs'), I: MMCR_API.block('minecraft:purpur_slab') }, 'D'), ['item_input_bus>=tiny', 'item_output_bus>=tiny', 'energy_input_hatch>=tiny'])

const towerA = MMCR_API.anyOf(MMCR_API.block('minecraft:deepslate_bricks'), allPortsExceptTinyOutput())
const towerC = MMCR_API.anyOf(MMCR_API.block('minecraft:deepslate_bricks'), MMCR_API.block('mmcr:item_output_bus_tiny'))
const tower = rows => pattern(rows, { X: MMCR_API.block('minecraft:polished_blackstone'), A: towerA, B: MMCR_API.block('minecraft:polished_blackstone_bricks'), C: towerC, D: MMCR_API.block('minecraft:gilded_blackstone'), E: MMCR_API.block(`${NS}:kubejs_distillation_tower_controller`) }, 'E')
const towerTiers = ['item_input_bus>=tiny', 'item_output_bus>=tiny', 'energy_input_hatch>=tiny']
const towerBuilder = new MMCR_STRUCTURE_BUILDER(cloneId('distillation_tower'))
towerBuilder.fullStructure(tower([['  XXX  ','  AAA  ','       ','       '],[' XXXXX ',' B   B ','  ACA  ','       '],['XXXXXXX','A     A',' B   B ','  DDD  '],['XXXXXXX','A     A',' B   B ','  DDD  '],['XXXXXXX','A     A',' B   B ','  DDD  '],[' XXXXX ',' B   B ','  BBB  ','       '],['  XXX  ','  BEB  ','       ','       ']]), MMCR_API.portRequirements({}), MMCR_API.portTierRequirements(towerTiers), [], new java.util.LinkedHashMap(), new java.util.LinkedHashMap())
towerBuilder.fullStructure(tower([['  XXX  ','  AAA  ','       ','       ','       '],[' XXXXX ',' B   B ','  ACA  ','  ACA  ','       '],['XXXXXXX','A     A',' B   B ',' B   B ','  DDD  '],['XXXXXXX','A     A',' B   B ',' B   B ','  DDD  '],['XXXXXXX','A     A',' B   B ',' B   B ','  DDD  '],[' XXXXX ',' B   B ','  BBB  ','  BBB  ','       '],['  XXX  ','  BEB  ','       ','       ','       ']]), MMCR_API.portRequirements({}), MMCR_API.portTierRequirements(towerTiers), [], new java.util.LinkedHashMap(), new java.util.LinkedHashMap())
towerBuilder.fullStructure(tower([['  XXX  ','  AAA  ','       ','       ','       ','       '],[' XXXXX ',' B   B ','  ACA  ','  ACA  ','  ACA  ','       '],['XXXXXXX','A     A',' B   B ',' B   B ',' B   B ','  DDD  '],['XXXXXXX','A     A',' B   B ',' B   B ',' B   B ','  DDD  '],['XXXXXXX','A     A',' B   B ',' B   B ',' B   B ','  DDD  '],[' XXXXX ',' B   B ','  BBB  ','  BBB  ','  BBB  ','       '],['  XXX  ','  BEB  ','       ','       ','       ','       ']]), MMCR_API.portRequirements({}), MMCR_API.portTierRequirements(towerTiers), [], new java.util.LinkedHashMap(), new java.util.LinkedHashMap()).build()

const ecoA = MMCR_API.anyOf(MMCR_API.block('minecraft:resin_bricks'), allPorts())
const ecoBuilder = new MMCR_STRUCTURE_BUILDER(cloneId('eco_matrix'))
for (const width of [3, 4, 5]) {
  const x = 'X'.repeat(width), a = 'A'.repeat(width), middle = `A${' '.repeat(width - 2)}A`, controller = `AB${'A'.repeat(width - 2)}`
  ecoBuilder.fullStructure(pattern([[x, a, x], [x, middle, x], [x, controller, x]], { X: MMCR_API.block('minecraft:sea_lantern'), A: ecoA, B: MMCR_API.block(`${NS}:kubejs_eco_matrix_controller`) }, 'B'), MMCR_API.portRequirements({}), MMCR_API.portTierRequirements(['energy_input_hatch>=tiny']), [], new java.util.LinkedHashMap(), new java.util.LinkedHashMap())
}
ecoBuilder.build()

const elevatorD = MMCR_API.anyOf(MMCR_API.block('minecraft:smooth_quartz'), itemIn, energyIn)
full('space_elevator', pattern([
  ['        X        ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '],['       XXX       ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '],['      XXXXX      ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '],['     XXAAAXX     ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '],['    XXXAAAXXX    ','        B        ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '],['   XXXXAAAXXXX   ','                 ','                 ','                 ','                 ','        X        ','                 ','                 ','                 ','                 ','                 ','                 '],['  XXXXXXXXXXXXX  ','                 ','                 ','                 ','        X        ','       XXX       ','                 ','                 ','                 ','                 ','                 ','                 '],[' XXAAAXXXXXAAAXX ','       XXX       ','       DDD       ','       XXX       ','       XXX       ','      XXXXX      ','       XXX       ','       X X       ','                 ','                 ','                 ','                 '],['XXXAAAXXXXXAAAXXX','    B  X X  B    ','       D D       ','       X X       ','      XX XX      ','     XXX XXX     ','       XXX       ','        X        ','        X        ','        X        ','        X        ','        X        '],[' XXAAAXXXXXAAAXX ','       XXX       ','       DED       ','       XXX       ','       XXX       ','      XXXXX      ','       XXX       ','       X X       ','                 ','                 ','                 ','                 '],['  XXXXXXXXXXXXX  ','                 ','                 ','                 ','        X        ','       XXX       ','                 ','                 ','                 ','                 ','                 '],['   XXXXXXXXXXX   ','                 ','                 ','                 ','                 ','        X        ','                 ','                 ','                 ','                 ','                 '],['    XXXXXXXXX    ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '],['     XXXXXXX     ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '],['      XXXXX      ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '],['       XXX       ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 '],['        X        ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ','                 ']
], { X: MMCR_API.block('minecraft:smooth_quartz'), A: MMCR_API.block('minecraft:amethyst_block'), B: MMCR_API.coupler(), D: elevatorD, E: MMCR_API.block(`${NS}:kubejs_space_elevator_controller`) }, 'E'), ['item_input_bus>=tiny', 'energy_input_hatch>=tiny'])

const reassemblerB = MMCR_API.anyOf(MMCR_API.block('minecraft:gold_block'), itemIn, itemOut, energyIn)
full('space_reassembler', pattern([['AAA','XBX','XBX','XDX'],['AAA','BEB','B B','DDD'],['AAA','XFX','XBX','XDX']], { X: MMCR_API.block('minecraft:quartz_pillar'), A: MMCR_API.block('minecraft:amethyst_block'), B: reassemblerB, D: MMCR_API.block('minecraft:glass'), E: MMCR_API.coupler(), F: MMCR_API.block(`${NS}:kubejs_space_reassembler_controller`) }, 'F'), ['item_input_bus>=tiny', 'item_output_bus>=tiny', 'energy_input_hatch>=tiny'])

ServerEvents.recipes(event => {
  const recipe = (path, machine, ticks, inputs, outputs = [], options = {}) => {
    const builder = new MMCR_RECIPE_BUILDER(cloneId(path)).machine(cloneId(machine)).tickTime(ticks)
      .inputs(inputs).maxThreads(options.maxThreads ?? 1).priority(options.priority ?? 0)
      .cancelIfPerTickFails(options.cancel ?? true).allowPartialOutputs(options.partial ?? false)
    if (options.parallelized !== false) builder.parallelized(true)
    if (options.deriveRequirements === false) builder.deriveRequirements(false)
    if (options.energy) builder.energyPerTick(options.energy)
    if (options.fluids) builder.fluidOutputs(options.fluids.map(([id, amount]) => MMCR_API.fluidStack(id, amount)))
    if (options.level) builder.requiresLevel(...options.level)
    if (options.hosts) builder.requiredHosts(...options.hosts.map(cloneId))
    if (options.smart) for (const [type, value] of options.smart) builder.smartInterfaceInput(type, value)
    if (options.componentInputs) for (const [id, count, components, consumeChance = 1] of options.componentInputs) builder.itemInputWithComponents(id, count, JsonIO.of(components), consumeChance)
    if (options.requirements) for (const requirement of options.requirements) builder.addRequirement(requirement)
    for (const output of outputs) {
      if (output.components) builder.itemOutputWithComponents(output.id, output.count, JsonIO.of(output.components))
      else if (output.chance !== undefined) builder.chancedItemOutput(output.id, output.count, output.chance)
      else builder.itemOutput(output.id, output.count)
    }
    builder.build()
  }
  const item = (id, count = 1) => MMCR_API.itemInput(id, count, 1)
  const chance = (id, count, value) => MMCR_API.itemInput(id, count, value)
  const fluid = (id, amount) => MMCR_API.fluidInput(id, amount)
  const energyOut = value => MMCR_API.energyOutput(value)
  const standard = (machine, first) => {
    recipe(first[0], machine, first[1], first[2], first[3], first[4])
    recipe(`${machine}_copper_to_nugget`, machine, 200, [item('minecraft:copper_ingot')], [{ id: 'minecraft:copper_nugget', count: 1 }], { energy: 2 })
    recipe(`${machine}_gold_to_nugget`, machine, 200, [item('minecraft:gold_ingot')], [{ id: 'minecraft:gold_nugget', count: 1 }], { energy: 3 })
    recipe(`${machine}_multi_item`, machine, 200, [item('minecraft:iron_ingot'), item('minecraft:gold_ingot'), item('minecraft:copper_ingot')], [{ id: 'minecraft:diamond', count: 1 }], { energy: 4 })
    recipe(`${machine}_multi_output`, machine, 200, [item('minecraft:iron_ingot')], [{ id: 'minecraft:iron_nugget', count: 1 }, { id: 'minecraft:gold_nugget', count: 1 }, { id: 'minecraft:copper_nugget', count: 1 }], { energy: 5 })
    recipe(`${machine}_water_input`, machine, 200, [fluid('minecraft:water', 250)], [{ id: 'minecraft:clay_ball', count: 1 }], { energy: 6 })
    recipe(`${machine}_lava_output`, machine, 200, [item('minecraft:coal')], [{ id: 'minecraft:redstone', count: 1 }], { energy: 7, fluids: [['minecraft:lava', 250]] })
    recipe(`${machine}_water_to_lava`, machine, 200, [fluid('minecraft:water', 500)], [{ id: 'minecraft:coal', count: 1 }], { energy: 8, fluids: [['minecraft:lava', 500]] })
    recipe(`${machine}_mixed_input`, machine, 200, [fluid('minecraft:water', 250), item('minecraft:iron_ingot'), item('minecraft:gold_ingot')], [{ id: 'minecraft:emerald', count: 1 }], { energy: 9 })
    recipe(`${machine}_mixed_output`, machine, 200, [fluid('minecraft:water', 250), item('minecraft:diamond'), energyOut(100)], [{ id: 'minecraft:iron_nugget', count: 1 }, { id: 'minecraft:gold_nugget', count: 1 }], { fluids: [['minecraft:lava', 125]] })
  }
  standard('blast_furnace', ['blast_furnace_iron_to_nugget', 200, [item('minecraft:iron_ingot')], [{ id: 'minecraft:iron_nugget', count: 1 }], { energy: 1 }])
  standard('alloy_furnace', ['alloy_furnace_netherite', 100, [item('minecraft:ancient_debris'), item('minecraft:gold_ingot')], [{ id: 'minecraft:netherite_ingot', count: 1 }], { energy: 5 }])
  standard('cracker', ['cracker_coal_lapis', 160, [item('minecraft:coal', 8), item('minecraft:lapis_lazuli')], [{ id: 'minecraft:redstone', count: 4 }], { energy: 100, fluids: [['minecraft:water', 500]] }])
  standard('reactor', ['reactor_diamond_water', 200, [item('minecraft:diamond'), fluid('minecraft:water', 500), energyOut(100)], [{ id: 'minecraft:coal', count: 1 }], { fluids: [['minecraft:lava', 500]] }])
  recipe('alloy_furnace_jei_large', 'alloy_furnace', 400, ['iron_ingot','gold_ingot','copper_ingot','redstone','lapis_lazuli','coal','diamond','emerald','quartz','amethyst_shard','netherite_scrap','iron_nugget','gold_nugget','copper_block','iron_block','gold_block','redstone_block','lapis_block','diamond_block','emerald_block','quartz_block'].map(id => item(`minecraft:${id}`)), ['iron_nugget','gold_nugget','copper_nugget','redstone','lapis_lazuli','coal','diamond','emerald','quartz','amethyst_shard','netherite_scrap','iron_ingot','gold_ingot','copper_ingot','iron_block','gold_block','copper_block','redstone_block','lapis_block','diamond_block','emerald_block'].map(id => ({ id: `minecraft:${id}`, count: 1 })))
  recipe('alloy_furnace_jei_25x25', 'alloy_furnace', 500, ['iron_ingot','gold_ingot','copper_ingot','redstone','lapis_lazuli','coal','diamond','emerald','quartz','amethyst_shard','netherite_scrap','iron_nugget','gold_nugget','copper_block','iron_block','gold_block','redstone_block','lapis_block','diamond_block','emerald_block','quartz_block','coal_block','raw_iron','raw_gold','raw_copper'].map(id => item(`minecraft:${id}`)), ['raw_copper','raw_gold','raw_iron','coal_block','quartz_block','emerald_block','diamond_block','lapis_block','redstone_block','gold_block','iron_block','copper_block','gold_nugget','iron_nugget','netherite_scrap','amethyst_shard','quartz','emerald','diamond','coal','lapis_lazuli','redstone','copper_ingot','gold_ingot','iron_ingot'].map(id => ({ id: `minecraft:${id}`, count: 1 })))
  recipe('thermal_smelting_furnace_coal_iron_to_netherite_scrap', 'thermal_smelting_furnace', 80, [item('minecraft:coal'), item('minecraft:raw_iron')], [{ id: 'minecraft:iron_ingot', count: 1 }], { energy: 200, maxThreads: 4 })
  for (const [name, level, ticks, input, output, energy] of [['copper','thermal_smelting_coil_copper',120,'raw_copper','copper_ingot',400],['iron','thermal_smelting_coil_iron',160,'iron_ingot','gold_ingot',800],['gold','thermal_smelting_coil_gold',200,'gold_ingot','diamond',1200],['diamond','thermal_smelting_coil_diamond',240,'diamond','netherite_ingot',2000]]) recipe(`thermal_smelting_furnace_${name}`, 'thermal_smelting_furnace', ticks, [item('minecraft:coal'), item(`minecraft:${input}`)], [{ id: `minecraft:${output}`, count: 1 }], { energy, maxThreads: 4, level: ['mmcr:thermal_smelting_coil', `mmcr:${level}`] })
  for (const machine of ['blast_furnace','alloy_furnace','cracker','reactor','thermal_smelting_furnace']) {
    const prefix = `${machine}_component_`
    recipe(`${prefix}chanced_input`, machine, 20, [], [{ id: 'minecraft:emerald', count: 1 }], { componentInputs: [['minecraft:diamond', 1, { 'minecraft:custom_name': { text: 'Chance' } }, 0.5]] })
    recipe(`${prefix}non_consumable_input`, machine, 20, [], [{ id: 'minecraft:emerald', count: 1 }], { componentInputs: [['minecraft:diamond', 1, { 'minecraft:custom_name': { text: 'Keep' } }, 0]] })
    recipe(`${prefix}non_consumable_sharpness_input`, machine, 100, [], [], { componentInputs: [['minecraft:diamond_sword', 1, { 'minecraft:enchantments': { 'minecraft:sharpness': 2 } }, 0]] })
    recipe(`${prefix}enchanted_output`, machine, 100, [item('minecraft:iron_sword')], [], { requirements: [MMCR_API.itemOutputRequirementWithComponents('minecraft:iron_sword', 1, JsonIO.of({ 'minecraft:enchantments': { 'minecraft:sharpness': 2 }, 'minecraft:repair_cost': 1 }), 1)] })
    recipe(`${prefix}input_to_plain_output`, machine, 20, [], [{ id: 'minecraft:emerald', count: 1 }], { componentInputs: [['minecraft:diamond', 1, { 'minecraft:custom_name': { text: 'Input Only' } }]] })
    recipe(`${prefix}plain_input_to_output`, machine, 20, [item('minecraft:iron_ingot')], [{ id: 'minecraft:gold_ingot', count: 1, components: { 'minecraft:custom_name': { text: 'Output Only' } } }])
    recipe(`${prefix}input_to_output`, machine, 20, [], [{ id: 'minecraft:gold_ingot', count: 1, components: { 'minecraft:custom_name': { text: 'Output' } } }], { componentInputs: [['minecraft:diamond', 1, { 'minecraft:custom_name': { text: 'Input' } }]] })
    recipe(`${prefix}mixed_inputs`, machine, 20, [item('minecraft:iron_ingot')], [{ id: 'minecraft:emerald', count: 1 }], { componentInputs: [['minecraft:diamond', 1, { 'minecraft:custom_name': { text: 'Named' } }]] })
    recipe(`${prefix}mixed_outputs`, machine, 20, [item('minecraft:iron_ingot')], [{ id: 'minecraft:gold_ingot', count: 1, components: { 'minecraft:custom_name': { text: 'Named Output' } } }, { id: 'minecraft:emerald', count: 1 }])
    recipe(`${prefix}chanced_outputs`, machine, 20, [item('minecraft:iron_ingot')], [], { requirements: [MMCR_API.itemInputRequirement('minecraft:apple', 1), MMCR_API.itemOutputRequirement('minecraft:emerald', 1, 1), MMCR_API.itemOutputRequirement('minecraft:diamond', 1, 0.5), MMCR_API.fluidOutputRequirement('minecraft:lava', 250, 0.25)], deriveRequirements: false })
    recipe(`${prefix}complex`, machine, 20, [item('minecraft:stick'), chance('minecraft:iron_nugget', 1, 0.5), chance('minecraft:gold_nugget', 1, 0.25)], [{ id: 'minecraft:emerald', count: 1 }, { id: 'minecraft:diamond', count: 1, chance: 0.5 }, { id: 'minecraft:redstone', count: 1, chance: 0.25 }])
  }
  recipe('blast_furnace_component_tag_input', 'blast_furnace', 20, [MMCR_API.tagInput('minecraft:logs', 1, 1)], [{ id: 'minecraft:charcoal', count: 1 }])
  new MMCR_RECIPE_BUILDER(cloneId('blast_furnace_component_tag_named_input')).machine(cloneId('blast_furnace')).tickTime(20).tagInputWithComponents('minecraft:planks', 1, JsonIO.of({ 'minecraft:custom_name': { text: 'Validated' } }), 1).itemOutput('minecraft:emerald', 1).build()
  new MMCR_RECIPE_BUILDER(cloneId('blast_furnace_component_tag_enchanted_input')).machine(cloneId('blast_furnace')).tickTime(20).tagInputWithComponents('minecraft:swords', 1, JsonIO.of({ 'minecraft:enchantments': { 'minecraft:sharpness': 2 } }), 1).itemOutput('minecraft:diamond', 1).build()
  for (const [name, ticks, energy, output, count, smart] of [['mode_1',200,5,'diamond',2,[['Mode',1]]],['mode_2',200,5,'gold_ingot',4,[['Mode',2]]],['mode_3',200,5,'iron_ingot',8,[['Mode',3]]],['temperature_400',320,3,'apple',8,[['Temperature',400]]],['temperature_1600',240,6,'baked_potato',6,[['Temperature',1600]]],['temperature_3200',160,9,'brick',4,[['Temperature',3200]]],['temperature_6800',60,14,'charcoal',2,[['Temperature',6800]]],['conversion_0',200,2,'stick',1,[['ConversionRate',0]]],['conversion_50',200,6,'bone_meal',4,[['ConversionRate',0.5]]],['conversion_100',200,12,'glowstone_dust',8,[['ConversionRate',1]]],['mode_temperature',120,10,'popped_chorus_fruit',3,[['Mode',2],['Temperature',3200]]],['mode_conversion',200,9,'string',6,[['Mode',3],['ConversionRate',0.75]]],['temperature_conversion',90,15,'clay_ball',5,[['Temperature',5200],['ConversionRate',0.8]]],['mode_temperature_conversion',80,18,'ender_pearl',4,[['Mode',1],['Temperature',5200],['ConversionRate',1]]]]) recipe(`purpur_furnace_${name}`, 'purpur_furnace', ticks, [item('minecraft:coal')], [{ id: `minecraft:${output}`, count }], { energy, smart })
  for (const [name, input, first, second, third] of [['coal','coal','coal','charcoal','gunpowder'],['oak_log','oak_log','charcoal','stick','coal'],['dried_kelp','dried_kelp','kelp','coal','bone_meal']]) recipe(`distillation_tower_${name}`, 'distillation_tower', 200, [item(`minecraft:${input}`)], [{ id: `minecraft:${first}`, count: 1 }, { id: `minecraft:${second}`, count: 1 }, { id: `minecraft:${third}`, count: 1 }], { energy: 40, maxThreads: 4, partial: true })
  recipe('eco_matrix_energy_drain', 'eco_matrix', 200, [], [], { energy: 100, cancel: true })
  recipe('space_elevator_thread_dispersal', 'space_elevator', 1000, [chance('mmcr:thread_disperser', 1, 0)], [], { energy: 10000, cancel: true, partial: false })
  recipe('space_reassembler_steak_to_golden_carrot', 'space_reassembler', 600, [item('minecraft:cooked_beef', 4)], [{ id: 'minecraft:golden_carrot', count: 1 }], { energy: 15000, partial: false, hosts: ['space_elevator'] })
  recipe('space_reassembler_water_to_healing', 'space_reassembler', 400, [], [{ id: 'minecraft:potion', count: 1, components: { 'minecraft:potion_contents': { potion: 'minecraft:healing' } } }], { energy: 8000, partial: false, hosts: ['space_elevator'], componentInputs: [['minecraft:potion', 1, { 'minecraft:potion_contents': { potion: 'minecraft:water' } }]] })
  recipe('space_reassembler_water_to_swiftness', 'space_reassembler', 400, [], [{ id: 'minecraft:potion', count: 1, components: { 'minecraft:potion_contents': { potion: 'minecraft:swiftness' } } }], { energy: 8000, partial: false, hosts: ['space_elevator'], componentInputs: [['minecraft:potion', 1, { 'minecraft:potion_contents': { potion: 'minecraft:awkward' } }]] })
})
