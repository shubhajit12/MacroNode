package macronode.macro.client.action;

import macronode.macro.client.engine.AimLockManager;
import macronode.macro.client.engine.Step;
import macronode.macro.client.util.ClientUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** Targeting & Camera blocks. Rotation changes are sent to the server by the normal movement packets. */
final class TargetingActions {
	private TargetingActions() {
	}

	static void register() {
		ActionRegistry.register(ActionType.LOOK_NEAREST_PLAYER, (ctx, b) -> {
			Entity t = ClientUtil.nearestPlayer(ctx.player(), b.getDouble("range"), null);
			if (t != null) ClientUtil.lookAt(ctx.player(), ClientUtil.aimPoint(t));
			return Step.NEXT;
		});

		ActionRegistry.register(ActionType.LOOK_PLAYER, (ctx, b) -> {
			Entity t = ClientUtil.nearestPlayer(ctx.player(), b.getDouble("range"), b.get("username"));
			if (t != null) ClientUtil.lookAt(ctx.player(), ClientUtil.aimPoint(t));
			return Step.NEXT;
		});

		ActionRegistry.register(ActionType.LOOK_NEAREST_ENTITY, (ctx, b) -> {
			Entity t = ClientUtil.nearestLiving(ctx.player(), b.getDouble("range"), b.getBool("hostileOnly"));
			if (t != null) ClientUtil.lookAt(ctx.player(), ClientUtil.aimPoint(t));
			return Step.NEXT;
		});

		ActionRegistry.register(ActionType.LOOK_ANGLE, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			boolean rel = b.getBool("relative");
			float pitch = p.getXRot();
			float yaw = p.getYRot();
			String ps = b.get("pitch").trim();
			String ys = b.get("yaw").trim();
			if (!ps.isEmpty() && !ps.equalsIgnoreCase("keep")) {
				float v = parse(ps, 0);
				pitch = rel ? pitch + v : v;
			}
			if (!ys.isEmpty() && !ys.equalsIgnoreCase("keep")) {
				float v = parse(ys, 0);
				yaw = rel ? yaw + v : v;
			}
			ClientUtil.setRotation(p, yaw, pitch, true);
			return Step.NEXT;
		});

		ActionRegistry.register(ActionType.RESET_CAMERA, (ctx, b) -> {
			ClientUtil.setRotation(ctx.player(), ctx.startYaw, ctx.startPitch, true);
			return Step.NEXT;
		});

		// Per-frame exponential smoothing toward the target, framerate independent.
		ActionRegistry.register(ActionType.SMOOTH_TRACK, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			if (ctx.elapsedInAction() >= b.getNanos("duration")) return Step.NEXT;
			double range = b.getDouble("range");
			Entity target;
			String targetType = b.get("target");
			if ("PLAYER".equals(targetType)) {
				target = ClientUtil.nearestPlayer(p, range, b.get("username"));
			} else if ("NEAREST_ENTITY".equals(targetType)) {
				target = ClientUtil.nearestLiving(p, range, false);
			} else {
				target = ClientUtil.nearestPlayer(p, range, null);
			}
			long now = System.nanoTime();
			long last = ctx.get("last", now);
			ctx.scratch.put("last", now);
			if (target != null) {
				double ticks = (now - last) / 50_000_000.0;
				double s = Mth.clamp(b.getDouble("smoothing"), 0.01, 1.0);
				float f = (float) (1.0 - Math.pow(1.0 - s, Math.max(ticks, 0.0)));
				float[] want = ClientUtil.anglesTo(p, ClientUtil.aimPoint(target));
				float yaw = p.getYRot() + Mth.wrapDegrees(want[0] - p.getYRot()) * f;
				float pitch = p.getXRot() + (want[1] - p.getXRot()) * f;
				ClientUtil.setRotation(p, yaw, pitch, false);
			}
			return Step.repeatNextFrame();
		});

		// Single-precision smooth aim with configurable target part (head/chest/feet)
		ActionRegistry.register(ActionType.SMOOTH_AIM, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			if (ctx.elapsedInAction() >= b.getNanos("duration")) return Step.NEXT;
			double range = b.getDouble("range");
			Entity target;
			String targetType = b.get("target");
			if ("NEAREST_ENTITY".equals(targetType)) {
				target = ClientUtil.nearestLiving(p, range, false);
			} else if ("LOCKED_ENTITY".equals(targetType)) {
				target = ctx.mc.crosshairPickEntity;
			} else {
				target = ClientUtil.nearestPlayer(p, range, null);
			}
			if (target != null) {
				net.minecraft.world.phys.Vec3 aimPoint;
				if ("CHEST".equals(b.get("aimPart"))) {
					aimPoint = ClientUtil.aimPointChest(target);
				} else if ("FEET".equals(b.get("aimPart"))) {
					aimPoint = ClientUtil.aimPointFeet(target);
				} else {
					aimPoint = ClientUtil.aimPointHead(target);
				}
				float s = Mth.clamp(b.getFloat("smoothing"), 0.01f, 1.0f);
				float[] want = ClientUtil.anglesTo(p, aimPoint);
				float yaw = ClientUtil.lerpAngle(p.getYRot(), want[0], s);
				float pitch = ClientUtil.lerpAngle(p.getXRot(), want[1], s);
				ClientUtil.setRotation(p, yaw, pitch, false);
			}
			return Step.repeatNextFrame();
		});

		// Start continuous background aim lock
		ActionRegistry.register(ActionType.START_AIM_LOCK, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			if (p == null) return Step.NEXT;
			String targetType = b.get("target");
			String username = b.get("username");
			float smoothing = b.getFloat("smoothing");
			double range = b.getDouble("range");
			String stopCondition = b.get("stopCondition");
			boolean stopOnDeath = "DEATH".equals(stopCondition) || "OUT_OF_RANGE".equals(stopCondition);
			boolean stopOnOutOfRange = "OUT_OF_RANGE".equals(stopCondition);
			AimLockManager.get().start(targetType, username, smoothing, range, stopOnDeath, stopOnOutOfRange);
			return Step.NEXT;
		});

		// Stop continuous background aim lock
		ActionRegistry.register(ActionType.STOP_AIM_LOCK, (ctx, b) -> {
			AimLockManager.get().stop();
			return Step.NEXT;
		});
	}

	private static float parse(String s, float def) {
		try {
			return Float.parseFloat(s.replace("\u00B0", ""));
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
