package net.minestom.server.timer;

import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import net.minestom.server.MinecraftServer;
import org.jctools.queues.MpscUnboundedArrayQueue;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Represents a scheduler that will execute tasks with a precision based on its ticking rate.
 * If precision is important, consider using a JDK executor service or any third party library.
 * <p>
 * Tasks are by default executed in the caller thread.
 */
public class Scheduler {
    private static final AtomicInteger TASK_COUNTER = new AtomicInteger();
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        return thread;
    });

    private static final MpscUnboundedArrayQueue<TaskImpl> tasksToExecute = new MpscUnboundedArrayQueue<>(64);
    private static final MpscUnboundedArrayQueue<TaskImpl> tickEndTasksToExecute = new MpscUnboundedArrayQueue<>(64);
    // Tasks scheduled on a certain tick/tick end
    private static final Int2ObjectAVLTreeMap<List<TaskImpl>> tickStartTaskQueue = new Int2ObjectAVLTreeMap<>();
    private static final Int2ObjectAVLTreeMap<List<TaskImpl>> tickEndTaskQueue = new Int2ObjectAVLTreeMap<>();

    private static int tickState;

    /**
     * Process scheduled tasks based on time to increase scheduling precision.
     * <p>
     * This method is not thread-safe.
     */
    public static void process() {
        processTick(0);
    }

    /**
     * Advance 1 tick and call {@link #process()}.
     * <p>
     * This method is not thread-safe.
     */
    public static void processTick() {
        processTick(1);
    }

    private static void processTick(int tickDelta) {
        processTickTasks(tickStartTaskQueue, tasksToExecute, tickDelta);
    }

    /**
     * Execute tasks set to run at the end of this tick.
     * <p>
     * This method is not thread-safe.
     */
    public static void processTickEnd() {
        processTickTasks(tickEndTaskQueue, tickEndTasksToExecute, 0);
    }

    private static void processTickTasks(Int2ObjectAVLTreeMap<List<TaskImpl>> targetTaskQueue, MpscUnboundedArrayQueue<TaskImpl> targetTasksToExecute, int tickDelta) {
        tickState += tickDelta;
        int tickToProcess;
        while (!targetTaskQueue.isEmpty() && (tickToProcess = targetTaskQueue.firstIntKey()) <= tickState) {
            final List<TaskImpl> tickScheduledTasks = targetTaskQueue.remove(tickToProcess);
            if (tickScheduledTasks != null) tickScheduledTasks.forEach(targetTasksToExecute::relaxedOffer);
        }
        runTasks(targetTasksToExecute);
    }

    private static void runTasks(MpscUnboundedArrayQueue<TaskImpl> targetQueue) {
        // Run all tasks lock-free, either in the current thread or pool
        if (!targetQueue.isEmpty()) targetQueue.drain(task -> {
                if (task.isAlive()) handleTask(task);
            });
    }

    /**
     * Submits a new task with custom scheduling logic.
     * <p>
     * This is the primitive method used by all scheduling shortcuts,
     * {@code task} is immediately executed in the caller thread to retrieve its scheduling state
     * and the task will stay alive as long as {@link TaskSchedule#stop()} is not returned (or {@link Task#cancel()} is called).
     *
     * @param task          the task to be directly executed in the caller thread
     * @param executionType the execution type
     * @return the created task
     */
    public static @NotNull Task submitTask(@NotNull Supplier<TaskSchedule> task,
                                    @NotNull ExecutionType executionType) {
        final TaskImpl taskRef = new TaskImpl(TASK_COUNTER.getAndIncrement(), task,
                executionType);
        handleTask(taskRef);
        return taskRef;
    }

    static void unparkTask(TaskImpl task) {
        if (task.tryUnpark()) tasksToExecute.relaxedOffer(task);
    }

    private static void safeExecute(TaskImpl task) {
        // Prevent the task from being executed in the current thread
        // By either adding the task to the execution queue or submitting it to the pool
        switch (task.executionType()) {
            case TICK_START -> tasksToExecute.offer(task);
            case TICK_END -> tickEndTasksToExecute.offer(task);
        }
    }

    private static void handleTask(TaskImpl task) {
        TaskSchedule schedule;
        try {
            schedule = task.task().get();
        } catch (Throwable t) {
            MinecraftServer.getExceptionManager().handleException(new RuntimeException("Exception in scheduled task", t));
            schedule = TaskSchedule.stop();
        }

        if (schedule instanceof TaskScheduleImpl.DurationSchedule durationSchedule) SCHEDULER.schedule(() -> safeExecute(task), durationSchedule.duration().toMillis(), TimeUnit.MILLISECONDS);
        else if (schedule instanceof TaskScheduleImpl.TickSchedule tickSchedule) (task.executionType() == ExecutionType.TICK_START? tickStartTaskQueue : tickEndTaskQueue)
                .computeIfAbsent(tickState + tickSchedule.tick(), i -> new ArrayList<>()).add(task);
        else if (schedule instanceof TaskScheduleImpl.FutureSchedule futureSchedule) futureSchedule.future().thenRun(() -> safeExecute(task));
        else if (schedule instanceof TaskScheduleImpl.Park) task.parked = true;
        else if (schedule instanceof TaskScheduleImpl.Stop) task.cancel();
        else if (schedule instanceof TaskScheduleImpl.Immediate) {
            if (task.executionType() == ExecutionType.TICK_END) tickEndTasksToExecute.relaxedOffer(task);
            else tasksToExecute.relaxedOffer(task);
        }
    }

    public static @NotNull Task submitTask(@NotNull Supplier<TaskSchedule> task) {
        return submitTask(task, ExecutionType.TICK_START);
    }

    public static @NotNull Task.Builder buildTask(@NotNull Runnable task) {
        return new Task.Builder(task);
    }

    public static @NotNull Task scheduleTask(@NotNull Runnable task,
                                       @NotNull TaskSchedule delay, @NotNull TaskSchedule repeat,
                                       @NotNull ExecutionType executionType) {
        return buildTask(task).delay(delay).repeat(repeat).executionType(executionType).schedule();
    }

    public static @NotNull Task scheduleTask(@NotNull Runnable task, @NotNull TaskSchedule delay, @NotNull TaskSchedule repeat) {
        return scheduleTask(task, delay, repeat, ExecutionType.TICK_START);
    }

    public static @NotNull Task scheduleTask(@NotNull Supplier<TaskSchedule> task, @NotNull TaskSchedule delay) {
        return new Task.Builder(task).delay(delay).schedule();
    }

    public static @NotNull Task scheduleNextTick(@NotNull Runnable task, @NotNull ExecutionType executionType) {
        return buildTask(task).delay(TaskSchedule.nextTick()).executionType(executionType).schedule();
    }

    public static @NotNull Task scheduleNextTick(@NotNull Runnable task) {
        return scheduleNextTick(task, ExecutionType.TICK_START);
    }

    public static @NotNull Task scheduleEndOfTick(@NotNull Runnable task) {
        return scheduleNextProcess(task, ExecutionType.TICK_END);
    }

    public static @NotNull Task scheduleNextProcess(@NotNull Runnable task, @NotNull ExecutionType executionType) {
        return buildTask(task).delay(TaskSchedule.immediate()).executionType(executionType).schedule();
    }

    public static @NotNull Task scheduleNextProcess(@NotNull Runnable task) {
        return scheduleNextProcess(task, ExecutionType.TICK_START);
    }
}
