"""Cache loot tables: the small stash each camp keeps in a chest inside its walls (set on the chest when the camp is
built; the War Chest you get for claiming the standard stays the big reward). Each kind's stash is themed, rolls 2-3
times and is worth about a quarter of that kind's War Chest, and holds nothing above the camp's age."""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'data', 'warbands', 'loot_table', 'chests', 'cache')


def item(name, weight, lo=None, hi=None, enchant=None, potion=None, title=None):
    e = {'type': 'minecraft:item', 'name': 'minecraft:' + name, 'weight': weight}
    fns = []
    if lo is not None:
        fns.append({'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': float(lo), 'max': float(hi if hi is not None else lo)}, 'add': False})
    if enchant:
        fns.append({'function': 'minecraft:enchant_with_levels', 'levels': {'type': 'minecraft:uniform', 'min': float(enchant[0]), 'max': float(enchant[1])}, 'options': '#minecraft:on_random_loot'})
    if potion:
        fns.append({'function': 'minecraft:set_potion', 'id': 'minecraft:' + potion})
    if title:
        fns.append({'function': 'minecraft:set_name', 'name': {'translate': title}})
    if fns:
        e['functions'] = fns
    return e


def table(rolls, entries):
    return {'type': 'minecraft:chest', 'pools': [{'bonus_rolls': 0.0, 'rolls': {'type': 'minecraft:uniform', 'min': float(rolls[0]), 'max': float(rolls[1])}, 'entries': entries}]}


T = {
    # village: food, tools and trade goods
    'bandit_camp': table((2, 3), [item('bread', 14, 2, 5), item('cooked_porkchop', 10, 2, 4), item('arrow', 12, 6, 14), item('iron_nugget', 10, 4, 9),
                                   item('leather', 8, 2, 4), item('gold_nugget', 8, 3, 8), item('stone_sword', 5), item('bow', 3, enchant=(1, 8))]),
    'poachers_lodge': table((2, 3), [item('arrow', 16, 8, 16), item('rabbit_hide', 10, 2, 4), item('leather', 10, 2, 5), item('cooked_rabbit', 10, 2, 4),
                                      item('feather', 10, 4, 10), item('string', 8, 3, 7), item('bow', 5, enchant=(4, 12)), item('spyglass', 2)]),
    'smugglers_dock': table((2, 3), [item('cod', 10, 2, 5), item('salmon', 8, 2, 4), item('emerald', 8, 1, 3), item('gold_nugget', 10, 4, 9), item('string', 8, 3, 6),
                                      item('name_tag', 3), item('compass', 4), item('map', 3), item('lead', 6, 1, 2), item('fishing_rod', 3, enchant=(4, 12))]),
    # feudal: metal, brews and cold-weather gear
    'fort': table((2, 3), [item('iron_ingot', 12, 2, 5), item('coal', 10, 4, 9), item('cooked_beef', 10, 2, 4), item('arrow', 10, 8, 16), item('shield', 3),
                            item('iron_helmet', 3), item('crossbow', 3, enchant=(4, 12)), item('emerald', 6, 1, 3)]),
    'quarry_stronghold': table((2, 3), [item('cobblestone', 10, 8, 16), item('iron_ingot', 10, 2, 4), item('coal', 10, 4, 10), item('raw_copper', 8, 3, 7), item('gold_ingot', 6, 1, 3),
                                         item('stone_pickaxe', 4, enchant=(4, 12)), item('tnt', 3, 1, 2), item('torch', 8, 6, 12)]),
    'witch_coven': table((2, 3), [item('glass_bottle', 10, 2, 5), item('redstone', 10, 3, 8), item('glowstone_dust', 8, 2, 5), item('spider_eye', 8, 1, 3), item('sugar', 8, 2, 5),
                                   item('potion', 6, potion='healing'), item('potion', 5, potion='swiftness'), item('potion', 4, potion='fire_resistance'), item('lingering_potion', 2, potion='regeneration')]),
    'frozen_outpost': table((2, 3), [item('cooked_salmon', 10, 2, 4), item('leather', 8, 2, 4), item('snowball', 6, 4, 10), item('powder_snow_bucket', 3), item('iron_ingot', 8, 2, 4),
                                      item('leather_chestplate', 4), item('bow', 3, enchant=(4, 12)), item('golden_carrot', 5, 2, 4)]),
    # castle: better kit, still well under the War Chest
    'keep': table((2, 3), [item('iron_ingot', 10, 3, 6), item('gold_ingot', 8, 2, 5), item('emerald', 8, 2, 4), item('golden_carrot', 6, 3, 6), item('book', 5, enchant=(8, 18)),
                            item('iron_sword', 3, enchant=(6, 14)), item('iron_chestplate', 3, enchant=(6, 14)), item('diamond', 2, 1, 1), item('experience_bottle', 5, 2, 4)]),
    'cavalry_encampment': table((2, 3), [item('wheat', 10, 4, 9), item('carrot', 8, 3, 6), item('hay_block', 5, 1, 2), item('saddle', 3), item('lead', 8, 1, 3), item('iron_horse_armor', 3),
                                          item('golden_carrot', 8, 3, 6), item('iron_ingot', 8, 2, 5), item('iron_sword', 2, enchant=(6, 14))]),
    'sunken_crypt': table((2, 3), [item('bone', 12, 3, 8), item('rotten_flesh', 8, 3, 6), item('gold_ingot', 8, 2, 4), item('emerald', 6, 1, 3), item('lapis_lazuli', 8, 4, 9),
                                    item('book', 4, enchant=(8, 18)), item('amethyst_shard', 5, 2, 5), item('golden_apple', 2), item('skeleton_skull', 1)]),
    'illager_hall': table((2, 3), [item('emerald', 12, 2, 5), item('crossbow', 4, enchant=(6, 14)), item('arrow', 8, 8, 16), item('iron_axe', 4, enchant=(6, 14)), item('book', 4, enchant=(8, 18)),
                                    item('golden_carrot', 6, 3, 5), item('bread', 8, 3, 6)]),
}

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for kind, data in T.items():
        with open(os.path.join(OUT, kind + '.json'), 'w') as f:
            json.dump(data, f, indent=2)
            f.write('\n')
    print(len(T), 'cache tables')
