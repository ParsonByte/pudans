package cn.tolim.pudans.infra.actuator;

import cn.tolim.pudans.Application;
import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@WebEndpoint(id = "restart")
@Component
@Slf4j
class RestartEndpoint implements ApplicationListener<ApplicationStartedEvent> {

    private SpringApplication application;

    private String[] args;

    private ConfigurableApplicationContext context;

    private static void closeRecursively(ApplicationContext ctx) {
        while (ctx != null) {
            if (ctx instanceof Closeable closeable) {
                try {
                    closeable.close();
                } catch (IOException e) {
                    log.error("无法关闭上下文：{}", ctx.getId(), e);
                }
            }
            ctx = ctx.getParent();
        }
    }

    @WriteOperation
    public Mono<Map<String, String>> restart() {
        return Mono.fromSupplier(() -> {
            var threadGroup = new ThreadGroup("RestartGroup");
            var thread = new Thread(threadGroup, this::doRestart, "restartMain");
            thread.setDaemon(false);
            thread.setContextClassLoader(Application.class.getClassLoader());
            thread.start();
            return Map.of("message", "Restarting");
        });
    }

    private synchronized void doRestart() {
        log.info("正在重启...");
        if (this.context != null) {
            try {
                closeRecursively(this.context);
                var shutdownHandlers = SpringApplication.getShutdownHandlers();
                if (shutdownHandlers instanceof Runnable runnable) {

                    runnable.run();
                }
                this.context = this.application.run(args);
                log.info("已重启");
            } catch (Throwable t) {
                log.error("重启失败。", t);
            }
        }
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        if (this.context == null) {
            this.application = event.getSpringApplication();
            this.args = event.getArgs();
            this.context = event.getApplicationContext();
        }
    }
}
