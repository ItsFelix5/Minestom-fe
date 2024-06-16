package net.minestom.server.scoreboard;

import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.pointer.Pointers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.audience.PacketGroupingAudience;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.play.TeamsPacket;
import net.minestom.server.network.packet.server.play.TeamsPacket.CollisionRule;
import net.minestom.server.network.packet.server.play.TeamsPacket.NameTagVisibility;
import net.minestom.server.utils.PacketUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * This object represents a team on a scoreboard that has a common display theme and other properties.
 */
public class Team implements PacketGroupingAudience {
    private static final byte ALLOW_FRIENDLY_FIRE_BIT = 0x01;
    private static final byte SEE_INVISIBLE_PLAYERS_BIT = 0x02;

    private static final Set<Team> teams = new CopyOnWriteArraySet<>();

    /**
     * A collection of all registered entities who are on the team.
     */
    private final Set<String> members;

    /**
     * The registry name of the team.
     */
    private final String teamName;
    /**
     * The display name of the team.
     */
    private Component teamDisplayName;
    /**
     * A BitMask.
     */
    private byte friendlyFlags;
    /**
     * The visibility of the team.
     */
    private NameTagVisibility nameTagVisibility;
    /**
     * The collision rule of the team.
     */
    private CollisionRule collisionRule;

    /**
     * Used to color the name of players on the team <br>
     * The color of a team defines how the names of the team members are visualized.
     */
    private NamedTextColor teamColor;

    /**
     * Shown before the names of the players who belong to this team.
     */
    private Component prefix;
    /**
     * Shown after the names of the player who belong to this team.
     */
    private Component suffix;

    private final Set<Player> playerMembers = ConcurrentHashMap.newKeySet();
    private boolean isPlayerMembersUpToDate;

    // Adventure
    private final Pointers pointers;

    /**
     * Gets a {@link Team} with the given name
     *
     * @param teamName The registry name of the team
     * @return a registered {@link Team} or {@code null}
     */
    public static Team getTeam(String teamName) {
        for (Team team : teams) if (team.getTeamName().equals(teamName)) return team;
        return null;
    }

    /**
     * Checks if the given name a registry name of a registered {@link Team}
     *
     * @param teamName The name of the team
     * @return {@code true} if the team is registered, otherwise {@code false}
     */
    public static boolean exists(String teamName) {
        for (Team team : teams) if (team.getTeamName().equals(teamName)) return true;
        return false;
    }

    /**
     * Checks if the given {@link Team} registered
     *
     * @param team The searched team
     * @return {@code true} if the team is registered, otherwise {@code false}
     */
    public static boolean exists(Team team) {
        return exists(team.getTeamName());
    }

    /**
     * Default constructor to creates a team.
     *
     * @param teamName The registry name for the team
     */
    public Team(@NotNull String teamName) {
        this.teamName = teamName;

        this.teamDisplayName = Component.empty();
        this.friendlyFlags = 0x00;
        this.nameTagVisibility = NameTagVisibility.ALWAYS;
        this.collisionRule = CollisionRule.ALWAYS;

        this.teamColor = NamedTextColor.WHITE;
        this.prefix = Component.empty();
        this.suffix = Component.empty();

        this.members = new CopyOnWriteArraySet<>();

        this.pointers = Pointers.builder()
                .withDynamic(Identity.NAME, this::getTeamName)
                .withDynamic(Identity.DISPLAY_NAME, this::getTeamDisplayName)
                .build();


        teams.add(this);
        PacketUtils.broadcastPlayPacket(createTeamsCreationPacket());
    }

    /**
     * Deletes a {@link Team}
     */
    public void delete() {
        // Sends to all online players a team destroy packet
        if(teams.remove(this)) PacketUtils.broadcastPlayPacket(createTeamDestructionPacket());
    }

    /**
     * Adds a member to the {@link Team}.
     * <br>
     * This member collection can contain {@link Player} or {@link LivingEntity}.
     * For players use their username, for entities use their UUID
     *
     * @param member The member to be added
     */
    public void addMember(@NotNull String member) {
        addMembers(List.of(member));
    }

