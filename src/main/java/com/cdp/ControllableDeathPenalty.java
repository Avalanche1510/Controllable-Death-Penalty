package com.cdp;

import com.cdp.command.CdpCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ControllableDeathPenalty implements ModInitializer {
	public static final String MOD_ID = "controllable-death-penalty";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> CdpCommands.register(dispatcher, registryAccess));
		ServerTickEvents.END_SERVER_TICK.register(DeathPenaltyHandler::restoreExternalSlotsAfterAttachmentTransfers);
		LOGGER.info("Controllable Death Penalty initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
