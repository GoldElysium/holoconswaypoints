package xyz.holocons.mc.waypoints;

import java.util.Optional;
import java.util.UUID;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.Registry;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import it.unimi.dsi.fastutil.ints.IntList;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

public record Hologram(long chunkKey, UUID uniqueId) {

    public Hologram(Waypoint waypoint, Player player) {
        this(waypoint.getChunkKey(), player.getUniqueId());
    }

    // https://wiki.vg/Entity_metadata#Mobs
    private static final int ARMOR_STAND_TYPE_ID = 1;
    private static final Vector HOLOGRAM_POSITION_OFFSET = new Vector(0.5, 1.6, 0.5);

    // https://nms.screamingsandals.org/1.18.1/net/minecraft/network/protocol/game/ClientboundAddMobPacket.html
    public static PacketContainer getSpawnPacket(int entityId, UUID uniqueId, Waypoint waypoint) {
        // Calculate the hologram location
        var location = waypoint.getLocation().add(HOLOGRAM_POSITION_OFFSET);

        // Create a new entity to send to the client
        var packet = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY_LIVING);

        // Set entity information
        packet.getIntegers()
            .write(0, entityId)             // id
            .write(1, ARMOR_STAND_TYPE_ID)  // type
            .write(2, 0)                    // xd
            .write(3, 0)                    // yd
            .write(4, 0);                   // zd

        // Set entity location
        packet.getDoubles()
            .write(0, location.getX())      // x
            .write(1, location.getY())      // y
            .write(2, location.getZ());     // z

        // Set the entity UUID
        packet.getUUIDs()
            .write(0, uniqueId);            // uuid

        // Set the entity rotation
        packet.getBytes()
            .write(0, (byte)0)              // yRot
            .write(1, (byte)0)              // xRot
            .write(2, (byte)0);             // yHeadRot

        return packet;
    }

    // https://nms.screamingsandals.org/1.18.1/net/minecraft/network/protocol/game/ClientboundSetEntityDataPacket.html
    // https://wiki.vg/Entity_metadata#Entity_Metadata_Format
    public static PacketContainer getMetadataPacket(int entityId, Waypoint waypoint) {
        // Get the waypoint name as chat component
        var name = WrappedChatComponent.fromJson(GsonComponentSerializer.gson().serialize(waypoint.getDisplayName()));

        // Create entity data ojbect
        var watcher = new WrappedDataWatcher();

        // Set entity as invisible
        watcher.setObject(0, Registry.get(Byte.class), (byte)0x20, true);
        // Set the entity name
        watcher.setObject(2, Registry.getChatComponentSerializer(true), Optional.of(name.getHandle()), true);
        // Make the entity name visisble
        watcher.setObject(3, Registry.get(Boolean.class), true, true);
        // Disable baseplate (0x08) and set it as a marker (0x10)
        watcher.setObject(15, Registry.get(Byte.class), (byte)(0x08 | 0x10), true);

        var metadata = watcher.getWatchableObjects();

        // Create the packet with the created metadata
        var packet = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);

        // Set the entity id and add the metadata
        packet.getIntegers()
            .write(0, entityId);    // id
        packet.getWatchableCollectionModifier()
            .write(0, metadata);    // packedItems

        return packet;
    }

    // https://nms.screamingsandals.org/1.18.1/net/minecraft/network/protocol/game/ClientboundRemoveEntitiesPacket.html
    public static PacketContainer getDestroyPacket(int... entityId) {
        var entityIds = IntList.of(entityId);
        var packet = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);

        packet.getIntLists()
            .write(0, entityIds);   // entityIds

        return packet;
    }
}
