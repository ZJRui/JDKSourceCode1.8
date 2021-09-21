/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package java.lang.ref;

import sun.misc.Cleaner;
import sun.misc.JavaLangRefAccess;
import sun.misc.SharedSecrets;

/**
 * Abstract base class for reference objects.  This class defines the
 * operations common to all reference objects.  Because reference objects are
 * implemented in close cooperation with the garbage collector, this class may
 * not be subclassed directly.
 * <p>
 * 引用对象的抽象基类。这个类定义了所有引用对象的通用操作。因为引用对象是与垃圾回收器密切合作实现的，所以这个类不能直接子类化。
 *
 * @author Mark Reinhold
 * @since 1.2
 */

/**
 * https://www.jianshu.com/p/d275812816e5
 *
 * @param <T>
 */
public abstract class Reference<T> {

    /* A Reference instance is in one of four possible internal states:
          Reference实例有以下四种可能的内部状态:
     *
     *     Active: Subject to special treatment by the garbage collector.  Some
     *     time after the collector detects that the reachability of the
     *     referent has changed to the appropriate state, it changes the
     *     instance's state to either Pending or Inactive, depending upon
     *     whether or not the instance was registered with a queue when it was
     *     created.  In the former case it also adds the instance to the
     *     pending-Reference list.  Newly-created instances are Active.
                           活动的:接受垃圾回收器的特殊处理。一些
                *时间后，收集器检测的可达
                * referent已经改变到适当的状态，它改变
                *实例的状态为Pending或Inactive，取决于
                *是否在队列中注册了实例
                *创建。在前一种情况下，它还将实例添加到
                * pending-Reference列表。新创建的实例为“Active”。

                Reference刚开始被构造时处于这个状态。当对象的可达性发生改变（不再可达）的某个时间后，、
                会被更改为pending状态（前提是构造Reference对象时传入了引用队列）。


                Pending: 当Reference包装的referent = null的时候（当对象的可达性发生改变（不再可达）的某个时间后），JVM会把Reference设置成pending状态。
                如果Reference创建时指定了ReferenceQueue，那么会被ReferenceHandler线程处理进入到ReferenceQueue队列中，如果没有就进入Inactive状态。

                ===================================

     *
     *     Pending: An element of the pending-Reference list, waiting to be
     *     enqueued by the Reference-handler thread.  Unregistered instances
     *     are never in this state.
                * Pending: Pending - reference列表中的元素，等待被挂起
                *被引用处理程序线程加入队列。未注册的实例
                *从未处于这种状态。
                挂起状态： 一个挂起引用列表中等待被引用处理线程排队的元素。没有注册的实例不可能有这个状态。
                处于这个状态时，说明引用列表即将被ReferenceHandler线程加入到引用队列的对象（前提是构造Reference对象时传入了引用队列）。

                我觉得：当Reference对象所包装的referent对象为null的时候，Reference对象处于Pending状态，他等待被ReferenceHandler线程
                放入到队列Queue（这个队列是我们在创建Reference对象时指定的Queue）中。 如果被放入到了Queue中，则其状态变成Enqueued
     *===========================
     *     Enqueued: An element of the queue with which the instance was
     *     registered when it was created.  When an instance is removed from
     *     its ReferenceQueue, it is made Inactive.  Unregistered instances are
     *     never in this state.
                           Enqueued:实例所在队列的一个元素
                *在创建时注册。当实例从
                *它的ReferenceQueue，它是Inactive。未注册的实例
                从来没有在这种状态。
                这个引用的对象即将被垃圾回收时处于这个状态，此时已经被JVM加入到了引用队列（如果构造时指定的话），当从引用队列中取出时，状态随之变为inactive状态。
     *=======================
     *     Inactive: Nothing more to do.  Once an instance becomes Inactive its
     *     state will never change again.
                      不活跃的:无事可做。一旦实例变为Inactive，它的
                *状态永远不会再改变。
                引用的对象已经被垃圾回收，一旦处于这个状态就无法改变了。
     *

     =============================
     * The state is encoded in the queue and next fields as follows:
       *状态编码在队列和下一个字段如下:
     *
     *     Active: queue = ReferenceQueue with which instance is registered, or
     *     ReferenceQueue.NULL if it was not registered with a queue; next =
     *     null.
     激活的:queue = ReferenceQueue实例被注册，或者
* ReferenceQueue。如果没有注册到队列，则为NULL;下一个=
* null。
     *
     *     Pending: queue = ReferenceQueue with which instance is registered;
     *     next = this
     *
     Pending: queue = ReferenceQueue;
* next = this

     *     Enqueued: queue = ReferenceQueue.ENQUEUED; next = Following instance
     *     in queue, or this if at end of list.
     * Enqueued: queue = referencqueue . Enqueued;next =下面的实例
*在队列中，或者这个if在列表的末尾。
＊
     *
     *     Inactive: queue = ReferenceQueue.NULL; next = this.
     *未激活:queue = referencqueue . null;下一个=。
     *
     =================================
     * With this scheme the collector need only examine the next field in order
     * to determine whether a Reference instance requires special treatment: If
     * the next field is null then the instance is active; if it is non-null,
     * then the collector should treat the instance normally.
     **在这种模式下，收集器只需要按顺序检查下一个字段
*确定引用实例是否需要特殊处理
*下一个字段为空，则该实例是活动的;如果它是非空的，
*则收集器应该正常地处理实例。

===============================
     * To ensure that a concurrent collector can discover active Reference
     * objects without interfering with application threads that may apply
     * the enqueue() method to those objects, collectors should link
     * discovered objects through the discovered field. The discovered
     * field is also used for linking Reference objects in the pending list.
     * 保证一个并发收集器可以发现活跃的Reference
      * 对象不干扰可能适用的应用程序线程
      * enqueue() 方法到这些对象，收集者应该链接
      * 通过发现的字段发现的对象。 发现的
      * 字段还用于链接挂起列表中的参考对象。
      注意这个属性并不是static类型的，pending属性是static类型的

     */

