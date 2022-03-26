/*
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

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent.atomic;
import java.util.function.LongUnaryOperator;
import java.util.function.LongBinaryOperator;
import sun.misc.Unsafe;

/**
 * A {@code long} value that may be updated atomically.  See the
 * {@link java.util.concurrent.atomic} package specification for
 * description of the properties of atomic variables. An
 * {@code AtomicLong} is used in applications such as atomically
 * incremented sequence numbers, and cannot be used as a replacement
 * for a {@link java.lang.Long}. However, this class does extend
 * {@code Number} to allow uniform access by tools and utilities that
 * deal with numerically-based classes.
 *
 * @since 1.5
 * @author Doug Lea
 */
public class AtomicLong extends Number implements java.io.Serializable {
    private static final long serialVersionUID = 1927816293512124184L;

    // setup to use Unsafe.compareAndSwapLong for updates
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final long valueOffset;

    /**
     * Records whether the underlying JVM supports lockless
     * compareAndSwap for longs. While the Unsafe.compareAndSwapLong
     * method works in either case, some constructions should be
     * handled at Java level to avoid locking user-visible locks.
     */
    static final boolean VM_SUPPORTS_LONG_CAS = VMSupportsCS8();

    /**
     * Returns whether underlying JVM supports lockless CompareAndSet
     * for longs. Called only once and cached in VM_SUPPORTS_LONG_CAS.
     */
    private static native boolean VMSupportsCS8();

    static {
        try {
            valueOffset = unsafe.objectFieldOffset
                (AtomicLong.class.getDeclaredField("value"));
        } catch (Exception ex) { throw new Error(ex); }
    }

    private volatile long value;

    /**
     * Creates a new AtomicLong with the given initial value.
     *
     * @param initialValue the initial value
     */
    public AtomicLong(long initialValue) {
        value = initialValue;
    }

    /**
     * Creates a new AtomicLong with initial value {@code 0}.
     */
    public AtomicLong() {
    }

    /**
     * Gets the current value.
     *
     * @return the current value
     */
    public final long get() {
        return value;
    }

    /**
     * Sets to the given value.
     *
     * @param newValue the new value
     */
    public final void set(long newValue) {
        value = newValue;
    }

    /**
     * Eventually sets to the given value.
     *最终设置为给定值。
     * J.U.C原子工具类AtomicXXX中，set和lazySet的区别:https://blog.csdn.net/aitangyong/article/details/41577503
     *
     * 按照javadoc的字面意思：set()会立刻修改旧值，别的线程可以立刻看到更新后的值；而lazySet不会立刻(但是最终会)修改旧值，
     * 别的线程看到新值的时间会延迟一些。JDK bug database上有对lazySet的详细描述：
     * http://bugs.java.com/bugdatabase/view_bug.do?bug_id=7065550
     *
     * http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6275329
     * As probably the last little JSR166 follow-up for Mustang, we added a "lazySet" method to the Atomic classes (AtomicInteger, AtomicReference, etc). This is a niche method that is sometimes useful when fine-tuning code using non-blocking data structures. The semantics are that the write is guaranteed not to be re-ordered with any previous write, but may be reordered with subsequent operations (or equivalently, might not be visible to other threads) until some other volatile write or synchronizing action occurs).
     *
     * The main use case is for nulling out fields of nodes in non-blocking data structures solely for the sake of avoiding long-term garbage retention; it applies when it is harmless if other threads see non-null values for a while, but you'd like to ensure that structures are eventually GCable. In such cases, you can get better performance by avoiding the costs of the null volatile-write. There are a few other use cases along these lines for non-reference-based atomics as well, so the method is supported across all of the AtomicX classes.
     *
     * For people who like to think of these operations in terms of machine-level barriers on common multiprocessors, lazySet provides a preceeding store-store barrier (which is either a no-op or very cheap on current platforms), but no store-load barrier (which is usually the expensive part of a volatile-write).
     *
     *
     * set()和volatile具有一样的效果(能够保证内存可见性，能够避免指令重排序)，但是使用lazySet不能保证其他线程能立刻看到修改后的值(有可能发生指令重排序)。简单点理解：lazySet比set()具有性能优势，但是使用场景很有限。在网上没有找到lazySet和set的性能数据对比，而且CPU的速度很快的，应用的瓶颈往往不在CPU，而是在IO、网络、数据库等。对于并发程序要优先保证正确性，然后出现性能瓶颈的时候再去解决。因为定位并发导致的问题，往往要比定位性能问题困难很多。
     *
     * 在stackoverflow上，也有很人提问set和lazySet的问题：
     *
     * http://stackoverflow.com/questions/1468007/atomicinteger-lazyset-vs-set
     *
     * http://stackoverflow.com/questions/25840733/atomic-integer-lazyset-performance-gains
     *
     * http://stackoverflow.com/questions/7557156/atomicxxx-lazyset-in-terms-of-happens-before-edges
     *
     * ==================
     * lazySet是使用Unsafe.putOrderedObject方法，这个方法在对低延迟代码是很有用的，它能够实现非阻塞的写入，
     * 这些写入不会被Java的JIT重新排序指令(instruction reordering)，这样它使用快速的存储-存储(store-store) barrier,
     * 而不是较慢的存储-加载(store-load) barrier, 后者总是用在volatile的写操作上，这种性能提升是有代价的，虽然便宜，也就是写
     * 后结果并不会被其他线程看到，甚至是自己的线程，通常是几纳秒后被其他线程看到，这个时间比较短，所以代价可以忍受。
     * 设想如下场景: 设置一个 volatile 变量为 null，让这个对象被 GC 掉，volatile write 是消耗比较大（store-load 屏障）的，
     * 但是 putOrderedInt 只会加 store-store 屏障，损耗会小一些。
     *
     * @param newValue the new value
     * @since 1.6
     */
    public final void lazySet(long newValue) {
        unsafe.putOrderedLong(this, valueOffset, newValue);
    }

