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

import java.util.function.Consumer;

/**
 * Reference queues, to which registered reference objects are appended by the
 * garbage collector after the appropriate reachability changes are detected.
 * 引用队列，在检测到适当的可达性更改后，垃圾收集器将向其添加已注册的引用对象。
 *
 * @author   Mark Reinhold
 * @since    1.2
 */

public class ReferenceQueue<T> {

    /**
     * Constructs a new reference-object queue.
     */
    public ReferenceQueue() { }

    private static class Null<S> extends ReferenceQueue<S> {
        boolean enqueue(Reference<? extends S> r) {
            return false;
        }
    }

    static ReferenceQueue<Object> NULL = new Null<>();
    static ReferenceQueue<Object> ENQUEUED = new Null<>();

    /**
     * 这个是什么风骚用法？
     * static:表示这个类的对象和外部类的对象才之间没有关系
     * private：限制只能在当前类内部使用
     *
     * 下面 new 了一个Lock，则表示每一个ReferenceQueue对象内部都会有一个Lock对象
     *
     */
    static private class Lock { };
    private Lock lock = new Lock();
    private volatile Reference<? extends T> head = null;
    private long queueLength = 0;

    /**
     * Called only by Reference class
     * 仅由Reference类调用
     *
     * 该方法在 Reference 中的ReferenceHandler线程中被调用
     *
     *
     * 该方法的主要作用是将 目标对象 也就是参数Reference对象，设置到当前对象ReferenceQueue 所表示的队列中。
     * 首先当前对象ReferenceQueue 表示一个队列，这个队列 实际上是一个链表，ReferenceQueue通过内部的属性 Reference head 持有这个链表。
     * 队列中的每一个节点就是一个Reference对象，这个Reference对象内部有一个next 属性，指向当前Reference对象在队列中的下一个元素。
     *
     * 因此，参数Reference对象 进入队列的逻辑 就是将 首先将当前ReferenceQueue的head设置为 参数Reference对象的next。
     * 然后将参数Reference对象作为ReferenceQueue的head
     *
     * ====================
     *
     * ReferenceQueue是一个 先进后出（FILO）的队列，用于保存注册在这个队列的且引用的对象已经被垃圾收集器回收的引用对象。这个队列的实现其实是由Reference和ReferenceQueue共同完成的
     *
     * ReferenceQueue类中保存一个Reference对象的属性head，用于表示队列的队顶。
     *
     * 每个Reference对象都有一个Reference对象的属性next，用于保存队列中下一个元素
     *
     *
     * ============
     * Reference是如何被放入队列的
     *
     * Reference类有一个内部静态类ReferenceHandler extends Thread，这个内部类中有静态代码块生成该类的一个守护线程对象并启动。那这个静态内部类的run方法做了什么，有什么作用呢？
     *
     * @param r
     * @return
     */
    boolean enqueue(Reference<? extends T> r) { /* Called only by Reference class */
        synchronized (lock) {
            // Check that since getting the lock this reference hasn't already been
            // enqueued (and even then removed)
            /**
             // 检查自从获得锁后，这个引用还没有被
             // 入队（甚至被移除）
              (1)获取引用对象中的queue，如果创建该引用对象的时候没有传递queue则 queue=null
              (2)判断queue是否等于Enqueued，如果Reference引用对象已经被放入到了queue中，这个时候其queue就变成了Enqueued。
             */
            ReferenceQueue<?> queue = r.queue;
            if ((queue == NULL) || (queue == ENQUEUED)) {
                return false;
            }
            /**
             * assert关键字语法很简单，有两种用法：
             *
             * 1、assert <boolean表达式>
             * 如果<boolean表达式>为true，则程序继续执行。
             * 如果为false，则程序抛出AssertionError，并终止执行。
             *
             * 2、assert <boolean表达式> : <错误信息表达式>
             * 如果<boolean表达式>为true，则程序继续执行。
             * 如果为false，则程序抛出java.lang.AssertionError，并输入<错误信息表达式>。
             *
             * 判断Reference对象中的queue属性等于this。
             * 当前对象this是ReferenceQueue类型的对象，
             * 我们在创建Reference对象的时候 提供的queue也是ReferenceQueue类型的对象，且语义上：ReferenceHandlerThread 将Reference对象
             * 放置到其queue属性所指向的ReferenceQueue中。
             *
             * 这里的assert就是判断 Reference对象所进入的 那个ReferenceQueue是不是其queue属性所指定的ReferenceQueue。
             * 因为enquue方法是ReferenceQueue的方法，我们完全可以拿着另外一个ReferenceQueue对象，调用其enqueu方法传入当前的Reference对象
             * 这样就意味着将Reference对象放置到其他的ReferenceQueue中了，所以这里校验Reference对象所被放入到的ReferenceQueue是不是其queue属性
             * 所指定的ReferenceQueue。
             *
             */
            assert queue == this;
            /**
             * 将Reference对象的queue属性设置为Enqueued，表示该引用对象已经被放入到了Reference对象中的属性queue所引用的ReferenceQueue中了
             */
            r.queue = ENQUEUED;
            /**
             * 注意这个r是Reference对象，将原来的head保存到Reference中的next。
             * 然后原来的head设置为当前的Reference对象
             */
            r.next = (head == null) ? r : head;
            head = r;
            queueLength++;
            if (r instanceof FinalReference) {
                sun.misc.VM.addFinalRefCount(1);
            }
            /**
             * 上面 使用synchronized获取到了lock的锁，因此这里可以调用notifyAll。
             *
             * 问题：为什么这里要调用notifyAll？
             * 要解释这个问题我们需要找到 哪里调用了 lock对象的wait，另外并不是调用了notifyAll就会立刻唤醒，应该是退出synchronized的时候才会唤醒；
             *唤醒调用remove的线程
             */
            lock.notifyAll();
            return true;
        }
    }

