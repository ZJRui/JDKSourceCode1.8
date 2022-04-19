/*
 * %W% %E%
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package sun.misc.resources;

/**
 * <p> This class represents the <code>ResourceBundle</code>
 * for sun.misc.
 * 
 * @author Michael Colburn
 * @version 1.5, 11/17/05
 */

public class Messages_pt_BR extends java.util.ListResourceBundle {

    /**
     * Returns the contents of this <code>ResourceBundle</code>.     
     * <p>   
     * @return the contents of this <code>ResourceBundle</code>.
     */
    public Object[][] getContents() {
	return contents;
    }
        
    private static final Object[][] contents = {
	{ "optpkg.versionerror", "ERRO: formato de vers\u00e3o inv\u00e1lido usado no arquivo jar {0}. Verifique a documenta\u00e7\u00e3o para obter o formato de vers\u00e3o suportado." },
	{ "optpkg.attributeerror", "ERRO: o atributo de manifesto JAR {0} necess\u00e1rio n\u00e3o est\u00e1 definido no arquivo JAR {1}." },
	{ "optpkg.attributeserror", "ERRO: alguns atributos de manifesto JAR necess\u00e1rios n\u00e3o est\u00e3o definidos no arquivo JAR {0}." }
    };
    
}
