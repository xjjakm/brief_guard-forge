package cn.blockforge.generated.briefguard.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import cn.blockforge.generated.briefguard.BriefsMaterialKind;

@Mod.EventBusSubscriber(modid = "brief_guard", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BriefsClient {
    private static int accumValue;
    private static int accumMax;
    private static int accumKindOrdinal = -1;

    private BriefsClient() {}

    public static void sync(int entityId, ItemStack stack) {
        Player localPlayer = net.minecraft.client.Minecraft.getInstance().player;
        if (localPlayer == null) return;
        net.minecraft.world.entity.Entity entity = localPlayer.level().getEntity(entityId);
        if (entity instanceof Player target) {
            target.getCapability(cn.blockforge.generated.briefguard.BriefsCapability.UNDERWEAR)
                    .ifPresent(handler -> handler.setStack(stack));
        }
    }

    /** Server pushes this player's accumulation gauge for the HUD. */
    public static void onAccum(int entityId, int kindOrdinal, int value, int max) {
        Player localPlayer = net.minecraft.client.Minecraft.getInstance().player;
        if (localPlayer == null || localPlayer.getId() != entityId) return;
        accumKindOrdinal = kindOrdinal;
        accumValue = value;
        accumMax = max;
    }

    public static int accumValue() { return accumValue; }
    public static int accumMax() { return accumMax; }
    public static int accumKind() { return accumKindOrdinal; }

    /** 生成 HUD 上的积蓄文本；炫彩内裤显示当前元素名，其余显示 数值/上限。 */
    public static String gaugeText(int kindOrdinal, int value, int max) {
        if (kindOrdinal == BriefsMaterialKind.RAINBOW.ordinal()) {
            String[] names = {"火", "水", "雷", "毒", "霜", "光"};
            int idx = Math.max(0, Math.min(names.length - 1, value));
            return names[idx];
        }
        return value + "/" + max;
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            if (!(event.getSkin(skin) instanceof PlayerRenderer renderer)) continue;
            BriefsModel<AbstractClientPlayer> model = new BriefsModel<>(BriefsModel.createLayer().bakeRoot());
            renderer.addLayer(new BriefsLayer(
                    (net.minecraft.client.renderer.entity.RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>)
                            (net.minecraft.client.renderer.entity.RenderLayerParent<?, ?>) renderer,
                    model));
        }
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("briefs_bar", (forgeGui, gui, partialTick, width, height) -> BriefsHud.render(gui));
    }
}
