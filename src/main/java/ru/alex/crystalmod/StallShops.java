package ru.alex.crystalmod;

import com.google.gson.JsonObject;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

/** Event-driven catalogue snapshots; no ticker, chunk loading or background per-stall polling. */
public final class StallShops {
    private static final RateLimiter COMMANDS=new RateLimiter(8,60_000);
    private StallShops(){}
    static JsonObject snapshot(MarketStallEntity s){
        JsonObject b=new JsonObject();b.addProperty("username",s.ownerName);b.addProperty("stall_id",s.stallId.toString());
        b.addProperty("dimension",s.getLevel().dimension().location().toString());
        b.addProperty("x",s.getBlockPos().getX());b.addProperty("y",s.getBlockPos().getY());b.addProperty("z",s.getBlockPos().getZ());
        b.addProperty("registry_id",BuiltInRegistries.ITEM.getKey(s.sample.getItem()).toString());
        b.addProperty("party_size",s.partySize);b.addProperty("price",s.price);b.addProperty("stock",MarketStallMenu.stock(s));return b;
    }
    static void publish(MarketStallEntity s){
        if(s.getLevel()==null || s.getLevel().isClientSide || s.sample.isEmpty() || s.ownerName.isEmpty())return;
        long now=System.currentTimeMillis();if(now-s.lastCatalogPublish<15000)return;s.lastCatalogPublish=now;
        CrystalAPI.shopRequestAsync("stall_publish_mod",snapshot(s)).exceptionally(e->{CrystalMod.LOGGER.debug("Stall catalogue unavailable: {}",e.getMessage());return null;});
    }
    static void removed(MarketStallEntity s){
        if(s.ownerName.isEmpty())return;
        JsonObject b=new JsonObject();b.addProperty("username",s.ownerName);b.addProperty("stall_id",s.stallId.toString());
        CrystalAPI.shopRequestAsync("stall_remove_mod",b).exceptionally(e->{CrystalMod.LOGGER.debug("Stall catalogue removal pending: {}",e.getMessage());return null;});
    }
    private static void send(ServerPlayer player,String action,JsonObject body){
        if(COMMANDS.isLimited(player.getUUID())){player.sendSystemMessage(Component.translatable("crystalmod.stall.shop_limit"));return;}
        body.addProperty("username",player.getGameProfile().getName());
        CrystalAPI.shopRequestAsync(action,body).whenComplete((result,error)->player.getServer().execute(()->{
            if(error!=null){player.sendSystemMessage(Component.translatable("crystalmod.stall.shop_unavailable"));return;}
            if(!result.get("ok").getAsBoolean()){player.sendSystemMessage(Component.literal(result.has("error")?result.get("error").getAsString():"Shop request failed"));return;}
            if(result.has("items")){
                for(var entry:result.getAsJsonArray("items")){var s=entry.getAsJsonObject();player.sendSystemMessage(Component.literal("#"+s.get("id").getAsInt()+" · "+s.get("name").getAsString()+" · "+s.get("status").getAsString()));}
            }else if(result.has("id"))player.sendSystemMessage(Component.translatable("crystalmod.stall.shop_submitted",result.get("id").getAsInt()));
            else player.sendSystemMessage(Component.translatable("crystalmod.stall.shop_linked"));
        }));
    }
    static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> commands(){
        return Commands.literal("shop")
            .then(Commands.literal("list").executes(ctx->{send(ctx.getSource().getPlayerOrException(),"shop_list_mod",new JsonObject());return 1;}))
            .then(Commands.literal("apply").then(Commands.argument("name",StringArgumentType.string()).then(Commands.argument("specialization",StringArgumentType.greedyString()).executes(ctx->{
                JsonObject body=new JsonObject();body.addProperty("name",StringArgumentType.getString(ctx,"name"));body.addProperty("specialization",StringArgumentType.getString(ctx,"specialization"));
                send(ctx.getSource().getPlayerOrException(),"shop_apply_mod",body);return 1;
            }))))
            .then(Commands.literal("link").then(Commands.argument("id",IntegerArgumentType.integer(0)).executes(ctx->{
                ServerPlayer p=ctx.getSource().getPlayerOrException();
                if(!(p.pick(6,1,false) instanceof BlockHitResult hit) || !(p.level().getBlockEntity(hit.getBlockPos()) instanceof MarketStallEntity s)
                        || !p.getUUID().equals(s.owner) || s.sample.isEmpty()){
                    p.sendSystemMessage(Component.translatable("crystalmod.stall.shop_look"));return 0;
                }
                JsonObject b=snapshot(s);b.addProperty("shop_id",IntegerArgumentType.getInteger(ctx,"id"));send(p,"stall_link_mod",b);return 1;
            })));
    }
}
