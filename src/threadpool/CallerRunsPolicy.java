package threadpool;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Logger;

/**
 * При переполнении очередь/воркеры заняты.
 * Вызывающий поток выполняет задачу синхронно — так мы:
 *  1. Не теряем задачу;
 *  2. Замедляем «производителя» и сглаживаем пик нагрузки.
 * Минусы: может блокировать важный I/O‑поток вызывающего.
 */
public class CallerRunsPolicy implements RejectedExecutionHandler {

    private static final Logger LOG = Logger.getLogger(CallerRunsPolicy.class.getName());

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        LOG.warning("[Rejected] Task " + r + " was rejected — executing in caller thread!");
        r.run();
    }
}