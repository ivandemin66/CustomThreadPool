package threadpool;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.Logger;

/**
 * Кастомный пул, распределяющий задачи по схемe Round Robin
 * между личными очередями воркеров. Поддерживает авто‑масштабирование
 * и гарантирует наличие minSpareThreads свободных воркеров.
 */
public class CustomThreadPool implements CustomExecutor {

    private static final Logger LOG = Logger.getLogger(CustomThreadPool.class.getName());

    // Конфигурация
    private final int  corePoolSize;
    private final int  maxPoolSize;
    private final int  queueSize;
    private final long keepAliveNanos;
    private final int  minSpareThreads;

    private final RejectedExecutionHandler rejectionHandler;
    private final CustomThreadFactory      threadFactory;

    // Состояние
    private final List<Worker> workers = new CopyOnWriteArrayList<>();
    private final AtomicInteger rrCounter = new AtomicInteger(0);
    private final AtomicInteger busyWorkers = new AtomicInteger(0);
    private volatile  boolean   isShutdown = false;

    public CustomThreadPool(int corePoolSize,
                            int maxPoolSize,
                            int queueSize,
                            long keepAliveTime,
                            TimeUnit unit,
                            int minSpareThreads,
                            CustomThreadFactory threadFactory,
                            RejectedExecutionHandler rejectionHandler) {

        if (corePoolSize <= 0 || maxPoolSize < corePoolSize)
            throw new IllegalArgumentException("Bad pool sizes");
        if (minSpareThreads < 0 || minSpareThreads > maxPoolSize)
            throw new IllegalArgumentException("Bad minSpareThreads");

        this.corePoolSize  = corePoolSize;
        this.maxPoolSize   = maxPoolSize;
        this.queueSize     = queueSize;
        this.keepAliveNanos= unit.toNanos(keepAliveTime);
        this.minSpareThreads = minSpareThreads;
        this.threadFactory   = threadFactory;
        this.rejectionHandler= rejectionHandler;

        // стартуем базовые потоки
        for (int i = 0; i < corePoolSize; i++) createWorker();
    }

    // ---------- API ----------

    @Override
    public void execute(Runnable command) {
        submitInternal(command);
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        FutureTask<T> ft = new FutureTask<>(callable);
        submitInternal(ft);
        return ft;
    }

    @Override
    public void shutdown() {
        isShutdown = true;
        LOG.info("[Pool] Shutdown requested (graceful)");
        workers.forEach(Worker::signalShutdown);
    }

    @Override
    public void shutdownNow() {
        isShutdown = true;
        LOG.info("[Pool] Shutdown NOW requested");
        workers.forEach(Worker::interrupt);
    }

    // ---------- Внутренняя логика ----------

    private void submitInternal(Runnable task) {
        if (isShutdown) {
            throw new RejectedExecutionException("Pool already shut down");
        }

        // Балансировка: Round‑Robin по очередям воркеров
        Worker target = chooseWorker();
        boolean offered = target.queue.offer(task);

        if (!offered) {
            // Очередь переполнена — попытаемся расширить пул
            if (workers.size() < maxPoolSize) {
                Worker w = createWorker();
                offered = w.queue.offer(task);
            }
            if (!offered) {
                // Ничего не помогло — политика отказа
                rejectionHandler.rejectedExecution(task, null);
            } else {
                LOG.fine("[Pool] Task accepted after scaling, queue of " + target.getName());
            }
        } else {
            LOG.fine("[Pool] Task accepted into queue of " + target.getName());
        }
    }

    /**
     * Выбирает воркера по Round Robin.
     */
    private Worker chooseWorker() {
        int idx = rrCounter.getAndIncrement();
        return workers.get(idx % workers.size());
    }

    /**
     * Создаёт новый воркер и добавляет в список.
     */
    private Worker createWorker() {
        BlockingQueue<Runnable> q = new ArrayBlockingQueue<>(queueSize);
        Worker w = new Worker(q);
        workers.add(w);
        w.start();
        return w;
    }

    /**
     * Коррекция «LIFО» — чтобы после завершения потоков гарантировать
     * наличие minSpareThreads свободных (idle) воркеров.
     */
    private void maintainSpareThreads() {
        long free = workers.stream().filter(Worker::isIdle).count();
        while (free < minSpareThreads && workers.size() < maxPoolSize) {
            createWorker();
            free++;
        }
    }

    // ---------- Внутренний класс Worker ----------

    private class Worker extends Thread {
        private final BlockingQueue<Runnable> queue;
        private volatile boolean running = true;
        private final AtomicLong lastTaskTime = new AtomicLong(System.nanoTime());

        Worker(BlockingQueue<Runnable> q) {
            super();
            setName(threadFactory.newThread(this::runLoop).getName());
            this.queue = q;
        }

        boolean isIdle() {
            return busyWorkers.get() <= 0 || queue.isEmpty(); // если нет занятых — все idle
        }

        void signalShutdown() {
            running = false;
            this.interrupt();
        }

        private void runLoop() {
            try {
                while (running && !Thread.currentThread().isInterrupted()) {
                    Runnable task = queue.poll(keepAliveNanos, TimeUnit.NANOSECONDS);
                    if (task != null) {
                        busyWorkers.incrementAndGet();
                        LOG.info("[Worker] " + getName() + " executes " + task);
                        try {
                            task.run();
                        } finally {
                            lastTaskTime.set(System.nanoTime());
                            busyWorkers.decrementAndGet();
                        }
                    } else { // timeout
                        long idleFor = System.nanoTime() - lastTaskTime.get();
                        if (workers.size() > corePoolSize && idleFor >= keepAliveNanos) {
                            LOG.info("[Worker] " + getName() + " idle timeout, stopping.");
                            break;
                        }
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } finally {
                LOG.info("[Worker] " + getName() + " terminated.");
                workers.remove(this);
                maintainSpareThreads();
            }
        }
    }
}