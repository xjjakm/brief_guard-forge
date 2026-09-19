package cn.blockforge.generated.briefguard.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import cn.blockforge.generated.briefguard.BriefsCapability;
import cn.blockforge.generated.briefguard.BriefsNetwork;

/**
 * 内裤栏 HUD：在快捷栏左侧独立显示当前穿着的内裤（空槽也常显），并显示其积蓄进度。
 * 空手右键（未指向方块）可脱下；蹲下 + 空手右键可激活主动技能。
 */
@Mod.EventBusSubscriber(modid = "brief_guard", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BriefsHud {
    private static final int SIZE = 18;

    private BriefsHud() {}

    public static int slotX() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getWindow().getGuiScaledWidth() / 2 - 91 - SIZE - 6;
    }

    public static int slotY() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getWindow().getGuiScaledHeight() - 42;
    }

    public static void render(GuiGraphics gui) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int x = slotX();
        int y = slotY();
        // 槽位边框：缺口样式，常态显示。
        gui.fill(x, y, x + SIZE, y + SIZE, 0xFF3F3F3F);
        gui.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, 0xFF1A1A1A);
        // 未穿戴常显空槽；穿戴则显示物品图标。
        ItemStack worn = BriefsCapability.get(mc.player).getStack();
        if (!worn.isEmpty()) {
            gui.renderItem(worn, x + 1, y + 1);
        }
        // 标签
        gui.drawString(mc.font, Component.translatable("hud.brief_guard.briefs_bar"),
                x - 1, y - 11, 0xE0E0E0);

        // 积蓄提示：进度条 + 数值。
        int kind = BriefsClient.accumKind();
        int value = BriefsClient.accumValue();
        int max = BriefsClient.accumMax();
        if (max > 0) {
            int clamped = Math.max(0, Math.min(max, value));
            int filled = (int) Math.round(SIZE * (double) clamped / max);
            int barColor = clamped >= max ? 0xFFFFD24A : 0xFF7CE86A;
            gui.fill(x + 1, y + SIZE - 4, x + 1 + Math.max(1, filled), y + SIZE - 2, barColor);
            String text = BriefsClient.gaugeText(kind, value, max);
            gui.drawString(mc.font, text, x, y - 20, 0xFFFFFF);
        }
    }

    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Player player = event.getEntity();
        if (player == null) return;
        // 仅在客户端（Dist.CLIENT）处理：空手右键，未指向方块。
        if (!player.getMainHandItem().isEmpty()) return;
        if (BriefsCapability.get(player).getStack().isEmpty()) return;
        if (player.isShiftKeyDown()) {
            BriefsNetwork.sendActivate();
        } else {
            BriefsNetwork.sendRemove();
        }
    }

    @SubscribeEvent
    public static void onClick(InputEvent.MouseButton event) {
        if (event.getButton() != 0) return;   // 仅左键，避免与空手右键的主动技能冲突
        if (event.getAction() != 0) return;   // 按下才算
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || mc.level == null) return; // 仅在游戏内
        double scale = mc.getWindow().getGuiScale();
        int mx = (int) (mc.mouseHandler.xpos() / scale);
        int my = (int) (mc.mouseHandler.ypos() / scale);
        int x = slotX();
        int y = slotY();
        if (mx >= x && mx < x + SIZE && my >= y && my < y + SIZE) {
            if (!BriefsCapability.get(mc.player).getStack().isEmpty()) {
                BriefsNetwork.sendRemove();
                mc.player.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC, 0.6F, 1.0F);
            }
        }
    }
}
