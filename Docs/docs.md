# Controllable Death Penalty Docs

## Introduction

Controllable Death Penalty replaces the all-or-nothing player-death inventory rule with independent checks for every occupied slot. It is server-authoritative and stores one global rule set in the world's saved data. Changes made with commands take effect immediately and survive server restarts.

The vanilla `keepInventory` game rule always takes priority. When it is enabled, this mod does not drop items, remove experience, or damage equipment.

## Compatible Mods
- Traveler's Backpack

## Complete rule structure

```text
{
    DropChance: <double>,
    MinDropPerc: <double>,
    MaxDropPerc: <double>,
    MinExperienceLossPerc: <double>,
    MaxExperienceLossPerc: <double>,
    DoArmorDrop: <bool>,
    DoMainhandDrop: <bool>,
    DoOffhandDrop: <bool>,
    DoToolbarDrop: <bool>,
    StackInsurance: <bool>,
    DoDurabilityLoss: <bool>,
    DurabilityLossChance: <double>,
    MinDurabilityLossPerc: <double>,
    MaxDurabilityLossPerc: <double>,
    DurabilityInsurance: <bool>,
    MaxExperienceOrbs: <int>,
    WhiteList: [<item identifier>, ...]
}
```

All percentage values accept values from `0.0` to `1.0`. Each minimum value must not be greater than its matching maximum value.

## Death processing

Every non-empty slot is handled independently. White-listed items are skipped before any random check: they never drop and never receive durability damage.

The selected hotbar slot is the main-hand slot. The other eight hotbar slots are toolbar slots. The 27 ordinary inventory slots always use the normal drop rule; armor, selected main hand, off hand, and the remaining toolbar slots use their corresponding boolean setting.

When Traveler's Backpack 11.2.7 is installed without its external Trinkets equipment integration enabled, the mod also intercepts Traveler's Backpack's native equipped-backpack slot. The equipped backpack is one independent, drop-enabled slot using the ordinary rules: `DropChance` determines whether its native death action runs, while adding its item ID to `WhiteList` keeps it unconditionally. A successful check is delegated to Traveler's Backpack itself, preserving that mod's death-site placement, void protection, and fallback item drop settings. Only the backpack item itself is processed. Its internal inventory is never enumerated or checked independently and remains intact as component data on the retained, placed, or dropped backpack. Trinkets, accessory slots, and external slots supplied by other mods are outside this compatibility path.

For a non-stackable item with durability, the mod performs the durability check first:

1. `DoDurabilityLoss` must be enabled.
2. The independent `DurabilityLossChance` check must succeed.
3. A uniform random percentage between `MinDurabilityLossPerc` and `MaxDurabilityLossPerc` is multiplied by the item's full maximum durability, then rounded up.
4. `DurabilityInsurance` keeps at least one durability point. Without it, durability loss can destroy the item before its drop check, leaving nothing to drop.

If the item still exists, the mod then performs the independent `DropChance` check:

1. A non-stackable item in a drop-enabled slot drops as one whole item when the check succeeds.
2. A stackable item in a drop-enabled slot selects a uniform random percentage between `MinDropPerc` and `MaxDropPerc`. The resulting amount is rounded up and removed from the stack.
3. With `StackInsurance` enabled, a stack can never lose its final item. A one-item stack therefore stays in place even when its drop check succeeds.
4. A drop-disabled slot never drops its item, regardless of the `DropChance` result. Its independent durability check still applies.

Because the two checks are independent, a drop-enabled durability item can be damaged and dropped, unchanged and retained, unchanged and dropped, or damaged and retained. Every slot receives its own random checks.

## Experience processing

The mod reads the player's current total experience, selects a uniform random percentage between `MinExperienceLossPerc` and `MaxExperienceLossPerc`, and rounds the loss up. It removes exactly that amount from the player.

The same amount is emitted at the death position. `MaxExperienceOrbs` limits the number of entities by dividing the total as evenly as possible among at most that many experience orbs. This changes the number and value of entities, not the total experience: every removed point is represented by an experience orb.

## Parameters

```text
DropChance
Meaning: Chance for an individual slot check to succeed
Type: double
Range: [0.0, 1.0]
Default: 0.5

MinDropPerc / MaxDropPerc
Meaning: Inclusive uniform-random range for the proportion of a stack that drops
Type: double
Range: [0.0, 1.0], MinDropPerc <= MaxDropPerc
Default: 0.1 / 0.5
Note: The calculated item count is rounded up

MinExperienceLossPerc / MaxExperienceLossPerc
Meaning: Inclusive uniform-random range for the proportion of current total experience lost on death
Type: double
Range: [0.0, 1.0], MinExperienceLossPerc <= MaxExperienceLossPerc
Default: 0.1 / 0.5

DoArmorDrop
Meaning: Whether the four armor slots may drop
Type: bool
Default: false

DoMainhandDrop
Meaning: Whether the currently selected hotbar slot may drop
Type: bool
Default: false

DoOffhandDrop
Meaning: Whether the off-hand slot may drop
Type: bool
Default: false

DoToolbarDrop
Meaning: Whether the eight non-selected hotbar slots may drop
Type: bool
Default: true

StackInsurance
Meaning: Keep the final item in every stack that would otherwise lose all of its items
Type: bool
Default: true

DoDurabilityLoss
Meaning: Enable the independent durability-loss check for non-stackable durability items
Type: bool
Default: true

DurabilityLossChance
Meaning: Chance that an eligible item receives durability damage; checked before and independently from DropChance
Type: double
Range: [0.0, 1.0]
Default: 0.5

MinDurabilityLossPerc / MaxDurabilityLossPerc
Meaning: Inclusive uniform-random range for durability damage as a proportion of full maximum durability
Type: double
Range: [0.0, 1.0], MinDurabilityLossPerc <= MaxDurabilityLossPerc
Default: 0.05 / 0.2
Note: The calculated durability damage is rounded up

DurabilityInsurance
Meaning: Prevent compensation damage from destroying an item
Type: bool
Default: true

MaxExperienceOrbs
Meaning: Maximum number of experience-orb entities created for one player's death
Type: integer
Range: [1, 2147483647]
Default: 10

WhiteList
Meaning: Item identifiers that are exempt from item loss and durability loss
Type: list of identifiers
Default: empty
Examples: minecraft:totem_of_undying, othermod:valuable_item
```

## Commands

`/cdp get` is available to every command source. All modifying commands require permission level 4.

```text
/cdp get
/cdp reset

/cdp set dropChance 0.5
/cdp set minDropPerc 0.25
/cdp set maxDropPerc 0.75
/cdp set minExperienceLossPerc 0.1
/cdp set maxExperienceLossPerc 0.5
/cdp set doArmorDrop true
/cdp set doMainhandDrop true
/cdp set doOffhandDrop true
/cdp set doToolbarDrop false
/cdp set stackInsurance true
/cdp set doDurabilityLoss true
/cdp set durabilityLossChance 0.5
/cdp set minDurabilityLossPerc 0.05
/cdp set maxDurabilityLossPerc 0.2
/cdp set durabilityInsurance true
/cdp set maxExperienceOrbs 10

/cdp whitelist add minecraft:totem_of_undying
/cdp whitelist remove minecraft:totem_of_undying
/cdp whitelist list
```

The command rejects a percentage update that would make a minimum value exceed its matching maximum value. `/cdp reset` restores all defaults and clears the whitelist.
