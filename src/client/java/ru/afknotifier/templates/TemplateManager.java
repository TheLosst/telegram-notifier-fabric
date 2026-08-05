package ru.afknotifier.templates;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;
import ru.afknotifier.AfkNotifierClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * Работа с файлами шаблонов: создание при первом запуске, чтение с кэшем
 * и подстановка значений.
 *
 * Файлы лежат в config/afk-notifier/templates/ внутри папки Minecraft.
 * Правки подхватываются на лету — перезаход в игру не нужен: перед каждым
 * чтением сверяется время изменения файла.
 */
public final class TemplateManager {

	/** Что успели прочитать: текст шаблона и время изменения файла на тот момент. */
	private record Cached(String text, long modifiedAt) {
	}

	private static final Map<MessageTemplate, Cached> CACHE = new EnumMap<>(MessageTemplate.class);

	private TemplateManager() {
	}

	public static Path directory() {
		return FabricLoader.getInstance().getConfigDir().resolve("afk-notifier").resolve("templates");
	}

	public static Path fileOf(MessageTemplate template) {
		return directory().resolve(template.fileName());
	}

	/**
	 * Создаёт папку и недостающие файлы шаблонов. Вызывается при старте мода
	 * и повторно перед открытием файла — на случай, если его удалили руками.
	 */
	public static void ensureFiles() {
		try {
			Files.createDirectories(directory());

			for (MessageTemplate template : MessageTemplate.values()) {
				Path file = fileOf(template);
				if (!Files.exists(file)) {
					Files.writeString(file, template.defaultText(), StandardCharsets.UTF_8);
					AfkNotifierClient.LOGGER.info("Создан шаблон {}", file.getFileName());
				}
			}

			// Справку перезаписываем всегда: она не пользовательская,
			// а список подстановок может меняться между версиями мода.
			Files.writeString(directory().resolve("README.txt"), buildReadme(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			AfkNotifierClient.LOGGER.error("Не удалось создать файлы шаблонов", e);
		}
	}

	/** Подставляет значения в шаблон. Ключи — без фигурных скобок. */
	public static String render(MessageTemplate template, Map<String, String> values) {
		String text = read(template);
		for (Map.Entry<String, String> entry : values.entrySet()) {
			text = text.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		return text;
	}

	/** Открывает файл шаблона в редакторе по умолчанию для этой ОС. */
	public static void openInEditor(MessageTemplate template) {
		ensureFiles();
		Util.getPlatform().openPath(fileOf(template));
	}

	/** Открывает саму папку с шаблонами. */
	public static void openDirectory() {
		ensureFiles();
		Util.getPlatform().openPath(directory());
	}

	/**
	 * Читает шаблон, переиспользуя закэшированный текст, пока файл не менялся.
	 * Если файл пропал или не читается — откатываемся на текст по умолчанию,
	 * отправка уведомлений из-за этого не ломается.
	 */
	private static String read(MessageTemplate template) {
		Path file = fileOf(template);

		try {
			if (!Files.exists(file)) {
				return template.defaultText();
			}

			long modifiedAt = Files.getLastModifiedTime(file).toMillis();
			Cached cached = CACHE.get(template);
			if (cached != null && cached.modifiedAt() == modifiedAt) {
				return cached.text();
			}

			String text = Files.readString(file, StandardCharsets.UTF_8).strip();
			if (text.isEmpty()) {
				// Пустой файл — почти наверняка случайность, шлём хоть что-то.
				return template.defaultText();
			}

			CACHE.put(template, new Cached(text, modifiedAt));
			return text;
		} catch (IOException | RuntimeException e) {
			AfkNotifierClient.LOGGER.warn("Не удалось прочитать шаблон {}, беру текст по умолчанию",
					template.fileName(), e);
			return template.defaultText();
		}
	}

	private static String buildReadme() {
		StringBuilder sb = new StringBuilder("""
				Шаблоны сообщений AFK Notifier
				==============================

				Каждый файл — текст одного уведомления. Правьте как обычный текст,
				переносы строк сохраняются. Изменения подхватываются сразу, перезаход
				в игру не требуется.

				В фигурных скобках — подстановки. Неизвестные подстановки остаются
				в тексте как есть, так что опечатку видно прямо в сообщении.

				Если файл удалить, он будет создан заново со стандартным текстом.
				Пустой файл игнорируется — уйдёт стандартный текст.

				Доступные подстановки по файлам:
				""");

		for (MessageTemplate template : MessageTemplate.values()) {
			sb.append('\n').append(template.fileName()).append('\n');
			for (String placeholder : template.placeholders()) {
				sb.append("    {").append(placeholder).append("}  — ")
						.append(describe(placeholder)).append('\n');
			}
		}

		sb.append("""

				Значения, которые не удалось определить, подставляются словами
				«неизвестен» / «не определена» — пустых строк в сообщении не будет.

				Про символы
				-----------
				Экранировать ничего не нужно: текст уходит в Telegram как есть,
				разметка (Markdown/HTML) не включена. Любые символы — &, %, #, *,
				подчёркивания, скобки, эмодзи, переносы строк — безопасны.

				Единственное ограничение — длина: Telegram принимает не больше
				4096 символов на сообщение. Более длинный текст мод обрежет сам
				и поставит в конце многоточие.
				""");
		return sb.toString();
	}

	private static String describe(String placeholder) {
		return switch (placeholder) {
			case "time" -> "дата и время, формат dd.MM.yyyy HH:mm:ss";
			case "damage" -> "сколько HP снято";
			case "hp" -> "текущее здоровье после урона";
			case "maxhp" -> "максимальное здоровье";
			case "deathmessage" -> "текст смерти от игры (Steve was slain by Zombie)";
			case "reason" -> "причина отключения или кика";
			case "server" -> "адрес сервера";
			default -> "";
		};
	}
}
