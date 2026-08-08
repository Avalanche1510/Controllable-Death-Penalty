package com.cdp.command;

import com.cdp.config.DeathPenaltyConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class CdpCommands {
	private static final String CHINESE_LANGUAGE_RESOURCE = "/assets/controllable-death-penalty/lang/zh_cn.json";
	private static final Map<String, String> HELP_FALLBACKS = loadHelpFallbacks();
	private static final HelpParameter[] HELP_PARAMETERS = {
		new HelpParameter("dropChance"), new HelpParameter("minDropPerc"), new HelpParameter("maxDropPerc"),
		new HelpParameter("minExperienceLossPerc"), new HelpParameter("maxExperienceLossPerc"),
		new HelpParameter("doArmorDrop"), new HelpParameter("doMainhandDrop"), new HelpParameter("doOffhandDrop"),
		new HelpParameter("doToolbarDrop"), new HelpParameter("stackInsurance"),
		new HelpParameter("nonStackableInsurance"), new HelpParameter("doDurabilityLoss"),
		new HelpParameter("durabilityLossChance"), new HelpParameter("minDurabilityLossPerc"),
		new HelpParameter("maxDurabilityLossPerc"), new HelpParameter("durabilityInsurance"),
		new HelpParameter("maxExperienceOrbs"), new HelpParameter("whiteList")
	};

	private CdpCommands() { }

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
		var root = Commands.<CommandSourceStack>literal("cdp");
		root.then(Commands.literal("get").executes(context -> show(context.getSource())));
		root.then(helpCommand());
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
			.then(boolSetting("nonStackableInsurance", (c, v) -> c.nonStackableInsurance = v))
			.then(boolSetting("doDurabilityLoss", (c, v) -> c.doDurabilityLoss = v))
			.then(boolSetting("durabilityInsurance", (c, v) -> c.durabilityInsurance = v))
			.then(Commands.literal("maxExperienceOrbs").then(Commands.argument("value", IntegerArgumentType.integer(1))
				.executes(context -> update(context.getSource(), c -> c.maxExperienceOrbs = IntegerArgumentType.getInteger(context, "value"))))));
		root.then(Commands.<CommandSourceStack>literal("whitelist").requires(source -> Commands.LEVEL_OWNERS.check(source.permissions()))
			.then(Commands.literal("add").then(Commands.argument("item", ResourceArgument.resource(registryAccess, Registries.ITEM))
				.executes(context -> whitelist(context.getSource(), ResourceArgument.getResource(context, "item", Registries.ITEM).key().identifier(), true))))
			.then(Commands.literal("remove").then(Commands.argument("item", IdentifierArgument.id())
				.suggests((context, builder) -> {
					String remaining = builder.getRemainingLowerCase();
					DeathPenaltyConfig.get(context.getSource().getServer()).whiteList.stream()
						.map(Identifier::toString)
						.filter(id -> id.toLowerCase(java.util.Locale.ROOT).startsWith(remaining))
						.sorted()
						.forEach(builder::suggest);
					return builder.buildFuture();
				})
				.executes(context -> whitelist(context.getSource(), IdentifierArgument.getId(context, "item"), false))))
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
			source.sendFailure(message("command.cdp.minimum_exceeds_maximum", "最小百分比不能大于对应的最大百分比。"));
			return 0;
		}
		config.setDirty();
		source.sendSuccess(() -> message("command.cdp.setting_updated", "CDP 设置已更新。"), true);
		return 1;
	}

	private static boolean validRanges(DeathPenaltyConfig c) {
		return c.minDropPerc <= c.maxDropPerc && c.minExperienceLossPerc <= c.maxExperienceLossPerc
			&& c.minDurabilityLossPerc <= c.maxDurabilityLossPerc;
	}

	private static int show(CommandSourceStack source) {
		DeathPenaltyConfig c = DeathPenaltyConfig.get(source.getServer());
		source.sendSystemMessage(message("command.cdp.rules.summary",
			"CDP 当前规则：掉落概率=%s，耐久损失概率=%s，堆叠掉落比例=%s-%s，经验损失比例=%s-%s，耐久损失比例=%s-%s",
			c.dropChance, c.durabilityLossChance, c.minDropPerc, c.maxDropPerc, c.minExperienceLossPerc,
			c.maxExperienceLossPerc, c.minDurabilityLossPerc, c.maxDurabilityLossPerc));
		source.sendSystemMessage(message("command.cdp.rules.slots",
			"槽位掉落：护甲=%s，主手=%s，副手=%s，其余快捷栏=%s", settingState(c.doArmorDrop),
			settingState(c.doMainhandDrop), settingState(c.doOffhandDrop), settingState(c.doToolbarDrop)));
		source.sendSystemMessage(message("command.cdp.rules.insurance",
			"保险与限制：堆叠保险=%s，不可堆叠物保险=%s，耐久保险=%s，耐久损失=%s，最大经验球数=%s",
			settingState(c.stackInsurance), settingState(c.nonStackableInsurance), settingState(c.durabilityInsurance),
			settingState(c.doDurabilityLoss), c.maxExperienceOrbs));
		return 1;
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> helpCommand() {
		var parameter = Commands.<CommandSourceStack>literal("parameter");
		for (HelpParameter helpParameter : HELP_PARAMETERS) {
			parameter.then(Commands.literal(helpParameter.name)
				.executes(context -> showParameterHelp(context.getSource(), helpParameter)));
		}
		return Commands.<CommandSourceStack>literal("help")
			.executes(context -> sendHelp(context.getSource(), "command.cdp.help.overview"))
			.then(Commands.literal("command").executes(context -> showCommandHelp(context.getSource())))
			.then(parameter);
	}

	private static int showCommandHelp(CommandSourceStack source) {
		return sendHelp(source, "command.cdp.help.command.header", "command.cdp.help.command.get",
			"command.cdp.help.command.help", "command.cdp.help.command.reset", "command.cdp.help.command.set",
			"command.cdp.help.command.whitelist");
	}

	private static int showParameterHelp(CommandSourceStack source, HelpParameter parameter) {
		source.sendSystemMessage(helpMessage("command.cdp.help.parameter.header", parameter.name));
		return sendHelp(source, "command.cdp.help.parameter." + parameter.name);
	}

	private static int sendHelp(CommandSourceStack source, String... keys) {
		Arrays.stream(keys).map(CdpCommands::helpMessage).forEach(source::sendSystemMessage);
		return 1;
	}

	private static int reset(CommandSourceStack source) {
		DeathPenaltyConfig config = DeathPenaltyConfig.get(source.getServer());
		DeathPenaltyConfig defaults = new DeathPenaltyConfig();
		copy(defaults, config);
		config.setDirty();
		source.sendSuccess(() -> message("command.cdp.settings_reset", "CDP 设置已重置为默认值。"), true);
		return 1;
	}

	private static void copy(DeathPenaltyConfig from, DeathPenaltyConfig to) {
		to.dropChance = from.dropChance; to.minDropPerc = from.minDropPerc; to.maxDropPerc = from.maxDropPerc;
		to.minExperienceLossPerc = from.minExperienceLossPerc; to.maxExperienceLossPerc = from.maxExperienceLossPerc;
		to.doArmorDrop = from.doArmorDrop; to.doMainhandDrop = from.doMainhandDrop; to.doOffhandDrop = from.doOffhandDrop; to.doToolbarDrop = from.doToolbarDrop;
		to.stackInsurance = from.stackInsurance; to.nonStackableInsurance = from.nonStackableInsurance;
		to.doDurabilityLoss = from.doDurabilityLoss;
		to.durabilityLossChance = from.durabilityLossChance;
		to.minDurabilityLossPerc = from.minDurabilityLossPerc; to.maxDurabilityLossPerc = from.maxDurabilityLossPerc;
		to.durabilityInsurance = from.durabilityInsurance; to.maxExperienceOrbs = from.maxExperienceOrbs;
		to.whiteList.clear(); to.whiteList.addAll(from.whiteList);
	}

	private static int whitelist(CommandSourceStack source, Identifier id, boolean add) {
		DeathPenaltyConfig config = DeathPenaltyConfig.get(source.getServer());
		boolean changed = add ? config.whiteList.add(id) : config.whiteList.remove(id);
		if (!changed) {
			source.sendFailure(message(add ? "command.cdp.whitelist.already_added" : "command.cdp.whitelist.not_found",
				add ? "该物品已在白名单中。" : "该物品不在白名单中。"));
			return 0;
		}
		config.setDirty();
		source.sendSuccess(() -> message(add ? "command.cdp.whitelist.added" : "command.cdp.whitelist.removed",
			add ? "已将 %s 加入白名单。" : "已将 %s 从白名单移除。", id), true);
		return 1;
	}

	private static int listWhitelist(CommandSourceStack source) {
		DeathPenaltyConfig config = DeathPenaltyConfig.get(source.getServer());
		String items = config.whiteList.stream().map(Identifier::toString).sorted(Comparator.naturalOrder()).collect(Collectors.joining(", "));
		source.sendSystemMessage(message("command.cdp.whitelist.list", "CDP 白名单：%s",
			items.isEmpty() ? message("command.cdp.empty", "（空）") : Component.literal(items)));
		return 1;
	}

	private static Component settingState(boolean value) {
		return value ? message("command.cdp.enabled", "开启") : message("command.cdp.disabled", "关闭");
	}

	private static Component message(String key, String fallback, Object... arguments) {
		return Component.translatableWithFallback(key, fallback, arguments);
	}

	private static Component helpMessage(String key, Object... arguments) {
		return Component.translatableWithFallback(key, HELP_FALLBACKS.getOrDefault(key, key), arguments);
	}

	private static Map<String, String> loadHelpFallbacks() {
		Map<String, String> fallbacks = new HashMap<>();
		try (var stream = CdpCommands.class.getResourceAsStream(CHINESE_LANGUAGE_RESOURCE)) {
			if (stream == null) return fallbacks;
			JsonObject language = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : language.entrySet()) {
				if (entry.getKey().startsWith("command.cdp.help.")) fallbacks.put(entry.getKey(), entry.getValue().getAsString());
			}
		} catch (Exception ignored) {
			// A missing fallback must not prevent command registration; translated clients still resolve their own keys.
		}
		return fallbacks;
	}

	private record HelpParameter(String name) { }

	@FunctionalInterface private interface ConfigUpdate { void apply(DeathPenaltyConfig config); }
	@FunctionalInterface private interface DoubleSetter { void set(DeathPenaltyConfig config, double value); }
	@FunctionalInterface private interface BoolSetter { void set(DeathPenaltyConfig config, boolean value); }
}
