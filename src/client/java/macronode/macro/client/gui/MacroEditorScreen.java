package macronode.macro.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import macronode.macro.client.action.ActionBlock;
import macronode.macro.client.action.ActionCategory;
import macronode.macro.client.action.ActionType;
import macronode.macro.client.action.ParamDef;
import macronode.macro.client.action.ParamKind;
import macronode.macro.client.data.GlobalSettings;
import macronode.macro.client.data.MacroProfile;
import macronode.macro.client.data.ProfileStore;
import macronode.macro.client.engine.MacroEngine;
import macronode.macro.client.engine.TimeSpec;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Pathmind / Scratch style node editor ([E]).
 * <ul>
 *   <li>Left: block palette by category. Click = append to chain, Shift+Click = add unlinked node.</li>
 *   <li>Center: pannable canvas. Drag nodes; drag from a node's right port to another node to link;
 *       drop a node next to an output port to snap + auto-link; right-click detaches a node.
 *       Scroll over a node to tweak its inline time/count value.</li>
 *   <li>Right: inspector with typed parameter fields.</li>
 *   <li>Bottom: real-time sequence preview and estimated duration.</li>
 * </ul>
 * Execution order = follow links from the START node.
 */
public class MacroEditorScreen extends Screen {
	private static final int TOP = 24, BOTTOM = 30, PAL_W = 128, INS_W = 158;
	private static final int NODE_W = 118, NODE_H = 34, START_W = 72, ROW = 12;
	private static final int START_ID = -2, NONE = -1;

	private enum Drag { NONE, NODE, LINK, PAN }

	private record Label(String text, int x, int y) {
	}

	/** type == null => category header. */
	private record PaletteEntry(ActionCategory category, ActionType type) {
	}

	private static final List<PaletteEntry> PALETTE = new ArrayList<>();

	static {
		for (ActionCategory c : ActionCategory.values()) {
			PALETTE.add(new PaletteEntry(c, null));
			for (ActionType t : ActionType.values()) if (t.category == c) PALETTE.add(new PaletteEntry(c, t));
		}
	}

	private final Screen parent;
	private MacroProfile profile;
	private double panX = 10, panY = 10;
	private int selected = NONE;
	private Drag drag = Drag.NONE;
	private int dragId, dragOffX, dragOffY, linkFrom;
	private boolean linkElse;
	private int mouseX, mouseY;
	private int paletteScroll;
	private String status = "";
	private int statusColor = Theme.GRAY;
	private long statusUntil;
	private final List<Label> labels = new ArrayList<>();

	public MacroEditorScreen(Screen parent, MacroProfile profile) {
		super(Component.literal("MacroNode Editor"));
		this.parent = parent;
		this.profile = profile;
		ProfileStore.get().settings().lastEditedProfile = profile.id;
	}

	/** Opens the last edited profile (or the first / a fresh one). */
	public static MacroEditorScreen openDefault(Screen parent) {
		ProfileStore store = ProfileStore.get();
		MacroProfile p = store.byId(store.settings().lastEditedProfile);
		if (p == null && !store.profiles().isEmpty()) p = store.profiles().get(0);
		if (p == null) {
			p = new MacroProfile();
			p.name = "My First Macro";
			store.save(p);
		}
		return new MacroEditorScreen(parent, p);
	}

	private static Component lit(String s) {
		return Component.literal(s);
	}

	private void setStatus(String s, int color) {
		status = s;
		statusColor = color;
		statusUntil = System.currentTimeMillis() + 3500;
	}

	// ------------------------------------------------------------------ geometry

	private int cx0() { return PAL_W; }
	private int cy0() { return TOP; }
	private int cx1() { return width - INS_W; }
	private int cy1() { return height - BOTTOM; }
	private int sx(int x) { return cx0() + (int) panX + x; }
	private int sy(int y) { return cy0() + (int) panY + y; }

	private boolean inCanvas(int x, int y) {
		return x >= cx0() && x < cx1() && y >= cy0() && y < cy1();
	}

	private int nodeW(int id) {
		return id == START_ID ? START_W : NODE_W;
	}

	private int nodeX(int id) {
		return id == START_ID ? profile.startX : profile.block(id).x;
	}

	private int nodeY(int id) {
		return id == START_ID ? profile.startY : profile.block(id).y;
	}

	private int nodeAt(int mx, int my) {
		for (int i = profile.blocks.size() - 1; i >= 0; i--) {
			ActionBlock b = profile.blocks.get(i);
			if (mx >= sx(b.x) && mx < sx(b.x) + NODE_W && my >= sy(b.y) && my < sy(b.y) + NODE_H) return b.id;
		}
		if (mx >= sx(profile.startX) && mx < sx(profile.startX) + START_W
				&& my >= sy(profile.startY) && my < sy(profile.startY) + NODE_H) return START_ID;
		return NONE;
	}

