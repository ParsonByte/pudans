package cn.tolim.pudans.iam.infra.scheduling;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.config.CronTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

public class SpringSchedulerRegister implements DisposableBean {
    private final TaskScheduler scheduler;
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    public SpringSchedulerRegister(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void addCronTask(String name, Runnable task, String expression) {
        register(name, new CronTask(task, expression));
    }

    public TaskScheduler getScheduler() {
        return scheduler;
    }

    public void removeCronTask(String name) {
        ScheduledFuture<?> future = futures.remove(name);
        if (future != null) {
            future.cancel(false);
        }
    }

    @Override
    public void destroy() {
        futures.values().forEach(future -> future.cancel(true));
        futures.clear();
    }

    private void register(String name, CronTask task) {
        if (name == null || task == null) {
            return;
        }
        removeCronTask(name);
        futures.put(name, scheduler.schedule(task.getRunnable(), task.getTrigger()));
    }
}
