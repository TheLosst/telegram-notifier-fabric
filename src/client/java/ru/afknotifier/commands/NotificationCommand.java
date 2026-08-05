package ru.afknotifier.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import ru.afknotifier.ModConfig;

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
							ctx.getSource().sendFeedback(status());
							return 1;
						})
						.then(ClientCommands.argument("enabled", BoolArgumentType.bool())
								.executes(ctx -> {
									boolean value = BoolArgumentType.getBool(ctx, "enabled");
									ctx.getSource().sendFeedback(apply(value));
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
			feedback(status());
			feedback(usage());
			return false;
		}

		String argument = parts[1].toLowerCase(Locale.ROOT);
		switch (argument) {
			case "true" -> feedback(apply(true));
			case "false" -> feedback(apply(false));
			default -> {
				feedback(Component.translatable("afk-notifier.command.unknown", parts[1])
						.withStyle(ChatFormatting.RED));
				feedback(usage());
			}
		}
		return false;
	}

	/** Применяет значение и сразу пишет его в файл конфига. */
	private static Component apply(boolean enabled) {
		ModConfig config = ModConfig.get();
		config.enabled = enabled;
		config.save();

		if (enabled && (config.botToken.isBlank() || config.chatId.isBlank())) {
			// Включить-то включили, но отправлять всё равно некуда.
			return prefixed(Component.translatable("afk-notifier.command.on.nocreds")
					.withStyle(ChatFormatting.YELLOW));
		}
		return prefixed(Component
				.translatable(enabled ? "afk-notifier.command.on" : "afk-notifier.command.off")
				.withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
	}

	private static Component status() {
		boolean enabled = ModConfig.get().enabled;
		return prefixed(Component.translatable("afk-notifier.command.status",
				Component.translatable(enabled ? "afk-notifier.command.state.on" : "afk-notifier.command.state.off")
						.withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)));
	}

	private static Component usage() {
		return prefixed(Component.translatable("afk-notifier.command.usage", CHAT_PREFIX)
				.withStyle(ChatFormatting.GRAY));
	}

	private static Component prefixed(Component message) {
		return Component.literal("[AFK Notifier] ").withStyle(ChatFormatting.AQUA).append(message);
	}

	/** Вывод в чат только для нас — на сервер ничего не уходит. */
	private static void feedback(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui != null) {
			client.gui.chatListener().handleSystemMessage(message, false);
		}
	}
}
