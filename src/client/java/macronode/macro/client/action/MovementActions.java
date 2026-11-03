package macronode.macro.client.action;

import macronode.macro.client.engine.Step;
import macronode.macro.client.util.ClientUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Movement & World blocks. Movement is driven by holding vanilla movement keys for N ticks. */
final class MovementActions {
	private static final long TICK = 50_000_000L;

	private MovementActions() {
	}

	static void register() {
		// 2 ticks guarantees at least one client tick observes the jump key.
		ActionRegistry.register(ActionType.JUMP, (ctx, b) -> {
			ctx.hold(ctx.mc.options.keyJump, 2 * TICK);
			return Step.NEXT;
		});

		ActionRegistry.register(ActionType.SNEAK, (ctx, b) -> ActionRegistry.holdMode(ctx, b, ctx.mc.options.keyShift));
		ActionRegistry.register(ActionType.SPRINT, (ctx, b) -> ActionRegistry.holdMode(ctx, b, ctx.mc.options.keySprint));

		ActionRegistry.register(ActionType.STRAFE, (ctx, b) -> {
			KeyMapping key = switch (b.get("direction")) {
				case "RIGHT" -> ctx.mc.options.keyRight;
				case "BACK" -> ctx.mc.options.keyDown;
				case "FORWARD" -> ctx.mc.options.keyUp;
				default -> ctx.mc.options.keyLeft;
			};
			return ActionRegistry.holdFor(ctx, key, b.getNanos("duration"));
		});

		// Raycasts from the current rotation; optionally waits until the player's hitbox has left the target space.
		ActionRegistry.register(ActionType.PLACE_BLOCK, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			InteractionHand hand = "OFF".equals(b.get("hand")) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			boolean canWait = b.getBool("waitClear") && ctx.elapsedInAction() < b.getNanos("timeout");
			BlockHitResult hit = ClientUtil.freshBlockHit(p);
			if (hit == null) return canWait ? Step.repeatNextFrame() : Step.NEXT;
			BlockPos placePos = hit.getBlockPos().relative(hit.getDirection());
			if (canWait && p.getBoundingBox().intersects(new AABB(placePos))) return Step.repeatNextFrame();
			ClientUtil.useOn(hand, hit);
			return Step.NEXT;
		});

		// Repeats: wait for ground -> look down + jump -> once feet clear the block, place under them.
		ActionRegistry.register(ActionType.PILLAR, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			if (!ctx.scratch.containsKey("left")) {
				ctx.scratch.put("left", Math.max(1, b.getInt("count")));
				ctx.scratch.put("yaw", p.getYRot());
				ctx.scratch.put("pitch", p.getXRot());
			}
			int left = ctx.get("left", 1);
			if (ctx.stage == 0) {
				if (!p.onGround()) {
					if (ctx.elapsedInStage() > 20 * TICK) return finishPillar(ctx);
					return Step.repeatNextFrame();
				}
				ClientUtil.setRotation(p, p.getYRot(), 90f, true);
				BlockPos feet = BlockPos.containing(p.position());
				ctx.scratch.put("feet", feet);
				ctx.hold(ctx.mc.options.keyJump, 2 * TICK);
				ctx.setStage(1);
				return Step.repeatNextFrame();
			}
			BlockPos feet = ctx.get("feet", BlockPos.ZERO);
			if (p.getY() >= feet.getY() + 1.0) {
				BlockPos below = feet.below();
				ClientUtil.useOn(InteractionHand.MAIN_HAND,
						new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false));
				left--;
				ctx.scratch.put("left", left);
				if (left <= 0) return finishPillar(ctx);
				ctx.setStage(0);
				return Step.repeatNextFrame();
			}
			if (ctx.elapsedInStage() > 15 * TICK) return finishPillar(ctx);
			return Step.repeatNextFrame();
		});

		ActionRegistry.register(ActionType.ELYTRA_ROCKET, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			switch (ctx.stage) {
				case 0 -> {
					if (p.isFallFlying()) {
						ctx.setStage(2);
						return Step.repeatNextFrame();
					}
					if (p.onGround()) {
						ctx.hold(ctx.mc.options.keyJump, 2 * TICK);
						ctx.setStage(1);
						return Step.repeat(6 * TICK);
					}
					ctx.setStage(1);
					return Step.repeatNextFrame();
				}
				case 1 -> {
					// A fresh jump press while airborne deploys the elytra (vanilla tryToStartFallFlying).
					if (!p.isFallFlying()) ctx.hold(ctx.mc.options.keyJump, 2 * TICK);
					ctx.setStage(2);
					return Step.repeat(3 * TICK);
				}
				default -> {
					if (b.getBool("rocket") && p.isFallFlying()) {
						if (p.getOffhandItem().is(Items.FIREWORK_ROCKET)) {
							ClientUtil.useItem(InteractionHand.OFF_HAND);
						} else {
							int slot = ClientUtil.findHotbar(p, ClientUtil.itemMatcher(Items.FIREWORK_ROCKET));
							if (slot >= 0) {
								int orig = p.getInventory().getSelectedSlot();
								ctx.selectSlot(slot);
								ClientUtil.useItem(InteractionHand.MAIN_HAND);
								p.getInventory().setSelectedSlot(orig);
							}
						}
					}
					return Step.NEXT;
				}
			}
		});
	}

	private static Step finishPillar(macronode.macro.client.engine.ExecContext ctx) {
		float yaw = ctx.get("yaw", ctx.player().getYRot());
		float pitch = ctx.get("pitch", ctx.player().getXRot());
		ClientUtil.setRotation(ctx.player(), yaw, pitch, true);
		return Step.NEXT;
	}
}
