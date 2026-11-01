package macronode.macro.client.gui;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** Flat dark-theme button with optional accent color and "selected" state (tabs, toggles). */
public class DarkButton extends AbstractButton {
	private final Consumer<DarkButton> action;
	private int accent = Theme.CYAN;
	public boolean selected;

	public DarkButton(int x, int y, int w, int h, Component message, Consumer<DarkButton> action) {
		super(x, y, w, h, message);
		this.action = action;
	}

	public DarkButton accent(int color) {
		this.accent = color;
		return this;
	}

	public DarkButton selected(boolean s) {
		this.selected = s;
		return this;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		action.accept(this);
	}

	@Override
	protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		int x = getX(), y = getY(), w = getWidth(), h = getHeight();
		boolean hot = isHoveredOrFocused() && active;
		int bg;
		if (!active) bg = 0xFF1E1F23;
		else if (selected) bg = Theme.withAlpha(accent, 0x55);
		else bg = hot ? Theme.lighten(Theme.CARD_ALT, 0.08f) : Theme.CARD_ALT;
		g.fill(x, y, x + w, y + h, bg);
		g.renderOutline(x, y, w, h, selected || hot ? accent : Theme.BORDER);
		Font font = Minecraft.getInstance().font;
		String s = Theme.ellipsize(font, getMessage().getString(), w - 6);
		g.drawString(font, s, x + (w - font.width(s)) / 2, y + (h - 8) / 2, active ? Theme.WHITE : Theme.GRAY, false);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