	private int outPortAt(int mx, int my) {
		List<Integer> ids = new ArrayList<>();
		ids.add(START_ID);
		for (ActionBlock b : profile.blocks) ids.add(b.id);
		for (int id : ids) {
			int px = sx(nodeX(id)) + nodeW(id) + 2, py = sy(nodeY(id)) + NODE_H / 2;
			if (Math.abs(mx - px) <= 5 && Math.abs(my - py) <= 6) return id;
		}
		return NONE;
	}

	/** If / Else nodes expose a second (red, bottom) output port for the False branch. */
	private int elsePortAt(int mx, int my) {
		for (ActionBlock b : profile.blocks) {
			if (b.actionType() != ActionType.IF_ELSE) continue;
			int px = sx(b.x) + NODE_W / 2, py = sy(b.y) + NODE_H + 2;
			if (Math.abs(mx - px) <= 6 && Math.abs(my - py) <= 5) return b.id;
		}
		return NONE;
	}

	private boolean reachable(int from, int target) {
		return profile.reachableFrom(from).contains(target);
	}

	private PaletteEntry paletteAt(int mx, int my) {
		int top = TOP + 16;
		if (mx >= PAL_W || my < top || my >= height - BOTTOM) return null;
		int idx = (my - top + paletteScroll) / ROW;
		return idx >= 0 && idx < PALETTE.size() ? PALETTE.get(idx) : null;
	}

	// ------------------------------------------------------------------ widgets

	@Override
	protected void init() {
		labels.clear();
		addRenderableWidget(new DarkButton(4, 4, 14, 16, lit("<"), b -> switchProfile(-1)));
		addRenderableWidget(new DarkButton(136, 4, 14, 16, lit(">"), b -> switchProfile(1)));
		int x = 156;
		x = topButton(x, 34, "New", Theme.MINT, this::newProfile);
		x = topButton(x, 52, "Trigger...", Theme.MINT, () -> {
			save();
			minecraft.setScreen(new BindingModalScreen(this, profile, false));
		});
		x = topButton(x, 36, "Save", Theme.CYAN, this::save);
		x = topButton(x, 42, "\u25B6 Test", Theme.MINT, this::test);
		x = topButton(x, 40, "\u25A0 Stop", Theme.RED, () -> MacroEngine.get().stop(profile));
		topButton(x, 44, "Layout", Theme.VIOLET, this::autoLayout);
		addRenderableWidget(new DarkButton(width - 92, 4, 44, 16, lit("Config"), b -> {
			save();
			minecraft.setScreen(new ConfigScreen(parent));
		}).accent(Theme.VIOLET));
		addRenderableWidget(new DarkButton(width - 44, 4, 40, 16, lit("Done"), b -> onClose()));
		buildInspector();
	}

	private int topButton(int x, int w, String label, int accent, Runnable r) {
		addRenderableWidget(new DarkButton(x, 4, w, 16, lit(label), b -> r.run()).accent(accent));
		return x + w + 3;
	}

