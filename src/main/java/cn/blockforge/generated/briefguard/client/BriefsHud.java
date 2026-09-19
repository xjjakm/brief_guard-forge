package cn.blockforge.generated.briefguard.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import cn.blockforge.generated.briefguard.BriefsCapability;
import cn.blockforge.generated.briefguard.BriefsNetwork;

/**
 * 内裤栏 HUD：在快捷栏左侧独立显示当前穿着的内裤（空槽也常显）。
 * 在空手、未打开任何界面时点击该槽位即可脱下内裤。
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