    /**
     * Adds a members to the {@link Team}.
     * <br>
     * This member collection can contain {@link Player} or {@link LivingEntity}.
     * For players use their username, for entities use their UUID
     *
     * @param toAdd The members to be added
     */
    public void addMembers(@NotNull Collection<@NotNull String> toAdd) {
        // Adds a new member to the team
        this.members.addAll(toAdd);

        // Sends to all online players the add player packet
        PacketUtils.broadcastPlayPacket(new TeamsPacket(teamName,
                new TeamsPacket.AddEntitiesToTeamAction(toAdd)));

        // invalidate player members
        this.isPlayerMembersUpToDate = false;
    }

    /**
     * Removes a member from the {@link Team}.
     * <br>
     * This member collection can contain {@link Player} or {@link LivingEntity}.
     * For players use their username, for entities use their UUID
     *
     * @param member The member to be removed
     */
    public void removeMember(@NotNull String member) {
        removeMembers(List.of(member));
    }

    /**
     * Removes members from the {@link Team}.
     * <br>
     * This member collection can contain {@link Player} or {@link LivingEntity}.
     * For players use their username, for entities use their UUID
     *
     * @param toRemove The members to be removed
     */
    public void removeMembers(@NotNull Collection<@NotNull String> toRemove) {
        // Initializes remove player packet
        final TeamsPacket removePlayerPacket = new TeamsPacket(teamName,
                new TeamsPacket.RemoveEntitiesToTeamAction(toRemove));
        // Sends to all online player the remove player packet
        PacketUtils.broadcastPlayPacket(removePlayerPacket);

        // Removes the member from the team
        this.members.removeAll(toRemove);

        // invalidate player members
        this.isPlayerMembersUpToDate = false;
    }

    /**
     * Changes the display name of the team.
     * <br><br>
     * <b>Warning: </b> If you do not call {@link #sendUpdatePacket()}, this is only changed of the <b>server side</b>.
     *
     * @param teamDisplayName The new display name
     */
    public void setTeamDisplayName(Component teamDisplayName) {
        this.teamDisplayName = teamDisplayName;
    }

    /**
     * Changes the {@link NameTagVisibility} of the team.
     * <br><br>
     * <b>Warning: </b> If you do not call {@link #sendUpdatePacket()}, this is only changed of the <b>server side</b>.
     *
     * @param visibility The new tag visibility
     */
    public void setNameTagVisibility(@NotNull NameTagVisibility visibility) {
        this.nameTagVisibility = visibility;
    }

    /**
     * Changes the {@link CollisionRule} of the team.
     * <br><br>
     * <b>Warning: </b> If you do not call {@link #sendUpdatePacket()}, this is only changed of the <b>server side</b>.
     *
     * @param rule The new rule
     */
    public void setCollisionRule(@NotNull CollisionRule rule) {
        this.collisionRule = rule;
    }

    /**
     * Changes the color of the team.
     * <br><br>
     * <b>Warning: </b> If you do not call {@link #sendUpdatePacket()}, this is only changed of the <b>server side</b>.
     *
     * @param color The new team color
     */
    public void setTeamColor(@NotNull NamedTextColor color) {
        this.teamColor = color;
    }

    /**
     * Changes the prefix of the team.
     * <br><br>
     * <b>Warning: </b> If you do not call {@link #sendUpdatePacket()}, this is only changed of the <b>server side</b>.
     *
     * @param prefix The new prefix
     */
    public void setPrefix(Component prefix) {
        this.prefix = prefix;
    }

    /**
     * Changes the suffix of the team.
     * <br><br>
     * <b>Warning: </b> If you do not call {@link #sendUpdatePacket()}, this is only changed of the <b>server side</b>.
     *
     * @param suffix The new suffix
     */
    public void setSuffix(Component suffix) {
        this.suffix = suffix;
    }

    private boolean getFriendlyFlagBit(byte index) {
        return (this.friendlyFlags & index) == index;
    }

    private void setFriendlyFlagBit(byte index, boolean value) {
        if (value) this.friendlyFlags |= index;
        else this.friendlyFlags &= ~index;
    }

