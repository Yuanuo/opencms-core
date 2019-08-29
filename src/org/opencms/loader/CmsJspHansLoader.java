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

package org.opencms.loader;

import org.opencms.file.CmsObject;
import org.opencms.file.CmsResource;
import org.opencms.main.CmsException;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Locale;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.apache.commons.lang3.StringUtils;

import com.hankcs.hanlp.HanLP;

public class CmsJspHansLoader extends CmsJspLoader {

    /**
     * Default constructor.<p>
     */
    public CmsJspHansLoader() {

        super();
    }

    /**
     * @see org.opencms.loader.A_CmsXmlDocumentLoader#load(org.opencms.file.CmsObject, org.opencms.file.CmsResource, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    public void load(CmsObject cms, CmsResource resource, HttpServletRequest req, HttpServletResponse res)
    throws ServletException, IOException, CmsException {

        final boolean byLocaleLng = getConfiguration().getBoolean("accept.locale", false);
        final boolean byAcceptLngAny = getConfiguration().getBoolean("accept.language.any", false);
        final boolean byAcceptLngTop = getConfiguration().getBoolean("accept.language.top", !byAcceptLngAny);
        
        if (byLocaleLng || byAcceptLngTop || byAcceptLngAny) {
            final String localeLng = byLocaleLng ? String.valueOf(cms.getRequestContext().getLocale()).toLowerCase() : null;
            final String headerLng = String.valueOf(req.getHeader("Accept-Language")).toLowerCase();
            TranslateHan translateHans = null;
            
            if(byAcceptLngAny) {
                translateHans = TranslateHan.valueByContains(headerLng);
            } else if(byAcceptLngTop) {
                final int idxOfZhcn = headerLng.indexOf("zh-cn");
                int idxOfHant = Integer.MAX_VALUE;
                for(String tmpItm : headerLng.split(";")) {
                    translateHans = TranslateHan.valueByContains(tmpItm);
                    if(null != translateHans) {
                        idxOfHant = headerLng.indexOf(tmpItm);
                        break;
                    }
                }
                if(idxOfZhcn > -1 && idxOfZhcn < idxOfHant) {
                    // founded hant flag is after than idxOfZhcn
                    translateHans = null;
                }
            } else if(byLocaleLng && localeLng != null) {
                translateHans = TranslateHan.valueByContains(localeLng);
            }
            if(null != translateHans) {
                //force set to 'zh', cause no zh_TW in solr fields.
                cms.getRequestContext().setLocale(Locale.CHINESE);
                final ByteArrayServletResponseWrapper wrappedRes = new ByteArrayServletResponseWrapper(res);
                super.load(cms, resource, req, wrappedRes);
                String wrappedCon = new String(wrappedRes.getWriterBytes(), res.getCharacterEncoding());
                // process translate
                switch(translateHans) {
                    case tw:
                        wrappedCon = HanLP.s2tw(wrappedCon);
                        break;
                    case hk:
                        wrappedCon = HanLP.s2hk(wrappedCon);
                        break;
                    case t:
                    default:
                        wrappedCon = HanLP.s2t(wrappedCon);
                        break;
                }
                final byte[] result = wrappedCon.getBytes(res.getCharacterEncoding());
                res.setContentLength(result.length);
                res.getOutputStream().write(result);
                res.getOutputStream().flush();
                return ;
            }
        }

        super.load(cms, resource, req, res);
    }
    
    private static enum TranslateHan {
        t, tw, hk;
        
        static TranslateHan valueByContains(String lowerLngString) {

            if(lowerLngString.contains("zh_tw") || lowerLngString.contains("zh-tw")) {
                return TranslateHan.tw;
            } else if(lowerLngString.contains("zh_hk") || lowerLngString.contains("zh-hk")) {
                return TranslateHan.hk;
            } else if(lowerLngString.contains("hant")) {
                return TranslateHan.t;
            } else if(lowerLngString.contains("zh_cn") || lowerLngString.contains("zh-cn")) {
                return null;
            }
            return null;
        }
    }
    
    private static class ByteArrayServletResponseWrapper extends HttpServletResponseWrapper {

        ByteArrayServletOutputStream m_out;
        /** A print writer that writes in the m_out stream. */
        java.io.PrintWriter m_writer;
        public ByteArrayServletResponseWrapper(HttpServletResponse response) {

            super(response);
        }

