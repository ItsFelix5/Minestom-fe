package net.minestom.server.network.packet.client.play;

import net.minestom.server.entity.Player;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.server.play.StatisticsPacket;
import net.minestom.server.statistic.PlayerStatistic;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record ClientStatusPacket(@NotNull Action action) implements ClientPacket {
    public ClientStatusPacket(@NotNull NetworkBuffer reader) {
        this(reader.readEnum(Action.class));
    }

    @Override
    public void write(@NotNull NetworkBuffer writer) {
        writer.writeEnum(Action.class, action);
    }

    @Override
    public void listener(Player player) {
        switch (action) {
            case PERFORM_RESPAWN -> player.respawn();
            case REQUEST_STATS -> {
                List<StatisticsPacket.Statistic> statisticList = new ArrayList<>();
                final Map<PlayerStatistic, Integer> playerStatisticValueMap = player.getStatisticValueMap();
                for (var entry : playerStatisticValueMap.entrySet()) {
                    final PlayerStatistic playerStatistic = entry.getKey();
                    final int value = entry.getValue();
                    statisticList.add(new StatisticsPacket.Statistic(playerStatistic.getCategory(),
                            playerStatistic.getStatisticId(), value));
                }
                StatisticsPacket statisticsPacket = new StatisticsPacket(statisticList);
                player.sendPacket(statisticsPacket);
            }
        }
    }

    public enum Action {
        PERFORM_RESPAWN,
        REQUEST_STATS
    }
}
