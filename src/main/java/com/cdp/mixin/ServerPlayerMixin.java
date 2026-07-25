package com.cdp.mixin;

import com.cdp.DeathPenaltyHandler;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Transfers the inventory state retained by the custom death penalty to the newly respawned player. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Inject(method = "restoreFrom", at = @At("TAIL"))
	private void cdp$restoreRetainedDeathPenaltyState(ServerPlayer oldPlayer, boolean alive, CallbackInfo ci) {
		DeathPenaltyHandler.restoreRetainedState((ServerPlayer) (Object) this);
	}
}
