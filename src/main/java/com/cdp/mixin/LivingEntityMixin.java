package com.cdp.mixin;

import com.cdp.DeathPenaltyHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
	private void cdp$handlePlayerDeathLoot(ServerLevel level, DamageSource source, CallbackInfo ci) {
		if (!((Object) this instanceof ServerPlayer player) || level.getGameRules().get(GameRules.KEEP_INVENTORY)) return;
		DeathPenaltyHandler.apply(player, level);
		ci.cancel();
	}
}
