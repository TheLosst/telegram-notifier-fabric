package ru.afknotifier.gui;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/**
 * База для записей-кнопок на экране настроек.
 *
 * У Cloth Config нет готового элемента «кнопка с произвольным действием»
 * (в gui/entries только поля значений), поэтому реализуем свой поверх
 * AbstractConfigListEntry с виджетом Button.
 *
 * Запись занимает две строки: сверху подпись и кнопка, снизу — строка статуса.
 */
public abstract class ButtonEntry extends AbstractConfigListEntry<Void> {

	protected static final int BUTTON_WIDTH = 150;
	protected static final int COLOR_IDLE = 0xFFAAAAAA;
	protected static final int COLOR_OK = 0xFF55FF55;
	protected static final int COLOR_ERROR = 0xFFFF5555;

	protected final Button button;
	private final List<AbstractWidget> widgets;

	private Component status = Component.empty();
	private int statusColor = COLOR_IDLE;

	protected ButtonEntry(Component fieldName, Component buttonLabel) {
		super(fieldName, false);
		this.button = Button.builder(buttonLabel, b -> onPress())
				.bounds(0, 0, BUTTON_WIDTH, 20)
				.build();
		this.widgets = List.of(this.button);
	}

	/** Что делать по нажатию. */
	protected abstract void onPress();

	protected void setStatus(Component text, int color) {
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
		// Кнопки ничего не меняют в конфиге, «изменёнными» не считаются.
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
