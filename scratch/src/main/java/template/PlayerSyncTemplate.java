package template;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.scratch.interest.Broadcast;
import net.minestom.scratch.inventory.InventoryHolder;
import net.minestom.scratch.inventory.ScratchInventoryUtils;
import net.minestom.scratch.listener.ScratchFeature;
import net.minestom.scratch.network.NetworkContext;
import net.minestom.scratch.registry.ScratchRegistryTools;
import net.minestom.scratch.world.PaletteWorld;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.collision.Aerodynamics;
import net.minestom.server.collision.PhysicsResult;
import net.minestom.server.collision.PhysicsUtils;
import net.minestom.server.color.DyeColor;
import net.minestom.server.coordinate.ChunkRangeUtils;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.instance.WorldBorder;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.banner.BannerPattern;
import net.minestom.server.item.ItemComponent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.BannerPatterns;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.common.ClientPingRequestPacket;
import net.minestom.server.network.packet.client.configuration.ClientFinishConfigurationPacket;
import net.minestom.server.network.packet.client.login.ClientLoginAcknowledgedPacket;
import net.minestom.server.network.packet.client.login.ClientLoginStartPacket;
import net.minestom.server.network.packet.client.status.StatusRequestPacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.common.KeepAlivePacket;
import net.minestom.server.network.packet.server.common.PingResponsePacket;
import net.minestom.server.network.packet.server.configuration.FinishConfigurationPacket;
import net.minestom.server.network.packet.server.login.LoginSuccessPacket;
import net.minestom.server.network.packet.server.play.*;
import net.minestom.server.network.packet.server.play.data.WorldPos;
import net.minestom.server.network.packet.server.status.ResponsePacket;
import net.minestom.server.registry.DynamicRegistry;
import net.minestom.server.world.DimensionType;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Integer.parseInt;

import net.minestom.server.entity.*;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.PlayerInfoRemovePacket;
import net.minestom.server.network.packet.server.play.PlayerInfoUpdatePacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;

/**
 * Sync server where players can see each other interacting with the world.
 */
public final class PlayerSyncTemplate {
    private static final int BOT_WIDTH = 5;
    private static final int BOT_HEIGHT = 5;

    private static final SocketAddress ADDRESS = new InetSocketAddress("0.0.0.0", 25545);
    private static final int VIEW_DISTANCE = 8;

    public static void main(String[] args) throws Exception {
        new PlayerSyncTemplate();
    }

