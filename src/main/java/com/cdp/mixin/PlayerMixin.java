package com.cdp.mixin;

import com.cdp.access.CdpPlayerAccess;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Player.class)
public abstract class PlayerMixin implements CdpPlayerAccess {
	@Shadow protected abstract void destroyVanishingCursedItems();

	@Override
	public void cdp$destroyVanishingCursedItems() {
		destroyVanishingCursedItems();
	}
}
