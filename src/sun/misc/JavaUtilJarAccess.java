/*
 * %W% %E%
 *
 * Copyright (c) 2006, 2009, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package sun.misc;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.security.CodeSource;
import java.security.cert.Certificate;
import sun.misc.JavaUtilJarAccess;

public interface JavaUtilJarAccess {
    public boolean jarFileHasClassPathAttribute(JarFile jar) throws IOException;
    public CodeSource[] getCodeSources(JarFile jar, URL url);
    public CodeSource getCodeSource(JarFile jar, URL url, String name);
    public Enumeration<String> entryNames(JarFile jar, CodeSource[] cs);
    public Enumeration<JarEntry> entries2(JarFile jar);
    public void setEagerValidation(JarFile jar, boolean eager);
    public List getManifestDigests(JarFile jar);
}
