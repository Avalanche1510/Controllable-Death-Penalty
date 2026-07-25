<details>
<summary>中文</summary>

# 可控死亡惩罚

## 许可证

本项目采用 CC0-1.0 许可证。您可以自由学习、使用并将其纳入自己的项目。

## 简介

可控死亡惩罚为 Minecraft 26.1.1 提供一个可由服务器管理员在游戏内即时调整的全局死亡惩罚系统。
它不使用“全部掉落”或“完全保留”这两种固定规则，而是对每个物品格分别执行独立的耐久损失与掉落概率检查；可堆叠物品可以部分掉落，耐久物品则可能损伤、掉落、同时发生或均不发生。

规则按世界保存，重启服务器后仍然有效。白名单可以保护原版或其他模组的任意物品。经验会按当前总经验的随机比例损失，并以严格守恒的经验球总值掉出。

模组逻辑完全由服务端决定；客户端无需安装即可按原版机制看到并拾取掉出的物品和经验球。

## 资源指南

[模组文档](Docs/模组文档.md)<br>
[更新日志](ChangeLog/更新日志.md)

</details>

<details>
<summary>English</summary>

# Controllable Death Penalty

## License

This project is available under the CC0-1.0 license. Feel free to learn from it and incorporate it into your own projects.

## Introduction

Controllable Death Penalty adds a world-global death penalty system for Minecraft 26.1.1 that server administrators can adjust in game.
Instead of choosing between dropping everything and keeping everything, it performs independent durability-loss and drop checks for each occupied slot. Stackable items can drop partially, while durability items may be damaged, dropped, both, or neither.

Rules are saved with the world and persist across server restarts. The whitelist can protect any vanilla or modded item. Experience loss is selected from a random percentage of the player's current total experience and is emitted as exactly conserved experience-orb value.

All logic is server-authoritative. Clients do not need the mod and use normal vanilla item and experience-orb behaviour.

## Resource Guide

[Documentation](Docs/docs.md)<br>
[Change Log](ChangeLog/changelog.md)

</details>
