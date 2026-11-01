package macronode.macro.client.action;

import macronode.macro.client.engine.ExecContext;
import macronode.macro.client.engine.Step;

/** Executes (one stage of) an action block. Runs on the client thread. */
@FunctionalInterface
public interface ActionHandler {
	Step run(ExecContext ctx, ActionBlock block);
}
