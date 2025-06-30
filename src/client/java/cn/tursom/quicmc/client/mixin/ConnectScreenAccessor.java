package cn.tursom.quicmc.client.mixin;

import io.netty.channel.ChannelFuture;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.ClientConnection;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ConnectScreen.class)
public interface ConnectScreenAccessor {
    @Accessor("LOGGER")
    static Logger LOGGER() {
        throw new AssertionError();
    }

    @Accessor("ABORTED_TEXT")
    static Text ABORTED_TEXT() {
        throw new AssertionError();
    }

    @Accessor("connectingCancelled")
    boolean connectingCancelled();

    @Accessor("parent")
    Screen parent();

    @Accessor("failureErrorMessage")
    Text failureErrorMessage();

    @Accessor("future")
    void future(ChannelFuture channelFuture);

    @Accessor("connection")
    ClientConnection connection();

    @Accessor("connection")
    void connection(ClientConnection connection);

    @Invoker("setStatus")
    void invokeSetStatus(Text status);
}