	private void buildInspector() {
		int ix = width - INS_W + 6, iw = INS_W - 12;
		int y = TOP + 42;
		if (selected == START_ID) {
			labels.add(new Label("Trigger: " + profile.trigger.display(), ix, y));
			y += 12;
			addRenderableWidget(new DarkButton(ix, y, iw, 16, lit("Edit Trigger / Binding"), b -> {
				save();
				minecraft.setScreen(new BindingModalScreen(this, profile, false));
			}).accent(Theme.MINT));
			y += 20;
			addRenderableWidget(new DarkButton(ix, y, iw, 16, lit("Override native: " + (profile.overrideNative ? "ON" : "OFF")), b -> {
				profile.overrideNative = !profile.overrideNative;
				rebuildWidgets();
			}).selected(profile.overrideNative));
			return;
		}
		ActionBlock b = profile.block(selected);
		if (b == null) return;
		ActionType t = b.actionType();
		for (ParamDef d : t.params) {
			if (y > height - BOTTOM - 80) break;
			labels.add(new Label(d.label(), ix, y));
			y += 10;
			if (d.kind() == ParamKind.ENUM || d.kind() == ParamKind.BOOL) {
				addRenderableWidget(new DarkButton(ix, y, iw, 16, lit(b.get(d.key())), btn -> {
					List<String> opts = d.options();
					int i = opts.indexOf(b.get(d.key()));
					String nv = opts.get((i + 1) % opts.size());
					b.params.put(d.key(), nv);
					btn.setMessage(lit(nv));
				}).accent(d.kind() == ParamKind.BOOL ? Theme.MINT : Theme.VIOLET));
			} else {
				EditBox eb = new EditBox(font, ix, y, iw, 16, lit(d.label()));
				eb.setMaxLength(128);
				eb.setValue(b.get(d.key()));
				if (d.kind() == ParamKind.TIME) eb.setTextColor(TimeSpec.isValid(eb.getValue()) ? Theme.MINT : Theme.RED);
				eb.setResponder(v -> {
					b.params.put(d.key(), v);
					if (d.kind() == ParamKind.TIME) eb.setTextColor(TimeSpec.isValid(v) ? Theme.MINT : Theme.RED);
				});
				addRenderableWidget(eb);
			}
			y += 21;
		}
		y += 4;
		int half = (iw - 4) / 2;
		addRenderableWidget(new DarkButton(ix, y, half, 16, lit("Duplicate"), btn -> duplicateSelected()));
		addRenderableWidget(new DarkButton(ix + half + 4, y, half, 16, lit("Detach"), btn -> {
			detach(b);
			setStatus("Detached from chain", Theme.GRAY);
		}));
		y += 20;
		addRenderableWidget(new DarkButton(ix, y, iw, 16, lit("Delete Node [Del]"), btn -> deleteSelected()).accent(Theme.RED));
	}

	// ------------------------------------------------------------------ graph operations

	private void select(int id) {
		if (selected != id) {
			selected = id;
			rebuildWidgets();
		}
	}

	private void addBlock(ActionType t, boolean unlinked) {
		ActionBlock b;
		if (unlinked) {
			b = new ActionBlock(profile.nextBlockId(), t);
			b.x = (cx1() - cx0()) / 2 - (int) panX - NODE_W / 2;
			b.y = (cy1() - cy0()) / 2 - (int) panY - NODE_H / 2;
			profile.blocks.add(b);
		} else {
			b = profile.append(t);
		}
		selected = b.id;
		rebuildWidgets();
		setStatus("Added " + t.displayName + (unlinked ? " (unlinked)" : ""), Theme.MINT);
	}

	/** Removes a block from the chain (predecessor -> successor) but keeps it on the canvas. */
	private void detach(ActionBlock b) {
		if (profile.rootId == b.id) profile.rootId = b.next;
		for (ActionBlock o : profile.blocks) {
			if (o.next == b.id) o.next = b.next;
			if (o.elseNext == b.id) o.elseNext = b.next;
		}
		b.next = NONE;
	}

	private void deleteSelected() {
		if (selected > 0) {
			profile.remove(selected);
			selected = NONE;
			rebuildWidgets();
			setStatus("Node deleted", Theme.GRAY);
		}
	}

	private void duplicateSelected() {
		ActionBlock b = profile.block(selected);
		if (b == null) return;
		ActionBlock c = b.copy();
		c.id = profile.nextBlockId();
		c.next = NONE;
		c.x += 16;
		c.y += NODE_H + 16;
		profile.blocks.add(c);
		select(c.id);
	}

	/** Snap-link used when a node is dropped next to an output port: inserts it after that node. */
	private boolean snapConnect(int from, int to) {
		if (to == START_ID || from == to) return false;
		if (from != START_ID && reachable(to, from)) return false;
		profile.unlinkIncoming(to);
		ActionBlock tb = profile.block(to);
		int old = from == START_ID ? profile.rootId : profile.block(from).next;
		if (from == START_ID) profile.rootId = to;
		else profile.block(from).next = to;
		if (tb.next == NONE && old != NONE && old != to && !reachable(old, to)) tb.next = old;
		return true;
	}

	private void save() {
		ProfileStore.get().save(profile);
		ProfileStore.get().settings().lastEditedProfile = profile.id;
		ProfileStore.get().saveSettings();
		setStatus("Saved '" + profile.name + "'", Theme.MINT);
	}

	private void switchProfile(int dir) {
		List<MacroProfile> list = ProfileStore.get().profiles();
		save();
		if (list.isEmpty()) return;
		int idx = Math.max(0, list.indexOf(profile));
		profile = list.get(Math.floorMod(idx + dir, list.size()));
		ProfileStore.get().settings().lastEditedProfile = profile.id;
		selected = NONE;
		panX = panY = 10;
		rebuildWidgets();
	}

