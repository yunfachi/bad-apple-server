package net.minestom.server.entity;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import static net.minestom.server.utils.SlotUtils.*;

public enum EquipmentSlot {
    MAIN_HAND(false, -1),
    OFF_HAND(false, -1),
    BOOTS(true, BOOTS_SLOT),
    LEGGINGS(true, LEGGINGS_SLOT),
    CHESTPLATE(true, CHESTPLATE_SLOT),
    HELMET(true, HELMET_SLOT);

    private static final List<EquipmentSlot> ARMORS = List.of(BOOTS, LEGGINGS, CHESTPLATE, HELMET);

    private final boolean armor;
    private final int armorSlot;

    EquipmentSlot(boolean armor, int armorSlot) {
        this.armor = armor;
        this.armorSlot = armorSlot;
    }

    public boolean isHand() {
        return !armor;
    }

    public boolean isArmor() {
        return armor;
    }

    public int armorSlot() {
        return armorSlot;
    }

    public static @NotNull List<@NotNull EquipmentSlot> armors() {
        return ARMORS;
    }
}
