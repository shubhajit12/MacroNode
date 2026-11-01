package macronode.macro.client.gui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import macronode.macro.client.data.GlobalSettings;
import macronode.macro.client.data.MacroProfile;
import macronode.macro.client.data.PresetRegistry;
import macronode.macro.client.data.ProfileCodec;
import macronode.macro.client.data.ProfileStore;
import macronode.macro.client.engine.MacroEngine;
import macronode.macro.client.recorder.MacroRecorder;
import macronode.macro.client.recorder.RecordGenerator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * Config Menu hub ([O]): profile manager, official presets, global settings and import/export
 * (Base64 share codes via clipboard, .json files via the import modal).
 */
public class ConfigScreen extends Screen {
	private enum Tab {
		PROFILES("Profiles"), PRESETS("Presets"), SETTINGS("Settings"), TRANSFER("Import / Export"), RECORD("Record");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private record Row(MacroProfile profile, int x, int y, int w, int h) {
	}

	private record Card(PresetRegistry.Preset preset, int x, int y, int w, int h) {
	}

	private static final int ROW_H = 44;
	private static Tab lastTab = Tab.PROFILES;

	private final Screen parent;
	private Tab tab = lastTab;
	private int scroll;
	private String pendingDelete;
	private boolean importModal;
	private int importScroll;
	private String status = "";
	private int statusColor = Theme.GRAY;
	private long statusUntil;

	private int px, py, pw, ph, cx, cy, cw, ch;
	private int mx0, my0, mw, mh;
	private final List<Row> rows = new ArrayList<>();
	private final List<Card> cards = new ArrayList<>();

	public ConfigScreen(Screen parent) {
		super(Component.literal("MacroNode Config"));
		this.parent = parent;
	}

	private static Component lit(String s) {
		return Component.literal(s);
	}

	private static String onOff(boolean b) {
		return b ? "ON" : "OFF";
	}

	private void setStatus(String s, int color) {
		status = s;
		statusColor = color;
		statusUntil = System.currentTimeMillis() + 4000;
	}

	@Override
	protected void init() {
		pw = Math.min(width - 20, 480);
		ph = Math.min(height - 20, 290);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		cx = px + 114;
		cy = py + 30;
		cw = pw - 120;
		ch = ph - 46;
		rows.clear();
		cards.clear();
		if (importModal) {
			initImportModal();
			return;
		}
		int ty = py + 30;
		for (Tab t : Tab.values()) {
			addRenderableWidget(new DarkButton(px + 6, ty, 100, 18, lit(t.label), b -> {
				tab = t;
				lastTab = t;
				scroll = 0;
				pendingDelete = null;
				rebuildWidgets();
			}).selected(t == tab));
			ty += 22;
		}
		addRenderableWidget(new DarkButton(px + 6, py + ph - 48, 100, 18, lit("Macro Editor [E]"),
				b -> minecraft.setScreen(MacroEditorScreen.openDefault(this))).accent(Theme.VIOLET));
		addRenderableWidget(new DarkButton(px + 6, py + ph - 26, 100, 18, lit("Close"), b -> onClose()));
		switch (tab) {
			case PROFILES -> initProfiles();
			case PRESETS -> initPresets();
			case SETTINGS -> initSettings();
			case TRANSFER -> initTransfer();
			case RECORD -> initRecord();
		}
	}

	// ------------------------------------------------------------------ Profiles tab

	private int visibleRows() {
		return Math.max(1, (ch - 22) / ROW_H);
	}

