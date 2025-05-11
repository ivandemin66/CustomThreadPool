package threadpool;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

//Создаёт именные рабочие потоки и фиксирует их жизненный цикл в логах.

public class CustomThreadFactory implements ThreadFactory {

    private static final Logger LOG = Logger.getLogger(CustomThreadFactory.class.getName());
    private final String      poolName;
    private final AtomicInteger counter = new AtomicInteger(0);

    public CustomThreadFactory(String poolName) {
        this.poolName = poolName;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, poolName + "-worker-" + counter.incrementAndGet());
        t.setDaemon(false);
        LOG.info("[ThreadFactory] Creating new thread: " + t.getName());
        t.setUncaughtExceptionHandler((thr, ex) ->
                LOG.severe("[ThreadFactory] Uncaught in " + thr.getName() + ": " + ex));
        return t;
    }
}