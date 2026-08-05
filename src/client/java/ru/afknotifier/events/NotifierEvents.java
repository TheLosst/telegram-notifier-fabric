package ru.afknotifier.events;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import ru.afknotifier.AfkNotifierClient;
import ru.afknotifier.Messages;
import ru.afknotifier.ModConfig;
import ru.afknotifier.telegram.TelegramClient;

/**
 * Обработчики отслеживаемых событий.
 *
 * Мод клиентский, поэтому урон детектим не серверными damage-событиями,
 * а сравнением здоровья игрока между тиками.
 */
public final class NotifierEvents {

	/** Здоровье на прошлом тике. NaN = состояние ещё не инициализировано. */
	private static float previousHealth = Float.NaN;

	/** Чтобы не слать сообщение о смерти каждый тик, пока игрок лежит мёртвый. */
	private static boolean deathReported = false;

	/** Время последнего отправленного сообщения об уроне — для троттлинга. */
	private static long lastDamageSentAt = 0L;

	/** Предупреждение о незаполненных полях пишем в лог один раз, а не на каждое событие. */
	private static boolean credentialsWarningLogged = false;

	private NotifierEvents() {
	}

	public static void register() {
		// Коннект: для клиентского мода нужны именно ClientPlayConnectionEvents.
		// ServerPlayConnectionEvents — серверные, на клиенте они не сработают.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			resetHealthState();
			if (ModConfig.get().notifyOnConnect) {
				send(Messages.connect(serverAddress(client)));
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			resetHealthState();
			if (ModConfig.get().notifyOnDisconnect) {
				send(Messages.disconnect(DisconnectReasonHolder.consume()));
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(NotifierEvents::onEndClientTick);
	}

	private static void onEndClientTick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			resetHealthState();
			return;
		}

		float health = player.getHealth();

		// Первый тик после входа в мир — только запоминаем базовое значение.
		if (Float.isNaN(previousHealth)) {
			previousHealth = health;
			return;
		}

		ModConfig config = ModConfig.get();

		// Смерть.
		if (health <= 0.0f) {
			if (!deathReported) {
				deathReported = true;
				if (config.notifyOnDeath) {
					send(Messages.death(deathMessage(player)));
				}
			}
			previousHealth = health;
			return;
		}

		// Здоровье выросло — лечение или респавн. Сбрасываем флаг смерти.
		if (health > previousHealth) {
			deathReported = false;
			previousHealth = health;
			return;
		}

		// Здоровье упало — это урон.
		if (health < previousHealth) {
			float delta = previousHealth - health;
			previousHealth = health;

			if (!config.notifyOnDamage) {
				return;
			}

			// Троттлинг: DoT-эффекты (яд, огонь) иначе заспамят Telegram.
			long now = System.currentTimeMillis();
			if (now - lastDamageSentAt < config.damageThrottleMs) {
				return;
			}
			lastDamageSentAt = now;

			// Источник урона в тиковой модели недоступен — ограничиваемся дельтой HP.
			send(Messages.damage(delta, health, player.getMaxHealth(), null));
		}
	}

	/** Death message берём из клиентского combat tracker, если он его знает. */
	private static String deathMessage(LocalPlayer player) {
		try {
			return player.getCombatTracker().getDeathMessage().getString();
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static String serverAddress(Minecraft client) {
		ServerData data = client.getCurrentServer();
		return data == null ? null : data.ip;
	}

	private static void resetHealthState() {
		previousHealth = Float.NaN;
		deathReported = false;
	}

	private static void send(String text) {
		ModConfig config = ModConfig.get();
		if (!config.enabled) {
			return;
		}
		if (!config.canSend()) {
			if (!credentialsWarningLogged) {
				credentialsWarningLogged = true;
				AfkNotifierClient.LOGGER.warn(
						"Токен бота или chat_id не заданы — уведомления не отправляются. "
								+ "Заполните их в Mods -> AFK Notifier -> шестерёнка.");
			}
			return;
		}
		credentialsWarningLogged = false;
		TelegramClient.sendAndLog(config.botToken, config.chatId, text);
	}
}
