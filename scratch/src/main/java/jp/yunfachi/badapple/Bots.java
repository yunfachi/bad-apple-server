package jp.yunfachi.badapple;

import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.crypto.FilterMask;
import net.minestom.server.entity.*;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.play.*;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.registry.Registries;
import net.minestom.server.utils.SlotUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.SocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static java.lang.Float.parseFloat;
import static jp.yunfachi.badapple.Server.*;
import static jp.yunfachi.badapple.Shield.defaultShields;
import static jp.yunfachi.badapple.VideoToArray.processFrames;

public class Bots {
    double x;
    double y;
    double z;
    int width;
    int height;
    float yaw;
    float pitch;
    double x_density;
    double y_density;
    int firstBotEntityId = 0;
    Instant startInstant;
    List<List<List<String>>> frames;

    Bots(double x, double y, double z, int width, int height, float yaw, float pitch, double x_density, double y_density) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.width = width;
        this.height = height;
        this.yaw = yaw;
        this.pitch = pitch;
        this.x_density = x_density;
        this.y_density = y_density;

        try {
            frames = processFrames(width, height, "/home/yunfachi/git/Minestom/frames/frame_%04d.png", 100);//6569);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public UUID generateUUID(int x, int y) {
        return UUID.nameUUIDFromBytes(new byte[x + this.width * y]);
    }

    public void forEachBot(BiConsumer<Integer, Integer> action) {
//        for (int x = 0; x < this.width; x++) {
//            for (int y = 0; y < this.height; y++) {
//                action.accept(x, y);
//            }
//        }

        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                action.accept(x, y);
            }
        }
    }

    public void join() {
        firstBotEntityId = lastEntityId.get() + 1;
        forEachBot((x, y) -> {
            UUID uuid = generateUUID(x, y);

            sendPacket(new PlayerInfoUpdatePacket(
                    PlayerInfoUpdatePacket.Action.ADD_PLAYER,
                    new PlayerInfoUpdatePacket.Entry(
                            uuid, "yunfachi_bot",
                            new ArrayList<>(), false, 0,
                            GameMode.CREATIVE, null, null
                    )
            ));
            sendPacket(new SpawnEntityPacket(
                    lastEntityId.getAndIncrement(), uuid, EntityType.PLAYER.id(),
                    new Pos(
                            this.x + (width - x) * x_density,
                            this.y + (height - y) * y_density,
                            this.z
                    ), this.yaw, 0, (short) 0, (short) 0, (short) 0
            ));
        });
    }

    public void waitUntilFrame(int fps, int frame) {
        try {
//            System.out.println((((long) 1000000000 / fps * frame) - (Instant.now().getNano() - startInstant.getNano()))%1000000000 + " - " + frame);
            Thread.sleep(Duration.ofNanos(
                    (((long) 1000000000 / fps * frame) - (Instant.now().getNano() - startInstant.getNano()))%1000000000
            ));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void play(int fps, int play_fps) {
        startInstant = Instant.now();
        forEachBot((x, y) -> {
            UUID uuid = generateUUID(x, y);

            Thread.startVirtualThread(() -> {
                for (int frame = 0; frame < frames.size(); frame++) {
                    sendPacket(new EntityEquipmentPacket(x + y * width + firstBotEntityId, Map.of(
                            EquipmentSlot.MAIN_HAND, defaultShields.get(this.frames.get(frame).get(y).get(x))
                    )));
                    waitUntilFrame(play_fps, frame);
                }
            });
        });

//        Thread.startVirtualThread(() -> {
//            for (int frame = 0; frame < frames.size(); frame++) {
//                int finalFrame = frame;
//                forEachBot((x, y) -> {
//                    UUID uuid = generateUUID(x, y);
//                    sendPacket(new EntityEquipmentPacket(x + y * width + firstBotEntityId, Map.of(
//                            EquipmentSlot.MAIN_HAND, defaultShields.get(this.frames.get(finalFrame).get(y).get(x))
//                    )));
//                });
//                waitUntilFrame(play_fps, frame);
//            }
//        });
    }
}