	private void newProfile() {
		save();
		MacroProfile p = new MacroProfile();
		p.name = "Macro " + (ProfileStore.get().profiles().size() + 1);
		ProfileStore.get().save(p);
		profile = p;
		selected = NONE;
		panX = panY = 10;
		rebuildWidgets();
		setStatus("Created '" + p.name + "' - set a trigger with Trigger...", Theme.MINT);
	}

	private void test() {
		save();
		minecraft.setScreen(null);
		MacroEngine.get().start(profile);
	}

	private void autoLayout() {
		List<ActionBlock> chain = profile.chain();
		int perRow = Math.max(1, (cx1() - cx0() - START_W - 60) / (NODE_W + 14));
		for (int i = 0; i < chain.size(); i++) {
			chain.get(i).x = profile.startX + START_W + 20 + (i % perRow) * (NODE_W + 14);
			chain.get(i).y = profile.startY + (i / perRow) * (NODE_H + 26);
		}
		int rows = (chain.size() + perRow - 1) / perRow;
		int k = 0;
		for (ActionBlock b : profile.blocks) {
			if (chain.contains(b)) continue;
			b.x = profile.startX + START_W + 20 + (k % perRow) * (NODE_W + 14);
			b.y = profile.startY + (rows + 1 + k / perRow) * (NODE_H + 26);
			k++;
		}
		panX = panY = 10;
		setStatus("Auto-layout applied", Theme.VIOLET);
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (super.mouseClicked(e, doubleClick)) return true;
		int mx = (int) e.x(), my = (int) e.y();
		PaletteEntry pe = paletteAt(mx, my);
		if (pe != null) {
			if (pe.type() != null && e.button() == 0) addBlock(pe.type(), e.hasShiftDown());
			return true;
		}
		if (!inCanvas(mx, my)) return false;
		setFocused(null);
		if (e.button() == 0) {
			int elsePort = elsePortAt(mx, my);
			if (elsePort != NONE) {
				drag = Drag.LINK;
				linkFrom = elsePort;
				linkElse = true;
				return true;
			}
			int port = outPortAt(mx, my);
			if (port != NONE) {
				drag = Drag.LINK;
				linkFrom = port;
				linkElse = false;
				return true;
			}
			int id = nodeAt(mx, my);
			if (id != NONE) {
				dragId = id;
				dragOffX = mx - sx(nodeX(id));
				dragOffY = my - sy(nodeY(id));
				drag = Drag.NODE;
				select(id);
				return true;
			}
			select(NONE);
		} else if (e.button() == 1) {
			int id = nodeAt(mx, my);
			if (id > 0) {
				detach(profile.block(id));
				setStatus("Detached from chain", Theme.GRAY);
				return true;
			}
		}
		drag = Drag.PAN;
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		switch (drag) {
			case NODE -> {
				int nx = (int) e.x() - dragOffX - cx0() - (int) panX;
				int ny = (int) e.y() - dragOffY - cy0() - (int) panY;
				if (dragId == START_ID) {
					profile.startX = nx;
					profile.startY = ny;
				} else if (profile.block(dragId) != null) {
					profile.block(dragId).x = nx;
					profile.block(dragId).y = ny;
				}
				return true;
			}
			case PAN -> {
				panX += dx;
				panY += dy;
				return true;
			}
			case LINK -> {
				return true;
			}
			default -> {
				return super.mouseDragged(e, dx, dy);
			}
		}
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		Drag d = drag;
		drag = Drag.NONE;
		switch (d) {
			case NODE -> finishNodeDrag();
			case LINK -> finishLink((int) e.x(), (int) e.y());
			case PAN -> {
			}
			default -> {
				return super.mouseReleased(e);
			}
		}
		return true;
	}

	private void finishNodeDrag() {
		GlobalSettings s = ProfileStore.get().settings();
		int g = Math.max(2, s.gridSize);
		if (dragId == START_ID) {
			if (s.snapToGrid) {
				profile.startX = Math.round(profile.startX / (float) g) * g;
				profile.startY = Math.round(profile.startY / (float) g) * g;
			}
			return;
		}
		ActionBlock b = profile.block(dragId);
		if (b == null) return;
		if (s.snapToGrid) {
			b.x = Math.round(b.x / (float) g) * g;
			b.y = Math.round(b.y / (float) g) * g;
		}
		// Magnetic snap: input port close to another node's output port => link + align.
		int inX = b.x, inY = b.y + NODE_H / 2;
		int best = NONE;
		double bestD = 20;
		List<Integer> ids = new ArrayList<>();
		ids.add(START_ID);
		for (ActionBlock o : profile.blocks) if (o.id != b.id) ids.add(o.id);
		for (int id : ids) {
			int ox = nodeX(id) + nodeW(id), oy = nodeY(id) + NODE_H / 2;
			double dist = Math.hypot(ox - inX, oy - inY);
			if (dist < bestD) {
				bestD = dist;
				best = id;
			}
		}
		if (best != NONE) {
			if (snapConnect(best, b.id)) {
				b.x = nodeX(best) + nodeW(best) + 12;
				b.y = nodeY(best);
				setStatus("Snapped & linked", Theme.CYAN);
			} else {
				setStatus("That link would create a loop - use Repeat Loop instead", Theme.RED);
			}
		}
	}

