package ru.afknotifier.keys;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import ru.afknotifier.NotificationToggle;

/**
 * Хоткей для быстрого вкл/выкл уведомлений.
 *
 * По умолчанию F6 — в ваниле эта клавиша свободна. Переназначается штатно
 * через Настройки -> Управление -> Разное.
 */
public final class ToggleKeyBinding {

	private static KeyMapping toggleKey;

	private ToggleKeyBinding() {
	}

	public static void register() {
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.afk-notifier.toggle",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_F6,
				KeyMapping.Category.MISC));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// consumeClick() отдаёт по одному нажатию за вызов: while, а не if,
			// иначе при нескольких нажатиях за тик часть потеряется.
			while (toggleKey.consumeClick()) {
				NotificationToggle.showInChat(NotificationToggle.toggle());
			}
		});
	}
}
