# Controllable Death Penalty Change Log

## Introduction

This file records Git-level changes for Controllable Death Penalty. It is intended to provide a detailed, release-ready history of behaviour and documentation changes.

# Format

year.month.day-sequence

## Records

### 26.9.22-1

Migrated the Minecraft 26.2 branch to Minecraft 26.2, Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, Fabric Loom 1.18, and Gradle 9.7.1 while keeping the mod version at 1.2.0-alpha.

Updated the GameTest source for the Minecraft 26.2 `EntityTypes.ITEM` API and changed the GameTest mod metadata to require Minecraft 26.2. The complete Gradle build and local client startup now succeed on the new version.

Verified the existing death-penalty behaviour in the Minecraft 26.2 client without identifying a blocking gameplay regression. Updated Traveler's Backpack compatibility documentation for the tested 11.3.2 release.

Updated the bilingual README version references and changed its documentation and change-log links to explicit files on the `26.2` branch, so release pages do not resolve them through another default branch.

### 26.8.9-1

- New version 1.2.0-alpha is about to be released

Changed `/cdp whitelist add <item>` from a permissive namespaced-identifier argument to a server item-registry argument. It now provides Tab completion for loaded vanilla and modded items like `/give`, and rejects misspelled, nonexistent, or currently unloaded item IDs during parsing instead of silently saving invalid whitelist entries.

`/cdp whitelist remove <item>` now provides Tab completion from the current whitelist, allowing both loaded items and stale entries from uninstalled mods to be selected directly, while continuing to accept any syntactically valid full identifier. Added GameTest coverage for accepting registered items, rejecting unregistered items, and suggesting stale removal entries from unloaded mods.

Replaced the hard-coded `/cdp` runtime feedback with localizable translation keys and added Chinese and English language resources. Clients with CDP installed see messages in their own language, while clients without CDP receive the server's Chinese fallback text so the mod remains readable when installed on the server only. This covers setting and reset results, percentage-range validation, the current-rule overview, and whitelist add, remove, and list messages; boolean states in the Chinese overview display as enabled or disabled.

Added branched help commands available to all users: `/cdp help command` lists all available commands, while `/cdp help parameter <parameter>` shows the range, available values, and purpose of one parameter. Concise bilingual help text is maintained separately as language resources, avoiding both a complete-document chat dump and help content hard-coded in Java.

Added `NonStackableInsurance`, enabled by default. While enabled, a non-stackable item remains in its original slot even after succeeding on `DropChance` in a drop-enabled slot. This removes the inconsistent outcome where a stack retains its final item while a single non-stackable item is removed completely. Disabling the setting restores the previous whole-item drop behaviour.

This insurance controls dropping only and does not skip the independent durability-loss check. If `DurabilityInsurance` is also disabled, a non-stackable item can still be destroyed by exhausting its durability. Traveler's Backpack's native equipped-backpack slot is protected by this insurance as well; `NonStackableInsurance` must be disabled when native backpack placement on death is desired.

The new field uses backward-compatible saved-data decoding. Existing worlds enable the insurance when first loaded by the new version while retaining all existing settings. Added `/cdp set nonStackableInsurance <true|false>`, `/cdp get` output, and GameTest coverage.

### 26.7.26-2

- New version 1.1.0-alpha is about to be released

Added optional compatibility with the native equipped-backpack slot in Traveler's Backpack 11.2.7. When that mod is installed without its external Trinkets equipment integration enabled, the worn backpack item is treated as one independent, drop-enabled slot governed by `DropChance`, the whitelist, and all other applicable CDP rules.

Only the backpack item itself is processed. Items stored inside it are never enumerated or checked separately; the internal inventory remains intact as component data on the retained or dropped backpack `ItemStack`.

Only when CDP retains the backpack does it temporarily remove the stack from the dead player's Traveler's Backpack attachment, suppressing the native death action. The retained backpack is restored to its dedicated slot when the player respawns.

When CDP's drop check succeeds, it no longer uses the generic item-entity spawning path. It immediately calls Traveler's Backpack's native `BackpackDeathHelper.onPlayerDrops(...)` entry point. A successful placement is completed by that mod on the spot; when native item fallback is required, the attachment remains available for its `AFTER_DEATH` listener to finish the fallback. This preserves native death-site placement, forced placement, void protection, grave-mod detection, and item fallback when placement is unavailable.

Fixed retained backpacks disappearing after respawn in the initial compatibility implementation. Fabric Data Attachment API performs its death-attachment transfer through `AFTER_RESPAWN`, after `restoreFrom` has returned, so the previous early restoration was overwritten by the old player's intentionally empty attachment. Backpack restoration now runs after that attachment transfer is complete.

Eliminated the remaining respawn race caused by mod initialization order. Fabric attachment transfer, CDP, and Traveler's Backpack can register `AFTER_RESPAWN` callbacks in different orders. Retained backpacks are now restored at the end of the server tick, after all attachment transfers and other mods' respawn callbacks have completed.

Fixed source items being removed when the world rejected creation of their dropped item entity. A source slot is now reduced or cleared only after the world successfully accepts the corresponding entity, preserving the item when spawning fails.

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
