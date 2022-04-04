package sun.misc;


import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.security.AccessController;
import java.security.PrivilegedAction;

public class Cleaner extends PhantomReference<Object> {
    /**
     * 我们创建一个引用对象的时候 一般也会提供一个ReferenceQueue对象，当目标对象被回收后，引用对象被放置到这个ReferenceQueue中
     * 引用队列，在检测到适当的可到达性更改后，垃圾回收器将已注册的引用对象添加到该队列中
     * --------------
     * 引用队列什么时候使用呢？
     *
     * 那么，如果我们希望在这个体系中，如果obj没有被其它对象引用，只是在这个Ref中存在引用时，就把obj对象gc掉。这时候就可以使用这里提到的Reference对象了。
     * 我们希望当一个对象被gc掉的时候通知用户线程，进行额外的处理时，就需要使用引用队列了。ReferenceQueue即这样的一个对象，当一个obj被gc掉之后，
     * 其相应的包装类，即ref对象会被放入queue中。我们可以从queue中获取到相应的对象信息，同时进行额外的处理。比如反向操作，数据清理等。
     *
     * 在Clearn的实现中，我们提供了一个引用对象，期望实现在 引用进入到这个引用队列的时候 会执行 释放对外内存的操作。这个实现原理就是
     *
     * 首先Reference类中有一个静态属性Reference<Object> pending ，他是一个链表结构，垃圾回收期会在 将Reference对象所引用 的目标对象回收之后
     * 将这个引用对象放置到pending队列中。
     * Reference类中的静态代码块 会启动一个ReferenceHandler线程，这个线程会不断的地执行tryHandlePending方法，在这个方法中会不断的从pending队列中取出Reference对象，
     * 然后将这个对象放入到创建该Reference对象时指定的ReferenceQueue队列中。 也就是执行了 ReferenceQueue的enqueue方法。
     *
     * 在enqueu方法中会判断 当前进入ReferenceQueue的Reference是否是Clear类型的对象，如果是clear类型的对象就会调用其clear方法，在我们的Clear类的clear方法内
     * 就实现了堆外内存的释放。
     *
     * -----------
     *
     * 在下面的代码中我们看到 在Cleaner的构造其中使用了  super(var1, dummyQueue); ，也就是使用了dummyQueue作为ReferenceQueu。
     * 当Cleaner对象进入到ReferenceQueue 中的时候会执行 ReferenceQueue的enqueu方法，在enqueu方法中又会调用Cleaner的clear方法。
     *
     */
    private static final ReferenceQueue<Object> dummyQueue = new ReferenceQueue();
    private static Cleaner first = null;
    private Cleaner next = null;
    private Cleaner prev = null;
    private final Runnable thunk;

    private static synchronized Cleaner add(Cleaner var0) {
        if (first != null) {
            var0.next = first;
            first.prev = var0;
        }

        first = var0;
        return var0;
    }

    private static synchronized boolean remove(Cleaner var0) {
        if (var0.next == var0) {
            return false;
        } else {
            if (first == var0) {
                if (var0.next != null) {
                    first = var0.next;
                } else {
                    first = var0.prev;
                }
            }

            if (var0.next != null) {
                var0.next.prev = var0.prev;
            }

            if (var0.prev != null) {
                var0.prev.next = var0.next;
            }

            var0.next = var0;
            var0.prev = var0;
            return true;
        }
    }

    private Cleaner(Object var1, Runnable var2) {
        super(var1, dummyQueue);
        this.thunk = var2;
    }

    public static Cleaner create(Object var0, Runnable var1) {
        return var1 == null ? null : add(new Cleaner(var0, var1));
    }

    public void clean() {
        if (remove(this)) {
            try {
                /**
                 * 在DirectByteBuffer中创建Cleaner的时候指定了 Deallocator 作为thunk
                 *   cleaner = Cleaner.create(this, new Deallocator(base, size, cap));
                 *   在Deallocator类中会执行 内存释放：  unsafe.freeMemory(address);
                 *
                 *
                 *   ==============
                 * 补充内容 clear方法和clean方法
                 * Reference类中定义了一个clear方法，会将引用关联的目标对象设置为空，这个clear方法不是被虚拟机调用的，而是
                 * 用户调用，比如我们将缓存设置为无效的时候  需要将 引用对象关联的目标对象设置为null，从而废弃这个引用对象。
                 *
                 *Cleaner是PhantomReference引用的子类，他定义了一个clean方法，clean方法中会执行业务逻辑Runnable，在
                 * DirectByteBuffer的实现中这个业务逻辑就是释放堆外内存， 这个clean方法的调用是在ReferenceHandler线程 中从
                 * Pending队列中取出引用，如果这个引用是Cleaner 就会执行器clean方法，可见clean方法执行时机是虚拟机控制的，而不是我们手动调用。
                 *
                 * =====================
                 * 关于引用队列有什么作用
                 * 当引用对象关联的对象不可达的时候（当对象的可达性发生改变（不再可达，一般是被引用的对象被gc）的某个时间后），JVM会把Reference设置成pending状态，所有处于pending状态的引用会形成一个链表，这条链表由GC维护。
                 *  Reference引用类中的静态代码块 会启动一个ReferenceHandler线程，这个线程会不断的从pending队列中取出Reference对象，如果这个引用对象是特殊的cleaner引用对象则调用其clean方法，在clean方法中释放堆外内存的占用。 针对其他类型的引用对象将这个引用对象放入到引用队列中。
                 * ReferenceHandler线程在处理引用时会调用clean方法，同时会将这个引用对象加入到用户创建的引用队列中，一般用户会启动一个线程处理这个引用队列中的引用，从而实现虚拟机回收引用时通知用户。比如Finalizer类是一个FinalReference，Finalizer类会启动一个FinalizerThread线程，这个线程会从队列中取出引用（Finalizer f = (Finalizer)queue.remove();），获取到引用关联的对象，然后调用对象的finalize方法。（）
                 *
                 *
                 */
                this.thunk.run();
            } catch (final Throwable var2) {
                AccessController.doPrivileged(new PrivilegedAction<Void>() {
                    public Void run() {
                        if (System.err != null) {
                            (new Error("Cleaner terminated abnormally", var2)).printStackTrace();
                        }

                        System.exit(1);
                        return null;
                    }
                });
            }

        }
    }
}
