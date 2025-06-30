package cn.tursom.quicmc.client.mixin;

import cn.tursom.quicmc.client.network.QuicConnector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.logging.UncaughtExceptionLogger;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {
    @Final
    @Shadow
    static Logger LOGGER;

    @Final
    @Shadow
    private static AtomicInteger CONNECTOR_THREADS_COUNT;

    @Inject(at = @At("HEAD"),
            method = "connect(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/network/ServerAddress;Lnet/minecraft/client/network/ServerInfo;)V",
            cancellable = true)
    private void connect(MinecraftClient client, ServerAddress address, @Nullable ServerInfo info, CallbackInfo ci) {
        if (!info.address.startsWith("quic://")) {
            return;
        }

        Thread thread = new QuicConnector(
                "Server Connector #" + CONNECTOR_THREADS_COUNT.incrementAndGet(),
                (ConnectScreen) (Object) this,
                client, address, info);
        thread.setUncaughtExceptionHandler(new UncaughtExceptionLogger(LOGGER));
        thread.start();
        ci.cancel();
    }
}