        /**
         * @see javax.servlet.ServletResponseWrapper#getOutputStream()
         */
        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            
            if(null == m_out) {
                initStream();
            }
            return m_out;
        }
        
        @Override
        public PrintWriter getWriter() throws IOException {
        
            if(null == m_writer) {
                initStream();
            }
            return m_writer;
        }
        
        void initStream() throws IOException {
            
            if(m_out == null) {
                m_out = new ByteArrayServletOutputStream();
            }

            if (m_writer == null) {
                // create a PrintWriter that uses the encoding required for the request context
                m_writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(m_out, getCharacterEncoding())), false);
            }
        }

        /**
         * Returns the bytes that have been written on the current writers output stream.<p>
         *
         * @return the bytes that have been written on the current writers output stream
         */
        public byte[] getWriterBytes() {

            if (m_out == null) {
                // No output was written so far, just return an empty array
                return new byte[0];
            }
            if (m_writer != null) {
                // Flush the writer in case something was written on it
                m_writer.flush();
            }
            return m_out.getBytes();
        }
    }

    /**
     * Wrapped implementation of the ServletOutputStream.<p>
     *
     * This implementation writes to an internal buffer and optionally to another
     * output stream at the same time.<p>
     *
     * It should be fully transparent to the standard ServletOutputStream.<p>
     */
    private static class ByteArrayServletOutputStream extends ServletOutputStream {

        /** The optional output stream to write to. */
        private ServletOutputStream m_servletStream;

        /** The internal stream buffer. */
        private ByteArrayOutputStream m_stream;

        /**
         * Constructor that must be used if the stream should write
         * only to a buffer.<p>
         */
        public ByteArrayServletOutputStream() {

            m_servletStream = null;
            clear();
        }

        /**
         * Constructor that must be used if the stream should write
         * to a buffer and to another stream at the same time.<p>
         *
         * @param servletStream The stream to write to
         */
        public ByteArrayServletOutputStream(ServletOutputStream servletStream) {

            m_servletStream = servletStream;
            clear();
        }

        /**
         * Clears the buffer by initializing the buffer with a new stream.<p>
         */
        public void clear() {

            m_stream = new java.io.ByteArrayOutputStream(1024);
        }

        /**
         * @see java.io.OutputStream#close()
         */
        @Override
        public void close() throws IOException {

            if (m_stream != null) {
                m_stream.close();
            }
            if (m_servletStream != null) {
                m_servletStream.close();
            }
            super.close();
        }

        /**
         * @see java.io.OutputStream#flush()
         */
        @Override
        public void flush() throws IOException {

            if (m_servletStream != null) {
                m_servletStream.flush();
            }
        }

        /**
         * Provides access to the bytes cached in the buffer.<p>
         *
         * @return the cached bytes from the buffer
         */
        public byte[] getBytes() {

            return null == m_stream ? new byte[0] : m_stream.toByteArray();
        }
        
        public String getString() {

            return null == m_stream ? "" : m_stream.toString();
        }

        /**
         * @see java.io.OutputStream#write(byte[], int, int)
         */
        @Override
        public void write(byte[] b, int off, int len) throws IOException {

            m_stream.write(b, off, len);
            if (m_servletStream != null) {
                m_servletStream.write(b, off, len);
            }
        }

        /**
         * @see java.io.OutputStream#write(int)
         */
        @Override
        public void write(int b) throws IOException {

            m_stream.write(b);
            if (m_servletStream != null) {
                m_servletStream.write(b);
            }
        }

        /**
         * @see javax.servlet.ServletOutputStream#isReady()
         */
        @Override
        public boolean isReady() {

            return null != m_stream;
        }

        /**
         * @see javax.servlet.ServletOutputStream#setWriteListener(javax.servlet.WriteListener)
         */
        @Override
        public void setWriteListener(WriteListener writeListener) {

        }
    }
}
