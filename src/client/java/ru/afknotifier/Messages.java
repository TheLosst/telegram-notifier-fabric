package ru.afknotifier;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Шаблоны сообщений в Telegram. Время — локальное время системы.
 */
public final class Messages {

	private static final DateTimeFormatter FORMAT =
			DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

	private Messages() {
	}

	public static String now() {
		return LocalDateTime.now().format(FORMAT);
	}

	/** Урон. Источник добавляем только если он известен. */
	public static String damage(float delta, float current, float max, String source) {
		StringBuilder sb = new StringBuilder()
				.append("🩸 Получен урон\n")
				.append("🕐 ").append(now()).append('\n')
				.append("💥 Урон: ").append(trim(delta)).append(" HP\n")
				.append("❤️ Текущее HP: ").append(trim(current)).append('/').append(trim(max));
		if (source != null && !source.isBlank()) {
			sb.append("\n☠️ Источник: ").append(source);
		}
		return sb.toString();
	}

	/** Смерть. Death message добавляем, если клиент его знает. */
	public static String death(String deathMessage) {
		StringBuilder sb = new StringBuilder()
				.append("💀 Бот для фарма умер\n")
				.append("🕐 ").append(now());
		if (deathMessage != null && !deathMessage.isBlank()) {
			sb.append("\n📜 ").append(deathMessage);
		}
		return sb.toString();
	}

	public static String disconnect(String reason) {
		return "🔌 Отключение от сервера\n"
				+ "🕐 " + now() + "\n"
				+ "📋 Причина: " + (reason == null || reason.isBlank() ? "не определена" : reason);
	}

	public static String connect(String address) {
		return "✅ Подключение к серверу\n"
				+ "🕐 " + now() + "\n"
				+ "🌐 Сервер: " + (address == null || address.isBlank() ? "неизвестен" : address);
	}

	public static String test() {
		return "✅ AFK Notifier: тестовое сообщение. Связь с Telegram работает.\n"
				+ "🕐 " + now();
	}

	/** 12.0 -> "12", 12.5 -> "12.5" — чтобы не показывать лишний ноль. */
	private static String trim(float value) {
		if (value == Math.rint(value)) {
			return String.valueOf((long) value);
		}
		return String.valueOf(Math.round(value * 10f) / 10f);
	}
}
