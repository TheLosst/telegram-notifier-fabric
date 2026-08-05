package ru.afknotifier.mixin;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.afknotifier.events.DisconnectReasonHolder;

/**
 * Единственный миксин в моде: забирает причину дисконнекта.
 *
 * Callback-события Fabric API её не отдают (Disconnect передаёт только handler
 * и client), а по ТЗ причину нужно показать в сообщении.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public class ClientCommonPacketListenerImplMixin {

	@Inject(method = "onDisconnect", at = @At("HEAD"))
	private void afkNotifier$captureDisconnectReason(DisconnectionDetails details, CallbackInfo ci) {
		DisconnectReasonHolder.set(details.reason().getString());
	}
}
