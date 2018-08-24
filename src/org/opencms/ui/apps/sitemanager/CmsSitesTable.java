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

package org.opencms.ui.apps.sitemanager;

import org.opencms.file.CmsFile;
import org.opencms.file.CmsResource;
import org.opencms.main.CmsException;
import org.opencms.main.OpenCms;
import org.opencms.site.CmsSite;
import org.opencms.site.CmsSiteMatcher;
import org.opencms.ui.A_CmsUI;
import org.opencms.ui.CmsCssIcon;
import org.opencms.ui.CmsVaadinUtils;
import org.opencms.ui.apps.CmsAppWorkplaceUi;
import org.opencms.ui.apps.CmsFileExplorerConfiguration;
import org.opencms.ui.apps.CmsPageEditorConfiguration;
import org.opencms.ui.apps.CmsSitemapEditorConfiguration;
import org.opencms.ui.apps.Messages;
import org.opencms.ui.components.CmsResourceIcon;
import org.opencms.ui.components.OpenCmsTheme;
import org.opencms.ui.contextmenu.CmsContextMenu;
import org.opencms.ui.contextmenu.I_CmsSimpleContextMenuEntry;
import org.opencms.util.CmsStringUtil;
import org.opencms.workplace.explorer.menu.CmsMenuItemVisibilityMode;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.vaadin.event.MouseEvents;
import com.vaadin.server.Resource;
import com.vaadin.server.StreamResource;
import com.vaadin.shared.MouseEventDetails.MouseButton;
import com.vaadin.ui.Component;
import com.vaadin.ui.Image;
import com.vaadin.ui.themes.ValoTheme;
import com.vaadin.v7.data.Item;
import com.vaadin.v7.data.util.IndexedContainer;
import com.vaadin.v7.data.util.filter.Or;
import com.vaadin.v7.data.util.filter.SimpleStringFilter;
import com.vaadin.v7.event.ItemClickEvent;
import com.vaadin.v7.event.ItemClickEvent.ItemClickListener;
import com.vaadin.v7.shared.ui.label.ContentMode;
import com.vaadin.v7.ui.Label;
import com.vaadin.v7.ui.Table;

/**
 *  Class to create Vaadin Table object with all available sites.<p>
 */
public class CmsSitesTable extends Table {

    /**
     * The delete project context menu entry.<p>
     */
    class DeleteEntry implements I_CmsSimpleContextMenuEntry<Set<String>> {

        /**
         * @see org.opencms.ui.contextmenu.I_CmsSimpleContextMenuEntry#executeAction(java.lang.Object)
         */
        public void executeAction(final Set<String> data) {

            m_manager.openDeleteDialog(data);
        }

        /**
         * @see org.opencms.ui.contextmenu.I_CmsSimpleContextMenuEntry#getTitle(java.util.Locale)
         */
        public String getTitle(Locale locale) {

            return CmsVaadinUtils.getMessageText(Messages.GUI_PROJECTS_DELETE_0);
        }

        /**
         * @see org.opencms.ui.contextmenu.I_CmsSimpleContextMenuEntry#getVisibility(java.lang.Object)
         */
        public CmsMenuItemVisibilityMode getVisibility(Set<String> data) {

            return CmsMenuItemVisibilityMode.VISIBILITY_ACTIVE;
        }
    }

    /**
     * The edit project context menu entry.<p>
     */
    class EditEntry implements I_CmsSimpleContextMenuEntry<Set<String>>, I_CmsSimpleContextMenuEntry.I_HasCssStyles {

        /**
         * @see org.opencms.ui.contextmenu.I_CmsSimpleContextMenuEntry#executeAction(java.lang.Object)
         */
        public void executeAction(Set<String> data) {

            String siteRoot = data.iterator().next();
            m_manager.openEditDailog(siteRoot);
        }

        /**
         * @see org.opencms.ui.contextmenu.I_CmsSimpleContextMenuEntry.I_HasCssStyles#getStyles()
         */
        public String getStyles() {

            return ValoTheme.LABEL_BOLD;
        }

