package java.util.concurrent;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

/**
 * An {@link ExecutorService} that executes each submitted task using
 * one of possibly several pooled threads, normally configured
 * using {@link Executors} factory methods.
 *
 * <p>Thread pools address two different problems: they usually
 * provide improved performance when executing large numbers of
 * asynchronous tasks, due to reduced per-task invocation overhead,
 * and they provide a means of bounding and managing the resources,
 * including threads, consumed when executing a collection of tasks.
 * Each {@code ThreadPoolExecutor} also maintains some basic
 * statistics, such as the number of completed tasks.
 *
 * <p>To be useful across a wide range of contexts, this class
 * provides many adjustable parameters and extensibility
 * hooks. However, programmers are urged to use the more convenient
 * {@link Executors} factory methods {@link
 * Executors#newCachedThreadPool} (unbounded thread pool, with
 * automatic thread reclamation), {@link Executors#newFixedThreadPool}
 * (fixed size thread pool) and {@link
 * Executors#newSingleThreadExecutor} (single background thread), that
 * preconfigure settings for the most common usage
 * scenarios. Otherwise, use the following guide when manually
 * configuring and tuning this class:
 *
 * <dl>
 *
 * <dt>Core and maximum pool sizes</dt>
 *
 * <dd>A {@code ThreadPoolExecutor} will automatically adjust the
 * pool size (see {@link #getPoolSize})
 * according to the bounds set by
 * corePoolSize (see {@link #getCorePoolSize}) and
 * maximumPoolSize (see {@link #getMaximumPoolSize}).
 *
 * When a new task is submitted in method {@link #execute(Runnable)},
 * and fewer than corePoolSize threads are running, a new thread is
 * created to handle the request, even if other worker threads are
 * idle.  If there are more than corePoolSize but less than
 * maximumPoolSize threads running, a new thread will be created only
 * if the queue is full.  By setting corePoolSize and maximumPoolSize
 * the same, you create a fixed-size thread pool. By setting
 * maximumPoolSize to an essentially unbounded value such as {@code
 * Integer.MAX_VALUE}, you allow the pool to accommodate an arbitrary
 * number of concurrent tasks. Most typically, core and maximum pool
 * sizes are set only upon construction, but they may also be changed
 * dynamically using {@link #setCorePoolSize} and {@link
 * #setMaximumPoolSize}. </dd>
 *
 * <dt>On-demand construction</dt>
 *
 * <dd>By default, even core threads are initially created and
 * started only when new tasks arrive, but this can be overridden
 * dynamically using method {@link #prestartCoreThread} or {@link
 * #prestartAllCoreThreads}.  You probably want to prestart threads if
 * you construct the pool with a non-empty queue. </dd>
 *
 * <dt>Creating new threads</dt>
 *
 * <dd>New threads are created using a {@link ThreadFactory}.  If not
 * otherwise specified, a {@link Executors#defaultThreadFactory} is
 * used, that creates threads to all be in the same {@link
 * ThreadGroup} and with the same {@code NORM_PRIORITY} priority and
 * non-daemon status. By supplying a different ThreadFactory, you can
 * alter the thread's name, thread group, priority, daemon status,
 * etc. If a {@code ThreadFactory} fails to create a thread when asked
 * by returning null from {@code newThread}, the executor will
 * continue, but might not be able to execute any tasks. Threads
 * should possess the "modifyThread" {@code RuntimePermission}. If
 * worker threads or other threads using the pool do not possess this
 * permission, service may be degraded: configuration changes may not
 * take effect in a timely manner, and a shutdown pool may remain in a
 * state in which termination is possible but not completed.</dd>
 *
 * <dt>Keep-alive times</dt>
 *
 * <dd>If the pool currently has more than corePoolSize threads,
 * excess threads will be terminated if they have been idle for more
 * than the keepAliveTime (see {@link #getKeepAliveTime(TimeUnit)}).
 * This provides a means of reducing resource consumption when the
 * pool is not being actively used. If the pool becomes more active
 * later, new threads will be constructed. This parameter can also be
 * changed dynamically using method {@link #setKeepAliveTime(long,
 * TimeUnit)}.  Using a value of {@code Long.MAX_VALUE} {@link
 * TimeUnit#NANOSECONDS} effectively disables idle threads from ever
 * terminating prior to shut down. By default, the keep-alive policy
 * applies only when there are more than corePoolSize threads. But
 * method {@link #allowCoreThreadTimeOut(boolean)} can be used to
 * apply this time-out policy to core threads as well, so long as the
 * keepAliveTime value is non-zero. </dd>
 *
 * <dt>Queuing</dt>
 *
 * <dd>Any {@link BlockingQueue} may be used to transfer and hold
 * submitted tasks.  The use of this queue interacts with pool sizing:
 *
 * <ul>
 *
 * <li> If fewer than corePoolSize threads are running, the Executor
 * always prefers adding a new thread
 * rather than queuing.</li>
 *
 * <li> If corePoolSize or more threads are running, the Executor
 * always prefers queuing a request rather than adding a new
 * thread.</li>
 *
 * <li> If a request cannot be queued, a new thread is created unless
 * this would exceed maximumPoolSize, in which case, the task will be
 * rejected.</li>
 *
 * </ul>
 *
 * There are three general strategies for queuing:
 * <ol>
 *
 * <li> <em> Direct handoffs.</em> A good default choice for a work
 * queue is a {@link SynchronousQueue} that hands off tasks to threads
 * without otherwise holding them. Here, an attempt to queue a task
 * will fail if no threads are immediately available to run it, so a
 * new thread will be constructed. This policy avoids lockups when
 * handling sets of requests that might have internal dependencies.
 * Direct handoffs generally require unbounded maximumPoolSizes to
 * avoid rejection of new submitted tasks. This in turn admits the
 * possibility of unbounded thread growth when commands continue to
 * arrive on average faster than they can be processed.  </li>
 *
 * <li><em> Unbounded queues.</em> Using an unbounded queue (for
 * example a {@link LinkedBlockingQueue} without a predefined
 * capacity) will cause new tasks to wait in the queue when all
 * corePoolSize threads are busy. Thus, no more than corePoolSize
 * threads will ever be created. (And the value of the maximumPoolSize
 * therefore doesn't have any effect.)  This may be appropriate when
 * each task is completely independent of others, so tasks cannot
 * affect each others execution; for example, in a web page server.
 * While this style of queuing can be useful in smoothing out
 * transient bursts of requests, it admits the possibility of
 * unbounded work queue growth when commands continue to arrive on
 * average faster than they can be processed.  </li>
 *
 * <li><em>Bounded queues.</em> A bounded queue (for example, an
 * {@link ArrayBlockingQueue}) helps prevent resource exhaustion when
 * used with finite maximumPoolSizes, but can be more difficult to
 * tune and control.  Queue sizes and maximum pool sizes may be traded
 * off for each other: Using large queues and small pools minimizes
 * CPU usage, OS resources, and context-switching overhead, but can
 * lead to artificially low throughput.  If tasks frequently block (for
 * example if they are I/O bound), a system may be able to schedule
 * time for more threads than you otherwise allow. Use of small queues
 * generally requires larger pool sizes, which keeps CPUs busier but
 * may encounter unacceptable scheduling overhead, which also
 * decreases throughput.  </li>
 *
 * </ol>
 *
 * </dd>
 *
 * <dt>Rejected tasks</dt>
 *
 * <dd>New tasks submitted in method {@link #execute(Runnable)} will be
 * <em>rejected</em> when the Executor has been shut down, and also when
 * the Executor uses finite bounds for both maximum threads and work queue
 * capacity, and is saturated.  In either case, the {@code execute} method
 * invokes the {@link
 * RejectedExecutionHandler#rejectedExecution(Runnable, ThreadPoolExecutor)}
 * method of its {@link RejectedExecutionHandler}.  Four predefined handler
 * policies are provided:
 *
 * <ol>
 *
 * <li> In the default {@link ThreadPoolExecutor.AbortPolicy}, the
 * handler throws a runtime {@link RejectedExecutionException} upon
 * rejection. </li>
 *
 * <li> In {@link ThreadPoolExecutor.CallerRunsPolicy}, the thread
 * that invokes {@code execute} itself runs the task. This provides a
 * simple feedback control mechanism that will slow down the rate that
 * new tasks are submitted. </li>
 *
 * <li> In {@link ThreadPoolExecutor.DiscardPolicy}, a task that
 * cannot be executed is simply dropped.  </li>
 *
 * <li>In {@link ThreadPoolExecutor.DiscardOldestPolicy}, if the
 * executor is not shut down, the task at the head of the work queue
 * is dropped, and then execution is retried (which can fail again,
 * causing this to be repeated.) </li>
 *
 * </ol>
 *
 * It is possible to define and use other kinds of {@link
 * RejectedExecutionHandler} classes. Doing so requires some care
 * especially when policies are designed to work only under particular
 * capacity or queuing policies. </dd>
 *
 * <dt>Hook methods</dt>
 *
 * <dd>This class provides {@code protected} overridable
 * {@link #beforeExecute(Thread, Runnable)} and
 * {@link #afterExecute(Runnable, Throwable)} methods that are called
 * before and after execution of each task.  These can be used to
 * manipulate the execution environment; for example, reinitializing
 * ThreadLocals, gathering statistics, or adding log entries.
 * Additionally, method {@link #terminated} can be overridden to perform
 * any special processing that needs to be done once the Executor has
 * fully terminated.
 *
 * <p>If hook or callback methods throw exceptions, internal worker
 * threads may in turn fail and abruptly terminate.</dd>
 *
 * <dt>Queue maintenance</dt>
 *
 * <dd>Method {@link #getQueue()} allows access to the work queue
 * for purposes of monitoring and debugging.  Use of this method for
 * any other purpose is strongly discouraged.  Two supplied methods,
 * {@link #remove(Runnable)} and {@link #purge} are available to
 * assist in storage reclamation when large numbers of queued tasks
 * become cancelled.</dd>
 *
 * <dt>Finalization</dt>
 *
 * <dd>A pool that is no longer referenced in a program <em>AND</em>
 * has no remaining threads will be {@code shutdown} automatically. If
 * you would like to ensure that unreferenced pools are reclaimed even
 * if users forget to call {@link #shutdown}, then you must arrange
 * that unused threads eventually die, by setting appropriate
 * keep-alive times, using a lower bound of zero core threads and/or
 * setting {@link #allowCoreThreadTimeOut(boolean)}.  </dd>
 *
 * </dl>
 *
 * <p><b>Extension example</b>. Most extensions of this class
 * override one or more of the protected hook methods. For example,
 * here is a subclass that adds a simple pause/resume feature:
 *
 *  <pre> {@code
 * class PausableThreadPoolExecutor extends ThreadPoolExecutor {
 *   private boolean isPaused;
 *   private ReentrantLock pauseLock = new ReentrantLock();
 *   private Condition unpaused = pauseLock.newCondition();
 *
 *   public PausableThreadPoolExecutor(...) { super(...); }
 *
 *   protected void beforeExecute(Thread t, Runnable r) {
 *     super.beforeExecute(t, r);
 *     pauseLock.lock();
 *     try {
 *       while (isPaused) unpaused.await();
 *     } catch (InterruptedException ie) {
 *       t.interrupt();
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 *
 *   public void pause() {
 *     pauseLock.lock();
 *     try {
 *       isPaused = true;
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 *
 *   public void resume() {
 *     pauseLock.lock();
 *     try {
 *       isPaused = false;
 *       unpaused.signalAll();
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 * }}</pre>
 *
 * @since 1.5
 * @author Doug Lea
 */
