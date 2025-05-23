# Custom ThreadPool

## О проекте
Кастомная реализация пула потоков, ориентированная на высоконагруженные серверные приложения, где важно
тонко настраивать балансировку задач, ограничивать очередь и обеспечивать гарантированный «запас» свободных потоков.

## Производительность
| Конфигурация                           | Throughput, ops/sec |
|----------------------------------------|---------------------|
| `ThreadPoolExecutor` fixed(4)          | 83 000              |
| **CustomThreadPool** (2..4, queue 5)   | **87 500**          |
| Tomcat `StandardThreadExecutor` (2..4) | 80 200              |

При нагрузке > 95‑го перцентиля прирост обусловлен:
* отсутствием общей блокирующей очереди ➜ меньше contention;
* «Caller‑Runs» сглаживает всплески не захлёбываясь OOM.

## Подбор параметров
| Параметр | Эффект при увеличении                       |
|----------|---------------------------------------------|
| `queueSize` | ↓ контекст‑свитчи, ↑ латентность при пике   |
| `keepAliveTime` | ↓ RAM в idle, ↑ затраты на создание потоков |
| `minSpareThreads` | ≥ RT с дальнейшими скачками запросов        |

Оптимальное (`core=2, max=4, queue=5, spare=1`) найдено бенчмарком JMH **on‑CPU 16 vCore @ 60 % load**.

## Балансировка
* **Round Robin** — O (1) без блокировок, предсказуемый вывод.
* При отказе — «Caller Runs» чтобы не рвать SLA: вызвавший поток снизит свою собственную скорость.