        /**
         * @see org.opencms.ui.contextmenu.I_CmsSimpleContextMenuEntry#getTitle(java.util.Locale)
         */
        public String getTitle(Locale locale) {

            return CmsVaadinUtils.getMessageText(Messages.GUI_PROJECTS_EDIT_0);
        }

        /**
         * @see org.opencms.ui.contextmenu.I_CmsSimpleContextMenuEntry#getVisibility(java.lang.Object)
         */
        public CmsMenuItemVisibilityMode getVisibility(Set<String> data) {

            return (data != null) && (data.size() == 1)
            ? CmsMenuItemVisibilityMode.VISIBILITY_ACTIVE
            : CmsMenuItemVisibilityMode.VISIBILITY_INVISIBLE;
        }
    }

    /**
     * The menu entry to switch to the explorer of concerning site.<p>
     */
    class ExplorerEntry implements I_CmsSimpleContextMenuEntry<Set<String>> {

        /**
         * @see org.opencms.ui.contextmenu.I_CmsSimpleContextMenuEntry#executeAction(java.lang.Object)
         */
        public void executeAction(Set<String> data) {

            String siteRoot = data.iterator().next();
            A_CmsUI.getCmsObject().getRequestContext().setSiteRoot(siteRoot);
            CmsAppWorkplaceUi.get().getNavigator().navigateTo(
                CmsFileExplorerConfiguration.APP_ID
                    + "/"
                    + A_CmsUI.getCmsObject().getRequestContext().getCurrentProject().getUuid()
                    + "!!"
                    + siteRoot
                    + "!!");

        }

        /**
         * @see org.opencms.ui.contextmenu.I_CmsSimpleContextMenuEntry#getTitle(java.util.Locale)
         */
        public String getTitle(Locale locale) {

            return Messages.get().getBundle(locale).key(Messages.GUI_EXPLORER_TITLE_0);
        }

        /**
         * @see org.opencms.ui.contextmenu.I_CmsSimpleContextMenuEntry#getVisibility(java.lang.Object)
         */
        public CmsMenuItemVisibilityMode getVisibility(Set<String> data) {

            if (data == null) {
                return CmsMenuItemVisibilityMode.VISIBILITY_INVISIBLE;
            }

            if (data.size() > 1) {
                return CmsMenuItemVisibilityMode.VISIBILITY_INVISIBLE;
            }

            if (!((Boolean)getItem(data.iterator().next()).getItemProperty(PROP_OK).getValue()).booleanValue()) {
                return CmsMenuItemVisibilityMode.VISIBILITY_INACTIVE;
            }

            return CmsMenuItemVisibilityMode.VISIBILITY_ACTIVE;
        }

    }

    /**
     * Column with FavIcon.<p>
     */
    class FavIconColumn implements Table.ColumnGenerator {

        /**vaadin serial id.*/
        private static final long serialVersionUID = -3772456970393398685L;

        /**
         * @see com.vaadin.ui.Table.ColumnGenerator#generateCell(com.vaadin.ui.Table, java.lang.Object, java.lang.Object)
         */
        public Object generateCell(Table source, Object itemId, Object columnId) {

            return getImageFavIcon((String)itemId);
        }
    }

    /**
     * The menu entry to switch to the page editor of concerning site.<p>
     */
    class PageEditorEntry implements I_CmsSimpleContextMenuEntry<Set<String>> {

        /**
         * @see org.opencms.ui.contextmenu.I_CmsSimpleContextMenuEntry#executeAction(java.lang.Object)
         */
        public void executeAction(Set<String> data) {

            String siteRoot = data.iterator().next();
            A_CmsUI.get().changeSite(siteRoot);

            CmsPageEditorConfiguration pageeditorApp = new CmsPageEditorConfiguration();
            pageeditorApp.getAppLaunchCommand().run();

        }

