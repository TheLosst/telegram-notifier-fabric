package ru.afknotifier;

import ru.afknotifier.templates.MessageTemplate;
import ru.afknotifier.templates.TemplateManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Сборка текстов уведомлений.
 *
 * Сами тексты живут в файлах шаблонов (config/afk-notifier/templates/),
 * здесь только подготовка значений для подстановки. Время — локальное
 * время системы.
 */
public final class Messages {

	private static final DateTimeFormatter FORMAT =
			DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

	private Messages() {
	}

	public static String now() {
		return LocalDateTime.now().format(FORMAT);
	}

	public static String damage(float delta, float current, float max) {
		return TemplateManager.render(MessageTemplate.DAMAGE, Map.of(
				"time", now(),
				"damage", trim(delta),
				"hp", trim(current),
				"maxhp", trim(max)));
	}

	public static String death(String deathMessage) {
		return TemplateManager.render(MessageTemplate.DEATH, Map.of(
				"time", now(),
				"deathmessage", orDefault(deathMessage, "причина неизвестна")));
	}

	public static String disconnect(String reason) {
		return TemplateManager.render(MessageTemplate.DISCONNECT, Map.of(
				"time", now(),
				"reason", orDefault(reason, "не определена")));
	}

	public static String connect(String address) {
		return TemplateManager.render(MessageTemplate.CONNECT, Map.of(
				"time", now(),
				"server", orDefault(address, "неизвестен")));
	}

	/**
	 * Тестовое сообщение намеренно не выносится в шаблон: оно нужно, чтобы
	 * проверить связь, и должно оставаться узнаваемым, даже если пользователь
	 * переписал все остальные тексты.
	 */
	public static String test() {
		return "✅ AFK Notifier: тестовое сообщение. Связь с Telegram работает.\n"
				+ "🕐 " + now();
	}

	/** Пустые значения заменяем словами, иначе в шаблоне повиснет пустая строка. */
	private static String orDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	/** 12.0 -> "12", 12.5 -> "12.5" — чтобы не показывать лишний ноль. */
	private static String trim(float value) {
		if (value == Math.rint(value)) {
			return String.valueOf((long) value);
		}
		return String.valueOf(Math.round(value * 10f) / 10f);
	}
}