    /**
     * Changes the friendly flags for allow friendly fire.
     * <br><br>
     * <b>Warning: </b> If you do not call {@link #sendUpdatePacket()}, this is only changed of the <b>server side</b>.
     */
    public void setAllowFriendlyFire(boolean value) {
        this.setFriendlyFlagBit(ALLOW_FRIENDLY_FIRE_BIT, value);
    }

    public boolean isAllowFriendlyFire() {
        return this.getFriendlyFlagBit(ALLOW_FRIENDLY_FIRE_BIT);
    }

    /**
     * Changes the friendly flags to see invisible players of own team.
     * <br><br>
     * <b>Warning: </b> If you do not call {@link #sendUpdatePacket()}, this is only changed of the <b>server side</b>.
     */
    public void setSeeInvisiblePlayers(boolean value) {
        this.setFriendlyFlagBit(SEE_INVISIBLE_PLAYERS_BIT, value);
    }

    public boolean isSeeInvisiblePlayers() {
        return this.getFriendlyFlagBit(SEE_INVISIBLE_PLAYERS_BIT);
    }

    /**
     * Gets the registry name of the team.
     *
     * @return the registry name
     */
    public String getTeamName() {
        return teamName;
    }

    /**
     * Creates the creation packet to add a team.
     *
     * @return the packet to add the team
     */
    public @NotNull TeamsPacket createTeamsCreationPacket() {
        return new TeamsPacket(teamName, new TeamsPacket.CreateTeamAction(teamDisplayName, friendlyFlags,
                nameTagVisibility, collisionRule, teamColor, prefix, suffix, members));
    }

    /**
     * Creates an destruction packet to remove the team.
     *
     * @return the packet to remove the team
     */
    public @NotNull TeamsPacket createTeamDestructionPacket() {
        return new TeamsPacket(teamName, new TeamsPacket.RemoveTeamAction());
    }

    /**
     * Obtains an unmodifiable {@link Set} of registered players who are on the team.
     *
     * @return an unmodifiable {@link Set} of registered players
     */
    public @NotNull Set<String> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    /**
     * Gets the display name of the team.
     *
     * @return the display name
     */
    public Component getTeamDisplayName() {
        return teamDisplayName;
    }

    /**
     * Gets the friendly flags of the team.
     *
     * @return the friendly flags
     */
    public byte getFriendlyFlags() {
        return friendlyFlags;
    }

    /**
     * Gets the tag visibility of the team.
     *
     * @return the tag visibility
     */
    public @NotNull NameTagVisibility getNameTagVisibility() {
        return nameTagVisibility;
    }

    /**
     * Gets the collision rule of the team.
     *
     * @return the collision rule
     */
    public @NotNull CollisionRule getCollisionRule() {
        return collisionRule;
    }

    /**
     * Gets the color of the team.
     *
     * @return the team color
     */
    public @NotNull NamedTextColor getTeamColor() {
        return teamColor;
    }

    /**
     * Gets the prefix of the team.
     *
     * @return the team prefix
     */
    public Component getPrefix() {
        return prefix;
    }

    /**
     * Gets the suffix of the team.
     *
     * @return the suffix team
     */
    public Component getSuffix() {
        return suffix;
    }

    /**
     * Sends an {@link TeamsPacket.UpdateTeamAction} action packet.
     */
    public void sendUpdatePacket() {
        PacketUtils.broadcastPlayPacket(new TeamsPacket(teamName, new TeamsPacket.UpdateTeamAction(teamDisplayName, friendlyFlags,
                nameTagVisibility, collisionRule, teamColor, prefix, suffix)));
    }

    @Override
    public @NotNull Collection<Player> getPlayers() {
        if (!this.isPlayerMembersUpToDate) {
            this.playerMembers.clear();

            for (String member : this.members) {
                Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(member);

                if (player != null) this.playerMembers.add(player);
            }

            this.isPlayerMembersUpToDate = true;
        }

        return this.playerMembers;
    }

    @Override
    public @NotNull Pointers pointers() {
        return this.pointers;
    }
}
