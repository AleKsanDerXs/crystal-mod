package ru.alex.crystalmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.alex.crystalmod.client.MarketStallScreen;
import ru.alex.crystalmod.client.ShopScreen;

/** Drives the real item picker, shop editor, linking and unlinking through GUI packets. */
final class StallGuiCheck {
    private static int stage,ticks,shopId,captureAfter;
    private static java.util.UUID stallId;
    private static boolean scheduled;
    private static volatile boolean done;
    private static final String NAME="GUI Артель "+System.currentTimeMillis();
    private static void click(Minecraft mc,String label){
        for(var c:mc.screen.children())if(c instanceof Button b && b.getMessage().getString().equals(label)){if(!b.active)throw new AssertionError("Inactive "+label);b.onPress();return;}
        throw new AssertionError("Missing GUI button "+label);
    }
    private static void field(Minecraft mc,String label,String text){
        for(var c:mc.screen.children())if(c instanceof EditBox b && b.getMessage().getString().equals(label)){b.setValue(text);return;}
        throw new AssertionError("Missing GUI field "+label);
    }
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),m->{});}
    static boolean tick(Minecraft mc,BlockPos pos){
        if(done)return true;
        try{
            if(++ticks>1000)throw new AssertionError("Shop GUI timed out at "+stage);
            if(stage==0 && !scheduled){scheduled=true;mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.getInventory().setItem(0,new ItemStack(Items.COPPER_INGOT,64));p.getInventory().setItem(1,new ItemStack(Items.IRON_INGOT,64));p.inventoryMenu.broadcastChanges();
                MarketStallMenu.open(p,pos);
            });stage=1;}
            else if(stage==1 && mc.screen instanceof MarketStallScreen && mc.player.containerMenu instanceof MarketStallMenu m && m.data.get(3)==1){click(mc,Component.translatable("crystalmod.stall.choose_item").getString());stage=2;}
            else if(stage==2 && mc.screen.getClass().getSimpleName().equals("StallItemScreen")){
                shot(mc,"market-gui-item-picker");for(var c:mc.screen.children())if(c instanceof Button b){b.onPress();break;}stage=3;
            }else if(stage==3 && mc.screen instanceof MarketStallScreen && mc.player.containerMenu instanceof MarketStallMenu m && m.data.get(3)==1){if(!m.offeredSample.is(Items.COPPER_INGOT))throw new AssertionError("Client menu retained old sample after selection");click(mc,Component.translatable("crystalmod.stall.shops").getString());stage=4;}
            else if(stage==4 && mc.screen instanceof ShopScreen s && !s.pending()){click(mc,"Создать магазин");stage=5;}
            else if(stage==5 && mc.screen instanceof ShopScreen s && s.editing()){
                field(mc,"Название",NAME);field(mc,"Специализация","Металлы и детали Create");field(mc,"Девиз","Автоматическая поставка");
                for(var c:mc.screen.children())if(c instanceof MultiLineEditBox b)b.setValue("Лавка рядом со станцией. Доставка металлов и деталей.");
                captureAfter=ticks+6;stage=50;
            }else if(stage==50 && ticks>=captureAfter){shot(mc,"market-gui-registration");click(mc,"Оформление");captureAfter=0;stage=6;}else if(stage==6 && mc.screen instanceof ShopScreen){field(mc,"Цвет #RRGGBB","#aabbcc");click(mc,"Эмблема: Лавка");click(mc,"Обложка: Сияние");stage=7;}
            else if(stage==7 && mc.screen instanceof ShopScreen){shot(mc,"market-gui-appearance");click(mc,"Отправить на проверку");stage=8;}
            else if(stage==8 && mc.screen instanceof ShopScreen s && !s.pending() && !s.editing()){
                if(!s.feedback().contains("отправлена"))throw new AssertionError(s.feedback());
                if(captureAfter==0){captureAfter=ticks+6;return false;}if(ticks<captureAfter)return false;
                for(var c:mc.screen.children())if(c instanceof Button b && b.getMessage().getString().startsWith("#")){
                    shopId=Integer.parseInt(b.getMessage().getString().split(" · ")[0].substring(1));break;
                }
                if(shopId<=0)throw new AssertionError("New GUI shop missing");shot(mc,"market-gui-shop-list");click(mc,"Привязать");stage=9;
            }else if(stage==9 && mc.screen instanceof ShopScreen s && !s.pending()){
                if(!s.feedback().contains("Привязка обновлена"))throw new AssertionError(s.feedback());
                for(var c:mc.screen.children())if(c instanceof Button b && b.getMessage().getString().startsWith("#"+shopId+" ·")){b.onPress();break;}stage=10;
            }else if(stage==10 && mc.screen instanceof ShopScreen s && !s.pending() && s.editing()){
                boolean name=false;for(var c:mc.screen.children())if(c instanceof EditBox b && b.getMessage().getString().equals("Название"))name=b.getValue().equals(NAME);
                if(!name)throw new AssertionError("Shop editor did not load saved profile");field(mc,"Девиз","Изменено через игровое окно");click(mc,"Отправить на проверку");stage=11;
            }else if(stage==11 && mc.screen instanceof ShopScreen s && !s.pending() && !s.editing()){click(mc,"Снять привязку");stage=12;}
            else if(stage==12 && mc.screen instanceof ShopScreen s && !s.pending()){
                if(!s.feedback().contains("Привязка обновлена"))throw new AssertionError(s.feedback());click(mc,"Назад");stage=13;
            }else if(stage==13 && mc.screen instanceof MarketStallScreen){click(mc,Component.translatable("crystalmod.stall.choose_item").getString());stage=14;}
            else if(stage==14 && mc.screen.getClass().getSimpleName().equals("StallItemScreen")){
                var buttons=mc.screen.children().stream().filter(c->c instanceof Button).map(c->(Button)c).toList();buttons.get(1).onPress();stage=15;
            }
            else if(stage==15 && mc.screen instanceof MarketStallScreen && mc.player.containerMenu instanceof MarketStallMenu menu){
                if(!menu.offeredSample.is(Items.IRON_INGOT))throw new AssertionError("Client menu did not update to second selected sample");
                mc.screen.onClose();stage=16;mc.getSingleplayerServer().execute(()->{
                try{
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var stall=(MarketStallEntity)p.level().getBlockEntity(pos);
                    if(p.getInventory().getItem(0).getCount()!=64 || !p.getInventory().getItem(0).is(Items.COPPER_INGOT) || p.getInventory().getItem(1).getCount()!=64 || !stall.sample.is(Items.IRON_INGOT))throw new AssertionError("Ghost picker consumed goods");
                    stallId=stall.stallId;CrystalMod.LOGGER.info("STALLGUI SHOP: PASS shop={} name={} stall={}",shopId,NAME,stallId);
                    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(CrystalItems.SHOP_LEDGER.get()));
                    CrystalItems.SHOP_LEDGER.get().use(p.level(),p,net.minecraft.world.InteractionHand.MAIN_HAND);
                    stage=17;
                }catch(Throwable e){CrystalMod.LOGGER.error("STALLGUI RESULT: FAIL",e);mc.execute(mc::stop);}
            });}
            else if(stage==17 && mc.screen instanceof ru.alex.crystalmod.client.ShopLedgerScreen){
                for(var c:mc.screen.children())if(c instanceof Button b && b.getMessage().getString().contains(NAME)){b.onPress();stage=18;break;}
            }else if(stage==18 && mc.screen instanceof ru.alex.crystalmod.client.ShopLedgerScreen){
                for(var c:mc.screen.children())if(c instanceof Button b && b.getMessage().getString().equals("К магазинам")){
                    shot(mc,"market-gui-ledger");mc.screen.onClose();CrystalMod.LOGGER.info("STALLGUI RESULT: PASS shop={} name={} stall={}",shopId,NAME,stallId);done=true;break;
                }
            }
        }catch(Throwable e){CrystalMod.LOGGER.error("STALLGUI RESULT: FAIL",e);mc.stop();}
        return done;
    }
}
