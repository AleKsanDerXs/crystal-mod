package ru.alex.crystalmod.client;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.alex.crystalmod.net.ShopRequestPayload;
import ru.alex.crystalmod.net.ShopResponsePayload;

/** Owner-only summaries; requests are made only when the player opens or pages the book. */
public final class ShopLedgerScreen extends Screen {
    private final int containerId; private int sequence,page=1,total,offset; private boolean waiting,started,details; private String titleText="Торговая книга",message="";
    private JsonArray rows=new JsonArray();
    public ShopLedgerScreen(int id){super(Component.literal("Торговая книга"));containerId=id;}
    private void request(String action,JsonObject body){if(waiting)return;waiting=true;message="Загрузка…";PacketDistributor.sendToServer(new ShopRequestPayload(containerId,++sequence,action,body.toString()));rebuildWidgets();}
    private void list(){JsonObject b=new JsonObject();b.addProperty("page",page);request("list",b);}
    void receive(ShopResponsePayload p){
        if(p.containerId()!=containerId || p.requestId()!=sequence)return;
        waiting=false;try{JsonObject r=JsonParser.parseString(p.json()).getAsJsonObject();if(!r.get("ok").getAsBoolean()){message=r.has("error")?r.get("error").getAsString():"Ошибка";}else if(p.action().equals("list")){rows=r.getAsJsonArray("items");page=r.get("page").getAsInt();total=r.get("total").getAsInt();details=false;titleText="Выберите магазин";message=rows.isEmpty()?"Магазинов пока нет":"";}else if(p.action().equals("summary")){rows=r.getAsJsonArray("items");details=true;offset=0;titleText=r.get("name").getAsString();message=rows.isEmpty()?"Продаж пока нет":"Последние 100 строк по дням и товарам";}}catch(RuntimeException e){message="Некорректный ответ сайта";}rebuildWidgets();
    }
    private Button button(String label,int x,int y,int w,Runnable action){Button b=Button.builder(Component.literal(label),v->action.run()).bounds((width-330)/2+x,(height-210)/2+y,w,18).build();b.active=!waiting;return addRenderableWidget(b);}
    @Override protected void init(){
        if(!details){int y=35;for(JsonElement e:rows){JsonObject shop=e.getAsJsonObject();int id=shop.get("id").getAsInt();button(font.plainSubstrByWidth(shop.get("name").getAsString(),290),12,y,306,()->{JsonObject b=new JsonObject();b.addProperty("id",id);request("summary",b);});y+=26;}}
        Button prev=button("←",12,184,50,()->{if(details){offset=Math.max(0,offset-5);rebuildWidgets();}else{page--;list();}});prev.active&=details?offset>0:page>1;
        Button next=button("→",68,184,50,()->{if(details){offset+=5;rebuildWidgets();}else{page++;list();}});next.active&=details?offset+5<rows.size():page*4<total;
        button(details?"К магазинам":"Закрыть",198,184,120,()->{if(details){details=false;page=1;list();}else onClose();});
        if(!started){started=true;list();}
    }
    @Override public void tick(){if(minecraft.player==null || minecraft.player.containerMenu.containerId!=containerId)minecraft.setScreen(null);}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        renderBackground(g,mx,my,dt);int x=(width-330)/2,y=(height-210)/2;g.fill(x,y,x+330,y+210,0xF0101018);g.drawString(font,font.plainSubstrByWidth(titleText,300),x+12,y+10,0xE7D9FF,false);
        if(details){g.drawString(font,"Дата / товар · ушло · приход · комиссия",x+12,y+26,0xAAAAAA,false);for(int i=offset;i<Math.min(rows.size(),offset+5);i++){JsonObject row=rows.get(i).getAsJsonObject();String item=row.get("item_id").isJsonNull()?"?":row.get("item_id").getAsString();var key=net.minecraft.resources.ResourceLocation.tryParse(item);if(key!=null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key))item=new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(key)).getHoverName().getString();String line=row.get("day").getAsString()+"  "+item+" ×"+row.get("goods").getAsString();g.drawString(font,font.plainSubstrByWidth(line,306),x+12,y+42+(i-offset)*27,0xFFFFFF,false);g.drawString(font,"Приход: "+row.get("income").getAsString()+"  Комиссия: "+row.get("commission").getAsString(),x+12,y+53+(i-offset)*27,0x94E2D5,false);}}
        for(var w:renderables)w.render(g,mx,my,dt);g.drawString(font,font.plainSubstrByWidth(message,306),x+12,y+170,0xF0BB80,false);
    }
    @Override public boolean isPauseScreen(){return false;}
}
