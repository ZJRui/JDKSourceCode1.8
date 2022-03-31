/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.nio;

import java.io.FileDescriptor;
import sun.misc.Unsafe;


/**
 * A direct byte buffer whose content is a memory-mapped region of a file.
 *一个直接字节缓冲区，其内容是文件的内存映射区域。
 * <p> Mapped byte buffers are created via the {@link
 * java.nio.channels.FileChannel#map FileChannel.map} method.  This class
 * extends the {@link ByteBuffer} class with operations that are specific to
 * memory-mapped file regions.
 * <p>映射字节缓冲区是通过 FileChannel.map 方法创建的。 此类使用特定于内存映射文件区域的操作扩展 ByteBuffer 类。<p/>
 *
 * <p> A mapped byte buffer and the file mapping that it represents remain
 * valid until the buffer itself is garbage-collected.
 *
 *
 * <p> The content of a mapped byte buffer can change at any time, for example
 * if the content of the corresponding region of the mapped file is changed by
 * this program or another.  Whether or not such changes occur, and when they
 * occur, is operating-system dependent and therefore unspecified.
 *
 * <p>映射字节缓冲区的内容可以随时更改，例如，如果该程序或其他程序更改了映射文件的相应区域的内容。 此类更改是否发生以及何时发生取决于操作系统，因此未指定。</p>
 *
 * <a name="inaccess"></a><p> All or part of a mapped byte buffer may become
 * inaccessible at any time, for example if the mapped file is truncated.  An
 * attempt to access an inaccessible region of a mapped byte buffer will not
 * change the buffer's content and will cause an unspecified exception to be
 * thrown either at the time of the access or at some later time.  It is
 * therefore strongly recommended that appropriate precautions be taken to
 * avoid the manipulation of a mapped file by this program, or by a
 * concurrently running program, except to read or write the file's content.
 *<p>映射字节缓冲区的全部或部分可能在任何时候变得不可访问，例如，如果映射文件被截断。 访问映射字节缓冲区的不可访问区域的
 * 尝试不会更改缓冲区的内容，并且会导致在访问时或稍后的某个时间抛出未指定的异常。 因此，强烈建议采取适当的预防措施，以避免
 * 该程序或同时运行的程序对映射文件的操作，除非读取或写入文件的内容。<p/>
 *
 * <p> Mapped byte buffers otherwise behave no differently than ordinary direct
 * byte buffers. </p>
 * <p>映射字节缓冲区的行为与普通的直接字节缓冲区没有区别。</p>
 *
 *
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 * @since 1.4
 *
 *
 *
 *
 *
 *
 * 一种直接字节缓冲区，其内容是文件的内存映射区域。
 * 映射字节缓冲区是通过FileChannel创建的。地图的方法。这个类通过特定于内存映射文件区域的操作扩展了ByteBuffer类。
 * 映射的字节缓冲区及其所代表的文件映射在缓冲区本身被垃圾回收之前都是有效的。
 * 映射字节缓冲区的内容可以在任何时候改变，例如，如果映射文件的相应区域的内容被这个或另一个程序改变了。这些更改是否发生以及何时发生取决于操作系统，
 * 因此是未指定的。
 * 映射字节缓冲区的全部或部分在任何时候都可能无法访问，例如，如果映射文件被截断。尝试访问映射字节缓冲区的不可访问区域不会改变缓冲区的内容，
 * 并将导致在访问时或稍后某个时间抛出未指定的异常。因此，强烈建议采取适当的预防措施，以避免该程序或并发运行的程序对映射文件的操作，
 * 但读取或写入文件的内容除外。
 * 否则，映射字节缓冲区的行为与普通直接字节缓冲区没有什么不同。
 *
 * <p></p>
 *
 * MappedByteBuffer 有两个子类DirectByteBuffer和DirectByteBufferR
 * --------------------
 *
 * <p></p>
 * 问题：关于内存释放
 * 通过DirectByteBuffer就可以知道，MappedByteBuffer对象使用是直接内存，因为FileChannel的map方法返回的MappedByteBuffer实际上是DirectByteBuffer类的对象
 * 我们都知道直接内存是不受java堆内存管理，这就引发了一个问题，如何释放MappedByteBuffer对象占用的直接内存？
 * DirectByteBuffer是对外内存，对外内存是存在于JVM管控之外的一块内存区域，因此他不受JVMD管控。
 * 在讲解DirectByteBuffer之前，需要先了解两个知识点：
 * （1）Java引用类型，因为DirectByteBuffer是通过虚引用（Phantom Reference）来实现堆外内存的释放的
 * PhantomReference 是所有 弱引用类型种最弱的引用类型，不同于软引用和弱引用，虚引用无法通过get方法来取得目标对象的强引用从而使用目标对象，观察源码可以发现get被重写永远返回null
 *
 * 那虚引用到底有什么作用？其实虚引用主要被用来 跟踪对象被垃圾回收的状态，通过查看引用队列中是否包含对象所对应的虚引用来判断它是否 即将被垃圾回收，从而采取行动。它并不被期待用来取得目标对象的引用，而目标对象被回收前，它的引用会被放入一个 ReferenceQueue 对象中，从而达到跟踪对象垃圾回收的作用。
 * 关于java引用类型的实现和原理可以阅读之前的文章Reference 、ReferenceQueue 详解 和Java 引用类型简述
 * (2)关于Linux的内核态和用户态
 * 当我们通过JNI调用的native方法实际上就是从用户态切换到了内核态的一种方式。并且通过该系统调用使用操作系统所提供的功能。
 * Q：为什么需要用户进程(位于用户态中)要通过系统调用(Java中即使JNI)来调用内核态中的资源，或者说调用操作系统的服务了？
 * A：intel cpu提供Ring0-Ring3四种级别的运行模式，Ring0级别最高，Ring3最低。Linux使用了Ring3级别运行用户态，Ring0作为内核态。
 * Ring3状态不能访问Ring0的地址空间，包括代码和数据。因此用户态是没有权限去操作内核态的资源的，它只能通过系统调用外完成用户态到内核态的切换，然后在完成相关操作后再有内核态切换回用户态。
 *
 * --------------
 * <p></p>
 * DirectByteBuffer ———— 直接缓冲
 * DirectByteBuffer是Java用于实现堆外内存的一个重要类，我们可以通过该类实现堆外内存的创建、使用和销毁。
 *
 * DirectByteBuffer该类本身还是位于Java内存模型的堆中。堆内内存是JVM可以直接管控、操纵。
 * 而DirectByteBuffer中的unsafe.allocateMemory(size);是个一个native方法，这个方法分配的是堆外内存，通过C的malloc来进行分配的。
 * 分配的内存是系统本地的内存，并不在Java的内存中，也不属于JVM管控范围，所以在DirectByteBuffer一定会存在某种方式来操纵堆外内存。
 * 在DirectByteBuffer的父类Buffer中有个address属性：
 *    // Used only by direct buffers
 *     // NOTE: hoisted here for speed in JNI GetDirectBufferAddress
 *     long address;
 *
 * address只会被直接缓存给使用到。之所以将address属性升级放在Buffer中，是为了在JNI调用GetDirectBufferAddress时提升它调用的速率。
 * address表示分配的堆外内存的地址。
 * unsafe.allocateMemory(size);分配完堆外内存后就会返回分配的堆外内存基地址，并将这个地址赋值给了address属性。这样我们后面通过JNI对这个堆外内存操作时都是通过这个address来实现的了。
 *
 *
 */

