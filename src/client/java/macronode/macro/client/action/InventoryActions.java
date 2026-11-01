package macronode.macro.client.action;

import java.util.Locale;
import java.util.function.Predicate;

import macronode.macro.client.engine.Step;
import macronode.macro.client.util.ClientUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/** Inventory & Equipment blocks. Slot moves use real container clicks, exactly as a player would. */
final class InventoryActions {
	private InventoryActions() {
	}

	static void register() {
		ActionRegistry.register(ActionType.SELECT_SLOT, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			String s = b.get("slot").trim().toLowerCase(Locale.ROOT);
			if (s.matches("[1-9]")) {
				ctx.selectSlot(Integer.parseInt(s) - 1);
			} else if (s.equals("previous") || s.equals("prev")) {
				if (ctx.previousSlot >= 0) p.getInventory().setSelectedSlot(ctx.previousSlot);
			} else {
				Predicate<ItemStack> m = ClientUtil.itemMatcher(s);
				int slot = ClientUtil.findHotbar(p, m);
				if (slot >= 0) {
					ctx.selectSlot(slot);
				} else if (b.getBool("fetch")) {
					// Not on the hotbar: swap it from the main inventory into an empty (or the current) hotbar slot.
					int src = ClientUtil.findInventory(p, m, 9, 36);
					if (src >= 0) {
						int dst = ClientUtil.emptyHotbarSlot(p);
						if (dst < 0) dst = p.getInventory().getSelectedSlot();
						if (ClientUtil.click(ClientUtil.menuSlot(src), dst, ClickType.SWAP)) ctx.selectSlot(dst);
					}
				}
			}
			return Step.NEXT;
		});

		ActionRegistry.register(ActionType.SWAP_HANDS, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			if (!p.isSpectator() && ctx.mc.getConnection() != null) {
				ctx.mc.getConnection().send(new ServerboundPlayerActionPacket(
						ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
			}
			return Step.NEXT;
		});

		ActionRegistry.register(ActionType.DROP_ITEM, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			if (!p.isSpectator() && p.drop("STACK".equals(b.get("mode")))) p.swing(InteractionHand.MAIN_HAND);
			return Step.NEXT;
		});

		ActionRegistry.register(ActionType.INVENTORY_SCREEN, (ctx, b) -> {
			Minecraft mc = ctx.mc;
			LocalPlayer p = ctx.player();
			String mode = b.get("mode");
			boolean open = mc.screen instanceof AbstractContainerScreen<?>;
			if (mode.equals("OPEN") || (mode.equals("TOGGLE") && !open)) {
				if (mc.screen == null) mc.setScreen(new InventoryScreen(p));
			} else if (open) {
				p.closeContainer();
			}
			return Step.NEXT;
		});

		ActionRegistry.register(ActionType.OFFHAND_ITEM, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			Predicate<ItemStack> m = ClientUtil.itemMatcher(b.get("item"));
			if (m.test(p.getOffhandItem())) return Step.NEXT;
			int src = ClientUtil.findInventory(p, m, 0, 36);
			// Button 40 + SWAP = vanilla "F" key swap of the hovered slot with the offhand.
			if (src >= 0) ClientUtil.click(ClientUtil.menuSlot(src), 40, ClickType.SWAP);
			return Step.NEXT;
		});

		ActionRegistry.register(ActionType.EQUIP_ARMOR, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			EquipmentSlot target = switch (b.get("piece")) {
				case "HELMET" -> EquipmentSlot.HEAD;
				case "LEGGINGS" -> EquipmentSlot.LEGS;
				case "BOOTS" -> EquipmentSlot.FEET;
				default -> EquipmentSlot.CHEST;
			};
			int armorMenuSlot = switch (target) {
				case HEAD -> 5;
				case CHEST -> 6;
				case LEGS -> 7;
				default -> 8;
			};
			String itemSpec = b.get("item").trim();
			Predicate<ItemStack> idMatch = itemSpec.isEmpty() ? s -> true : ClientUtil.itemMatcher(itemSpec);
			int src = ClientUtil.findInventory(p, s -> {
				Equippable eq = s.get(DataComponents.EQUIPPABLE);
				return !s.isEmpty() && eq != null && eq.slot() == target && idMatch.test(s);
			}, 0, 36);
			if (src < 0) return Step.NEXT;
			int menu = ClientUtil.menuSlot(src);
			if (p.inventoryMenu.getSlot(armorMenuSlot).getItem().isEmpty()) {
				ClientUtil.click(menu, 0, ClickType.QUICK_MOVE);
			} else {
				ClientUtil.click(menu, 0, ClickType.PICKUP);
				ClientUtil.click(armorMenuSlot, 0, ClickType.PICKUP);
				ClientUtil.click(menu, 0, ClickType.PICKUP);
			}
			return Step.NEXT;
		});

		ActionRegistry.register(ActionType.AUTO_REFILL, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			String s = b.get("slot").trim();
			int hot = s.matches("[1-9]") ? Integer.parseInt(s) - 1 : p.getInventory().getSelectedSlot();
			ItemStack cur = p.getInventory().getItem(hot);
			String spec = b.get("item").trim();
			Predicate<ItemStack> m;
			if (!spec.isEmpty()) m = ClientUtil.itemMatcher(spec);
			else if (!cur.isEmpty()) m = ClientUtil.itemMatcher(cur.getItem());
			else return Step.NEXT;
			if (!cur.isEmpty() && (cur.getCount() > b.getInt("threshold") || !m.test(cur))) return Step.NEXT;
			int src = ClientUtil.findInventory(p, m, 9, 36);
			if (src < 0) return Step.NEXT;
			if (cur.isEmpty()) ClientUtil.click(ClientUtil.menuSlot(src), hot, ClickType.SWAP);
			else ClientUtil.click(ClientUtil.menuSlot(src), 0, ClickType.QUICK_MOVE); // merges into hotbar stack
			return Step.NEXT;
		});

		ActionRegistry.register(ActionType.QUICK_CLICK, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			ClickType type;
			try {
				type = ClickType.valueOf(b.get("click"));
			} catch (IllegalArgumentException e) {
				type = ClickType.PICKUP;
			}
			int slot = b.getInt("slot");
			boolean valid = slot == -999 || (slot >= 0 && slot < p.containerMenu.slots.size());
			if (valid && ctx.mc.gameMode != null) {
				ctx.mc.gameMode.handleInventoryMouseClick(p.containerMenu.containerId, slot, b.getInt("button"), type, p);
			}
			return Step.NEXT;
		});
	}
}
