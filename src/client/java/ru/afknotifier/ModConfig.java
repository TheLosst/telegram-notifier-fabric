package ru.afknotifier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Модель настроек мода + загрузка/сохранение в config/afk-notifier.json.
 *
 * Файл редактируется через экран Mod Menu, руками в него лезть не нужно.
 */
public class ModConfig {

	/** Глобальный вкл/выкл. */
	public boolean enabled = true;

	/** Токен Telegram-бота (@BotFather). Вводится в игре, в коде не хардкодится. */
	public String botToken = "";

	/** id чата/пользователя. Строка: для групп chat_id бывает отрицательным. */
	public String chatId = "";

	public boolean notifyOnDamage = true;
	public boolean notifyOnDeath = true;
	public boolean notifyOnDisconnect = true;
	public boolean notifyOnConnect = true;

	/** Троттлинг сообщений об уроне, мс — чтобы яд/огонь не заспамили Telegram. */
	public int damageThrottleMs = 3000;

	// --- инфраструктура -----------------------------------------------------

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ModConfig instance;

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("afk-notifier.json");
	}

	public static ModConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static ModConfig load() {
		Path file = path();
		if (Files.exists(file)) {
			try {
				String json = Files.readString(file, StandardCharsets.UTF_8);
				ModConfig loaded = GSON.fromJson(json, ModConfig.class);
				if (loaded != null) {
					return loaded;
				}
			} catch (IOException | RuntimeException e) {
				// Битый конфиг не должен ронять мод — просто откатываемся на дефолты.
				AfkNotifierClient.LOGGER.warn("Не удалось прочитать конфиг, беру значения по умолчанию", e);
			}
		}
		return new ModConfig();
	}

	/** Вызывается из setSavingRunnable экрана Cloth Config. */
	public void save() {
		try {
			Path file = path();
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			AfkNotifierClient.LOGGER.error("Не удалось сохранить конфиг", e);
		}
	}

	/** Отправка возможна только если мод включён и заполнены оба поля. */
	public boolean canSend() {
		return enabled && !botToken.isBlank() && !chatId.isBlank();
	}
}
