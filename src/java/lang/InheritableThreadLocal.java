
package java.lang;
import java.lang.ref.*;

/**
 * This class extends <tt>ThreadLocal</tt> to provide inheritance of values
 * from parent thread to child thread: when a child thread is created, the
 * child receives initial values for all inheritable thread-local variables
 * for which the parent has values.  Normally the child's values will be
 * identical to the parent's; however, the child's value can be made an
 * arbitrary function of the parent's by overriding the <tt>childValue</tt>
 * method in this class.
 * 此类扩展 <tt>ThreadLocal</tt> 以提供值的继承
 * 从父线程到子线程：创建子线程时，
 * child 接收所有可继承线程局部变量的初始值
 * 父级具有值的。 通常孩子的价值观是
 * 与父母相同； 但是，孩子的价值可以
 * 通过覆盖 <tt>childValue</tt> 实现父级的任意函数
 * 此类中的方法。
 *
 * <p>Inheritable thread-local variables are used in preference to
 * ordinary thread-local variables when the per-thread-attribute being
 * maintained in the variable (e.g., User ID, Transaction ID) must be
 * automatically transmitted to any child threads that are created.
 *
 * <p>优先使用可继承的线程局部变量
 * 当 per-thread-attribute 为普通线程局部变量时
 * 必须在变量中维护（例如，用户 ID、交易 ID）
 * 自动传输到任何创建的子线程。
 *
 * @author  Josh Bloch and Doug Lea
 * @version %I%, %G%
 * @see     ThreadLocal
 * @since   1.2
 */

public class InheritableThreadLocal<T> extends ThreadLocal<T> {

    /**
     * InheritableThreadLocal是ThreadLocal的子类，当父线程创建一个InheritableThreadLocal对象之后，
     * InheritableThreadLocal的内容能够在这个父线程的所有子线程中共享。
     *
     * 在这个类中重写了 getMap和createmap方法
     *
     * 在分析ThreadLocal的代码中，set和get的方法中，都有getMap和createMap。这两个方法重写了之后，就将之前的从Thread中的
     * threadLoccals获取threadLocalMap变成了从inheritableThreadLocals获取ThreadLocalMap。 在看完这些代码之后，
     * 还是没有明白，是如何将ThreadLocalMap的内容放置到Thread的inheritableThreadLocals变量的。 因此需要进一步对Thread代码进行分析。
     *
     * -----------
     * InheritableThreadLocal的使用方式是在主线程中通过InheritableThreadLocal设置值到主线程汇中，然后再子线程中 也是通过同一个InheritableThreadLocal对象
     * 从子线程中获取值。这个InheritableThreadLocal的取值不是到原来的主线程中取值，而是从当前线程的 inheritableThreadLocals这个 ThreadLocalMap中取值。这个是要注意的地方。
     * 基于这个原因，当我们在当前子线程中 使用了 InheritableThreadLocal 更新其设置的值，这个时候并不会影响主线程 通过InheritableThreadLocal得到的值，也不会影响其他同级别的子线程通过InheritableThreadLocal得到的值。
     *
     * 主线程创建多个子线程的时候，每个子线程都有自己的备份。 这个操作是在线程创建的 时候 也就是 Thread的init方法中完成的
     *  // 当父线程的 inheritableThreadLocals 的值不为空时
     *  // 会把 inheritableThreadLocals 里面的值全部传递给子线程
     *  if (inheritThreadLocals && parent.inheritableThreadLocals != null)
     *             this.inheritableThreadLocals =
     *                 ThreadLocal.createInheritedMap(parent.inheritableThreadLocals);
     *
     *
     *-------------------------
     * 总结
     * 1.InheritableThreadLocal在线程创建的时候，从父线程中拷贝了inheritableThreadLocals，这是一个相对的深度拷贝，重建了整个ThreadLocalMap。如果Entry的value不是引用类型，那么这些Entry的值在每个Thread中互不影响。由于只copy到Entry这一级，如果Entry的value本身就是引用类型，那么将会共享
     *
     * 2.InheritableThreadLocal利用了面向对象的多态特性，重写了childValue、getMap和createMap方法。在Thread中对inheritableThreadLocals进行了处理。这说明，如果在Thread的基础上实现共享内存或者事务等，只能使用ThreadLocal或者InheritableThreadLocal来实现。
     *
     * 3.InheritableThreadLocal与ThreadLocal会有相同的内存泄漏的风险。因此需要注意对remove方法的使用。避免导致OOM或者内存泄漏。
     *
     * InheritableThreadLocal存在的 问题：
     * 1.必须是初始化的子线程，才能继承父线程的inheritableThreadLocals变量，那么如果是线程池，因为是复用线程的原因，没有进行init，
     * 所以自然也就没办法进行重新赋值，会导致从子线程get出来的参数与父线程往InheritableThreadLocal塞进去的参数不一致问题
     *
     * JDK的InheritableThreadLocal类可以完成父线程到子线程的值传递。但对于使用线程池等会池化复用线程的执行组件的情况，线程由线程池创建好，
     * 并且线程是池化起来反复使用的；这时父子线程关系的ThreadLocal值传递已经没有意义，应用需要的实际上是把 任务提交给线程池时的ThreadLocal值传递到 任务执行时。
     *
     *
     *
     */

    /**
     * Computes the child's initial value for this inheritable thread-local
     * variable as a function of the parent's value at the time the child
     * thread is created.  This method is called from within the parent
     * thread before the child is started.
     * <p>
     * This method merely returns its input argument, and should be overridden
     * if a different behavior is desired.
     *
     *
     * 为这个可继承的线程本地计算孩子的初始值
     * 变量作为子项时父项值的函数
     * 创建线程。 此方法是从父级内部调用的
     * 子进程启动前的线程。
     * <p>
     * 此方法仅返回其输入参数，应被覆盖
     * 如果需要不同的行为。
     *
     * ---------------------
     *
     *  拿到父线程的值后，可以在这里处理后再返回给子线程
     *
     * @param parentValue the parent thread's value
     * @return the child thread's initial value
     */
    protected T childValue(T parentValue) {
        return parentValue;
    }

    /**
     * Get the map associated with a ThreadLocal.
     *
     * 获取当前线程内的 inheritableThreadLocals 属性。
     * inheritableThreadLocals重写了一个方法：
     *使其返回的不是t.threadLocal，而是t.inheritableThreadLocals，而这个getMap方法，恰恰就是获取对应线程的ThreadLocalMap方法，进一步从map中get出Value，然后return，至此全剧终
     * ---------------------------------
     *
     * @param t the current thread
     */
    ThreadLocalMap getMap(Thread t) {
        /**
         *
         * 注意这个 getMap参数接收的t是当前线程。因此下面的代码也就是 获取当前线程的 InheritableThreadLocals中
         *
         *
         */
        return t.inheritableThreadLocals;
    }

    /**
     * Create the map associated with a ThreadLocal.
     *
     * @param t the current thread
     * @param firstValue value for the initial entry of the table.
     * @param map the map to store.
     */
    void createMap(Thread t, T firstValue) {
        t.inheritableThreadLocals = new ThreadLocalMap(this, firstValue);
    }
}
