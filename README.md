# Lucky Tweaks

Fixes and balance tools for the Lucky Block mod. Makes a lucky block's stored Luck actually apply when a player breaks it (upstream bug: it always rolled at luck 0), adds configurable per-block luck caps (applied to both luck-crafting results and drop rolls), and exposes a tiny API for other mods to contribute luck to a break.

Minecraft 1.20.1, Forge. Written for the [Lucky World Invasion Reloaded](https://github.com/Laink/LuckyWorldInvasionReloaded) modpack, but it runs on its own.

## Building

No local dependency to set up:

```bash
./gradlew build
```

## Contributing

Issues and pull requests are welcome. Keep code and comments in English.

## License

Official builds are free to redistribute unmodified (mirrors, modpacks) with credit. Modified versions and forks published as separate mods need permission. See [LICENSE](LICENSE).
