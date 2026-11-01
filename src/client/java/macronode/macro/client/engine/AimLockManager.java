package macronode.macro.client.engine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import macronode.macro.client.util.ClientUtil;

/**
 * Manages continuous background aim tracking that runs independently of macro execution.
 * When active, it smoothly interpolates the player's rotation toward a target each frame.
 */
public final class AimLockManager {
	private static final AimLockManager INSTANCE = new AimLockManager();

	public static AimLockManager get() {
		return INSTANCE;
	}

	private AimLockState state = AimLockState.INACTIVE;
	private Entity target;
	private float smoothing;
	private double maxRange;
	private String targetType;
	private String targetUsername;
	private boolean stopOnDeath;
	private boolean stopOnOutOfRange;
	private long lastUpdateTime;

	private AimLockManager() {
	}

	public void start(String targetType, String username, float smoothing, double maxRange,
			boolean stopOnDeath, boolean stopOnOutOfRange) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) return;

		Entity t = findTarget(p, targetType, username, maxRange);
		if (t == null) return;

		this.target = t;
		this.smoothing = Mth.clamp(smoothing, 0.01f, 1.0f);
		this.maxRange = maxRange;
		this.targetType = targetType;
		this.targetUsername = username;
		this.stopOnDeath = stopOnDeath;
		this.stopOnOutOfRange = stopOnOutOfRange;
		this.state = AimLockState.ACTIVE;
		this.lastUpdateTime = System.nanoTime();
	}

	public void stop() {
		this.state = AimLockState.INACTIVE;
		this.target = null;
	}

	public boolean isActive() {
		return state == AimLockState.ACTIVE;
	}

	public void pump() {
		if (!isActive()) return;

		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null) {
			stop();
			return;
		}

		long now = System.nanoTime();
		long delta = now - lastUpdateTime;
		lastUpdateTime = now;

		if (shouldStop(p)) {
			stop();
			return;
		}

		if (target != null && target.isAlive()) {
			net.minecraft.world.phys.Vec3 aimPoint = getAimPoint(target);
			float[] targetAngles = ClientUtil.anglesTo(p, aimPoint);
			float currentYaw = p.getYRot();
			float currentPitch = p.getXRot();

			float lerpYaw = ClientUtil.lerpAngle(currentYaw, targetAngles[0], smoothing);
			float lerpPitch = ClientUtil.lerpAngle(currentPitch, targetAngles[1], smoothing);

			ClientUtil.setRotation(p, lerpYaw, lerpPitch, false);
		} else {
			stop();
		}
	}

	private boolean shouldStop(LocalPlayer p) {
		if (target == null) return true;
		if (!target.isAlive()) return stopOnDeath;
		if (stopOnOutOfRange && p.distanceToSqr(target) > maxRange * maxRange) return true;
		return false;
	}

	private Entity findTarget(LocalPlayer p, String targetType, String username, double range) {
		if ("NEAREST_PLAYER".equals(targetType)) {
			return ClientUtil.nearestPlayer(p, range, username);
		} else if ("NEAREST_ENTITY".equals(targetType)) {
			return ClientUtil.nearestLiving(p, range, false);
		} else if ("LOCKED_ENTITY".equals(targetType)) {
			if (target != null && target.isAlive() && p.distanceToSqr(target) <= range * range) {
				return target;
			}
			return ClientUtil.nearestLiving(p, range, false);
		}
		return ClientUtil.nearestPlayer(p, range, username);
	}

	private net.minecraft.world.phys.Vec3 getAimPoint(Entity e) {
		return e.getEyePosition();
	}

	private enum AimLockState {
		INACTIVE,
		ACTIVE
	}
}