    private T referent;         /* Treated specially by GC */

    /**
     * ReferenceQueue，即引用队列，当一个Reference引用的对象被垃圾回收后，这个Reference会被加入到这个队列（前提是在构造Reference时声明了这个队列）。
     * 表示被包装的对象被回收时，需要被通知的队列，该队列在Reference构造函数中指定。当Referent被回收时，Reference对象就出在了Pending状态，
     * Reference会等待被放入到该队列中。如果放入到了队列中Reference对象的状态变成Enqueue。 如果构造函数没有指定队列，那么就进入inactive状态。
     *
     * 注意这个属性并不是static类型的，pending属性是static类型的
     *
     * //所属的引用队列，如果指定了引用队列并且没有入队，那么queue指向的就是指定的引用队列，如果没有指定
     * 	//引用队列，则默认为ReferenceQueue.NULL，如果入队，则queue改为java.lang.ref.ReferenceQueue#ENQUEUED,所以这个变量
     * 	//既可以保存指定的引用队列，也可以作为一个标记判断这个Reference有没有入队
     * 	//设置为volatile是因为有ReferenceHandler线程负责入队操作，即会更改这个queue指向ENQUEUED
     *
     * 	注意：一旦当前Reference对象进入到其属性queue所指定的ReferenceQueue中，那么当前Reference对象的queue属性就会被设置为java.lang.ref.ReferenceQueue#ENQUEUED
     * 	这个逻辑是在ReferenceQueue的enqueue方法中实现的。
     *
     *
     *
     */
    volatile ReferenceQueue<? super T> queue;

    /* When active:   NULL
     *     pending:   this
     *    Enqueued:   next reference in queue (or this if last)
     *    Inactive:   this
     *
     *  当Reference对象在queue中时（即Reference处于Enqueued状态），next描述当前引用节点所存储的下一个即将被处理的节点。
     * ReferenceHandler线程会把pending状态的Reference放入ReferenceQueue中，上面说的next，discovered 字段在入队之后也会发生变化，下一小节会介绍。
     */
    @SuppressWarnings("rawtypes")
    volatile Reference next;

