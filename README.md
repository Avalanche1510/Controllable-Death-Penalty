<details>
<summary>中文</summary>

# 可控死亡惩罚

## 许可证

本项目采用 CC0-1.0 许可证。您可以自由学习、使用并将其纳入自己的项目。

## 简介

可控死亡惩罚为 Minecraft 26.2 提供一个可由服务器管理员在游戏内即时调整的全局死亡惩罚系统。
它不使用“全部掉落”或“完全保留”这两种固定规则，而是对每个物品格分别执行独立的耐久损失与掉落概率检查；可堆叠物品可以部分掉落，耐久物品则可能损伤、掉落、同时发生或均不发生。

规则按世界保存，重启服务器后仍然有效。默认的堆叠保险会保留每格可堆叠物品的最后一个，新的不可堆叠物保险则阻止工具、武器等单件物品因掉落检定而被整件移出。白名单可以保护原版或其他模组的任意物品。经验会按当前总经验的随机比例损失，并以严格守恒的经验球总值掉出。

模组逻辑完全由服务端决定；客户端无需安装即可按原版机制看到并拾取掉出的物品和经验球。

已兼容 Traveler's Backpack 11.3.2 的原生已装备背包槽。掉落检查成功后由 Traveler's Backpack 保留其死亡地点自动放置等原生行为；CDP 只处理背包物品本身，不会分别检查背包内部库存。启用 Trinkets 外部装备集成时不适用此兼容。

## 兼容的模组：
- Traveler's Backpack (旅行者背包)

## 资源指南

[模组文档（26.2）](https://github.com/Avalanche1510/Controllable-Death-Penalty/blob/26.2/Docs/%E6%A8%A1%E7%BB%84%E6%96%87%E6%A1%A3.md)<br>
[更新日志（26.2）](https://github.com/Avalanche1510/Controllable-Death-Penalty/blob/26.2/ChangeLog/%E6%9B%B4%E6%96%B0%E6%97%A5%E5%BF%97.md)

</details>

<details>
<summary>English</summary>

# Controllable Death Penalty

## License

This project is available under the CC0-1.0 license. Feel free to learn from it and incorporate it into your own projects.

## Introduction

Controllable Death Penalty adds a world-global death penalty system for Minecraft 26.2 that server administrators can adjust in game.
Instead of choosing between dropping everything and keeping everything, it performs independent durability-loss and drop checks for each occupied slot. Stackable items can drop partially, while durability items may be damaged, dropped, both, or neither.

Rules are saved with the world and persist across server restarts. Stack insurance retains the final item in each stack by default, while the new non-stackable insurance prevents tools, weapons, and other single items from being removed by a successful drop check. The whitelist can protect any vanilla or modded item. Experience loss is selected from a random percentage of the player's current total experience and is emitted as exactly conserved experience-orb value.

All logic is server-authoritative. Clients do not need the mod and use normal vanilla item and experience-orb behaviour.

The native equipped-backpack slot from Traveler's Backpack 11.3.2 is supported. A successful drop check preserves Traveler's Backpack's native death-site placement behaviour. CDP processes only the backpack item, never its internal inventory; this compatibility does not apply while the external Trinkets equipment integration is enabled.

## Compatible Mods
- Traveler's Backpack

## Resource Guide

[Documentation (26.2)](https://github.com/Avalanche1510/Controllable-Death-Penalty/blob/26.2/Docs/docs.md)<br>
[Change Log (26.2)](https://github.com/Avalanche1510/Controllable-Death-Penalty/blob/26.2/ChangeLog/changelog.md)

</details>
