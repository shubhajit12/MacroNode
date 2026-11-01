package macronode.macro.client.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import macronode.macro.client.action.ActionBlock;
import macronode.macro.client.action.ActionType;

/** A macro profile: trigger + node graph. Serialized to config/macronode/profiles/<id>.json. */
public class MacroProfile {
	public String id = newId();
	public String name = "New Macro";
	public boolean enabled = true;
	/** When true the trigger key press is swallowed and never reaches vanilla (e.g. "2" won't select slot 2). */
	public boolean overrideNative = true;
	/** Pressing the trigger while the macro runs stops it (useful for infinite loops). */
	public boolean stopOnRepress = true;
	public Trigger trigger = new Trigger();

	/** Editor position of the Start node. */
	public int startX = 20;
	public int startY = 40;
	/** Id of the block wired to Start, -1 if none. */
	public int rootId = -1;
	public List<ActionBlock> blocks = new ArrayList<>();

	public static String newId() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}

	public ActionBlock block(int id) {
		for (ActionBlock b : blocks) if (b.id == id) return b;
		return null;
	}

	public int nextBlockId() {
		int max = 0;
		for (ActionBlock b : blocks) max = Math.max(max, b.id);
		return max + 1;
	}

	/** Follows next-links from the Start node producing the executable sequence (cycle safe). */
	public List<ActionBlock> chain() {
		return chainFrom(rootId);
	}

	/** Linear chain starting at a block id, following next-links (used for If/Else false branches). */
	public List<ActionBlock> chainFrom(int startId) {
		List<ActionBlock> out = new ArrayList<>();
		Set<Integer> seen = new HashSet<>();
		ActionBlock cur = block(startId);
		while (cur != null && seen.add(cur.id)) {
			if (cur.actionType() != null) out.add(cur);
			cur = block(cur.next);
		}
		return out;
	}

	/** Every block id reachable from {@code fromId} via next AND else links (includes fromId). */
	public Set<Integer> reachableFrom(int fromId) {
		Set<Integer> seen = new HashSet<>();
		java.util.ArrayDeque<Integer> todo = new java.util.ArrayDeque<>();
		if (block(fromId) != null) todo.add(fromId);
		while (!todo.isEmpty()) {
			ActionBlock b = block(todo.poll());
			if (b == null || !seen.add(b.id)) continue;
			if (b.next != -1) todo.add(b.next);
			if (b.elseNext != -1) todo.add(b.elseNext);
		}
		return seen;
	}

	/** Blocks reachable from the Start node, including If/Else false branches. */
	public Set<Integer> reachableIds() {
		return reachableFrom(rootId);
	}

	/** Last block in the chain, or null if chain is empty. */
	public ActionBlock tail() {
		List<ActionBlock> c = chain();
		return c.isEmpty() ? null : c.get(c.size() - 1);
	}

	/** Appends a new block at the end of the chain and lays it out to the right of the previous one. */
	public ActionBlock append(ActionType type) {
		ActionBlock b = new ActionBlock(nextBlockId(), type);
		ActionBlock tail = tail();
		if (tail == null) {
			b.x = startX + 70;
			b.y = startY;
			rootId = b.id;
		} else {
			b.x = tail.x + 130;
			b.y = tail.y;
			if (b.x > 640) {
				b.x = startX + 70;
				b.y = tail.y + 60;
			}
			b.next = tail.next;
			tail.next = b.id;
		}
		blocks.add(b);
		return b;
	}

	/** Removes a block, re-linking its predecessor to its successor. */
	public void remove(int id) {
		ActionBlock b = block(id);
		if (b == null) return;
		if (rootId == id) rootId = b.next;
		for (ActionBlock o : blocks) {
			if (o.next == id) o.next = b.next;
			if (o.elseNext == id) o.elseNext = b.next;
		}
		blocks.remove(b);
	}

	/** Detaches whatever currently points at {@code id} (Start or another block). */
	public void unlinkIncoming(int id) {
		if (rootId == id) rootId = -1;
		for (ActionBlock o : blocks) {
			if (o.next == id) o.next = -1;
			if (o.elseNext == id) o.elseNext = -1;
		}
	}

	public String chainSummary() {
		StringBuilder sb = new StringBuilder();
		for (ActionBlock b : chain()) {
			if (!sb.isEmpty()) sb.append(" -> ");
			sb.append(b.summary());
		}
		return sb.isEmpty() ? "(empty)" : sb.toString();
	}

	public MacroProfile copy() {
		MacroProfile p = new MacroProfile();
		p.id = id;
		p.name = name;
		p.enabled = enabled;
		p.overrideNative = overrideNative;
		p.stopOnRepress = stopOnRepress;
		p.trigger = trigger == null ? new Trigger() : trigger.copy();
		p.startX = startX;
		p.startY = startY;
		p.rootId = rootId;
		for (ActionBlock b : blocks) p.blocks.add(b.copy());
		return p;
	}

	/** Fixes up data loaded from untrusted JSON. */
	public void sanitize() {
		if (id == null || id.isBlank()) id = newId();
		id = id.replaceAll("[^A-Za-z0-9_-]", "");
		if (id.isEmpty()) id = newId();
		if (name == null) name = "Macro";
		if (trigger == null) trigger = new Trigger();
		if (trigger.type == null) trigger.type = Trigger.Type.SINGLE;
		if (trigger.keys == null) trigger.keys = new ArrayList<>();
		if (blocks == null) blocks = new ArrayList<>();
		blocks.removeIf(b -> b == null || b.actionType() == null);
		for (ActionBlock b : blocks) if (b.params == null) b.params = new java.util.LinkedHashMap<>();
	}
}