    /* When active:   next element in a discovered reference list maintained by GC (or this if last)
     *     pending:   next element in the pending list (or null if last)
     *   otherwise:   NULL
     *
     * 活动时:发现的由GC维护的引用列表中的下一个元素(如果是最后一个)
     * pending: pending列表中的下一个元素(如果最后一个元素是null)
     *其他:零
     *
     * 注意这个属性并不是static类型的，pending属性是static类型的
     *
     * 注意这个属性是由 JVM使用的
     *
     * discovered: 当处于Reference处在pending状态：discovered为pending集合中的下一个元素；其他状态：discovered为null
     *
     * //由JVM控制，表示下一个要被回收的对象
     *垃圾收集器管理的引用列表中下一个引用对象，由JVM管理和赋值。
     */
    transient private Reference<T> discovered;  /* used by VM */


    /* Object used to synchronize with the garbage collector.  The collector
     * must acquire this lock at the beginning of each collection cycle.  It is
     * therefore critical that any code holding this lock complete as quickly
     * as possible, allocate no new objects, and avoid calling user code.
     */
    static private class Lock {
    }

    private static Lock lock = new Lock();


    /* List of References waiting to be enqueued.  The collector adds
     * References to this list, while the Reference-handler thread removes
     * them.  This list is protected by the above lock object. The
     * list uses the discovered field to link its elements.
     *
     * 等待进入队列的引用列表。收集器补充道
     *当Reference-handler线程移除时，对这个列表的引用
     *。这个列表受上述锁对象的保护。的
     * list使用发现的字段链接它的元素。

     * * 注意：当JVM收集器将对象回收，Reference的referent为null的时候，收集器需要将Reference对象
     * 设置为Pending状态，也就是将其放置到Pending队列中。 也就是收集器将Reference对象放置到pending List中。
     *
     * Reference-handler thread 将Reference从pending list中移除，并将其放置到Queue中，设置为Enqueued状态
     *
     * pending理解链表有点费解，因为代码层面上看这明明就是Reference对象。其实当Reference处在Pending状态时，他的pending字段被
     * 赋值成了下一个要处理的对象（即下面讲的discovered），通过discovered可以拿到下一个对象并且赋值给pending，
     * 直到最后一个，所以这里就可以把它当成一个链表。而discovered是JVM的垃圾回收器添加进去的，大家可以不用关心底层细节。
     *
     *
     *
     * 注意这个pending是一个static 类型的属性，也就意味着所有的Reference对象共享同一个pending，jvm内唯一。
     *
     *	//引用列表中下一个要进入引用队列的对象(这个引用的对象已经被GC)，由ReferenceHandler线程负责加入队列
	//pending由JVM赋值
     */
    private static Reference<Object> pending = null;//注意这个pending是一个static 类型的属性，也就意味着所有的Reference对象共享同一个pending，jvm内唯一。

    /**
     * High-priority thread to enqueue pending References
     * 高优先级线程将挂起的引用放入队列
     *
     */
    private static class ReferenceHandler extends Thread {

        private static void ensureClassInitialized(Class<?> clazz) {
            try {
                Class.forName(clazz.getName(), true, clazz.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw (Error) new NoClassDefFoundError(e.getMessage()).initCause(e);
            }
        }

        static {
            // pre-load and initialize InterruptedException and Cleaner classes
            // so that we don't get into trouble later in the run loop if there's
            // memory shortage while loading/initializing them lazily.
            /**
             * //预加载并初始化InterruptedException类和Cleaner类，这样当惰性加载/初始化它们时，如果出现内存短缺，就不会在运行循环中遇到麻烦。
             *
             */
            ensureClassInitialized(InterruptedException.class);
            ensureClassInitialized(Cleaner.class);
        }

        ReferenceHandler(ThreadGroup g, String name) {
            super(g, name);
        }

        /**
         *
         * Reference是如何被放入队列的
         *
         * Reference类有一个内部静态类ReferenceHandler extends Thread，这个内部类中有静态代码块生成该类的一个守护线程对象并启动。那这个静态内部类的run方法做了什么，有什么作用呢？
         *
         *它的作用就是不停地从pending队列取出，并放入ReferenceQueue中。
         * 这里又出现一个pending队列，它的实现也很简单。Reference类中有一个静态属性Reference pending(注意是静态属性，属于类的，多个Reference对象全局共享)。
         * 这个pending对象相当于pending队列的顶部，它的discovered属性则是pending队列的下一个元素，以此类推。
         *
         *那又是谁把这些Reference对象放入pending队列呢？答案是垃圾收集器，它会把可达性合适的引用放入pending队列
         *那么整个过程就很明了了，一旦一个Reference引用指向的对象可达性合适，这个引用就会被垃圾收集器放到pending队列中，
         * 而同时一个辅助线程ReferenceHandler则不停地从中取出Reference对象放入到该对象注册的ReferenceQueue中
         *
         */
        public void run() {
            /**
             * 死循环
             */
            while (true) {

                /**
                 * 注意这里的参数是true
                 */
                tryHandlePending(true);
            }
        }
    }// end ReferenceHanlder

