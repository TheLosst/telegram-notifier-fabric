package ru.afknotifier;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Общая логика включения/выключения уведомлений.
 *
 * Сюда ходят все три способа переключения — экран настроек тут ни при чём,
 * а вот команда в чате и хоткей должны вести себя одинаково, поэтому логика
 * лежит в одном месте, а не дублируется.
 */
public final class NotificationToggle {

	private NotificationToggle() {
	}

	/** Ставит значение и сразу пишет его в файл конфига. */
	public static Component apply(boolean enabled) {
		ModConfig config = ModConfig.get();
		config.enabled = enabled;
		config.save();

		if (enabled && (config.botToken.isBlank() || config.chatId.isBlank())) {
			// Включить включили, но отправлять всё равно некуда.
			return prefixed(Component.translatable("afk-notifier.command.on.nocreds")
					.withStyle(ChatFormatting.YELLOW));
		}
		return prefixed(Component
				.translatable(enabled ? "afk-notifier.command.on" : "afk-notifier.command.off")
				.withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
	}

	/** Переключает на противоположное — это то, что делает хоткей. */
	public static Component toggle() {
		return apply(!ModConfig.get().enabled);
	}

	public static Component status() {
		boolean enabled = ModConfig.get().enabled;
		return prefixed(Component.translatable("afk-notifier.command.status",
				Component.translatable(enabled
								? "afk-notifier.command.state.on"
								: "afk-notifier.command.state.off")
						.withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)));
	}

	public static Component prefixed(Component message) {
		return Component.literal("[AFK Notifier] ").withStyle(ChatFormatting.AQUA).append(message);
	}

	/** Вывод в чат только для нас — на сервер ничего не уходит. */
	public static void showInChat(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui != null) {
			client.gui.chatListener().handleSystemMessage(message, false);
		}
	}
}
