package cn.blockforge.generated.briefguard;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeneratedMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BriefsCreative {
    private BriefsCreative() {}

    @SubscribeEvent
    public static void addItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(GeneratedMod.LEATHER_BRIEFS);
            event.accept(GeneratedMod.COPPER_BRIEFS);
            event.accept(GeneratedMod.CHAIN_BRIEFS);
            event.accept(GeneratedMod.IRON_BRIEFS);
            event.accept(GeneratedMod.GOLD_BRIEFS);
            event.accept(GeneratedMod.DIAMOND_BRIEFS);
            event.accept(GeneratedMod.NETHERITE_BRIEFS);
            event.accept(GeneratedMod.DRAGON_HEAD_BRIEFS);
            event.accept(GeneratedMod.CHASTITY_BRIEFS);
            event.accept(GeneratedMod.SLIME_BRIEFS);
            event.accept(GeneratedMod.SPICY_BRIEFS);
            event.accept(GeneratedMod.POOP_BRIEFS);
            event.accept(GeneratedMod.SILVERFISH_BRIEFS);
            event.accept(GeneratedMod.TENTACLE_BRIEFS);
            event.accept(GeneratedMod.EDIBLE_BRIEFS);
            event.accept(GeneratedMod.TRAPDOOR_BRIEFS);
            event.accept(GeneratedMod.PROMOTION_BRIEFS);
            event.accept(GeneratedMod.STICKY_PISTON_BRIEFS);
            event.accept(GeneratedMod.GASEOUS_BRIEFS);
            event.accept(GeneratedMod.SWORD_BRIEFS);
            event.accept(GeneratedMod.SHIELD_BRIEFS);
        }
    }
}
