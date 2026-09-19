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
            event.accept(GeneratedMod.MECHANICAL_BRIEFS);
            event.accept(GeneratedMod.SEVEN_CURSES_BRIEFS);
            event.accept(GeneratedMod.HI_TEETH_BRIEFS);
            event.accept(GeneratedMod.FIREWORK_BRIEFS);
            event.accept(GeneratedMod.BRIEFS_BRIEFS);
            event.accept(GeneratedMod.POOR_BRIEFS);
            event.accept(GeneratedMod.BROKEN_BRIEFS);
            event.accept(GeneratedMod.HEAVY_BRIEFS);
            event.accept(GeneratedMod.RAINBOW_BRIEFS);
            event.accept(GeneratedMod.CURRY_BRIEFS);
            event.accept(GeneratedMod.ENDER_PEARL_BRIEFS);
            event.accept(GeneratedMod.CREEPER_BRIEFS);
            event.accept(GeneratedMod.MIRROR_BRIEFS);
            event.accept(BriefsGear.ELASTIC_BELT_SWORD);
            event.accept(BriefsGear.ELASTIC_GRAPPLE);
            event.accept(BriefsGear.TALC_POWDER);
            event.accept(BriefsGear.FRESH_AIR_FAN_ITEM);
            event.accept(BriefsEcho.ECHO_BLADE);
            event.accept(BriefsEcho.ECHO_AMULET);
            event.accept(BriefsEcho.SONAR_PULSE);
            event.accept(BriefsEcho.RESONANCE_BELL_ITEM);
            event.accept(BriefsCryo.FROSTBITE_BLADE);
            event.accept(BriefsCryo.FROST_BARRIER);
            event.accept(BriefsCryo.FROST_FLASK);
            event.accept(BriefsCryo.CRYO_CONDENSER_ITEM);
            event.accept(BriefsFlux.FLUXBRAND);
            event.accept(BriefsFlux.FLUXCHARM);
            event.accept(BriefsFlux.FLUXCHARGE);
            event.accept(BriefsFlux.FLUXSHARD);
            event.accept(BriefsFlux.FLUXANCHOR_ITEM);
            event.accept(BriefsStorm.THUNDERFANG);
            event.accept(BriefsStorm.VOLT_CHARM);
            event.accept(BriefsStorm.STORM_SACHET);
            event.accept(BriefsStorm.VOLT_DART);
            event.accept(BriefsStorm.VOLT_STONE_ITEM);
            event.accept(BriefsShadow.SHADOWBLADE);
            event.accept(BriefsShadow.VEIL_CHARM);
            event.accept(BriefsShadow.VEIL_FLASK);
            event.accept(BriefsShadow.SHADE_DUST);
            event.accept(BriefsShadow.SHADE_ANCHOR_ITEM);
            event.accept(BriefsChrono.CHRONOBLADE);
            event.accept(BriefsChrono.REWIND_CHARM);
            event.accept(BriefsChrono.STASIS_FLASK);
            event.accept(BriefsChrono.REWIND_SAND);
            event.accept(BriefsChrono.CHRONO_ANCHOR_ITEM);
        }
    }
}
