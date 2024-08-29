package jp.yunfachi.badapple;

import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.color.DyeColor;
import net.minestom.server.instance.block.banner.BannerPattern;
import net.minestom.server.item.ItemComponent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.BannerPatterns;
import net.minestom.server.item.component.CustomData;
import net.minestom.server.registry.DynamicRegistry;

import java.util.List;
import java.util.Map;

public class Shield {
    public static ItemStack createShield(DyeColor BaseColor, BannerPatterns.Layer... layers) {
        return ItemStack.builder(Material.SHIELD)
                .set(ItemComponent.BANNER_PATTERNS, new BannerPatterns(List.of(layers).reversed()))
                .set(ItemComponent.BASE_COLOR, BaseColor)
                .build();
    }

    public static BannerPatterns.Layer lr(DyeColor color, DynamicRegistry.Key pattern) {
        return new BannerPatterns.Layer(pattern, color);
    }

    static final Map<String, ItemStack> defaultShields = Map.ofEntries(
            Map.entry("0000", createShield(DyeColor.BLACK)),
            Map.entry("0001", createShield(DyeColor.BLACK, lr(DyeColor.WHITE, BannerPattern.HALF_HORIZONTAL_BOTTOM), lr(DyeColor.BLACK, BannerPattern.HALF_VERTICAL))),
            Map.entry("0010", createShield(DyeColor.BLACK, lr(DyeColor.WHITE, BannerPattern.HALF_HORIZONTAL_BOTTOM), lr(DyeColor.BLACK, BannerPattern.HALF_VERTICAL_RIGHT))),
            Map.entry("0011", createShield(DyeColor.BLACK, lr(DyeColor.WHITE, BannerPattern.HALF_HORIZONTAL_BOTTOM))),
            Map.entry("0100", createShield(DyeColor.BLACK, lr(DyeColor.WHITE, BannerPattern.HALF_HORIZONTAL), lr(DyeColor.BLACK, BannerPattern.HALF_VERTICAL))),
            Map.entry("0101", createShield(DyeColor.BLACK, lr(DyeColor.WHITE, BannerPattern.HALF_VERTICAL_RIGHT))),
            Map.entry("0110", createShield(DyeColor.WHITE, lr(DyeColor.BLACK, BannerPattern.SQUARE_TOP_LEFT), lr(DyeColor.BLACK, BannerPattern.SQUARE_BOTTOM_RIGHT))),
            Map.entry("0111", createShield(DyeColor.WHITE, lr(DyeColor.BLACK, BannerPattern.HALF_HORIZONTAL), lr(DyeColor.WHITE, BannerPattern.HALF_VERTICAL_RIGHT))),
            Map.entry("1000", createShield(DyeColor.BLACK, lr(DyeColor.WHITE, BannerPattern.HALF_HORIZONTAL), lr(DyeColor.BLACK, BannerPattern.HALF_VERTICAL_RIGHT))),
            Map.entry("1001", createShield(DyeColor.WHITE, lr(DyeColor.BLACK, BannerPattern.SQUARE_TOP_RIGHT), lr(DyeColor.BLACK, BannerPattern.SQUARE_BOTTOM_LEFT))),
            Map.entry("1010", createShield(DyeColor.BLACK, lr(DyeColor.WHITE, BannerPattern.HALF_VERTICAL))),
            Map.entry("1011", createShield(DyeColor.WHITE, lr(DyeColor.BLACK, BannerPattern.HALF_HORIZONTAL), lr(DyeColor.BLACK, BannerPattern.HALF_VERTICAL))),
            Map.entry("1100", createShield(DyeColor.BLACK, lr(DyeColor.WHITE, BannerPattern.HALF_HORIZONTAL))),
            Map.entry("1101", createShield(DyeColor.WHITE, lr(DyeColor.BLACK, BannerPattern.HALF_HORIZONTAL_BOTTOM), lr(DyeColor.BLACK, BannerPattern.HALF_VERTICAL_RIGHT))),
            Map.entry("1110", createShield(DyeColor.WHITE, lr(DyeColor.BLACK, BannerPattern.HALF_HORIZONTAL_BOTTOM), lr(DyeColor.BLACK, BannerPattern.HALF_VERTICAL))),
            Map.entry("1111", createShield(DyeColor.WHITE))
    );
}
