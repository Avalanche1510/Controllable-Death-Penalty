package com.cdp.config;

import com.cdp.ControllableDeathPenalty;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.util.datafix.DataFixTypes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** World-global death penalty settings. Values are kept mutable so commands can update one field at a time. */
public final class DeathPenaltyConfig extends SavedData {
	public static final SavedDataType<DeathPenaltyConfig> TYPE = new SavedDataType<>(
		ControllableDeathPenalty.id("death_penalty"), DeathPenaltyConfig::new, createCodec(), DataFixTypes.LEVEL
	);

	public double dropChance = 0.50;
	public double minDropPerc = 0.10;
	public double maxDropPerc = 0.50;
	public double minExperienceLossPerc = 0.10;
	public double maxExperienceLossPerc = 0.50;
	public boolean doArmorDrop = false;
	public boolean doMainhandDrop = false;
	public boolean doOffhandDrop = false;
	public boolean doToolbarDrop = true;
	public boolean stackInsurance = true;
	public boolean nonStackableInsurance = true;
	public boolean doDurabilityLoss = true;
	public double durabilityLossChance = 0.50;
	public double minDurabilityLossPerc = 0.05;
	public double maxDurabilityLossPerc = 0.20;
	public boolean durabilityInsurance = true;
	public int maxExperienceOrbs = 10;
	public final Set<Identifier> whiteList;

	public DeathPenaltyConfig() {
		this(new HashSet<>());
	}

	private DeathPenaltyConfig(double dropChance, double minDropPerc, double maxDropPerc,
				double minExperienceLossPerc, double maxExperienceLossPerc, boolean doArmorDrop,
				boolean doMainhandDrop, boolean doOffhandDrop, boolean doToolbarDrop, boolean stackInsurance,
				boolean nonStackableInsurance, DurabilitySettings durability, int maxExperienceOrbs,
				List<Identifier> whiteList) {
		this.dropChance = dropChance;
		this.minDropPerc = minDropPerc;
		this.maxDropPerc = maxDropPerc;
		this.minExperienceLossPerc = minExperienceLossPerc;
		this.maxExperienceLossPerc = maxExperienceLossPerc;
		this.doArmorDrop = doArmorDrop;
		this.doMainhandDrop = doMainhandDrop;
		this.doOffhandDrop = doOffhandDrop;
		this.doToolbarDrop = doToolbarDrop;
		this.stackInsurance = stackInsurance;
		this.nonStackableInsurance = nonStackableInsurance;
		this.doDurabilityLoss = durability.enabled;
		this.durabilityLossChance = durability.chance;
		this.minDurabilityLossPerc = durability.minLossPercentage;
		this.maxDurabilityLossPerc = durability.maxLossPercentage;
		this.durabilityInsurance = durability.insurance;
		this.maxExperienceOrbs = maxExperienceOrbs;
		this.whiteList = new HashSet<>(whiteList);
	}

	private DeathPenaltyConfig(Set<Identifier> whiteList) {
		this.whiteList = whiteList;
	}

	private static Codec<DeathPenaltyConfig> createCodec() {
		MapCodec<DurabilitySettings> durabilityCodec = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.BOOL.fieldOf("doDurabilityLoss").forGetter(DurabilitySettings::enabled),
			Codec.DOUBLE.optionalFieldOf("durabilityLossChance", 0.50).forGetter(DurabilitySettings::chance),
			Codec.DOUBLE.fieldOf("minDurabilityLossPerc").forGetter(DurabilitySettings::minLossPercentage),
			Codec.DOUBLE.fieldOf("maxDurabilityLossPerc").forGetter(DurabilitySettings::maxLossPercentage),
			Codec.BOOL.fieldOf("durabilityInsurance").forGetter(DurabilitySettings::insurance)
		).apply(instance, DurabilitySettings::new));

		return RecordCodecBuilder.create(instance -> instance.group(
			Codec.DOUBLE.fieldOf("dropChance").forGetter(c -> c.dropChance),
			Codec.DOUBLE.fieldOf("minDropPerc").forGetter(c -> c.minDropPerc),
			Codec.DOUBLE.fieldOf("maxDropPerc").forGetter(c -> c.maxDropPerc),
			Codec.DOUBLE.fieldOf("minExperienceLossPerc").forGetter(c -> c.minExperienceLossPerc),
			Codec.DOUBLE.fieldOf("maxExperienceLossPerc").forGetter(c -> c.maxExperienceLossPerc),
			Codec.BOOL.fieldOf("doArmorDrop").forGetter(c -> c.doArmorDrop),
			Codec.BOOL.fieldOf("doMainhandDrop").forGetter(c -> c.doMainhandDrop),
			Codec.BOOL.fieldOf("doOffhandDrop").forGetter(c -> c.doOffhandDrop),
			Codec.BOOL.fieldOf("doToolbarDrop").forGetter(c -> c.doToolbarDrop),
			Codec.BOOL.fieldOf("stackInsurance").forGetter(c -> c.stackInsurance),
			Codec.BOOL.optionalFieldOf("nonStackableInsurance", true).forGetter(c -> c.nonStackableInsurance),
			durabilityCodec.forGetter(c -> new DurabilitySettings(
				c.doDurabilityLoss,
				c.durabilityLossChance,
				c.minDurabilityLossPerc,
				c.maxDurabilityLossPerc,
				c.durabilityInsurance
			)),
			Codec.INT.fieldOf("maxExperienceOrbs").forGetter(c -> c.maxExperienceOrbs),
			Identifier.CODEC.listOf().fieldOf("whiteList").forGetter(c -> List.copyOf(c.whiteList))
		).apply(instance, DeathPenaltyConfig::new));
	}

	public static DeathPenaltyConfig get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean isWhitelisted(Identifier itemId) {
		return whiteList.contains(itemId);
	}

	private record DurabilitySettings(
		boolean enabled,
		double chance,
		double minLossPercentage,
		double maxLossPercentage,
		boolean insurance
	) { }
}
