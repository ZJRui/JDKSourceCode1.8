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
import java.util.function.LongBinaryOperator;
import java.util.function.DoubleBinaryOperator;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A package-local class holding common representation and mechanics
 * for classes supporting dynamic striping on 64bit values. The class
 * extends Number so that concrete subclasses must publicly do so.
 *
 * 一个包本地类，为支持 64 位值动态条带化的类提供通用表示和机制。
 * 该类扩展了 Number 以便具体的子类必须公开这样做。
 */
@SuppressWarnings("serial")
abstract class Striped64 extends Number {
    /*
     * This class maintains a lazily-initialized table of atomically
     * updated variables, plus an extra "base" field.
     * 这个类维护一个惰性树池化的原子表更新的变量，加上一个额外的 base 字段。
     *  The table size is a power of two.
     * table的大小是2的幂次。
     * Indexing uses masked per-thread hash codes.
     * 索引使用 maske线程的哈希值
     * Nearly all declarations in this class are package-private,
     * accessed directly by subclasses.
     * 这个类中几乎所有的声明都是包私有的，由子类直接访问。
     *
     * Table entries are of class Cell; 表元素是cell类
     *  a variant of AtomicLong padded
     * (via @sun.misc.Contended) to reduce cache contention.
     * AtomicLong填充的变体（通过Contended 减少缓冲争用）
     *  Padding is overkill for most Atomics because they are usually
     * irregularly scattered in memory and thus don't interfere much
     * with each other.
     * 填充对于大多数Atomics来说是矫枉过正，因为他们通常不规则地分散在内存中，因此不会不会干扰太多彼此
     *
     * But Atomic objects residing in arrays will
     * tend to be placed adjacent to each other, and so will most
     * often share cache lines (with a huge negative performance
     * impact) without this precaution.
     * 但是驻留在数组中的原子对象会往往批次相邻放置，大多数情况下也是如此，经常共享缓存行（具有巨大的负面性能影响）没有这种预防措施
     *
     *
     * In part because Cells are relatively large, we avoid creating
     * them until they are needed.
     * 部分是因为Cell比较大，我们避免创建他们知道他们被需要。
     * When there is no contention, all
     * updates are made to the base field.
     * 没有争执时，所有对基本字段进行更新。
     *
     * Upon first contention (a
     * failed CAS on base update), the table is initialized to size 2.
     *
     * 在第一次竞争时（一个对base的更新cas失败），表被初始化为大小2.
     *
     * The table size is doubled upon further contention until
     * reaching the nearest power of two greater than or equal to the
     * number of CPUS. Table slots remain empty (null) until they are
     * needed.
     * 表大小进一步争用时翻倍，直到达到大于或等于2的最接近的幂CPU数量。 表槽保持为 空，直到他们需要。
     *
     *
     * A single spinlock ("cellsBusy") is used for initializing and
     * resizing the table, as well as populating slots with new Cells.
     * 一个自旋锁 用于初始化和调整表格大小，以及用新单元格填充插槽。
     *
     * There is no need for a blocking lock; when the lock is not
     * available, threads try other slots (or the base).
     * 不需要阻塞锁，当没有锁可用，线程尝试其他插槽（或者base）。
     *
     * During these
     * retries, there is increased contention and reduced locality,
     * which is still better than alternatives.
     * 在这些器件重试，竞争增加，局部性减少，这仍然比替代品更好。
     *
     *
     * The Thread probe fields maintained via ThreadLocalRandom serve
     * as per-thread hash codes.
     * 通过ThreadLocalRandom服务维护的Thread探测字段作为每个线程的哈希码。
     *
     * We let them remain uninitialized as
     * zero (if they come in this way) until they contend at slot
     * 0.
     * 我们让他们保持未初始化状态零（如果他们以这种方式出现） 直到他们在插槽中竞争0.
     *
     *  They are then initialized to values that typically do not
     * often conflict with others.
     * 然后将他们初始化为通常不会的值 经常与其他线程发生冲突。
     *
     * Contention and/or table collisions
     * are indicated by failed CASes when performing an update
     * operation.
     * 争用或者表冲突 在执行更新时由失败的cas指示。
     *
     * Upon a collision, if the table size is less than
     * the capacity, it is doubled in size unless some other thread
     * holds the lock.
     * 发生冲突时，如果表大小小于容量，除非有其他线程，否则他的大小会增加一倍持有锁。
     *
     * If a hashed slot is empty, and lock is
     * available, a new Cell is created.
     * 如果散列槽是空的，并且锁是可用，创建一个新的单元格。
     * Otherwise, if the slot
     * exists, a CAS is tried.
     * 否则，如果插槽存在，尝试cas
     * Retries proceed by "double hashing",
     * using a secondary hash (Marsaglia XorShift) to try to find a
     * free slot.
     * 重试通过双重哈希进行，使用二级哈希尝试找到一个免费插槽。
     *
     *
     * The table size is capped because, when there are more threads
     * than CPUs, supposing that each thread were bound to a CPU,
     * there would exist a perfect hash function mapping threads to
     * slots that eliminates collisions.
     *
     * 表大小有上限，因为当有更多线程时比cpu，假设每个线程都绑定到一个cpu，会有一个完美的哈希函数映射线程到消除冲突的插槽。
     *
     *  When we reach capacity, we
     * search for this mapping by randomly varying the hash codes of
     * colliding threads.
     * 当我们达到容量时，我们通过随机改变哈希码来搜索这个映射冲突线程。
     *
     * Because search is random, and collisions
     * only become known via CAS failures, convergence can be slow,
     * and because threads are typically not bound to CPUS forever,
     * may not occur at all.
     * 因为搜索是随机的，并且冲突仅通过cas故障才知道，收敛可能很慢，并且因为线程通常不会永远绑定到cpu，
     * 可能根本不会发生。
     *  However, despite these limitations,
     * observed contention rates are typically low in these cases.
     *然而，尽管有这些限制，在这些情况下，观察到的争用频率通常很低。
     *
     *
     * It is possible for a Cell to become unused when threads that
     * once hashed to it terminate, as well as in the case where
     * doubling the table causes no thread to hash to it under
     * expanded mask.
     * 当线程 一旦散列到它终止，以及在以下情况下将表加倍不会导致任何线程对其进行哈希运算扩大面具
     *
     * We do not try to detect or remove such cells,
     * under the assumption that for long-running instances, observed
     * contention levels will recur, so the cells will eventually be
     * needed again; and for short-lived ones, it does not matter.
     * 我们不会尝试检测或移除此类 cells， 假设对于长时间运行的实例，观察到争用级别将重复出现，因此单元格最终将是再次需要； 对于短命的，没关系。
     */

