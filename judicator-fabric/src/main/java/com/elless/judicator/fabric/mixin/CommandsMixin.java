package com.elless.judicator.fabric.mixin;

import com.mojang.brigadier.ParseResults;
import com.elless.judicator.fabric.JudicatorFabric;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every command before it runs (Fabric API has no event for it): a muted player's blocked commands, and the commands a
 * staff member types while asked for evidence.
 */
@Mixin(Commands.class)
public abstract class CommandsMixin {

    @Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
    private void judicator$beforeCommand(ParseResults<CommandSourceStack> parse, String command, CallbackInfo callback) {
        if (!JudicatorFabric.allowCommand(parse.getContext().getSource(), command)) callback.cancel();
    }
}