    /**
     * Try handle pending {@link Reference} if there is one.<p>
     * Return {@code true} as a hint that there might be another
     * {@link Reference} pending or {@code false} when there are no more pending
     * {@link Reference}s at the moment and the program can do some other
     * useful work instead of looping.
     *
     * @param waitForNotify if {@code true} and there was no pending
     *                      {@link Reference}, wait until notified from VM
     *                      or interrupted; if {@code false}, return immediately
     *                      when there is no pending {@link Reference}.
     * @return {@code true} if there was a {@link Reference} pending and it
     * was processed, or we waited for notification and either got it
     * or thread was interrupted before being notified;
     * {@code false} otherwise.
     *
     *
     * 如果有的话，尝试处理pending Reference。
     * 返回true作为提示，表示可能有另一个Reference挂起;返回false表示目前没有挂起的Reference，程序可以做一些其他有用的工作，而不是循环。
     *
     * 参数:
     * waitForNotify -如果为true，并且没有pending Reference，等待直到VM通知或中断;如果为false，则在没有pending Reference时立即返回。
     * 返回:
     * 如果有一个Reference pending并且它被处理了，或者我们在等待通知并得到了它，或者线程在被通知之前被中断了，则为true;否则错误。
     *
     *
     *
     * 问题：这个 tryHandlePending 方法被调用了两次， 一次是在ReferenceHandler线程的run方法中，一次是在下面的
     *  SharedSecrets.setJavaLangRefAccess 方法中。 第二次调用是什么意思？而且这两次调用方法传入的参数一个是true一个是false
     *
     *
     * 注意： 注意ReferenceHandler这个类被定义为static ，也就是ReferenceHandler对象并不依赖于外部类Reference 的实例对象而存在，
     * 换句话说，你可以创建很多个Reference对象，但是 因为  new ReferenceHandler 是在static 块中定义的，因此整个JVM内只有一个ReferenceHandler
     * 那么Referencehandler 作为线程，线程中 所用到的外部类的属性 也必须是static的，
     *
     */
    static boolean tryHandlePending(boolean waitForNotify) {
        Reference<Object> r;
        Cleaner c;
        try {
            /**
             * ReferenceHandler使用到了 static类型的lock属性
             *
             */
            synchronized (lock) {
                /**
                 *
                 * pending 这个属性 JVM 给赋值的吗？有些不解。 因为从下面的这个逻辑来看，如果pending不为null，才会给pending赋值；
                 *
                 * 如果pening为null，那不就意味着pending永远不会赋值？ JVM在收回对象的时候 会不会将pending 设置为某一个值？
                 *
                 * pending是一个Reference类型的对象，Reference对象内部有next属性，因此Reference对象可以构成一个链表结构。
                 *
                 * 我们知道pending的本意 表示 当前Reference对象处于 pending队列中。从上面的文档注释： The collector adds
                 *      * References to this list, while the Reference-handler thread removes them. 中我们可以大约理解到垃圾收集器
                 *      来处理pending
                 *
                 *
                 * 通过上面代码可以看到ReferenceHandler线程做的是不断的检查pending是否为null,
                 * 如果不为null,将pending对象进行入队操作，而pending的赋值由JVM操作。所以ReferenceQueue在这里作为JVM与上
                 * 层Reference对象管理之间的消息传递方式。
                 *
                 */
                if (pending != null) {
                    /**
                     * 如果pending不为null，下面的这段逻辑是取出pending链表中的第一个元素，实际上pending就是pending链表的头结点
                     * 然后discovered是pending链表的第二个节点。这段代码的逻辑就是取出pending节点 作为 赋值给r。
                     * 然后将discovered 节点作为pending节点，从而作为链表的头结点。
                     *
                     * 然后将原来的pending头结点 放入到queue所指向的ReferenceQueue队列中。也就是queue.enqueue(r); 这个r就是原来的pending链表头结点
                     *
                     */
                    r = pending;
                    // 'instanceof' might throw OutOfMemoryError sometimes
                    // so do this before un-linking 'r' from the 'pending' chain...
                    /**
                     * // 'instanceof'有时可能抛出OutOfMemoryError
                     * //在从'pending'链中解除'r'链接之前执行此操作…
                     *
                     *  Cleaner extends PhantomReference<Object>  ,因此Cleaner也是Reference
                     *
                     *
                     */
                    c = r instanceof Cleaner ? (Cleaner) r : null;
                    // unlink 'r' from 'pending' chain
                    pending = r.discovered;
                    r.discovered = null;
                } else {
                    // The waiting on the lock may cause an OutOfMemoryError
                    // because it may try to allocate exception objects.
                    if (waitForNotify) {
                        lock.wait();
                    }
                    // retry if waited
                    return waitForNotify;
                }
            }
        } catch (OutOfMemoryError x) {
            // Give other threads CPU time so they hopefully drop some live references
            // and GC reclaims some space.
            // Also prevent CPU intensive spinning in case 'r instanceof Cleaner' above
            // persistently throws OOME for some time...
            Thread.yield();
            // retry
            return true;
        } catch (InterruptedException x) {
            // retry
            return true;
        }

        // Fast path for cleaners
        if (c != null) {
            c.clean();
            return true;
        }

        ReferenceQueue<? super Object> q = r.queue;
        /**
         * ReferenceQueue入队过程
         * 上面说到ReferenceHandler线程会把pending状态的Reference对象放入到ReferenceQueue队列中。
         * 查看ReferenceQueue中入队源代码。
         *
         */
        if (q != ReferenceQueue.NULL) q.enqueue(r);
        return true;
    }

