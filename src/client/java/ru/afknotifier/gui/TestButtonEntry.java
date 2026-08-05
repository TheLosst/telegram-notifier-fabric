package ru.afknotifier.gui;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import ru.afknotifier.AfkNotifierClient;
import ru.afknotifier.Messages;
import ru.afknotifier.telegram.TelegramClient;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Кнопка «Отправить тестовое сообщение» на экране настроек.
 *
 * У Cloth Config нет готового элемента-кнопки с произвольным действием
 * (в gui/entries есть только поля значений), поэтому реализуем собственный
 * AbstractConfigListEntry с виджетом Button внутри. Значения он берёт не из
 * сохранённого конфига, а через Supplier'ы прямо из полей экрана — чтобы связку
 * токен+chat_id можно было проверить до нажатия Save.
 *
 * Запись занимает две строки: сверху подпись и кнопка, снизу — статус ответа.
 */
public class TestButtonEntry extends AbstractConfigListEntry<Void> {

	private static final int BUTTON_WIDTH = 150;
	private static final int COLOR_IDLE = 0xFFAAAAAA;
	private static final int COLOR_OK = 0xFF55FF55;
	private static final int COLOR_ERROR = 0xFFFF5555;

	private final Button button;
	private final List<AbstractWidget> widgets;
	private final Supplier<String> tokenSupplier;
	private final Supplier<String> chatIdSupplier;

	private Component status = Component.empty();
	private int statusColor = COLOR_IDLE;

	public TestButtonEntry(Component fieldName, Supplier<String> tokenSupplier, Supplier<String> chatIdSupplier) {
		super(fieldName, false);
		this.tokenSupplier = tokenSupplier;
		this.chatIdSupplier = chatIdSupplier;
		this.button = Button.builder(Component.translatable("afk-notifier.config.test.button"), b -> runTest())
				.bounds(0, 0, BUTTON_WIDTH, 20)
				.build();
		this.widgets = List.of(this.button);
	}

	private void runTest() {
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

	private void setStatus(Component text, int color) {
		this.status = text;
		this.statusColor = color;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int index, int y, int x, int entryWidth,
	                               int entryHeight, int mouseX, int mouseY, boolean isHovered, float delta) {
		super.extractRenderState(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);

		var font = Minecraft.getInstance().font;
		graphics.text(font, getDisplayedFieldName(), x, y + 6, getPreferredTextColor());

		button.setX(x + entryWidth - BUTTON_WIDTH);
		button.setY(y);
		button.extractRenderState(graphics, mouseX, mouseY, delta);

		if (status != Component.empty()) {
			graphics.text(font, status, x, y + 26, statusColor);
		}
	}

	@Override
	public Void getValue() {
		return null;
	}

	@Override
	public Optional<Void> getDefaultValue() {
		return Optional.empty();
	}

	@Override
	public int getItemHeight() {
		// Две строки: кнопка и статус под ней.
		return 44;
	}

	@Override
	public boolean isEdited() {
		// Кнопка ничего не меняет в конфиге, поэтому «изменённой» не считается.
		return false;
	}

	@Override
	public List<? extends GuiEventListener> children() {
		return widgets;
	}

	@Override
	public List<? extends NarratableEntry> narratables() {
		return widgets;
	}
}
