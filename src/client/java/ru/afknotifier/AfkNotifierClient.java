package ru.afknotifier;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.afknotifier.commands.NotificationCommand;
import ru.afknotifier.events.NotifierEvents;
import ru.afknotifier.keys.ToggleKeyBinding;
import ru.afknotifier.templates.TemplateManager;

/**
 * Точка входа клиентского мода.
 */
public class AfkNotifierClient implements ClientModInitializer {

	public static final String MOD_ID = "afk-notifier";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		// Читаем конфиг и раскладываем шаблоны сразу, чтобы файлы появились
		// при первом запуске, а не при первом событии.
		ModConfig.get();
		TemplateManager.ensureFiles();
		NotifierEvents.register();
		NotificationCommand.register();
		ToggleKeyBinding.register();

		// Пункт 5 ТЗ: при старте мода пишем в лог, что он загрузился.
		LOGGER.info("AFK Notifier загружен");
	}
}
