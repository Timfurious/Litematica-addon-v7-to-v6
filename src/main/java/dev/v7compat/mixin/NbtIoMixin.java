package dev.v7compat.mixin;

import dev.v7compat.V7Compat;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;

/**
 * Litematica lit les .litematic avec NbtIo.readCompressed(InputStream).
 * On intercepte le résultat et, si c'est une schématique de version trop récente,
 * on la convertit directement dans le compound renvoyé (rien n'est écrit sur le disque).
 */
@Mixin(NbtIo.class)
public abstract class NbtIoMixin {

    @Inject(
            method = "readCompressed(Ljava/io/InputStream;)Lnet/minecraft/nbt/NbtCompound;",
            at = @At("RETURN")
    )
    private static void litematicaV7Compat$convert(InputStream stream, CallbackInfoReturnable<NbtCompound> cir) {
        V7Compat.apply(cir.getReturnValue());
    }
}