    static {
        /**
         *
         * 问题： 我们在上面看到了 ReferenceHandler线程，那么这个线程什么时候被创建 什么时候被启动，
         * 是不是每一个Reference对象都有一个ReferenceHandler线程呢？
         *
         * 这段代码是一个静态代码块，因此ReferenceHandler线程只会被创建一次，且JVM启动的时候就会启动
         *
         * 那么这个ReferenceHandler线程做了什么任务呢？ 只有一个对象ReferenceHandler 他又是如何与所有的Reference对象进行交互的？
         *
         */
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        for (ThreadGroup tgn = tg;
             tgn != null;
             tg = tgn, tgn = tg.getParent())
            ;
        Thread handler = new ReferenceHandler(tg, "Reference Handler");
        /* If there were a special system-only priority greater than
         * MAX_PRIORITY, it would be used here
         */
        handler.setPriority(Thread.MAX_PRIORITY);
        handler.setDaemon(true);
        handler.start();

        // provide access in SharedSecrets //检查java.lang.ref包的访问权限？
        SharedSecrets.setJavaLangRefAccess(new JavaLangRefAccess() {
            @Override
            public boolean tryHandlePendingReference() {
                /**
                 * 这里调用了上面的tryHandlePending 方法，
                 * 问题： 为什么这里要调用这个方法，  handlePending的工作不是交给ReferenceHandler线程来处理的吗
                 * 问题2： 注意下面调用tryHandlePending方法的时候传递的参数是false
                 *
                 */
                return tryHandlePending(false);
            }
        });
    }

    /* -- Referent accessor and setters -- */

    /**
     * Returns this reference object's referent.  If this reference object has
     * been cleared, either by the program or by the garbage collector, then
     * this method returns <code>null</code>.
     *
     * @return The object to which this reference refers, or
     * <code>null</code> if this reference object has been cleared
     */
    public T get() {
        return this.referent;
    }