	private void initProfiles() {
		List<MacroProfile> list = ProfileStore.get().profiles();
		addRenderableWidget(new DarkButton(cx, cy, 100, 16, lit("+ New Profile"),
				b -> minecraft.setScreen(new BindingModalScreen(this, new MacroProfile(), true))).accent(Theme.MINT));
		addRenderableWidget(new DarkButton(cx + 104, cy, 120, 16, lit("Paste Share Code"), b -> importClipboard()));
		int visible = visibleRows();
		scroll = Mth.clamp(scroll, 0, Math.max(0, list.size() - visible));
		for (int i = 0; i < visible && scroll + i < list.size(); i++) {
			MacroProfile p = list.get(scroll + i);
			int ry = cy + 22 + i * ROW_H;
			rows.add(new Row(p, cx, ry, cw, ROW_H - 4));
			int by = ry + ROW_H - 4 - 16;
			int bx = cx + cw - 4;
			boolean confirm = p.id.equals(pendingDelete);
			bx -= 36;
			addRenderableWidget(new DarkButton(bx, by, 34, 13, lit(confirm ? "Sure?" : "Del"), b -> {
				if (p.id.equals(pendingDelete)) {
					MacroEngine.get().stop(p);
					ProfileStore.get().delete(p);
					pendingDelete = null;
					setStatus("Deleted '" + p.name + "'", Theme.GRAY);
				} else {
					pendingDelete = p.id;
				}
				rebuildWidgets();
			}).accent(Theme.RED).selected(confirm));
			bx -= 40;
			addRenderableWidget(new DarkButton(bx, by, 38, 13, lit("Share"), b -> {
				minecraft.keyboardHandler.setClipboard(ProfileCodec.toShareCode(p));
				setStatus("Share code for '" + p.name + "' copied to clipboard", Theme.MINT);
			}));
			bx -= 36;
			addRenderableWidget(new DarkButton(bx, by, 34, 13, lit("Bind"),
					b -> minecraft.setScreen(new BindingModalScreen(this, p, false))));
			bx -= 36;
			addRenderableWidget(new DarkButton(bx, by, 34, 13, lit("Edit"),
					b -> minecraft.setScreen(new MacroEditorScreen(this, p))).accent(Theme.VIOLET));
			bx -= 36;
			addRenderableWidget(new DarkButton(bx, by, 34, 13, lit(onOff(p.enabled)), b -> {
				p.enabled = !p.enabled;
				ProfileStore.get().save(p);
				rebuildWidgets();
			}).selected(p.enabled).accent(Theme.MINT));
		}
	}

	private void renderProfiles(GuiGraphics g) {
		List<MacroProfile> list = ProfileStore.get().profiles();
		if (list.isEmpty()) {
			g.drawString(font, "No profiles yet. Create one or import a preset.", cx, cy + 30, Theme.GRAY, false);
			return;
		}
		for (Row r : rows) {
			MacroProfile p = r.profile();
			Theme.panel(g, r.x(), r.y(), r.w(), r.h(), Theme.CARD);
			g.fill(r.x(), r.y(), r.x() + 2, r.y() + r.h(), p.enabled ? Theme.CYAN : Theme.BORDER);
			int tx = r.x() + 7;
			String name = Theme.ellipsize(font, p.name, 150);
			g.drawString(font, name, tx, r.y() + 5, p.enabled ? Theme.WHITE : Theme.GRAY, false);
			tx += font.width(name) + 6;
			tx += Theme.tag(g, font, p.trigger.display(), tx, r.y() + 3, Theme.MINT) + 4;
			if (p.overrideNative) tx += Theme.tag(g, font, "override", tx, r.y() + 3, Theme.CYAN) + 4;
			if (MacroEngine.get().isRunning(p.id)) Theme.tag(g, font, "running", tx, r.y() + 3, Theme.VIOLET);
			g.drawString(font, Theme.ellipsize(font, p.chainSummary(), r.w() - 14), r.x() + 7, r.y() + 16, Theme.GRAY, false);
			g.drawString(font, p.chain().size() + " blocks", r.x() + 7, r.y() + 28, Theme.withAlpha(Theme.GRAY, 0x99), false);
		}
		int visible = visibleRows();
		if (list.size() > visible) {
			String s = (scroll + 1) + "-" + Math.min(list.size(), scroll + visible) + " of " + list.size() + "  (scroll)";
			g.drawString(font, s, cx + cw - font.width(s), cy + 4, Theme.GRAY, false);
		}
	}

	// ------------------------------------------------------------------ Presets tab

