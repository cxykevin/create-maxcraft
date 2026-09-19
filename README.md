# Create: MaxCraft

A **NeoForge 1.21.1** addon for **Create 6.0.10** (`net.neoforged.moddev` 2.0.78, NeoForge 21.1.248,
Parchment 2024.11.17, Java 21). It lifts Create's hard-coded limits on packages, factory panels and Mechanical
Crafter arrays: packages of any size, crafting grids of any size, and machines that place a whole batch into a
crafter array in the right shape.

The mod id stays `maxcraft`; the name shown in the mod list is **Create: MaxCraft**.

## What this mod depends on

| Mod | 中文名 | Relation | How it is provided |
| --- | --- | --- | --- |
| Create 6.0.10-231 | 机械动力 | **required** | Gradle dependency from `maven.createmod.net` |
| Create: Cyber Goggles 1.21.1-8.6.1 | 机械动力：赛博护目镜 | **optional**, client side | jar in `run/mods/` |

Create's own libraries (Flywheel, Ponder, Catnip, Registrate, Vanillin) come in transitively — they are part of
Create, not extra mods.

### Dev-run-only mods (not build dependencies)

Installed as jars in `run/mods/` only, on neither the compile classpath nor any Gradle configuration:

| Mod | 中文名 | Version |
| --- | --- | --- |
| Just Enough Items | JEI | 19.51.0.418 |
| Just Enough Characters | 通用拼音搜索 | 4.5.29 |
| Sodium | — | 0.8.13+mc1.21.1 |
| Lithium | — | 0.15.4+mc1.21.1 |

Sodium, JEI and Just Enough Characters are client-only, which is why the **server run has its own game directory**
(`run-server/`).

## Content

