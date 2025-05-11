package main;

import threadpool.CallerRunsPolicy;
import threadpool.CustomThreadFactory;
import threadpool.CustomThreadPool;

import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Демонстрирует:
 *  1. Нормальную обработку задач.
 *  2. Автомасштабирование и тайм‑аут idle.
 *  3. Отказ при переполнении.
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {

        // Красивый вывод логов в консоль
        Logger root = Logger.getLogger("");
        root.setLevel(Level.INFO);

        CustomThreadPool pool = new CustomThreadPool(
                2,                      // corePoolSize
                4,                      // maxPoolSize
                5,                      // queueSize
                5, TimeUnit.SECONDS,    // keepAlive
                1,                      // minSpareThreads
                new CustomThreadFactory("MyPool"),
                new CallerRunsPolicy()
        );

        // Имитационные задачи
        for (int i = 1; i <= 15; i++) {
            int id = i;
            pool.execute(() -> {
                String name = Thread.currentThread().getName();
                System.out.println("[Task " + id + "] start in " + name);
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                System.out.println("[Task " + id + "] finish in " + name);
            });
        }

        // Дать поработать
        Thread.sleep(10_000);

        // Корректное завершение
        pool.shutdown();

        // Ждём завершения всех задач
        Thread.sleep(6_000);

        // Проверяем жёсткое завершение
        pool.shutdownNow();
    }
}