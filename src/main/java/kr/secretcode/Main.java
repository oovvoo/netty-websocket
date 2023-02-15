package kr.secretcode;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GlobalEventExecutor;

public class Main {
    public static DefaultChannelGroup allChnnels;
    public static void main(String[] args) throws InterruptedException {

        allChnnels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

        Channel channel = new ServerBootstrap().channel(NioServerSocketChannel.class)
                .group(new NioEventLoopGroup(1), new NioEventLoopGroup(4))
                .handler(new LoggingHandler(LogLevel.DEBUG))
                .option(ChannelOption.SO_BACKLOG, 200)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(
                                new IdleStateHandler(10,5,0),
                                new HttpServerCodec(),
                                new HttpObjectAggregator(65535),
                                new WebSocketServerProtocolHandler("/test"),
                                new SimpleChannelInboundHandler<WebSocketFrame>() {
                                    @Override
                                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                        //IdleStatHandler에 지정된 시간동안 ReadEvent가 없다면 모든 클라이언트에게 Ping을 보냄
                                        if(evt instanceof IdleStateEvent){
                                            IdleStateEvent event = (IdleStateEvent) evt;
                                            if(event.state() == IdleState.READER_IDLE){
                                                allChnnels.writeAndFlush(new PingWebSocketFrame());
                                            }
                                        }
                                    }

                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame msg) throws Exception {
                                        if(msg instanceof TextWebSocketFrame)
                                        {
                                            TextWebSocketFrame textWebSocketFrame = (TextWebSocketFrame) msg;
                                            String textMessage = textWebSocketFrame.text();
                                            allChnnels.writeAndFlush(new TextWebSocketFrame(ctx.channel().remoteAddress()+" "+textMessage));
                                        }

                                    }

                                    @Override
                                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                        allChnnels.add(ctx.channel());
                                    }

                                    @Override
                                    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                        allChnnels.remove(ctx.channel());
                                    }

                                    @Override
                                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                        allChnnels.remove(ctx.channel());
                                    }
                                }
                        );

                    }
                })
                .bind(2097).sync().channel();


        channel.closeFuture().sync();

    }
}