package cn.blockforge.generated.briefguard;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

public final class BriefsMaterial implements ArmorMaterial {
    private final String name;
    private final int durability;
    private final int defense;
    private final float toughness;
    private final int enchantmentValue;
    private final SoundEvent equipSound;
    private final float knockbackResistance;
    private final Ingredient repairIngredient;

    public BriefsMaterial(String name, int durability, int defense, float toughness, int enchantmentValue,
                          SoundEvent equipSound, float knockbackResistance, Ingredient repairIngredient) {
        this.name = name;
        this.durability = durability;
        this.defense = defense;
        this.toughness = toughness;
        this.enchantmentValue = enchantmentValue;
        this.equipSound = equipSound;
        this.knockbackResistance = knockbackResistance;
        this.repairIngredient = repairIngredient;
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return durability * (type == ArmorItem.Type.HELMET ? 11 : 16);
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return defense;
    }

    @Override
    public int getEnchantmentValue() {
        return enchantmentValue;
    }

    @Override
    public SoundEvent getEquipSound() {
        return equipSound;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return repairIngredient;
    }

    @Override
    public String getName() {
        return GeneratedMod.MOD_ID + ":" + name;
    }

    @Override
    public float getToughness() {
        return toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return knockbackResistance;
    }
}