public abstract class MappedByteBuffer
    extends ByteBuffer
{

    /**
     *
     *
     * * <p>映射的字节缓冲区和它所代表的文件映射在缓冲区本身被垃圾收集之前保持有效。(从这个地方 隐约感觉到  是 文件 映射到 用户进程内存的空间)
     *  *
     *  * 内存映射： mmap是一种内存映射文件的方法，即将一个文件或者其它对象映射到进程的地址空间，实现文件磁盘地址和进程虚拟地址空间中一段虚拟地址的一一对映关系。
     *  * 实现这样的映射关系后，进程就可以采用指针的方式读写操作这一段内存，而系统会自动回写脏页面到对应的文件磁盘上，即完成了对文件的操作而不必再调用read,write等系统调用函数
     *  *
     *  * mmap 把文件内容映射到以 start 地址开始的长度为 length 字节的内存空间上。而被映
     *  * 射到内存上的文件的范围则是：由文件描述符 fd 指定的文件中，从偏移量 offset 开始，长度
     *  * 为 length 字节的部分。
     *  * mmap 系统调用的返回值为实际映射到的内存空间的起始地址。内存分配失败时会返回常量
     *  * MAP_FAILED。
     *  *
     *  * FileChannel.map的方法会返回一个 MappedByteBuffer 对象，map方法中 调用了 native map0 方法，这个native方法的实现 在FileChannelImpl.c文件中 Java_sun_nio_ch_FileChannelImpl_map0方法
     *  * 调用了 mmap64这个方法，mmap64和mmap等价，也就是 mmap系统调用。
     *  *
     *  *
     *  * </p>
     *
     */



    // This is a little bit backwards: By rights MappedByteBuffer should be a
    // subclass of DirectByteBuffer, but to keep the spec clear and simple, and
    // for optimization purposes, it's easier to do it the other way around.
    // This works because DirectByteBuffer is a package-private class.
    /**
     * //这有点倒退
     * // 正确的方式 MappedByteBuffer应该是DirectByteBuffer的子类。DirectByteBuffer的子类，但是为了保持规范的清晰和简单，并且
     * //为优化目的，它更容易做相反的方法。
     * //这是因为DirectByteBuffer是一个包私有类。
     */

    // For mapped buffers, a FileDescriptor that may be used for mapping
    // operations if valid; null if the buffer is not mapped.
    private final FileDescriptor fd;

    // This should only be invoked by the DirectByteBuffer constructors
    //
    MappedByteBuffer(int mark, int pos, int lim, int cap, // package-private
                     FileDescriptor fd)
    {
        super(mark, pos, lim, cap);
        this.fd = fd;
    }

    MappedByteBuffer(int mark, int pos, int lim, int cap) { // package-private
        super(mark, pos, lim, cap);
        this.fd = null;
    }

    private void checkMapped() {
        if (fd == null)
            // Can only happen if a luser explicitly casts a direct byte buffer
            throw new UnsupportedOperationException();
    }

    // Returns the distance (in bytes) of the buffer from the page aligned address
    // of the mapping. Computed each time to avoid storing in every direct buffer.
    private long mappingOffset() {
        int ps = Bits.pageSize();
        long offset = address % ps;
        return (offset >= 0) ? offset : (ps + offset);
    }

    private long mappingAddress(long mappingOffset) {
        return address - mappingOffset;
    }

    private long mappingLength(long mappingOffset) {
        return (long)capacity() + mappingOffset;
    }

    /**
     * Tells whether or not this buffer's content is resident in physical
     * memory.
     *
     * <p> A return value of <tt>true</tt> implies that it is highly likely
     * that all of the data in this buffer is resident in physical memory and
     * may therefore be accessed without incurring any virtual-memory page
     * faults or I/O operations.  A return value of <tt>false</tt> does not
     * necessarily imply that the buffer's content is not resident in physical
     * memory.
     *
     * <p> The returned value is a hint, rather than a guarantee, because the
     * underlying operating system may have paged out some of the buffer's data
     * by the time that an invocation of this method returns.  </p>
     *
     * @return  <tt>true</tt> if it is likely that this buffer's content
     *          is resident in physical memory
     *
     *
     *      说明该缓冲区的内容是否驻留在物理内存中。
     * 返回值为true意味着该缓冲区中的所有数据极有可能都驻留在物理内存中，因此可以在不引发任何虚拟内存页面错误或I/O操作的情况下访问它们。
     * 返回值为false并不一定意味着缓冲区的内容不在物理内存中。
     * 返回值是一个提示，而不是保证，因为在此方法调用返回时，底层操作系统可能已经换出了缓冲区的一些数据。
     *
     * 返回:
     * 如果该缓冲区的内容很可能驻留在物理内存中，则为
     */
    public final boolean isLoaded() {
        checkMapped();
        if ((address == 0) || (capacity() == 0))
            return true;
        long offset = mappingOffset();
        long length = mappingLength(offset);
        return isLoaded0(mappingAddress(offset), length, Bits.pageCount(length));
    }

    // not used, but a potential target for a store, see load() for details.
    private static byte unused;

    /**
     * Loads this buffer's content into physical memory.
     *
     * <p> This method makes a best effort to ensure that, when it returns,
     * this buffer's content is resident in physical memory.  Invoking this
     * method may cause some number of page faults and I/O operations to
     * occur. </p>
     *
     * @return  This buffer
     *
     * 将该缓冲区的内容加载到物理内存中。
     * 该方法尽最大努力确保当它返回时，该缓冲区的内容驻留在物理内存中。调用此方法可能会导致出现一些页面错误和I/O操作。
     *
     * 返回:
     * 这个缓冲区
     * 的注释:
     * @org.jetbrains.annotations。合同(“→这种“)
     *
     */
    public final MappedByteBuffer load() {
        checkMapped();
        if ((address == 0) || (capacity() == 0))
            return this;
        long offset = mappingOffset();
        long length = mappingLength(offset);
        load0(mappingAddress(offset), length);

        // Read a byte from each page to bring it into memory. A checksum
        // is computed as we go along to prevent the compiler from otherwise
        // considering the loop as dead code.
        Unsafe unsafe = Unsafe.getUnsafe();
        int ps = Bits.pageSize();
        int count = Bits.pageCount(length);
        long a = mappingAddress(offset);
        byte x = 0;
        for (int i=0; i<count; i++) {
            x ^= unsafe.getByte(a);
            a += ps;
        }
        if (unused != 0)
            unused = x;

        return this;
    }

    /**
     * Forces any changes made to this buffer's content to be written to the
     * storage device containing the mapped file.
     *
     * <p> If the file mapped into this buffer resides on a local storage
     * device then when this method returns it is guaranteed that all changes
     * made to the buffer since it was created, or since this method was last
     * invoked, will have been written to that device.
     *
     * <p> If the file does not reside on a local device then no such guarantee
     * is made.
     *
     * <p> If this buffer was not mapped in read/write mode ({@link
     * java.nio.channels.FileChannel.MapMode#READ_WRITE}) then invoking this
     * method has no effect. </p>
     *
     * @return  This buffer
     */
    public final MappedByteBuffer force() {
        checkMapped();
        if ((address != 0) && (capacity() != 0)) {
            long offset = mappingOffset();
            force0(fd, mappingAddress(offset), mappingLength(offset));
        }
        return this;
    }

    private native boolean isLoaded0(long address, long length, int pageCount);
    private native void load0(long address, long length);
    private native void force0(FileDescriptor fd, long address, long length);
}
