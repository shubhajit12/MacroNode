package macronode.macro.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Dark UI palette + small drawing helpers shared by every MacroNode screen. Colors are ARGB. */
public final class Theme {
	public static final int CANVAS = 0xFF121214;
	public static final int CARD = 0xFF25262B;
	public static final int CARD_ALT = 0xFF2C2D32;
	public static final int BORDER = 0xFF3A3B40;
	public static final int CYAN = 0xFF06B6D4;
	public static final int VIOLET = 0xFF6366F1;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int GRAY = 0xFFA1A1AA;
	public static final int MINT = 0xFF10B981;
	public static final int RED = 0xFFEF4444;
	public static final int GRID = 0xFF1A1A1E;
	public static final int SHADE = 0xB0000000;

	private Theme() {
	}

	public static void panel(GuiGraphics g, int x, int y, int w, int h, int fill) {
		g.fill(x, y, x + w, y + h, fill);
		g.renderOutline(x, y, w, h, BORDER);
	}

	public static void panel(GuiGraphics g, int x, int y, int w, int h) {
		panel(g, x, y, w, h, CARD);
	}

	/** Rounded-ish "pill" tag, e.g. key tags in mint green. Returns width drawn. */
	public static int tag(GuiGraphics g, Font font, String text, int x, int y, int color) {
		int w = font.width(text) + 6;
		g.fill(x, y, x + w, y + 11, withAlpha(color, 0x33));
		g.renderOutline(x, y, w, 11, withAlpha(color, 0xAA));
		g.drawString(font, text, x + 3, y + 2, color, false);
		return w;
	}

	public static int withAlpha(int argb, int alpha) {
		return (alpha << 24) | (argb & 0x00FFFFFF);
	}

	public static int lighten(int argb, float f) {
		int a = argb >>> 24, r = (argb >> 16) & 0xFF, gr = (argb >> 8) & 0xFF, b = argb & 0xFF;
		r = Math.min(255, (int) (r + (255 - r) * f));
		gr = Math.min(255, (int) (gr + (255 - gr) * f));
		b = Math.min(255, (int) (b + (255 - b) * f));
		return (a << 24) | (r << 16) | (gr << 8) | b;
	}

	public static String ellipsize(Font font, String s, int maxW) {
		if (font.width(s) <= maxW) return s;
		return font.plainSubstrByWidth(s, Math.max(0, maxW - font.width("..."))) + "...";
	}

	/** Draws a smooth cubic Bezier connector between two ports using tiny quads. */
	public static void bezier(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
		double dx = Math.max(30, Math.abs(x2 - x1) * 0.5);
		double cx1 = x1 + dx, cy1 = y1, cx2 = x2 - dx, cy2 = y2;
		double len = Math.hypot(x2 - x1, y2 - y1) + dx;
		int steps = (int) Math.max(12, Math.min(400, len / 1.5));
		for (int i = 0; i <= steps; i++) {
			double t = i / (double) steps, u = 1 - t;
			double px = u * u * u * x1 + 3 * u * u * t * cx1 + 3 * u * t * t * cx2 + t * t * t * x2;
			double py = u * u * u * y1 + 3 * u * u * t * cy1 + 3 * u * t * t * cy2 + t * t * t * y2;
			int ix = (int) Math.round(px), iy = (int) Math.round(py);
			g.fill(ix - 1, iy - 1, ix + 1, iy + 1, color);
		}
	}
}
