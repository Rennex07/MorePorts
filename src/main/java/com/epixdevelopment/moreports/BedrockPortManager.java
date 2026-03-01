package com.epixdevelopment.moreports;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.bukkit.Bukkit;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class BedrockPortManager {

    private final MorePorts plugin;
    private final List<Channel> boundChannels = new ArrayList<>();
    private final Map<InetSocketAddress, ProxySession> sessions = new ConcurrentHashMap<>();
    private EventLoopGroup workerGroup;

    private String targetAddress;
    private int targetPort;

    public BedrockPortManager(MorePorts plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("bedrock.enabled", false)) {
            return;
        }

        targetAddress = plugin.getConfig().getString("bedrock.target-address", "127.0.0.1");
        targetPort = plugin.getConfig().getInt("bedrock.target-port", 19132);
        List<Integer> ports = plugin.getConfig().getIntegerList("bedrock.ports");

        if (ports.isEmpty()) {
            return;
        }

        if (workerGroup == null) {
            workerGroup = new NioEventLoopGroup();
        }

        for (int port : ports) {
            bindPort(port);
        }
        
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupSessions, 1200L, 1200L);
    }

    private void bindPort(int port) {
        Bootstrap b = new Bootstrap();
        b.group(workerGroup)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel ch) {
                        ch.pipeline().addLast(new InboundHandler());
                    }
                });

        try {
            Channel channel = b.bind(port).sync().channel();
            boundChannels.add(channel);
            plugin.getLogger().info("Bedrock Proxy listening on UDP port " + port + " -> " + targetAddress + ":" + targetPort);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to bind Bedrock port " + port, e);
        }
    }

    public void shutdown() {
        for (Channel ch : boundChannels) {
            try {
                ch.close().sync();
            } catch (InterruptedException e) {
                plugin.getLogger().log(Level.WARNING, "Interrupted while closing Bedrock channel", e);
            }
        }
        boundChannels.clear();
        
        for (ProxySession session : sessions.values()) {
            session.close();
        }
        sessions.clear();

        if (workerGroup != null) {
            workerGroup.shutdownGracefully().syncUninterruptibly();
            workerGroup = null;
        }
    }

    private void cleanupSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> {
            boolean expired = (now - entry.getValue().lastActivity) > 30000;
            if (expired) {
                entry.getValue().close();
            }
            return expired;
        });
    }

    private class InboundHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            InetSocketAddress sender = packet.sender();
            ProxySession session = sessions.computeIfAbsent(sender, k -> new ProxySession(ctx.channel(), sender));
            session.lastActivity = System.currentTimeMillis();
            session.forwardToGeyser(packet.content().retain());
        }
    }

    private class ProxySession {
        private final Channel clientChannel;
        private final InetSocketAddress clientAddress;
        private Channel geyserChannel;
        public volatile long lastActivity;

        public ProxySession(Channel clientChannel, InetSocketAddress clientAddress) {
            this.clientChannel = clientChannel;
            this.clientAddress = clientAddress;
            this.lastActivity = System.currentTimeMillis();
            setupGeyserConnection();
        }

        private void setupGeyserConnection() {
            Bootstrap b = new Bootstrap();
            b.group(workerGroup)
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInitializer<NioDatagramChannel>() {
                        @Override
                        protected void initChannel(NioDatagramChannel ch) {
                            ch.pipeline().addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
                                    lastActivity = System.currentTimeMillis();
                                    if (clientChannel.isActive()) {
                                        clientChannel.writeAndFlush(new DatagramPacket(packet.content().retain(), clientAddress));
                                    }
                                }
                            });
                        }
                    });

            try {
                this.geyserChannel = b.bind(0).sync().channel();
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to create backend session for " + clientAddress);
            }
        }

        public void forwardToGeyser(ByteBuf content) {
            if (geyserChannel != null && geyserChannel.isActive()) {
                geyserChannel.writeAndFlush(new DatagramPacket(content, new InetSocketAddress(targetAddress, targetPort)));
            } else {
                content.release();
            }
        }

        public void close() {
            if (geyserChannel != null) {
                geyserChannel.close();
            }
        }
    }
}