public class ThreadPoolExecutor extends AbstractExecutorService {
    /**
     * The main pool control state, ctl, is an atomic integer packing
     * two conceptual fields
     *   workerCount, indicating the effective number of threads
     *   runState,    indicating whether running, shutting down etc
     *
     *   workerCount：表示有效线程数。
     *
     * In order to pack them into one int, we limit workerCount to
     * (2^29)-1 (about 500 million) threads rather than (2^31)-1 (2
     * billion) otherwise representable. If this is ever an issue in
     * the future, the variable can be changed to be an AtomicLong,
     * and the shift/mask constants below adjusted. But until the need
     * arises, this code is a bit faster and simpler using an int.
     *
     * 为了将它们打包成一个 int，我们将 workerCount 限制为
     * (2^29)-1（约 5 亿）个线程而不是 (2^31)-1 (2
     * 十亿）否则可代表。 如果这是一个问题
     * 未来，变量可以改成AtomicLong，
     * 和下面的移位/掩码常数已调整。 但是直到需要
     * 出现时，这段代码使用 int 会更快更简单。
     *
     * The workerCount is the number of workers that have been
     * permitted to start and not permitted to stop.  The value may be
     * transiently different from the actual number of live threads,
     * for example when a ThreadFactory fails to create a thread when
     * asked, and when exiting threads are still performing
     * bookkeeping before terminating. The user-visible pool size is
     * reported as the current size of the workers set.
     *
     * workerCount 是已完成的工人数
     * 允许启动，不允许停止。 该值可能是
     * 与实际活动线程数暂时不同，
     * 例如当一个 ThreadFactory 未能创建线程时
     * 询问，并且当退出线程仍在执行时
     * 终止前的簿记。 用户可见的池大小为
     * 报告为工人集的当前大小。
     *
     * The runState provides the main lifecycle control, taking on values:
     *
     *   RUNNING:  Accept new tasks and process queued tasks
     *   SHUTDOWN: Don't accept new tasks, but process queued tasks
     *   STOP:     Don't accept new tasks, don't process queued tasks,
     *             and interrupt in-progress tasks
     *   TIDYING:  All tasks have terminated, workerCount is zero,
     *             the thread transitioning to state TIDYING
     *             will run the terminated() hook method
     *   TERMINATED: terminated() has completed
     *
     *
     * runState 提供主要的生命周期控制，取值：
     *
     * RUNNING：接受新任务并处理排队任务
     * SHUTDOWN：不接受新任务，但处理排队的任务
     * STOP：不接受新任务，不处理排队任务，
     * 并中断正在进行的任务
     * TIDYING：所有任务都已终止，workerCount 为零，
     * 线程转换到状态 TIDYING
     * 将运行 terminate() 钩子方法
     * TERMINATED：terminated（）已完成
     *
     * The numerical order among these values matters, to allow
     * ordered comparisons. The runState monotonically increases over
     * time, but need not hit each state. The transitions are:
     *
     * 这些值之间的数字顺序很重要，以允许
     * 有序比较。 runState 单调递增
     * 时间，但不必击中每个状态。 过渡是：
     *
     * RUNNING -> SHUTDOWN
     *    On invocation of shutdown(), perhaps implicitly in finalize()
     * (RUNNING or SHUTDOWN) -> STOP
     *    On invocation of shutdownNow()
     * SHUTDOWN -> TIDYING
     *    When both queue and pool are empty
     * STOP -> TIDYING
     *    When pool is empty
     * TIDYING -> TERMINATED
     *    When the terminated() hook method has completed
     *
     * Threads waiting in awaitTermination() will return when the
     * state reaches TERMINATED.
     *
     * 在 awaitTermination() 中等待的线程将在状态达到 TERMINATED。
     *
     * Detecting the transition from SHUTDOWN to TIDYING is less
     * straightforward than you'd like because the queue may become
     * empty after non-empty and vice versa during SHUTDOWN state, but
     * we can only terminate if, after seeing that it is empty, we see
     * that workerCount is 0 (which sometimes entails a recheck -- see
     * below).
     *
     * 检测从 SHUTDOWN 到 TIDYING 的过渡较少
     * 比你想要的更简单，因为队列可能会变成
     * 在 SHUTDOWN 状态下非空后为空，反之亦然，但是
     * 我们只有在看到它是空的之后才能终止，我们看到
     * workerCount 为 0（有时需要重新检查——参见
     * 以下）。
     */
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    private static final int COUNT_BITS = Integer.SIZE - 3;
    private static final int CAPACITY   = (1 << COUNT_BITS) - 1;

    // runState is stored in the high-order bits
    private static final int RUNNING    = -1 << COUNT_BITS;
    private static final int SHUTDOWN   =  0 << COUNT_BITS;
    private static final int STOP       =  1 << COUNT_BITS;
    private static final int TIDYING    =  2 << COUNT_BITS;
    private static final int TERMINATED =  3 << COUNT_BITS;

    // Packing and unpacking ctl
    private static int runStateOf(int c)     { return c & ~CAPACITY; }
    private static int workerCountOf(int c)  { return c & CAPACITY; }
    private static int ctlOf(int rs, int wc) { return rs | wc; }

    /*
     * Bit field accessors that don't require unpacking ctl.
     * These depend on the bit layout and on workerCount being never negative.
     */

    private static boolean runStateLessThan(int c, int s) {
        return c < s;
    }

    private static boolean runStateAtLeast(int c, int s) {
        return c >= s;
    }

    private static boolean isRunning(int c) {
        return c < SHUTDOWN;
    }

