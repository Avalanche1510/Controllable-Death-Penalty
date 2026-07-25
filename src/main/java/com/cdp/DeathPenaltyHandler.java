package com.cdp;

import com.cdp.config.DeathPenaltyConfig;
import com.cdp.access.CdpPlayerAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The single server-side path for all player death penalties. */
public final class DeathPenaltyHandler {
	private static final EquipmentSlot[] RETAINED_EQUIPMENT_SLOTS = {
		EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.OFFHAND
	};
	private static final Map<UUID, RetainedDeathState> RETAINED_DEATH_STATES = new HashMap<>();

	private DeathPenaltyHandler() { }

	public static void apply(ServerPlayer player, ServerLevel level) {
		DeathPenaltyConfig config = DeathPenaltyConfig.get(level.getServer());
		((CdpPlayerAccess) player).cdp$destroyVanishingCursedItems();
		RandomSource random = level.getRandom();
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
			processEquipment(player, slot, config.doArmorDrop, config, random);
		}
		Inventory inventory = player.getInventory();
		int selected = inventory.getSelectedSlot();
		// Since 26.1, getContainerSize() also includes mapped equipment slots
		// (armor, offhand, body and saddle). Those slots are handled separately
		// above/below and must never pass through the ordinary inventory rules.
		for (int slot = 0; slot < inventory.getNonEquipmentItems().size(); slot++) {
			boolean allowed = slot == selected ? config.doMainhandDrop : (!Inventory.isHotbarSlot(slot) || config.doToolbarDrop);
			processInventory(player, inventory, slot, allowed, config, random);
		}
		processEquipment(player, EquipmentSlot.OFFHAND, config.doOffhandDrop, config, random);
		processExperience(player, level, config);
		snapshotRetainedState(player);
	}

	private static void snapshotRetainedState(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		int nonEquipmentSlotCount = inventory.getNonEquipmentItems().size();
		List<ItemStack> retainedInventory = new ArrayList<>(nonEquipmentSlotCount);
		for (int slot = 0; slot < nonEquipmentSlotCount; slot++) {
			retainedInventory.add(inventory.getItem(slot).copy());
		}
		ItemStack[] retainedEquipment = new ItemStack[RETAINED_EQUIPMENT_SLOTS.length];
		for (int index = 0; index < RETAINED_EQUIPMENT_SLOTS.length; index++) {
			retainedEquipment[index] = player.getItemBySlot(RETAINED_EQUIPMENT_SLOTS[index]).copy();
		}
		RETAINED_DEATH_STATES.put(player.getUUID(), new RetainedDeathState(
			retainedInventory,
			inventory.getSelectedSlot(),
			retainedEquipment,
			player.experienceLevel,
			progressPoints(player),
			player.totalExperience
		));
	}

	/**
	 * Restores the exact post-penalty state to the newly created player instance.
	 * A state exists only after this handler has taken over an actual death.
	 */
	public static void restoreRetainedState(ServerPlayer player) {
		RetainedDeathState state = RETAINED_DEATH_STATES.remove(player.getUUID());
		if (state == null) return;

		Inventory inventory = player.getInventory();
		inventory.clearContent();
		int inventorySize = Math.min(inventory.getNonEquipmentItems().size(), state.inventory.size());
		for (int slot = 0; slot < inventorySize; slot++) {
			inventory.setItem(slot, state.inventory.get(slot).copy());
		}
		inventory.setSelectedSlot(state.selectedSlot);

		for (int index = 0; index < RETAINED_EQUIPMENT_SLOTS.length; index++) {
			player.setItemSlot(RETAINED_EQUIPMENT_SLOTS[index], state.equipment[index].copy());
		}
		player.setExperienceLevels(state.experienceLevel);
		player.setExperiencePoints(state.experienceProgressPoints);
		player.totalExperience = state.totalExperience;
	}

	private static void processInventory(ServerPlayer player, Inventory inventory, int slot, boolean allowed, DeathPenaltyConfig config, RandomSource random) {
		ItemStack stack = inventory.getItem(slot);
		if (processStack(player, stack, allowed, config, random)) inventory.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
	}

	private static void processEquipment(ServerPlayer player, EquipmentSlot slot, boolean allowed, DeathPenaltyConfig config, RandomSource random) {
		ItemStack stack = player.getItemBySlot(slot);
		if (processStack(player, stack, allowed, config, random)) player.setItemSlot(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
	}

	private static boolean processStack(ServerPlayer player, ItemStack stack, boolean allowed, DeathPenaltyConfig config, RandomSource random) {
		if (stack.isEmpty() || config.isWhitelisted(BuiltInRegistries.ITEM.getKey(stack.getItem()))) return false;

		boolean changed = false;
		if (config.doDurabilityLoss && !stack.isStackable() && stack.isDamageableItem()
				&& random.nextDouble() < config.durabilityLossChance) {
			changed = applyDurabilityLoss(stack, config, random);
		}

		// Durability is checked first. Without insurance it may destroy the item,
		// leaving nothing for the independent drop check to emit.
		if (stack.isEmpty()) return changed;

		boolean dropSucceeded = random.nextDouble() < config.dropChance;
		if (!allowed || !dropSucceeded) return changed;

		if (stack.isStackable()) {
			int dropCount = roundedPercentage(stack.getCount(), randomBetween(random, config.minDropPerc, config.maxDropPerc));
			if (config.stackInsurance) dropCount = Math.min(dropCount, Math.max(0, stack.getCount() - 1));
			if (dropCount > 0) {
				player.drop(stack.copyWithCount(dropCount), true, false);
				stack.shrink(dropCount);
			}
		} else {
			player.drop(stack.copy(), true, false);
			stack.setCount(0);
		}
		return true;
	}

	private static boolean applyDurabilityLoss(ItemStack stack, DeathPenaltyConfig config, RandomSource random) {
		int loss = roundedPercentage(stack.getMaxDamage(), randomBetween(random, config.minDurabilityLossPerc, config.maxDurabilityLossPerc));
		int newDamage = stack.getDamageValue() + loss;
		if (config.durabilityInsurance) newDamage = Math.min(newDamage, Math.max(0, stack.getMaxDamage() - 1));
		if (newDamage >= stack.getMaxDamage()) stack.setCount(0); else stack.setDamageValue(newDamage);
		return true;
	}

	private static void processExperience(ServerPlayer player, ServerLevel level, DeathPenaltyConfig config) {
		int currentExperience = currentExperience(player);
		int lost = Math.min(currentExperience, roundedPercentage(currentExperience, randomBetween(level.getRandom(), config.minExperienceLossPerc, config.maxExperienceLossPerc)));
		if (lost == 0) return;
		player.giveExperiencePoints(-lost);
		player.totalExperience = currentExperience - lost;
		int orbCount = Math.min(lost, config.maxExperienceOrbs);
		int baseValue = lost / orbCount;
		int remainder = lost % orbCount;
		for (int index = 0; index < orbCount; index++) {
			level.addFreshEntity(new ExperienceOrb(level, player.getX(), player.getY(), player.getZ(), baseValue + (index < remainder ? 1 : 0)));
		}
	}

	private static int currentExperience(ServerPlayer player) {
		long level = player.experienceLevel;
		long levelBase = level <= 16 ? level * level + 6L * level
			: level <= 31 ? (5L * level * level - 81L * level + 720L) / 2L
			: (9L * level * level - 325L * level + 4440L) / 2L;
		long progress = progressPoints(player);
		return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, levelBase + progress));
	}

	public static int progressPoints(ServerPlayer player) {
		return Math.clamp(Math.round(player.experienceProgress * player.getXpNeededForNextLevel()), 0, player.getXpNeededForNextLevel() - 1);
	}

	private static double randomBetween(RandomSource random, double min, double max) {
		return min + random.nextDouble() * (max - min);
	}

	private static int roundedPercentage(int base, double percentage) {
		return percentage <= 0.0 || base <= 0 ? 0 : (int) Math.ceil(base * percentage);
	}

	private record RetainedDeathState(
		List<ItemStack> inventory,
		int selectedSlot,
		ItemStack[] equipment,
		int experienceLevel,
		int experienceProgressPoints,
		int totalExperience
	) { }
}
