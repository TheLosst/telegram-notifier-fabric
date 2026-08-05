# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Client-side Fabric mod for Minecraft **26.2**. Watches the local player on an AFK farm and sends Telegram notifications on damage, death, connect and disconnect. `SPEC.md` is the original Russian spec (ТЗ); `USAGE.md` is the build/usage guide.

## Build — Docker only

**The host has no JDK and no Gradle.** Never suggest running `./gradlew` or `java` directly on the host.

```bash
./build.sh          # → build/libs/afk-notifier-1.1.1.jar
```

`build.sh` bootstraps the Gradle Wrapper into the repo if `gradlew`/`gradle-wrapper.jar` are missing (one-off `gradle:9.5.1-jdk25` container, run in an *empty* dir — running `gradle wrapper` in the project root fails because it tries to configure the Loom build before the wrapper exists). Then it builds the image (`./gradlew build --no-daemon` runs inside) and copies the jar out via a bind-mount on `/out`.

On Windows/Git Bash, bind-mount paths must be Windows-style and `MSYS_NO_PATHCONV=1` must be set — `build.sh` handles this via `cygpath -m`.

Only the build runs in Docker. The mod itself is never run in a container.

There is no test framework.

## Version constraints (MC 26.2) — all verified against Maven, not memory

- **26.2 is not obfuscated, so no mappings are declared at all.** Do not add Yarn, and do *not* add `loom.officialMojangMappings()` — Loom fails with `Failed to find official mojang mappings for 26.2`. The `dependencies` block has no `mappings` line. (`SPEC.md` says to use official Mojang mappings; that is wrong for 26.2.)
- For the same reason there is nothing to remap, so deps use plain **`implementation`**, not `modImplementation`.
- Loom plugin id is `net.fabricmc.fabric-loom`, pinned to `1.17.17`.
- `org.gradle.configuration-cache=false` is required (fabric-loom#1349).
- Java 25; Gradle 9.5.1; Fabric Loader 0.19.3; Fabric API `0.156.0+26.2`.
- Cloth Config is `me.shedaniel.cloth:cloth-config-fabric:26.2.155`; Mod Menu is `com.terraformersmc:modmenu:20.0.1`. The `+fabric` / `+26.2` suffixes in `SPEC.md` are not part of the Maven coordinates.

Because 26.2 is unobfuscated, Minecraft classes carry **official Mojang names**: `Minecraft`, `LocalPlayer`, `Component`, `Screen`. Note `net.minecraft.resources.Identifier` (not `ResourceLocation`).

When unsure of an API, dump it rather than guessing — the MC client jar and dependency jars can be inspected with `javap` in a throwaway `eclipse-temurin:25-jdk` container.

## Architecture

All code is client-only, in `src/client/java/ru/afknotifier/` (Loom `splitEnvironmentSourceSets()`).

- `AfkNotifierClient` — `ClientModInitializer`; loads config, registers events, logs on startup.
- `ModConfig` — Gson model, `config/afk-notifier.json`, accessed as a singleton via `ModConfig.get()`. `canSend()` gates all sending.
- `Messages` — builds notification text; the wording itself lives in user-editable template files, this only supplies placeholder values and the `dd.MM.yyyy HH:mm:ss` timestamp.
- `templates/MessageTemplate` + `templates/TemplateManager` — see below.
- `telegram/TelegramClient` — Bot API over `java.net.http.HttpClient`, always `sendAsync`. `send()` returns `CompletableFuture<SendResult>` and **never throws** — errors arrive as a `SendResult`. This return value is what the Test button consumes, so do not turn it into fire-and-forget; `sendAndLog()` is the fire-and-forget wrapper for events.
- `events/NotifierEvents` — all event wiring.
- `events/DisconnectReasonHolder` + `mixin/ClientCommonPacketListenerImplMixin` — see below.
- `gui/ModConfigScreen`, `gui/TestButtonEntry`, `gui/ModMenuIntegration`.
- `NotificationToggle` — the single place that flips `config.enabled`, saves, and formats the chat feedback. The chat command and the keybind both go through it; put any new toggle entry point here rather than duplicating the logic.
- `commands/NotificationCommand` — chat toggle, see below.
- `keys/ToggleKeyBinding` — F6 by default.

### Event detection

The mod is client-side, so damage is detected by **diffing `player.getHealth()` across `ClientTickEvents.END_CLIENT_TICK`**, not via server damage events. `previousHealth` is `NaN` until initialized and is reset when the player is null or on (dis)connect. A health *increase* clears the death flag (respawn/heal). Damage messages are throttled by `damageThrottleMs` so poison/fire don't spam. The damage source is not reachable from a tick, so only the HP delta is reported.

**Connect/disconnect use `ClientPlayConnectionEvents`, not `ServerPlayConnectionEvents`.** The spec names the server-side events; those never fire on a client connecting to a remote server.

`ClientPlayConnectionEvents.Disconnect` does not carry the kick reason, so the one mixin in the mod injects at HEAD of `ClientCommonPacketListenerImpl#onDisconnect` and parks the reason in `DisconnectReasonHolder`, which the event handler consumes. This is the only reason a mixin exists — the spec's "try callbacks first" was tried and the callback lacks the data.

### Chat command

`#tgnotification <true|false>` toggles `config.enabled`. It is *not* a real command: `ClientSendMessageEvents.ALLOW_CHAT` intercepts the outgoing chat line and returns `false` so it never reaches the server.

Tab-completion cannot work for a `#` prefix — the chat suggestion machinery is brigadier, which only parses lines starting with `/`. So the same command is *also* registered as a genuine client command via `ClientCommandRegistrationCallback` + `ClientCommands.literal(...)` (note: `ClientCommands`, not the older `ClientCommandManager`), where `BoolArgumentType` supplies `true`/`false` suggestions for free. Both paths call the same `apply(boolean)`. Keep them in sync when changing behaviour.

Chat output goes through `Minecraft.getInstance().gui.chatListener().handleSystemMessage(component, false)` — in 26.2 there is no `Gui.getChat()`, no `ChatComponent.addMessage`, and no `Player.displayClientMessage`.

### Keybinding

The Fabric module is **`fabric-key-mapping-api-v1`**, not the older key-binding one: `net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(...)`. In 26.2 `KeyMapping`'s category argument is a `KeyMapping.Category` object (`Category.MISC`, or `Category.register(Identifier)` for a custom one), not a translation-key string. Presses are polled with `consumeClick()` in `END_CLIENT_TICK` — loop with `while`, not `if`, since it yields one queued press per call.

### Message templates

One plain-text file per event under `config/afk-notifier/templates/`, created on init by `TemplateManager.ensureFiles()`. Placeholders are `{name}`; unknown ones are left verbatim so typos are visible in the delivered message. Every placeholder resolves to a non-empty fallback ("не определена", "неизвестен") so a template line never collapses to a dangling label.

Reads are cached and invalidated by file mtime, so edits apply without a restart. A missing or blank file falls back to `MessageTemplate.defaultText()` — templates must never be able to break sending. `README.txt` in that directory is regenerated on every launch from the enum, so add new placeholders there by editing `MessageTemplate` and `TemplateManager.describe(...)`, not by hand.

The test message is deliberately *not* templated — it must stay recognisable when everything else has been rewritten.

### Escaping for the Telegram API

Nothing in a template needs escaping, and that is a property worth preserving:

- The request body is `x-www-form-urlencoded` and the text goes through `URLEncoder.encode`, so `&`, `=`, `%`, `#`, newlines and emoji cannot corrupt the request.
- **`parse_mode` is deliberately unset.** Text is sent literally, so Markdown/HTML metacharacters (`*`, `_`, `[`, `` ` ``, `<`) are harmless. If anyone ever adds `parse_mode`, user templates immediately become capable of producing `400 can't parse entities`, and escaping must be added in `TelegramClient` at the same time.

`TelegramClient.sanitize()` is the single choke point every send passes through: it strips the text, rejects empty payloads before hitting the network, and truncates at 4096 UTF-16 units (Telegram's limit) without splitting a surrogate pair.

### Config screen

Cloth Config `ConfigBuilder`, saved via `setSavingRunnable`. Cloth has no action-button entry type, so `TestButtonEntry` is a custom `AbstractConfigListEntry<Void>` wrapping a `Button` widget, modelled on Cloth's own `BooleanListEntry`. In 26.2 entries render through `extractRenderState(GuiGraphicsExtractor, …)` and widgets are drawn with `widget.extractRenderState(graphics, mouseX, mouseY, delta)`.

The Test button reads the token/chatId through `Supplier`s bound to the live `StringListEntry` widgets, so it validates what is currently typed rather than what is saved. Results render as a status line inside the entry (`getItemHeight()` is 44 to fit two rows) and are also logged; `Minecraft` exposes no public toast manager accessor in 26.2, so the spec's `SystemToast` route is not used.

## Conventions

- **Code comments in Russian.**
- Lang files live at `src/main/resources/assets/afk-notifier/lang/` — the spec's `src/main/resources/lang/` path is not where Minecraft looks.
- The mod icon is `src/main/resources/assets/afk-notifier/icon.png`, 128×128, referenced from `fabric.mod.json` as `assets/afk-notifier/icon.png`. The full-size source lives in `art/logo.png`, deliberately **outside** `src/main/resources` — everything under that tree is packed into the jar, and the 720×720 original is larger than the rest of the jar combined. Regenerate the icon from the source rather than editing it directly.
- The bot token is entered in-game only; never hardcode or commit it.
- Send failures are logged and never crash the mod or spam in-game chat.