	private void finishLink(int mx, int my) {
		if (linkElse) {
			linkElse = false;
			ActionBlock src = profile.block(linkFrom);
			if (src == null) return;
			int t = nodeAt(mx, my);
			if (t == NONE || t == START_ID || t == linkFrom) {
				src.elseNext = NONE;
				setStatus("Else link removed", Theme.GRAY);
				return;
			}
			if (reachable(t, linkFrom)) {
				setStatus("That link would create a loop - use Repeat Loop instead", Theme.RED);
				return;
			}
			profile.unlinkIncoming(t);
			src.elseNext = t;
			setStatus("False branch linked", Theme.RED);
			return;
		}
		int target = nodeAt(mx, my);
		if (target == NONE || target == START_ID || target == linkFrom) {
			if (linkFrom == START_ID) profile.rootId = NONE;
			else if (profile.block(linkFrom) != null) profile.block(linkFrom).next = NONE;
			setStatus("Link removed", Theme.GRAY);
			return;
		}
		if (linkFrom != START_ID && reachable(target, linkFrom)) {
			setStatus("That link would create a loop - use Repeat Loop instead", Theme.RED);
			return;
		}
		profile.unlinkIncoming(target);
		if (linkFrom == START_ID) profile.rootId = target;
		else profile.block(linkFrom).next = target;
		setStatus("Linked", Theme.CYAN);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int mx = (int) mouseX, my = (int) mouseY;
		if (mx < PAL_W && my > TOP) {
			int max = Math.max(0, PALETTE.size() * ROW - (height - BOTTOM - TOP - 16));
			paletteScroll = Mth.clamp(paletteScroll - (int) (scrollY * ROW * 2), 0, max);
			return true;
		}
		if (inCanvas(mx, my)) {
			int id = nodeAt(mx, my);
			if (id > 0 && adjustInline(profile.block(id), scrollY)) {
				if (id == selected) rebuildWidgets();
				return true;
			}
			panY += scrollY * 20;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** Scroll-wheel editing of the inline value shown on a node. */
	private boolean adjustInline(ActionBlock b, double scrollY) {
		int dir = scrollY > 0 ? 1 : -1;
		ActionType t = b.actionType();
		switch (t) {
			case WAIT_TICKS -> {
				double ticks = b.getNanos("ticks") / (double) TimeSpec.NANOS_PER_TICK;
				double v = Math.max(0, Math.round((ticks + dir * 0.1) * 10) / 10.0);
				b.params.put("ticks", (v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v)) + "t");
				return true;
			}
			case WAIT_MS -> {
				b.params.put("ms", Long.toString(Math.max(0, Math.round(b.getDouble("ms")) + dir * 5L)));
				return true;
			}
			case REPEAT -> {
				b.params.put("times", Integer.toString(Math.max(0, b.getInt("times") + dir)));
				return true;
			}
			case PILLAR -> {
				b.params.put("count", Integer.toString(Math.max(1, b.getInt("count") + dir)));
				return true;
			}
			default -> {
				if (t.param("duration") != null) {
					long ticks = Math.max(0, Math.round(b.getNanos("duration") / (double) TimeSpec.NANOS_PER_TICK) + dir);
					b.params.put("duration", ticks + "t");
					return true;
				}
				return false;
			}
		}
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (getFocused() instanceof EditBox eb && eb.isFocused()) {
			if (e.key() == GLFW.GLFW_KEY_ESCAPE || e.key() == GLFW.GLFW_KEY_ENTER) {
				setFocused(null);
				return true;
			}
			return super.keyPressed(e);
		}
		switch (e.key()) {
			case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> {
				deleteSelected();
				return true;
			}
			case GLFW.GLFW_KEY_S -> {
				if (e.hasControlDown()) {
					save();
					return true;
				}
			}
			case GLFW.GLFW_KEY_D -> {
				if (e.hasControlDown()) {
					duplicateSelected();
					return true;
				}
			}
			default -> {
			}
		}
		return super.keyPressed(e);
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, width, height, Theme.CANVAS);
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float partialTick) {
		mouseX = mx;
		mouseY = my;
		renderCanvas(g, mx, my);
		renderPalette(g, mx, my);
		renderInspector(g);
		renderBars(g);
		super.render(g, mx, my, partialTick);
	}

