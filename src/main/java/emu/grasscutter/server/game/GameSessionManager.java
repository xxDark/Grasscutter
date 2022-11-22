package emu.grasscutter.server.game;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

import emu.grasscutter.Grasscutter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.DefaultEventLoop;
import kcp.highway.KcpListener;
import kcp.highway.Ukcp;

public class GameSessionManager {
    private static final DefaultEventLoop logicThread = new DefaultEventLoop();
    private static final ConcurrentHashMap<Ukcp,GameSession> sessions = new ConcurrentHashMap<>();
    private static final KcpListener listener = new KcpListener(){
        @Override
        public void onConnected(Ukcp ukcp) {
            int times = 0;
            GameServer server = Grasscutter.getGameServer();
            while (server==null){//Waiting server to establish
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    ukcp.close();
                    return;
                }
                if(times++>5){
                    Grasscutter.getLogger().error("Service is not available!");
                    ukcp.close();
                    return;
                }
                server = Grasscutter.getGameServer();
            }
            GameSession conversation = new GameSession(server);
            conversation.onConnected(new KcpTunnel(){
                @Override
                public InetSocketAddress getAddress() {
                    return ukcp.user().getRemoteAddress();
                }

                @Override
                public void writeData(byte[] bytes) {
                    ByteBuf buf = Unpooled.wrappedBuffer(bytes);
                    ukcp.write(buf);
                    buf.release();
                }

                @Override
                public void writeData(ByteBuf buf) {
                    ukcp.write(buf);
                }

                @Override
                public void close() {
                    ukcp.close();
                }

                @Override
                public int getSrtt() {
                    return ukcp.srtt();
                }
            });
            sessions.put(ukcp,conversation);
        }

        @Override
        public void handleReceive(ByteBuf buf, Ukcp kcp) {
            GameSession conversation = sessions.get(kcp);
            if (conversation != null) {
                ByteBuf copy = PooledByteBufAllocator.DEFAULT.heapBuffer(buf.readableBytes());
                buf.getBytes(0, copy);
                logicThread.execute(() -> {
                    try {
                        conversation.handleReceive(copy);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        copy.release();
                    }
                });
            }
        }

        @Override
        public void handleException(Throwable ex, Ukcp ukcp) {

        }

        @Override
        public void handleClose(Ukcp ukcp) {
            GameSession conversation = sessions.get(ukcp);
            if(conversation!=null) {
                conversation.handleClose();
                sessions.remove(ukcp);
            }
        }
    };

    public static KcpListener getListener() {
        return listener;
    }

    interface KcpTunnel{
        InetSocketAddress getAddress();
        void writeData(byte[] bytes);
        void writeData(ByteBuf buf);
        void close();
        int getSrtt();
    }
    interface KcpChannel{
        void onConnected(KcpTunnel tunnel);
        void handleClose();
        void handleReceive(ByteBuf bytes);
    }
}
