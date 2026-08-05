package ru.afknotifier.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import ru.afknotifier.NotificationToggle;

import java.util.Locale;

/**
 * Быстрое вкл/выкл уведомлений, не заходя в настройки.
 *
 * Доступно двумя способами:
 *   #tgnotification true|false — перехват чата (сообщение на сервер не уходит);
 *   /tgnotification true|false — обычная клиентская команда с автодополнением.
 *
 * Автодополнение (подсказки по Tab) работает только у варианта со слэшем:
 * подсказки в чате строит brigadier, а он разбирает лишь строки, начинающиеся
 * с «/». Для «#» вместо этого печатаем подсказку по использованию.
 *
 * Сама логика переключения — в NotificationToggle, общая с хоткеем.
 */
public final class NotificationCommand {

	/** Имя команды без префикса. */
	private static final String NAME = "tgnotification";

	/** Как команда выглядит в чате. */
	private static final String CHAT_PREFIX = "#" + NAME;

	private NotificationCommand() {
	}

	public static void register() {
		// Вариант со слэшем: brigadier сам подсказывает true/false по Tab.
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
				dispatcher.register(ClientCommands.literal(NAME)
						.executes(ctx -> {
							ctx.getSource().sendFeedback(NotificationToggle.status());
							return 1;
						})
						.then(ClientCommands.argument("enabled", BoolArgumentType.bool())
								.executes(ctx -> {
									boolean value = BoolArgumentType.getBool(ctx, "enabled");
									ctx.getSource().sendFeedback(NotificationToggle.apply(value));
									return 1;
								}))));

		// Вариант с решёткой: перехватываем исходящее сообщение чата.
		ClientSendMessageEvents.ALLOW_CHAT.register(NotificationCommand::onChatMessage);
	}

	/**
	 * @return false — сообщение наше, обрабатываем локально и на сервер не шлём.
	 */
	private static boolean onChatMessage(String message) {
		String[] parts = message.trim().split("\\s+");
		if (!parts[0].equalsIgnoreCase(CHAT_PREFIX)) {
			return true;
		}

		if (parts.length == 1) {
			// Голая команда — показываем текущее состояние и как её писать.
			NotificationToggle.showInChat(NotificationToggle.status());
			NotificationToggle.showInChat(usage());
			return false;
		}

		String argument = parts[1].toLowerCase(Locale.ROOT);
		switch (argument) {
			case "true" -> NotificationToggle.showInChat(NotificationToggle.apply(true));
			case "false" -> NotificationToggle.showInChat(NotificationToggle.apply(false));
			default -> {
				NotificationToggle.showInChat(NotificationToggle.prefixed(
						Component.translatable("afk-notifier.command.unknown", parts[1])
								.withStyle(ChatFormatting.RED)));
				NotificationToggle.showInChat(usage());
			}
		}
		return false;
	}

	private static Component usage() {
		return NotificationToggle.prefixed(
				Component.translatable("afk-notifier.command.usage", CHAT_PREFIX)
						.withStyle(ChatFormatting.GRAY));
	}
}
