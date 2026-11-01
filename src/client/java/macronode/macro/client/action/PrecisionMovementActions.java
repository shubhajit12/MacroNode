package macronode.macro.client.action;

import java.util.ArrayList;
import java.util.List;

import macronode.macro.client.engine.Step;
import macronode.macro.client.util.ClientUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Precision Movement & Positioning. */
final class PrecisionMovementActions {
	private static final Direction[] SUPPORT_ORDER = {
			Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
	private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

	private PrecisionMovementActions() {
	}

	static void register() {
		ActionRegistry.register(ActionType.LOOK_AT_COORDS, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			Vec3 target = new Vec3(coord(b.get("x"), p.getX()), coord(b.get("y"), p.getY()), coord(b.get("z"), p.getZ()));
			ClientUtil.lookAt(p, target);
			return Step.NEXT;
		});

		// Cobweb: placed in the target's feet block. Obsidian: the target's own hitbox blocks that space,
		// so the 4 blocks around the feet are filled instead (surround trap).
		ActionRegistry.register(ActionType.AUTO_TRAP, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			double reach = Math.min(b.getDouble("range"), p.blockInteractionRange());
			Player target = ClientUtil.nearestPlayer(p, reach + 1.5, b.get("username"));
			if (target == null) return Step.NEXT;
			boolean web = !"OBSIDIAN".equals(b.get("block"));
			int orig = p.getInventory().getSelectedSlot();
			int slot = DefenseActions.hotbarOrFetch(p, web ? Items.COBWEB : Items.OBSIDIAN);
			if (slot < 0) return Step.NEXT;
			ctx.selectSlot(slot);

			BlockPos feet = BlockPos.containing(target.position());
			List<BlockPos> spots = new ArrayList<>();
			if (web) spots.add(feet);
			else for (Direction d : HORIZONTAL) spots.add(feet.relative(d));
			Level level = p.level();
			for (BlockPos pos : spots) {
				if (!level.getBlockState(pos).canBeReplaced()) continue;
				BlockHitResult hit = supportHit(level, pos);
				if (hit == null || p.getEyePosition().distanceTo(hit.getLocation()) > reach) continue;
				ClientUtil.lookAt(p, hit.getLocation());
				ClientUtil.useOn(InteractionHand.MAIN_HAND, hit);
			}
			if (b.getBool("restore")) p.getInventory().setSelectedSlot(orig);
			return Step.NEXT;
		});
	}

	/** A click on a solid neighbour's face that results in a block being placed at {@code pos}. */
	private static BlockHitResult supportHit(Level level, BlockPos pos) {
		for (Direction d : SUPPORT_ORDER) {
			BlockPos n = pos.relative(d);
			if (level.getBlockState(n).canBeReplaced()) continue;
			Direction face = d.getOpposite();
			Vec3 c = Vec3.atCenterOf(n).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
			return new BlockHitResult(c, face, n, false);
		}
		return null;
	}

	/** "~" / "~2.5" = relative to the player; integer absolute X/Z values aim at the block centre. */
	private static double coord(String s, double base) {
		String t = s == null ? "" : s.trim();
		try {
			if (t.startsWith("~")) return base + (t.length() > 1 ? Double.parseDouble(t.substring(1)) : 0);
			double v = Double.parseDouble(t);
			return t.contains(".") ? v : v + 0.5;
		} catch (NumberFormatException e) {
			return base;
		}
	}
}
