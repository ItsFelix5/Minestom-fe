package net.minestom.server.timer;

public enum ExecutionType {
    /**
     * Schedule tasks to execute at the beginning of the tick
     */
    TICK_START,
    /**
     * Schedule tasks to execute at the end of the tick
     */
    TICK_END
}
