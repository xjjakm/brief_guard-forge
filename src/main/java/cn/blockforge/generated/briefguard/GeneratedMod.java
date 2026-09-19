package cn.blockforge.generated.briefguard;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.ResourceLocation;

@Mod(GeneratedMod.MOD_ID)
public final class GeneratedMod {
    public static final String MOD_ID = "brief_guard";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    public static final ArmorMaterial LEATHER_MATERIAL = new BriefsMaterial("leather", 5, 2, 0, 15, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, Ingredient.of(Items.LEATHER));
    public static final ArmorMaterial COPPER_MATERIAL = new BriefsMaterial("copper", 7, 4, 1, 10, SoundEvents.ARMOR_EQUIP_IRON, 0.0F, Ingredient.of(Items.COPPER_INGOT));
    public static final ArmorMaterial CHAIN_MATERIAL = new BriefsMaterial("chain", 12, 5, 1, 12, SoundEvents.ARMOR_EQUIP_CHAIN, 0.0F, Ingredient.of(Items.IRON_NUGGET));
    public static final ArmorMaterial IRON_MATERIAL = new BriefsMaterial("iron", 15, 6, 2, 9, SoundEvents.ARMOR_EQUIP_IRON, 0.0F, Ingredient.of(Items.IRON_INGOT));
    public static final ArmorMaterial GOLD_MATERIAL = new BriefsMaterial("gold", 7, 5, 0, 25, SoundEvents.ARMOR_EQUIP_GOLD, 0.0F, Ingredient.of(Items.GOLD_INGOT));
    public static final ArmorMaterial DIAMOND_MATERIAL = new BriefsMaterial("diamond", 33, 8, 3, 10, SoundEvents.ARMOR_EQUIP_DIAMOND, 0.1F, Ingredient.of(Items.DIAMOND));
    public static final ArmorMaterial NETHERITE_MATERIAL = new BriefsMaterial("netherite", 37, 10, 4, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, 0.9F, Ingredient.of(Items.NETHERITE_INGOT));

    // 扩展：十四种以机制为主的内裤材料
    public static final ArmorMaterial DRAGON_HEAD_MATERIAL = new BriefsMaterial("dragon_head", 33, 8, 3, 12, SoundEvents.ARMOR_EQUIP_NETHERITE, 0.2F, Ingredient.of(Items.BLAZE_ROD));
    public static final ArmorMaterial CHASTITY_MATERIAL = new BriefsMaterial("chastity", 37, 10, 4, 15, SoundEvents.ARMOR_EQUIP_IRON, 0.6F, Ingredient.of(Items.IRON_INGOT));
    public static final ArmorMaterial SLIME_MATERIAL = new BriefsMaterial("slime", 12, 6, 0, 8, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, Ingredient.of(Items.SLIME_BALL));
    public static final ArmorMaterial SPICY_MATERIAL = new BriefsMaterial("spicy", 8, 3, 0, 20, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, Ingredient.of(Items.SWEET_BERRIES));
    public static final ArmorMaterial POOP_MATERIAL = new BriefsMaterial("poop", 10, 4, 0, 5, SoundEvents.ARMOR_EQUIP_GENERIC, 0.0F, Ingredient.of(Items.BROWN_DYE));
    public static final ArmorMaterial SILVERFISH_MATERIAL = new BriefsMaterial("silverfish", 15, 5, 0, 6, SoundEvents.ARMOR_EQUIP_GENERIC, 0.0F, Ingredient.of(Items.STONE));
    public static final ArmorMaterial TENTACLE_MATERIAL = new BriefsMaterial("tentacle", 20, 6, 1, 10, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, Ingredient.of(Items.KELP));
    public static final ArmorMaterial EDIBLE_MATERIAL = new BriefsMaterial("edible", 6, 1, 0, 30, SoundEvents.ARMOR_EQUIP_GENERIC, 0.0F, Ingredient.of(Items.WHEAT));
    public static final ArmorMaterial TRAPDOOR_MATERIAL = new BriefsMaterial("trapdoor", 25, 7, 1, 6, SoundEvents.ARMOR_EQUIP_GENERIC, 0.0F, Ingredient.of(Items.OAK_PLANKS));
    public static final ArmorMaterial PROMOTION_MATERIAL = new BriefsMaterial("promotion", 26, 5, 1, 18, SoundEvents.ARMOR_EQUIP_DIAMOND, 0.0F, Ingredient.of(Items.EMERALD));
    public static final ArmorMaterial STICKY_PISTON_MATERIAL = new BriefsMaterial("sticky_piston", 22, 7, 0, 8, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, Ingredient.of(Items.SLIME_BALL));
    public static final ArmorMaterial GASEOUS_MATERIAL = new BriefsMaterial("gaseous", 10, 3, 0, 12, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, Ingredient.of(Items.PHANTOM_MEMBRANE));
    public static final ArmorMaterial SWORD_MATERIAL = new BriefsMaterial("sword", 28, 6, 1, 9, SoundEvents.ARMOR_EQUIP_IRON, 0.0F, Ingredient.of(Items.IRON_INGOT));
    public static final ArmorMaterial SHIELD_MATERIAL = new BriefsMaterial("shield", 34, 9, 2, 11, SoundEvents.ARMOR_EQUIP_IRON, 0.3F, Ingredient.of(Items.IRON_INGOT));

