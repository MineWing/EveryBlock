package dev.everyblock.message;

import org.junit.jupiter.api.Test;
import org.bukkit.Server;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import net.kyori.adventure.bossbar.BossBar;
import static org.mockito.Mockito.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
