# Lucky Tweaks

Fixes and balance tools for the Lucky Block mod. Makes a lucky block's stored Luck actually apply when a player breaks it (upstream bug: it always rolled at luck 0), adds configurable per-block luck caps (applied to both luck-crafting results and drop rolls), and exposes a tiny API for other mods to contribute luck to a break.

Minecraft 1.20.1, Forge. Written for the [Lucky World Invasion Reloaded](https://github.com/LuckyWorldInvasionReloaded/Modpack) modpack, but it runs on its own.

## Achievements

An advancement ladder for lucky blocks, on the vanilla advancement screen (`L`) with the usual toast and
chat announcement. Sixty of them. The lucky-block half:

| Line | Counter | Tiers |
| --- | --- | --- |
| Blocks broken | `broken` | 1, 25, 50, 100, 200, 500, 1000 |
| Different kinds broken | `broken_types` | 5, 10, 25 |
| Best Luck ever crafted | `crafted_luck_max` | +50, +100 |
| Worst Luck ever crafted | `crafted_luck_min` | -50, -100 |
| Blocks fused | `fused` | 1, 50 |
| Legendary drops | `legendary` | 1, 10, 50 |
| Blocks broken at negative Luck | `negative_luck_breaks` | 1, 25 |
| Blocks broken at +100 Luck | `max_luck_breaks` | 1, 25 |

…and the pack half, about what the rest of Lucky World Invasion puts in your way:

| Line | Counter | Tiers |
| --- | --- | --- |
| Different Lucky Tools found | `tools_found` | 1, 4, 8 |
| Same Lucky Tool twice (not cool) | `tool_dupes` | 1 |
| Best Chance carried into a break | `chance_max` | +50%, +100% |
| Lucky events hitting a MEGA jackpot | `mega_jackpots` | 1 |
| Items bought in a shop | `shop_buys` | 1, 10, 25 |
| Bought from a legendary slot | `legendary_buys` | 1 |
| Uninfused lucky weapon used (kamikaze) | `raw_weapon_uses` | 1, 25 |
| Different water bosses met | `water_bosses` | 1, 3, 6 |
| Invasions completed | `invasions` | 1, 5, 10 |
| Extendo Grip equipped | `extendo` | 1 |
| All-nighters pulled (dusk to dawn, no bed) | `all_nighters` | 1, 3 |
| Cheesecake à la merde eaten | `cheesecake` | 1 |
| Cursed drops rolled | `cursed_drops` | 1, 10, 50 |
| Sacred Hearts eaten | `sacred_hearts` | 1, 10 |
| Highest day of the run reached alive | `days_survived` | 10, 25, 50 |
| Times knocked down and revived | `revived` | 1, 10 |
| Played on after the pool fell to its last life | `last_stand` | 1 |
| Crocodiles killed with your gear inside | `croc_recovered` | 1 |
| The Ender Trigon slain | `dragon_slain` | 1 |
| Chaos gauntlets escaped | `gauntlet_escapes` | 1 |

Counters are per player and per world, kept in the world's data storage — so they survive death,
respawn and the shared-lives pool alike. `/luckyachievements` prints your own; ops can read another
player's, `/luckyachievements set <player> <counter> <value>` to test a tier without grinding to it, or
`/luckyachievements grant <players> <counter> [amount]` to report an event from a script or a datapack.
The whole feature is behind the `achievements.enableAchievements` config switch.

The pack half names content owned by other mods, so what it watches for is config, not code:
`luckyToolItems`, `luckyWeaponItems`, `waterBosses` (+ `waterBossRange`), `extendoGripItem`,
`cheesecakeItem` and `finalBossEntity` under `[achievements]`. An id no installed mod provides is simply
never matched — a smaller pack quietly gets a shorter ladder. Four counters cannot be observed from here
at all, because they happen inside another mod's or the pack's own logic, and arrive through the API
instead: `reportMegaJackpot` (Lucky XP's events), `reportShopPurchase` (vending machines and the Lucky
Merchant), `reportInvasionCompleted` (Optional Suffering), plus `gauntlet_escapes` for the Chaos Lucky
Block gauntlet — all four also reachable with `grant` from a KubeJS script until those mods call them:

```
/luckyachievements grant @s gauntlet_escapes
```

Everything runs on one generic criterion, `luckytweaks:lucky_progress`, taking a `stat` and a `min`
(negative `min` matches at or *below* it, for the curse side). A datapack can therefore move a
threshold, add tiers, or replace the tree entirely without touching the jar:

```json
{
  "criteria": {
    "progress": {
      "trigger": "luckytweaks:lucky_progress",
      "conditions": { "stat": "broken", "min": 5000 }
    }
  }
}
```

## Building

No local dependency to set up:

```bash
./gradlew build
```

## Contributing

Issues and pull requests are welcome. Keep code and comments in English.

## License

Official builds are free to redistribute unmodified (mirrors, modpacks) with credit. Modified versions and forks published as separate mods need permission. See [LICENSE](LICENSE).
