package com.cdp.test;

import com.cdp.config.DeathPenaltyConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.lang.reflect.Method;

public final class DeathPenaltyGameTest {
	@SuppressWarnings("removal")
	private static void verifyTravelersBackpackNativeSlot(GameTestHelper helper) {
		if (!FabricLoader.getInstance().isModLoaded("travelersbackpack")) {
			helper.succeed();
			return;
		}

		Item backpackItem = BuiltInRegistries.ITEM.getValue(
			Identifier.fromNamespaceAndPath("travelersbackpack", "standard")
		);
		helper.assertFalse(backpackItem == Items.AIR, "Traveler's Backpack standard item is not registered");

		DeathPenaltyConfig config = DeathPenaltyConfig.get(helper.getLevel().getServer());
		config.dropChance = 1.0;
		config.doDurabilityLoss = false;
		config.minExperienceLossPerc = 0.0;
		config.maxExperienceLossPerc = 0.0;
		config.whiteList.clear();
		config.whiteList.add(Identifier.fromNamespaceAndPath("travelersbackpack", "standard"));

		Component retainedMarker = Component.literal("cdp-travelers-retained-" + UUID.randomUUID());
		ServerPlayer oldPlayer = makeMockPlayerAtTestSite(helper);
		ItemStack retainedBackpack = new ItemStack(backpackItem);
		retainedBackpack.set(DataComponents.CUSTOM_NAME, retainedMarker);
		equipTravelersBackpack(oldPlayer, retainedBackpack);
		oldPlayer.setHealth(0.0F);
		oldPlayer.die(helper.getLevel().damageSources().genericKill());

		helper.runAfterDelay(5, () -> {
			ServerPlayer respawnedPlayer = helper.getLevel().getServer().getPlayerList().respawn(
				oldPlayer, false, Entity.RemovalReason.KILLED
			);
			helper.runAfterDelay(1, () -> {
				try {
					ItemStack restored = getWearingTravelersBackpack(respawnedPlayer);
					helper.assertTrue(retainedMarker.equals(restored.get(DataComponents.CUSTOM_NAME)),
						"White-listed equipped backpack was not restored after all attachment callbacks");
					helper.assertValueEqual(0, findMarkedDrops(helper.getLevel(), retainedMarker).size(),
						"White-listed equipped backpack unexpectedly dropped");
				} finally {
					helper.getLevel().getServer().getPlayerList().remove(respawnedPlayer);
				}

				verifyTravelersBackpackDropsExactlyOnce(helper, config, backpackItem);
			});
		});
	}

	@SuppressWarnings("removal")
	private static void verifyTravelersBackpackDropsExactlyOnce(GameTestHelper helper,
			DeathPenaltyConfig config, Item backpackItem) {
		config.whiteList.clear();
		config.dropChance = 1.0;
		config.nonStackableInsurance = false;
		Component droppedMarker = Component.literal("cdp-travelers-dropped-" + UUID.randomUUID());
		ServerPlayer oldPlayer = makeMockPlayerAtTestSite(helper);
		ItemStack droppedBackpack = new ItemStack(backpackItem);
		droppedBackpack.set(DataComponents.CUSTOM_NAME, droppedMarker);
		equipTravelersBackpack(oldPlayer, droppedBackpack);
		BlockPos deathPosition = oldPlayer.blockPosition();
		oldPlayer.setHealth(0.0F);
		oldPlayer.die(helper.getLevel().damageSources().genericKill());

		helper.runAfterDelay(5, () -> {
			ServerPlayer respawnedPlayer = helper.getLevel().getServer().getPlayerList().respawn(
				oldPlayer, false, Entity.RemovalReason.KILLED
			);
			try {
				helper.assertValueEqual(0, findMarkedDrops(helper.getLevel(), droppedMarker).size(),
					"Traveler's Backpack native placement was bypassed by a custom item drop");
				helper.assertTrue(findNearbyBlock(helper.getLevel(), deathPosition, Block.byItem(backpackItem)),
					"Drop-enabled equipped backpack was not placed by Traveler's Backpack's native death handler");
				helper.assertTrue(getWearingTravelersBackpack(respawnedPlayer).isEmpty(),
					"Dropped equipped backpack unexpectedly returned after respawn");
			} finally {
				helper.getLevel().getServer().getPlayerList().remove(respawnedPlayer);
				helper.killAllEntitiesOfClass(ItemEntity.class);
			}
			helper.succeed();
		});
	}