        /**
         * @see org.opencms.ui.contextmenu.I_CmsSimpleContextMenuEntry#getTitle(java.util.Locale)
         */
        public String getTitle(Locale locale) {

            return CmsVaadinUtils.getMessageText(Messages.GUI_PAGEEDITOR_TITLE_0);
        }

        /**
         * @see org.opencms.ui.contextmenu.I_CmsSimpleContextMenuEntry#getVisibility(java.lang.Object)
         */
        public CmsMenuItemVisibilityMode getVisibility(Set<String> data) {

            if (data == null) {
                return CmsMenuItemVisibilityMode.VISIBILITY_INVISIBLE;
            }

            if (data.size() > 1) {
                return CmsMenuItemVisibilityMode.VISIBILITY_INVISIBLE;
            }

            if (!((Boolean)getItem(data.iterator().next()).getItemProperty(PROP_OK).getValue()).booleanValue()) {
                return CmsMenuItemVisibilityMode.VISIBILITY_INACTIVE;
            }

            return CmsMenuItemVisibilityMode.VISIBILITY_ACTIVE;
        }

    }

    /**
     * The menu entry to switch to the sitemap editor of concerning site.<p>
     */
    class SitemapEntry implements I_CmsSimpleContextMenuEntry<Set<String>> {

        /**
         * @see org.opencms.ui.contextmenu.I_CmsSimpleContextMenuEntry#executeAction(java.lang.Object)
         */
        public void executeAction(Set<String> data) {

            String siteRoot = data.iterator().next();
            A_CmsUI.get().changeSite(siteRoot);

            CmsSitemapEditorConfiguration sitemapApp = new CmsSitemapEditorConfiguration();
            sitemapApp.getAppLaunchCommand().run();
        }

        /**
         * @see org.opencms.ui.contextmenu.I_CmsSimpleContextMenuEntry#getTitle(java.util.Locale)
         */
        public String getTitle(Locale locale) {

            return CmsVaadinUtils.getMessageText(Messages.GUI_SITEMAP_TITLE_0);
        }

        /**
         * @see org.opencms.ui.contextmenu.I_CmsSimpleContextMenuEntry#getVisibility(java.lang.Object)
         */
        public CmsMenuItemVisibilityMode getVisibility(Set<String> data) {

            return (data != null) && (data.size() == 1)
            ? CmsMenuItemVisibilityMode.VISIBILITY_ACTIVE
            : CmsMenuItemVisibilityMode.VISIBILITY_INVISIBLE;
        }

    }

    /**Site aliases property.*/
    private static final String PROP_ALIASES = "aliases";

    /** Favicon property. */
    private static final String PROP_FAVICON = "favicon";

    /**Site icon property. */
    private static final String PROP_ICON = "icon";

    /**boolean property for webserver option. Used for style generator.*/
    private static final String PROP_IS_WEBSERVER = "isweb";

    /**Site path property  (is id for site row in table).*/
    private static final String PROP_PATH = "path";

    /**Site securesites property.*/
    private static final String PROP_SECURESITES = "securesites";

    /**Site server property.*/
    private static final String PROP_SERVER = "server";

    /**Site title property.*/
    public static final String PROP_TITLE = "title";

    /**Is site new? */
    public static final String PROP_NEW = "new";

    /**SSL Mode. */
    public static final String PROP_SSL = "ssl";

    /**Is site config ok? */
    public static final String PROP_OK = "ok";

    /**Is site path below other existing site? */
    public static final String PROP_UNDER_OTHER_SITE = "othersite";

    /**Were site root moved or renamed? */
    public static final String PROP_CHANGED = "changed";

    /**vaadin serial id.*/
    private static final long serialVersionUID = 4655464609332605219L;

    /** The project manager instance. */
    CmsSiteManager m_manager;

    /** The data container. */
    IndexedContainer m_container;

    /** The context menu. */
    private CmsContextMenu m_menu;

    /** The available menu entries. */
    private List<I_CmsSimpleContextMenuEntry<Set<String>>> m_menuEntries;

