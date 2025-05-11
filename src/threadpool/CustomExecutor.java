package threadpool;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

public interface CustomExecutor extends Executor {

    @Override
    void execute(Runnable command);

    <T> Future<T> submit(Callable<T> callable);

    void shutdown();      // мягкое завершение: ждём задачи

    void shutdownNow();   // жёсткое завершение: прерываем потоки
}