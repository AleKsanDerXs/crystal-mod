package ru.alex.crystalmod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ru.alex.crystalmod.MarketStallMenu;
import ru.alex.crystalmod.net.ShopRequestPayload;
import ru.alex.crystalmod.net.ShopResponsePayload;

/** Shop forms keep the current container open; late replies cannot replace a newer form. */
public final class ShopScreen extends Screen {
    private static final AtomicInteger IDS=new AtomicInteger();
    private static final String[] EMBLEMS={"fa-store","fa-industry","fa-anchor","fa-gem","fa-wheat-awn","fa-hammer"};
    private static final String[] EMBLEM_NAMES={"Лавка","Завод","Порт","Кристалл","Ферма","Мастерская"};
    private static final String[] COVERS={"aurora","forge","forest"},COVER_NAMES={"Сияние","Кузница","Лес"};
    private final Screen parent;
    private final int containerId;
    private int x,y,lastRequest,page=1,total,linkedId;
    private boolean waiting,started,editor,appearance;
    private String message="",linkedName="";
    private JsonArray shops=new JsonArray();
    private JsonObject draft=new JsonObject();
    private Button save;
    public ShopScreen(Screen parent,int containerId){super(Component.literal("Мои магазины"));this.parent=parent;this.containerId=containerId;}
    public static void handle(ShopResponsePayload p,IPayloadContext ctx){ctx.enqueueWork(()->{
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.player.containerMenu.containerId!=p.containerId())return;
        if(p.action().equals("ledger_open")){mc.setScreen(new ShopLedgerScreen(p.containerId()));return;}
        if(mc.screen instanceof ShopLedgerScreen ledger){ledger.receive(p);return;}
        if(p.action().equals("open")){mc.setScreen(new ShopScreen(mc.screen,p.containerId()));return;}
        if(mc.screen instanceof StallItemScreen selector && p.action().equals("select")){selector.error(p.json());return;}
        if(mc.screen instanceof ShopScreen screen && screen.containerId==p.containerId() && screen.lastRequest==p.requestId())screen.apply(p);
    });}
    private boolean canLink(){return minecraft.player.containerMenu instanceof MarketStallMenu m && m.data.get(3)==1 && !m.offeredSample.isEmpty();}
    public boolean pending(){return waiting;}
    public boolean editing(){return editor;}
    public String feedback(){return message;}
    private void request(String action,JsonObject body){
        if(waiting)return;waiting=true;lastRequest=IDS.incrementAndGet();
        PacketDistributor.sendToServer(new ShopRequestPayload(containerId,lastRequest,action,body.toString()));rebuildWidgets();
    }
    private void list(){JsonObject b=new JsonObject();b.addProperty("page",page);request("list",b);}
    private void apply(ShopResponsePayload packet){
        waiting=false;
        try{
            JsonObject reply=JsonParser.parseString(packet.json()).getAsJsonObject();
            if(!reply.get("ok").getAsBoolean()){message=reply.has("error")?reply.get("error").getAsString():"Не удалось сохранить";rebuildWidgets();return;}
            switch(packet.action()){
                case "list"->{shops=reply.getAsJsonArray("items");page=reply.get("page").getAsInt();total=reply.get("total").getAsInt();linkedId=reply.get("linked_shop_id").getAsInt();linkedName=reply.get("linked_shop_name").getAsString();}
                case "get"->{draft=reply.getAsJsonObject("profile").deepCopy();draft.add("id",reply.get("id"));editor=true;appearance=false;message="";}
                case "save"->{editor=false;message="Заявка отправлена на проверку";page=1;list();return;}
                case "link"->{message="Привязка обновлена";linkedId=reply.get("shop_id").getAsInt();linkedName="";for(var entry:shops)if(entry.getAsJsonObject().get("id").getAsInt()==linkedId)linkedName=entry.getAsJsonObject().get("name").getAsString();}
            }
        }catch(RuntimeException e){message="Некорректный ответ сайта. Повторите позже.";}
        rebuildWidgets();
    }
    private Button button(String text,int bx,int by,int w,Runnable action){
        Button b=Button.builder(Component.literal(text),v->action.run()).bounds(x+bx,y+by,w,18).build();b.active=!waiting;return addRenderableWidget(b);
    }
    private String value(String key,String fallback){return draft.has(key)?draft.get(key).getAsString():fallback;}
    private EditBox field(String label,String key,int top,int max){
        EditBox box=new EditBox(font,x+12,y+top,316,16,Component.literal(label));box.setMaxLength(max);box.setValue(value(key,""));box.setEditable(!waiting);
        box.setResponder(v->draft.addProperty(key,v));addRenderableWidget(box);return box;
    }
    private static int index(String[] values,String value){for(int i=0;i<values.length;i++)if(values[i].equals(value))return i;return 0;}
    private void create(){
        draft=new JsonObject();for(String key:new String[]{"name","specialization","tagline","about"})draft.addProperty(key,"");
        draft.addProperty("accent","#94e2d5");draft.addProperty("emblem",EMBLEMS[0]);draft.addProperty("cover",COVERS[0]);
        editor=true;appearance=false;message="";rebuildWidgets();
    }
    @Override protected void init(){
        x=(width-340)/2;y=(height-230)/2;
        if(!editor){
            int row=48;
            for(var entry:shops){JsonObject s=entry.getAsJsonObject();int id=s.get("id").getAsInt();String name=s.get("name").getAsString();
                button(font.plainSubstrByWidth("#"+id+" · "+name,216),12,row,222,()->{JsonObject b=new JsonObject();b.addProperty("id",id);request("get",b);});
                Button bind=button(id==linkedId?"Привязан":"Привязать",240,row,88,()->{JsonObject b=new JsonObject();b.addProperty("id",id);request("link",b);});bind.active&=canLink() && id!=linkedId;row+=30;
            }
            button("Создать магазин",12,172,150,this::create);
            Button unlink=button("Снять привязку",172,172,156,()->{JsonObject b=new JsonObject();b.addProperty("id",0);request("link",b);});unlink.active&=canLink() && linkedId!=0;
            Button prev=button("←",12,195,40,()->{page--;list();});prev.active&=page>1;
            Button next=button("→",60,195,40,()->{page++;list();});next.active&=page*4<total;
            button("Обновить",110,195,100,this::list);button("Назад",220,195,108,this::onClose);
        }else{
            button("Основное",12,28,152,()->{appearance=false;rebuildWidgets();});button("Оформление",176,28,152,()->{appearance=true;rebuildWidgets();});
            if(!appearance){
                field("Название","name",61,48);field("Специализация","specialization",94,80);field("Девиз","tagline",127,120);
                var about=new MultiLineEditBox(font,x+12,y+160,316,24,Component.literal("Что продаёте и как добраться"),Component.literal("О магазине"));
                about.setCharacterLimit(3000);about.setValue(value("about",""));about.setValueListener(v->draft.addProperty("about",v));addRenderableWidget(about);
            }else{
                field("Цвет #RRGGBB","accent",61,7);
                int emblem=index(EMBLEMS,value("emblem",EMBLEMS[0])),cover=index(COVERS,value("cover",COVERS[0]));
                button("Эмблема: "+EMBLEM_NAMES[emblem],12,94,316,()->{draft.addProperty("emblem",EMBLEMS[(emblem+1)%EMBLEMS.length]);rebuildWidgets();});
                button("Обложка: "+COVER_NAMES[cover],12,127,316,()->{draft.addProperty("cover",COVERS[(cover+1)%COVERS.length]);rebuildWidgets();});
            }
            save=button("Отправить на проверку",12,198,208,()->request("save",draft.deepCopy()));
            button("К списку",230,198,98,()->{editor=false;message="";rebuildWidgets();});
        }
        if(!started){started=true;list();}
    }
    @Override public void tick(){
        if(minecraft.player==null || minecraft.player.containerMenu.containerId!=containerId){minecraft.setScreen(null);return;}
        if(editor && save!=null)save.active=!waiting && value("name","").trim().length()>=3 && value("specialization","").trim().length()>=3 && value("accent","").matches("#[a-fA-F0-9]{6}");
    }
    private static String status(JsonObject shop){
        if(shop.get("pending").getAsBoolean() && shop.get("status").getAsString().equals("approved"))return "Изменения на проверке";
        return switch(shop.get("status").getAsString()){case "approved"->"Зарегистрирован";case "rejected"->"Нужны исправления";case "suspended"->"Приостановлен";default->"На проверке";};
    }
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        renderBackground(g,mx,my,dt);g.fill(x,y,x+340,y+230,0xF0101018);g.drawString(font,editor?"Заявка на магазин":"Мои магазины",x+12,y+11,0xE7D9FF,false);
        if(editor){
            g.drawString(font,appearance?"Цвет акцента #RRGGBB":"Название",x+12,y+50,0xCCCCCC,false);
            if(!appearance){g.drawString(font,"Специализация",x+12,y+83,0xCCCCCC,false);g.drawString(font,"Девиз",x+12,y+116,0xCCCCCC,false);g.drawString(font,"О магазине",x+12,y+149,0xCCCCCC,false);}
            else g.drawWordWrap(font,Component.literal("Оформление и текст проверяются модератором. Прилавки продолжают торговать во время проверки."),x+12,y+158,312,0xAAAAAA);
        }else{
            g.drawString(font,font.plainSubstrByWidth(canLink()?(linkedId==0?"Выберите магазин; название — изменить заявку":"Прилавок: "+linkedName):"Откройте настроенный прилавок для привязки",316),x+12,y+30,0xAAAAAA,false);
            int row=69;for(var entry:shops){g.drawString(font,status(entry.getAsJsonObject()),x+14,y+row,0x94E2D5,false);row+=30;}
            if(shops.isEmpty())g.drawString(font,waiting?"Загрузка…":"Магазинов пока нет",x+12,y+70,0xAAAAAA,false);
        }
        for(var widget:renderables)widget.render(g,mx,my,dt);
        String text=waiting?"Обработка…":message;g.drawString(font,font.plainSubstrByWidth(text,316),x+12,y+219,0x94E2D5,false);
        if(!text.isEmpty() && mx>=x && mx<x+340 && my>=y+217 && my<y+230)g.renderTooltip(font,Component.literal(text),mx,my);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
