package macronode.macro.client.action;

import macronode.macro.client.engine.HeldKeys;
import macronode.macro.client.engine.Step;
import macronode.macro.client.mixin.MinecraftInvoker;
import macronode.macro.client.util.ClientUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Combat & Player Action blocks. */
final class CombatActions {
	private static final long TICK = 50_000_000L;

	private CombatActions() {
	}

	/** Vanilla left-click path (entity attack / block start-break / miss swing). */
	static void attack(Minecraft mc) {
		mc.missTime = 0;
		((MinecraftInvoker) mc).macronode$startAttack();
	}

	static void register() {
		ActionRegistry.register(ActionType.ATTACK, (ctx, b) -> {
			attack(ctx.mc);
			return Step.NEXT;
		});

		ActionRegistry.register(ActionType.USE_ITEM, (ctx, b) -> {
			Minecraft mc = ctx.mc;
			LocalPlayer p = ctx.player();
			switch (b.get("mode")) {
				case "HOLD" -> {
					// Hold vanilla "use" so eating/drawing/blocking runs through the normal tick code.
					if (ctx.stage == 0) {
						ctx.hold(mc.options.keyUse, HeldKeys.FOREVER);
						ctx.setStage(1);
						return Step.repeatNextFrame();
					}
					long el = ctx.elapsedInAction();
					boolean finishedUsing = el > 3 * TICK && !p.isUsingItem();
					if (el >= b.getNanos("duration") || finishedUsing) {
						ctx.release(mc.options.keyUse);
						return Step.NEXT;
					}
					return Step.repeatNextFrame();
				}
				case "RELEASE" -> {
					ctx.release(mc.options.keyUse);
					if (p.isUsingItem() && mc.gameMode != null) mc.gameMode.releaseUsingItem(p);
					return Step.NEXT;
				}
				default -> {
					if ("OFF".equals(b.get("hand"))) ClientUtil.useItem(InteractionHand.OFF_HAND);
					else ((MinecraftInvoker) mc).macronode$startUseItem();
					return Step.NEXT;
				}
			}
		});

		ActionRegistry.register(ActionType.INTERACT_ENTITY, (ctx, b) -> {
			Minecraft mc = ctx.mc;
			LocalPlayer p = ctx.player();
			double range = b.getDouble("range");
			Entity e = mc.crosshairPickEntity;
			if (e == null) e = ClientUtil.nearestLiving(p, range, false);
			if (e != null && e.distanceTo(p) <= range && mc.gameMode != null) {
				for (InteractionHand hand : InteractionHand.values()) {
					InteractionResult r = mc.gameMode.interact(p, e, hand);
					if (r.consumesAction()) {
						p.swing(hand);
						break;
					}
				}
			}
			return Step.NEXT;
		});

		ActionRegistry.register(ActionType.BREAK_BLOCK, (ctx, b) ->
				ActionRegistry.holdFor(ctx, ctx.mc.options.keyAttack, b.getNanos("duration")));

		// Jump, wait until falling (fallDistance > 0, airborne) then attack => vanilla critical hit.
		ActionRegistry.register(ActionType.CRIT_SWING, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			if (ctx.stage == 0) {
				if (p.onGround()) ctx.hold(ctx.mc.options.keyJump, 2 * TICK);
				ctx.setStage(1);
				return Step.repeatNextFrame();
			}
			boolean falling = !p.onGround() && p.fallDistance > 0.0 && p.getDeltaMovement().y < 0;
			if (falling || ctx.elapsedInAction() >= b.getNanos("timeout")) {
				attack(ctx.mc);
				return Step.NEXT;
			}
			return Step.repeatNextFrame();
		});