	private void initPresets() {
		List<PresetRegistry.Preset> presets = PresetRegistry.PRESETS;
		int cardW = (cw - 6) / 2;
		int cardH = Math.min(104, (ch - 6) / 2);
		for (int i = 0; i < presets.size(); i++) {
			PresetRegistry.Preset preset = presets.get(i);
			int x = cx + (i % 2) * (cardW + 6);
			int y = cy + (i / 2) * (cardH + 6);
			cards.add(new Card(preset, x, y, cardW, cardH));
			addRenderableWidget(new DarkButton(x + 6, y + cardH - 20, cardW - 12, 15, lit("Import & Bind"), b -> {
				MacroProfile p = preset.create();
				ProfileStore.get().save(p);
				setStatus("Imported preset '" + p.name + "'", Theme.MINT);
				minecraft.setScreen(new BindingModalScreen(this, p, false));
			}).accent(Theme.MINT));
		}
	}

	private void renderPresets(GuiGraphics g) {
		for (Card c : cards) {
			Theme.panel(g, c.x(), c.y(), c.w(), c.h(), Theme.CARD);
			g.fill(c.x(), c.y(), c.x() + c.w(), c.y() + 2, Theme.VIOLET);
			g.drawString(font, Theme.ellipsize(font, c.preset().name(), c.w() - 12), c.x() + 6, c.y() + 7, Theme.WHITE, false);
			g.drawWordWrap(font, lit(c.preset().description()), c.x() + 6, c.y() + 20, c.w() - 12, Theme.GRAY, false);
			String chain = c.preset().create().chainSummary();
			g.drawWordWrap(font, lit(chain), c.x() + 6, c.y() + 46, c.w() - 12, Theme.MINT, false);
		}
	}

	// ------------------------------------------------------------------ Settings tab

	private void initSettings() {
		GlobalSettings s = ProfileStore.get().settings();
		int y = cy;
		int half = (cw - 4) / 2;
		addRenderableWidget(new DarkButton(cx, y, cw, 18, lit("Master switch (all macros + overrides): " + onOff(s.masterEnabled)), b -> {
			s.masterEnabled = !s.masterEnabled;
			saveSettings();
		}).selected(s.masterEnabled).accent(Theme.MINT));
		y += 22;
		addRenderableWidget(new DarkButton(cx, y, half, 18, lit("Action-bar feedback: " + onOff(s.feedback)), b -> {
			s.feedback = !s.feedback;
			saveSettings();
		}).selected(s.feedback));
		addRenderableWidget(new DarkButton(cx + half + 4, y, half, 18, lit("Concurrent macros: " + onOff(s.allowConcurrent)), b -> {
			s.allowConcurrent = !s.allowConcurrent;
			saveSettings();
		}).selected(s.allowConcurrent));
		y += 22;
		addRenderableWidget(new DarkButton(cx, y, half, 18, lit("Editor snap to grid: " + onOff(s.snapToGrid)), b -> {
			s.snapToGrid = !s.snapToGrid;
			saveSettings();
		}).selected(s.snapToGrid).accent(Theme.VIOLET));
		addRenderableWidget(new DarkButton(cx + half + 4, y, half, 18, lit("Grid size: " + s.gridSize + "px"), b -> {
			int[] sizes = {5, 10, 20, 25};
			int next = sizes[0];
			for (int i = 0; i < sizes.length; i++) if (sizes[i] == s.gridSize) next = sizes[(i + 1) % sizes.length];
			s.gridSize = next;
			saveSettings();
		}).accent(Theme.VIOLET));
		y += 30;
		addRenderableWidget(new DarkButton(cx, y, half, 18, lit("Stop all running (" + MacroEngine.get().runningCount() + ")"), b -> {
			MacroEngine.get().stopAll();
			setStatus("All macros stopped", Theme.GRAY);
			rebuildWidgets();
		}).accent(Theme.RED));
		addRenderableWidget(new DarkButton(cx + half + 4, y, half, 18, lit("Reload profiles from disk"), b -> {
			ProfileStore.get().load();
			setStatus("Reloaded " + ProfileStore.get().profiles().size() + " profile(s)", Theme.MINT);
		}));
		y += 22;
		addRenderableWidget(new DarkButton(cx, y, half, 18, lit("Open config folder"),
				b -> Util.getPlatform().openPath(ProfileStore.get().rootDir())));
	}

	private void saveSettings() {
		ProfileStore.get().saveSettings();
		rebuildWidgets();
	}