    /**Counter for valid sites.*/
    private int m_siteCounter;

    /**
     * Constructor.<p>
     *
     * @param manager the project manager
     */
    public CmsSitesTable(CmsSiteManager manager) {

        m_manager = manager;

        m_container = new IndexedContainer();
        m_container.addContainerProperty(
            PROP_ICON,
            Label.class,
            new Label(new CmsCssIcon(OpenCmsTheme.ICON_SITE).getHtml(), ContentMode.HTML));
        m_container.addContainerProperty(PROP_FAVICON, Image.class, null);
        m_container.addContainerProperty(PROP_SERVER, String.class, "");
        m_container.addContainerProperty(PROP_TITLE, String.class, "");
        m_container.addContainerProperty(PROP_IS_WEBSERVER, Boolean.class, new Boolean(true));
        m_container.addContainerProperty(PROP_PATH, String.class, "");
        m_container.addContainerProperty(PROP_ALIASES, String.class, "");
        m_container.addContainerProperty(PROP_SECURESITES, String.class, "");
        m_container.addContainerProperty(PROP_OK, Boolean.class, new Boolean(true));
        m_container.addContainerProperty(PROP_UNDER_OTHER_SITE, Boolean.class, new Boolean(false));
        m_container.addContainerProperty(PROP_CHANGED, Boolean.class, new Boolean(false));
        m_container.addContainerProperty(PROP_NEW, Boolean.class, new Boolean(false));
        m_container.addContainerProperty(PROP_SSL, Integer.class, new Integer(1));

        setContainerDataSource(m_container);
        setColumnHeader(PROP_SSL, "");
        setColumnHeader(PROP_ICON, "");
        setColumnHeader(PROP_FAVICON, "");
        setColumnHeader(PROP_SERVER, CmsVaadinUtils.getMessageText(Messages.GUI_SITE_SERVER_0));
        setColumnHeader(PROP_TITLE, CmsVaadinUtils.getMessageText(Messages.GUI_SITE_TITLE_0));
        setColumnHeader(PROP_PATH, CmsVaadinUtils.getMessageText(Messages.GUI_SITE_PATH_0));
        setColumnHeader(PROP_ALIASES, CmsVaadinUtils.getMessageText(Messages.GUI_SITE_ALIASES_0));
        setColumnHeader(PROP_SECURESITES, CmsVaadinUtils.getMessageText(Messages.GUI_SITE_SECURESERVER_0));
        setColumnAlignment(PROP_FAVICON, Align.CENTER);
        setColumnExpandRatio(PROP_SERVER, 2);
        setColumnExpandRatio(PROP_TITLE, 2);
        setColumnExpandRatio(PROP_PATH, 2);
        setColumnWidth(PROP_FAVICON, 40);
        setColumnWidth(PROP_SSL, 130);

        setColumnAlignment(PROP_FAVICON, Align.CENTER);
        setSelectable(true);
        setMultiSelect(true);
        m_menu = new CmsContextMenu();
        m_menu.setAsTableContextMenu(this);
        addItemClickListener(new ItemClickListener() {

            private static final long serialVersionUID = 1L;

            public void itemClick(ItemClickEvent event) {

                onItemClick(event, event.getItemId(), event.getPropertyId());
            }
        });
        setCellStyleGenerator(new CellStyleGenerator() {

            private static final long serialVersionUID = 1L;

            public String getStyle(Table source, Object itemId, Object propertyId) {

                String styles = "";

                if (PROP_SSL.equals(propertyId)) {
                    styles += " " + getSSLStyle(OpenCms.getSiteManager().getSiteForSiteRoot((String)itemId));
                }

                if (PROP_SERVER.equals(propertyId)) {
                    styles += " " + OpenCmsTheme.HOVER_COLUMN;
                    if (!((Boolean)source.getItem(itemId).getItemProperty(PROP_OK).getValue()).booleanValue()) {
                        if (((Boolean)source.getItem(itemId).getItemProperty(PROP_CHANGED).getValue()).booleanValue()) {
                            styles += " " + OpenCmsTheme.STATE_CHANGED;
                        } else {
                            styles += " " + OpenCmsTheme.EXPIRED;
                        }
                    } else {
                        if (((Boolean)source.getItem(itemId).getItemProperty(PROP_NEW).getValue()).booleanValue()) {
                            styles += " " + OpenCmsTheme.STATE_NEW;
                        }
                    }
                }
                if (PROP_TITLE.equals(propertyId)
                    & ((Boolean)source.getItem(itemId).getItemProperty(PROP_IS_WEBSERVER).getValue()).booleanValue()) {
                    styles += " " + OpenCmsTheme.IN_NAVIGATION;
                }
                if (styles.isEmpty()) {
                    return null;
                }

                return styles;
            }
        });

        addGeneratedColumn(PROP_SSL, new ColumnGenerator() {

            private static final long serialVersionUID = -2144476865774782965L;

            public Object generateCell(Table source, Object itemId, Object columnId) {

                return getSSLStatus(OpenCms.getSiteManager().getSiteForSiteRoot((String)itemId));

            }

        });
        addGeneratedColumn(PROP_FAVICON, new FavIconColumn());

        setItemDescriptionGenerator(new ItemDescriptionGenerator() {

            private static final long serialVersionUID = 7367011213487089661L;

            public String generateDescription(Component source, Object itemId, Object propertyId) {

                if (PROP_SSL.equals(propertyId)) {

                    return OpenCms.getSiteManager().getSiteForSiteRoot(
                        (String)itemId).getSSLMode().getLocalizedMessage();
                }
                return null;
            }
        });

        setColumnCollapsingAllowed(true);
        setColumnCollapsible(PROP_ALIASES, true);
        setColumnCollapsible(PROP_SECURESITES, true);
        setColumnCollapsible(PROP_PATH, false);
        setColumnCollapsible(PROP_SERVER, false);
        setColumnCollapsible(PROP_TITLE, false);
        setColumnCollapsible(PROP_FAVICON, false);
        setColumnCollapsible(PROP_ICON, false);

        setVisibleColumns(
            PROP_ICON,
            PROP_SSL,
            PROP_FAVICON,
            PROP_SERVER,
            PROP_TITLE,
            PROP_PATH,
            PROP_SECURESITES,
            PROP_ALIASES);

        setColumnCollapsed(PROP_ALIASES, true);
        setColumnCollapsed(PROP_SECURESITES, true);
        setColumnWidth(PROP_ICON, 40);
    }

