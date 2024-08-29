package jp.yunfachi.badapple;

import net.kyori.adventure.text.Component;
import net.minestom.scratch.network.NetworkContext;
import net.minestom.scratch.registry.ScratchRegistryTools;
import net.minestom.scratch.world.ImmutableChunkRepeatWorld;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.ChunkRangeUtils;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.common.ClientPingRequestPacket;
import net.minestom.server.network.packet.client.configuration.ClientFinishConfigurationPacket;
import net.minestom.server.network.packet.client.login.ClientLoginAcknowledgedPacket;
import net.minestom.server.network.packet.client.login.ClientLoginStartPacket;
import net.minestom.server.network.packet.client.play.ClientChatMessagePacket;
import net.minestom.server.network.packet.client.status.StatusRequestPacket;
import net.minestom.server.network.packet.server.common.PingResponsePacket;
import net.minestom.server.network.packet.server.configuration.FinishConfigurationPacket;
import net.minestom.server.network.packet.server.login.LoginSuccessPacket;
import net.minestom.server.network.packet.server.play.*;
import net.minestom.server.network.packet.server.play.data.WorldPos;
import net.minestom.server.network.packet.server.status.ResponsePacket;
import net.minestom.server.registry.DynamicRegistry;
import net.minestom.server.world.DimensionType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static jp.yunfachi.badapple.Constants.VIEW_DISTANCE;

public class Connection {
    final SocketChannel client;
    final NetworkContext networkContext = new NetworkContext.Sync(this::write);
    boolean online = true;

    String username;
    UUID uuid;

    Connection(SocketChannel client) {
        this.client = client;
    }

    void networkLoopRead() {
        while (online) {
            this.online = networkContext.read(buffer -> {
                try { return client.read(buffer); }
                catch (IOException e) { return -1; }
            }, this::handlePacket);
        }
    }

    boolean write(ByteBuffer buffer) {
        try {
            final int length = client.write(buffer.flip());
            if (length == -1) online = false;
        } catch (IOException e) {
            online = false;
        }
        return online;
    }

    void handlePacket(ClientPacket packet) {
        if (packet instanceof ClientFinishConfigurationPacket) {
            init();
            this.networkContext.flush();
            return;
        }
        if (networkContext.state() == ConnectionState.PLAY) {
            processPacket(packet);
            this.networkContext.flush();
            return;
        }
        switch (packet) {
            case StatusRequestPacket ignored -> {
                this.networkContext.write(new ResponsePacket("""
                            {
                                "version": {
                                    "name": "%s",
                                    "protocol": %s
                                },
                                "players": {
                                    "max": 69,
                                    "online": -1
                                },
                                "description": {
                                    "text": "Yunfachi's Bad Apple"
                                },
                                "enforcesSecureChat": false,
                                "previewsChat": false
                            }
                            """.formatted(MinecraftServer.VERSION_NAME, MinecraftServer.PROTOCOL_VERSION)));
            }
            case ClientPingRequestPacket pingRequestPacket -> {
                this.networkContext.write(new PingResponsePacket(pingRequestPacket.number()));
            }
            case ClientLoginStartPacket startPacket -> {
                username = startPacket.username();
                uuid = UUID.randomUUID();
                this.networkContext.write(new LoginSuccessPacket(startPacket.profileId(), startPacket.username(), 0, false));
            }
            case ClientLoginAcknowledgedPacket ignored -> {
                this.networkContext.write(ScratchRegistryTools.REGISTRY_PACKETS);
                this.networkContext.write(new FinishConfigurationPacket());
            }
            default -> {
            }
        }
        this.networkContext.flush();
    }

    private final ImmutableChunkRepeatWorld world = new ImmutableChunkRepeatWorld(
            Objects.requireNonNull(ScratchRegistryTools.DIMENSION_REGISTRY.get(DimensionType.OVERWORLD)),
            ScratchRegistryTools.BIOME_REGISTRY,
            unit -> unit.modifier().fillHeight(0, 48, Block.STONE)
    );

    void init() {
        final Pos position = new Pos(0, 55, 0);
//        this.position = position;
//        this.oldPosition = position;

        final DimensionType dimension = world.dimensionType();
        final DynamicRegistry.Key<DimensionType> dimensionKey = ScratchRegistryTools.DIMENSION_REGISTRY.getKey(dimension);
        final int dimensionId = ScratchRegistryTools.DIMENSION_REGISTRY.getId(dimensionKey);

        this.networkContext.write(new JoinGamePacket(
                Server.lastEntityId.getAndIncrement(), false, List.of(), 0,
                VIEW_DISTANCE, VIEW_DISTANCE,
                false, true, false,
                dimensionId, dimensionKey.name(),
                0, GameMode.CREATIVE, null, false, true,
                new WorldPos(dimensionKey.name(), Vec.ZERO), 0, false)
        );
        this.networkContext.write(new SpawnPositionPacket(position, 0));
        this.networkContext.write(new PlayerPositionAndLookPacket(position, (byte) 0, 0));
        this.networkContext.write(new PlayerInfoUpdatePacket(EnumSet.of(PlayerInfoUpdatePacket.Action.ADD_PLAYER, PlayerInfoUpdatePacket.Action.UPDATE_LISTED),
                List.of(
                        new PlayerInfoUpdatePacket.Entry(uuid, username, List.of(),
                                true, 1, GameMode.CREATIVE, null, null)
                )));

        this.networkContext.write(new UpdateViewDistancePacket(VIEW_DISTANCE));
        this.networkContext.write(new UpdateViewPositionPacket(position.chunkX(), position.chunkZ()));

        ChunkRangeUtils.forChunksInRange(position.chunkX(), position.chunkZ(), VIEW_DISTANCE,
                (x, z) -> networkContext.write(world.chunkPacket(x, z)));

        this.networkContext.write(new ChangeGameStatePacket(ChangeGameStatePacket.Reason.LEVEL_CHUNKS_LOAD_START, 0f));
    }

    void processPacket(ClientPacket packet) {
        if (packet instanceof ClientChatMessagePacket chatMessagePacket) {
            Server.instance.processCommand(chatMessagePacket.message());
        }
    }
}
