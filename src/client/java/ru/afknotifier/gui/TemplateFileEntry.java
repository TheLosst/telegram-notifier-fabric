package ru.afknotifier.gui;

import net.minecraft.network.chat.Component;
import ru.afknotifier.AfkNotifierClient;
import ru.afknotifier.templates.MessageTemplate;
import ru.afknotifier.templates.TemplateManager;

/**
 * Кнопка «Открыть файл» для одного шаблона сообщения.
 *
 * Открывает файл в редакторе по умолчанию для текущей ОС. Правки подхватываются
 * сразу — TemplateManager сверяет время изменения файла перед каждым чтением.
 */
public class TemplateFileEntry extends ButtonEntry {

	/** Подтверждение «открыт» живёт недолго — читать его после первой секунды незачем. */
	private static final long CONFIRMATION_TTL_MS = 4000L;

	private final MessageTemplate template;

	public TemplateFileEntry(MessageTemplate template) {
		super(Component.translatable(template.translationKey()),
				Component.translatable("afk-notifier.template.open"));
		this.template = template;
	}

	@Override
	protected void onPress() {
		try {
			TemplateManager.openInEditor(template);
			setStatus(Component.translatable("afk-notifier.template.opened", template.fileName()),
					COLOR_OK, CONFIRMATION_TTL_MS);
		} catch (RuntimeException e) {
			// Если ОС не смогла открыть файл — подсказываем путь, чтобы дойти руками.
			// Ошибку не прячем по таймеру: путь надо успеть прочитать и скопировать.
			AfkNotifierClient.LOGGER.error("Не удалось открыть шаблон {}", template.fileName(), e);
			setStatus(Component.translatable("afk-notifier.template.failed",
					TemplateManager.fileOf(template).toString()), COLOR_ERROR);
		}
	}
}
