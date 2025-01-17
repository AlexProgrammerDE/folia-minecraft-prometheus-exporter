package de.sldk.mc.metrics;

import io.prometheus.client.Gauge;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Get total count of Villagers.
 * <p>
 * Labelled by
 * <ul>
 *     <li> World ({@link World#getName()})
 *     <li> Type, e.g. 'desert', 'plains ({@link org.bukkit.entity.Villager.Type})
 *     <li> Profession, e.g. 'fisherman', 'farmer', or 'none' ({@link org.bukkit.entity.Villager.Profession})
 *     <li> Level ({@link Villager#getVillagerLevel()})
 * </ul>
 */
public class Villagers extends WorldMetric {

    private static final Gauge VILLAGERS = Gauge.build()
            .name(prefix("villagers_total"))
            .help("Villagers total count, labelled by world, type, profession, and level")
            .labelNames("world", "type", "profession", "level")
            .create();

    public Villagers(Plugin plugin) {
        super(plugin, VILLAGERS);
    }

    @Override
    protected void clear() {
        VILLAGERS.clear();
    }

    @Override
    public void collect(World world) {
        CompletableFuture<Collection<Villager>> worldVillagers = new CompletableFuture<>();
        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> worldVillagers.complete(world.getEntitiesByClass(Villager.class)));
        Map<VillagerGrouping, Long> mapVillagerGroupingToCount = worldVillagers.join().stream()
                .map(e -> {
                    CompletableFuture<VillagerGrouping> grouping = new CompletableFuture<>();
                    e.getScheduler().execute(plugin, () -> grouping.complete(new VillagerGrouping(e)), null, 0);
                    return grouping;
                })
                .collect(Collectors.toList()) // Collecting forces all completable futures to execute
                .stream()
                .map(CompletableFuture::join)
                .collect(Collectors.groupingBy(e -> e, Collectors.counting()));

        mapVillagerGroupingToCount.forEach((grouping, count) ->
                VILLAGERS
                        .labels(world.getName(),
                                grouping.type.getKey().getKey(),
                                grouping.profession.getKey().getKey(),
                                Integer.toString(grouping.level))
                        .set(count)
        );
    }

    /**
     * Class used to group villagers together before summation.
     */
    private static class VillagerGrouping {
        private final Villager.Type type;
        private final Villager.Profession profession;
        private final int level;

        VillagerGrouping(Villager villager) {
            this.type = villager.getVillagerType();
            this.profession = villager.getProfession();
            this.level = villager.getVillagerLevel();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VillagerGrouping that = (VillagerGrouping) o;
            return level == that.level &&
                    type == that.type &&
                    profession == that.profession;
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, profession, level);
        }
    }
}
