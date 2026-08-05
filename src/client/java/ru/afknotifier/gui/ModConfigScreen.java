package ru.afknotifier.gui;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.StringListEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.afknotifier.ModConfig;
import ru.afknotifier.templates.MessageTemplate;
import ru.afknotifier.templates.TemplateManager;

/**
 * Экран настроек на Cloth Config. Открывается из Mod Menu.
 */
public final class ModConfigScreen {

	private ModConfigScreen() {
	}

	public static Screen create(Screen parent) {
		ModConfig config = ModConfig.get();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable("afk-notifier.config.title"))
				// Значения применяются в конфиг и пишутся в файл по кнопке Save.
				.setSavingRunnable(config::save);

		ConfigEntryBuilder entries = builder.entryBuilder();

		// --- Основное -------------------------------------------------------
		ConfigCategory general = builder.getOrCreateCategory(
				Component.translatable("afk-notifier.config.category.general"));

		general.addEntry(entries
				.startBooleanToggle(Component.translatable("afk-notifier.config.enabled"), config.enabled)
				.setDefaultValue(true)
				.setSaveConsumer(value -> config.enabled = value)
				.build());

		// Ссылки на поля нужны кнопке Test: она читает то, что введено сейчас,
		// а не то, что уже сохранено в файл.
		StringListEntry tokenEntry = entries
				.startStrField(Component.translatable("afk-notifier.config.botToken"), config.botToken)
				.setDefaultValue("")
				.setTooltip(Component.translatable("afk-notifier.config.botToken.tooltip"))
				.setSaveConsumer(value -> config.botToken = value)
				.build();
		general.addEntry(tokenEntry);

		StringListEntry chatIdEntry = entries
				.startStrField(Component.translatable("afk-notifier.config.chatId"), config.chatId)
				.setDefaultValue("")
				.setTooltip(Component.translatable("afk-notifier.config.chatId.tooltip"))
				.setSaveConsumer(value -> config.chatId = value)
				.build();
		general.addEntry(chatIdEntry);

		general.addEntry(new TestButtonEntry(
				Component.translatable("afk-notifier.config.test"),
				tokenEntry::getValue,
				chatIdEntry::getValue));

		// --- События --------------------------------------------------------
		ConfigCategory events = builder.getOrCreateCategory(
				Component.translatable("afk-notifier.config.category.events"));

		events.addEntry(entries
				.startBooleanToggle(Component.translatable("afk-notifier.config.notifyOnDamage"), config.notifyOnDamage)
				.setDefaultValue(true)
				.setSaveConsumer(value -> config.notifyOnDamage = value)
				.build());

		events.addEntry(entries
				.startBooleanToggle(Component.translatable("afk-notifier.config.notifyOnDeath"), config.notifyOnDeath)
				.setDefaultValue(true)
				.setSaveConsumer(value -> config.notifyOnDeath = value)
				.build());

		events.addEntry(entries
				.startBooleanToggle(Component.translatable("afk-notifier.config.notifyOnDisconnect"), config.notifyOnDisconnect)
				.setDefaultValue(true)
				.setSaveConsumer(value -> config.notifyOnDisconnect = value)
				.build());

		events.addEntry(entries
				.startBooleanToggle(Component.translatable("afk-notifier.config.notifyOnConnect"), config.notifyOnConnect)
				.setDefaultValue(true)
				.setSaveConsumer(value -> config.notifyOnConnect = value)
				.build());

		events.addEntry(entries
				.startIntField(Component.translatable("afk-notifier.config.damageThrottleMs"), config.damageThrottleMs)
				.setDefaultValue(3000)
				.setMin(0)
				.setTooltip(Component.translatable("afk-notifier.config.damageThrottleMs.tooltip"))
				.setSaveConsumer(value -> config.damageThrottleMs = value)
				.build());

		// --- Шаблоны сообщений ----------------------------------------------
		ConfigCategory templates = builder.getOrCreateCategory(
				Component.translatable("afk-notifier.config.category.templates"));

		templates.addEntry(entries
				.startTextDescription(Component.translatable("afk-notifier.template.hint"))
				.build());

		// По кнопке открывается сам файл шаблона в редакторе по умолчанию.
		for (MessageTemplate template : MessageTemplate.values()) {
			templates.addEntry(new TemplateFileEntry(template));
		}

		templates.addEntry(new ButtonEntry(
				Component.translatable("afk-notifier.template.folder"),
				Component.translatable("afk-notifier.template.folder.button")) {
			@Override
			protected void onPress() {
				TemplateManager.openDirectory();
				setStatus(Component.translatable("afk-notifier.template.folder.opened"), COLOR_OK, 4000L);
			}
		});

		return builder.build();
	}
}
