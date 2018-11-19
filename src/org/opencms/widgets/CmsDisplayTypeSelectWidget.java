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

package org.opencms.widgets;

import org.opencms.ade.configuration.CmsADEConfigData;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsProperty;
import org.opencms.file.CmsPropertyDefinition;
import org.opencms.file.CmsResource;
import org.opencms.loader.CmsTemplateLoaderFacade;
import org.opencms.main.CmsException;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.util.CmsStringUtil;
import org.opencms.workplace.CmsWorkplaceMessages;
import org.opencms.xml.containerpage.I_CmsFormatterBean;
import org.opencms.xml.types.CmsXmlDisplayFormatterValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.logging.Log;

import com.google.common.collect.Sets;

/**
 * Widget to select a type and formatter combination.<p>
 */
public class CmsDisplayTypeSelectWidget extends CmsSelectWidget {

    /**
     * Formatter option comparator, sorting by type name and rank.<p>
     */
    class FormatterComparator implements Comparator<FormatterOption> {

        /**
         * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
         */
        public int compare(FormatterOption o1, FormatterOption o2) {

            return o1.m_typeName.equals(o2.m_typeName) ? o2.m_rank - o1.m_rank : o1.m_typeName.compareTo(o2.m_typeName);
        }
    }

    /**
     * Formatter select option.<p>
     */
    class FormatterOption {

        /** The option key. */
        String m_key;

        /** The option label. */
        String m_label;

        /** The formatter rank. */
        int m_rank;

        /** The type name. */
        String m_typeName;

        /**
         * Constructor.<p>
         *
         * @param key the option key
         * @param typeName the type name
         * @param label the option label
         * @param rank the formatter rank
         */
        FormatterOption(String key, String typeName, String label, int rank) {

            m_key = key;
            m_typeName = typeName;
            m_label = label;
            m_rank = rank;
        }
    }

    /** The logger instance for this class. */
    private static final Log LOG = CmsLog.getLog(CmsDisplayTypeSelectWidget.class);

    /** The display types split regex. */
    private static final String TYPES_SPLITTER = " *, *";

    /** The widget configuration. */
    private String m_config;

    /**
     * @see org.opencms.widgets.A_CmsSelectWidget#getConfiguration()
     */
    @Override
    public String getConfiguration() {

        return m_config;
    }

    /**
     * @see org.opencms.widgets.CmsSelectWidget#newInstance()
     */
    @Override
    public I_CmsWidget newInstance() {

        return new CmsDisplayTypeSelectWidget();
    }

    /**
     * @see org.opencms.widgets.A_CmsSelectWidget#setConfiguration(java.lang.String)
     */
    @Override
    public void setConfiguration(String configuration) {

        m_config = configuration;
    }

    /**
     * @see org.opencms.widgets.A_CmsSelectWidget#parseSelectOptions(org.opencms.file.CmsObject, org.opencms.widgets.I_CmsWidgetDialog, org.opencms.widgets.I_CmsWidgetParameter)
     */
    @Override
    protected List<CmsSelectWidgetOption> parseSelectOptions(
        CmsObject cms,
        I_CmsWidgetDialog widgetDialog,
        I_CmsWidgetParameter param) {

        Locale wpLocale = OpenCms.getWorkplaceManager().getWorkplaceLocale(cms);
        List<CmsSelectWidgetOption> result = new ArrayList<CmsSelectWidgetOption>();
        List<FormatterOption> options = new ArrayList<FormatterOption>();
        String resourcePath = getResourcePath(cms, widgetDialog);
        String resourceSitePath = cms.getRequestContext().removeSiteRoot(resourcePath);

        Set<String> containerTypes = new HashSet<>();

        if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(m_config)) {
            String types = null;

            if (CmsPropertyDefinition.PROPERTY_TEMPLATE_DISPLAY_TYPES.equals(m_config)) {
                try {
                    CmsProperty prop = cms.readPropertyObject(
                        resourceSitePath,
                        CmsPropertyDefinition.PROPERTY_TEMPLATE_DISPLAY_TYPES,
                        true);
                    String propValue = prop.getValue();
                    if (!CmsStringUtil.isEmptyOrWhitespaceOnly(propValue)) {
                        types = propValue;
                    } else {
                        // look up template property
                        try {
                            CmsResource resource = cms.readResource(resourceSitePath);
                            CmsTemplateLoaderFacade loaderFacade = OpenCms.getResourceManager().getTemplateLoaderFacade(
                                cms,
                                null,
                                resource,
                                CmsPropertyDefinition.PROPERTY_TEMPLATE);
                            CmsResource template = loaderFacade.getLoaderStartResource();
                            if (template != null) {
                                prop = cms.readPropertyObject(
                                    template,
                                    CmsPropertyDefinition.PROPERTY_TEMPLATE_DISPLAY_TYPES,
                                    false);
                                propValue = prop.getValue();
                                if (!CmsStringUtil.isEmptyOrWhitespaceOnly(propValue)) {
                                    types = propValue;
                                }
                            }
                        } catch (Exception ex) {
                            LOG.debug(ex.getMessage(), ex);
                        }
                    }
                } catch (CmsException e) {
                    LOG.warn(e.getLocalizedMessage(), e);
                }
            } else {
                types = m_config;
            }
            if (types != null) {
                containerTypes.addAll(Arrays.asList(types.split(TYPES_SPLITTER)));
            }
        }
        CmsADEConfigData config = OpenCms.getADEManager().lookupConfiguration(cms, resourcePath);
        if (config != null) {
            for (I_CmsFormatterBean formatter : config.getDisplayFormatters(cms)) {
                if (!containerTypes.isEmpty()) {
                    if (Sets.intersection(containerTypes, formatter.getContainerTypes()).isEmpty()) {
                        continue;
                    }
                }
                for (String typeName : formatter.getResourceTypeNames()) {
                    String label = formatter.getNiceName(wpLocale)
                        + " ("
                        + CmsWorkplaceMessages.getResourceTypeName(wpLocale, typeName)
                        + ")";
                    options.add(
                        new FormatterOption(
                            typeName + CmsXmlDisplayFormatterValue.SEPARATOR + formatter.getId(),
                            typeName,
                            label,
                            formatter.getRank()));
                }
            }
        }
        Collections.sort(options, new FormatterComparator());
        for (FormatterOption option : options) {
            result.add(new CmsSelectWidgetOption(option.m_key, false, option.m_label));
        }
        return result;
    }
}