		ActionRegistry.register(ActionType.AUTO_SHIELD, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			if (ctx.stage == 0) {
				if (!p.getOffhandItem().is(Items.SHIELD)) {
					int slot = ClientUtil.findHotbar(p, ClientUtil.itemMatcher(Items.SHIELD));
					if (slot < 0) return Step.NEXT;
					ctx.scratch.put("restore", p.getInventory().getSelectedSlot());
					ctx.selectSlot(slot);
				}
				ctx.hold(ctx.mc.options.keyUse, HeldKeys.FOREVER);
				ctx.setStage(1);
				return Step.repeat(b.getNanos("duration"));
			}
			ctx.release(ctx.mc.options.keyUse);
			Integer restore = ctx.get("restore", null);
			if (restore != null) p.getInventory().setSelectedSlot(restore);
			return Step.NEXT;
		});

		ActionRegistry.register(ActionType.CRYSTAL, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			if (ctx.stage == 0) {
				InteractionHand hand = InteractionHand.MAIN_HAND;
				if (p.getOffhandItem().is(Items.END_CRYSTAL)) {
					hand = InteractionHand.OFF_HAND;
				} else {
					int slot = ClientUtil.findHotbar(p, ClientUtil.itemMatcher(Items.END_CRYSTAL));
					if (slot < 0) return Step.NEXT;
					ctx.selectSlot(slot);
				}
				BlockHitResult hit = ClientUtil.freshBlockHit(p);
				if (hit != null) {
					BlockState st = p.level().getBlockState(hit.getBlockPos());
					if (st.is(Blocks.OBSIDIAN) || st.is(Blocks.BEDROCK)) ClientUtil.useOn(hand, hit);
				}
				ctx.setStage(1);
				return Step.repeat(b.getNanos("delay"));
			}
			double range = b.getDouble("range");
			Entity target = ctx.mc.crosshairPickEntity instanceof EndCrystal ec ? ec : null;
			if (target == null) {
				double best = range * range;
				for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(range), e -> e instanceof EndCrystal)) {
					double d = e.distanceToSqr(p);
					if (d <= best) {
						best = d;
						target = e;
					}
				}
			}
			if (target != null && ctx.mc.gameMode != null) {
				ctx.mc.gameMode.attack(p, target);
				p.swing(InteractionHand.MAIN_HAND);
			}
			return Step.NEXT;
		});

		// Place anchor (if not already looking at one) -> charge with glowstone -> blow with non-glowstone item.
		ActionRegistry.register(ActionType.ANCHOR, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			long delay = b.getNanos("delay");
			switch (ctx.stage) {
				case 0 -> {
					ctx.scratch.put("orig", p.getInventory().getSelectedSlot());
					BlockHitResult hit = ClientUtil.freshBlockHit(p);
					if (hit == null) return Step.NEXT;
					BlockPos pos;
					if (p.level().getBlockState(hit.getBlockPos()).is(Blocks.RESPAWN_ANCHOR)) {
						pos = hit.getBlockPos();
					} else {
						int slot = ClientUtil.findHotbar(p, ClientUtil.itemMatcher(Items.RESPAWN_ANCHOR));
						if (slot < 0) return Step.NEXT;
						ctx.selectSlot(slot);
						ClientUtil.useOn(InteractionHand.MAIN_HAND, hit);
						pos = hit.getBlockPos().relative(hit.getDirection());
					}
					ctx.scratch.put("pos", pos);
					ctx.setStage(1);
					return Step.repeat(delay);
				}
				case 1 -> {
					int glow = ClientUtil.findHotbar(p, ClientUtil.itemMatcher(Items.GLOWSTONE));
					if (glow < 0) return Step.NEXT;
					ctx.selectSlot(glow);
					ClientUtil.useOn(InteractionHand.MAIN_HAND, anchorHit(ctx.get("pos", BlockPos.ZERO)));
					ctx.setStage(2);
					return Step.repeat(delay);
				}
				default -> {
					int slot = ClientUtil.findHotbar(p, ClientUtil.itemMatcher(Items.TOTEM_OF_UNDYING));
					if (slot < 0) {
						for (int i = 0; i < 9; i++) {
							var st = p.getInventory().getItem(i);
							if (!st.is(Items.GLOWSTONE) && !st.is(Items.RESPAWN_ANCHOR)) {
								slot = i;
								break;
							}
						}
					}
					if (slot >= 0) {
						ctx.selectSlot(slot);
						ClientUtil.useOn(InteractionHand.MAIN_HAND, anchorHit(ctx.get("pos", BlockPos.ZERO)));
					}
					if (b.getBool("restore")) p.getInventory().setSelectedSlot(ctx.get("orig", p.getInventory().getSelectedSlot()));
					return Step.NEXT;
				}
			}
		});
	}

	private static BlockHitResult anchorHit(BlockPos pos) {
		return new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
	}
}