	private void renderCanvas(GuiGraphics g, int mx, int my) {
		int x0 = cx0(), y0 = cy0(), x1 = cx1(), y1 = cy1();
		g.enableScissor(x0, y0, x1, y1);
		int gs = 20;
		for (int x = x0 + Math.floorMod((int) panX, gs); x < x1; x += gs) g.fill(x, y0, x + 1, y1, Theme.GRID);
		for (int y = y0 + Math.floorMod((int) panY, gs); y < y1; y += gs) g.fill(x0, y, x1, y + 1, Theme.GRID);

		List<ActionBlock> chain = profile.chain();
		Map<Integer, Integer> order = new HashMap<>();
		for (int i = 0; i < chain.size(); i++) order.put(chain.get(i).id, i);

		// Scope bars under loop bodies (violet) and conditional skip ranges (red).
		for (int i = 0; i < chain.size(); i++) {
			ActionBlock c = chain.get(i);
			int n = 0, color = 0;
			if (c.actionType() == ActionType.REPEAT) {
				n = Math.max(1, c.getInt("body"));
				color = Theme.VIOLET;
			} else if ((c.actionType() == ActionType.CONDITION || c.actionType() == ActionType.CHECK_ITEM_COUNT) && "SKIP".equals(c.get("onFalse"))) {
				n = Math.max(0, c.getInt("skip"));
				color = Theme.RED;
			}
			for (int j = i + 1; j <= i + n && j < chain.size(); j++) {
				ActionBlock body = chain.get(j);
				g.fill(sx(body.x), sy(body.y) + NODE_H + 2, sx(body.x) + NODE_W, sy(body.y) + NODE_H + 4, color);
			}
		}

		ActionBlock root = profile.block(profile.rootId);
		if (root != null) link(g, START_ID, root, Theme.CYAN);
		java.util.Set<Integer> live = profile.reachableIds();
		for (ActionBlock b : profile.blocks) {
			ActionBlock n = profile.block(b.next);
			if (n != null) link(g, b.id, n, live.contains(b.id) ? Theme.CYAN : Theme.BORDER);
			ActionBlock e = profile.block(b.elseNext);
			if (e != null) Theme.bezier(g, sx(b.x) + NODE_W / 2, sy(b.y) + NODE_H + 2, sx(e.x) - 2, sy(e.y) + NODE_H / 2,
					live.contains(b.id) ? Theme.RED : Theme.withAlpha(Theme.RED, 0x66));
		}
		if (drag == Drag.LINK) {
			if (linkElse) Theme.bezier(g, sx(nodeX(linkFrom)) + NODE_W / 2, sy(nodeY(linkFrom)) + NODE_H + 2, mx, my, Theme.RED);
			else Theme.bezier(g, sx(nodeX(linkFrom)) + nodeW(linkFrom) + 2, sy(nodeY(linkFrom)) + NODE_H / 2, mx, my, Theme.VIOLET);
		}

		drawStart(g);
		int running = MacroEngine.get().currentBlock(profile.id);
		for (ActionBlock b : profile.blocks) {
			Integer idx = order.get(b.id);
			drawNode(g, b, idx == null ? (live.contains(b.id) ? -2 : -1) : idx, running == b.id);
		}
		if (profile.blocks.isEmpty()) {
			String s = "Click a block on the left to add it  \u00B7  Shift+Click adds an unlinked node";
			g.drawString(font, s, (x0 + x1 - font.width(s)) / 2, (y0 + y1) / 2, Theme.GRAY, false);
		}
		g.disableScissor();
	}

	private void link(GuiGraphics g, int fromId, ActionBlock to, int color) {
		Theme.bezier(g, sx(nodeX(fromId)) + nodeW(fromId) + 2, sy(nodeY(fromId)) + NODE_H / 2,
				sx(to.x) - 2, sy(to.y) + NODE_H / 2, color);
	}

	private void drawStart(GuiGraphics g) {
		int x = sx(profile.startX), y = sy(profile.startY);
		g.fill(x, y, x + START_W, y + NODE_H, selected == START_ID ? Theme.CARD_ALT : Theme.CARD);
		g.fill(x, y, x + START_W, y + 3, Theme.VIOLET);
		g.renderOutline(x, y, START_W, NODE_H, selected == START_ID ? Theme.CYAN : Theme.VIOLET);
		g.drawString(font, "START", x + 5, y + 6, Theme.WHITE, false);
		Theme.tag(g, font, Theme.ellipsize(font, profile.trigger.display(), START_W - 16), x + 5, y + 19, Theme.MINT);
		g.fill(x + START_W, y + NODE_H / 2 - 3, x + START_W + 4, y + NODE_H / 2 + 3, profile.rootId != NONE ? Theme.CYAN : Theme.GRAY);
	}

