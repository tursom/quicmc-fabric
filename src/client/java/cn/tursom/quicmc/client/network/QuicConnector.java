package cn.tursom.quicmc.client.network;

import cn.tursom.quicmc.client.mixin.ConnectScreenAccessor;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.incubator.codec.quic.*;
import io.netty.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.network.*;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkState;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.Optional;

public class QuicConnector extends Thread {
    private static final QuicSslContext SSL_CONTEXT = QuicSslContextBuilder.forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .applicationProtocols("minecraft", "raw", "quic") // 多个协议选项
            .build();

    private static final EventLoopGroup group = new NioEventLoopGroup();

    private final ConnectScreen connectScreen;
    private final MinecraftClient client;
    private final ServerAddress address;
    private final ServerInfo info;

    public QuicConnector(@NotNull String name, ConnectScreen connectScreen, MinecraftClient client, ServerAddress address, @Nullable ServerInfo info) {
        super(name);
        this.connectScreen = connectScreen;
        this.client = client;
        this.address = address;
        this.info = info;
    }

    @Override
    public void run() {
        ConnectScreenAccessor accessor = (ConnectScreenAccessor) connectScreen;

        InetSocketAddress inetSocketAddress = null;
        try {
            if (accessor.connectingCancelled()) {
                return;
            }

            Optional<InetSocketAddress> optional = AllowedAddressResolver.DEFAULT.resolve(address).map(Address::getInetSocketAddress);
            if (accessor.connectingCancelled()) {
                return;
            }

            if (optional.isEmpty()) {
                client.execute(() -> client.setScreen(new DisconnectedScreen(accessor.parent(), accessor.failureErrorMessage(), ConnectScreen.BLOCKED_HOST_TEXT)));
                return;
            }

            inetSocketAddress = optional.get();
            ClientConnection clientConnection;
            Future<QuicStreamChannel> streamChannelFuture;
            synchronized (accessor) {
                if (accessor.connectingCancelled()) {
                    return;
                }

                clientConnection = new ClientConnection(NetworkSide.CLIENTBOUND);

                //accessor.future(ClientConnection.connect(inetSocketAddress, client.options.shouldUseNativeTransport(), clientConnection));
                streamChannelFuture = connect(clientConnection, inetSocketAddress);
            }

            QuicStreamChannel quicChannel = streamChannelFuture.syncUninterruptibly().get();
            accessor.future(quicChannel.newSucceededFuture());
            synchronized (accessor) {
                if (accessor.connectingCancelled()) {
                    clientConnection.disconnect(ConnectScreenAccessor.ABORTED_TEXT());
                    return;
                }

                accessor.connection(clientConnection);
            }

            accessor.connection().setPacketListener(new ClientLoginNetworkHandler(accessor.connection(), client, info, accessor.parent(), false, (Duration) null, accessor::invokeSetStatus));
            accessor.connection().send(new HandshakeC2SPacket(inetSocketAddress.getHostName(), inetSocketAddress.getPort(), NetworkState.LOGIN));
            accessor.connection().send(new LoginHelloC2SPacket(client.getSession().getUsername(), Optional.ofNullable(client.getSession().getUuidOrNull())));
        } catch (Exception var9) {
            if (accessor.connectingCancelled()) {
                return;
            }

            Throwable var5 = var9.getCause();
            Exception exception3;
            if (var5 instanceof Exception exception2) {
                exception3 = exception2;
            } else {
                exception3 = var9;
            }

            ConnectScreenAccessor.LOGGER().error("Couldn't connect to server", var9);
            String string = inetSocketAddress == null ? exception3.getMessage() : exception3.getMessage().replaceAll(inetSocketAddress.getHostName() + ":" + inetSocketAddress.getPort(), "").replaceAll(inetSocketAddress.toString(), "");
            client.execute(() -> client.setScreen(new DisconnectedScreen(accessor.parent(), accessor.failureErrorMessage(), Text.translatable("disconnect.genericReason", new Object[]{string}))));
        }
    }

    @SneakyThrows
    private static Future<QuicStreamChannel> connect(ClientConnection connection, SocketAddress inetsocketaddress) {
        Bootstrap bootstrap = new Bootstrap();
        Channel channel = bootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .handler(new QuicClientCodecBuilder()
                        .sslContext(SSL_CONTEXT)
                        .initialMaxData(33554432L)
                        .initialMaxStreamDataBidirectionalLocal(16777216L)
                        .initialMaxStreamDataBidirectionalRemote(16777216L)
                        .initialMaxStreamDataUnidirectional(16777216L)
                        .initialMaxStreamsBidirectional(100L)
                        .initialMaxStreamsUnidirectional(100L)
                        .activeMigration(true)
                        .build())
                .bind(0).sync().channel();

        // 连接到服务器
        QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                .handler(new ChannelInitializer<QuicChannel>() {
                    @Override
                    protected void initChannel(QuicChannel ch) {
                        // QUIC 连接处理器
                        ch.pipeline().addLast(new QuicConnectionHandler());
                    }
                })
                .streamHandler(new QuicStreamInitializer(connection))
                .remoteAddress(inetsocketaddress)
                .connect()
                .get();

        // 创建流并发送数据
        return quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, new QuicStreamInitializer(connection));
    }

    @Slf4j
    @RequiredArgsConstructor
    private static class QuicStreamInitializer extends ChannelInitializer<QuicStreamChannel> {
        private final ClientConnection connection;

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.read();
        }

        @Override
        protected void initChannel(QuicStreamChannel ch) {
            ChannelPipeline channelPipeline = ch.pipeline().addLast("timeout", new ReadTimeoutHandler(30));
            ClientConnection.addHandlers(channelPipeline, NetworkSide.CLIENTBOUND);
            channelPipeline.addLast("packet_handler", connection);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            super.channelRead(ctx, msg);
        }
    }

    private static class QuicConnectionHandler extends ChannelInboundHandlerAdapter {
    }
}