    private final AtomicInteger lastEntityId = new AtomicInteger();
    private final AtomicBoolean stop = new AtomicBoolean(false);
    private final ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.INET);
    private final ConcurrentLinkedQueue<PlayerInfo> waitingPlayers = new ConcurrentLinkedQueue<>();

    private final Instance instance = new Instance(new PaletteWorld(ScratchRegistryTools.DIMENSION_REGISTRY.get(DimensionType.OVERWORLD),
            ScratchRegistryTools.BIOME_REGISTRY, unit -> unit.modifier().fillHeight(0, 48, Block.STONE)));
    private final Map<Integer, Player> players = new HashMap<>();
    private final Map<Integer, Entity> entities = new HashMap<>();

    PlayerSyncTemplate() throws Exception {
        server.bind(ADDRESS);
        System.out.println("Server started on: " + ADDRESS);
        Thread.startVirtualThread(this::listenCommands);
        Thread.startVirtualThread(this::listenConnections);
        MinecraftServer.init();
        ticks();
        server.close();
        System.out.println("Server stopped");
    }

    void listenCommands() {
        Scanner scanner = new Scanner(System.in);
        while (serverRunning()) {
            final String line = scanner.nextLine();
            if (line.equals("stop")) {
                stop.set(true);
                System.out.println("Stopping server...");
            } else if (line.equals("gc")) {
                System.gc();
            }
        }
    }

    void listenConnections() {
        while (serverRunning()) {
            try {
                final SocketChannel client = server.accept();
                System.out.println("Accepted connection from " + client.getRemoteAddress());
                Connection connection = new Connection(client);
                Thread.startVirtualThread(connection::networkLoopRead);
                Thread.startVirtualThread(connection::networkLoopWrite);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    void ticks() {
//        {
//            var entity = new Entity(EntityType.ZOMBIE, instance, new Pos(0, 60, 0));
//            entities.put(entity.id, entity);
//        }
        int keepAliveId = 0;
        while (serverRunning()) {
            final long time = System.nanoTime();
            // Connect waiting players
            PlayerInfo playerInfo;
            while ((playerInfo = waitingPlayers.poll()) != null) {
                if (playerInfo.username.startsWith("yunfachi_")) {
                    final Player player = new Player(playerInfo, instance, new Pos(
                            BOT_WIDTH - parseInt(playerInfo.username.split("_")[2]) * 0.65,
                            55 + BOT_HEIGHT - parseInt(playerInfo.username.split("_")[1]),
                            0, 165, 0)
                    );
                    this.players.put(player.id, player);
                } else {
                    final Player player = new Player(playerInfo, instance, new Pos(0, 55, 0));
                    this.players.put(player.id, player);
                }
            }
            // Tick playing players
            List<Player> toRemove = new ArrayList<>();
            final boolean sendKeepAlive = keepAliveId++ % (20 * 20) == 0;
            for (Player player : players.values()) {
                if (sendKeepAlive) player.connection.networkContext.write(new KeepAlivePacket(keepAliveId));
                if (!player.tick()) toRemove.add(player);
            }
            // Tick entities
            for (Entity entity : entities.values()) {
                entity.tick();
            }
            // Remove disconnected players
            for (Player player : toRemove) {
                this.players.remove(player.id);
                var synchronizerEntry = player.synchronizerEntry;
                if (synchronizerEntry != null) synchronizerEntry.unmake();
            }
            // Compute broadcast packets
            instance.broadcaster.process();
            {
                final long heapUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                final double elapsed = (System.nanoTime() - time) / 1_000_000.0;
                final PlayerListHeaderAndFooterPacket packet = new PlayerListHeaderAndFooterPacket(
                        Component.text("Welcome to Minestom!"),
                        Component.text("Tick: " + String.format("%.2f", elapsed) + "ms")
                                .append(Component.newline())
                                .append(Component.text("Heap: " + heapUsage / 1024 / 1024 + "MB"))
                );
                players.values().forEach(player -> player.connection.networkContext.write(packet));
            }
            // Flush all connections
            for (Player player : players.values()) {
                player.connection.networkContext.flush();
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    boolean serverRunning() {
        return !stop.get();
    }

    static final class Instance {
        final PaletteWorld blockHolder;
        final Broadcast broadcaster = new Broadcast();
        final Broadcast.World synchronizer = broadcaster.makeWorld(VIEW_DISTANCE);
        final WorldBorder worldBorder = new WorldBorder(50, 0, 0, 1, 300);

        public Instance(PaletteWorld blockHolder) {
            this.blockHolder = blockHolder;
        }
    }

    final class Connection {
        final SocketChannel client;
        final NetworkContext.Async networkContext = new NetworkContext.Async();
        final ConcurrentLinkedQueue<ClientPacket> packetQueue = new ConcurrentLinkedQueue<>();
        volatile boolean online = true;

        PlayerInfo playerInfo;

        Connection(SocketChannel client) {
            this.client = client;
        }

        void networkLoopRead() {
            while (online) {
                this.online = this.networkContext.read(buffer -> {
                    try {
                        return client.read(buffer);
                    } catch (IOException e) {
                        return -1;
                    }
                }, this::handleAsyncPacket);
            }
        }

        void networkLoopWrite() {
            while (online) {
                this.online = this.networkContext.write(buffer -> {
                    try {
                        return client.write(buffer.flip());
                    } catch (IOException e) {
                        return -1;
                    }
                });
            }
        }

        void handleAsyncPacket(ClientPacket packet) {
            if (packet instanceof ClientFinishConfigurationPacket) {
                waitingPlayers.offer(playerInfo);
                return;
            }
            if (networkContext.state() == ConnectionState.PLAY) {
                packetQueue.add(packet);
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
                                    "online": -1,
                                    "sample": [
                                            
                                            ]
                                },
                                "description": {
                                    "text": "Awesome Minestom"
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
                    // TODO: remove random UUID, currently necessary to test with multiple players
                    this.playerInfo = new PlayerInfo(this, startPacket.username(), UUID.randomUUID());
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
    }

    record PlayerInfo(Connection connection, String username, UUID uuid) {
    }

    final class Entity {
        private final int id = lastEntityId.incrementAndGet();
        private final UUID uuid = UUID.randomUUID();
        final EntityType type;
        final Instance instance;
        final Broadcast.World.Entry synchronizerEntry;

        final Aerodynamics aerodynamics;

        Pos position;
        Vec velocity = Vec.ZERO;
        boolean onGround;

        Entity(EntityType type, Instance instance, Pos position) {
            this.type = type;
            this.instance = instance;
            this.position = position;
            this.synchronizerEntry = instance.synchronizer.makeEntry(id, position,
                    () -> {
                        final var spawnPacket = new SpawnEntityPacket(
                                id, uuid, type.id(),
                                this.position, 165, 0, (short) 0, (short) 0, (short) 0
                        );
                        return List.of(spawnPacket);
                    },
                    () -> List.of(new DestroyEntitiesPacket(id)));

            this.aerodynamics = new Aerodynamics(type.registry().acceleration(), 0.91, 1 - type.registry().drag());
        }

        void tick() {
            PhysicsResult physicsResult = PhysicsUtils.simulateMovement(position, velocity, type.registry().boundingBox(),
                    instance.worldBorder, instance.blockHolder, aerodynamics, false, true, onGround, false, null);

            this.position = physicsResult.newPosition();
            this.velocity = physicsResult.newVelocity();
            this.onGround = physicsResult.isOnGround();

            synchronizerEntry.move(physicsResult.newPosition());
            synchronizerEntry.signalLocal(new EntityTeleportPacket(id, physicsResult.newPosition(), onGround));
            if (!velocity.isZero())
                synchronizerEntry.signalLocal(new EntityVelocityPacket(id, velocity.mul(8000f / ServerFlag.SERVER_TICKS_PER_SECOND)));
        }
    }

    ItemStack createShield(DyeColor BaseColor, BannerPatterns.Layer... layers) {
        return ItemStack.builder(Material.SHIELD)
                .set(ItemComponent.BANNER_PATTERNS, new BannerPatterns(List.of(layers).reversed()))
                .set(ItemComponent.BASE_COLOR, BaseColor)
                .build();
    }

    BannerPatterns.Layer lr(DyeColor color, DynamicRegistry.Key pattern) {
        return new BannerPatterns.Layer(pattern, color);
    }

    final List<ItemStack> defaultShields =
            List.of(
            createShield(DyeColor.BLACK),
            createShield(DyeColor.BLACK, lr(DyeColor.WHITE, BannerPattern.STRIPE_LEFT)),
            createShield(DyeColor.BLACK, lr(DyeColor.WHITE, BannerPattern.RHOMBUS))
    );

    final class Player {
        private final int id = lastEntityId.incrementAndGet();
        private final Connection connection;
        private final String username;
        private final UUID uuid;

        Instance instance;
        Broadcast.World.Entry synchronizerEntry;
        GameMode gameMode = GameMode.SURVIVAL;
        Pos position;
        Pos oldPosition;
        final InventoryHolder inventoryHolder = new InventoryHolder(id, this::sendPacket, play -> synchronizerEntry.signalLocal(play));
        ItemStack[] inventory = new ItemStack[46];
        ItemStack cursor = ItemStack.AIR;

        final ScratchFeature.Messaging messaging;
        final ScratchFeature.Movement movement;
        final ScratchFeature.ChunkLoading chunkLoading;
        final ScratchFeature.EntityInteract entityInteract;
        final ScratchFeature.InventoryHandling inventoryHandling;

        private void sendPacket(ServerPacket.Play packet) {
            this.connection.networkContext.write(packet);
        }

        Player(PlayerInfo info, Instance spawnInstance, Pos spawnPosition) {
            this.connection = info.connection;
            this.username = info.username;
            this.uuid = info.uuid;

            this.instance = spawnInstance;
            this.position = spawnPosition;
            this.oldPosition = spawnPosition;

            Arrays.fill(inventory, ItemStack.AIR);

            for (int i = 0; i < defaultShields.size(); i++) {
                this.inventory[i] = defaultShields.get(i);
            }

            this.synchronizerEntry = instance.synchronizer.makeReceiver(id, position,
                    () -> {
                        final var spawnPacket = new SpawnEntityPacket(
                                id, uuid, EntityType.PLAYER.id(),
                                this.position, 165, 0, (short) 0, (short) 0, (short) 0
                        );
                        return List.of(getAddPlayerToList(), spawnPacket);
                    },
                    () -> List.of(new DestroyEntitiesPacket(id)),
                    (plays, ints) -> connection.networkContext.write(new NetworkContext.Packet.PlayList(plays, ints)));

            this.messaging = new ScratchFeature.Messaging(new ScratchFeature.Messaging.Mapping() {
                @Override
                public Component formatMessage(String message) {
                    return Component.text(username).color(TextColor.color(0xFFFFFF))
                            .append(Component.text(": "))
                            .append(Component.text(message));
                }

                @Override
                public void signal(ServerPacket.Play packet) {

                }
            });

            this.movement = new ScratchFeature.Movement(new ScratchFeature.Movement.Mapping() {
                @Override
                public int id() {
                    return id;
                }

                @Override
                public Pos position() {
                    return position;
                }

                @Override
                public void updatePosition(Pos position) {
                    Player.this.position = position;
                    synchronizerEntry.move(position);
                }

                @Override
                public void signalMovement(ServerPacket.Play packet) {
                    synchronizerEntry.signalLocal(packet);
                }
            });

            this.chunkLoading = new ScratchFeature.ChunkLoading(new ScratchFeature.ChunkLoading.Mapping() {
                @Override
                public int viewDistance() {
                    return VIEW_DISTANCE;
                }

                @Override
                public Pos oldPosition() {
                    return oldPosition;
                }

                @Override
                public ChunkDataPacket chunkPacket(int chunkX, int chunkZ) {
                    return instance.blockHolder.generatePacket(chunkX, chunkZ);
                }

                @Override
                public void sendPacket(ServerPacket.Play packet) {
                    Player.this.connection.networkContext.write(packet);
                }
            });

            this.entityInteract = new ScratchFeature.EntityInteract(new ScratchFeature.EntityInteract.Mapping() {
                @Override
                public void left(int id) {

                }

                @Override
                public void right(int id) {

                }
            });

            this.inventoryHandling = new ScratchFeature.InventoryHandling(new ScratchFeature.InventoryHandling.Mapping() {
                @Override
                public void setPlayerItem(int slot, ItemStack itemStack) {
                    inventory[slot] = itemStack;
                    System.out.println("afigel");
//                    new PlaybackPlayer("bot", null, null).updateNewViewer(this);
                }

                @Override
                public void setCursorItem(ItemStack itemStack) {
                    cursor = itemStack;
                }
            });

            if (!Player.this.username.startsWith("yunfachi_")) // TODO
                this.connection.networkContext.writePlays(initPackets());
        }

        private List<ServerPacket.Play> initPackets() {
            PaletteWorld blockHolder = instance.blockHolder;
            List<ServerPacket.Play> packets = new ArrayList<>();

            final JoinGamePacket joinGamePacket = new JoinGamePacket(
                    id, false, List.of(), 0,
                    8, 8,
                    false, true, false,
                    0, "world",
                    0, gameMode, null, false, true,
                    new WorldPos("dimension", Vec.ZERO), 0, false);
            packets.add(joinGamePacket);
            packets.add(new SpawnPositionPacket(position, 0));
            packets.add(new PlayerPositionAndLookPacket(position, (byte) 0, 0));
//            packets.add(getAddPlayerToList());

            packets.add(new UpdateViewDistancePacket(VIEW_DISTANCE));
            packets.add(new UpdateViewPositionPacket(position.chunkX(), position.chunkZ()));
            ChunkRangeUtils.forChunksInRange(position.chunkX(), position.chunkZ(), VIEW_DISTANCE,
                    (x, z) -> packets.add(blockHolder.generatePacket(x, z)));

            packets.add(new ChangeGameStatePacket(ChangeGameStatePacket.Reason.LEVEL_CHUNKS_LOAD_START, 0f));

            packets.add(ScratchInventoryUtils.makePlayerPacket(inventory, cursor));

            return packets;
        }

        boolean tick() {
            ClientPacket packet;
            while ((packet = connection.packetQueue.poll()) != null) {
                this.messaging.accept(packet);
                this.movement.accept(packet);
                this.chunkLoading.accept(packet);
                this.entityInteract.accept(packet);
            }
            this.oldPosition = this.position;

            UUID uuid = UUID.randomUUID();
            var entry = new PlayerInfoUpdatePacket.Entry(uuid, "bot",  new ArrayList<PlayerInfoUpdatePacket.Property>(), false,
                    0, GameMode.CREATIVE, null, null);

            return connection.online;
        }

        private PlayerInfoUpdatePacket getAddPlayerToList() {
            final var infoEntry = new PlayerInfoUpdatePacket.Entry(uuid, username, List.of(),
                    true, 1, gameMode, null, null);
            return new PlayerInfoUpdatePacket(EnumSet.of(PlayerInfoUpdatePacket.Action.ADD_PLAYER, PlayerInfoUpdatePacket.Action.UPDATE_LISTED),
                    List.of(infoEntry));
        }
    }
}

@SuppressWarnings("UnstableApiUsage")
class PlaybackPlayer extends Entity {
    private final String username;

    private final String skinTexture;
    private final String skinSignature;

    public PlaybackPlayer(@NotNull String username, @Nullable String skinTexture, @Nullable String skinSignature) {
        super(EntityType.PLAYER);
        this.username = username;

        this.skinTexture = skinTexture;
        this.skinSignature = skinSignature;

        setNoGravity(true);
    }

    public void updateNewViewer(Player player) {
        var properties = new ArrayList<PlayerInfoUpdatePacket.Property>();
        if (skinTexture != null && skinSignature != null) {
            properties.add(new PlayerInfoUpdatePacket.Property("textures", skinTexture, skinSignature));
        }
        var entry = new PlayerInfoUpdatePacket.Entry(getUuid(), username, properties, false,
                0, GameMode.SURVIVAL, null, null);
        player.sendPacket(new PlayerInfoUpdatePacket(PlayerInfoUpdatePacket.Action.ADD_PLAYER, entry));

        // Spawn the player entity
        super.updateNewViewer(player);

        // Enable skin layers
        player.sendPackets(new EntityMetaDataPacket(getEntityId(), Map.of(17, Metadata.Byte((byte) 127))));
    }

    @Override
    public void updateOldViewer(@NotNull Player player) {
        super.updateOldViewer(player);

        player.sendPacket(new PlayerInfoRemovePacket(getUuid()));
    }
}