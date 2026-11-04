package ru.alex.crystalmod;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.StringReader;
import java.io.IOException;
import java.math.BigDecimal;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import ru.alex.crystalmod.net.ShopRequestPayload;
import ru.alex.crystalmod.net.ShopResponsePayload;

/** Event-driven catalogue snapshots and validated GUI actions; no per-block timer. */
public final class StallShops {
    private static final RateLimiter COMMANDS=new RateLimiter(30,60_000);
    private StallShops(){}
    static JsonObject snapshot(MarketStallEntity s){
        JsonObject b=new JsonObject();b.addProperty("username",s.ownerName);b.addProperty("stall_id",s.stallId.toString());
        b.addProperty("dimension",s.getLevel().dimension().location().toString());
        b.addProperty("x",s.getBlockPos().getX());b.addProperty("y",s.getBlockPos().getY());b.addProperty("z",s.getBlockPos().getZ());
        b.addProperty("registry_id",BuiltInRegistries.ITEM.getKey(s.sample.getItem()).toString());
        b.addProperty("party_size",s.partySize);b.addProperty("price",s.price);b.addProperty("stock",MarketStallMenu.stock(s));
        s.catalogRevision=Math.max(s.catalogRevision+1,System.currentTimeMillis());s.setChanged();b.addProperty("catalog_revision",s.catalogRevision);return b;
    }
    static void publish(MarketStallEntity s){publish(s,false);}
    static void publish(MarketStallEntity s,boolean force){
        if(s.getLevel()==null || s.getLevel().isClientSide || s.sample.isEmpty() || s.ownerName.isEmpty())return;
        long now=System.currentTimeMillis();if(!force && now-s.lastCatalogPublish<15000)return;s.lastCatalogPublish=now;
        catalogue("stall_publish_mod",snapshot(s));
    }
    static void removed(MarketStallEntity s){if(!s.ownerName.isEmpty())catalogue("stall_remove_mod",snapshot(s));}
    private static void catalogue(String action,JsonObject body){
        CrystalAPI.shopRequestAsync(action,body).whenComplete((r,e)->{
            if(e!=null || !r.get("ok").getAsBoolean())CrystalMod.LOGGER.debug("Stall catalogue update unavailable ({})",action);
        });
    }
    public static void open(ServerPlayer p){
        if(StallAccess.allowed(p))PacketDistributor.sendToPlayer(p,new ShopResponsePayload(p.containerMenu.containerId,0,"open","{}"));
    }
    private static boolean limited(ServerPlayer p){
        if(!COMMANDS.isLimited(p.getUUID()))return false;p.sendSystemMessage(Component.translatable("crystalmod.stall.shop_limit"));return true;
    }
    private static MarketStallEntity owned(ServerPlayer p){
        return p.containerMenu instanceof MarketStallMenu m && m.stillValid(p) && m.stall()!=null && p.getUUID().equals(m.stall().owner)?m.stall():null;
    }
    private static int integer(JsonObject body,String key,int fallback){
        if(!body.has(key))return fallback;
        if(!body.get(key).isJsonPrimitive() || !body.getAsJsonPrimitive(key).isNumber())throw new IllegalArgumentException();
        int value=body.get(key).getAsBigDecimal().intValueExact();if(value<0)throw new IllegalArgumentException();return value;
    }
    private static JsonObject input(String json){
        // GUI forms are flat: reject nested client data without recursive JSON parsing.
        if(json.length()>8192)throw new IllegalArgumentException();
        try(var reader=new JsonReader(new StringReader(json))){
            reader.setLenient(false);reader.beginObject();JsonObject body=new JsonObject();
            while(reader.hasNext()){
                String name=reader.nextName();JsonToken token=reader.peek();
                if(token==JsonToken.STRING)body.addProperty(name,reader.nextString());
                else if(token==JsonToken.NUMBER)body.addProperty(name,new BigDecimal(reader.nextString()));
                else throw new IllegalArgumentException();
            }
            reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException();return body;
        }catch(IOException e){throw new IllegalArgumentException(e);}
    }
    public static void handle(ServerPlayer p,ShopRequestPayload request){
        if(!StallAccess.allowed(p) || p.containerMenu.containerId!=request.containerId())return;
        if(limited(p)){JsonObject error=new JsonObject();error.addProperty("ok",false);error.addProperty("error","Слишком часто. Подождите минуту.");respond(p,request,error);return;}
        try{
            JsonObject input=input(request.json()),body=new JsonObject();String action;
            switch(request.action()){
                case "list"->{action="shop_list_mod";body.addProperty("page",Math.max(1,integer(input,"page",1)));body.addProperty("per_page",4);var stall=owned(p);if(stall!=null)body.addProperty("stall_id",stall.stallId.toString());}
                case "get"->{action="shop_get_mod";body.addProperty("id",integer(input,"id",0));}
                case "save"->{
                    action="shop_apply_mod";if(input.has("id"))body.addProperty("id",integer(input,"id",0));
                    for(String key:new String[]{"name","specialization","tagline","about","accent","emblem","cover"}){
                        if(!input.has(key) || !input.get(key).isJsonPrimitive() || !input.getAsJsonPrimitive(key).isString())throw new IllegalArgumentException();
                        body.addProperty(key,input.get(key).getAsString());
                    }
                }
                case "link"->{
                    var s=owned(p);if(s==null || s.sample.isEmpty())throw new IllegalArgumentException();
                    action="stall_link_mod";body=snapshot(s);body.addProperty("shop_id",integer(input,"id",0));
                }
                case "select"->{
                    if(!(p.containerMenu instanceof MarketStallMenu m) || !m.selectItem(p,integer(input,"slot",99)))throw new IllegalArgumentException();
                    return;
                }
                default->throw new IllegalArgumentException();
            }
            body.addProperty("username",p.getGameProfile().getName());
            CrystalAPI.shopRequestAsync(action,body).whenComplete((result,error)->{
                var server=p.getServer();if(server.isStopped())return;
                server.execute(()->{
                    if(server.getPlayerList().getPlayer(p.getUUID())!=p || p.containerMenu.containerId!=request.containerId() || !StallAccess.allowed(p))return;
                    JsonObject reply=result;
                    if(error!=null){reply=new JsonObject();reply.addProperty("ok",false);reply.addProperty("error","Сайт временно недоступен или очередь занята. Повторите позже.");}
                    respond(p,request,reply);
                });
            });
        }catch(RuntimeException e){JsonObject error=new JsonObject();error.addProperty("ok",false);error.addProperty("error","Действие недоступно. Откройте свой прилавок и повторите.");respond(p,request,error);}
    }
    private static void respond(ServerPlayer p,ShopRequestPayload request,JsonObject reply){PacketDistributor.sendToPlayer(p,new ShopResponsePayload(request.containerId(),request.requestId(),request.action(),reply.toString()));}
    private static void send(ServerPlayer player,String action,JsonObject body){
        if(!StallAccess.allowed(player) || limited(player))return;
        body.addProperty("username",player.getGameProfile().getName());
        CrystalAPI.shopRequestAsync(action,body).whenComplete((result,error)->{
            if(player.getServer().isStopped())return;
            player.getServer().execute(()->{
                if(error!=null){player.sendSystemMessage(Component.translatable("crystalmod.stall.shop_unavailable"));return;}
                if(!result.get("ok").getAsBoolean()){player.sendSystemMessage(Component.literal(result.get("error").getAsString()));return;}
                if(result.has("items"))for(var entry:result.getAsJsonArray("items")){var s=entry.getAsJsonObject();player.sendSystemMessage(Component.literal("#"+s.get("id").getAsInt()+" · "+s.get("name").getAsString()+" · "+s.get("status").getAsString()));}
                else if(result.has("id"))player.sendSystemMessage(Component.translatable("crystalmod.stall.shop_submitted",result.get("id").getAsInt()));
                else player.sendSystemMessage(Component.translatable("crystalmod.stall.shop_linked"));
            });
        });
    }
    static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> commands(){
        return Commands.literal("shop").executes(ctx->{open(ctx.getSource().getPlayerOrException());return 1;})
            .then(Commands.literal("list").executes(ctx->{send(ctx.getSource().getPlayerOrException(),"shop_list_mod",new JsonObject());return 1;}))
            .then(Commands.literal("apply").then(Commands.argument("name",StringArgumentType.string()).then(Commands.argument("specialization",StringArgumentType.greedyString()).executes(ctx->{
                JsonObject body=new JsonObject();body.addProperty("name",StringArgumentType.getString(ctx,"name"));body.addProperty("specialization",StringArgumentType.getString(ctx,"specialization"));send(ctx.getSource().getPlayerOrException(),"shop_apply_mod",body);return 1;
            }))))
            .then(Commands.literal("link").then(Commands.argument("id",IntegerArgumentType.integer(0)).executes(ctx->{
                ServerPlayer p=ctx.getSource().getPlayerOrException();
                if(!(p.pick(6,1,false) instanceof BlockHitResult hit) || !(p.level().getBlockEntity(hit.getBlockPos()) instanceof MarketStallEntity s) || !p.getUUID().equals(s.owner) || s.sample.isEmpty()){
                    p.sendSystemMessage(Component.translatable("crystalmod.stall.shop_look"));return 0;
                }
                JsonObject b=snapshot(s);b.addProperty("shop_id",IntegerArgumentType.getInteger(ctx,"id"));send(p,"stall_link_mod",b);return 1;
            })));
    }
}