	private void drawNode(GuiGraphics g, ActionBlock b, int index, boolean running) {
		ActionType t = b.actionType();
		boolean linked = index >= 0 || index == -2; // -2 = reachable only via an If/Else false branch
		int x = sx(b.x), y = sy(b.y), accent = t.category.color;
		g.fill(x, y, x + NODE_W, y + NODE_H, b.id == selected ? Theme.CARD_ALT : Theme.CARD);
		g.fill(x, y, x + NODE_W, y + 3, linked ? accent : Theme.withAlpha(accent, 0x55));
		g.renderOutline(x, y, NODE_W, NODE_H, running ? Theme.MINT : b.id == selected ? Theme.CYAN : Theme.BORDER);
		g.drawString(font, Theme.ellipsize(font, t.displayName, NODE_W - 22), x + 5, y + 6, linked ? Theme.WHITE : Theme.GRAY, false);
		if (index >= 0) {
			String n = Integer.toString(index + 1);
			g.drawString(font, n, x + NODE_W - 4 - font.width(n), y + 6, Theme.GRAY, false);
		}
		String tag = b.inlineTag();
		if (tag != null) Theme.tag(g, font, Theme.ellipsize(font, tag, NODE_W - 18), x + 5, y + 19, Theme.MINT);
		else g.drawString(font, Theme.ellipsize(font, t.category.label, NODE_W - 10), x + 5, y + 21, Theme.withAlpha(Theme.GRAY, 0x88), false);
		g.fill(x - 4, y + NODE_H / 2 - 3, x, y + NODE_H / 2 + 3, linked ? Theme.CYAN : Theme.BORDER);
		boolean hot = drag == Drag.NONE && outPortAt(mouseX, mouseY) == b.id;
		g.fill(x + NODE_W, y + NODE_H / 2 - 3, x + NODE_W + 4, y + NODE_H / 2 + 3,
				hot ? Theme.VIOLET : b.next != NONE ? Theme.CYAN : Theme.GRAY);
		if (t == ActionType.IF_ELSE) {
			int ex = x + NODE_W / 2;
			g.fill(ex - 3, y + NODE_H, ex + 3, y + NODE_H + 4, b.elseNext != NONE ? Theme.RED : Theme.withAlpha(Theme.RED, 0x88));
			g.drawString(font, "T", x + NODE_W + 6, y + NODE_H / 2 - 9, Theme.MINT, false);
			g.drawString(font, "F", ex + 5, y + NODE_H + 1, Theme.RED, false);
		}
	}

	private void renderPalette(GuiGraphics g, int mx, int my) {
		int bottom = height - BOTTOM;
		g.fill(0, TOP, PAL_W, bottom, Theme.CARD);
		g.fill(PAL_W - 1, TOP, PAL_W, bottom, Theme.BORDER);
		g.drawString(font, "BLOCKS", 6, TOP + 4, Theme.CYAN, false);
		int top = TOP + 16;
		g.enableScissor(0, top, PAL_W - 1, bottom);
		PaletteEntry hover = paletteAt(mx, my);
		for (int i = 0; i < PALETTE.size(); i++) {
			int y = top + i * ROW - paletteScroll;
			if (y + ROW < top || y > bottom) continue;
			PaletteEntry e = PALETTE.get(i);
			if (e.type() == null) {
				g.fill(4, y + 3, 7, y + 9, e.category().color);
				g.drawString(font, Theme.ellipsize(font, e.category().label, PAL_W - 14), 10, y + 2, e.category().color, false);
			} else {
				boolean hot = e == hover;
				if (hot) g.fill(2, y, PAL_W - 3, y + ROW, Theme.CARD_ALT);
				g.drawString(font, Theme.ellipsize(font, e.type().displayName, PAL_W - 18), 12, y + 2, hot ? Theme.WHITE : Theme.GRAY, false);
			}
		}
		g.disableScissor();
	}

