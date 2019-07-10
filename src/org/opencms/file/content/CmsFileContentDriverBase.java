/*
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (c) Alkacon Software GmbH & Co. KG (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.file.content;

import org.opencms.configuration.CmsParameterConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * 
 */
public abstract class CmsFileContentDriverBase implements I_CmsFileContentDriver {

    /** The params configuration. */
    private Map<String, String> m_configuration;

    /**
     * The constructor of the class just do init.<p>
     */
    public CmsFileContentDriverBase() {

        m_configuration = new LinkedHashMap<>();
    }

    /**
     * @see org.opencms.configuration.I_CmsConfigurationParameterHandler#addConfigurationParameter(java.lang.String, java.lang.String)
     * 
     * @param paramName name of parameter
     * @param paramValue config value
     */
    public void addConfigurationParameter(String paramName, String paramValue) {

        m_configuration.put(paramName, paramValue);
    }

    /**
     * returns the parameter config in opencms-vfs.xml
     * 
     * @param param name of parameter
     * 
     * @return config value
     */
    protected String getConfigurationParam(String param) {

        return m_configuration.get(param);
    }

    /**
     * Will always return <code>null</code> since this driver has no params.<p>
     * @return configuration or null
     *
     * @see org.opencms.configuration.I_CmsConfigurationParameterHandler#getConfiguration()
     */
    public Map<String, String> getConfiguration() {

        // return the configuration in an immutable form
        return m_configuration;
    }
    
    /**
     * @param configuration
     * @param paramName
     * @param defaultValue
     * @return
     */
    protected String getNotNullParamValue(CmsParameterConfiguration configuration, String paramName, String defaultValue) {

        // get from global config
        String paramValue = configuration.get(paramName);

        // get from custom config
        if (StringUtils.isBlank(paramValue))
            paramValue = getConfigurationParam(paramName);

        // get from default
        if (StringUtils.isBlank(paramValue))
            return defaultValue;
        return paramValue;
    }
}