    public static final RegistryObject<Item> LEATHER_BRIEFS = ITEMS.register("leather_briefs", () -> new BriefsArmorItem(LEATHER_MATERIAL, Type.HELMET, BriefsMaterialKind.LEATHER, new Item.Properties().durability(65)));
    public static final RegistryObject<Item> COPPER_BRIEFS = ITEMS.register("copper_briefs", () -> new BriefsArmorItem(COPPER_MATERIAL, Type.CHESTPLATE, BriefsMaterialKind.COPPER, new Item.Properties().durability(91)));
    public static final RegistryObject<Item> CHAIN_BRIEFS = ITEMS.register("chain_briefs", () -> new BriefsArmorItem(CHAIN_MATERIAL, Type.CHESTPLATE, BriefsMaterialKind.CHAIN, new Item.Properties().durability(156)));
    public static final RegistryObject<Item> IRON_BRIEFS = ITEMS.register("iron_briefs", () -> new BriefsArmorItem(IRON_MATERIAL, Type.CHESTPLATE, BriefsMaterialKind.IRON, new Item.Properties().durability(195)));
    public static final RegistryObject<Item> GOLD_BRIEFS = ITEMS.register("gold_briefs", () -> new BriefsArmorItem(GOLD_MATERIAL, Type.CHESTPLATE, BriefsMaterialKind.GOLD, new Item.Properties().durability(91)));
    public static final RegistryObject<Item> DIAMOND_BRIEFS = ITEMS.register("diamond_briefs", () -> new BriefsArmorItem(DIAMOND_MATERIAL, Type.CHESTPLATE, BriefsMaterialKind.DIAMOND, new Item.Properties().durability(429)));
    public static final RegistryObject<Item> NETHERITE_BRIEFS = ITEMS.register("netherite_briefs", () -> new BriefsArmorItem(NETHERITE_MATERIAL, Type.CHESTPLATE, BriefsMaterialKind.NETHERITE, new Item.Properties().durability(481).fireResistant()));

