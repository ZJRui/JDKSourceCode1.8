/*
 * %W% %E%
 *
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package sun.misc.resources;

/**
 * <p> This class represents the <code>ResourceBundle</code>
 * for sun.misc.
 * 
 * @author Michael Colburn
 * @version %I%, %G%
 */

public class Messages_de extends java.util.ListResourceBundle {

    /**
     * Returns the contents of this <code>ResourceBundle</code>.     
     * <p>   
     * @return the contents of this <code>ResourceBundle</code>.
     */
    public Object[][] getContents() {
	return contents;
    }
        
    private static final Object[][] contents = {
	{ "optpkg.versionerror", "FEHLER: In der JAR-Datei {0} wurde ein ung\u00fcltiges Versionsformat verwendet. Pr\u00fcfen Sie in der Dokumentation, welches Versionsformat unterst\u00fctzt wird." },
	{ "optpkg.attributeerror", "FEHLER: In der JAR-Datei {1} ist das erforderliche JAR-Manifestattribut {0} nicht gesetzt." },
	{ "optpkg.attributeserror", "FEHLER: In der JAR-Datei {0} sind einige erforderliche JAR-Manifestattribute nicht gesetzt." }
    };
    
}
