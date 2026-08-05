package ru.afknotifier.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import ru.afknotifier.AfkNotifierClient;
import ru.afknotifier.Messages;
import ru.afknotifier.telegram.TelegramClient;

import java.util.function.Supplier;

/**
 * Кнопка «Отправить тестовое сообщение».
 *
 * Значения берёт не из сохранённого конфига, а через Supplier'ы прямо из полей
 * экрана — чтобы связку токен+chat_id можно было проверить до нажатия Save.
 */
public class TestButtonEntry extends ButtonEntry {

	private final Supplier<String> tokenSupplier;
	private final Supplier<String> chatIdSupplier;

	public TestButtonEntry(Component fieldName, Supplier<String> tokenSupplier, Supplier<String> chatIdSupplier) {
		super(fieldName, Component.translatable("afk-notifier.config.test.button"));
		this.tokenSupplier = tokenSupplier;
		this.chatIdSupplier = chatIdSupplier;
	}

	@Override
	protected void onPress() {
		String token = tokenSupplier.get();
		String chatId = chatIdSupplier.get();

		// Пустые поля — запрос не шлём вообще.
		if (token == null || token.isBlank() || chatId == null || chatId.isBlank()) {
			setStatus(Component.translatable("afk-notifier.config.test.empty"), COLOR_ERROR);
			return;
		}

		// Защита от даблкликов: пока запрос в полёте, кнопка недоступна.
		button.active = false;
		setStatus(Component.translatable("afk-notifier.config.test.sending"), COLOR_IDLE);

		TelegramClient.send(token, chatId, Messages.test()).thenAccept(result ->
				// Возвращаемся в клиентский поток: трогаем состояние виджета.
				Minecraft.getInstance().execute(() -> {
					button.active = true;
					if (result.ok()) {
						AfkNotifierClient.LOGGER.info("Тест Telegram пройден");
						setStatus(Component.translatable("afk-notifier.config.test.ok"), COLOR_OK);
					} else {
						AfkNotifierClient.LOGGER.warn("Тест Telegram не пройден: HTTP {} — {}",
								result.httpCode(), result.description());
						setStatus(Component.translatable("afk-notifier.config.test.fail",
								result.httpCode(), result.description()), COLOR_ERROR);
					}
				}));
	}
}
