package ru.alex.crystalmod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.alex.crystalmod.net.ShopRequestPayload;

/** Choosing a sample never transfers an inventory stack into the stall. */
final class StallItemScreen extends Screen {
    private final Screen parent;private final int containerId;private int x,y;private String feedback="";
    StallItemScreen(Screen parent,int containerId){super(Component.literal("Товар прилавка"));this.parent=parent;this.containerId=containerId;}
    void error(String json){try{feedback=com.google.gson.JsonParser.parseString(json).getAsJsonObject().get("error").getAsString();}catch(RuntimeException e){feedback="Не удалось выбрать товар";}}
    @Override protected void init(){
        x=(width-286)/2;y=(height-190)/2;
        for(int i=0;i<36;i++){
            final int slot=i;ItemStack item=minecraft.player.getInventory().getItem(i),plain=new ItemStack(item.getItem());
            Button b=Button.builder(Component.empty(),v->PacketDistributor.sendToServer(new ShopRequestPayload(containerId,0,"select","{\"slot\":"+slot+"}")))
                    .bounds(x+12+(i%9)*29,y+42+(i/9)*29,28,28).build();b.active=!item.isEmpty() && ItemStack.isSameItemSameComponents(item,plain);addRenderableWidget(b);
        }
        addRenderableWidget(Button.builder(Component.literal("Назад"),b->onClose()).bounds(x+12,y+165,262,18).build());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        renderBackground(g,mx,my,dt);g.fill(x,y,x+286,y+190,0xF0101018);g.drawString(font,title,x+12,y+10,0xE7D9FF,false);
        g.drawString(font,"Выберите обычный предмет из инвентаря",x+12,y+26,0xAAAAAA,false);for(var widget:renderables)widget.render(g,mx,my,dt);
        g.drawString(font,font.plainSubstrByWidth(feedback,262),x+12,y+153,0xF0BB80,false);
        for(int i=0;i<36;i++){int px=x+18+(i%9)*29,py=y+48+(i/9)*29;ItemStack item=minecraft.player.getInventory().getItem(i);g.renderItem(item,px,py);
            if(!item.isEmpty() && mx>=px-5 && mx<px+23 && my>=py-5 && my<py+23)g.renderTooltip(font,item,mx,my);
        }
    }
    @Override public void tick(){if(minecraft.player==null || minecraft.player.containerMenu.containerId!=containerId)minecraft.setScreen(null);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
