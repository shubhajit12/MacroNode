package macronode.macro.client.action;

import java.util.Locale;

import macronode.macro.client.engine.ExecContext;
import macronode.macro.client.engine.HeldKeys;
import macronode.macro.client.engine.Step;
import macronode.macro.client.util.ClientUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.phys.BlockHitResult;

/** Defensive & Survival Automation. */
final class DefenseActions {
	private static final long TICK = 50_000_000L;

	private DefenseActions() {
	}

	static void register() {
		// Select milk -> hold use until the drink finishes -> swap back.
		ActionRegistry.register(ActionType.DRINK_MILK, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			switch (ctx.stage) {
				case 0 -> {
					int orig = p.getInventory().getSelectedSlot();
					int slot = hotbarOrFetch(p, Items.MILK_BUCKET);
					if (slot < 0) return Step.NEXT;
					ctx.scratch.put("orig", orig);
					ctx.selectSlot(slot);
					ctx.hold(ctx.mc.options.keyUse, HeldKeys.FOREVER);
					ctx.setStage(1);
					return Step.WAITING;
				}
				case 1 -> {
					long el = ctx.elapsedInAction();
					if ((el > 3 * TICK && !p.isUsingItem()) || el >= b.getNanos("maxHold")) {
						ctx.release(ctx.mc.options.keyUse);
						ctx.setStage(2);
						return Step.repeat(TICK);
					}
					return Step.WAITING;
				}
				default -> {
					if (b.getBool("restore")) p.getInventory().setSelectedSlot(ctx.get("orig", p.getInventory().getSelectedSlot()));
					return Step.NEXT;
				}
			}
		});

		ActionRegistry.register(ActionType.WAIT_DEBUFF, (ctx, b) ->
				Waits.until(ctx, b, effectActive(ctx.player(), b.get("effect"))));

		ActionRegistry.register(ActionType.MLG_WATER, DefenseActions::mlg);
	}

	/**
	 * Stage 0: detect a dangerous fall. 1: water bucket out, pitch +90, place when the ground is in reach.
	 * 2: wait for landing. 3: scoop the water back up. 4: restore slot + camera.
	 */
	private static Step mlg(ExecContext ctx, ActionBlock b) {
		LocalPlayer p = ctx.player();
		boolean timeout = ctx.elapsedInAction() >= b.getNanos("timeout");
		switch (ctx.stage) {
			case 0 -> {
				boolean falling = !p.onGround() && p.fallDistance >= b.getDouble("minFall") && p.getDeltaMovement().y < 0;
				if (!falling) return b.getBool("waitForFall") && !timeout ? Step.WAITING : Step.NEXT;
				int orig = p.getInventory().getSelectedSlot();
				int slot = hotbarOrFetch(p, Items.WATER_BUCKET);
				if (slot < 0) return Step.NEXT;
				ctx.scratch.put("orig", orig);
				ctx.scratch.put("yaw", p.getYRot());
				ctx.scratch.put("pitch", p.getXRot());
				ctx.selectSlot(slot);
				ClientUtil.setRotation(p, p.getYRot(), 90f, true);
				ctx.setStage(1);
				return Step.WAITING;
			}
			case 1 -> {
				ClientUtil.setRotation(p, p.getYRot(), 90f, true);
				if (p.onGround() || p.isInWater() || timeout) return finishMlg(ctx);
				BlockHitResult hit = ClientUtil.freshBlockHit(p);
				if (hit != null && p.getY() - (hit.getBlockPos().getY() + 1) <= 3.0) {
					ClientUtil.useItem(InteractionHand.MAIN_HAND);
					ctx.setStage(2);
				}
				return Step.WAITING;
			}
			case 2 -> {
				if (!b.getBool("scoop")) return finishMlg(ctx);
				if (p.onGround() || p.isInWater() || timeout) {
					ctx.setStage(3);
					return Step.repeat(2 * TICK);
				}
				return Step.WAITING;
			}
			case 3 -> {
				ClientUtil.setRotation(p, p.getYRot(), 90f, true);
				if (p.getMainHandItem().is(Items.BUCKET)) ClientUtil.useItem(InteractionHand.MAIN_HAND);
				ctx.setStage(4);
				return Step.repeat(TICK);
			}
			default -> {
				return finishMlg(ctx);
			}
		}
	}

	private static Step finishMlg(ExecContext ctx) {
		LocalPlayer p = ctx.player();
		p.getInventory().setSelectedSlot(ctx.get("orig", p.getInventory().getSelectedSlot()));
		ClientUtil.setRotation(p, ctx.get("yaw", p.getYRot()), ctx.get("pitch", p.getXRot()), true);
		return Step.NEXT;
	}

	/** Hotbar slot holding the item, pulling it from the main inventory into an empty/current slot if needed. */
	static int hotbarOrFetch(LocalPlayer p, Item item) {
		int slot = ClientUtil.findHotbar(p, ClientUtil.itemMatcher(item));
		if (slot >= 0) return slot;
		int src = ClientUtil.findInventory(p, ClientUtil.itemMatcher(item), 9, 36);
		if (src < 0) return -1;
		int dst = ClientUtil.emptyHotbarSlot(p);
		if (dst < 0) dst = p.getInventory().getSelectedSlot();
		return ClientUtil.click(ClientUtil.menuSlot(src), dst, ClickType.SWAP) ? dst : -1;
	}

	/** True if the named effect (or any harmful effect for ANY_HARMFUL) is active on the player. */
	static boolean effectActive(LocalPlayer p, String name) {
		String n = name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
		if (n.isEmpty() || n.equals("ANY_HARMFUL")) {
			for (MobEffectInstance inst : p.getActiveEffects()) {
				if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) return true;
			}
			return false;
		}
		Holder<MobEffect> effect = switch (n) {
			case "WEAKNESS" -> MobEffects.WEAKNESS;
			case "SLOWNESS" -> MobEffects.SLOWNESS;
			case "POISON" -> MobEffects.POISON;
			case "WITHER" -> MobEffects.WITHER;
			case "MINING_FATIGUE" -> MobEffects.MINING_FATIGUE;
			case "BLINDNESS" -> MobEffects.BLINDNESS;
			case "NAUSEA" -> MobEffects.NAUSEA;
			case "HUNGER" -> MobEffects.HUNGER;
			case "LEVITATION" -> MobEffects.LEVITATION;
			case "DARKNESS" -> MobEffects.DARKNESS;
			default -> null;
		};
		return effect != null && p.hasEffect(effect);
	}
}
