package dev.everyblock.message;

import org.junit.jupiter.api.Test;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import net.kyori.adventure.bossbar.BossBar;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MessageServiceTest {
    @Test
    void configuredLinesAcceptsYamlLists() {
        assertEquals(List.of("line one", "", "line three"),
                MessageService.configuredLines(List.of("line one", "", "line three")));
    }

    @Test
    void configuredLinesKeepsLegacyScalarValuesWorking() {
        assertEquals(List.of("one old line"), MessageService.configuredLines("one old line"));
        assertEquals(List.of(), MessageService.configuredLines(""));
    }

    @Test
    void shutdownHidesActiveBossBarsEvenWhenExpiryTaskNeverRuns() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(mock(BukkitScheduler.class));
        Player player = mock(Player.class);
        MessageService service = new MessageService(plugin);
        YamlConfiguration messages = new YamlConfiguration();
        messages.loadFromString("celebrate:\n  actions:\n    - '[bossbar] Success | 30'\n");
        service.apply(messages);
        service.send(player, "celebrate");
        verify(player).showBossBar(any(BossBar.class));
        service.shutdown();
        service.shutdown();
        verify(player, times(1)).hideBossBar(any(BossBar.class));
    }

    @Test
    void onlyPluginTaggedFireworksHaveDamageCancelled() {
        MessageService service = new MessageService(null);
        Firework firework = mock(Firework.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(firework.getPersistentDataContainer()).thenReturn(data);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(firework);
        service.onCelebrationDamage(event);
        verify(event, never()).setCancelled(true);
        when(data.has(new NamespacedKey("everyblock", "celebration"), PersistentDataType.BYTE))
                .thenReturn(true);
        service.onCelebrationDamage(event);
        verify(event).setCancelled(true);
    }

    @Test
    void injectedValuesAreNotExpandedAgain() throws Exception {
        MessageService service = new MessageService(null);
        YamlConfiguration messages = new YamlConfiguration();
        messages.loadFromString("tag: BLOCKS\n");
        service.apply(messages);
        assertEquals("/blocks search %tag%",
                service.applyPlaceholders("/blocks search %query%", Map.of("query", "%tag%")));
        assertEquals("BLOCKS %percent%", service.applyPlaceholders("%tag% %block%",
                Map.of("block", "%percent%", "percent", "50.0")));
    }

    @Test
    void trailingPercentSignAfterAPlaceholderIsKept() {
        assertEquals("50.0%", MessageService.replace("%percent%%", Map.of("percent", "50.0")));
        assertEquals("(50.0%) 3 left", MessageService.replace("(%percent%%) %remaining% left",
                Map.of("percent", "50.0", "remaining", "3")));
    }

    @Test
    void unknownTokensAndLonePercentSignsArePreserved() {
        assertEquals("%unknown% 7 100% %",
                MessageService.replace("%unknown% %amount% 100% %", Map.of("amount", "7")));
        assertEquals("no placeholders", MessageService.replace("no placeholders", Map.of("amount", "7")));
    }

    @Test
    void resolutionCacheRemembersHitsAndMissesUntilCleared() {
        AtomicInteger lookups = new AtomicInteger();
        MessageService.ResolutionCache<String> cache = new MessageService.ResolutionCache<>(name -> {
            lookups.incrementAndGet();
            return name.equals("KNOWN") ? "resolved" : null;
        });
        for (int attempt = 0; attempt < 3; attempt++) {
            assertEquals("resolved", cache.resolve("KNOWN"));
            assertNull(cache.resolve("ENTITY_VILLAGER_NOPE"));
        }
        assertEquals(2, lookups.get());
        cache.clear();
        cache.resolve("KNOWN");
        assertEquals(3, lookups.get());
    }

    @Test
    void launchPointsClusterNearbyPlayersPerWorld() {
        World overworld = mock(World.class);
        World nether = mock(World.class);
        List<Player> players = List.of(
                player(overworld, 0, 0),
                player(overworld, 10, 5),
                player(overworld, 40, 0),
                player(nether, 0, 0),
                offline(player(overworld, 500, 0)));
        List<Location> points = MessageService.launchPoints(players);
        assertEquals(List.of(0.0, 40.0, 0.0), points.stream().map(Location::getX).toList());
        assertEquals(List.of(overworld, overworld, nether), points.stream().map(Location::getWorld).toList());
    }

    @Test
    void launchPointsAreCappedPerRound() {
        World world = mock(World.class);
        List<Player> players = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            players.add(player(world, index * 100, 0));
        }
        assertEquals(MessageService.MAX_FIREWORK_LAUNCH_POINTS, MessageService.launchPoints(players).size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fireworkRoundsLaunchPerClusterAndOnlyTrackSpawnedFireworks() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        List<Runnable> rounds = new ArrayList<>();
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenAnswer(invocation -> {
            rounds.add(invocation.getArgument(1));
            return null;
        });
        World world = mock(World.class);
        // The first spawn is cancelled by "another plugin": the consumer still runs,
        // but the entity never becomes valid and no EntityRemoveEvent follows.
        List<Firework> spawned = new ArrayList<>();
        when(world.spawn(any(Location.class), eq(Firework.class), any(Consumer.class))).thenAnswer(invocation -> {
            Firework firework = firework(!spawned.isEmpty());
            ((Consumer<Firework>) invocation.getArgument(2)).accept(firework);
            spawned.add(firework);
            return firework;
        });
        doReturn(List.of(player(world, 0, 0), player(world, 3, 3), player(world, 200, 0)))
                .when(server).getOnlinePlayers();

        MessageService service = new MessageService(plugin);
        YamlConfiguration messages = new YamlConfiguration();
        messages.loadFromString("finale:\n  actions:\n    - '[firework] #F7A48D | STAR | 1 | 2 | 8'\n");
        service.apply(messages);
        service.sendAll("finale", Map.of());
        assertEquals(2, rounds.size());
        rounds.forEach(Runnable::run);

        // Two clusters per round, two rounds: four fireworks rather than six.
        assertEquals(4, spawned.size());
        service.shutdown();
        verify(spawned.get(0), never()).remove();
        spawned.subList(1, spawned.size()).forEach(firework -> verify(firework).remove());
    }

    private static Player player(World world, double x, double z) {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenAnswer(invocation -> new Location(world, x, 64, z));
        return player;
    }

    private static Player offline(Player player) {
        when(player.isOnline()).thenReturn(false);
        return player;
    }

    private static Firework firework(boolean valid) {
        Firework firework = mock(Firework.class);
        when(firework.isValid()).thenReturn(valid);
        when(firework.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(firework.getFireworkMeta()).thenReturn(mock(FireworkMeta.class));
        return firework;
    }
}
