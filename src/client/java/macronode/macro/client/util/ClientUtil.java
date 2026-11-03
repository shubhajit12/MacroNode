package macronode.macro.client.util;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Shared world/inventory helpers for action handlers. All methods must run on the client thread. */
public final class ClientUtil {
	private ClientUtil() {
	}

	public static Minecraft mc() {
		return Minecraft.getInstance();
	}

	// ------------------------------------------------------------------ items

	/** Parses "golden_apple,minecraft:enchanted_golden_apple" into a stack predicate. */
	public static Predicate<ItemStack> itemMatcher(String spec) {
		Set<Item> items = new HashSet<>();
		if (spec != null) {
			for (String raw : spec.split(",")) {
				String token = raw.trim().toLowerCase(Locale.ROOT);
				if (token.isEmpty()) continue;
				Identifier id = Identifier.tryParse(token.contains(":") ? token : "minecraft:" + token);
				if (id == null) continue;
				BuiltInRegistries.ITEM.getOptional(id).ifPresent(items::add);
			}
		}
		return stack -> !stack.isEmpty() && items.contains(stack.getItem());
	}

	public static Predicate<ItemStack> itemMatcher(Item item) {
		return stack -> !stack.isEmpty() && stack.is(item);
	}

	public static int findHotbar(LocalPlayer p, Predicate<ItemStack> match) {
		Inventory inv = p.getInventory();
		int sel = inv.getSelectedSlot();
		if (match.test(inv.getItem(sel))) return sel;
		for (int i = 0; i < 9; i++) if (match.test(inv.getItem(i))) return i;
		return -1;
	}

	/** Searches inventory indices [from, to). Main inventory is 9..35, hotbar 0..8. */
	public static int findInventory(LocalPlayer p, Predicate<ItemStack> match, int from, int to) {
		Inventory inv = p.getInventory();
		for (int i = from; i < to; i++) if (match.test(inv.getItem(i))) return i;
		return -1;
	}

	public static int emptyHotbarSlot(LocalPlayer p) {
		for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).isEmpty()) return i;
		return -1;
	}

	/**
	 * Maps an {@link Inventory} index to the InventoryMenu slot id.
	 * Menu layout: 0 result, 1-4 craft, 5-8 armor (head..feet), 9-35 main, 36-44 hotbar, 45 offhand.
	 */
	public static int menuSlot(int invIndex) {
		if (invIndex >= 0 && invIndex < 9) return 36 + invIndex;
		if (invIndex < 36) return invIndex;
		if (invIndex >= 36 && invIndex <= 39) return 8 - (invIndex - 36);
		if (invIndex == 40) return 45;
		return -1;
	}

	/** Clicks a slot of the player's own inventory menu. Returns false if another container is open. */
	public static boolean click(int menuSlot, int button, ClickType type) {
		Minecraft mc = mc();
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || menuSlot < 0) return false;
		if (p.containerMenu != p.inventoryMenu) return false;
		mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, menuSlot, button, type, p);
		return true;
	}

	// ------------------------------------------------------------------ interaction

	/** Raycasts from the CURRENT rotation (not last frame's crosshair) so look+place works in one frame. */
	public static BlockHitResult freshBlockHit(LocalPlayer p) {
		HitResult r = p.pick(p.blockInteractionRange(), 1.0f, false);
		if (r instanceof BlockHitResult bhr && r.getType() == HitResult.Type.BLOCK) return bhr;
		return null;
	}

	public static boolean useOn(InteractionHand hand, BlockHitResult hit) {
		Minecraft mc = mc();
		if (mc.player == null || mc.gameMode == null || hit == null) return false;
		InteractionResult res = mc.gameMode.useItemOn(mc.player, hand, hit);
		if (res.consumesAction()) mc.player.swing(hand);
		return res.consumesAction();
	}

	public static boolean useItem(InteractionHand hand) {
		Minecraft mc = mc();
		if (mc.player == null || mc.gameMode == null) return false;
		InteractionResult res = mc.gameMode.useItem(mc.player, hand);
		if (res.consumesAction()) mc.player.swing(hand);
		return res.consumesAction();
	}

	// ------------------------------------------------------------------ targeting

	public static Player nearestPlayer(LocalPlayer self, double range, String name) {
		if (self == null || mc().level == null) return null;
		Player best = null;
		double bestD = range * range;
		for (AbstractClientPlayer p : mc().level.players()) {
			if (p == self || !p.isAlive() || p.isSpectator()) continue;
			if (name != null && !name.isBlank() && !p.getName().getString().equalsIgnoreCase(name.trim())) continue;
			double d = p.distanceToSqr(self);
			if (d <= bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	public static LivingEntity nearestLiving(LocalPlayer self, double range, boolean hostileOnly) {
		if (self == null || mc().level == null) return null;
		LivingEntity best = null;
		double bestD = range * range;
		for (Entity e : mc().level.getEntities(self, self.getBoundingBox().inflate(range),
				e -> e instanceof LivingEntity && e.isAlive() && !(e instanceof ArmorStand) && !e.isSpectator())) {
			if (hostileOnly && !(e instanceof Enemy)) continue;
			double d = e.distanceToSqr(self);
			if (d <= bestD) {
				bestD = d;
				best = (LivingEntity) e;
			}
		}
		return best;
	}

	public static float[] anglesTo(LocalPlayer p, Vec3 target) {
		Vec3 eye = p.getEyePosition();
		double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
		double h = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
		return new float[]{yaw, Mth.clamp(pitch, -90f, 90f)};
	}

	public static void lookAt(LocalPlayer p, Vec3 target) {
		float[] a = anglesTo(p, target);
		setRotation(p, a[0], a[1], true);
	}

	/** Sets rotation; snap=true also sets the previous-frame values so the camera doesn't interpolate. */
	public static void setRotation(LocalPlayer p, float yaw, float pitch, boolean snap) {
		pitch = Mth.clamp(pitch, -90f, 90f);
		p.setYRot(yaw);
		p.setXRot(pitch);
		p.setYHeadRot(yaw);
		if (snap) {
			p.yRotO = yaw;
			p.xRotO = pitch;
		}
	}

	public static Vec3 aimPoint(Entity e) {
		return e.getEyePosition();
	}

	public static Vec3 aimPointHead(Entity e) {
		return e.getEyePosition();
	}

	public static Vec3 aimPointChest(Entity e) {
		Vec3 eye = e.getEyePosition();
		Vec3 feet = e.position();
		return new Vec3((eye.x + feet.x) / 2, (eye.y + feet.y) / 2, (eye.z + feet.z) / 2);
	}

	public static Vec3 aimPointFeet(Entity e) {
		return e.position();
	}

	public static float lerpAngle(float start, float end, float alpha) {
		float delta = Mth.wrapDegrees(end - start);
		return start + delta * Mth.clamp(alpha, 0.0f, 1.0f);
	}
}