    /**
     * Filters the table according to given search string.<p>
     *
     * @param search string to be looked for.
     */
    public void filterTable(String search) {

        m_container.removeAllContainerFilters();
        if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(search)) {
            m_container.addContainerFilter(
                new Or(
                    new SimpleStringFilter(PROP_TITLE, search, true, false),
                    new SimpleStringFilter(PROP_PATH, search, true, false),
                    new SimpleStringFilter(PROP_SERVER, search, true, false)));
        }
        if ((getValue() != null) & !((Set<String>)getValue()).isEmpty()) {
            setCurrentPageFirstItemId(((Set<String>)getValue()).iterator().next());
        }
    }

    /**
     * Returns number of correctly configured sites.<p>
     *
     * @return number of sites
     */
    public int getSitesCount() {

        return m_siteCounter;
    }

    /**
     *  Reads sites from Site Manager and adds them to tabel.<p>
     */
    public void loadSites() {

        m_container.removeAllItems();
        List<CmsSite> sites = OpenCms.getSiteManager().getAvailableSites(m_manager.getRootCmsObject(), true);
        m_siteCounter = 0;
        CmsCssIcon icon = new CmsCssIcon(OpenCmsTheme.ICON_SITE);
        icon.setOverlay(OpenCmsTheme.STATE_CHANGED + " " + CmsResourceIcon.ICON_CLASS_CHANGED);
        boolean showPublishButton = false;
        for (CmsSite site : sites) {
            if (site.getSiteMatcher() != null) {
                m_siteCounter++;
                Item item = m_container.addItem(site.getSiteRoot());

                item.getItemProperty(PROP_SERVER).setValue(site.getUrl());
                item.getItemProperty(PROP_TITLE).setValue(site.getTitle());
                item.getItemProperty(PROP_IS_WEBSERVER).setValue(new Boolean(site.isWebserver()));
                item.getItemProperty(PROP_PATH).setValue(site.getSiteRoot());
                item.getItemProperty(PROP_ALIASES).setValue(getNiceStringFormList(site.getAliases()));
                if (OpenCms.getSiteManager().isOnlyOfflineSite(site)) {
                    item.getItemProperty(PROP_NEW).setValue(new Boolean(true));
                    item.getItemProperty(PROP_ICON).setValue(new Label(icon.getHtmlWithOverlay(), ContentMode.HTML));
                    showPublishButton = true;
                } else {
                    item.getItemProperty(PROP_ICON).setValue(new Label(icon.getHtml(), ContentMode.HTML));
                }
                if (site.hasSecureServer()) {
                    item.getItemProperty(PROP_SECURESITES).setValue(site.getSecureUrl());
                }
                item.getItemProperty(PROP_OK).setValue(new Boolean(!OpenCms.getSiteManager().isSiteUnderSite(site)));
            }
        }

        for (CmsSite site : OpenCms.getSiteManager().getAvailableCorruptedSites(m_manager.getRootCmsObject(), true)) {

            Item item = m_container.addItem(site.getSiteRoot());

            //Make sure item doesn't exist in table yet.. should never happen
            if (item != null) {
                item.getItemProperty(PROP_ICON).setValue(new Label(icon.getHtml(), ContentMode.HTML));
                item.getItemProperty(PROP_SERVER).setValue(site.getUrl());
                item.getItemProperty(PROP_TITLE).setValue(site.getTitle());
                item.getItemProperty(PROP_IS_WEBSERVER).setValue(new Boolean(site.isWebserver()));
                item.getItemProperty(PROP_PATH).setValue(site.getSiteRoot());
                item.getItemProperty(PROP_ALIASES).setValue(getNiceStringFormList(site.getAliases()));
                item.getItemProperty(PROP_OK).setValue(new Boolean(false));
                if (!site.getSiteRootUUID().isNullUUID()) {
                    if (m_manager.getRootCmsObject().existsResource(site.getSiteRootUUID())) {
                        item.getItemProperty(PROP_CHANGED).setValue(new Boolean(true));
                        item.getItemProperty(PROP_ICON).setValue(
                            new Label(icon.getHtmlWithOverlay(), ContentMode.HTML));
                        showPublishButton = true;
                    } else {
                        //Site root deleted, publish makes no sense any more (-> OK=FALSE)

                    }
                }
                if (site.hasSecureServer()) {
                    item.getItemProperty(PROP_SECURESITES).setValue(site.getSecureUrl());
                }
            }
        }
        m_manager.showPublishButton(showPublishButton);
    }

    protected String getSSLStatus(CmsSite site) {

        if (site.getSSLMode().isSecure()) {
            return CmsVaadinUtils.getMessageText(Messages.GUI_SITE_ENCRYPTED_0);
        }
        return CmsVaadinUtils.getMessageText(Messages.GUI_SITE_UNENCRYPTED_0);
    }

    protected String getSSLStyle(CmsSite site) {

        if (site.getSSLMode().isSecure()) {
            return OpenCmsTheme.TABLE_COLUMN_BOX_CYAN;
        }
        return OpenCmsTheme.TABLE_COLUMN_BOX_GRAY;
    }

    /**
     * Returns an favicon image with click listener on right clicks.<p>
     *
     * @param itemId of row to put image in.
     * @return Vaadin Image.
     */
    Image getImageFavIcon(final String itemId) {

        Resource resource = getFavIconResource(itemId);

        if (resource != null) {

            Image favIconImage = new Image("", resource);

            favIconImage.setDescription(CmsVaadinUtils.getMessageText(Messages.GUI_SITE_FAVICON_0));

            favIconImage.addClickListener(new MouseEvents.ClickListener() {

                private static final long serialVersionUID = 5954790734673665522L;

                public void click(com.vaadin.event.MouseEvents.ClickEvent event) {

                    onItemClick(event, itemId, PROP_FAVICON);

                }
            });

            return favIconImage;
        } else {
            return null;
        }
    }

    /**
     * Returns the available menu entries.<p>
     *
     * @return the menu entries
     */
    List<I_CmsSimpleContextMenuEntry<Set<String>>> getMenuEntries() {

        if (m_menuEntries == null) {
            m_menuEntries = new ArrayList<I_CmsSimpleContextMenuEntry<Set<String>>>();
            m_menuEntries.add(new EditEntry());
            m_menuEntries.add(new DeleteEntry());
            m_menuEntries.add(new ExplorerEntry());
            //            m_menuEntries.add(new SitemapEntry()); //TODO add when sitemap issue fixed
            m_menuEntries.add(new PageEditorEntry());
        }
        return m_menuEntries;
    }

    /**
     * Handles the table item clicks, including clicks on images inside of a table item.<p>
     *
     * @param event the click event
     * @param itemId of the clicked row
     * @param propertyId column id
     */
    @SuppressWarnings("unchecked")
    void onItemClick(MouseEvents.ClickEvent event, Object itemId, Object propertyId) {

        if (!event.isCtrlKey() && !event.isShiftKey()) {

            changeValueIfNotMultiSelect(itemId);

            // don't interfere with multi-selection using control key
            if (event.getButton().equals(MouseButton.RIGHT) || (propertyId == PROP_ICON)) {

                m_menu.setEntries(getMenuEntries(), (Set<String>)getValue());
                m_menu.openForTable(event, itemId, propertyId, this);
            } else if (event.getButton().equals(MouseButton.LEFT) && PROP_SERVER.equals(propertyId)) {
                String siteRoot = (String)itemId;
                m_manager.openEditDailog(siteRoot);
            }
        }
    }

    /**
     * Checks value of table and sets it new if needed:<p>
     * if multiselect: new itemId is in current Value? -> no change of value<p>
     * no multiselect and multiselect, but new item not selected before: set value to new item<p>
     *
     * @param itemId if of clicked item
     */
    private void changeValueIfNotMultiSelect(Object itemId) {

        @SuppressWarnings("unchecked")
        Set<String> value = (Set<String>)getValue();
        if (value == null) {
            select(itemId);
        } else if (!value.contains(itemId)) {
            setValue(null);
            select(itemId);
        }
    }

    /**
     * Loads the FavIcon of a given site.<p>
     *
     * @param siteRoot of the given site.
     * @return the favicon as resource or default image if no faicon was found.
     */
    private Resource getFavIconResource(String siteRoot) {

        try {
            CmsResource favicon = m_manager.getRootCmsObject().readResource(siteRoot + "/" + CmsSiteManager.FAVICON);
            CmsFile faviconFile = m_manager.getRootCmsObject().readFile(favicon);
            final byte[] imageData = faviconFile.getContents();
            return new StreamResource(new StreamResource.StreamSource() {

                private static final long serialVersionUID = -8868657402793427460L;

                public InputStream getStream() {

                    return new ByteArrayInputStream(imageData);

                }
            }, "");
        } catch (CmsException e) {
            return null;
        }
    }

    /**
     * Makes a String from aliases (comma separated).<p>
     *
     * @param aliases List of aliases.
     * @return nice string.
     */
    private String getNiceStringFormList(List<CmsSiteMatcher> aliases) {

        if (aliases.isEmpty()) {
            return "";
        }
        String ret = "";
        for (CmsSiteMatcher alias : aliases) {
            ret += alias.getServerName() + ", ";
        }
        return ret.substring(0, ret.length() - ", ".length());
    }

}