	private void renderSettings(GuiGraphics g) {
		int y = cy + 144;
		g.drawString(font, "Hotkeys", cx, y, Theme.WHITE, false);
		g.drawWordWrap(font, lit("[O] Config Hub, [E] Macro Editor, Stop-All (unbound). Rebind them in Options > Controls > Key Binds > MacroNode."),
				cx, y + 12, cw, Theme.GRAY, false);
	}

	// ------------------------------------------------------------------ Import / Export tab

	private void initTransfer() {
		int y = cy + 14;
		int half = (cw - 4) / 2;
		addRenderableWidget(new DarkButton(cx, y, half, 18, lit("Import share code (clipboard)"), b -> importClipboard()).accent(Theme.MINT));
		addRenderableWidget(new DarkButton(cx + half + 4, y, half, 18, lit("Import .json file..."), b -> {
			importModal = true;
			importScroll = 0;
			rebuildWidgets();
		}).accent(Theme.MINT));
		y += 22;
		addRenderableWidget(new DarkButton(cx, y, half, 18, lit("Open imports folder"),
				b -> Util.getPlatform().openPath(ProfileStore.get().importsDir())));
		y += 40;
		addRenderableWidget(new DarkButton(cx, y, half, 18, lit("Export all as .json files"), b -> {
			int n = 0;
			for (MacroProfile p : ProfileStore.get().profiles()) {
				try {
					ProfileStore.get().exportFile(p);
					n++;
				} catch (IOException e) {
					setStatus("Export failed: " + e.getMessage(), Theme.RED);
				}
			}
			setStatus("Exported " + n + " profile(s) to config/macronode/exports", Theme.MINT);
		}).accent(Theme.CYAN));
		addRenderableWidget(new DarkButton(cx + half + 4, y, half, 18, lit("Copy all share codes"), b -> {
			StringBuilder sb = new StringBuilder();
			for (MacroProfile p : ProfileStore.get().profiles()) sb.append(ProfileCodec.toShareCode(p)).append('\n');
			minecraft.keyboardHandler.setClipboard(sb.toString().trim());
			setStatus("Copied " + ProfileStore.get().profiles().size() + " share code(s), one per line", Theme.MINT);
		}).accent(Theme.CYAN));
		y += 22;
		addRenderableWidget(new DarkButton(cx, y, half, 18, lit("Open exports folder"), b -> {
			try {
				java.nio.file.Files.createDirectories(ProfileStore.get().exportsDir());
			} catch (IOException ignored) {
			}
			Util.getPlatform().openPath(ProfileStore.get().exportsDir());
		}));
	}

	private void renderTransfer(GuiGraphics g) {
		g.drawString(font, "Import", cx, cy + 2, Theme.WHITE, false);
		g.drawString(font, "Export", cx, cy + 64, Theme.WHITE, false);
		g.drawWordWrap(font, lit("Share codes (MN1:...) are GZIP + Base64 profile JSON. Use 'Share' on a profile card to copy a single one. "
				+ "Drop .json files into config/macronode/imports to load them here."), cx, cy + 136, cw, Theme.GRAY, false);
	}

	// ------------------------------------------------------------------ Record tab

	private void initRecord() {
		MacroRecorder recorder = MacroRecorder.get();
		boolean isRecording = recorder.isRecording();
		int y = cy + 14;
		int half = (cw - 4) / 2;

		// Recording status indicator
		int statusColor = isRecording ? Theme.RED : Theme.GRAY;
		String statusText = isRecording ? "● Recording" : "○ Idle";
		addRenderableWidget(new DarkButton(cx, y, cw, 18, lit(statusText), b -> {
			if (isRecording) {
				recorder.stopRecording();
				setStatus("Recording stopped", Theme.GRAY);
			} else {
				recorder.startRecording();
				setStatus("Recording started - press actions in-game", Theme.MINT);
			}
			rebuildWidgets();
		}).selected(isRecording).accent(statusColor));
		y += 22;

		// Event count display
		int eventCount = recorder.getEvents().size();
		DarkButton eventCountButton = new DarkButton(cx, y, half, 18, lit("Events: " + eventCount), b -> {});
		eventCountButton.active = false;
		addRenderableWidget(eventCountButton);
		addRenderableWidget(new DarkButton(cx + half + 4, y, half, 18, lit("Clear Events"), b -> {
			recorder.clearEvents();
			setStatus("Events cleared", Theme.GRAY);
			rebuildWidgets();
		}).accent(Theme.RED));
		y += 30;

		// Save to profile button
		DarkButton saveButton = new DarkButton(cx, y, cw, 18, lit("Save to Profile"), b -> {
			if (eventCount == 0) {
				setStatus("No events recorded", Theme.RED);
				return;
			}
			MacroProfile profile = RecordGenerator.generateProfile(recorder.getEvents(), "Recorded Macro");
			ProfileStore.get().save(profile);
			recorder.clearEvents();
			setStatus("Saved as '" + profile.name + "'", Theme.MINT);
			tab = lastTab = Tab.PROFILES;
			rebuildWidgets();
		});
		saveButton.active = eventCount != 0;
		saveButton.accent(Theme.MINT);
		addRenderableWidget(saveButton);
	}

