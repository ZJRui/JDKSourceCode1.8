/*
 * %W% %E%
 *
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package sun.misc;

import java.io.FileDescriptor;

public interface JavaIOFileDescriptorAccess {
    public void set(FileDescriptor obj, int fd);
    public int get(FileDescriptor fd);

    // Only valid on Windows
    public void setHandle(FileDescriptor obj, long handle);
    public long getHandle(FileDescriptor obj);
}