    /**
     * Atomically sets to the given value and returns the old value.
     *
     * @param newValue the new value
     * @return the previous value
     */
    public final long getAndSet(long newValue) {
        return unsafe.getAndSetLong(this, valueOffset, newValue);
    }

    /**
     * Atomically sets the value to the given updated value
     * if the current value {@code ==} the expected value.
     *
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that
     * the actual value was not equal to the expected value.
     */
    public final boolean compareAndSet(long expect, long update) {
        return unsafe.compareAndSwapLong(this, valueOffset, expect, update);
    }

    /**
     * Atomically sets the value to the given updated value
     * if the current value {@code ==} the expected value.
     *
     * <p><a href="package-summary.html#weakCompareAndSet">May fail
     * spuriously and does not provide ordering guarantees</a>, so is
     * only rarely an appropriate alternative to {@code compareAndSet}.
     *
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful
     */
    public final boolean weakCompareAndSet(long expect, long update) {
        return unsafe.compareAndSwapLong(this, valueOffset, expect, update);
    }

    /**
     * Atomically increments by one the current value.
     *
     * @return the previous value
     */
    public final long getAndIncrement() {
        /**
         * 源码getAndAddLong(Object o, long offset, long delta)中do while循环体是实现的关键所在，其逻辑是：
         * 第一步先取得AtomicLong里存储的数值，
         * 第二步对AtomicLong的当前数值进行加1操作，
         * 第三步调用weakCompareAndSetLong方法来进行原子更新操作，该方法先检查当前数值是否等于v，等
         * 于意味着AtomicLong的值没有被其他线程修改过，则将weakCompareAndSetLong的当前数值更新成v+delta的值，如果不等于v，
         * weakCompareAndSetLong方法会返回false，程序会进入do while循环重新进行weakCompareAndSetLong操作。
         * 这里隐含了一个问题，当对于共享变量（假设变量名字是a）的竞争非常激烈的时候，在当前线程读取a、改变a之间，a的值会被别的线程改变，从而导致当前线程一直重试（自旋），一直占用CPU。
         * 这就引出另一个问题，对于锁抢占很激烈的时候，串行是最好的解决办法。比如使用synchronized。
         *
         * java.util.concurrent.atomic中的原子操作基本是基于Unsafe或者VarHandle实现的。
         *
         *
         */
        return unsafe.getAndAddLong(this, valueOffset, 1L);
    }

    /**
     * Atomically decrements by one the current value.
     *
     * @return the previous value
     */
    public final long getAndDecrement() {
        return unsafe.getAndAddLong(this, valueOffset, -1L);
    }