	private static boolean findNearbyBlock(ServerLevel level, BlockPos center, Block expectedBlock) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = center.getX() - 12; x <= center.getX() + 12; x++) {
			for (int y = Math.max(level.getMinY(), center.getY() - 12);
					y <= Math.min(level.getMaxY() - 1, center.getY() + 12); y++) {
				for (int z = center.getZ() - 12; z <= center.getZ() + 12; z++) {
					if (level.getBlockState(cursor.set(x, y, z)).is(expectedBlock)) return true;
				}
			}
		}
		return false;
	}

	private static List<? extends ItemEntity> findMarkedDrops(ServerLevel level, Component marker) {
		return level.getEntities(EntityTypes.ITEM,
			entity -> marker.equals(entity.getItem().get(DataComponents.CUSTOM_NAME)));
	}

	private static void equipTravelersBackpack(ServerPlayer player, ItemStack backpack) {
		invokeTravelersBackpackApi("equipBackpack", new Class<?>[]{net.minecraft.world.entity.player.Player.class, ItemStack.class},
			player, backpack);
	}

	private static ItemStack getWearingTravelersBackpack(ServerPlayer player) {
		Object result = invokeTravelersBackpackApi("getWearingBackpack",
			new Class<?>[]{net.minecraft.world.entity.player.Player.class}, player);
		return result instanceof ItemStack stack ? stack : ItemStack.EMPTY;
	}

	private static Object invokeTravelersBackpackApi(String methodName, Class<?>[] parameterTypes, Object... arguments) {
		try {
			Class<?> attachmentUtils = Class.forName("com.tiviacz.travelersbackpack.attachment.AttachmentUtils");
			Method method = attachmentUtils.getMethod(methodName, parameterTypes);
			return method.invoke(null, arguments);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Traveler's Backpack GameTest API call failed: " + methodName, exception);
		}
	}

	@SuppressWarnings("removal")
	@GameTest(maxTicks = 100)
	public void protectedArmorAndOffhandSurviveDeathAndRespawn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PlayerList playerList = level.getServer().getPlayerList();
		level.getGameRules().set(GameRules.KEEP_INVENTORY, false, level.getServer());

		DeathPenaltyConfig defaults = new DeathPenaltyConfig();
		helper.assertValueEqual(0.50, defaults.maxExperienceLossPerc,
			"Default maximum experience loss percentage");
		helper.assertTrue(defaults.nonStackableInsurance,
			"Non-stackable item insurance must be enabled by default");
		var validWhitelistParse = level.getServer().getCommands().getDispatcher().parse(
			"cdp whitelist add minecraft:diamond",
			level.getServer().createCommandSourceStack()
		);
		helper.assertFalse(validWhitelistParse.getReader().canRead(),
			"Registered whitelist item argument did not consume the complete identifier");
		helper.assertTrue(validWhitelistParse.getExceptions().isEmpty(),
			"Registered whitelist item produced a command parse error");
		var invalidWhitelistParse = level.getServer().getCommands().getDispatcher().parse(
			"cdp whitelist add othermod:not_registered",
			level.getServer().createCommandSourceStack()
		);
		helper.assertFalse(invalidWhitelistParse.getExceptions().isEmpty(),
			"Unregistered whitelist item was accepted by the command parser");
		DeathPenaltyConfig commandConfig = DeathPenaltyConfig.get(level.getServer());
		Identifier staleWhitelistId = Identifier.fromNamespaceAndPath("removedmod", "old_item");
		commandConfig.whiteList.add(staleWhitelistId);
		var removeWhitelistParse = level.getServer().getCommands().getDispatcher().parse(
			"cdp whitelist remove removed",
			level.getServer().createCommandSourceStack()
		);
		var removeSuggestions = level.getServer().getCommands().getDispatcher()
			.getCompletionSuggestions(removeWhitelistParse).join();
		helper.assertTrue(removeSuggestions.getList().stream()
			.anyMatch(suggestion -> staleWhitelistId.toString().equals(suggestion.getText())),
			"Whitelist removal did not suggest a stale entry from an unloaded mod");
		var commandHelpParse = level.getServer().getCommands().getDispatcher().parse(
			"cdp help command", level.getServer().createCommandSourceStack()
		);
		helper.assertFalse(commandHelpParse.getReader().canRead(),
			"Command help did not consume the complete command");
		helper.assertTrue(commandHelpParse.getExceptions().isEmpty(),
			"Command help did not parse");
		var parameterHelpParse = level.getServer().getCommands().getDispatcher().parse(
			"cdp help parameter nonStackableInsurance", level.getServer().createCommandSourceStack()
		);
		helper.assertFalse(parameterHelpParse.getReader().canRead(),
			"Known parameter help did not consume the complete command");
		helper.assertTrue(parameterHelpParse.getExceptions().isEmpty(),
			"Known parameter help did not parse");
		var unknownParameterHelpParse = level.getServer().getCommands().getDispatcher().parse(
			"cdp help parameter notAParameter", level.getServer().createCommandSourceStack()
		);
		helper.assertTrue(unknownParameterHelpParse.getReader().canRead()
			|| !unknownParameterHelpParse.getExceptions().isEmpty(),
			"Unknown parameter was accepted by the help command");

		DeathPenaltyConfig config = DeathPenaltyConfig.get(level.getServer());
		config.dropChance = 1.0;
		config.doArmorDrop = false;
		config.doMainhandDrop = true;
		config.doOffhandDrop = false;
		config.doToolbarDrop = false;
		config.nonStackableInsurance = false;
		config.doDurabilityLoss = false;
		config.minExperienceLossPerc = 0.0;
		config.maxExperienceLossPerc = 0.0;
		config.whiteList.clear();

		ServerPlayer oldPlayer = makeMockPlayerAtTestSite(helper);
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

	private static ServerPlayer makeMockPlayerAtTestSite(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 testPosition = helper.absoluteVec(new Vec3(0.5, 1.0, 0.5));
		player.setPos(testPosition.x, testPosition.y, testPosition.z);
		return player;
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
			new Combination(Items.WOODEN_SWORD, 1.0, 1.0, false, true, true),
			new Combination(Items.STONE_SWORD, 0.0, 0.0, false, false, false),
			new Combination(Items.IRON_SWORD, 1.0, 0.0, false, true, false),
			new Combination(Items.DIAMOND_SWORD, 0.0, 1.0, false, false, true),
			new Combination(Items.GOLDEN_SWORD, 1.0, 0.0, true, false, false)
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
		config.nonStackableInsurance = combination.nonStackableInsurance;

		Component testMarker = Component.literal("cdp-gametest-" + UUID.randomUUID());
		ItemStack original = new ItemStack(combination.item);
		original.set(DataComponents.CUSTOM_NAME, testMarker);
		ItemStack expected = original.copy();
		if (combination.shouldTakeDamage) {
			expected.setDamageValue((int) Math.ceil(expected.getMaxDamage() * config.minDurabilityLossPerc));
		}

		ServerPlayer oldPlayer = makeMockPlayerAtTestSite(helper);
		oldPlayer.setItemSlot(EquipmentSlot.MAINHAND, original.copy());
		oldPlayer.setHealth(0.0F);
		oldPlayer.die(level.damageSources().genericKill());

		// Item entities are committed to the level at the end of the current tick.
		helper.runAfterDelay(5, () -> {
			ServerPlayer respawnedPlayer = null;
			try {
				var droppedItems = level.getEntities(EntityTypes.ITEM,
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
			if (nextIndex < combinations.size()) {
				runCombination(helper, config, combinations, nextIndex);
			} else {
				verifyTravelersBackpackNativeSlot(helper);
			}
		});
	}

	private record Combination(
		Item item,
		double dropChance,
		double durabilityChance,
		boolean nonStackableInsurance,
		boolean shouldDrop,
		boolean shouldTakeDamage
	) { }
}