    // 扩展：十四种以机制为主的内裤物品
    public static final RegistryObject<Item> DRAGON_HEAD_BRIEFS = ITEMS.register("dragon_head_briefs", () -> new BriefsArmorItem(DRAGON_HEAD_MATERIAL, Type.CHESTPLATE, BriefsMaterialKind.DRAGON_HEAD, new Item.Properties().durability(429).fireResistant()));
    public static final RegistryObject<Item> CHASTITY_BRIEFS = ITEMS.register("chastity_briefs", () -> new BriefsArmorItem(CHASTITY_MATERIAL, Type.CHESTPLATE, BriefsMaterialKind.CHASTITY, new Item.Properties().durability(481)));
    public static final RegistryObject<Item> SLIME_BRIEFS = ITEMS.register("slime_briefs", () -> new BriefsArmorItem(SLIME_MATERIAL, Type.CHESTPLATE, BriefsMaterialKind.SLIME, new Item.Properties().durability(156)));
    public static final RegistryObject<Item> SPICY_BRIEFS = ITEMS.register("spicy_briefs", () -> new BriefsArmorItem(SPICY_MATERIAL, Type.CHESTPLATE, BriefsMaterialKind.SPICY, new Item.Properties().durability(104)));
    public static final RegistryObject<Item> POOP_BRIEFS = ITEMS.register("poop_briefs", () -> new BriefsArmorItem(POOP_MATERIAL, Type.CHESTPLATE, BriefsMaterialKind.POOP, new Item.Properties().durability(130)));
    public static final RegistryObject<Item> SILVERFISH_BRIEFS = ITEMS.register("silverfish_briefs", () -> new BriefsArmorItem(SILVERFISH_MATERIAL, Type.CHESTPLATE, BriefsMaterialKind.SILVERFISH, new Item.Properties().durability(195)));
    public static final RegistryObject<Item> TENTACLE_BRIEFS = ITEMS.register("tentacle_briefs", () -> new BriefsArmorItem(TENTACLE_MATERIAL, Type.CHESTPLATE, BriefsMaterialKind.TENTACLE, new Item.Properties().durability(260)));
    public static final RegistryObject<Item> EDIBLE_BRIEFS = ITEMS.register("edible_briefs", () -> new BriefsArmorItem(EDIBLE_MATERIAL, Type.CHESTPLATE, BriefsMaterialKind.EDIBLE, new Item.Properties().durability(78)));
    public static final RegistryObject<Item> TRAPDOOR_BRIEFS = ITEMS.register("trapdoor_briefs", () -> new BriefsArmorItem(TRAPDOOR_MATERIAL, Type.CHESTPLATE, BriefsMaterialKind.TRAPDOOR, new Item.Properties().durability(325)));
    public static final RegistryObject<Item> PROMOTION_BRIEFS = ITEMS.register("promotion_briefs", () -> new BriefsArmorItem(PROMOTION_MATERIAL, Type.CHESTPLATE, BriefsMaterialKind.PROMOTION, new Item.Properties().durability(338)));
    public static final RegistryObject<Item> STICKY_PISTON_BRIEFS = ITEMS.register("sticky_piston_briefs", () -> new BriefsArmorItem(STICKY_PISTON_MATERIAL, Type.CHESTPLATE, BriefsMaterialKind.STICKY_PISTON, new Item.Properties().durability(286)));
    public static final RegistryObject<Item> GASEOUS_BRIEFS = ITEMS.register("gaseous_briefs", () -> new BriefsArmorItem(GASEOUS_MATERIAL, Type.CHESTPLATE, BriefsMaterialKind.GASEOUS, new Item.Properties().durability(130)));
    public static final RegistryObject<Item> SWORD_BRIEFS = ITEMS.register("sword_briefs", () -> new BriefsArmorItem(SWORD_MATERIAL, Type.CHESTPLATE, BriefsMaterialKind.SWORD, new Item.Properties().durability(364)));
    public static final RegistryObject<Item> SHIELD_BRIEFS = ITEMS.register("shield_briefs", () -> new BriefsArmorItem(SHIELD_MATERIAL, Type.CHESTPLATE, BriefsMaterialKind.SHIELD, new Item.Properties().durability(442)));

    public GeneratedMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        bus.addListener(BriefsCapability::registerCapabilities);
        BriefsNetwork.init();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
