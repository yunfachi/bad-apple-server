package jp.yunfachi.badapple;

import net.kyori.adventure.text.Component;
import net.minestom.scratch.network.NetworkContext;
import net.minestom.server.MinecraftServer;
import net.minestom.server.color.DyeColor;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.instance.block.banner.BannerPattern;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.PlayerChatMessagePacket;
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import net.minestom.server.network.packet.server.play.SystemChatPacket;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static jp.yunfachi.badapple.Constants.ADDRESS;
import static jp.yunfachi.badapple.Shield.createShield;
import static jp.yunfachi.badapple.Shield.lr;

public class Server {
    public static void main(String[] args) throws Exception {
        new Server();
    }

    static Server instance;
    private final ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.INET);

    private final ReentrantLock stopLock = new ReentrantLock();
    private final Condition stopCondition = stopLock.newCondition();
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private static List<Connection> connections = new ArrayList<>();
    static final AtomicInteger lastEntityId = new AtomicInteger();
    private Bots bots;

    Server() throws Exception {
        instance = this;

        server.bind(ADDRESS);
        System.out.println("Server started on: " + ADDRESS);

        MinecraftServer.init();
//        System.out.println(createShield(DyeColor.BLACK, lr(DyeColor.WHITE, BannerPattern.CREEPER)));

        startVirtualThread(this::listenCommands);
        startVirtualThread(this::listenConnections);
        stopLock.lock();
        try { stopCondition.await(); }
        finally { stopLock.unlock(); }

        server.close();
        System.out.println("Server stopped");
    }

    Thread startVirtualThread(Runnable task) {
        return Thread.startVirtualThread(task);
    }

    void listenCommands() {
        Scanner scanner = new Scanner(System.in);
        while (serverRunning()) {
            final String line = scanner.nextLine();
            processCommand(line);
        }
    }

    void processCommand(String command) {
        if (command.equals("stop")) {
            stop();
            System.out.println("Stopping server...");
        } else if (command.equals("gc")) {
            System.gc();
        } else if (command.equals("nagarete")) {
            System.out.println("Nagarete ku toki no naka de demo");
            sendPacket(new SystemChatPacket(Component.text("Nagarete ku toki no naka de demo"), false));

            bots = new Bots(0, 55, 0, 69, 36, 155, 0, 0.65, 1.1);
            this.bots.join();
        } else if (command.equals("toki") && this.bots != null) {
            System.out.println("Toki no sukima ni nagasare tsuzukete");
            sendPacket(new SystemChatPacket(Component.text("Toki no sukima ni nagasare tsuzukete"), false));

            this.bots.play(20, 20);
        }
    }

    static void sendPacket(NetworkContext.Packet packet) {
        connections.forEach(connection -> {
            connection.networkContext.write(packet);
        });
    }
    static void sendPacket(ServerPacket packet) {
        connections.forEach(connection -> {
            connection.networkContext.write(packet);
        });
    }

    void listenConnections() {
        while (serverRunning()) {
            try {
                final SocketChannel client = server.accept();
                System.out.println("Accepted connection from " + client.getRemoteAddress());
                Connection connection = new Connection(client);
                connections.add(connection);
                Thread.startVirtualThread(connection::networkLoopRead);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    void stop() {
        stopLock.lock();
        try { stopCondition.signal(); }
        finally { stopLock.unlock(); }
        stopped.set(true);
    }

    boolean serverRunning() {
        return !stopped.get();
    }
}