    /**
     * Atomically adds the given value to the current value.
     *
     * @param delta the value to add
     * @return the previous value
     */
    public final long getAndAdd(long delta) {
        return unsafe.getAndAddLong(this, valueOffset, delta);
    }

    /**
     * Atomically increments by one the current value.
     *
     * @return the updated value
     */
    public final long incrementAndGet() {
        return unsafe.getAndAddLong(this, valueOffset, 1L) + 1L;
    }

    /**
     * Atomically decrements by one the current value.
     *
     * @return the updated value
     */
    public final long decrementAndGet() {
        return unsafe.getAndAddLong(this, valueOffset, -1L) - 1L;
    }

    /**
     * Atomically adds the given value to the current value.
     *
     * @param delta the value to add
     * @return the updated value
     */
    public final long addAndGet(long delta) {
        return unsafe.getAndAddLong(this, valueOffset, delta) + delta;
    }

    /**
     * Atomically updates the current value with the results of
     * applying the given function, returning the previous value. The
     * function should be side-effect-free, since it may be re-applied
     * when attempted updates fail due to contention among threads.
     *
     * @param updateFunction a side-effect-free function
     * @return the previous value
     * @since 1.8
     */
    public final long getAndUpdate(LongUnaryOperator updateFunction) {
        long prev, next;
        do {
            prev = get();
            next = updateFunction.applyAsLong(prev);
        } while (!compareAndSet(prev, next));
        return prev;
    }

    /**
     * Atomically updates the current value with the results of
     * applying the given function, returning the updated value. The
     * function should be side-effect-free, since it may be re-applied
     * when attempted updates fail due to contention among threads.
     *
     * @param updateFunction a side-effect-free function
     * @return the updated value
     * @since 1.8
     */
    public final long updateAndGet(LongUnaryOperator updateFunction) {
        long prev, next;
        do {
            prev = get();
            next = updateFunction.applyAsLong(prev);
        } while (!compareAndSet(prev, next));
        return next;
    }

    /**
     * Atomically updates the current value with the results of
     * applying the given function to the current and given values,
     * returning the previous value. The function should be
     * side-effect-free, since it may be re-applied when attempted
     * updates fail due to contention among threads.  The function
     * is applied with the current value as its first argument,
     * and the given update as the second argument.
     *
     * @param x the update value
     * @param accumulatorFunction a side-effect-free function of two arguments
     * @return the previous value
     * @since 1.8
     */
    public final long getAndAccumulate(long x,
                                       LongBinaryOperator accumulatorFunction) {
        long prev, next;
        do {
            prev = get();
            next = accumulatorFunction.applyAsLong(prev, x);
        } while (!compareAndSet(prev, next));
        return prev;
    }

    /**
     * Atomically updates the current value with the results of
     * applying the given function to the current and given values,
     * returning the updated value. The function should be
     * side-effect-free, since it may be re-applied when attempted
     * updates fail due to contention among threads.  The function
     * is applied with the current value as its first argument,
     * and the given update as the second argument.
     *
     * @param x the update value
     * @param accumulatorFunction a side-effect-free function of two arguments
     * @return the updated value
     * @since 1.8
     */
    public final long accumulateAndGet(long x,
                                       LongBinaryOperator accumulatorFunction) {
        long prev, next;
        do {
            prev = get();
            next = accumulatorFunction.applyAsLong(prev, x);
        } while (!compareAndSet(prev, next));
        return next;
    }

    /**
     * Returns the String representation of the current value.
     * @return the String representation of the current value
     */
    public String toString() {
        return Long.toString(get());
    }

    /**
     * Returns the value of this {@code AtomicLong} as an {@code int}
     * after a narrowing primitive conversion.
     * @jls 5.1.3 Narrowing Primitive Conversions
     */
    public int intValue() {
        return (int)get();
    }

    /**
     * Returns the value of this {@code AtomicLong} as a {@code long}.
     */
    public long longValue() {
        return get();
    }

    /**
     * Returns the value of this {@code AtomicLong} as a {@code float}
     * after a widening primitive conversion.
     * @jls 5.1.2 Widening Primitive Conversions
     */
    public float floatValue() {
        return (float)get();
    }

    /**
     * Returns the value of this {@code AtomicLong} as a {@code double}
     * after a widening primitive conversion.
     * @jls 5.1.2 Widening Primitive Conversions
     */
    public double doubleValue() {
        return (double)get();
    }

}
