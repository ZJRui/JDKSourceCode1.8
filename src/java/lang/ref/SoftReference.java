/*
 * Copyright (c) 1997, 2003, Oracle and/or its affiliates. All rights reserved.
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


/**
 * Soft reference objects, which are cleared at the discretion of the garbage
 * collector in response to memory demand.  Soft references are most often used
 * to implement memory-sensitive caches.
 * 软引用对象，垃圾收集器根据内存需求自行清除这些对象。软引用最常用于实现对内存敏感的缓存。
 *
 * <p> Suppose that the garbage collector determines at a certain point in time
 * that an object is <a href="package-summary.html#reachability">softly
 * reachable</a>.  At that time it may choose to clear atomically all soft
 * references to that object and all soft references to any other
 * softly-reachable objects from which that object is reachable through a chain
 * of strong references.  At the same time or at some later time it will
 * enqueue those newly-cleared soft references that are registered with
 * reference queues.
 *
 *假设垃圾收集器在某个时间点确定对象是软可达的。此时，它可以选择以原子方式清除对该对象的所有软引用，
 * 以及对通过强引用链可访问该对象的任何其他软可访问对象的所有软引用。在同一时间或稍后的时间，
 * 它将把那些新清除的、已注册到引用队列中的软引用放入队列。
 *
 *
 * <p> All soft references to softly-reachable objects are guaranteed to have
 * been cleared before the virtual machine throws an
 * <code>OutOfMemoryError</code>.  Otherwise no constraints are placed upon the
 * time at which a soft reference will be cleared or the order in which a set
 * of such references to different objects will be cleared.  Virtual machine
 * implementations are, however, encouraged to bias against clearing
 * recently-created or recently-used soft references.
 *
 * 在虚拟机抛出OutOfMemoryError之前，所有对软可达对象的软引用都保证已被清除。
 * 否则，对软引用被清除的时间或对不同对象的一组这样的引用被清除的顺序没有任何限制。
 * 但是，鼓励虚拟机实现偏向于清除最近创建或最近使用的软引用。
 *
 * <p> Direct instances of this class may be used to implement simple caches;
 * this class or derived subclasses may also be used in larger data structures
 * to implement more sophisticated caches.  As long as the referent of a soft
 * reference is strongly reachable, that is, is actually in use, the soft
 * reference will not be cleared.  Thus a sophisticated cache can, for example,
 * prevent its most recently used entries from being discarded by keeping
 * strong referents to those entries, leaving the remaining entries to be
 * discarded at the discretion of the garbage collector.
 * 这个类的直接实例可以用来实现简单的缓存;这个类或派生的子类也可以在更大的数据结构中使用，
 * 以实现更复杂的缓存。只要软引用的引用是强可达的，也就是说，它实际上是在使用的，
 * 软引用就不会被清除。因此，例如，一个成熟的缓存可以通过保持对这些条目的强引用来防止其最近使用的条目被丢弃，而让垃圾收集器自行决定是否丢弃剩余的条目。
 *
 * @author   Mark Reinhold
 * @since    1.2
 */

public class SoftReference<T> extends Reference<T> {

    /**
     * Timestamp clock, updated by the garbage collector
     */
    static private long clock;

    /**
     * Timestamp updated by each invocation of the get method.  The VM may use
     * this field when selecting soft references to be cleared, but it is not
     * required to do so.
     * 每次调用get方法时更新的时间戳。虚拟机在选择需要清除的软引用时，可能会使用该字段，但不需要。
     */
    private long timestamp;

    /**
     * Creates a new soft reference that refers to the given object.  The new
     * reference is not registered with any queue.
     *
     * @param referent object the new soft reference will refer to
     */
    public SoftReference(T referent) {
        super(referent);
        this.timestamp = clock;
    }

    /**
     * Creates a new soft reference that refers to the given object and is
     * registered with the given queue.
     *
     * @param referent object the new soft reference will refer to
     * @param q the queue with which the reference is to be registered,
     *          or <tt>null</tt> if registration is not required
     *
     */
    public SoftReference(T referent, ReferenceQueue<? super T> q) {
        super(referent, q);
        this.timestamp = clock;
    }

    /**
     * Returns this reference object's referent.  If this reference object has
     * been cleared, either by the program or by the garbage collector, then
     * this method returns <code>null</code>.
     * 返回这个引用对象的引用。如果这个引用对象已经被程序或垃圾回收器清除，那么这个方法将返回null。
     *
     * @return   The object to which this reference refers, or
     *           <code>null</code> if this reference object has been cleared
     */
    public T get() {
        T o = super.get();
        if (o != null && this.timestamp != clock)
            this.timestamp = clock;
        return o;
    }

}
