# Litematica V7 Compat (Fabric 1.17.1)

An addon for Litematica 1.17.1: when a schematic uses the V7 format (created in Minecraft 1.20.5+ / 1.21+ / 26.x), it is converted in memory when loaded, then loaded normally.

- No interface, no buttons, no configuration.
- No files are created or modified: the original `.litematic` file remains untouched.
- V6 schematics (and earlier versions) are not affected.
- If a future version of Litematica supports V7 natively, this mod automatically stops doing anything (it checks `LitematicaSchematic.SCHEMATIC_VERSION` using reflection).

The conversion logic is a Java port of the downgrade functionality from `litematica-converter` (`index.js`):

| Element | Conversion |
|---|---|
| Block palette | `short_grass` → `grass`, `crafter` → `dropper`, `copper_bulb` → `redstone_lamp`, `copper_door/trapdoor` → `iron_*`, `pale_oak_*` → `dark_oak_*`, `creaking_heart` → `dark_oak_log`, `resin_*` → `orange_concrete` |
| Signs | `front_text` / `back_text` / `is_waxed` → `Text1..4`, `Color`, `GlowingText` |
| Items (chests, item frames, shulkers, etc.) | `count` (int) → `Count` (byte), `components` → `tag` (damage, enchantments, name, lore, potions, custom data, shulker contents) |
| Ticks | `PendingBlockTicks` / `PendingFluidTicks` are added if missing |
| Header | `Version` = 6, `MinecraftDataVersion` = game version (2730) |

## Building

Requirements: JDK 17 (Gradle 7.3 does not support JDK 21).

```bash
gradle build
```

This requires Gradle 7.3.x to be installed.

Alternatively, open the project directory in IntelliJ IDEA (automatic Gradle import), then run the `build` task. The JAR file will be generated at:

`build/libs/litematica-v7-compat-1.0.0.jar`

Without a development environment, push this project to a GitHub repository. The Actions tab will build the JAR file using the `.github/workflows/build.yml` workflow and make it available for download.

## Installation

Place the JAR file in `.minecraft/mods/` alongside Fabric Loader, Litematica, and MaLiLib for Minecraft 1.17.1.

When a V7 schematic is converted, a message will appear in `logs/latest.log`:

```text
[litematica_v7_compat] V7 schematic (DataVersion ...) converted in memory to V6 ...
```

## Limitations

- As with the original converter, only the blocks listed in the table above are replaced. Blocks that do not exist in Minecraft 1.17.1 (e.g. cherry wood, tuff bricks, etc.) are simply ignored by Litematica.
- Entity data and block entity data introduced in Minecraft 1.20.5+ (other than signs and items) are not converted.

## About

This mod makes life easier for players using older versions of Minecraft, such as 1.17. In newer versions, a new schematic encoding format was introduced, and this mod automatically converts V7 schematics to V6.
