package com.cdp.compat;

import com.cdp.ControllableDeathPenalty;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Optional compatibility with Traveler's Backpack's native equipped-backpack
 * attachment. Reflection keeps Traveler's Backpack an optional dependency.
 */
public final class TravelersBackpackCompat {
	private static final String MOD_ID = "travelersbackpack";
	private static final Api API = loadApi();

	private TravelersBackpackCompat() { }

	/**
	 * Returns the native equipped backpack stack. The stack is owned by the
	 * attachment and may be processed in place. Trinkets integration is left to
	 * Trinkets because it is not the native Traveler's Backpack attachment.
	 */
	public static ItemStack getEquippedBackpack(ServerPlayer player) {
		if (API == null || API.usesExternalEquipmentIntegration()) return ItemStack.EMPTY;
		try {
			Object result = API.getWearingBackpack.invoke(null, player);
			return result instanceof ItemStack stack ? stack : ItemStack.EMPTY;
		} catch (ReflectiveOperationException | LinkageError exception) {
			logFailure("read", exception);
			return ItemStack.EMPTY;
		}
	}

	/**
	 * Clears the old player's native attachment after CDP has processed it.
	 * This prevents Traveler's Backpack's AFTER_DEATH handler from applying its
	 * own second drop operation.
	 */
	public static void detachFromDeadPlayer(ServerPlayer player) {
		if (API == null || API.usesExternalEquipmentIntegration()) return;
		try {
			Object result = API.getAttachment.invoke(null, player);
			if (result instanceof Optional<?> optional && optional.isPresent()) {
				API.remove.invoke(optional.get(), player);
			}
		} catch (ReflectiveOperationException | LinkageError exception) {
			logFailure("detach", exception);
		}
	}

	public static void restoreEquippedBackpack(ServerPlayer player, ItemStack backpack) {
		if (API == null || backpack.isEmpty() || API.usesExternalEquipmentIntegration()) return;
		try {
			API.equipBackpack.invoke(null, player, backpack.copy());
		} catch (ReflectiveOperationException | LinkageError exception) {
			logFailure("restore", exception);
		}
	}

	/**
	 * Calls Traveler's Backpack's own death-placement entry point immediately.
	 *
	 * @return true when the backpack was placed and fully handled; false when
	 * the native handler requests its later item-drop fallback or the optional
	 * API could not be invoked.
	 */
	public static boolean tryNativeDeathPlacement(ServerPlayer player, ItemStack backpack) {
		if (API == null || backpack.isEmpty() || API.usesExternalEquipmentIntegration()) return false;
		try {
			Object needsItemFallback = API.onPlayerDrops.invoke(null, player.level(), player, backpack);
			boolean placed = Boolean.FALSE.equals(needsItemFallback);
			ControllableDeathPenalty.LOGGER.info(
				"Traveler's Backpack drop check succeeded for {}: native result={}",
				player.getScoreboardName(),
				placed ? "placed" : "deferred item fallback"
			);
			return placed;
		} catch (ReflectiveOperationException | LinkageError exception) {
			logFailure("invoke native death placement for", exception);
			return false;
		}
	}

	private static Api loadApi() {
		if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) return null;
		try {
			Class<?> attachmentUtils = Class.forName("com.tiviacz.travelersbackpack.attachment.AttachmentUtils");
			Class<?> attachment = Class.forName("com.tiviacz.travelersbackpack.attachment.BackpackAttachment");
			Class<?> travelersBackpack = Class.forName("com.tiviacz.travelersbackpack.TravelersBackpack");
			Class<?> backpackDeathHelper = Class.forName("com.tiviacz.travelersbackpack.util.BackpackDeathHelper");
			Api api = new Api(
				attachmentUtils.getMethod("getWearingBackpack", Player.class),
				attachmentUtils.getMethod("getAttachment", Player.class),
				attachmentUtils.getMethod("equipBackpack", Player.class, ItemStack.class),
				attachment.getMethod("remove", Player.class),
				travelersBackpack.getMethod("enableIntegration"),
				backpackDeathHelper.getMethod("onPlayerDrops", Level.class, Player.class, ItemStack.class)
			);
			ControllableDeathPenalty.LOGGER.info("Enabled Traveler's Backpack native-slot compatibility");
			return api;
		} catch (ReflectiveOperationException | LinkageError exception) {
			ControllableDeathPenalty.LOGGER.warn(
				"Traveler's Backpack is installed, but its equipped-slot API is incompatible; compatibility is disabled",
				exception
			);
			return null;
		}
	}

	private static void logFailure(String operation, Throwable exception) {
		ControllableDeathPenalty.LOGGER.warn(
			"Failed to {} the Traveler's Backpack equipped slot; compatibility for this death is incomplete",
			operation,
			exception
		);
	}

	private record Api(
		Method getWearingBackpack,
		Method getAttachment,
		Method equipBackpack,
		Method remove,
		Method enableIntegration,
		Method onPlayerDrops
	) {
		private boolean usesExternalEquipmentIntegration() {
			try {
				return Boolean.TRUE.equals(enableIntegration.invoke(null));
			} catch (ReflectiveOperationException | LinkageError exception) {
				logFailure("detect integration mode for", exception);
				return true;
			}
		}
	}
}