    private Reference<? extends T> reallyPoll() {       /* Must hold lock */
        Reference<? extends T> r = head;
        if (r != null) {
            /**
             *取出 head的next， 将这个next作为head值
             *
             * 原来的head的next设置为自身，原来head的queue清空
             * 将原来的head返回，因此这里取出的是head。
             * 也就是后进先出，如果一个元素是后来进入的，从enqueu方法中我们看到 这个元素会被作为head
             * 如果从队列中取出一个元素，则取出的这个元素是head，因此就是一个后进先出的队列
             */
            @SuppressWarnings("unchecked")
            Reference<? extends T> rn = r.next;
            head = (rn == r) ? null : rn;
            r.queue = NULL;
            r.next = r;
            queueLength--;
            if (r instanceof FinalReference) {
                sun.misc.VM.addFinalRefCount(-1);
            }
            return r;
        }
        return null;
    }

    /**
     * Polls this queue to see if a reference object is available.  If one is
     * available without further delay then it is removed from the queue and
     * returned.  Otherwise this method immediately returns <tt>null</tt>.
     *
     * @return  A reference object, if one was immediately available,
     *          otherwise <code>null</code>
     *轮询该队列，以查看引用对象是否可用。如果其中一个是可用的，并且没有进一步的延迟，那么它将从队列中删除并返回。否则该方法将立即返回null。
     *    //出队操作，如果没有元素直接返回null
     */
    public Reference<? extends T> poll() {
        if (head == null)
            return null;
        synchronized (lock) {
            return reallyPoll();
        }
    }

    /**
     * Removes the next reference object in this queue, blocking until either
     * one becomes available or the given timeout period expires.
     *
     * <p> This method does not offer real-time guarantees: It schedules the
     * timeout as if by invoking the {@link Object#wait(long)} method.
     *
     * @param  timeout  If positive, block for up to <code>timeout</code>
     *                  milliseconds while waiting for a reference to be
     *                  added to this queue.  If zero, block indefinitely.
     *
     * @return  A reference object, if one was available within the specified
     *          timeout period, otherwise <code>null</code>
     *
     * @throws  IllegalArgumentException
     *          If the value of the timeout argument is negative
     *
     * @throws  InterruptedException
     *          If the timeout wait is interrupted
     *
     *   //出队，如果没有元素那么最多等待timeOut毫秒直到超时或者有元素入队
     */
    public Reference<? extends T> remove(long timeout)
        throws IllegalArgumentException, InterruptedException
    {
        if (timeout < 0) {
            throw new IllegalArgumentException("Negative timeout value");
        }
        synchronized (lock) {
            Reference<? extends T> r = reallyPoll();
            if (r != null) return r;
            long start = (timeout == 0) ? 0 : System.nanoTime();
            for (;;) {
                lock.wait(timeout);
                r = reallyPoll();
                if (r != null) return r;
                if (timeout != 0) {
                    long end = System.nanoTime();
                    timeout -= (end - start) / 1000_000;
                    if (timeout <= 0) return null;
                    start = end;
                }
            }
        }
    }

    /**
     * Removes the next reference object in this queue, blocking until one
     * becomes available.
     *
     * @return A reference object, blocking until one becomes available
     * @throws  InterruptedException  If the wait is interrupted
     */
    public Reference<? extends T> remove() throws InterruptedException {
        return remove(0);
    }

    /**
     * Iterate queue and invoke given action with each Reference.
     * Suitable for diagnostic purposes.
     * WARNING: any use of this method should make sure to not
     * retain the referents of iterated references (in case of
     * FinalReference(s)) so that their life is not prolonged more
     * than necessary.
     */
    void forEach(Consumer<? super Reference<? extends T>> action) {
        for (Reference<? extends T> r = head; r != null;) {
            action.accept(r);
            @SuppressWarnings("unchecked")
            Reference<? extends T> rn = r.next;
            if (rn == r) {
                if (r.queue == ENQUEUED) {
                    // still enqueued -> we reached end of chain
                    r = null;
                } else {
                    // already dequeued: r.queue == NULL; ->
                    // restart from head when overtaken by queue poller(s)
                    r = head;
                }
            } else {
                // next in chain
                r = rn;
            }
        }
    }
}
