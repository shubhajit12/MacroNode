package macronode.macro.client.action;

import java.util.function.Predicate;

import macronode.macro.client.engine.Step;
import macronode.macro.client.util.ClientUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;

/** Deep Inventory Intelligence. Check Item Count is evaluated by the engine via {@link LogicActions#itemCount}. */
final class InventoryIntelActions {
	private InventoryIntelActions() {
	}

	static void register() {
		ActionRegistry.register(ActionType.FIND_MOVE_ITEM, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			int hot = Math.max(1, Math.min(9, b.getInt("slot"))) - 1;
			Predicate<ItemStack> m = ClientUtil.itemMatcher(b.get("item"));
			ItemStack cur = p.getInventory().getItem(hot);
			boolean depleted = cur.isEmpty() || (m.test(cur) && cur.getCount() <= b.getInt("threshold"));
			if (!depleted && !(b.getBool("force") && !m.test(cur))) return Step.NEXT;
			int src = ClientUtil.findInventory(p, m, 9, 36);
			// SWAP puts the full stack into the hotbar slot; whatever was there goes back to the inventory slot.
			if (src >= 0) ClientUtil.click(ClientUtil.menuSlot(src), hot, ClickType.SWAP);
			return Step.NEXT;
		});

		ActionRegistry.register(ActionType.CHECK_ITEM_COUNT, (ctx, b) -> Step.NEXT); // interpreted by MacroRun

		ActionRegistry.register(ActionType.AUTO_ARMOR_SWAP, (ctx, b) -> {
			LocalPlayer p = ctx.player();
			String mat = b.get("material").trim().toLowerCase();
			Predicate<ItemStack> elytra = s -> s.is(Items.ELYTRA);
			switch (b.get("mode")) {
				case "TO_ELYTRA" -> equip(p, EquipmentSlot.CHEST, elytra);
				case "TO_CHESTPLATE" -> equip(p, EquipmentSlot.CHEST, armor(EquipmentSlot.CHEST, mat));
				case "FULL_SET" -> {
					for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
						equip(p, slot, armor(slot, mat));
					}
				}
				default -> {
					if (p.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) equip(p, EquipmentSlot.CHEST, armor(EquipmentSlot.CHEST, mat));
					else equip(p, EquipmentSlot.CHEST, elytra);
				}
			}
			return Step.NEXT;
		});
	}

	/** Armor (not elytra) for a slot, optionally filtered by id substring such as "netherite" or "diamond". */
	private static Predicate<ItemStack> armor(EquipmentSlot slot, String material) {
		return s -> {
			if (s.isEmpty() || s.is(Items.ELYTRA)) return false;
			Equippable eq = s.get(DataComponents.EQUIPPABLE);
			if (eq == null || eq.slot() != slot) return false;
			return material.isEmpty() || BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().contains(material);
		};
	}

	/** Moves the first matching inventory item into an armor slot (quick-move if empty, 3-click swap otherwise). */
	private static boolean equip(LocalPlayer p, EquipmentSlot slot, Predicate<ItemStack> match) {
		if (match.test(p.getItemBySlot(slot))) return false;
		int armorMenu = switch (slot) {
			case HEAD -> 5;
			case CHEST -> 6;
			case LEGS -> 7;
			default -> 8;
		};
		int src = ClientUtil.findInventory(p, match, 0, 36);
		if (src < 0) return false;
		int menu = ClientUtil.menuSlot(src);
		if (p.inventoryMenu.getSlot(armorMenu).getItem().isEmpty()) {
			return ClientUtil.click(menu, 0, ClickType.QUICK_MOVE);
		}
		return ClientUtil.click(menu, 0, ClickType.PICKUP)
				&& ClientUtil.click(armorMenu, 0, ClickType.PICKUP)
				&& ClientUtil.click(menu, 0, ClickType.PICKUP);
	}
}
