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

public class Messages_sv extends java.util.ListResourceBundle {

    /**
     * Returns the contents of this <code>ResourceBundle</code>.     
     * <p>   
     * @return the contents of this <code>ResourceBundle</code>.
     */
    public Object[][] getContents() {
	return contents;
    }
        
    private static final Object[][] contents = {
	{ "optpkg.versionerror", "FEL: Ogiltigt versionsformat i {0} JAR-fil. Kontrollera i dokumentationen vilket versionsformat som st\u00f6ds." },
	{ "optpkg.attributeerror", "FEL: Det JAR manifest-attribut {0} som kr\u00e4vs \u00e4r inte angivet i {1} JAR-filen." },
	{ "optpkg.attributeserror", "FEL: Vissa JAR manifest-attribut som kr\u00e4vs \u00e4r inte angivna i {0} JAR-filen." }
    };
    
}