	private void renderRecord(GuiGraphics g) {
		MacroRecorder recorder = MacroRecorder.get();
		int y = cy + 110;

		g.drawString(font, "Hotkey: F8 (toggle recording)", cx, y, Theme.CYAN, false);
		y += 20;

		g.drawString(font, "What gets recorded:", cx, y, Theme.WHITE, false);
		g.drawWordWrap(font, lit("• Hotbar slot changes (1-9)\n• Item usage (right-click duration)\n• Left-click attacks\n• Jump, Sneak, Sprint toggles"),
				cx, y + 12, cw, Theme.GRAY, false);

		y += 60;
		g.drawString(font, "Live Event Feed", cx, y, Theme.WHITE, false);
		java.util.List<MacroRecorder.RecordedEvent> events = recorder.getEvents();
		int maxDisplay = Math.min(6, events.size());
		int feedY = y + 12;
		for (int i = Math.max(0, events.size() - maxDisplay); i < events.size(); i++) {
			MacroRecorder.RecordedEvent event = events.get(i);
			String eventText = event.type().name() + " (" + event.timestamp() + "ms)";
			g.drawString(font, eventText, cx + 4, feedY, Theme.GRAY, false);
			feedY += 12;
		}
		if (events.isEmpty()) {
			g.drawString(font, "No events recorded yet", cx + 4, feedY, Theme.withAlpha(Theme.GRAY, 0x66), false);
		}
	}

	private void importClipboard() {
		String clip = minecraft.keyboardHandler.getClipboard();
		int ok = 0;
		String err = null;
		for (String line : clip.split("\\R")) {
			if (line.isBlank()) continue;
			try {
				ProfileStore.get().importProfile(ProfileCodec.fromShareCode(line));
				ok++;
			} catch (IllegalArgumentException e) {
				err = e.getMessage();
			}
			if (line.trim().startsWith("{")) break; // raw JSON spans multiple lines; handled below
		}
		if (ok == 0 && clip.trim().startsWith("{")) {
			try {
				ProfileStore.get().importProfile(ProfileCodec.fromShareCode(clip));
				ok = 1;
				err = null;
			} catch (IllegalArgumentException e) {
				err = e.getMessage();
			}
		}
		if (ok > 0) {
			setStatus("Imported " + ok + " profile(s) from clipboard", Theme.MINT);
			tab = lastTab = Tab.PROFILES;
		} else {
			setStatus(err == null ? "Clipboard is empty" : err, Theme.RED);
		}
		rebuildWidgets();
	}

	// ------------------------------------------------------------------ Import .json modal

	private static final int MODAL_ROWS = 8;

