package macronode.macro.client.engine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import macronode.macro.MacroNode;
import macronode.macro.client.action.ActionBlock;
import macronode.macro.client.action.ActionRegistry;
import macronode.macro.client.action.ActionType;
import macronode.macro.client.action.ControlActions;
import macronode.macro.client.data.MacroProfile;

/**
 * One executing instance of a profile. The node chain is flattened into a program; a program
 * counter walks it. Control blocks (Repeat / Conditional) are interpreted here, everything else is
 * dispatched to {@link ActionRegistry}.
 */
public final class MacroRun {
	/** Safety valve: max steps per pump so a zero-delay infinite loop cannot freeze the client. */
	private static final int STEP_BUDGET = 256;

	private static final class LoopFrame {
		final int start;
		final int end;
		int remaining; // -1 = infinite

		LoopFrame(int start, int end, int remaining) {
			this.start = start;
			this.end = end;
			this.remaining = remaining;
		}
	}

	public final MacroProfile profile;
	private List<ActionBlock> program;
	private final ExecContext ctx;
	private final Deque<LoopFrame> loops = new ArrayDeque<>();
	private int pc;
	private long wakeAt;
	private boolean finished;

	MacroRun(MacroProfile profile) {
		this.profile = profile;
		this.program = profile.chain();
		this.ctx = new ExecContext(this);
	}

	public boolean isFinished() {
		return finished;
	}

	public int pc() {
		return pc;
	}

	public int currentBlockId() {
		return pc >= 0 && pc < program.size() ? program.get(pc).id : -1;
	}

	void finish() {
		finished = true;
		HeldKeys.get().releaseOwned(this);
	}

	private void advance(int to) {
		pc = to;
		ctx.resetAction();
	}

	/** Executes as many steps as are due. Called every rendered frame and every client tick. */
	void pump() {
		int budget = STEP_BUDGET;
		while (!finished && budget-- > 0) {
			long now = System.nanoTime();
			if (now < wakeAt) return;
			if (ctx.player() == null) {
				finish();
				return;
			}

			// Close any loop whose body has completed: jump back or pop the frame.
			while (!loops.isEmpty() && pc >= loops.peek().end) {
				LoopFrame f = loops.peek();
				if (f.remaining < 0 || --f.remaining > 0) {
					advance(f.start);
					break;
				}
				loops.pop();
			}
			if (pc >= program.size()) {
				finish();
				return;
			}

			ActionBlock block = program.get(pc);
			ActionType type = block.actionType();

			if (type == ActionType.REPEAT) {
				int times = block.getInt("times");
				int start = pc + 1;
				int end = Math.min(program.size(), start + Math.max(1, block.getInt("body")));
				if (times != 1 && start < end) loops.push(new LoopFrame(start, end, times <= 0 ? -1 : times));
				advance(pc + 1);
				continue;
			}
			if (type == ActionType.IF_ELSE) {
				boolean ok = ControlActions.evaluate(ctx, block);
				if (block.getBool("negate")) ok = !ok;
				if (ok) {
					advance(pc + 1);
				} else {
					// False branch: continue with the chain wired to the node's red bottom (else) port.
					program = profile.chainFrom(block.elseNext);
					loops.clear();
					advance(0);
				}
				continue;
			}
			if (type == ActionType.CONDITION || type == ActionType.CHECK_ITEM_COUNT) {
				boolean ok = ControlActions.evaluate(ctx, block);
				if (block.getBool("negate")) ok = !ok;
				if (ok) {
					advance(pc + 1);
				} else if ("STOP".equals(block.get("onFalse"))) {
					finish();
					return;
				} else {
					advance(pc + 1 + Math.max(0, block.getInt("skip")));
				}
				continue;
			}

			Step step;
			try {
				step = ActionRegistry.execute(type, ctx, block);
			} catch (Exception e) {
				MacroNode.LOGGER.warn("MacroNode action {} failed: {}", type, e.toString());
				step = Step.NEXT;
			}
			switch (step.kind()) {
				case NEXT -> advance(pc + 1);
				case NEXT_AFTER -> {
					advance(pc + 1);
					if (step.nanos() > 0) {
						wakeAt = now + step.nanos();
						return;
					}
				}
				case REPEAT_AFTER -> {
					wakeAt = now + step.nanos();
					return;
				}
				case STOP -> {
					finish();
					return;
				}
			}
		}
	}
}
