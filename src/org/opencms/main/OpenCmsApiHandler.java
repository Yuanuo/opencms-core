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

package org.opencms.main;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.opencms.configuration.CmsParameterConfiguration;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsResource;
import org.opencms.loader.CmsJspLoader;
import org.opencms.loader.I_CmsResourceLoader;
import org.opencms.site.CmsSite;
import org.opencms.util.CmsStringUtil;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Handles custom api requests.<p>
 */
public class OpenCmsApiHandler extends HttpServlet implements I_CmsRequestHandler {

    /**
     * A constant for the optional 'baseUri' parameter.
     */
    public static final String PARAM_BASE_URI = "baseUri";

    /**
     * A constant for the HTTP 'referer'.
     */
    protected static final String HEADER_REFERER_KEY = "referer";

    /**
     * The handler name.
     */
    private static final String HANDLER_NAME = "Api";

    /**
     * The names used by this request handler.
     */
    private static final String[] HANDLER_NAMES = new String[]{HANDLER_NAME};

    /**
     * The log object for this class.
     */
    private static final Log LOG = CmsLog.getLog(OpenCmsApiHandler.class);

    /**
     * The serial version id.
     */
    private static final long serialVersionUID = -6028091947126299833L;

    private CmsParameterConfiguration params;

    /**
     * Returns the path to the spell check handler.<p>
     *
     * @return the path to the spell check handler
     */
    public static String getHandlerPath() {

        return OpenCmsServlet.HANDLE_PATH + HANDLER_NAME;
    }

    /**
     * Checks if the spell check request handler is configured.<p>
     *
     * @return <code>true</code> if the spell check request handler is configured
     */
    public static boolean isHandlerEnabled() {

        return OpenCmsCore.getInstance().getRequestHandler(HANDLER_NAME) != null;
    }

    /**
     * @see I_CmsRequestHandler#getHandlerNames()
     */
    public String[] getHandlerNames() {

        return HANDLER_NAMES;
    }

    @Override
    public void initParameters(CmsParameterConfiguration params) {
        // don't allow to overwrite this
        if (null == this.params)
            this.params = params;
    }

    /**
     * @see I_CmsRequestHandler#handle(HttpServletRequest, HttpServletResponse, String)
     */
    public void handle(HttpServletRequest req, HttpServletResponse res, String name) throws IOException {

        try {
            final String path = OpenCmsCore.getPathInfo(req).substring(11); // length of "/handleApi/"
            String targetJsp;
            if (path.endsWith(".api")) {
                // force the leading path to disable execute other module's jsp
                targetJsp = "/system/modules/api." + path.replaceAll("api$", "jsp");
                // avoid relative path
                targetJsp = targetJsp.replace("..", "");
            } else {
                // jsp from config is trusted
                targetJsp = null != this.params ? this.params.get(path) : null;
            }
            if (StringUtils.isBlank(targetJsp)) {
                sendError(res, 404, "unknown api");
                return;
            }
            final CmsObject cms = getCmsObject(req);
            CmsResource targetJspRes = null;
            try {
                targetJspRes = cms.readResource(targetJsp);
            } catch (CmsException ce) {
                LOG.error(ce.getLocalizedMessage(), ce);
            }
            if (null == targetJspRes) {
                sendError(res, 404, "unknown api in vfs");
                return;
            }
            I_CmsResourceLoader loader = OpenCms.getResourceManager().getLoader(CmsJspLoader.RESOURCE_LOADER_ID);
            loader.load(cms, targetJspRes, req, res);
        } catch (Exception e) {
            LOG.error(e.getLocalizedMessage(), e);
            sendError(res, 500, "error");
        }
    }

    private static void sendError(HttpServletResponse resp, int code, String msg) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        resp.getWriter().write("{\"code\":\"" + code + "\", \"msg\":\"" + msg + "\"}");
        resp.getWriter().flush();
        resp.getWriter().close();
    }

    /**
     * Returns the CMS object.<p>
     *
     * @param req the request
     * @return the CMS object
     * @throws CmsException if something goes wrong
     */
    protected CmsObject getCmsObject(HttpServletRequest req) throws CmsException {

        CmsObject cms = OpenCmsCore.getInstance().initCmsObjectFromSession(req);
        // use the guest user as fall back
        if (cms == null) {
            cms = OpenCmsCore.getInstance().initCmsObject(OpenCms.getDefaultUsers().getUserGuest());
            String siteRoot = OpenCmsCore.getInstance().getSiteManager().matchRequest(req).getSiteRoot();
            cms.getRequestContext().setSiteRoot(siteRoot);
        }
        String baseUri = getBaseUri(req, cms);
        if (baseUri != null) {
            cms.getRequestContext().setUri(baseUri);
        }
        return cms;
    }

    /**
     * Returns the base URI.<p>
     *
     * @param req the servlet request
     * @param cms the CmsObject
     * @return the base URI
     */
    private String getBaseUri(HttpServletRequest req, CmsObject cms) {

        String baseUri = req.getParameter(PARAM_BASE_URI);
        if (CmsStringUtil.isEmptyOrWhitespaceOnly(baseUri)) {
            String referer = req.getHeader(HEADER_REFERER_KEY);
            CmsSite site = OpenCms.getSiteManager().getSiteForSiteRoot(cms.getRequestContext().getSiteRoot());
            if (site != null) {
                String prefix = site.getServerPrefix(cms, "/") + OpenCms.getStaticExportManager().getVfsPrefix();
                if ((referer != null) && referer.startsWith(prefix)) {
                    baseUri = referer.substring(prefix.length());
                }
            }
        }
        return baseUri;
    }
}
