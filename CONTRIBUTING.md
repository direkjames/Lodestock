# Contributing

Thank you for wanting to help! Bug reports, ideas and pull requests are welcome.

- **Bugs and ideas:** use the [issue forms](https://github.com/direkjames/Lodestock/issues/new/choose) or [Discussions](https://github.com/direkjames/Lodestock/discussions). For a bigger change, please talk about it first so nobody wastes time.
- **Building:** you need JDK 21 or newer. `./gradlew build` builds everything and runs the tests (`gradlew.bat build` on Windows). The jar is in `paper/build/libs/`. To try it on a test server, `./gradlew :paper:runServer` (see `paper/build.gradle.kts` for `-PmcVersion`).
- **Layout:** `core` has the market logic and no Minecraft code (add unit tests there whenever you can), `api` is the public API for other plugins, `paper` is the plugin.
- **Changes to the public API** must be additions only (see [docs/API.md](docs/API.md)).
- **Style:** keep to the style of the surrounding code, keep messages in `lang/en.yml`, and add new settings with a safe default so existing config files keep working. Note user-visible changes in `CHANGELOG.md`.
- **License:** Lodestock is GPL-3.0. By contributing you agree that your work is released under the same license.
