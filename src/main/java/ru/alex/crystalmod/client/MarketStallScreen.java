package ru.alex.crystalmod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ru.alex.crystalmod.MarketStallMenu;

public class MarketStallScreen extends AbstractContainerScreen<MarketStallMenu> {
    private Button buy;
    public MarketStallScreen(MarketStallMenu menu, Inventory inventory, Component title) {
        super(menu,inventory,title); imageWidth=260; imageHeight=210;
    }
    private void button(String label,int x,int y,int width,int action) {
        addRenderableWidget(Button.builder(Component.literal(label), b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action))
                .bounds(leftPos+x,topPos+y,width,20).build());
    }
    @Override protected void init() {
        super.init();
        buy=addRenderableWidget(Button.builder(Component.translatable("crystalmod.stall.buy"), b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0))
                .bounds(leftPos+15,topPos+92,230,20).build());
        button("-10",15,132,45,3); button("-1",65,132,45,1); button("+1",115,132,45,2); button("+10",165,132,45,4);
        button("1",15,175,45,5); button("8",65,175,45,6); button("32",115,175,45,7); button("64",165,175,45,8);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
        buy.active=menu.data.get(4)==0 && menu.data.get(3)==0;
        for(var child:children()) if(child instanceof Button b && b!=buy) { b.visible=menu.data.get(3)==1; b.active=menu.data.get(4)==0; }
        super.render(g,mouseX,mouseY,partialTick);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y) {
        g.drawString(font,title,15,12,0xE7D9FF,false);
        if(!menu.offeredSample.isEmpty()) {
            g.renderItem(menu.offeredSample,16,32);
            g.drawString(font,font.plainSubstrByWidth(menu.offeredSample.getHoverName().getString(),205),40,36,0xFFFFFF,false);
        }
        g.drawString(font,Component.translatable("crystalmod.stall.offer",menu.data.get(1),menu.data.get(0)),15,58,0x94E2D5,false);
        g.drawString(font,Component.translatable("crystalmod.stall.stock",menu.data.get(2)),15,74,0xCCCCCC,false);
        if(menu.data.get(3)==1) {
            g.drawString(font,Component.translatable("crystalmod.stall.price_label"),15,120,0xCCCCCC,false);
            g.drawString(font,Component.translatable("crystalmod.stall.party_label"),15,161,0xCCCCCC,false);
        } else {
            g.drawString(font,Component.translatable("crystalmod.stall.local_only"),15,130,0xAAAAAA,false);
            if(menu.data.get(5)==1) g.drawString(font,Component.translatable("crystalmod.stall.success_label"),15,155,0x94E2D5,false);
        }
    }
    @Override protected void renderBg(GuiGraphics g,float tick,int x,int y) {
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xF0202030);
        g.fill(leftPos+2,topPos+2,leftPos+imageWidth-2,topPos+imageHeight-2,0xF0101018);
    }
}
