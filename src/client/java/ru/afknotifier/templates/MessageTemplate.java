package ru.afknotifier.templates;

/**
 * Шаблоны сообщений, которые можно править вручную.
 *
 * Каждому событию соответствует свой файл в config/afk-notifier/templates/.
 * Подстановки записываются в фигурных скобках; список доступных для каждого
 * события — в поле placeholders и в README.txt рядом с файлами.
 */
public enum MessageTemplate {

	DAMAGE("damage.txt", "afk-notifier.template.damage",
			new String[]{"time", "damage", "hp", "maxhp"},
			"""
			🩸 Получен урон
			🕐 {time}
			💥 Урон: {damage} HP
			❤️ Текущее HP: {hp}/{maxhp}"""),

	DEATH("death.txt", "afk-notifier.template.death",
			new String[]{"time", "deathmessage"},
			"""
			💀 Бот для фарма умер
			🕐 {time}
			📜 {deathmessage}"""),

	DISCONNECT("disconnect.txt", "afk-notifier.template.disconnect",
			new String[]{"time", "reason"},
			"""
			🔌 Отключение от сервера
			🕐 {time}
			📋 Причина: {reason}"""),

	CONNECT("connect.txt", "afk-notifier.template.connect",
			new String[]{"time", "server"},
			"""
			✅ Подключение к серверу
			🕐 {time}
			🌐 Сервер: {server}""");

	private final String fileName;
	private final String translationKey;
	private final String[] placeholders;
	private final String defaultText;

	MessageTemplate(String fileName, String translationKey, String[] placeholders, String defaultText) {
		this.fileName = fileName;
		this.translationKey = translationKey;
		this.placeholders = placeholders;
		this.defaultText = defaultText;
	}

	public String fileName() {
		return fileName;
	}

	public String translationKey() {
		return translationKey;
	}

	public String[] placeholders() {
		return placeholders;
	}

	public String defaultText() {
		return defaultText;
	}
}