	private void initImportModal() {
		mw = Math.min(width - 40, 280);
		mh = 60 + MODAL_ROWS * 16 + 24;
		mx0 = (width - mw) / 2;
		my0 = (height - mh) / 2;
		List<Path> files = ProfileStore.get().listImportFiles();
		importScroll = Mth.clamp(importScroll, 0, Math.max(0, files.size() - MODAL_ROWS));
		for (int i = 0; i < MODAL_ROWS && importScroll + i < files.size(); i++) {
			Path f = files.get(importScroll + i);
			addRenderableWidget(new DarkButton(mx0 + 10, my0 + 30 + i * 16, mw - 20, 14, lit(f.getFileName().toString()), b -> {
				try {
					MacroProfile p = ProfileStore.get().importFile(f);
					setStatus("Imported '" + p.name + "' from " + f.getFileName(), Theme.MINT);
					importModal = false;
					tab = lastTab = Tab.PROFILES;
				} catch (Exception e) {
					setStatus("Invalid file: " + e.getMessage(), Theme.RED);
				}
				rebuildWidgets();
			}));
		}
		int third = (mw - 28) / 3, by = my0 + mh - 22;
		addRenderableWidget(new DarkButton(mx0 + 10, by, third, 16, lit("Open Folder"),
				b -> Util.getPlatform().openPath(ProfileStore.get().importsDir())));
		addRenderableWidget(new DarkButton(mx0 + 14 + third, by, third, 16, lit("Refresh"), b -> rebuildWidgets()));
		addRenderableWidget(new DarkButton(mx0 + 18 + 2 * third, by, third, 16, lit("Cancel"), b -> {
			importModal = false;
			rebuildWidgets();
		}).accent(Theme.RED));
	}

	private void renderImportModal(GuiGraphics g) {
		g.fill(0, 0, width, height, Theme.SHADE);
		Theme.panel(g, mx0, my0, mw, mh, Theme.CARD);
		g.fill(mx0, my0, mx0 + mw, my0 + 2, Theme.MINT);
		g.drawString(font, "Import profile from .json", mx0 + 10, my0 + 8, Theme.WHITE, false);
		g.drawString(font, Theme.ellipsize(font, "config/macronode/imports/", mw - 20), mx0 + 10, my0 + 18, Theme.GRAY, false);
		if (ProfileStore.get().listImportFiles().isEmpty()) {
			g.drawWordWrap(font, lit("No .json files found. Put exported profiles into the imports folder, then press Refresh."),
					mx0 + 10, my0 + 40, mw - 20, Theme.GRAY, false);
		}
	}

	// ------------------------------------------------------------------ rendering & input

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, width, height, 0xE0121214);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		Theme.panel(g, px, py, pw, ph, Theme.CANVAS);
		g.fill(px + 1, py + 1, px + pw - 1, py + 22, Theme.CARD);
		g.fill(px + 1, py + 22, px + pw - 1, py + 23, Theme.BORDER);
		g.drawString(font, "MacroNode", px + 8, py + 8, Theme.WHITE, false);
		g.drawString(font, "Config Hub", px + 10 + font.width("MacroNode"), py + 8, Theme.CYAN, false);
		String info = ProfileStore.get().profiles().size() + " profiles  ·  " + MacroEngine.get().runningCount() + " running"
				+ (ProfileStore.get().settings().masterEnabled ? "" : "  ·  DISABLED");
		g.drawString(font, info, px + pw - 8 - font.width(info), py + 8,
				ProfileStore.get().settings().masterEnabled ? Theme.GRAY : Theme.RED, false);
		g.fill(px + 1, py + 23, px + 110, py + ph - 1, Theme.CARD);
		g.fill(px + 110, py + 23, px + 111, py + ph - 1, Theme.BORDER);
		if (!importModal) {
			switch (tab) {
				case PROFILES -> renderProfiles(g);
				case PRESETS -> renderPresets(g);
				case SETTINGS -> renderSettings(g);
				case TRANSFER -> renderTransfer(g);
				case RECORD -> renderRecord(g);
			}
		}
		if (System.currentTimeMillis() < statusUntil) {
			g.drawString(font, Theme.ellipsize(font, status, cw), cx, py + ph - 12, statusColor, false);
		}
		if (importModal) {
			g.nextStratum();
			renderImportModal(g);
		}
		super.render(g, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int d = (int) Math.signum(scrollY);
		if (d == 0) return false;
		if (importModal) {
			importScroll -= d;
			rebuildWidgets();
			return true;
		}
		if (tab == Tab.PROFILES) {
			scroll -= d;
			rebuildWidgets();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		if (importModal) {
			importModal = false;
			rebuildWidgets();
			return;
		}
		ProfileStore.get().saveSettings();
		minecraft.setScreen(parent);
	}
}
