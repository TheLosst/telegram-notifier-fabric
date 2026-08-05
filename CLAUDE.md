# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Client-side Fabric mod for Minecraft **26.2**. Watches the local player on an AFK farm and sends Telegram notifications on damage, death, connect and disconnect. `readme.md` is the original Russian spec (ТЗ); `USAGE.md` is the build/usage guide.

## Build — Docker only

**The host has no JDK and no Gradle.** Never suggest running `./gradlew` or `java` directly on the host.

```bash
./build.sh          # → build/libs/afk-notifier-1.0.0.jar
```

`build.sh` bootstraps the Gradle Wrapper into the repo if `gradlew`/`gradle-wrapper.jar` are missing (one-off `gradle:9.5.1-jdk25` container, run in an *empty* dir — running `gradle wrapper` in the project root fails because it tries to configure the Loom build before the wrapper exists). Then it builds the image (`./gradlew build --no-daemon` runs inside) and copies the jar out via a bind-mount on `/out`.

On Windows/Git Bash, bind-mount paths must be Windows-style and `MSYS_NO_PATHCONV=1` must be set — `build.sh` handles this via `cygpath -m`.

Only the build runs in Docker. The mod itself is never run in a container.

There is no test framework.

## Version constraints (MC 26.2) — all verified against Maven, not memory

- **26.2 is not obfuscated, so no mappings are declared at all.** Do not add Yarn, and do *not* add `loom.officialMojangMappings()` — Loom fails with `Failed to find official mojang mappings for 26.2`. The `dependencies` block has no `mappings` line. (`readme.md` says to use official Mojang mappings; that is wrong for 26.2.)
- For the same reason there is nothing to remap, so deps use plain **`implementation`**, not `modImplementation`.
- Loom plugin id is `net.fabricmc.fabric-loom`, pinned to `1.17.17`.
- `org.gradle.configuration-cache=false` is required (fabric-loom#1349).
- Java 25; Gradle 9.5.1; Fabric Loader 0.19.3; Fabric API `0.156.0+26.2`.
- Cloth Config is `me.shedaniel.cloth:cloth-config-fabric:26.2.155`; Mod Menu is `com.terraformersmc:modmenu:20.0.1`. The `+fabric` / `+26.2` suffixes in `readme.md` are not part of the Maven coordinates.

Because 26.2 is unobfuscated, Minecraft classes carry **official Mojang names**: `Minecraft`, `LocalPlayer`, `Component`, `Screen`. Note `net.minecraft.resources.Identifier` (not `ResourceLocation`).

When unsure of an API, dump it rather than guessing — the MC client jar and dependency jars can be inspected with `javap` in a throwaway `eclipse-temurin:25-jdk` container.

## Architecture

All code is client-only, in `src/client/java/ru/afknotifier/` (Loom `splitEnvironmentSourceSets()`).

- `AfkNotifierClient` — `ClientModInitializer`; loads config, registers events, logs on startup.
- `ModConfig` — Gson model, `config/afk-notifier.json`, accessed as a singleton via `ModConfig.get()`. `canSend()` gates all sending.
- `Messages` — Telegram message templates (emoji, `dd.MM.yyyy HH:mm:ss` local time).
- `telegram/TelegramClient` — Bot API over `java.net.http.HttpClient`, always `sendAsync`. `send()` returns `CompletableFuture<SendResult>` and **never throws** — errors arrive as a `SendResult`. This return value is what the Test button consumes, so do not turn it into fire-and-forget; `sendAndLog()` is the fire-and-forget wrapper for events.
- `events/NotifierEvents` — all event wiring.
- `events/DisconnectReasonHolder` + `mixin/ClientCommonPacketListenerImplMixin` — see below.
- `gui/ModConfigScreen`, `gui/TestButtonEntry`, `gui/ModMenuIntegration`.
- `commands/NotificationCommand` — chat toggle for `config.enabled`, see below.

### Event detection

The mod is client-side, so damage is detected by **diffing `player.getHealth()` across `ClientTickEvents.END_CLIENT_TICK`**, not via server damage events. `previousHealth` is `NaN` until initialized and is reset when the player is null or on (dis)connect. A health *increase* clears the death flag (respawn/heal). Damage messages are throttled by `damageThrottleMs` so poison/fire don't spam. The damage source is not reachable from a tick, so only the HP delta is reported.

**Connect/disconnect use `ClientPlayConnectionEvents`, not `ServerPlayConnectionEvents`.** The spec names the server-side events; those never fire on a client connecting to a remote server.

`ClientPlayConnectionEvents.Disconnect` does not carry the kick reason, so the one mixin in the mod injects at HEAD of `ClientCommonPacketListenerImpl#onDisconnect` and parks the reason in `DisconnectReasonHolder`, which the event handler consumes. This is the only reason a mixin exists — the spec's "try callbacks first" was tried and the callback lacks the data.

### Chat command

`#tgnotification <true|false>` toggles `config.enabled`. It is *not* a real command: `ClientSendMessageEvents.ALLOW_CHAT` intercepts the outgoing chat line and returns `false` so it never reaches the server.

Tab-completion cannot work for a `#` prefix — the chat suggestion machinery is brigadier, which only parses lines starting with `/`. So the same command is *also* registered as a genuine client command via `ClientCommandRegistrationCallback` + `ClientCommands.literal(...)` (note: `ClientCommands`, not the older `ClientCommandManager`), where `BoolArgumentType` supplies `true`/`false` suggestions for free. Both paths call the same `apply(boolean)`. Keep them in sync when changing behaviour.

Chat output goes through `Minecraft.getInstance().gui.chatListener().handleSystemMessage(component, false)` — in 26.2 there is no `Gui.getChat()`, no `ChatComponent.addMessage`, and no `Player.displayClientMessage`.

### Config screen

Cloth Config `ConfigBuilder`, saved via `setSavingRunnable`. Cloth has no action-button entry type, so `TestButtonEntry` is a custom `AbstractConfigListEntry<Void>` wrapping a `Button` widget, modelled on Cloth's own `BooleanListEntry`. In 26.2 entries render through `extractRenderState(GuiGraphicsExtractor, …)` and widgets are drawn with `widget.extractRenderState(graphics, mouseX, mouseY, delta)`.

The Test button reads the token/chatId through `Supplier`s bound to the live `StringListEntry` widgets, so it validates what is currently typed rather than what is saved. Results render as a status line inside the entry (`getItemHeight()` is 44 to fit two rows) and are also logged; `Minecraft` exposes no public toast manager accessor in 26.2, so the spec's `SystemToast` route is not used.

## Conventions

- **Code comments in Russian.**
- Lang files live at `src/main/resources/assets/afk-notifier/lang/` — the spec's `src/main/resources/lang/` path is not where Minecraft looks.
- The bot token is entered in-game only; never hardcode or commit it.
- Send failures are logged and never crash the mod or spam in-game chat.