    /**
     * Padded variant of AtomicLong supporting only raw accesses plus CAS.
     * AtomicLong的填充变体支持原始访问和Cas。
     *
     * JVM intrinsics note: It would be possible to use a release-only
     * form of CAS here, if it were provided.
     * JVM内部函数注意： 如果提供了cas，则可以在此处使用仅发布形式的cas。
     *
     */
    @sun.misc.Contended static final class Cell {
        volatile long value;
        Cell(long x) { value = x; }
        final boolean cas(long cmp, long val) {
            return UNSAFE.compareAndSwapLong(this, valueOffset, cmp, val);
        }

        // Unsafe mechanics
        private static final sun.misc.Unsafe UNSAFE;
        private static final long valueOffset;
        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> ak = Cell.class;
                valueOffset = UNSAFE.objectFieldOffset
                    (ak.getDeclaredField("value"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /** Number of CPUS, to place bound on table size */
    static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * Table of cells. When non-null, size is a power of 2.
     * cells数组是LongAdder高性能实现的必杀器：
     *
     * AtomicInteger只有一个value，所有线程累加都要通过cas竞争value这一个变量，高并发下线程争用非常严重；
     *
     * 而LongAdder则有两个值用于累加，一个是base，它的作用类似于AtomicInteger里面的value，在没有竞争的情况不会用到cells数组，它为null，
     * 这时使用base做累加，有了竞争后cells数组就上场了，第一次初始化长度为2，以后每次扩容都是变为原来的两倍，
     * 直到cells数组的长度大于等于当前服务器cpu的数量为止就不在扩容；每个线程会通过线程对cells[threadLocalRandomProbe%cells.length]
     * 位置的Cell对象中的value做累加，这样相当于将线程绑定到了cells中的某个cell对象上；
     *
     */
    transient volatile Cell[] cells;

    /**
     * Base value, used mainly when there is no contention, but also as
     * a fallback during table initialization races. Updated via CAS.
     *
     * 基础值，主要在没有争用的时候使用，也可以作为表初始化竞争期间的后备 。通过cas更新
     * 两个作用：
     * 1，在开始没有竞争的情况下，将累加值累加到base
     * 2，在cells初始化的过程中，cells不可用，这时会尝试将值累加到base上。
     *
     */
    transient volatile long base;

    /**
     * Spinlock (locked via CAS) used when resizing and/or creating Cells.
     *调整大小 或创建单元格时使用自旋锁（通过cas锁定）
     *
     * 他有两个值0或者1，，它的作用是当要修改cells数组时加锁，防止多线程同时修改cells数组，0为无锁，1为加锁，加锁的状况有三种
     * 1. cells数组初始化的时候；
     *
     * 2. cells数组扩容的时候；
     *
     * 3. 如果cells数组中某个元素为null，给这个位置创建新的Cell对象的时候；
     *
     *
     */
    transient volatile int cellsBusy;

    /**
     * Package-private default constructor
     */
    Striped64() {
    }

    /**
     * CASes the base field.
     */
    final boolean casBase(long cmp, long val) {
        return UNSAFE.compareAndSwapLong(this, BASE, cmp, val);
    }

    /**
     * CASes the cellsBusy field from 0 to 1 to acquire lock.
     */
    final boolean casCellsBusy() {
        return UNSAFE.compareAndSwapInt(this, CELLSBUSY, 0, 1);
    }

    /**
     * Returns the probe value for the current thread.
     * Duplicated from ThreadLocalRandom because of packaging restrictions.
     */
    static final int getProbe() {
        return UNSAFE.getInt(Thread.currentThread(), PROBE);
    }

    /**
     * Pseudo-randomly advances and records the given probe value for the
     * given thread.
     * Duplicated from ThreadLocalRandom because of packaging restrictions.
     */
    static final int advanceProbe(int probe) {
        probe ^= probe << 13;   // xorshift
        probe ^= probe >>> 17;
        probe ^= probe << 5;
        UNSAFE.putInt(Thread.currentThread(), PROBE, probe);
        return probe;
    }

    /**
     * Handles cases of updates involving initialization, resizing,
     * creating new Cells, and/or contention.
     * 处理涉及初始化、调整大小、创建新单元或争用的更新
     *
     * See above for
     * explanation. This method suffers the usual non-modularity
     * problems of optimistic retry code, relying on rechecked sets of
     * reads.
     *该方法具有通常的非模块化 乐观重试代码的问题，依赖于重新检查的集合读取。
     *
     *
     * @param x the value
     * @param fn the update function, or null for add (this convention
     * avoids the need for an extra field or function in LongAdder).
     * @param wasUncontended false if CAS failed before call
     */
    final void longAccumulate(long x, LongBinaryOperator fn,
                              boolean wasUncontended) {
        /**
         * 三个参数第一个为要累加的值，第二个为null，第三个为wasUncontended表示调用方法之前的add方法是否未发生竞争;
         */
        int h;
        /**
         * 获取线程对象的 标记为属性threadLocalRandomProbe ，如果为0表示线程的随机种子值没有被初始化.
         *
         *     //获取当前线程的threadLocalRandomProbe值作为hash值,如果当前线程的threadLocalRandomProbe为0，说明当前线程是第一次进入该方法，
         *     则强制设置线程的threadLocalRandomProbe为ThreadLocalRandom类的成员静态私有变量probeGenerator的值，后面会详细说hash值的生成;
         *         //另外需要注意，如果threadLocalRandomProbe=0，代表新的线程开始参与cell争用的情况
         *         //1.当前线程之前还没有参与过cells争用（也许cells数组还没初始化，进到当前方法来就是为了初始化cells数组后争用的）,是第一次执行base的cas累加操作失败；
         *         //2.或者是在执行add方法时，对cells某个位置的Cell的cas操作第一次失败，则将wasUncontended设置为false，那么这里会将其重新置为true；第一次执行操作失败；
         *        //凡是参与了cell争用操作的线程threadLocalRandomProbe都不为0；
         *
         *
         */
        if ((h = getProbe()) == 0) {
            /**
             * 对当前线程的种子值进行初始化。
             */
            ThreadLocalRandom.current(); // force initialization
            /**
             * Thread对象的threadLocalRandomProbe 属性在ThreadLocalRandom中的current方法中被设置为 0x9e3779b9
             *
             * 这个值在下面被用来和 cells数组的长度与运算，为什么要使用这个值？
             *
             */
            h = getProbe();
            /**
             * 设置为竞争标记为true ，为什么？
             * 在java.util.concurrent.atomic.LongAdder#add(long) 方法中，如果cas操作失败了 则uncontended 为false， 也就是wasUnContended为false。
             * 这里为什么设置为true？
             *
             */
            wasUncontended = true;
        }
        /**
         * //cas冲突标志，表示当前线程hash到的Cells数组的位置，做cas累加操作时与其它线程发生了冲突，cas失败；collide=true代表有冲突，collide=false代表无冲突
         */
        boolean collide = false;                // True if last slot nonempty
        for (;;) {
            Cell[] as; Cell a; int n; long v;
            /**
             *    //这个主干if有三个分支
             *             //1.主分支一：处理cells数组已经正常初始化了的情况（这个if分支处理add方法的四个条件中的3和4）
             *             //2.主分支二：处理cells数组没有初始化或者长度为0的情况；（这个分支处理add方法的四个条件中的1和2）
             *             //3.主分支三：处理如果cell数组没有初始化，并且其它线程正在执行对cells数组初始化的操作，及cellbusy=1；则尝试将累加值通过cas累加到base上
             *
             *
             */
            if ((as = cells) != null && (n = as.length) > 0) {

                /**
                 *内部小分支一：这个是处理add方法内部if分支的条件3：如果被hash到的位置为null，说明没有线程在这个位置设置过值，
                 * 没有竞争，可以直接使用，则用x值作为初始值创建一个新的Cell对象，对cells数组使用cellsBusy加锁，然后将这个Cell对象放到cells[m%cells.length]位置上
                 */
                if ((a = as[(n - 1) & h]) == null) {
                    /**
                     *   //cellsBusy == 0 代表当前没有线程cells数组做修改
                     */
                    if (cellsBusy == 0) {       // Try to attach new Cell
                        Cell r = new Cell(x);   // Optimistically create
                        /**
                         * casCellsBusy 将 cellsBusy设置为1，表示加锁
                         */
                        if (cellsBusy == 0 && casCellsBusy()) {
                            boolean created = false;
                            try {               // Recheck under lock
                                Cell[] rs; int m, j;
                                if ((rs = cells) != null &&
                                    (m = rs.length) > 0 &&
                                    rs[j = (m - 1) & h] == null) {
                                    rs[j] = r;
                                    created = true;
                                }
                            } finally {
                                cellsBusy = 0;
                            }
                            if (created)
                                break;
                            /**
                             * //如果created为false，说明上面指定的cells数组的位置cells[m%cells.length]已经有其它线程设置了cell了，继续执行循环。
                             */
                            continue;           // Slot is now non-empty
                        }
                    }
                    collide = false;
                }//end    if ((a = as[(n - 1) & h]) == null)
                /**
                 * 内部小分支二：如果add方法中条件4的通过cas设置cells[m%cells.length]位置的Cell对象中的value值设置为v+x
                 * 失败,说明已经发生竞争，将wasUncontended设置为true，跳出内部的if判断，最后重新计算一个新的probe，然后重新执行循环;
                 */
                else if (!wasUncontended)       // CAS already known to fail
                /**
                 *   //设置未竞争标志位true，继续执行，后面会算一个新的probe值，然后重新执行循环。
                 */
                    wasUncontended = true;      // Continue after rehash
                /**
                 *内部小分支三：新的争用线程参与争用的情况：处理刚进入当前方法时threadLocalRandomProbe=0的情况，
                 * 也就是当前线程第一次参与cell争用的cas失败，这里会尝试将x值加到cells[m%cells.length]的value ，如果成功直接退出
                 */
                else if (a.cas(v = a.value, ((fn == null) ? v + x :
                                             fn.applyAsLong(v, x))))
                    break;
                /**
                 * 内部小分支四：分支3处理新的线程争用执行失败了，这时如果cells数组的长度已经到了最大值（大于等于cup数量），
                 * 或者是当前cells已经做了扩容，则将collide设置为false，后面重新计算prob的值
                 */
                else if (n >= NCPU || cells != as)
                /**
                 * collide:碰撞
                 */
                    collide = false;            // At max size or stale
                /**
                 *内部小分支五：如果发生了冲突collide=false，则设置其为true；会在最后重新计算hash值后，进入下一次for循环
                 */
                else if (!collide)
                    collide = true;
                /**
                 *内部小分支六：扩容cells数组，新参与cell争用的线程两次均失败，且符合扩容条件，会执行该分支
                 */
                else if (cellsBusy == 0 && casCellsBusy()) {
                    try {
                        if (cells == as) {      // Expand table unless stale
                            Cell[] rs = new Cell[n << 1];
                            for (int i = 0; i < n; ++i)
                                rs[i] = as[i];
                            cells = rs;
                        }
                    } finally {
                        cellsBusy = 0;
                    }
                    collide = false;
                    continue;                   // Retry with expanded table
                }
                /**
                 * 为当前线程重新计算hash值
                 */
                h = advanceProbe(h);
            }//end  if ((as = cells) != null && (n = as.length) > 0)
            /**
             *  //这个大的分支处理add方法中的条件1与条件2成立的情况，如果cell表还未初始化或者长度为0，先尝试获取cellsBusy锁。
             */
            else if (cellsBusy == 0 && cells == as && casCellsBusy()) {
                boolean init = false;
                try {                           // Initialize table
                    //初始化cells数组，初始容量为2,并将x值通过hash&1，放到0个或第1个位置上
                    if (cells == as) {
                        Cell[] rs = new Cell[2];
                        rs[h & 1] = new Cell(x);
                        cells = rs;
                        init = true;
                    }
                } finally {
                    cellsBusy = 0;
                }
                if (init)
                    break;
            }
            /**
             *如果以上操作都失败了，则尝试将值累加到base上；
             */
            else if (casBase(v = base, ((fn == null) ? v + x :
                                        fn.applyAsLong(v, x))))
                break;                          // Fall back on using base
        }//end for
    }

    /**
     * Same as longAccumulate, but injecting long/double conversions
     * in too many places to sensibly merge with long version, given
     * the low-overhead requirements of this class. So must instead be
     * maintained by copy/paste/adapt.
     */
    final void doubleAccumulate(double x, DoubleBinaryOperator fn,
                                boolean wasUncontended) {
        int h;
        if ((h = getProbe()) == 0) {
            ThreadLocalRandom.current(); // force initialization
            h = getProbe();
            wasUncontended = true;
        }
        boolean collide = false;                // True if last slot nonempty
        for (;;) {
            Cell[] as; Cell a; int n; long v;
            if ((as = cells) != null && (n = as.length) > 0) {
                if ((a = as[(n - 1) & h]) == null) {
                    if (cellsBusy == 0) {       // Try to attach new Cell
                        Cell r = new Cell(Double.doubleToRawLongBits(x));
                        if (cellsBusy == 0 && casCellsBusy()) {
                            boolean created = false;
                            try {               // Recheck under lock
                                Cell[] rs; int m, j;
                                if ((rs = cells) != null &&
                                    (m = rs.length) > 0 &&
                                    rs[j = (m - 1) & h] == null) {
                                    rs[j] = r;
                                    created = true;
                                }
                            } finally {
                                cellsBusy = 0;
                            }
                            if (created)
                                break;
                            continue;           // Slot is now non-empty
                        }
                    }
                    collide = false;
                }
                else if (!wasUncontended)       // CAS already known to fail
                    wasUncontended = true;      // Continue after rehash
                else if (a.cas(v = a.value,
                               ((fn == null) ?
                                Double.doubleToRawLongBits
                                (Double.longBitsToDouble(v) + x) :
                                Double.doubleToRawLongBits
                                (fn.applyAsDouble
                                 (Double.longBitsToDouble(v), x)))))
                    break;
                else if (n >= NCPU || cells != as)
                    collide = false;            // At max size or stale
                else if (!collide)
                    collide = true;
                else if (cellsBusy == 0 && casCellsBusy()) {
                    try {
                        if (cells == as) {      // Expand table unless stale
                            Cell[] rs = new Cell[n << 1];
                            for (int i = 0; i < n; ++i)
                                rs[i] = as[i];
                            cells = rs;
                        }
                    } finally {
                        cellsBusy = 0;
                    }
                    collide = false;
                    continue;                   // Retry with expanded table
                }
                h = advanceProbe(h);
            }
            else if (cellsBusy == 0 && cells == as && casCellsBusy()) {
                boolean init = false;
                try {                           // Initialize table
                    if (cells == as) {
                        Cell[] rs = new Cell[2];
                        rs[h & 1] = new Cell(Double.doubleToRawLongBits(x));
                        cells = rs;
                        init = true;
                    }
                } finally {
                    cellsBusy = 0;
                }
                if (init)
                    break;
            }
            else if (casBase(v = base,
                             ((fn == null) ?
                              Double.doubleToRawLongBits
                              (Double.longBitsToDouble(v) + x) :
                              Double.doubleToRawLongBits
                              (fn.applyAsDouble
                               (Double.longBitsToDouble(v), x)))))
                break;                          // Fall back on using base
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long BASE;
    private static final long CELLSBUSY;
    private static final long PROBE;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> sk = Striped64.class;
            BASE = UNSAFE.objectFieldOffset
                (sk.getDeclaredField("base"));
            CELLSBUSY = UNSAFE.objectFieldOffset
                (sk.getDeclaredField("cellsBusy"));
            Class<?> tk = Thread.class;
            PROBE = UNSAFE.objectFieldOffset
                (tk.getDeclaredField("threadLocalRandomProbe"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
