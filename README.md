# MacroNode (Fabric 1.21.11, client-side)

Visual macro system for Minecraft: a Pathmind/Scratch-style node editor, high-priority key overriding,
a sub-tick executor, built-in presets and Base64 share codes.

## Install
1. Install Fabric Loader >= 0.20.1 for Minecraft 1.21.11 and Fabric API.
2. Drop `macronode-1.0.0.jar` into `.minecraft/mods`.

## Keys (rebind in Options > Controls > Key Binds > MacroNode)
| Key | Action |
|-----|--------|
| `O` | Config Hub: profiles, presets, settings, import/export |
| `E` | Macro Editor (node graph). Note: takes priority over vanilla inventory on `E`; rebind either if you prefer |
| unbound | Stop all running macros |

## Using it
* **Config Hub [O]** - `+ New Profile` opens the binding modal (name, Single vs Combo trigger, key recorder,
  High-Priority Override toggle). Presets tab imports the 4 official presets in one click.
* **Editor [E]** - click a palette block to append it to the chain (Shift+Click = unlinked node). Drag nodes,
  drag from a right port onto a node to link, drop a node next to a port to snap+link, right-click to detach,
  scroll a node to change its time/count, `Del` deletes, `Ctrl+D` duplicates, `Ctrl+S` saves. `Test` runs it.
* **Timing** - `0t` (same frame), `0.2t` (=10ms), `1t` (=50ms), `10ms`, `1.5s`.
* **Repeat Loop** repeats the next N blocks (0 = infinite; press the trigger again to stop).
  **Conditional Check** skips the next N blocks (or stops) when false.

## Files
* Profiles: `.minecraft/config/macronode/profiles/*.json`
* Settings: `.minecraft/config/macronode/settings.json`
* Drop `.json` files into `config/macronode/imports/` to load them from the import modal.
* Share codes: `MN1:` + URL-safe Base64(GZIP(JSON)). Raw JSON is also accepted from the clipboard.

## Build
Requires JDK 25 to run Gradle/Loom (the mod itself targets Java 21).
```
./gradlew build
```
Output: `build/libs/macronode-1.0.0.jar`.

## Code map (`src/client/java/macronode/macro/client`)
* `mixin/KeyboardHandlerMixin`, `MouseHandlerMixin` - HEAD injections that cancel native input for override profiles.
* `input/InputInterceptor` - single key / chord matching, most specific chord wins.
* `engine/` - `MacroEngine` (per-frame pump via `MinecraftMixin` + client tick), `MacroRun` (program counter, loops,
  conditions), `HeldKeys` (simulated key holds), `TimeSpec`, `Step`.
* `action/` - `ActionType` catalog, `ActionBlock` model, handlers per category.
* `data/` - `MacroProfile`, `Trigger`, `ProfileStore` (JSON IO), `ProfileCodec` (Base64), `PresetRegistry`.
* `gui/` - `ConfigScreen`, `BindingModalScreen`, `MacroEditorScreen`, `DarkButton`, `Theme`.
