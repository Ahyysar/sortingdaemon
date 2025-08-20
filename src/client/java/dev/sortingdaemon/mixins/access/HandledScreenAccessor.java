package dev.sortingdaemon.mixins.access;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x") int getX();                 // имена как в твоём проекте (у тебя компилилось с x/y)
    @Accessor("y") int getY();
    @Accessor("handler") ScreenHandler getHandler();
}
