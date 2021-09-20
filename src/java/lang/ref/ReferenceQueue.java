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
             * 将Reference对象的queue属性设置为Enqueued，表示该引用对象已经被放入到了queue中
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
             * 上面 使用synchronized获取到了lock的锁，因此这里可以调用notifyAll
             */
            lock.notifyAll();
            return true;
        }
    }

    private Reference<? extends T> reallyPoll() {       /* Must hold lock */
        Reference<? extends T> r = head;
        if (r != null) {
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
