# Controllable Death Penalty Change Log

## Introduction

This file records Git-level changes for Controllable Death Penalty. It is intended to provide a detailed, release-ready history of behaviour and documentation changes.

# Format

year.month.day-sequence

## Records

### 26.7.26-1

Changed the default maximum experience-loss percentage from 25% to 50%. Existing worlds retain their saved value; use `/cdp set maxExperienceLossPerc 0.5` or `/cdp reset` to apply the new default.

Fixed `/cdp whitelist add` and `/cdp whitelist remove` rejecting complete namespaced item identifiers containing a colon. Both commands now use Minecraft's identifier argument type and accept values such as `minecraft:diamond` and `othermod:test_item`.

Added GameTest assertions for the 50% default and namespaced whitelist command parsing.

### 26.7.25-4

Added `DurabilityLossChance`, defaulting to 50%. Durability loss is no longer derived from the success or failure of `DropChance`.

Each eligible non-stackable durability item now performs its independent durability-loss check before its independent drop check. A drop-enabled item can therefore be damaged and dropped, unchanged and retained, unchanged and dropped, or damaged and retained. Drop-disabled slots still block the drop result without blocking the independent durability check.

Added backward-compatible saved-data decoding: worlds created before this setting existed load `DurabilityLossChance` as 0.5 without resetting their other saved rules.

Added `/cdp set durabilityLossChance <0.0-1.0>` and included the new value in `/cdp get` and `/cdp reset`.

Expanded the server GameTest to deterministically verify all four durability/drop combinations and their retained or dropped item durability.

### 26.7.25-3

Fixed armor and off-hand drop protection on Minecraft 26.1.1. In this version, `Inventory.getContainerSize()` includes 36 ordinary inventory slots plus seven mapped equipment slots. The previous ordinary-inventory loop therefore processed armor and off-hand equipment a second time as drop-enabled inventory slots, bypassing `DoArmorDrop` and `DoOffhandDrop`.

Ordinary inventory processing, snapshotting, and restoration now use only `Inventory.getNonEquipmentItems()`. Armor and off-hand equipment are handled exactly once through their dedicated rules.

Added a deterministic server GameTest covering the complete death and respawn path. With a 100% drop chance, armor and off-hand dropping disabled, and durability loss disabled, the test verifies exact item and durability preservation both immediately after death processing and on the newly respawned player. A drop-enabled main-hand item is used as a control.

### 26.7.25-2

Fixed retained items disappearing after respawn. Items and experience that remain after the custom death checks are now copied from the dead player to the newly respawned player.

Changed the default stack-drop range from a fixed 50% to a uniform random 10%–50% (`MinDropPerc = 0.1`, `MaxDropPerc = 0.5`). Existing worlds retain their saved values; use `/cdp set minDropPerc 0.1` and `/cdp set maxDropPerc 0.5`, or `/cdp reset`, to apply the new default.

Corrected experience-loss calculation to derive current held experience from the vanilla level curve and current experience-bar progress, then synchronize the exact remaining amount after respawn.

Changed experience-bar point reconstruction from truncation to bounded rounding so repeated deaths do not lose one extra point because of floating-point representation.

Added a death-time snapshot for armor and off-hand equipment before the old player entity is removed, so protected equipment can be restored even after vanilla clears entity equipment. Durability compensation now also applies to a non-dropping tool in a drop-enabled slot after its chance check fails; items that actually drop are never damaged.

### 26.7.25-1

Initial release for Minecraft 26.1.1 on Fabric Loader 0.19.3 and Fabric API 0.145.4.

Added a server-authoritative, world-global death penalty configuration stored in world saved data. Rules persist through server restarts and apply immediately after command updates.

Added independent death checks for every occupied player slot. Armor, selected main hand, off hand, and the other eight hotbar slots have separate drop switches; the ordinary 27-slot inventory always uses the normal drop rule.

Added `DropChance` and the `MinDropPerc` / `MaxDropPerc` range. Stackable items select a uniform random drop proportion and round the resulting count up. Non-stackable items drop as complete items when their slot check succeeds.

Added `StackInsurance`, which prevents any stack from losing its final item.

Added compensation durability loss for slots whose drop switch is disabled. A successful `DropChance` check can damage a non-stackable durability item by a uniformly random percentage of its full maximum durability. Added `DoDurabilityLoss`, `MinDurabilityLossPerc`, `MaxDurabilityLossPerc`, and `DurabilityInsurance`; insured items retain at least one durability point.

Added an item identifier whitelist. White-listed vanilla and modded items skip all death-loss and durability checks.

Added random experience loss based on current total experience through `MinExperienceLossPerc` and `MaxExperienceLossPerc`. The deducted value is emitted at the death position with exact total-value conservation.

Added `MaxExperienceOrbs`, defaulting to 10. It limits spawned experience-orb entities by distributing the exact lost experience as evenly as possible across at most that many orbs.

Added `/cdp get` for public rule inspection. Added permission-level-4 commands for setting each rule, resetting defaults, and adding, removing, or listing white-list entries.

The vanilla `keepInventory` game rule takes priority: while enabled, the mod does not drop items, deduct experience, or damage equipment.

Added complete English and Chinese README, documentation, and change-log resources.
