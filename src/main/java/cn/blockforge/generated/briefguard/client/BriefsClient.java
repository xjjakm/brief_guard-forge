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

@Mod.EventBusSubscriber(modid = "brief_guard", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BriefsClient {
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
