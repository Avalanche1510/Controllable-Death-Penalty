package com.cdp.test;

import com.cdp.config.DeathPenaltyConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DeathPenaltyGameTest {
	@SuppressWarnings("removal")
	@GameTest(maxTicks = 40)
	public void protectedArmorAndOffhandSurviveDeathAndRespawn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PlayerList playerList = level.getServer().getPlayerList();
		level.getGameRules().set(GameRules.KEEP_INVENTORY, false, level.getServer());

		DeathPenaltyConfig defaults = new DeathPenaltyConfig();
		helper.assertValueEqual(0.50, defaults.maxExperienceLossPerc,
			"Default maximum experience loss percentage");
		var whitelistParse = level.getServer().getCommands().getDispatcher().parse(
			"cdp whitelist add othermod:test_item",
			level.getServer().createCommandSourceStack()
		);
		helper.assertFalse(whitelistParse.getReader().canRead(),
			"Whitelist identifier argument did not consume the namespace colon");
		helper.assertTrue(whitelistParse.getExceptions().isEmpty(),
			"Whitelist identifier with a namespace produced a command parse error");

		DeathPenaltyConfig config = DeathPenaltyConfig.get(level.getServer());
		config.dropChance = 1.0;
		config.doArmorDrop = false;
		config.doMainhandDrop = true;
		config.doOffhandDrop = false;
		config.doToolbarDrop = false;
		config.doDurabilityLoss = false;
		config.minExperienceLossPerc = 0.0;
		config.maxExperienceLossPerc = 0.0;
		config.whiteList.clear();

		ServerPlayer oldPlayer = helper.makeMockServerPlayerInLevel();
		ServerPlayer respawnedPlayer = null;
		Map<EquipmentSlot, ItemStack> protectedEquipment = new EnumMap<>(EquipmentSlot.class);
		protectedEquipment.put(EquipmentSlot.FEET, damaged(Items.DIAMOND_BOOTS, 11));
		protectedEquipment.put(EquipmentSlot.LEGS, damaged(Items.DIAMOND_LEGGINGS, 12));
		protectedEquipment.put(EquipmentSlot.CHEST, damaged(Items.DIAMOND_CHESTPLATE, 13));
		protectedEquipment.put(EquipmentSlot.HEAD, damaged(Items.DIAMOND_HELMET, 14));
		protectedEquipment.put(EquipmentSlot.OFFHAND, damaged(Items.SHIELD, 15));

		try {
			protectedEquipment.forEach((slot, stack) -> oldPlayer.setItemSlot(slot, stack.copy()));
			oldPlayer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));

			oldPlayer.setHealth(0.0F);
			oldPlayer.die(level.damageSources().genericKill());

			assertProtectedEquipment(helper, oldPlayer, protectedEquipment, "after death processing");
			helper.assertTrue(oldPlayer.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(),
				"Mainhand control item did not drop; the custom death handler was not exercised");

			respawnedPlayer = playerList.respawn(oldPlayer, false, Entity.RemovalReason.KILLED);
			assertProtectedEquipment(helper, respawnedPlayer, protectedEquipment, "after respawn");
			helper.assertTrue(respawnedPlayer.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(),
				"Mainhand control item unexpectedly returned after respawn");
		} finally {
			playerList.remove(respawnedPlayer == null ? oldPlayer : respawnedPlayer);
		}

		verifyIndependentDurabilityAndDropChecks(helper, config);
	}

	private static ItemStack damaged(Item item, int damage) {
		ItemStack stack = new ItemStack(item);
		stack.setDamageValue(damage);
		return stack;
	}

	private static void assertProtectedEquipment(GameTestHelper helper, ServerPlayer player,
			Map<EquipmentSlot, ItemStack> expectedEquipment, String stage) {
		expectedEquipment.forEach((slot, expected) -> helper.assertTrue(
			ItemStack.matches(expected, player.getItemBySlot(slot)),
			"Protected slot " + slot + " changed " + stage
		));
	}

	@SuppressWarnings("removal")
	private static void verifyIndependentDurabilityAndDropChecks(GameTestHelper helper, DeathPenaltyConfig config) {
		config.doMainhandDrop = true;
		config.doDurabilityLoss = true;
		config.minDurabilityLossPerc = 0.10;
		config.maxDurabilityLossPerc = 0.10;
		config.durabilityInsurance = true;

		List<Combination> combinations = List.of(
			new Combination(Items.WOODEN_SWORD, 1.0, 1.0, true, true),
			new Combination(Items.STONE_SWORD, 0.0, 0.0, false, false),
			new Combination(Items.IRON_SWORD, 1.0, 0.0, true, false),
			new Combination(Items.DIAMOND_SWORD, 0.0, 1.0, false, true)
		);
		runCombination(helper, config, combinations, 0);
	}

	@SuppressWarnings("removal")
	private static void runCombination(GameTestHelper helper, DeathPenaltyConfig config,
			List<Combination> combinations, int combinationIndex) {
		Combination combination = combinations.get(combinationIndex);
		ServerLevel level = helper.getLevel();
		PlayerList playerList = level.getServer().getPlayerList();
		helper.killAllEntitiesOfClass(ItemEntity.class);

		config.dropChance = combination.dropChance;
		config.durabilityLossChance = combination.durabilityChance;

		Component testMarker = Component.literal("cdp-gametest-" + UUID.randomUUID());
		ItemStack original = new ItemStack(combination.item);
		original.set(DataComponents.CUSTOM_NAME, testMarker);
		ItemStack expected = original.copy();
		if (combination.shouldTakeDamage) {
			expected.setDamageValue((int) Math.ceil(expected.getMaxDamage() * config.minDurabilityLossPerc));
		}

		ServerPlayer oldPlayer = helper.makeMockServerPlayerInLevel();
		oldPlayer.setItemSlot(EquipmentSlot.MAINHAND, original.copy());
		oldPlayer.setHealth(0.0F);
		oldPlayer.die(level.damageSources().genericKill());

		// Item entities are committed to the level at the end of the current tick.
		helper.runAfterDelay(5, () -> {
			ServerPlayer respawnedPlayer = null;
			try {
				var droppedItems = level.getEntities(EntityType.ITEM,
					entity -> testMarker.equals(entity.getItem().get(DataComponents.CUSTOM_NAME)));
				if (combination.shouldDrop) {
					helper.assertValueEqual(1, droppedItems.size(), "Expected exactly one dropped " + combination.item);
					helper.assertTrue(ItemStack.matches(expected, droppedItems.getFirst().getItem()),
						"Dropped item has the wrong durability for dropChance=" + combination.dropChance
							+ ", durabilityLossChance=" + combination.durabilityChance);
					helper.assertTrue(oldPlayer.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(),
						"Dropped item remained in the old player's main hand");
				} else {
					helper.assertValueEqual(0, droppedItems.size(), "Item dropped despite dropChance=" + combination.dropChance);
					helper.assertTrue(ItemStack.matches(expected, oldPlayer.getItemBySlot(EquipmentSlot.MAINHAND)),
						"Retained item has the wrong durability for dropChance=" + combination.dropChance
							+ ", durabilityLossChance=" + combination.durabilityChance);
				}

				respawnedPlayer = playerList.respawn(oldPlayer, false, Entity.RemovalReason.KILLED);
				ItemStack expectedAfterRespawn = combination.shouldDrop ? ItemStack.EMPTY : expected;
				helper.assertTrue(ItemStack.matches(expectedAfterRespawn, respawnedPlayer.getItemBySlot(EquipmentSlot.MAINHAND)),
					"Combination result changed during respawn");
			} finally {
				playerList.remove(respawnedPlayer == null ? oldPlayer : respawnedPlayer);
				helper.killAllEntitiesOfClass(ItemEntity.class);
			}

			int nextIndex = combinationIndex + 1;
			if (nextIndex < combinations.size()) runCombination(helper, config, combinations, nextIndex);
			else helper.succeed();
		});
	}

	private record Combination(
		Item item,
		double dropChance,
		double durabilityChance,
		boolean shouldDrop,
		boolean shouldTakeDamage
	) { }
}