    /**
     * Clears this reference object.  Invoking this method will not cause this
     * object to be enqueued.
     *
     * <p> This method is invoked only by Java code; when the garbage collector
     * clears references it does so directly, without invoking this method.
     * 清除此引用对象。调用此方法不会导致该对象进入队列。
     * 此方法仅由Java代码调用;当垃圾回收器清除引用时，它会直接清除引用，而不需要调用此方法。
     * <p>
     * //清除这个引用的对象，并不会导致这个Reference加入到引用队列
     */
    public void clear() {
        this.referent = null;
    }


    /* -- Queue operations -- */

    /**
     * Tells whether or not this reference object has been enqueued, either by
     * the program or by the garbage collector.  If this reference object was
     * not registered with a queue when it was created, then this method will
     * always return <code>false</code>.
     * <p>
     * <p>
     * 指示该引用对象是否已被程序或垃圾回收器入队。如果这个引用对象在创建时没有在队列中注册，那么这个方法将始终返回false。
     *
     * @return <code>true</code> if and only if this reference object has
     * been enqueued
     * <p>
     * //返回这个对象是否已经入队
     */
    public boolean isEnqueued() {
        return (this.queue == ReferenceQueue.ENQUEUED);
    }

    /**
     * Adds this reference object to the queue with which it is registered,
     * if any.
     *
     * <p> This method is invoked only by Java code; when the garbage collector
     * enqueues references it does so directly, without invoking this method.
     *
     * @return <code>true</code> if this reference object was successfully
     * enqueued; <code>false</code> if it was already enqueued or if
     * it was not registered with a queue when it was created
     *
     * 将此引用对象添加到其注册的队列(如果有的话)。
     * 此方法仅由Java代码调用;当垃圾回收器进行队列引用时，它直接这样做，而不需要调用此方法。
     *
     * 返回:
     * 如果该引用对象成功进入队列，则为True;如果它已经进入队列，或者它在创建时没有注册到队列中，则为False
     *
     *问题：对于一个软引用，当其所引用的对象 不存在任何强引用引用了该对象 ，且内存出现紧张的时候，JVM会回收这个对象及其所有的软引用。
     * 那么我们还需要关心的一个问题是 ：如何主动触发 放弃对这个软引用，这个时候我们需要做两个步骤：（1）清除该引用对象中的referent属性，也就是调用Reference的enqueue方法
     * （2）将这个引用对象放置到其属性queue所指向的ReferenceQueue中。
     * 这个用法可以在Spring的ConcurrentReferenceHashMap类的内部类SoftEntryReference<K,V>的release方法中看到。
     * SoftEntryReference是一个SoftReference的子类，他引用的对象类型是Map.Entry<K,V> ,如果我们想放弃一个SoftEntryReference对象，则需要
     * 执行enqueu方法和clear方法。因此其release方法实现如下
     * public void release() {
     * 			enqueue();//将引用对象自身放置到queue中
     * 			clear();//清除引用内部的属性referent（所引用的具体对象）
     * }
     *
     * 另外在JDK中的 FinalizableReferenceQueue类中的 close方法的实现中 也调用 enqueu 和clear方法。
     *
     *
     *  * 考虑当我我们从map中移除一个元素的时候，我们需要将引用和 这个引用所关联的对象 之间的关系解除。从而确保即便 对象存在的情况下 这个引用对象仍然能够被及时释放
     *
     */
    public boolean enqueue() {
        return this.queue.enqueue(this);
    }


    /* -- Constructors -- */

    Reference(T referent) {
        this(referent, null);
    }

    /**
     * reference表示需要被引用的对象，queue表示引用队列。
     * 如果指定了引用队列，那么当这个引用的对象被回收后，这个Reference对象本身会被加入到指定的ReferenceQueue。
     * 另外，PhantomReference只提供了第二种参数类型构造方法。
     * <p>
     * 注意： 是引用对象本身进入到 queue队列中，而不是引用对象所引用的对象进入队列
     *
     * @param referent
     * @param queue
     */
    Reference(T referent, ReferenceQueue<? super T> queue) {
        this.referent = referent;
        this.queue = (queue == null) ? ReferenceQueue.NULL : queue;
    }

}
