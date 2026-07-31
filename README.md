# Lucky Tweaks

Fixes and balance tools for the Lucky Block mod. Makes a lucky block's stored Luck actually apply when a player breaks it (upstream bug: it always rolled at luck 0), adds configurable per-block luck caps (applied to both luck-crafting results and drop rolls), and exposes a tiny API for other mods to contribute luck to a break.

Minecraft 1.20.1, Forge. Written for the [Lucky World Invasion Reloaded](https://github.com/LuckyWorldInvasionReloaded/Modpack) modpack, but it runs on its own.

## Achievements

An advancement ladder for lucky blocks, on the vanilla advancement screen (`L`) with the usual toast and
chat announcement. Twenty-three of them, in eight lines:

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

Counters are per player and per world, kept in the world's data storage — so they survive death,
respawn and the shared-lives pool alike. `/luckyachievements` prints your own; ops can read another
player's, or `/luckyachievements set <player> <counter> <value>` to test a tier without grinding to it.
The whole feature is behind the `achievements.enableAchievements` config switch.

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
