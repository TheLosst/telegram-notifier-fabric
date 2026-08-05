package ru.afknotifier;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.afknotifier.commands.NotificationCommand;
import ru.afknotifier.events.NotifierEvents;

/**
 * Точка входа клиентского мода.
 */
public class AfkNotifierClient implements ClientModInitializer {

	public static final String MOD_ID = "afk-notifier";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		// Читаем конфиг сразу, чтобы файл появился при первом запуске.
		ModConfig.get();
		NotifierEvents.register();
		NotificationCommand.register();

		// Пункт 5 ТЗ: при старте мода пишем в лог, что он загрузился.
		LOGGER.info("AFK Notifier загружен");
	}
}