	private void renderInspector(GuiGraphics g) {
		int x0 = width - INS_W, bottom = height - BOTTOM;
		g.fill(x0, TOP, width, bottom, Theme.CARD);
		g.fill(x0, TOP, x0 + 1, bottom, Theme.BORDER);
		g.drawString(font, "INSPECTOR", x0 + 6, TOP + 4, Theme.CYAN, false);
		ActionBlock b = profile.block(selected);
		if (selected == START_ID) {
			g.drawString(font, "Start Node", x0 + 6, TOP + 18, Theme.WHITE, false);
			g.drawString(font, "Entry point of the macro", x0 + 6, TOP + 28, Theme.VIOLET, false);
		} else if (b != null) {
			g.drawString(font, Theme.ellipsize(font, b.actionType().displayName, INS_W - 12), x0 + 6, TOP + 18, Theme.WHITE, false);
			g.drawString(font, Theme.ellipsize(font, b.actionType().category.label, INS_W - 12), x0 + 6, TOP + 28, b.actionType().category.color, false);
		} else {
			g.drawWordWrap(font, lit("Select a node to edit it.\n\nDrag nodes to move. Drag from a right port onto a node to link it. "
					+ "Drop a node beside a port to snap. Right-click detaches. If/Else: drag its red bottom port to wire the False branch. Scroll a node to tweak its value. "
					+ "Del deletes, Ctrl+D duplicates, Ctrl+S saves. Times: 0t, 0.2t, 10ms, 1s."), x0 + 6, TOP + 18, INS_W - 12, Theme.GRAY, false);
		}
		for (Label l : labels) g.drawString(font, l.text(), l.x(), l.y(), Theme.GRAY, false);
	}

	private void renderBars(GuiGraphics g) {
		g.fill(0, 0, width, TOP, Theme.CARD_ALT);
		g.fill(0, TOP - 1, width, TOP, Theme.BORDER);
		boolean running = MacroEngine.get().isRunning(profile.id);
		g.drawString(font, Theme.ellipsize(font, profile.name, 108), 22, 8, running ? Theme.MINT : Theme.WHITE, false);

		int y = height - BOTTOM;
		g.fill(0, y, width, height, Theme.CARD_ALT);
		g.fill(0, y, width, y + 1, Theme.BORDER);
		g.drawString(font, "Sequence:", 6, y + 5, Theme.CYAN, false);
		int sx0 = 8 + font.width("Sequence:");
		g.drawString(font, Theme.ellipsize(font, profile.chainSummary(), width - sx0 - 8), sx0, y + 5, Theme.GRAY, false);
		int tx = 6;
		g.drawString(font, "Trigger", tx, y + 18, Theme.GRAY, false);
		tx += font.width("Trigger") + 4;
		tx += Theme.tag(g, font, profile.trigger.display(), tx, y + 16, Theme.MINT) + 8;
		String est = "Est. " + estimate() + "  \u00B7  " + profile.chain().size() + " blocks"
				+ (profile.overrideNative ? "  \u00B7  override" : "");
		g.drawString(font, est, tx, y + 18, Theme.GRAY, false);
		if (System.currentTimeMillis() < statusUntil) {
			g.drawString(font, status, width - 6 - font.width(status), y + 18, statusColor, false);
		}
	}

	/** Estimated run time of the chain (waits + holds, loop bodies multiplied). */
	private String estimate() {
		List<ActionBlock> chain = profile.chain();
		long[] d = new long[chain.size()];
		long total = 0;
		for (int i = 0; i < chain.size(); i++) {
			d[i] = duration(chain.get(i));
			total += d[i];
		}
		for (int i = 0; i < chain.size(); i++) {
			ActionBlock b = chain.get(i);
			if (b.actionType() != ActionType.REPEAT) continue;
			int times = b.getInt("times");
			if (times <= 0) return "\u221E";
			long body = 0;
			for (int j = i + 1; j <= i + Math.max(1, b.getInt("body")) && j < chain.size(); j++) body += d[j];
			total += body * (times - 1);
		}
		return TimeSpec.formatNanos(total);
	}

	private static long duration(ActionBlock b) {
		return switch (b.actionType()) {
			case WAIT_TICKS -> b.getNanos("ticks");
			case WAIT_MS -> Math.round(b.getDouble("ms") * TimeSpec.NANOS_PER_MS);
			case USE_ITEM -> "HOLD".equals(b.get("mode")) ? b.getNanos("duration") : 0;
			case SNEAK, SPRINT -> "HOLD".equals(b.get("mode")) ? b.getNanos("duration") : 0;
			case BREAK_BLOCK, AUTO_SHIELD, SMOOTH_TRACK, STRAFE -> b.getNanos("duration");
			case CRYSTAL -> b.getNanos("delay");
			case ANCHOR -> 2 * b.getNanos("delay");
			default -> 0;
		};
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		save();
		minecraft.setScreen(parent);
	}
}
