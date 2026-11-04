package ru.alex.crystalmod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.alex.crystalmod.MarketStallMenu;
import ru.alex.crystalmod.net.StallConfigurePayload;

public class MarketStallScreen extends AbstractContainerScreen<MarketStallMenu> {
    private Button buy,save,mode,shops,item;
    private EditBox price,quantity;
    private boolean perUnit,hydrated;
    public MarketStallScreen(MarketStallMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=286;imageHeight=230;}
    private static int number(EditBox box){try{return Integer.parseInt(box.getValue());}catch(NumberFormatException e){return -1;}}
    private EditBox field(int x,String key,int length){
        EditBox box=new EditBox(font,leftPos+x,topPos+139,120,18,Component.translatable(key));
        box.setMaxLength(length);box.setFilter(s->s.matches("[0-9]*"));addRenderableWidget(box);return box;
    }
    @Override protected void init(){
        super.init();hydrated=false;perUnit=false;
        buy=addRenderableWidget(Button.builder(Component.translatable("crystalmod.stall.buy"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0)).bounds(leftPos+15,topPos+92,256,20).build());
        addRenderableWidget(Button.builder(Component.translatable("crystalmod.stall.refresh"),b->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,9)).bounds(leftPos+189,topPos+72,82,16).build());
        shops=addRenderableWidget(Button.builder(Component.translatable("crystalmod.stall.shops"),b->minecraft.setScreen(new ShopScreen(this,menu.containerId))).bounds(leftPos+151,topPos+92,120,20).build());
        item=addRenderableWidget(Button.builder(Component.translatable("crystalmod.stall.choose_item"),b->minecraft.setScreen(new StallItemScreen(this,menu.containerId))).bounds(leftPos+15,topPos+92,120,20).build());
        price=field(15,"crystalmod.stall.price_label",5);quantity=field(151,"crystalmod.stall.party_label",2);
        mode=addRenderableWidget(Button.builder(Component.translatable("crystalmod.stall.per_party"),b->{
            int p=number(price),q=number(quantity);boolean next=!perUnit;
            if(p>0 && q>0){
                long converted=next?(p%q==0?p/q:-1):(long)p*q;
                price.setValue(converted>0 && converted<=30000?Long.toString(converted):"");
            }
            perUnit=next;b.setMessage(Component.translatable(next?"crystalmod.stall.per_unit":"crystalmod.stall.per_party"));
        }).bounds(leftPos+15,topPos+166,120,20).build());
        mode.setTooltip(Tooltip.create(Component.translatable("crystalmod.stall.unit_hint")));
        save=addRenderableWidget(Button.builder(Component.translatable("crystalmod.stall.save"),b->PacketDistributor.sendToServer(new StallConfigurePayload(menu.containerId,number(price),number(quantity),perUnit))).bounds(leftPos+151,topPos+166,120,20).build());
    }
    @Override public void containerTick(){
        super.containerTick();
        if(!hydrated && menu.data.get(0)>0 && menu.data.get(1)>0){price.setValue(Integer.toString(menu.data.get(0)));quantity.setValue(Integer.toString(menu.data.get(1)));hydrated=true;}
    }
    private long total(){return perUnit?(long)number(price)*number(quantity):number(price);}
    private boolean valid(){return number(price)>0 && number(quantity)>0 && number(quantity)<=64 && total()>0 && total()<=30000;}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        boolean owner=menu.data.get(3)==1,busy=menu.data.get(4)!=0;
        buy.visible=!owner;buy.active=!owner && !busy;shops.visible=owner;item.visible=owner;shops.active=!busy;item.active=!busy;
        price.setVisible(owner);quantity.setVisible(owner);mode.visible=owner;save.visible=owner;
        price.setEditable(!busy);quantity.setEditable(!busy);mode.active=!busy;save.active=owner && !busy && valid();
        super.render(g,mouseX,mouseY,partialTick);
    }
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){
        g.drawString(font,title,15,12,0xE7D9FF,false);
        if(!menu.offeredSample.isEmpty()){g.renderItem(menu.offeredSample,16,32);g.drawString(font,font.plainSubstrByWidth(menu.offeredSample.getHoverName().getString(),225),40,36,0xFFFFFF,false);}
        g.drawString(font,Component.translatable("crystalmod.stall.offer",menu.data.get(1),menu.data.get(0)),15,58,0x94E2D5,false);
        g.drawString(font,Component.translatable("crystalmod.stall.stock",menu.data.get(2)),15,74,0xCCCCCC,false);
        if(menu.data.get(3)==1){
            g.drawString(font,Component.translatable("crystalmod.stall.price_input"),15,124,0xCCCCCC,false);
            g.drawString(font,Component.translatable("crystalmod.stall.party_label"),151,124,0xCCCCCC,false);
            g.drawString(font,Component.translatable(valid()?"crystalmod.stall.offer_preview":"crystalmod.stall.invalid_offer",total(),number(quantity)),15,195,valid()?0x94E2D5:0xF0BB80,false);
            if(menu.data.get(5)==3 && total()==menu.data.get(0) && number(quantity)==menu.data.get(1))g.drawString(font,Component.translatable("crystalmod.stall.saved"),15,210,0x94E2D5,false);
        }else{
            g.drawString(font,Component.translatable("crystalmod.stall.local_only"),15,130,0xAAAAAA,false);
            if(menu.data.get(5)==1)g.drawString(font,Component.translatable("crystalmod.stall.success_label"),15,155,0x94E2D5,false);
        }
    }
    @Override protected void renderBg(GuiGraphics g,float tick,int x,int y){g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xF0202030);g.fill(leftPos+2,topPos+2,leftPos+imageWidth-2,topPos+imageHeight-2,0xF0101018);}
}
