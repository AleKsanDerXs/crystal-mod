package ru.alex.crystalmod;

import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;

/** GUI packets need the same server-side login/freeze gate as commands. */
final class StallAccess {
    private static boolean checked;
    private static Method loggedIn,locked;
    static boolean allowed(ServerPlayer player){
        if(player.isSpectator())return false;
        if(!net.neoforged.fml.ModList.get().isLoaded("authmod"))return true;
        if(!checked){checked=true;try{
            Class<?> auth=Class.forName("ru.alex.authmod.AuthMod");loggedIn=auth.getMethod("isUserLoggedIn",String.class);locked=auth.getMethod("isUserLocked",String.class);
        }catch(ReflectiveOperationException e){CrystalMod.LOGGER.error("AuthMod API unavailable; stall management is disabled",e);}}
        if(loggedIn==null || locked==null)return false;
        try{String name=player.getGameProfile().getName();return (boolean)loggedIn.invoke(null,name) && !(boolean)locked.invoke(null,name);}
        catch(ReflectiveOperationException e){return false;}
    }
}
