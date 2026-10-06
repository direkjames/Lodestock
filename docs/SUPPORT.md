# Supported versions and support policy

## Minecraft versions

Lodestock is one jar for **Paper and Purpur**:

| Minecraft | Java | Status |
|---|---|---|
| 1.21.11 | 21 | Supported |
| 26.1, 26.2, 26.3 | 25 | Supported |

Other Paper forks may work but are not tested. Plain Spigot and Bukkit are not supported (Lodestock needs the Paper API).

## How versions are handled

- **Newer versions.** When a new Minecraft or Paper version comes out, I test Lodestock on it as soon as I can (usually within a week or two). If it works unchanged, it is added to the list above. If something breaks, it is fixed in a patch release (1.0.x or 1.x.0).
- **Older versions.** The oldest supported version is 1.21.11. A version is only dropped from the list in a **major** release (2.0.0), and the changelog announces it at least one minor release before. Within 1.x, a plugin jar that works on a version today keeps working on it.
- **One jar, newest wins.** The jar is built against the oldest supported Paper API, so one file covers everything in the table. Bug fixes always go into the current release; older releases are not patched, so please update first.
- **Pre-releases.** Beta builds (0.x) are not supported any more. Update to 1.0.0 or newer.
- **Minecraft snapshots and release candidates** are not supported.

## Compatibility promise for 1.x

- Your `config.yml`, `items.yml`, `gui.yml`, `discord.yml`, `events.yml` and `lang/` files keep working. New settings always have a safe default, so you never have to edit a file after updating.
- Commands and permissions are not renamed or removed.
- The database upgrades itself. Back it up first (see the [FAQ](FAQ.md#backups-the-database-and-resetting)).
- The public API ([docs/API.md](API.md)) only grows. `LodestockApi.API_VERSION` stays `1`.

## Where to get help

1. The [FAQ and troubleshooting guide](FAQ.md) and the [economy guide](ECONOMY.md).
2. [GitHub Discussions](https://github.com/direkjames/Lodestock/discussions) for questions and ideas.
3. [GitHub Issues](https://github.com/direkjames/Lodestock/issues/new/choose) for bugs. Please use the form.

Lodestock is a free project made by one person in their spare time. Replies can take a few days.