    /**
     * Attempts to CAS-increment the workerCount field of ctl.
     */
    private boolean compareAndIncrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect + 1);
    }

    /**
     * Attempts to CAS-decrement the workerCount field of ctl.
     */
    private boolean compareAndDecrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect - 1);
    }

    /**
     * Decrements the workerCount field of ctl. This is called only on
     * abrupt termination of a thread (see processWorkerExit). Other
     * decrements are performed within getTask.
     */
    private void decrementWorkerCount() {
        do {} while (! compareAndDecrementWorkerCount(ctl.get()));
    }

    /**
     * The queue used for holding tasks and handing off to worker
     * threads.  We do not require that workQueue.poll() returning
     * null necessarily means that workQueue.isEmpty(), so rely
     * solely on isEmpty to see if the queue is empty (which we must
     * do for example when deciding whether to transition from
     * SHUTDOWN to TIDYING).  This accommodates special-purpose
     * queues such as DelayQueues for which poll() is allowed to
     * return null even if it may later return non-null when delays
     * expire.
     */
    private final BlockingQueue<Runnable> workQueue;

    /**
     * Lock held on access to workers set and related bookkeeping.
     * While we could use a concurrent set of some sort, it turns out
     * to be generally preferable to use a lock. Among the reasons is
     * that this serializes interruptIdleWorkers, which avoids
     * unnecessary interrupt storms, especially during shutdown.
     * Otherwise exiting threads would concurrently interrupt those
     * that have not yet interrupted. It also simplifies some of the
     * associated statistics bookkeeping of largestPoolSize etc. We
     * also hold mainLock on shutdown and shutdownNow, for the sake of
     * ensuring workers set is stable while separately checking
     * permission to interrupt and actually interrupting.
     */
    private final ReentrantLock mainLock = new ReentrantLock();

    /**
     * Set containing all worker threads in pool. Accessed only when
     * holding mainLock.
     */
    private final HashSet<Worker> workers = new HashSet<Worker>();

    /**
     * Wait condition to support awaitTermination
     */
    private final Condition termination = mainLock.newCondition();

    /**
     * Tracks largest attained pool size. Accessed only under
     * mainLock.
     */
    private int largestPoolSize;

    /**
     * Counter for completed tasks. Updated only on termination of
     * worker threads. Accessed only under mainLock.
     */
    private long completedTaskCount;

    /*
     * All user control parameters are declared as volatiles so that
     * ongoing actions are based on freshest values, but without need
     * for locking, since no internal invariants depend on them
     * changing synchronously with respect to other actions.
     */

    /**
     * Factory for new threads. All threads are created using this
     * factory (via method addWorker).  All callers must be prepared
     * for addWorker to fail, which may reflect a system or user's
     * policy limiting the number of threads.  Even though it is not
     * treated as an error, failure to create threads may result in
     * new tasks being rejected or existing ones remaining stuck in
     * the queue.
     *
     * We go further and preserve pool invariants even in the face of
     * errors such as OutOfMemoryError, that might be thrown while
     * trying to create threads.  Such errors are rather common due to
     * the need to allocate a native stack in Thread.start, and users
     * will want to perform clean pool shutdown to clean up.  There
     * will likely be enough memory available for the cleanup code to
     * complete without encountering yet another OutOfMemoryError.
     */
    private volatile ThreadFactory threadFactory;

    /**
     * Handler called when saturated or shutdown in execute.
     */
    private volatile RejectedExecutionHandler handler;

    /**
     * Timeout in nanoseconds for idle threads waiting for work.
     * Threads use this timeout when there are more than corePoolSize
     * present or if allowCoreThreadTimeOut. Otherwise they wait
     * forever for new work.
     * 。*线程在超过corePoolSize时使用此等待工作的空闲线程的超时时间(纳秒)超时*存在或允许CoreThreadTimeOut。否则，他们将永远等待新的工作。
     */
    private volatile long keepAliveTime;

    /**
     * If false (default), core threads stay alive even when idle.
     * If true, core threads use keepAliveTime to time out waiting
     * for work.
     */
    private volatile boolean allowCoreThreadTimeOut;

    /**
     * Core pool size is the minimum number of workers to keep alive
     * (and not allow to time out etc) unless allowCoreThreadTimeOut
     * is set, in which case the minimum is zero.
     */
    private volatile int corePoolSize;

    /**
     * Maximum pool size. Note that the actual maximum is internally
     * bounded by CAPACITY.
     */
    private volatile int maximumPoolSize;

    /**
     * The default rejected execution handler
     */
    private static final RejectedExecutionHandler defaultHandler =
        new AbortPolicy();

    /**
     * Permission required for callers of shutdown and shutdownNow.
     * We additionally require (see checkShutdownAccess) that callers
     * have permission to actually interrupt threads in the worker set
     * (as governed by Thread.interrupt, which relies on
     * ThreadGroup.checkAccess, which in turn relies on
     * SecurityManager.checkAccess). Shutdowns are attempted only if
     * these checks pass.
     *
     * All actual invocations of Thread.interrupt (see
     * interruptIdleWorkers and interruptWorkers) ignore
     * SecurityExceptions, meaning that the attempted interrupts
     * silently fail. In the case of shutdown, they should not fail
     * unless the SecurityManager has inconsistent policies, sometimes
     * allowing access to a thread and sometimes not. In such cases,
     * failure to actually interrupt threads may disable or delay full
     * termination. Other uses of interruptIdleWorkers are advisory,
     * and failure to actually interrupt will merely delay response to
     * configuration changes so is not handled exceptionally.
     */
    private static final RuntimePermission shutdownPerm =
        new RuntimePermission("modifyThread");

    /* The context to be used when executing the finalizer, or null. */
    private final AccessControlContext acc;

    /**
     * Class Worker mainly maintains interrupt control state for
     * threads running tasks, along with other minor bookkeeping.
     * This class opportunistically extends AbstractQueuedSynchronizer
     * to simplify acquiring and releasing a lock surrounding each
     * task execution.  This protects against interrupts that are
     * intended to wake up a worker thread waiting for a task from
     * instead interrupting a task being run.  We implement a simple
     * non-reentrant mutual exclusion lock rather than use
     * ReentrantLock because we do not want worker tasks to be able to
     * reacquire the lock when they invoke pool control methods like
     * setCorePoolSize.  Additionally, to suppress interrupts until
     * the thread actually starts running tasks, we initialize lock
     * state to a negative value, and clear it upon start (in
     * runWorker).
     */
    private final class Worker
        extends AbstractQueuedSynchronizer
        implements Runnable
    {
        /**
         * This class will never be serialized, but we provide a
         * serialVersionUID to suppress a javac warning.
         */
        private static final long serialVersionUID = 6138294804551838833L;

        /** Thread this worker is running in.  Null if factory fails. */
        final Thread thread;
        /** Initial task to run.  Possibly null. */
        Runnable firstTask;
        /** Per-thread task counter */
        volatile long completedTasks;

        /***
         *
         * Worker类继承了AQS，使用AQS来实现独占锁的功能。
         * 为什么不使用ReentrantLock来实现？可以看到tryAcquire方法，他是不允许重入的，
         * 而ReentrantLock是允许可重入的：
         * 1、lock方法一旦获取独占锁，表示当前线程正在执行任务中；
         * 2、如果正在执行任务，则不应该中断线程；
         * 3、如果该线程现在不是独占锁的状态，也就是空闲状态，说明它没有处理任务，这时可以对该线程进行中断；
         * 4、线程池中执行shutdown方法或tryTerminate方法时会调用interruptIdleWorkers方法来中断空闲线程，
         * interruptIdleWorkers方法会使用tryLock方法来判断线程池中的线程是否是空闲状态；
         * 5、之所以设置为不可重入的，是因为在任务调用setCorePoolSize这类线程池控制的方法时，不
         * 会中断正在运行的线程所以，Worker继承自AQS，用于判断线程是否空闲以及是否处于被中断。
         *
         *
         * 问题： Worker作为一个Runnable ，交给自己的 thread属性执行 ，为什么Worker要基于AQS实现锁的功能？
         *
         */
        Worker(Runnable firstTask) {
            /**
             * Worker类继承了AQS并实现了Runnable接口,把state设置为-1，,阻止中断直到调用runWorker方法；
             *  因为AQS默认state是0，如果刚创建一个Worker对象，还没有执行任务时，这时候不应该被中断
             *
             */
            setState(-1); // inhibit interrupts until runWorker 禁止中断直到 runWorker
            /**
             * firstTask用来保存传入的任务，thread是在调用构造方法是通过ThreadFactory来创建的线程，是用来处理任务的线程。
             */
            this.firstTask = firstTask;
            /**
             * 创建一个线程，newThread方法传入的参数是this，因为Worker本身继承了Runnable接口，也就是一个线程；
             * 所以一个Worker对象在启动的时候会调用Worker类中run方法
             */
            this.thread = getThreadFactory().newThread(this);
        }

        /** Delegates main run loop to outer runWorker  */
        public void run() {
            runWorker(this);
        }

        // Lock methods
        //
        // The value 0 represents the unlocked state.
        // The value 1 represents the locked state.

        protected boolean isHeldExclusively() {
            return getState() != 0;
        }

        protected boolean tryAcquire(int unused) {

            /**
             * cas修改state，不可重入，只有一个线程获取成功，且获取成功的线程不可再次获取。
             * state根据0来判断，所以worker构造方法中假尼姑state设置为-1 是为了禁止在执行任务前对线程进行中断。
             * 因此在runWorker方法中会先调用worker对象的unlock方法将state设置为0.
             *
             * Worker对象的tryAcquire方法是在 ThreadPoolExecutor的interruptIdleWorkers方法和tryTerminate方法中 调用了interruptIdleWorkers
             * 在这个interruptIdleWorkers 方法中 ，遍历每一个Woker对象 调用其tryLock方法，如果获取成功则调用 Worker对象的Thread属性的interrupt方法 ，
             * 从而实现对woker线程进行中断。
             *
             * 那么问题是： 为什么 在终止 Woker的Thread线程之前需要先获取到Worker对象的AQS锁的呢？
             *
             *
             */
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        protected boolean tryRelease(int unused) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        public void lock()        { acquire(1); }
        public boolean tryLock()  { return tryAcquire(1); }
        public void unlock()      { release(1); }
        public boolean isLocked() { return isHeldExclusively(); }

        void interruptIfStarted() {
            Thread t;
            if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
                try {
                    t.interrupt();
                } catch (SecurityException ignore) {
                }
            }
        }
    }

    /*
     * Methods for setting control state
     */

    /**
     * Transitions runState to given target, or leaves it alone if
     * already at least the given target.
     *
     * @param targetState the desired state, either SHUTDOWN or STOP
     *        (but not TIDYING or TERMINATED -- use tryTerminate for that)
     */
    private void advanceRunState(int targetState) {
        for (;;) {
            int c = ctl.get();
            if (runStateAtLeast(c, targetState) ||
                ctl.compareAndSet(c, ctlOf(targetState, workerCountOf(c))))
                break;
        }
    }

    /**
     * Transitions to TERMINATED state if either (SHUTDOWN and pool
     * and queue empty) or (STOP and pool empty).  If otherwise
     * eligible to terminate but workerCount is nonzero, interrupts an
     * idle worker to ensure that shutdown signals propagate. This
     * method must be called following any action that might make
     * termination possible -- reducing worker count or removing tasks
     * from the queue during shutdown. The method is non-private to
     * allow access from ScheduledThreadPoolExecutor.
     */
    final void tryTerminate() {
        for (;;) {
            int c = ctl.get();
            if (isRunning(c) ||
                runStateAtLeast(c, TIDYING) ||
                (runStateOf(c) == SHUTDOWN && ! workQueue.isEmpty()))
                return;
            if (workerCountOf(c) != 0) { // Eligible to terminate
                interruptIdleWorkers(ONLY_ONE);
                return;
            }

            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                    try {
                        terminated();
                    } finally {
                        ctl.set(ctlOf(TERMINATED, 0));
                        termination.signalAll();
                    }
                    return;
                }
            } finally {
                mainLock.unlock();
            }
            // else retry on failed CAS
        }
    }

    /*
     * Methods for controlling interrupts to worker threads.
     */

    /**
     * If there is a security manager, makes sure caller has
     * permission to shut down threads in general (see shutdownPerm).
     * If this passes, additionally makes sure the caller is allowed
     * to interrupt each worker thread. This might not be true even if
     * first check passed, if the SecurityManager treats some threads
     * specially.
     */
    private void checkShutdownAccess() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(shutdownPerm);
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                for (Worker w : workers)
                    security.checkAccess(w.thread);
            } finally {
                mainLock.unlock();
            }
        }
    }

    /**
     * Interrupts all threads, even if active. Ignores SecurityExceptions
     * (in which case some threads may remain uninterrupted).
     */
    private void interruptWorkers() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers)
                w.interruptIfStarted();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Interrupts threads that might be waiting for tasks (as
     * indicated by not being locked) so they can check for
     * termination or configuration changes. Ignores
     * SecurityExceptions (in which case some threads may remain
     * uninterrupted).
     *
     * @param onlyOne If true, interrupt at most one worker. This is
     * called only from tryTerminate when termination is otherwise
     * enabled but there are still other workers.  In this case, at
     * most one waiting worker is interrupted to propagate shutdown
     * signals in case all threads are currently waiting.
     * Interrupting any arbitrary thread ensures that newly arriving
     * workers since shutdown began will also eventually exit.
     * To guarantee eventual termination, it suffices to always
     * interrupt only one idle worker, but shutdown() interrupts all
     * idle workers so that redundant workers exit promptly, not
     * waiting for a straggler task to finish.
     */
    private void interruptIdleWorkers(boolean onlyOne) {
        /**
         * 中断可能正在等待任务的线程（如未锁定所示），以便它们可以检查终止或配置更改。 忽略 SecurityExceptions（在这种情况下，某些线程可能保持不间断）。
         * 参数：
         * onlyOne - 如果为 true，则最多中断一名工作人员。 这仅在以其他方式启用终止但仍有其他工作人员时从 tryTerminate 调用。
         * 在这种情况下，在所有线程当前都在等待的情况下，最多会中断一个等待的工作人员以传播关闭信号。
         * 中断任意线程可确保自关闭开始后新到达的工作人员最终也将退出。 为了保证最终终止，总是只中断一个空闲的worker就足够了，但是shutdown()会中断所有空闲的worker，这样多余的worker就会立即退出，而不是等待一个落后的任务完成。
         *
         */
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers) {
                Thread t = w.thread;
                if (!t.isInterrupted() && w.tryLock()) {
                    try {
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    } finally {
                        w.unlock();
                    }
                }
                if (onlyOne)
                    break;
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Common form of interruptIdleWorkers, to avoid having to
     * remember what the boolean argument means.
     */
    private void interruptIdleWorkers() {
        interruptIdleWorkers(false);
    }

    private static final boolean ONLY_ONE = true;

    /*
     * Misc utilities, most of which are also exported to
     * ScheduledThreadPoolExecutor
     */

    /**
     * Invokes the rejected execution handler for the given command.
     * Package-protected for use by ScheduledThreadPoolExecutor.
     */
    final void reject(Runnable command) {
        handler.rejectedExecution(command, this);
    }

    /**
     * Performs any further cleanup following run state transition on
     * invocation of shutdown.  A no-op here, but used by
     * ScheduledThreadPoolExecutor to cancel delayed tasks.
     */
    void onShutdown() {
    }

    /**
     * State check needed by ScheduledThreadPoolExecutor to
     * enable running tasks during shutdown.
     *
     * @param shutdownOK true if should return true if SHUTDOWN
     */
    final boolean isRunningOrShutdown(boolean shutdownOK) {
        int rs = runStateOf(ctl.get());
        return rs == RUNNING || (rs == SHUTDOWN && shutdownOK);
    }

    /**
     * Drains the task queue into a new list, normally using
     * drainTo. But if the queue is a DelayQueue or any other kind of
     * queue for which poll or drainTo may fail to remove some
     * elements, it deletes them one by one.
     */
    private List<Runnable> drainQueue() {
        BlockingQueue<Runnable> q = workQueue;
        ArrayList<Runnable> taskList = new ArrayList<Runnable>();
        q.drainTo(taskList);
        if (!q.isEmpty()) {
            for (Runnable r : q.toArray(new Runnable[0])) {
                if (q.remove(r))
                    taskList.add(r);
            }
        }
        return taskList;
    }

    /*
     * Methods for creating, running and cleaning up after workers
     */

    /**
     * Checks if a new worker can be added with respect to current
     * pool state and the given bound (either core or maximum). If so,
     * the worker count is adjusted accordingly, and, if possible, a
     * new worker is created and started, running firstTask as its
     * first task. This method returns false if the pool is stopped or
     * eligible to shut down. It also returns false if the thread
     * factory fails to create a thread when asked.  If the thread
     * creation fails, either due to the thread factory returning
     * null, or due to an exception (typically OutOfMemoryError in
     * Thread.start()), we roll back cleanly.
     *
     * @param firstTask the task the new thread should run first (or
     * null if none). Workers are created with an initial first task
     * (in method execute()) to bypass queuing when there are fewer
     * than corePoolSize threads (in which case we always start one),
     * or when the queue is full (in which case we must bypass queue).
     * Initially idle threads are usually created via
     * prestartCoreThread or to replace other dying workers.
     *
     * @param core if true use corePoolSize as bound, else
     * maximumPoolSize. (A boolean indicator is used here rather than a
     * value to ensure reads of fresh values after checking other pool
     * state).
     * @return true if successful
     */
    private boolean addWorker(Runnable firstTask, boolean core) {
        retry:
        for (;;) {
            /**
             * 由于线程执行过程中，各种情况都有可能处于，通过for自旋的方式来保证worker的增加；
             */
            int c = ctl.get();
            //获取线程池运行状态
            int rs = runStateOf(c);

            // Check if queue empty only if necessary.
            /**
             *
             * 如果rs >= SHUTDOWN, 则表示此时不再接收新任务；
             * 接下来是三个条件 通过 && 连接，只要有一个任务不满足，就返回false；
             * 1.rs == SHUTDOWN，表示关闭状态，不再接收提交的任务，但却可以继续处理阻塞队列中已经保存的任务；
             * 2.fisrtTask为空
             * 3.Check if queue empty only if necessary.
             */

            if (rs >= SHUTDOWN && !(rs == SHUTDOWN && firstTask == null && !workQueue.isEmpty())) {
                return false;
            }

            for (;;) {
                //获取线程池的线程数
                int wc = workerCountOf(c);
                /**
                 * 如果线程数 >= CAPACITY， 也就是ctl的低29位的最大值，则返回false；
                 * 这里的core用来判断 限制线程数量的上限是corePoolSize还是maximumPoolSize；
                 * 如果core是ture表示根据corePoolSize来比较；
                 * 如果core是false表示根据maximumPoolSize来比较；
                 */
                if (wc >= CAPACITY ||
                    wc >= (core ? corePoolSize : maximumPoolSize))
                    return false;
                /**
                 * 在这里 对workCount数量进行增加。
                 * 从这里我们看到 实际上是先增加 workcount 然后才会创建 Worker线程。
                 *
                 * 某种意义上workcount可以看到 start的 worker数量。可以理解为正在运行的worker数量。
                 *
                 * Worker的run方法执行完之后 会执行 java.util.concurrent.ThreadPoolExecutor#runWorker(java.util.concurrent.ThreadPoolExecutor.Worker)
                 * 在这个runWorker方法的finally中 会执行ThreadPoolExecutor#processWorkerExit(java.util.concurrent.ThreadPoolExecutor.Worker, boolean)
                 * runWorker的finally意味着 Worker线程的退出。 在processWorkerExit 方法中会通过 decrementWorkerCount 方法对workerCount进行减少。
                 *
                 * 也就是说workCount=1 并不意味着有一个线程正在执行任务或者阻塞等待任务执行，可能这个线程正在执行退出尚未对workcount做减法。
                 *
                 */
                if (compareAndIncrementWorkerCount(c))
                    break retry;
                c = ctl.get();  // Re-read ctl
                //如果当前运行的状态不等于rs，说明线程池的状态已经改变了，则返回第一个for循环继续执行
                if (runStateOf(c) != rs)
                    continue retry;
                // else CAS failed due to workerCount change; retry inner loop
            }
        }

        boolean workerStarted = false;
        boolean workerAdded = false;
        Worker w = null;
        try {
            //根据firstTask来创建Worker对象
            w = new Worker(firstTask);
            //每一个Worker对象都会创建一个线程
            final Thread t = w.thread;
            if (t != null) {
                final ReentrantLock mainLock = this.mainLock;
                mainLock.lock();
                try {
                    // Recheck while holding lock.
                    // Back out on ThreadFactory failure or if
                    // shut down before lock acquired.
                    // 持有锁时重新检查。
                    // 如果 ThreadFactory 失败或如果
                    // 在获取锁之前关闭。
                    // 获取线程池的状态
                    int rs = runStateOf(ctl.get());

                    /**
                     * 线程池的状态小于Shutdown，表示线程池处于Running状态。
                     * 如果rs是running状态或rs是shutdown状态并且firstTask为null，则向线程池中添加线程。
                     * 因为在shutdown状态时不会添加新任务，但是还会处理workQueue中的任务。
                     * firstTask==null表示 任务已经在之前被添加到了队列中。
                     */
                    if (rs < SHUTDOWN ||
                        (rs == SHUTDOWN && firstTask == null)) {
                        if (t.isAlive()) // precheck that t is startable
                            throw new IllegalThreadStateException();
                        //workers是一个hashSet
                        workers.add(w);
                        int s = workers.size();
                        //largestPoolSize记录线程池中出现的最大的线程数量
                        if (s > largestPoolSize)
                            largestPoolSize = s;
                        workerAdded = true;
                    }
                } finally {
                    mainLock.unlock();
                }
                // 如果成功添加了 Worker，就可以启动 Worker 了
                if (workerAdded) {
                    t.start();
                    workerStarted = true;
                }
            }
        } finally {
            if (! workerStarted)
                addWorkerFailed(w);
        }
        return workerStarted;
    }

    /**
     * Rolls back the worker thread creation.
     * - removes worker from workers, if present
     * - decrements worker count
     * - rechecks for termination, in case the existence of this
     *   worker was holding up termination
     */
    private void addWorkerFailed(Worker w) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (w != null)
                workers.remove(w);
            decrementWorkerCount();
            tryTerminate();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Performs cleanup and bookkeeping for a dying worker. Called
     * only from worker threads. Unless completedAbruptly is set,
     * assumes that workerCount has already been adjusted to account
     * for exit.  This method removes thread from worker set, and
     * possibly terminates the pool or replaces the worker if either
     * it exited due to user task exception or if fewer than
     * corePoolSize workers are running or queue is non-empty but
     * there are no workers.
     *
     * @param w the worker
     * @param completedAbruptly if the worker died due to user exception
     */
    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        /**
         * 如果completedAbruptly为true，则说明线程执行时出现异常，需要将workerCount数量减一
         * 如果completedAbruptly为false，说明在getTask方法中已经对workerCount进行减一，这里不用再减
         */

        if (completedAbruptly) // If abrupt, then workerCount wasn't adjusted
            decrementWorkerCount();

        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            //统计完成的任务数
            completedTaskCount += w.completedTasks;
            //从workers中移除，也就表示从线程池中移除一个工作线程
            workers.remove(w);
        } finally {
            mainLock.unlock();
        }
        //钩子函数，根据线程池的状态来判断是否结束线程池
        tryTerminate();

        int c = ctl.get();
        /**
         * 当前线程是RUNNING或SHUTDOWN时，如果worker是异常结束，那么会直接addWorker；
         * 如果allowCoreThreadTimeOut=true，那么等待队列有任务，至少保留一个worker；
         * 如果allowCoreThreadTimeOut=false，workerCount少于coolPoolSize
         */
        if (runStateLessThan(c, STOP)) {
            if (!completedAbruptly) {
                int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
                if (min == 0 && ! workQueue.isEmpty())
                    min = 1;
                if (workerCountOf(c) >= min)
                    return; // replacement not needed
            }
            addWorker(null, false);
        }
    }

    /**
     * Performs blocking or timed wait for a task, depending on
     * current configuration settings, or returns null if this worker
     * must exit because of any of:
     * 1. There are more than maximumPoolSize workers (due to
     *    a call to setMaximumPoolSize).
     * 2. The pool is stopped.
     * 3. The pool is shutdown and the queue is empty.
     * 4. This worker timed out waiting for a task, and timed-out
     *    workers are subject to termination (that is,
     *    {@code allowCoreThreadTimeOut || workerCount > corePoolSize})
     *    both before and after the timed wait, and if the queue is
     *    non-empty, this worker is not the last thread in the pool.
     *
     * 根据任务执行阻塞或定时等待
     * 当前配置设置，如果此工作人员返回 null
     * 必须退出，因为：
     * 1. 有超过 maximumPoolSize 个工人（由于
     * 调用 setMaximumPoolSize)。
     * 2. 池停止。
     * 3. 池关闭，队列为空。
     * 4. 这个worker等待任务超时，超时
     * 工人可能被解雇（即，
     * {@code allowCoreThreadTimeOut || workerCount > corePoolSize})
     * 在定时等待之前和之后，如果队列是
     * 非空，这个worker不是池中的最后一个线程。
     *
     * @return task, or null if the worker must exit, in which case
     *         workerCount is decremented
     */
    private Runnable getTask() {
        //timeout变量的值表示上次从阻塞队列中获取任务是否超时
        boolean timedOut = false; // Did the last poll() time out?

        for (;;) {
            int c = ctl.get();
            int rs = runStateOf(c);

            /**
             * 如果rs >= SHUTDOWN，表示线程池非RUNNING状态，需要再次判断：
             * 1、rs >= STOP ，线程池是否正在STOP
             * 2、阻塞队列是否为空
             * 满足上述条件之一，则将workCount减一，并返回null；
             * 因为如果当前线程池的状态处于STOP及以上或队列为空，不能从阻塞队列中获取任务；
             */

            // Check if queue empty only if necessary.
            if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {
                decrementWorkerCount();
                return null;
            }

            int wc = workerCountOf(c);


            /**
             * timed变量用于判断是否需要进行超时控制；
             * allowCoreThreadTimeOut默认是false，也就是核心线程不允许进行超时；
             * wc > corePoolSize，表示当前线程数大于核心线程数量；
             * 对于超过核心线程数量的这些线程，需要进行超时控制；
             */

            // Are workers subject to culling?
            boolean timed = allowCoreThreadTimeOut || wc > corePoolSize;


            /**
             * wc > maximumPoolSize的情况是因为可能在此方法执行阶段同时执行了 setMaximumPoolSize方法；
             * timed && timedOut 如果为true，表示当前操作需要进行超时控制，并且上次从阻塞队列中获取任务发生了超时；
             * 接下来判断，如果有效咸亨数量大于1，或者workQueue为空，那么将尝试workCount减1；
             * 如果减1失败，则返回重试；
             * 如果wc==1时，也就说明当前线程是线程池中的唯一线程了；
             *
             *
             * 注意：第二个if判断，目的是为了控制线程池的有效线程数量。
             * 有上文分析得到，在execute方法时，如果当前线程池的线程数量超过coolPoolSize且小于maxmumPoolSize，
             * 并且阻塞队列已满时，则可以通过增加工作线程。但是如果工作线程在超时时间内没有获取到任务，timeOut=true，
             * 说明workQueue为空，也就说当前线程池不需要那么多线程来执行任务了，可以把多于的corePoolSize数量的线程销毁掉，
             * 保证线程数量在corePoolSize即可。
             * 什么时候会销毁线程？当然是runWorker方法执行完后，也就是Worker中的run方法执行完后，由JVM自动回收。
             *
             *
             */

            if ((wc > maximumPoolSize || (timed && timedOut))
                && (wc > 1 || workQueue.isEmpty())) {
                if (compareAndDecrementWorkerCount(c))
                    return null;
                continue;
            }

            /**
             * timed为trure，则通过workQueue的poll方法进行超时控制，如果在keepAliveTime时间内没有获取任务，则返回null；
             * 否则通过take方法，如果队列为空，则take方法会阻塞直到队列中不为空；
             *
             */
            try {
                Runnable r = timed ? workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) : workQueue.take();
                if (r != null)
                    return r;
                //如果r==null，说明已经超时了，timedOut = true;
                timedOut = true;
            } catch (InterruptedException retry) {
                //如果获取任务时当前线程发生了中断，则将timedOut = false;
                timedOut = false;
            }
        }
    }

    /**
     * Main worker run loop.  Repeatedly gets tasks from queue and
     * executes them, while coping with a number of issues:
     *
     * 主工作者运行循环。 反复从队列中获取任务并
     * 执行它们，同时处理一些问题：
     *
     * 1. We may start out with an initial task, in which case we
     * don't need to get the first one. Otherwise, as long as pool is
     * running, we get tasks from getTask. If it returns null then the
     * worker exits due to changed pool state or configuration
     * parameters.  Other exits result from exception throws in
     * external code, in which case completedAbruptly holds, which
     * usually leads processWorkerExit to replace this thread.
     *
     * 1. 我们可能从一个初始任务开始(比如创建Worker的时候给这个worker传递了一个任务)，在这种情况下，我们
     * 不需要从队列中获得第一个。 否则，只要池是
     * 运行时，我们从 getTask 获取任务。 如果它返回 null 然后
     * 由于更改池状态或配置，工作人员退出
     * 参数。 其他退出是由异常引发引起的
     * 外部代码，在这种情况下 completedAbruptly 成立，其中
     * 通常会导致 processWorkerExit 替换这个线程。
     *
     * 2. Before running any task, the lock is acquired to prevent
     * other pool interrupts while the task is executing, and then we
     * ensure that unless pool is stopping, this thread does not have
     * its interrupt set.
     *
     * 2. 在运行任何任务之前，获取锁以防止
     * 其他池在任务执行时中断，然后我们
     * 确保除非池正在停止，否则该线程没有
     * 它的中断集。
     *
     * 3. Each task run is preceded by a call to beforeExecute, which
     * might throw an exception, in which case we cause thread to die
     * (breaking loop with completedAbruptly true) without processing
     * the task.
     *
     * 3. 每个任务运行之前都会调用 beforeExecute，它
     * 可能会抛出异常，在这种情况下我们会导致线程死亡
     * (用completedAbruptly true 中断循环) 没有处理
     * 任务。
     *
     * 4. Assuming beforeExecute completes normally, we run the task,
     * gathering any of its thrown exceptions to send to afterExecute.
     * We separately handle RuntimeException, Error (both of which the
     * specs guarantee that we trap) and arbitrary Throwables.
     * Because we cannot rethrow Throwables within Runnable.run, we
     * wrap them within Errors on the way out (to the thread's
     * UncaughtExceptionHandler).  Any thrown exception also
     * conservatively causes thread to die.
     *
     * 4.假设beforeExecute正常完成，我们运行任务，
     * 收集任何抛出的异常以发送到 afterExecute。
     * 我们分别处理 RuntimeException、Error（这两个
     * 规范保证我们捕获）和任意 Throwables。
     * 因为我们不能在 Runnable.run 中重新抛出 Throwables，所以我们
     * 在出路时将它们包装在错误中（到线程的
     * 未捕获异常处理程序）。 任何抛出的异常也
     * 保守地导致线程死亡。
     *
     * 5. After task.run completes, we call afterExecute, which may
     * also throw an exception, which will also cause thread to
     * die. According to JLS Sec 14.20, this exception is the one that
     * will be in effect even if task.run throws.
     *
     * 5. task.run完成后，我们调用afterExecute，这可能
     * 也会抛出异常，这也会导致线程
     * 死。 根据 JLS Sec 14.20，这个例外是
     * 即使 task.run 抛出，也会生效。
     *
     * The net effect of the exception mechanics is that afterExecute
     * and the thread's UncaughtExceptionHandler have as accurate
     * information as we can provide about any problems encountered by
     * user code.
     *
     * 异常机制的最终效果是 afterExecute
     * 和线程的 UncaughtExceptionHandler 一样准确
     * 我们可以提供的关于遇到的任何问题的信息
     * 用户代码。
     *
     *
     * @param w the worker
     */
    final void runWorker(Worker w) {
        //https://juejin.cn/post/6844903920511238158
        Thread wt = Thread.currentThread();
        Runnable task = w.firstTask;
        w.firstTask = null;
        /**
         * 具体为什么先调用unlock 参考Worker的构造函数对 state的解释，以及 Worker的tryAcquire方法。
         * 简单而言就是 在创建Woker对象的时候 会将AQS的state设置为-1. 设置为-1的目的是为了禁止在runWoker之前对 Worker的Thread进行中断。
         * 因为在 ThreadPoolExecutor的 interruptIdleWorkers方法中 会遍历每一个Woker 调用woker对象的tryAcquire方法 获取AQS锁，如果获取成功了
         * 则调用Woker对象的Thread属性的interrupt方法对线程进行中断。
         * tryAcquire方法中获取锁的条件是 期望state=0，并将其设置为1 。也就是说 state！=0的时候不允许中断Woker的Thread。
         *
         * 因此里主要是 通过unlock方法将 state设置为 0.
         *
         * 而且 runWoker内部是一个While循环， 不断从 队列中取出任务执行。 值得注意的是  while内部 取出任务之后 总是 先 lock，然后再执行任务。
         * 为什么 lock不放置在while的外部呢？ lock的作用又是什么呢？
         *
         * while中执行了woker的lock，则会导致state不为0， Worker的tryAcquire方法中只有当state=0的时候才会获取锁成功，这就意味着 Woker实现的锁是一个
         * 独占式的且非重入的锁。只能被一个线程一次获取。 一旦执行任务的线程获取到了lock的锁，那么 其他线程通过ThreadPoolExecutor对象执行 interruptIdleWorkers
         * 来终止当前已经 取出了任务的线程的时候必须要先 使用Woker.tryLock 因此也就实现了如果Worker正在执行任务，则Worker不允许中断。
         *
         * 问题： 为什么Worker正在执行任务时 不允许中断Woker的线程呢？ 暂时不清楚
         *
         */
        w.unlock(); // allow interrupts
        //突然完成
        boolean completedAbruptly = true;
        try {
            /**
             * 注意这里的getTask ，如果getTask为null 那么就会导致当前work线程退出while循环，也就是执行完成了。
             * 当你调用了shutDownNow的时候 会将线程池的状态改为 stop，getTask内部for 死循环 从队列中获取任务，同时每次会检查线程池的状态
             * 如果线程池是stop状态，则返回null。导致这个work线程退出。 这也就实现了 关闭线程池
             */
            while (task != null || (task = getTask()) != null) {
                /**
                 *其中 Worker的runWorker方法中 在执行任务之前使用了 lock方法获取锁，任务执行结束之后再释放锁。这个锁的主要作用就是
                 * 避免线程池关闭线程时对正在执行任务的线程进行中断操作。
                 *
                 * 基于以上内容有两个问题：
                 * （1） 如果一个线程 正在运行，他不会主动检查线程的中断状态， 也不会执行能够抛出中断异常的阻塞方法， 那么
                 * 其他线程对这个线程发起中断 ，这个被中断的线程 应该感知不到 也不会退出线程，中断是一种协作机制，其他线程发起中断如果我不主动检测是感知不到中断，也不会退出线程。 我的这个理解是否正确？
                 *
                 * （2）为什么线程池中的线程 在执行任务的时候 要避免 这个线程被中断？
                 *按照我的理解，基于第一点的原因，我觉的 正在执行任务的线程允许中断也无妨，因为从源码的内容来看，
                 * 线程能否响应这个中断 取决于 用户的任务 task的run方法中是否 存在对中断的主动检查 以及是否会执行能够抛出中断异常的方法。
                 *
                 * 我们知道中断主要是 针对 阻塞的线程或者是主动检查中断状态的线程。 试想 ，如果当前Worker线程刚从 队列的poll方法恢复执行，然后这个时候发起了中断。
                 * 那么worker执行完之后又通过getTask取数据，但是在getTask中我们没有看到 主动检查中断状态的代码，那么这个线程能否感知到之前发生的中断呢？答案是可以看到，像sleep等阻塞方法
                 * 在进入之前会检查中断标记位，如果被中断立即抛出中断异常 不会再sleep
                 *
                 *
                 * 问题： 如果一个线程正在运行，然后其他线程对其进行了中断，然后这个线程调用了能够抛出中断异常的阻塞等待方法，那么这个线程能否被中断？ 主要是先对其发起中断调用，然后阻塞在能够抛出中断异常的方法处
                 *      // 比如：线程B 先对当前线程A发起了中断，那么当前线程执行sleep的时候能否响应中断？
                       //如果能响应中断的话 是立即响应中断还是 等待60秒后恢复执行时响应中断？
                 *     //答案是： 立即响应中断，也就是在sleep进入的时候发现中断标记位为true就抛出中断异常
                 *            CountDownLatch countDownLatch = new CountDownLatch(1);
                 *
                 *         Thread thread = new Thread(new Runnable() {
                 *             @Override
                 *             public void run() {
                 *                 System.out.println("runing");
                 *
                 *                 while (running) {//volatile
                 *
                 *                 }
                 *                 try {
                 *                     //问题： 线程B 先对当前线程A发起了中断，那么当前线程执行sleep的时候能否响应中断？
                 *                     //如果能响应中断的话 是立即响应中断还是 等待60秒后恢复执行时响应中断？
                 *                     //答案是： 立即响应中断，也就是在sleep进入的时候发现中断标记位为true就抛出中断异常
                 *                     Thread.sleep(1000 * 60);
                 *                 } catch (InterruptedException e) {
                 *
                 *                     e.printStackTrace();
                 *                 }
                 *             }
                 *         });
                 *         thread.start();
                 *         //先对线程进行中断
                 *         thread.interrupt();
                 *         Thread.sleep(10);
                 *         running = false;
                 * //在睡眠之前先检查是否已经发生了中断，若是则抛出中断异常
                 *
                 *
                 * ---------------------------------------------------------
                 *
                 * 说明1： 关于lock我们说是为了 禁止对正在执行任务的线程进行中断。 实际上这个 是否lock还可以做另外一种用处：就是判断线程是否active。
                 * 线程池中的线程可能有三种状态：（1）阻塞在take （2）正在执行任务 已经lock（3）处于take-> 没有执行任务 或者执行完任务尚未take之间的瞬态。
                 *
                 * ThreadPoolExecutor的getActiveCount：方法是返回 正在执行任务的线程的数量，他就是遍历每一个worker，看这个worker是否locked，如果是就认为正在执行任务
                 * ThreadPoolExecutor的gePoolSize是返回线程的数量，显然这和 getActiveCount有区别。
                 *
                 * 在Dubbo中提供了一种线程池实现 EagerThreadPoolExecutor， JUC的ThreadPoolExecutor标准实现是：提交任务时先判断线程池是否核心线程已满，如果没有则创建新的核心线程执行任务
                 * 如果核心线程已满，则入队，如果队列已满导致入队不成功则创建新的worker线程，如果worker线程已满创建失败则抛出异常。 也就是说
                 * JUC的队列满了之后才会开启新的线程来处理任务（前提是核心线程已满的情况下，且线程池线程数量没超过最大线程数量）
                 *
                 * EagerThreadPoolExecutor 当线程池核心线程已满，新来的任务不会被放入线程池队列，而是会开启新线程来执行处理任务。 这个是如何做到的呢？
                 * 其实就是EagerThreadPoolExecutor使用了自定义的TaskQueue，这个TaskQueue重写了队列的offer方法，
                 * TaskQueue在入队的时候发现 如果当前线程池的线程数小于最大线程数 则返回false表示入队失败，从而 创建新的线程。
                 * 创建新的线程的时候如果发现线程池中线程已经到了最大值 导致创建也失败了就会抛出异常  这个时候我们捕获这个异常 将任务放入队列中。具体参考源码
                 *
                 *
                 *
                 *
                 *
                 */
                w.lock();
                // If pool is stopping, ensure thread is interrupted;
                // if not, ensure thread is not interrupted.  This
                // requires a recheck in second case to deal with
                // shutdownNow race while clearing interrupt
                //如果池停止，请确保线程中断；
                //如果没有，请确保线程没有中断。这。
                //第二例需要复核处理。
                //清除中断时的Shudown Now竞争

                /**
                 * Thread.interrupted()//判断是否被中断，并清除当前中断状态
                 * isInterrupted:判断是否被中断
                 *
                 * 如果线程池正在停止，那么要保证当前线程时中断状态；
                 * 如果不是的话，则要保证当前线程不是中断状态
                 * 1.runStateAtLeast(ctl.get(), STOP)：runStateAtLeast 方法用于检查线程池的运行状态是否至少为 STOP。
                 * 如果线程池的状态达到了 STOP 或更高级别，说明线程池已经停止或正在停止。这是一个线程池的状态控制机制。
                 * 2.(Thread.interrupted() && runStateAtLeast(ctl.get(), STOP))：这一部分检查当前线程是否被中断，且线程池的状态是
                 * 否至少为 STOP。Thread.interrupted() 方法用于检查当前线程的中断状态，并清除中断状态。如果当前线
                 * 程被中断，且线程池的状态至少为 STOP，则表示需要中断工作线程。
                 * 3.!wt.isInterrupted()：检查工作线程 wt 的中断状态是否为 false，确保工作线程还没有被中断。
                 * 这段代码的目的是在特定条件下中断工作线程，以确保线程池在停止时能够正确地中断工作线程。
                 *
                 * ==================================下面这个if的逻辑可以堪称这个样子：
                 * 1.runStateAtLeast(ctl.get(), STOP) && !wt.isInterrupted()  or  2. (Thread.interrupted() && runStateAtLeast(ctl.get(), STOP)) && !wt.isInterrupted()
                 *
                 * 对于第一点：表示如果当前线城市至少是stop状态，且当前线程没有被中断，则中断当前的线程。 也就是如果线程池停止，请确保线程中断。
                 * 对于第2点： runStateAtLeast(ctl.get(), STOP) 为false的情况下(也就是线程池没有处于stop状态)才会
                 * 执行  (Thread.interrupted() && runStateAtLeast(ctl.get(), STOP))
                 *  而这个 《 (Thread.interrupted() && runStateAtLeast(ctl.get(), STOP))》 会先执行Thread.interrrupted擦除中断标记为，因为毕竟当前线程没有处于stop状态下
                 *  所以要清除中断标记位， 也就是 如果没有，请确保线程没有中断
                 *
                 *  ===================
                 * 注意 在shutDown方法中 会设置线程池的状态为 shutdowning.
                 * 而在shutdownNot中将线程池设置为stop
                 *
                 */
                if ((runStateAtLeast(ctl.get(), STOP) || (Thread.interrupted() && runStateAtLeast(ctl.get(), STOP))) && !wt.isInterrupted()) {
                    wt.interrupt();
                }
                try {
                    /**
                     * beforeExecute方法抛出异常  因为这个异常没有被catch 因此会退出while循环，同时任务并没有被执行
                     * 但是在finally中会 执行 w.completedTasks++; 最终退出runWoker
                     *
                     * task.run抛出异常 这些异常都会被 throw 导致runWorker线程退出，同时finally中会将这个异常交给afterExecute 方法执行
                     *
                     * afterExecute方法中抛出异常 因为afterExecute没有被tryCatch，因此异常会抛出导致runWoker退出。
                     *
                     * 在抛出异常的情况下 都不会执行 while 后面的completedAbruptly ，因此就使得completedAbruptly=true 表示突然完成（因为异常导致突然中止）
                     *
                     * 正常情况下如果没有抛出异常，那么 会继续执行while最终正常退出while，然后执行while后面的 completedAbruptly=false，表示非突然完成，也就是正常结束。
                     *
                     */
                    beforeExecute(wt, task);
                    Throwable thrown = null;
                    try {
                        //  //通过任务方式执行，不是线程方式
                        /**
                         * 调用shutdownnow()方法退出线程池时，线程池会向正在运行的任务发送Interrupt，任务中的阻塞操作会响应这个中断并
                         * 抛出InterruptedException，但同时会清除线程的Interrupted 状态标识，导致后续流程感知不到线程的中断了。要想立
                         * 即停止线程池中任务最好的方式就是直接向任务传递退出信号。
                         */
                        task.run();
                    } catch (RuntimeException x) {
                        thrown = x; throw x;
                    } catch (Error x) {
                        thrown = x; throw x;
                    } catch (Throwable x) {
                        thrown = x; throw new Error(x);
                    } finally {
                        afterExecute(task, thrown);
                    }
                } finally {
                    task = null;
                    w.completedTasks++;
                    w.unlock();
                }
            }//end while
            completedAbruptly = false;
        } finally {
            processWorkerExit(w, completedAbruptly);
        }
    }

    // Public constructors and methods

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters and default thread factory and rejected execution handler.
     * It may be more convenient to use one of the {@link Executors} factory
     * methods instead of this general purpose constructor.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
             Executors.defaultThreadFactory(), defaultHandler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters and default rejected execution handler.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @param threadFactory the factory to use when the executor
     *        creates a new thread
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     *         or {@code threadFactory} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
             threadFactory, defaultHandler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters and default thread factory.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @param handler the handler to use when execution is blocked
     *        because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     *         or {@code handler} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              RejectedExecutionHandler handler) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
             Executors.defaultThreadFactory(), handler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @param threadFactory the factory to use when the executor
     *        creates a new thread
     * @param handler the handler to use when execution is blocked
     *        because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     *         or {@code threadFactory} or {@code handler} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory,
                              RejectedExecutionHandler handler) {
        if (corePoolSize < 0 ||
            maximumPoolSize <= 0 ||
            maximumPoolSize < corePoolSize ||
            keepAliveTime < 0)
            throw new IllegalArgumentException();
        if (workQueue == null || threadFactory == null || handler == null)
            throw new NullPointerException();
        this.acc = System.getSecurityManager() == null ?
                null :
                AccessController.getContext();
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
    }

    /**
     * Executes the given task sometime in the future.  The task
     * may execute in a new thread or in an existing pooled thread.
     *
     * If the task cannot be submitted for execution, either because this
     * executor has been shutdown or because its capacity has been reached,
     * the task is handled by the current {@code RejectedExecutionHandler}.
     *
     * @param command the task to execute
     * @throws RejectedExecutionException at discretion of
     *         {@code RejectedExecutionHandler}, if the task
     *         cannot be accepted for execution
     * @throws NullPointerException if {@code command} is null
     */
    public void execute(Runnable command) {

        /**
         *     Executor框架是用来管理线程池的，他对外提供 往Executor中提交任务的方法
         *     一般而言 实现Executor 接口的线程池 在实现execute方法提交任务是会根据线程池中的核心线程数量来决定是创建新的核心线程执行这个任务还是将任务放置到队列中等待执行
         *     具体可以参考 ： java.util.concurrent.ThreadPoolExecutor#execute
         *      *
         *     这里仅仅是定义一个提交任务的接口。
         *             *
         *     线程池 并不一定意味着 任务会被交给 线程池中的线程执行，在Guava中 定义了一个直接线程池 Executor对象，如下
         *
         *     enum DirectExecutor implements Executor {
         *         INSTANCE;
         *      *
         *         @Override
         *         public void execute(Runnable command) {
         *             command.run();
         *         }
         *      *
         *         @Override
         *         public String toString() {
         *             return "MoreExecutors.directExecutor()";
         *         }
         *     }
         *
         *     在这个DirectExecutor 对象的execute方法中 直接执行当前的任务Command，也就意味着当前线程通过 DirectExecutor的execute方法
         *     提交任务给线程池的时候 这个任务直接在当前线程被执行了。
         *
         */



        // 若任务为空，则抛 NPE，不能执行空任务
        if (command == null) {
            throw new NullPointerException();
        }
        /**
         * ctl这个int 被分成两部分 workCount和runState
         * workCount表示有效线程数，The workerCount is the number of workers that have been permitted to start and not permitted to stop.
         * 具体参考ctl变量定义
         *
         * 既然ctl表示有效线程数 那么 ctl在哪里被修改呢？
         */
        int c = ctl.get();
        // 若工作线程数小于核心线程数，则创建新的线程，并把当前任务 command 作为这个线程的第一个任务
        /**
         * 在并发编程的艺术书中提到 如果核心线程数未满则创建新的核心线程。
         * 实际线程池中并没有什么核心线程、工作线程区分，核心线程和工作线程都是work对象。
         *
         *workerCountOf方法取出低29位的值，表示当前活动的线程数；
         * 如果当前活动的线程数小于corePoolSize，则新建一个线程放入线程池中，并把该任务放到线程中
         */
        if (workerCountOf(c) < corePoolSize) {
            /**
             * addWorker中的第二个参数表示限制添加线程的数量 是根据据corePoolSize 来判断还是maximumPoolSize来判断；
             * 如果是ture，根据corePoolSize判断
             * 如果是false，根据maximumPoolSize判断
             */
            if (addWorker(command, true)) {
                return;
            }
            /**
             * 如果添加失败，则重新获取ctl值
             */
            c = ctl.get();
        }
        /**
         * 至此，有以下两种情况：
         * 1.当前工作线程数大于等于核心线程数
         * 2.新建线程失败
         * 此时会尝试将任务添加到阻塞队列 workQueue
         */
        // 若线程池处于 RUNNING 状态，将任务添加到阻塞队列 workQueue 中
        if (isRunning(c) && workQueue.offer(command)) {
            //double-check，重新获取ctl的值
            int recheck = ctl.get();
            // 如果线程池已不处于 RUNNING 状态，那么移除已入队的任务，并且执行拒绝策略
            if (!isRunning(recheck) && remove(command)) {
                // 任务添加到阻塞队列失败，执行拒绝策略
                reject(command);
            }
            // 如果线程池还是 RUNNING 的，并且线程数为 0（什么情况下会出现这种情况），那么开启新的线程
            /**
             *获取线程池中的有效线程数，如果数量是0，则执行addWorker方法；
             * 第一个参数为null，表示在线程池中创建一个线程，但没有传递Runable对象给这个线程
             * 第二个参数为false，将线程池的线程数量的上限设置为maximumPoolSize，添加线程时根据maximumPoolSize来判断.
             * 这里要注意一下addWorker(null, false);，也就是创建一个线程，但并没有传入任务，因为任务已经被添加到workQueue中了，
             * 所以worker在执行的时候，会直接从workQueue中获取任务。所以，在workerCountOf(recheck) == 0时执行addWorker(null, false);
             * 也是为了保证线程池在RUNNING状态下必须要有一个线程来执行任务。
             *
             *
             * 问题： 上面的代码中将任务添加到队列中，一般情况下我们说 核心线程数已满则添加到任务队列中，并不会创建新的线程。
             * 那么下面为什么会 出现 workerCountOf(recheck) == 0的时候，为什么还会创建新的线程呢？
             * https://stackoverflow.com/questions/46901095/java-threadpoolexecutor-why-we-need-to-judge-the-worker-count-in-the-execute-fun
             * 考虑这样一种情况  corePoolSize=1，  我们第一次检查的时候 有一个线程正在运行，
             * 在if (workerCountOf(c) < corePoolSize) and workQueue.offer(command)之间（当前线程尚未添加Job到队列中），这个worker线程完成了他的工作。那么
             * 这个worker线程将会被阻塞。这个时候我们重新检查就会出现workerCountOf(recheck) == 0的情况，我们需要重新启动一个新的worker。
             * coolpoolSize不总是等于真实的worker的数量。
             * Every workercould die after they finish their job and find no job in the block queue within a limited time.
             * But a core thread never quits when it has finished working, even if there are no tasks in the queue
             *
             * Answer2： ctually if you look at the ThreadPoolExecutor.execute source there is a comment that states the same.
             * Particularly it says "So we recheck state and if necessary roll back the enqueuing if stopped,
             * or start a new thread if there are none.". I'm not sure what John misses in that comment
             *
             * Answer 3： 也就是说workCount=1 并不意味着有一个线程正在执行任务或者阻塞等待任务执行，可能这个线程正在执行退出尚未对workcount做减法。
             * 参考addWorker中对workCount的解释。
             *
             */
            else if (workerCountOf(recheck) == 0) {
                addWorker(null, false);
            }
        }
        //任务添加到 队列失败的情况下创建新的工作线程执行任务，
        /**
         * 至此，有以下两种情况：
         * 1.线程池处于非运行状态，线程池不再接受新的线程
         * 2.线程处于运行状态，但是阻塞队列已满，无法加入到阻塞队列
         * 此时会尝试以最大线程数为限制创建新的工作线程
         */
        else if (!addWorker(command, false)) {
            // 任务进入线程池失败，执行拒绝策略
            reject(command);
        }
    }

    /**
     * Initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be accepted.
     * Invocation has no additional effect if already shut down.
     *
     * <p>This method does not wait for previously submitted tasks to
     * complete execution.  Use {@link #awaitTermination awaitTermination}
     * to do that.
     *
     *
     *ThreadPoolExecutor中通常找到的shutdown方法。shutdown方法用于启动有序关闭，这意味着它允许先前提交的任务完成执行，但不接受新的任务执行。
     *
     * 以下是描述的关键要点：
     *
     * 启动有序关闭： 调用shutdown方法来启动关闭过程。
     *
     * 先前提交的任务将被执行：在关闭之前提交的任务将被允许完成执行。 已经提交到线程池中的任务会被继续执行。具体的实现逻辑是 在interruptIdleWorkers
     * 方法中不会对 正在执行任务的线程进行中断
     *
     * 不接受新任务执行：在调用shutdown之后，执行器停止接受新的任务执行。在关闭之后尝试提交新任务将导致拒绝。
     * 如果已经关闭，则调用不会产生额外效果：如果执行器已经关闭，再次调用shutdown将不会产生额外效果。一旦执行器关闭，它将保持在关闭状态。
     *
     * 该方法不等待先前提交的任务完成执行：shutdown方法本身不会阻塞并等待先前提交的任务完成。它启动关闭过程，但不等待任务完成。
     * 就是说在线程B中 使用shutDown方法停止线程池，不会阻塞线程B的继续执行，线程B会继续执行shutdown方法之后的代码。
     *
     * 使用awaitTermination等待任务完成：如果需要等待先前提交的任务完成，可以在调用shutdown之后使用awaitTermination方法。
     * awaitTermination会阻塞，直到所有任务完成执行或经过指定的超时时间。
     *
     * @throws SecurityException {@inheritDoc}
     */
    public void shutdown() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            checkShutdownAccess();
            advanceRunState(SHUTDOWN);
            interruptIdleWorkers();
            onShutdown(); // hook for ScheduledThreadPoolExecutor
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
    }

    /**
     * Attempts to stop all actively executing tasks, halts the
     * processing of waiting tasks, and returns a list of the tasks
     * that were awaiting execution. These tasks are drained (removed)
     * from the task queue upon return from this method.
     *
     * <p>This method does not wait for actively executing tasks to
     * terminate.  Use {@link #awaitTermination awaitTermination} to
     * do that.
     *
     * <p>There are no guarantees beyond best-effort attempts to stop
     * processing actively executing tasks.  This implementation
     * cancels tasks via {@link Thread#interrupt}, so any task that
     * fails to respond to interrupts may never terminate.
     *
     * @throws SecurityException {@inheritDoc}
     */
    public List<Runnable> shutdownNow() {
        List<Runnable> tasks;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            checkShutdownAccess();
            /**
             * 1.注意 在shutDown方法中 会设置线程池的状态为 shutdowning.
             * 而在shutdownNot中将线程池设置为stop
             *
             * 2.shutDownNow能够关闭线程池的原理是： （1）如果线程处于阻塞状态，由于shutdownNow会中断每一个work线程，因此会唤醒iwork线
             * 程的继续执行。 （2）worker 线程的while (task != null || (task = getTask()) != null) {} task不为null的时候会
             * 执行task， getTask在线程池为stop状态的时候会返回null，导致work线程退出 while循环从而终止了work Thread。
             * 那么shutdownNow一定能关闭线程池吗？ 答案是不一定， shutDownNow会对每一个线程进行中断，如果某一个workThread执行的业务
             * 中有两个 wait，当业务阻塞在第一个wait的时候 线程池shutdownNow的中断可能会唤醒该业务，但是后续业务又再次阻塞在wait，导致业务任
             * 务无法结束，线程池无法关闭。 只要改业务任务能够执行完成，对于这个workThread来说他再次从任务队列中getTask的时候会发现线
             * 程池是stop状态，所以会返回null task。从而退出while循环，从而workThread终止。
             */
            advanceRunState(STOP);
            interruptWorkers();
            tasks = drainQueue();
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
        return tasks;
    }

    public boolean isShutdown() {
        return ! isRunning(ctl.get());
    }

    /**
     * Returns true if this executor is in the process of terminating
     * after {@link #shutdown} or {@link #shutdownNow} but has not
     * completely terminated.  This method may be useful for
     * debugging. A return of {@code true} reported a sufficient
     * period after shutdown may indicate that submitted tasks have
     * ignored or suppressed interruption, causing this executor not
     * to properly terminate.
     *
     * @return {@code true} if terminating but not yet terminated
     */
    public boolean isTerminating() {
        int c = ctl.get();
        return ! isRunning(c) && runStateLessThan(c, TERMINATED);
    }

    public boolean isTerminated() {
        return runStateAtLeast(ctl.get(), TERMINATED);
    }

    public boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (;;) {
                if (runStateAtLeast(ctl.get(), TERMINATED))
                    return true;
                if (nanos <= 0)
                    return false;
                nanos = termination.awaitNanos(nanos);
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Invokes {@code shutdown} when this executor is no longer
     * referenced and it has no threads.
     */
    protected void finalize() {
        SecurityManager sm = System.getSecurityManager();
        if (sm == null || acc == null) {
            shutdown();
        } else {
            PrivilegedAction<Void> pa = () -> { shutdown(); return null; };
            AccessController.doPrivileged(pa, acc);
        }
    }

    /**
     * Sets the thread factory used to create new threads.
     *
     * @param threadFactory the new thread factory
     * @throws NullPointerException if threadFactory is null
     * @see #getThreadFactory
     */
    public void setThreadFactory(ThreadFactory threadFactory) {
        if (threadFactory == null)
            throw new NullPointerException();
        this.threadFactory = threadFactory;
    }

    /**
     * Returns the thread factory used to create new threads.
     *
     * @return the current thread factory
     * @see #setThreadFactory(ThreadFactory)
     */
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    /**
     * Sets a new handler for unexecutable tasks.
     *
     * @param handler the new handler
     * @throws NullPointerException if handler is null
     * @see #getRejectedExecutionHandler
     */
    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        if (handler == null)
            throw new NullPointerException();
        this.handler = handler;
    }

    /**
     * Returns the current handler for unexecutable tasks.
     *
     * @return the current handler
     * @see #setRejectedExecutionHandler(RejectedExecutionHandler)
     */
    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return handler;
    }

    /**
     * Sets the core number of threads.  This overrides any value set
     * in the constructor.  If the new value is smaller than the
     * current value, excess existing threads will be terminated when
     * they next become idle.  If larger, new threads will, if needed,
     * be started to execute any queued tasks.
     *
     * @param corePoolSize the new core size
     * @throws IllegalArgumentException if {@code corePoolSize < 0}
     * @see #getCorePoolSize
     */
    public void setCorePoolSize(int corePoolSize) {
        if (corePoolSize < 0)
            throw new IllegalArgumentException();
        int delta = corePoolSize - this.corePoolSize;
        this.corePoolSize = corePoolSize;
        if (workerCountOf(ctl.get()) > corePoolSize)
            interruptIdleWorkers();
        else if (delta > 0) {
            // We don't really know how many new threads are "needed".
            // As a heuristic, prestart enough new workers (up to new
            // core size) to handle the current number of tasks in
            // queue, but stop if queue becomes empty while doing so.
            int k = Math.min(delta, workQueue.size());
            while (k-- > 0 && addWorker(null, true)) {
                if (workQueue.isEmpty())
                    break;
            }
        }
    }

    /**
     * Returns the core number of threads.
     *
     * @return the core number of threads
     * @see #setCorePoolSize
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * Starts a core thread, causing it to idly wait for work. This
     * overrides the default policy of starting core threads only when
     * new tasks are executed. This method will return {@code false}
     * if all core threads have already been started.
     *
     * @return {@code true} if a thread was started
     */
    public boolean prestartCoreThread() {
        return workerCountOf(ctl.get()) < corePoolSize &&
            addWorker(null, true);
    }

    /**
     * Same as prestartCoreThread except arranges that at least one
     * thread is started even if corePoolSize is 0.
     */
    void ensurePrestart() {
        int wc = workerCountOf(ctl.get());
        if (wc < corePoolSize)
            addWorker(null, true);
        else if (wc == 0)
            addWorker(null, false);
    }

    /**
     * Starts all core threads, causing them to idly wait for work. This
     * overrides the default policy of starting core threads only when
     * new tasks are executed.
     *
     * @return the number of threads started
     */
    public int prestartAllCoreThreads() {
        int n = 0;
        while (addWorker(null, true))
            ++n;
        return n;
    }

    /**
     * Returns true if this pool allows core threads to time out and
     * terminate if no tasks arrive within the keepAlive time, being
     * replaced if needed when new tasks arrive. When true, the same
     * keep-alive policy applying to non-core threads applies also to
     * core threads. When false (the default), core threads are never
     * terminated due to lack of incoming tasks.
     *
     * @return {@code true} if core threads are allowed to time out,
     *         else {@code false}
     *
     * @since 1.6
     */
    public boolean allowsCoreThreadTimeOut() {
        return allowCoreThreadTimeOut;
    }

    /**
     * Sets the policy governing whether core threads may time out and
     * terminate if no tasks arrive within the keep-alive time, being
     * replaced if needed when new tasks arrive. When false, core
     * threads are never terminated due to lack of incoming
     * tasks. When true, the same keep-alive policy applying to
     * non-core threads applies also to core threads. To avoid
     * continual thread replacement, the keep-alive time must be
     * greater than zero when setting {@code true}. This method
     * should in general be called before the pool is actively used.
     *
     * @param value {@code true} if should time out, else {@code false}
     * @throws IllegalArgumentException if value is {@code true}
     *         and the current keep-alive time is not greater than zero
     *
     * @since 1.6
     */
    public void allowCoreThreadTimeOut(boolean value) {
        if (value && keepAliveTime <= 0)
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        if (value != allowCoreThreadTimeOut) {
            allowCoreThreadTimeOut = value;
            if (value)
                interruptIdleWorkers();
        }
    }

    /**
     * Sets the maximum allowed number of threads. This overrides any
     * value set in the constructor. If the new value is smaller than
     * the current value, excess existing threads will be
     * terminated when they next become idle.
     *
     * @param maximumPoolSize the new maximum
     * @throws IllegalArgumentException if the new maximum is
     *         less than or equal to zero, or
     *         less than the {@linkplain #getCorePoolSize core pool size}
     * @see #getMaximumPoolSize
     */
    public void setMaximumPoolSize(int maximumPoolSize) {
        if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize)
            throw new IllegalArgumentException();
        this.maximumPoolSize = maximumPoolSize;
        if (workerCountOf(ctl.get()) > maximumPoolSize)
            interruptIdleWorkers();
    }

    /**
     * Returns the maximum allowed number of threads.
     *
     * @return the maximum allowed number of threads
     * @see #setMaximumPoolSize
     */
    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    /**
     * Sets the time limit for which threads may remain idle before
     * being terminated.  If there are more than the core number of
     * threads currently in the pool, after waiting this amount of
     * time without processing a task, excess threads will be
     * terminated.  This overrides any value set in the constructor.
     *
     * @param time the time to wait.  A time value of zero will cause
     *        excess threads to terminate immediately after executing tasks.
     * @param unit the time unit of the {@code time} argument
     * @throws IllegalArgumentException if {@code time} less than zero or
     *         if {@code time} is zero and {@code allowsCoreThreadTimeOut}
     * @see #getKeepAliveTime(TimeUnit)
     */
    public void setKeepAliveTime(long time, TimeUnit unit) {
        if (time < 0)
            throw new IllegalArgumentException();
        if (time == 0 && allowsCoreThreadTimeOut())
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        long keepAliveTime = unit.toNanos(time);
        long delta = keepAliveTime - this.keepAliveTime;
        this.keepAliveTime = keepAliveTime;
        if (delta < 0)
            interruptIdleWorkers();
    }

    /**
     * Returns the thread keep-alive time, which is the amount of time
     * that threads in excess of the core pool size may remain
     * idle before being terminated.
     *
     * @param unit the desired time unit of the result
     * @return the time limit
     * @see #setKeepAliveTime(long, TimeUnit)
     */
    public long getKeepAliveTime(TimeUnit unit) {
        return unit.convert(keepAliveTime, TimeUnit.NANOSECONDS);
    }

    /* User-level queue utilities */

    /**
     * Returns the task queue used by this executor. Access to the
     * task queue is intended primarily for debugging and monitoring.
     * This queue may be in active use.  Retrieving the task queue
     * does not prevent queued tasks from executing.
     *
     * @return the task queue
     */
    public BlockingQueue<Runnable> getQueue() {
        return workQueue;
    }

    /**
     * Removes this task from the executor's internal queue if it is
     * present, thus causing it not to be run if it has not already
     * started.
     *
     * <p>This method may be useful as one part of a cancellation
     * scheme.  It may fail to remove tasks that have been converted
     * into other forms before being placed on the internal queue. For
     * example, a task entered using {@code submit} might be
     * converted into a form that maintains {@code Future} status.
     * However, in such cases, method {@link #purge} may be used to
     * remove those Futures that have been cancelled.
     *
     * @param task the task to remove
     * @return {@code true} if the task was removed
     */
    public boolean remove(Runnable task) {
        boolean removed = workQueue.remove(task);
        tryTerminate(); // In case SHUTDOWN and now empty
        return removed;
    }

    /**
     * Tries to remove from the work queue all {@link Future}
     * tasks that have been cancelled. This method can be useful as a
     * storage reclamation operation, that has no other impact on
     * functionality. Cancelled tasks are never executed, but may
     * accumulate in work queues until worker threads can actively
     * remove them. Invoking this method instead tries to remove them now.
     * However, this method may fail to remove tasks in
     * the presence of interference by other threads.
     */
    public void purge() {
        final BlockingQueue<Runnable> q = workQueue;
        try {
            Iterator<Runnable> it = q.iterator();
            while (it.hasNext()) {
                Runnable r = it.next();
                if (r instanceof Future<?> && ((Future<?>)r).isCancelled())
                    it.remove();
            }
        } catch (ConcurrentModificationException fallThrough) {
            // Take slow path if we encounter interference during traversal.
            // Make copy for traversal and call remove for cancelled entries.
            // The slow path is more likely to be O(N*N).
            for (Object r : q.toArray())
                if (r instanceof Future<?> && ((Future<?>)r).isCancelled())
                    q.remove(r);
        }

        tryTerminate(); // In case SHUTDOWN and now empty
    }

    /* Statistics */

    /**
     * Returns the current number of threads in the pool.
     *
     * @return the number of threads
     */
    public int getPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // Remove rare and surprising possibility of
            // isTerminated() && getPoolSize() > 0
            return runStateAtLeast(ctl.get(), TIDYING) ? 0
                : workers.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate number of threads that are actively
     * executing tasks.
     *
     ** 返回活跃线程的大概数量
     *       * 执行任务。
     *
     *
     * @return the number of threads
     */
    public int getActiveCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            int n = 0;
            for (Worker w : workers)
            /**
             * 说明1： 关于lock我们说是为了 禁止对正在执行任务的线程进行中断。 实际上这个 是否lock还可以做另外一种用处：就是判断线程是否active。
             * 线程池中的线程可能有三种状态：（1）阻塞在take （2）正在执行任务 已经lock（3）处于take-> 没有执行任务 或者执行完任务尚未take之间的瞬态。
             *
             * ThreadPoolExecutor的getActiveCount：方法是返回 正在执行任务的线程的数量，他就是遍历每一个worker，看这个worker是否locked，如果是就认为正在执行任务
             * ThreadPoolExecutor的gePoolSize是返回线程的数量，显然这和 getActiveCount有区别。
             *
             * 在Dubbo中提供了一种线程池实现 EagerThreadPoolExecutor， JUC的ThreadPoolExecutor标准实现是：提交任务时先判断线程池是否核心线程已满，如果没有则创建新的核心线程执行任务
             * 如果核心线程已满，则入队，如果队列已满导致入队不成功则创建新的worker线程，如果worker线程已满创建失败则抛出异常。 也就是说
             * JUC的队列满了之后才会开启新的线程来处理任务（前提是核心线程已满的情况下，且线程池线程数量没超过最大线程数量）
             *
             * EagerThreadPoolExecutor 当线程池核心线程已满，新来的任务不会被放入线程池队列，而是会开启新线程来执行处理任务。 这个是如何做到的呢？
             * 其实就是EagerThreadPoolExecutor使用了自定义的TaskQueue，这个TaskQueue重写了队列的offer方法，
             * TaskQueue在入队的时候发现 如果当前线程池的线程数小于最大线程数 则返回false表示入队失败，从而 创建新的线程。
             * 创建新的线程的时候如果发现线程池中线程已经到了最大值 导致创建也失败了就会抛出异常  这个时候我们捕获这个异常 将任务放入队列中。具体参考源码
             */
                if (w.isLocked())
                    ++n;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the largest number of threads that have ever
     * simultaneously been in the pool.
     *
     * @return the number of threads
     */
    public int getLargestPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            return largestPoolSize;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate total number of tasks that have ever been
     * scheduled for execution. Because the states of tasks and
     * threads may change dynamically during computation, the returned
     * value is only an approximation.
     *
     * @return the number of tasks
     */
    public long getTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers) {
                n += w.completedTasks;
                if (w.isLocked())
                    ++n;
            }
            return n + workQueue.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate total number of tasks that have
     * completed execution. Because the states of tasks and threads
     * may change dynamically during computation, the returned value
     * is only an approximation, but one that does not ever decrease
     * across successive calls.
     *
     * @return the number of tasks
     */
    public long getCompletedTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers)
                n += w.completedTasks;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns a string identifying this pool, as well as its state,
     * including indications of run state and estimated worker and
     * task counts.
     *
     * @return a string identifying this pool, as well as its state
     */
    public String toString() {
        long ncompleted;
        int nworkers, nactive;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            ncompleted = completedTaskCount;
            nactive = 0;
            nworkers = workers.size();
            for (Worker w : workers) {
                ncompleted += w.completedTasks;
                if (w.isLocked())
                    ++nactive;
            }
        } finally {
            mainLock.unlock();
        }
        int c = ctl.get();
        String rs = (runStateLessThan(c, SHUTDOWN) ? "Running" :
                     (runStateAtLeast(c, TERMINATED) ? "Terminated" :
                      "Shutting down"));
        return super.toString() +
            "[" + rs +
            ", pool size = " + nworkers +
            ", active threads = " + nactive +
            ", queued tasks = " + workQueue.size() +
            ", completed tasks = " + ncompleted +
            "]";
    }

    /* Extension hooks */

    /**
     * Method invoked prior to executing the given Runnable in the
     * given thread.  This method is invoked by thread {@code t} that
     * will execute task {@code r}, and may be used to re-initialize
     * ThreadLocals, or to perform logging.
     *
     * <p>This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke {@code super.beforeExecute} at the end of
     * this method.
     *
     * @param t the thread that will run task {@code r}
     * @param r the task that will be executed
     */
    protected void beforeExecute(Thread t, Runnable r) { }

    /**
     * Method invoked upon completion of execution of the given Runnable.
     * This method is invoked by the thread that executed the task. If
     * non-null, the Throwable is the uncaught {@code RuntimeException}
     * or {@code Error} that caused execution to terminate abruptly.
     *
     * <p>This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke {@code super.afterExecute} at the
     * beginning of this method.
     *
     * <p><b>Note:</b> When actions are enclosed in tasks (such as
     * {@link FutureTask}) either explicitly or via methods such as
     * {@code submit}, these task objects catch and maintain
     * computational exceptions, and so they do not cause abrupt
     * termination, and the internal exceptions are <em>not</em>
     * passed to this method. If you would like to trap both kinds of
     * failures in this method, you can further probe for such cases,
     * as in this sample subclass that prints either the direct cause
     * or the underlying exception if a task has been aborted:
     *
     *  <pre> {@code
     * class ExtendedExecutor extends ThreadPoolExecutor {
     *   // ...
     *   protected void afterExecute(Runnable r, Throwable t) {
     *     super.afterExecute(r, t);
     *     if (t == null && r instanceof Future<?>) {
     *       try {
     *         Object result = ((Future<?>) r).get();
     *       } catch (CancellationException ce) {
     *           t = ce;
     *       } catch (ExecutionException ee) {
     *           t = ee.getCause();
     *       } catch (InterruptedException ie) {
     *           Thread.currentThread().interrupt(); // ignore/reset
     *       }
     *     }
     *     if (t != null)
     *       System.out.println(t);
     *   }
     * }}</pre>
     *
     * @param r the runnable that has completed
     * @param t the exception that caused termination, or null if
     * execution completed normally
     */
    protected void afterExecute(Runnable r, Throwable t) { }

    /**
     * Method invoked when the Executor has terminated.  Default
     * implementation does nothing. Note: To properly nest multiple
     * overridings, subclasses should generally invoke
     * {@code super.terminated} within this method.
     */
    protected void terminated() { }

    /* Predefined RejectedExecutionHandlers */

    /**
     * A handler for rejected tasks that runs the rejected task
     * directly in the calling thread of the {@code execute} method,
     * unless the executor has been shut down, in which case the task
     * is discarded.
     */
    public static class CallerRunsPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code CallerRunsPolicy}.
         */
        public CallerRunsPolicy() { }

        /**
         * Executes task r in the caller's thread, unless the executor
         * has been shut down, in which case the task is discarded.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                r.run();
            }
        }
    }

    /**
     * A handler for rejected tasks that throws a
     * {@code RejectedExecutionException}.
     */
    public static class AbortPolicy implements RejectedExecutionHandler {
        /**
         * Creates an {@code AbortPolicy}.
         */
        public AbortPolicy() { }

        /**
         * Always throws RejectedExecutionException.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         * @throws RejectedExecutionException always
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            throw new RejectedExecutionException("Task " + r.toString() +
                                                 " rejected from " +
                                                 e.toString());
        }
    }

    /**
     * A handler for rejected tasks that silently discards the
     * rejected task.
     */
    public static class DiscardPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code DiscardPolicy}.
         */
        public DiscardPolicy() { }

        /**
         * Does nothing, which has the effect of discarding task r.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        }
    }

    /**
     * A handler for rejected tasks that discards the oldest unhandled
     * request and then retries {@code execute}, unless the executor
     * is shut down, in which case the task is discarded.
     */
    public static class DiscardOldestPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code DiscardOldestPolicy} for the given executor.
         */
        public DiscardOldestPolicy() { }

        /**
         * Obtains and ignores the next task that the executor
         * would otherwise execute, if one is immediately available,
         * and then retries execution of task r, unless the executor
         * is shut down, in which case task r is instead discarded.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                e.getQueue().poll();
                e.execute(r);
            }
        }
    }
}