| Thing | 中文名 | How it is made |
| --- | --- | --- |
| Large Package Component | 大型包裹构件 | **Sequenced assembly**: Sturdy Sheet, deployed with a Precision Mechanism, then pressed |
| Large Packager | 大型打包机 | Packager + component, or right click a Packager with one |
| Large Re-Packager | 大型理包机 | Re-Packager + component, or right click a Re-Packager with one |
| Extended Stock Ticker | 扩展仓储发报机 | Stock Ticker + component, or right click a Stock Ticker with one |
| Extended Factory Gauge | 扩展工厂仪表 | Factory Gauge + component (an item: Create's gauge carrying its grid size) |

The Large Packager and the Large Re-Packager also convert into each other at a crafting table, mirroring Create's
own `repackager_from_conversion` recipe. In-place upgrades carry the block entity's NBT across, so networks,
settings and held boxes survive.

## The package system

Create caps a package at nine stacks and stores contents in a vanilla `ItemContainerContents` (256 slots). This mod
lifts both, without touching the request/packaging limits:

| File | What it does |
| --- | --- |
| `logistics/PackageContents.java` | reads/writes package contents at any size, keeps a readable mirror |
| `registry/MaxcraftDataComponents.java` | `maxcraft:package_bulk_contents`, an unbounded `List<ItemStack>` |
| `mixin/PackageItemMixin.java` | `getContents` / `containing` use the size-aware implementation |
| `mixin/PackageRepackageHelperMixin.java` | the merge produces **one** package per order, however many stacks |
| `mixin/RepackagerBlockEntityMixin.java` | the Repackager collects its whole inventory before merging |
| `mixin/PackagerBlockEntityMixin.java` | the Large Packager fills `maxPackageStacks` stacks per cycle |

**Nothing is ever voided.** Unpacking, dropping, sawing apart and merging all read through the patched
`getContents`, so the full contents travel.

### Delivery and pickup

| Path | Behaviour |
| --- | --- |
| Large Packager → Mechanical Crafter | the pattern decides the layout (see below) |
| Large Re-Packager | waits for the **whole** order and merges it into **one** package |
| Breaking a gauge with 2+ panels | removes one panel, drops an extended gauge item |
| Wrenching a panel off | same |
| Breaking a gauge with one panel left | the block drops through the loot table, marked |

A dropped or wrenched extended gauge is **bare**: it carries its grid size and its upgrade marker, and nothing else.
Like a gauge fresh off the crafting table it has to be bound to a network (right click a Stock Ticker) before it can
be placed.

## The Extended Factory Gauge

Create's gauge, with a **crafting grid size stored in the panel's data** — there is no second block.

* **Per panel.** A gauge's four panels are four machines sharing a block: each keeps its own size, and upgrading one
  never touches the other three.
* **Upgrading.** Right click a panel with a Large Package Component, or use an extended gauge item. A slot that
  already has a panel is left alone — nothing is changed and nothing is consumed.
* **Items.** An extended gauge item carries a **single size** (`maxcraft:gauge_grid_sizes`, one value), so gauges of
  the same size are the same item and stack; which panel the size lands on is decided when the item is used. The size
  travels in a component of this mod's own, so Create's tuning and tag rewrites cannot lose it.
* **In the world**, panels that work with a large grid are drawn in brass. The panel body is Create's own dynamic
  model, so the model data carries the set of extended slots and the panel is laid out from a brass copy of Create's
  model — the shape and placement are Create's.
* **Display.** In crafting mode the panel shows its **material list** (and pages through it with the ‹ › buttons when
  there are more than nine); hovering it shows a **preview of the recipe's own shape**, never the padded grid.
  Ordinary gauges look and behave exactly as they did.

## Crafting larger recipes

* **The pattern.** A recipe smaller than the panel's grid is laid into the grid's **top left** with empty cells
  around it, so the machine receives a pattern for its own size. A recipe that does not fit keeps its own shape.
* **The layout.** Create hands the crafters over row by row; the mod reads how many crafters a row holds from the
  array itself and lays the pattern out at that width, so a 10x10 recipe lands as a 10x10 block in a 12x12 array
  rather than skewing.
* **Batches.** A package carrying many crafts' worth of ingredients fills the cells the pattern asks for in rounds —
  one item per cell per round — so the surplus is spread evenly instead of piling into the first crafter.

## Configuration

`maxcraft-server.toml` (in `<world>/serverconfig/` in single player, `config/` on a dedicated server):

```toml
# The largest number of stacks a single package may hold.
maxPackageStacks = 64
```

## Commands

This project uses the system Gradle 9.7.0 (the same one the sibling `../m8s` project uses) — there is no wrapper.

```bash
gradle build          # compile + jar
gradle runClient      # launch the Minecraft client (needs DISPLAY), game dir: run/
gradle runServer      # launch a dedicated server (no client-only mods), game dir: run-server/
gradle runData        # run the data generators
```

Running the client outside a full desktop session needs the session environment, e.g.:

```bash
DISPLAY=:1 WAYLAND_DISPLAY=wayland-0 XDG_RUNTIME_DIR=/run/user/1000 \
  XAUTHORITY=/run/user/1000/xauth_XXXXXX gradle runClient
```

## Self test

`PackageSystemSelfTest` exercises the patches inside a real server — package storage round trips, the
Re-Packager's merge, per-panel grid sizes, every pickup and placement path, and the pattern layout:

```bash
MAXCRAFT_SELFTEST=1 gradle runServer
```

It logs `SELFTEST PASSED` / `SELFTEST FAILED` with a check count and stops the server. Inert without the env var.

## Layout

```
build.gradle                                 moddev 2.0.78 + Create dependency
gradle.properties                            versions (NeoForge 21.1.248, Create 6.0.10-231, ...)
src/main/java/dev/maxcraft/Maxcraft.java     entry point (@Mod("maxcraft"))
src/main/java/dev/maxcraft/content/          the machines this mod adds
src/main/java/dev/maxcraft/logistics/        package contents, pattern layout, size storage
src/main/java/dev/maxcraft/mixin/            the Create patches
src/main/resources/maxcraft.mixins.json      mixin config
run/                                         client dev directory (world, logs, config, mods)
run-server/                                  dedicated server directory
```

The Gradle/NeoForge/Minecraft artifact cache is shared with `../m8s` through `~/.gradle`.

## License

**GNU Lesser General Public License v3.0 or later** — see `LICENSE`. The full text is the one published by the
FSF, and `mod_license` in `gradle.properties` is what the mod list shows.
