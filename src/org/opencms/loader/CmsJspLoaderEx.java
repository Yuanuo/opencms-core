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

//package org.opencms.loader;
//
//import org.opencms.file.CmsObject;
//import org.opencms.file.CmsProperty;
//import org.opencms.file.CmsResource;
//import org.opencms.main.CmsException;
//import org.opencms.main.OpenCms;
//import org.opencms.site.CmsSite;
//
//import java.io.BufferedWriter;
//import java.io.ByteArrayOutputStream;
//import java.io.IOException;
//import java.io.OutputStreamWriter;
//import java.io.PrintWriter;
//import java.util.Enumeration;
//import java.util.Locale;
//import java.util.SortedMap;
//
//import javax.servlet.ServletException;
//import javax.servlet.ServletOutputStream;
//import javax.servlet.ServletRequest;
//import javax.servlet.ServletResponse;
//import javax.servlet.WriteListener;
//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletResponse;
//import javax.servlet.http.HttpServletResponseWrapper;
//
//import org.apache.commons.lang3.StringUtils;
//
//import com.hankcs.hanlp.HanLP;
//
//public class CmsJspLoaderEx extends CmsJspLoader {
//
//    /**
//     * Default constructor.<p>
//     */
//    public CmsJspLoaderEx() {
//
//        super();
//    }
//
//    public void service(CmsObject cms, CmsResource resource, ServletRequest req, ServletResponse res)
//    throws ServletException, IOException, CmsLoaderException {
//
//        CmsSite cmsSite = OpenCms.getSiteManager().getCurrentSite(cms);
//        SortedMap<String, String> siteParams = cmsSite.getParameters();
//        String localeAutohans = null == siteParams ? "" : (String)siteParams.getOrDefault("locale.autohans", "");
//        String resourcePath = null == resource ? "" : resource.getRootPath();
//        if (localeAutohans.equals("area") && resourcePath.endsWith("jsp")) {
//            CmsProperty resourceProp = null;
//            CmsResource detailRes = cms.getRequestContext().getDetailResource();
//            if (null != detailRes) {
//                try {
//                    resourceProp = cms.readPropertyObject(detailRes, "locale.autohans", true);
//                } catch (Exception var14) {}
//            }
//
//            if (null == resourceProp || resourceProp.isNullProperty()) {
//                try {
//                    resourceProp = cms.readPropertyObject(resource, "locale.autohans", true);
//                } catch (Exception var13) {}
//            }
//
//            if (null != resourceProp && "true".equals(resourceProp.getValue())) {
//                RequestLocale reqLocale = this.preDetectRequestLocale(cms, resource, req, res);
//                if (null != reqLocale) {
//                    try {
//                        HttpServletResponseWrapperEx wrappedRes = new HttpServletResponseWrapperEx(
//                            (HttpServletResponse)res);
//                        super.service(cms, resource, req, wrappedRes);
//                        this.processTranslation(cms, resource, req, res, reqLocale, wrappedRes.getWriterBytes());
//                        return;
//                    } catch (Exception var15) {}
//                }
//            }
//        }
//
//        super.service(cms, resource, req, res);
//    }
//
//    public void load(CmsObject cms, CmsResource resource, HttpServletRequest req, HttpServletResponse res)
//    throws ServletException, IOException, CmsException {
//
//        final CmsSite cmsSite = OpenCms.getSiteManager().getCurrentSite(cms);
//        final SortedMap<String, String> siteParams = cmsSite.getParameters();
//        final String localeAutohans = null == siteParams ? "" : siteParams.getOrDefault("locale.autohans", "");
//        final String resourcePath = null == resource ? "" : resource.getRootPath();
//        if (localeAutohans.equals("site")
//            && StringUtils.endsWithAny(resourcePath, "jsp", "html")) {
//            RequestLocale reqLocale = this.preDetectRequestLocale(cms, resource, req, res);
//            if (null != reqLocale) {
//                try {
//                    HttpServletResponseWrapperEx wrappedRes = new HttpServletResponseWrapperEx(res);
//                    super.load(cms, resource, req, wrappedRes);
//                    this.processTranslation(cms, resource, req, res, reqLocale, wrappedRes.getWriterBytes());
//                    return;
//                } catch (Exception var11) {}
//            }
//        }
//
//        super.load(cms, resource, req, res);
//    }
//
//    private RequestLocale preDetectRequestLocale(
//        CmsObject cms,
//        CmsResource resource,
//        ServletRequest req,
//        ServletResponse res) {
//
//        String activeBy = this.getConfiguration().getOrDefault("activeby", "urlparam, toplng");
//        RequestLocale reqLocale = null;
//        if (activeBy.contains("urlparam")) {
//            reqLocale = RequestLocale.valueBy(req.getParameter("plz.hans"));
//        }
//
//        if (null == reqLocale && activeBy.contains("toplng")) {
//            Enumeration<Locale> locales = req.getLocales();
//
//            while (locales.hasMoreElements()) {
//                Locale locale = locales.nextElement();
//                reqLocale = RequestLocale.valueBy(locale.toString());
//                if (null != reqLocale) {
//                    break;
//                }
//            }
//        }
//
//        if (null == reqLocale && activeBy.contains("locale")) {
//            reqLocale = RequestLocale.valueBy(cms.getRequestContext().getLocale().toString());
//        }
//
//        return reqLocale;
//    }
//
//    private void processTranslation(
//        CmsObject cms,
//        CmsResource resource,
//        ServletRequest req,
//        ServletResponse res,
//        RequestLocale reqLocale,
//        byte[] bytes)
//    throws IOException {
//
//        String wrappedCon = new String(bytes, res.getCharacterEncoding());
//        switch (reqLocale) {
//            case cn:
//                wrappedCon = HanLP.t2s(wrappedCon);
//                break;
//            case tw:
//                wrappedCon = HanLP.s2tw(wrappedCon);
//                break;
//            case hk:
//                wrappedCon = HanLP.s2hk(wrappedCon);
//                break;
//            case t:
//                wrappedCon = HanLP.s2t(wrappedCon);
//                break;
//            default:
//                break;
//        }
//        byte[] result = wrappedCon.getBytes(res.getCharacterEncoding());
//        try {
//            res.setContentLength(result.length);
//            res.getOutputStream().write(result);
//            res.getOutputStream().flush();
//        } finally {
//            result = null;
//        }
//    }
//
//    private static enum RequestLocale {
//
//        t, tw, hk, cn;
//
//        static RequestLocale valueBy(String string) {
//
//            string = null != string ? string.toLowerCase() : "";
//            switch (string) {
//                case "zh_tw":
//                case "zh-tw":
//                    return tw;
//                case "zh_hk":
//                case "zh-hk":
//                    return hk;
//                case "hant":
//                    return t;
//                case "zh_cn":
//                case "zh-cn":
//                    return cn;
//                default:
//                    return null;
//            }
//        }
//    }
//    
//    private static class HttpServletResponseWrapperEx extends HttpServletResponseWrapper {
//        ServletOutputStreamEx m_out;
//        PrintWriter m_writer;
//
//        public HttpServletResponseWrapperEx(HttpServletResponse response) {
//            super(response);
//        }
//
//        public ServletOutputStream getOutputStream() throws IOException {
//            if (null == this.m_out) {
//                this.initStream();
//            }
//
//            return this.m_out;
//        }
//
//        public PrintWriter getWriter() throws IOException {
//            if (null == this.m_writer) {
//                this.initStream();
//            }
//
//            return this.m_writer;
//        }
//
//        void initStream() throws IOException {
//            if (this.m_out == null) {
//                this.m_out = new ServletOutputStreamEx();
//            }
//
//            if (this.m_writer == null) {
//                this.m_writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(this.m_out, this.getCharacterEncoding())), false);
//            }
//
//        }
//
//        public byte[] getWriterBytes() {
//            if (this.m_out == null) {
//                return new byte[0];
//            } else {
//                if (this.m_writer != null) {
//                    this.m_writer.flush();
//                }
//
//                return this.m_out.getBytes();
//            }
//        }
//    }
//    
//    private static class ServletOutputStreamEx extends ServletOutputStream {
//        private ServletOutputStream m_servletStream;
//        private ByteArrayOutputStream m_stream;
//
//        public ServletOutputStreamEx() {
//            this.m_servletStream = null;
//            this.clear();
//        }
//
//        public ServletOutputStreamEx(ServletOutputStream servletStream) {
//            this.m_servletStream = servletStream;
//            this.clear();
//        }
//
//        public void clear() {
//            this.m_stream = new ByteArrayOutputStream(1024);
//        }
//
//        public void close() throws IOException {
//            if (this.m_stream != null) {
//                this.m_stream.close();
//            }
//
//            if (this.m_servletStream != null) {
//                this.m_servletStream.close();
//            }
//
//            super.close();
//        }
//
//        public void flush() throws IOException {
//            if (this.m_servletStream != null) {
//                this.m_servletStream.flush();
//            }
//
//        }
//
//        public byte[] getBytes() {
//            return null == this.m_stream ? new byte[0] : this.m_stream.toByteArray();
//        }
//
//        public String getString() {
//            return null == this.m_stream ? "" : this.m_stream.toString();
//        }
//
//        public void write(byte[] b, int off, int len) throws IOException {
//            this.m_stream.write(b, off, len);
//            if (this.m_servletStream != null) {
//                this.m_servletStream.write(b, off, len);
//            }
//
//        }
//
//        public void write(int b) throws IOException {
//            this.m_stream.write(b);
//            if (this.m_servletStream != null) {
//                this.m_servletStream.write(b);
//            }
//
//        }
//
//        public boolean isReady() {
//            return null != this.m_stream;
//        }
//
//        public void setWriteListener(WriteListener writeListener) {
//        }
//    }
//}
