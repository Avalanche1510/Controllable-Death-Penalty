package com.cdp.command;

import com.cdp.config.DeathPenaltyConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Comparator;
import java.util.stream.Collectors;

public final class CdpCommands {
	private CdpCommands() { }

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		var root = Commands.<CommandSourceStack>literal("cdp");
		root.then(Commands.literal("get").executes(context -> show(context.getSource())));
		root.then(Commands.<CommandSourceStack>literal("reset").requires(source -> Commands.LEVEL_OWNERS.check(source.permissions())).executes(context -> reset(context.getSource())));
		root.then(Commands.<CommandSourceStack>literal("set").requires(source -> Commands.LEVEL_OWNERS.check(source.permissions()))
			.then(doubleSetting("dropChance", (c, v) -> c.dropChance = v))
			.then(doubleSetting("durabilityLossChance", (c, v) -> c.durabilityLossChance = v))
			.then(doubleSetting("minDropPerc", (c, v) -> c.minDropPerc = v))
			.then(doubleSetting("maxDropPerc", (c, v) -> c.maxDropPerc = v))
			.then(doubleSetting("minExperienceLossPerc", (c, v) -> c.minExperienceLossPerc = v))
			.then(doubleSetting("maxExperienceLossPerc", (c, v) -> c.maxExperienceLossPerc = v))
			.then(doubleSetting("minDurabilityLossPerc", (c, v) -> c.minDurabilityLossPerc = v))
			.then(doubleSetting("maxDurabilityLossPerc", (c, v) -> c.maxDurabilityLossPerc = v))
			.then(boolSetting("doArmorDrop", (c, v) -> c.doArmorDrop = v))
			.then(boolSetting("doMainhandDrop", (c, v) -> c.doMainhandDrop = v))
			.then(boolSetting("doOffhandDrop", (c, v) -> c.doOffhandDrop = v))
			.then(boolSetting("doToolbarDrop", (c, v) -> c.doToolbarDrop = v))
			.then(boolSetting("stackInsurance", (c, v) -> c.stackInsurance = v))
			.then(boolSetting("doDurabilityLoss", (c, v) -> c.doDurabilityLoss = v))
			.then(boolSetting("durabilityInsurance", (c, v) -> c.durabilityInsurance = v))
			.then(Commands.literal("maxExperienceOrbs").then(Commands.argument("value", IntegerArgumentType.integer(1))
				.executes(context -> update(context.getSource(), c -> c.maxExperienceOrbs = IntegerArgumentType.getInteger(context, "value"))))));
		root.then(Commands.<CommandSourceStack>literal("whitelist").requires(source -> Commands.LEVEL_OWNERS.check(source.permissions()))
			.then(Commands.literal("add").then(Commands.argument("item", IdentifierArgument.id()).executes(context -> whitelist(context.getSource(), IdentifierArgument.getId(context, "item"), true))))
			.then(Commands.literal("remove").then(Commands.argument("item", IdentifierArgument.id()).executes(context -> whitelist(context.getSource(), IdentifierArgument.getId(context, "item"), false))))
			.then(Commands.literal("list").executes(context -> listWhitelist(context.getSource()))));
		dispatcher.register(root);
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> doubleSetting(String name, DoubleSetter setter) {
		return Commands.literal(name).then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 1.0))
			.executes(context -> update(context.getSource(), c -> setter.set(c, DoubleArgumentType.getDouble(context, "value")))));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> boolSetting(String name, BoolSetter setter) {
		return Commands.literal(name).then(Commands.argument("value", BoolArgumentType.bool())
			.executes(context -> update(context.getSource(), c -> setter.set(c, BoolArgumentType.getBool(context, "value")))));
	}

	private static int update(CommandSourceStack source, ConfigUpdate update) {
		DeathPenaltyConfig config = DeathPenaltyConfig.get(source.getServer());
		DeathPenaltyConfig previous = new DeathPenaltyConfig();
		copy(config, previous);
		update.apply(config);
		if (!validRanges(config)) {
			copy(previous, config);
			source.sendFailure(Component.literal("Minimum percentages cannot exceed their matching maximum percentages."));
			return 0;
		}
		config.setDirty();
		source.sendSuccess(() -> Component.literal("CDP setting updated."), true);
		return 1;
	}

	private static boolean validRanges(DeathPenaltyConfig c) {
		return c.minDropPerc <= c.maxDropPerc && c.minExperienceLossPerc <= c.maxExperienceLossPerc
			&& c.minDurabilityLossPerc <= c.maxDurabilityLossPerc;
	}

	private static int show(CommandSourceStack source) {
		DeathPenaltyConfig c = DeathPenaltyConfig.get(source.getServer());
		source.sendSystemMessage(Component.literal("CDP: drop chance=" + c.dropChance + ", durability chance=" + c.durabilityLossChance
			+ ", stack=" + c.minDropPerc + "-" + c.maxDropPerc
			+ ", xp=" + c.minExperienceLossPerc + "-" + c.maxExperienceLossPerc + ", durability=" + c.minDurabilityLossPerc + "-" + c.maxDurabilityLossPerc));
		source.sendSystemMessage(Component.literal("Slots: armor=" + c.doArmorDrop + ", mainhand=" + c.doMainhandDrop + ", offhand=" + c.doOffhandDrop + ", toolbar=" + c.doToolbarDrop));
		source.sendSystemMessage(Component.literal("Insurance: stack=" + c.stackInsurance + ", durability=" + c.durabilityInsurance + ", durability loss=" + c.doDurabilityLoss + ", max XP orbs=" + c.maxExperienceOrbs));
		return 1;
	}

	private static int reset(CommandSourceStack source) {
		DeathPenaltyConfig config = DeathPenaltyConfig.get(source.getServer());
		DeathPenaltyConfig defaults = new DeathPenaltyConfig();
		copy(defaults, config);
		config.setDirty();
		source.sendSuccess(() -> Component.literal("CDP settings reset to defaults."), true);
		return 1;
	}

	private static void copy(DeathPenaltyConfig from, DeathPenaltyConfig to) {
		to.dropChance = from.dropChance; to.minDropPerc = from.minDropPerc; to.maxDropPerc = from.maxDropPerc;
		to.minExperienceLossPerc = from.minExperienceLossPerc; to.maxExperienceLossPerc = from.maxExperienceLossPerc;
		to.doArmorDrop = from.doArmorDrop; to.doMainhandDrop = from.doMainhandDrop; to.doOffhandDrop = from.doOffhandDrop; to.doToolbarDrop = from.doToolbarDrop;
		to.stackInsurance = from.stackInsurance; to.doDurabilityLoss = from.doDurabilityLoss;
		to.durabilityLossChance = from.durabilityLossChance;
		to.minDurabilityLossPerc = from.minDurabilityLossPerc; to.maxDurabilityLossPerc = from.maxDurabilityLossPerc;
		to.durabilityInsurance = from.durabilityInsurance; to.maxExperienceOrbs = from.maxExperienceOrbs;
		to.whiteList.clear(); to.whiteList.addAll(from.whiteList);
	}

	private static int whitelist(CommandSourceStack source, Identifier id, boolean add) {
		DeathPenaltyConfig config = DeathPenaltyConfig.get(source.getServer());
		boolean changed = add ? config.whiteList.add(id) : config.whiteList.remove(id);
		if (!changed) {
			source.sendFailure(Component.literal(add ? "Item is already whitelisted." : "Item is not whitelisted."));
			return 0;
		}
		config.setDirty();
		source.sendSuccess(() -> Component.literal((add ? "Whitelisted " : "Removed ") + id), true);
		return 1;
	}

	private static int listWhitelist(CommandSourceStack source) {
		DeathPenaltyConfig config = DeathPenaltyConfig.get(source.getServer());
		String items = config.whiteList.stream().map(Identifier::toString).sorted(Comparator.naturalOrder()).collect(Collectors.joining(", "));
		source.sendSystemMessage(Component.literal("CDP whitelist: " + (items.isEmpty() ? "(empty)" : items)));
		return 1;
	}

	@FunctionalInterface private interface ConfigUpdate { void apply(DeathPenaltyConfig config); }
	@FunctionalInterface private interface DoubleSetter { void set(DeathPenaltyConfig config, double value); }
	@FunctionalInterface private interface BoolSetter { void set(DeathPenaltyConfig config, boolean value); }
}
