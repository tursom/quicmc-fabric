# quicmc

支持 Minecraft 客户端通过 QUIC 协议连接到服务器的 mod。

目前仅支持客户端，服务端需要使用 [mc 网关](https://github.com/tursom/mc-gateway) 转发 QUIC 流量。

要使用该 mod，在服务器信息页面的服务器地址前增加 quic://（例：quic://example.mc:666），该 mod 就会接管底层的传输层实现，改用 QUIC 协议连接到服务器。
