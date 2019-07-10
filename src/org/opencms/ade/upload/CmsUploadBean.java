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

package org.opencms.ade.upload;

import org.opencms.db.CmsDbSqlException;
import org.opencms.db.CmsImportFolder;
import org.opencms.file.CmsFile;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsProperty;
import org.opencms.file.CmsPropertyDefinition;
import org.opencms.file.CmsResource;
import org.opencms.file.CmsResourceFilter;
import org.opencms.file.types.CmsResourceTypePlain;
import org.opencms.file.types.I_CmsResourceType;
import org.opencms.gwt.shared.I_CmsUploadConstants;
import org.opencms.i18n.CmsMessages;
import org.opencms.json.JSONArray;
import org.opencms.json.JSONException;
import org.opencms.json.JSONObject;
import org.opencms.jsp.CmsJspBean;
import org.opencms.loader.CmsLoaderException;
import org.opencms.lock.CmsLockException;
import org.opencms.main.CmsException;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.security.CmsSecurityException;
import org.opencms.util.CmsCollectionsGenericWrapper;
import org.opencms.util.CmsRequestUtil;
import org.opencms.util.CmsStringUtil;
import org.opencms.util.CmsUUID;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.PageContext;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadBase.FileSizeLimitExceededException;
import org.apache.commons.fileupload.FileUploadBase.SizeLimitExceededException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.logging.Log;

/**
 * Bean to be used in JSP scriptlet code that provides
 * access to the upload functionality.<p>
 *
 * @since 8.0.0
 */
public class CmsUploadBean extends CmsJspBean {

    /** The default upload timeout. */
    public static final int DEFAULT_UPLOAD_TIMEOUT = 20000;

    /** Key name for the session attribute that stores the id of the current listener. */
    public static final String SESSION_ATTRIBUTE_LISTENER_ID = "__CmsUploadBean.LISTENER";

    /** The log object for this class. */
    private static final Log LOG = CmsLog.getLog(CmsUploadBean.class);

    /** A static map of all listeners. */
    private static Map<CmsUUID, CmsUploadListener> m_listeners = new HashMap<CmsUUID, CmsUploadListener>();

    /** The gwt message bundle. */
    private CmsMessages m_bundle = org.opencms.ade.upload.Messages.get().getBundle();

    /** Signals that the start method is called. */
    private boolean m_called;

    /** A list of the file items to upload. */
    private List<FileItem> m_multiPartFileItems;

    /** The map of parameters read from the current request. */
    private Map<String, String[]> m_parameterMap;

    /** The names by id of the resources that have been created successfully. */
    private HashMap<String, String> m_resourcesCreated = new HashMap<String, String>();

    /** A CMS context for the root site. */
    private CmsObject m_rootCms;

    /** The server side upload delay. */
    private int m_uploadDelay;

    /** The upload hook URI. */
    private String m_uploadHook;

    /**
     * Constructor, with parameters.<p>
     *
     * @param context the JSP page context object
     * @param req the JSP request
     * @param res the JSP response
     *
     * @throws CmsException if something goes wrong
     */
    public CmsUploadBean(PageContext context, HttpServletRequest req, HttpServletResponse res)
    throws CmsException {

        super();
        init(context, req, res);

        m_rootCms = OpenCms.initCmsObject(getCmsObject());
        m_rootCms.getRequestContext().setSiteRoot("");
    }

    /**
     * Returns the listener for given CmsUUID.<p>
     *
     * @param listenerId the uuid
     *
     * @return the according listener
     */
    public static CmsUploadListener getCurrentListener(CmsUUID listenerId) {

        return m_listeners.get(listenerId);
    }

    /**
     * Returns the VFS path for the given filename and folder.<p>
     *
     * @param cms the cms object
     * @param fileName the filename to combine with the folder
     * @param folder the folder to combine with the filename
     *
     * @return the VFS path for the given filename and folder
     */
    public static String getNewResourceName(CmsObject cms, String fileName, String folder) {

        String newResname = CmsResource.getName(fileName.replace('\\', '/'));
        newResname = cms.getRequestContext().getFileTranslator().translateResource(newResname);
        newResname = folder + newResname;
        return newResname;
    }

    /**
     * Sets the uploadDelay.<p>
     *
     * @param uploadDelay the uploadDelay to set
     */
    public void setUploadDelay(int uploadDelay) {

        m_uploadDelay = uploadDelay;
    }

    /**
     * Starts the upload.<p>
     *
     * @return the response String (JSON)
     */
    public String start() {

        // ensure that this method can only be called once
        if (m_called) {
            throw new UnsupportedOperationException();
        }
        m_called = true;

        // create a upload listener
        CmsUploadListener listener = createListener();
        try {
            // try to parse the request
            parseRequest(listener);
            // try to create the resources on the VFS
            createResources(listener);
            // trigger update offline indexes, important for gallery search
            OpenCms.getSearchManager().updateOfflineIndexes();
        } catch (CmsException e) {
            // an error occurred while creating the resources on the VFS, create a special error message
            LOG.error(e.getMessage(), e);
            return generateResponse(Boolean.FALSE, getCreationErrorMessage(), formatStackTrace(e));
        } catch (CmsUploadException e) {
            // an expected error occurred while parsing the request, the error message is already set in the exception
            LOG.debug(e.getMessage(), e);
            return generateResponse(Boolean.FALSE, e.getMessage(), formatStackTrace(e));
        } catch (Throwable e) {
            // an unexpected error occurred while parsing the request, create a non-specific error message
            LOG.error(e.getMessage(), e);
            String message = m_bundle.key(org.opencms.ade.upload.Messages.ERR_UPLOAD_UNEXPECTED_0);
            return generateResponse(Boolean.FALSE, message, formatStackTrace(e));
        } finally {
            removeListener(listener.getId());
        }
        // the upload was successful inform the user about success
        return generateResponse(Boolean.TRUE, m_bundle.key(org.opencms.ade.upload.Messages.LOG_UPLOAD_SUCCESS_0), "");
    }

    /**
     * Creates a upload listener and puts it into the static map.<p>
     *
     * @return the listener
     */
    private CmsUploadListener createListener() {

        CmsUploadListener listener = new CmsUploadListener(getRequest().getContentLength());
        listener.setDelay(m_uploadDelay);
        m_listeners.put(listener.getId(), listener);
        getRequest().getSession().setAttribute(SESSION_ATTRIBUTE_LISTENER_ID, listener.getId());
        return listener;
    }

    /**
     * In order to set a meaningful Title for each uploaded file, 
     * this feature allows a text file called "env.txt" to be provided in the upload queue.
     * The format of each file in the file must be "filename[TAB or SPACE]fileTitle".
     * 
     * For example, mass production of data,
     * the need to generate a large number of articles uploaded to the system,
     * The file name likes "article_00001.xml ~ article_00100.xml",
     * If by default, the file "article_00001.xml" shows Title "article_00001",
     * Very inconvenient when managing resources.
     * 
     * So, if you provide a title definition in "env.txt," for example:
     *  article_00001.xml Some Title For Article1
     *  article_00002.xml Some Title For Article2
     *  ...
     * , This time to create a resource,
     * the file "article_00001.xml" display Title "Some Title For Article1",
     * so that it will be very convenient in the management of resources.
     * 
     * For the directory can also be:
     *  folder Articles
     *  folder\subfolder Category1 of Articles
     * 
     * @return the envMap with fileName-preSetString
     */
    private Map<String, String> prepareEnvSet() {

        FileItem preEnvItem = null;
        for (FileItem fileItem : m_multiPartFileItems) {
            if("env.txt".equalsIgnoreCase(fileItem.getName())) {
                preEnvItem = fileItem;
                break;
            }
        }
        Map<String, String> envMap = new HashMap<>();
        if(null != preEnvItem) {
            try (InputStream inStream = preEnvItem.getInputStream()) {
                IOUtils.lineIterator(inStream, "UTF-8").forEachRemaining(line -> {
                    String[] arr = line.split("\t", 2);
                    if(arr.length != 2)
                        arr = line.split(" ", 2);
                    if(arr.length == 2)
                        envMap.put(arr[0].trim(), arr[1].trim());
                });
            } catch (Exception e) {
                //do nothing
            }
            //The env.txt file does not need to be stored, remove from the file queue.
            m_multiPartFileItems.remove(preEnvItem);
            //delete from disk
            preEnvItem.delete();
        }
        return envMap;
    }

    /**
     * Creates the resources.<p>
     * @param listener the listener
     *
     * @throws CmsException if something goes wrong
     * @throws UnsupportedEncodingException
     */
    private void createResources(CmsUploadListener listener) throws CmsException, UnsupportedEncodingException {

        CmsObject cms = getCmsObject();
        String[] isRootPathVals = m_parameterMap.get(I_CmsUploadConstants.UPLOAD_IS_ROOT_PATH_FIELD_NAME);
        if ((isRootPathVals != null) && (isRootPathVals.length > 0) && Boolean.parseBoolean(isRootPathVals[0])) {
            cms = m_rootCms;
        }
        // get the target folder
        String targetFolder = getTargetFolder(cms);
        m_uploadHook = OpenCms.getWorkplaceManager().getUploadHook(cms, targetFolder);

        List<String> filesToUnzip = getFilesToUnzip();

        //Try to find and parse env.txt
        final Map<String, String> envMap = prepareEnvSet();
        //If bulk upload some kind of data, 
        //you can specify typeid in env.txt,
        //so you can not judge for each file should be what type of data.
        final int typeidByEnv = NumberUtils.toInt(envMap.get("typeid"), -1);
        // iterate over the list of files to upload and create each single resource
        for (FileItem fileItem : m_multiPartFileItems) {
            if ((fileItem != null) && (!fileItem.isFormField())) {
                // determine the new resource name
                String fileName = m_parameterMap.get(
                    fileItem.getFieldName() + I_CmsUploadConstants.UPLOAD_FILENAME_ENCODED_SUFFIX)[0];
                fileName = URLDecoder.decode(fileName, "UTF-8");

                if (filesToUnzip.contains(CmsResource.getName(fileName.replace('\\', '/')))) {
                    // import the zip
                    CmsImportFolder importZip = new CmsImportFolder();
                    InputStream inputStream = null;
                    try {
                        // Uploaded files are already stored on disk, 
                        // and reading streams directly is better than reading as a byte[].
                        inputStream = new BufferedInputStream(fileItem.getInputStream());
                        // put envMap in current context
                        cms.getRequestContext().setAttribute("envMap", envMap);
                        importZip.importZip(inputStream, targetFolder, cms, false);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if(null != inputStream)
                            try {
                                inputStream.close();
                            } catch (Exception e) {//
                            }
                        fileItem.delete();
                        // get the created resource names
                        for (CmsResource importedResource : importZip.getImportedResources()) {
                            m_resourcesCreated.put(
                                importedResource.getStructureId().toString(),
                                importedResource.getName());
                        }
                    }
                } else {
					//For this upload action,the file is already stored in disk,so don't need read it all bytes in memeory.
                    InputStream content = null;
                    try {
                        content = fileItem.getInputStream();
                        //get displayTitle from prepare set, by filename
                        final String titleByEnv = envMap.get(fileItem.getName());
                        // create the resource
                        CmsResource importedResource = createSingleResource(cms, fileName,
                            titleByEnv, typeidByEnv, targetFolder, content);
                        // add the name of the created resource to the list of successful created resources
                        m_resourcesCreated.put(importedResource.getStructureId().toString(), importedResource.getName());
                    } catch (IOException e) {
                        //logicly, this should be never happen
                    } finally {
                        if(null != content)
                            try {
                                content.close();
                            } catch (IOException e) {
                                //ignored
                            }
                        fileItem.delete();
                    }
                }

                if (listener.isCanceled()) {
                    throw listener.getException();
                }
            }
        }
    }

    /**
     * Creates a single resource and returns the new resource.<p>
     *
     * @param cms the CMS context to use
     * @param fileName the name of the resource to create
     * @param titleByEnv predefined displayTitle
     * @param typeidByEnv predefined id of the resourcetype 
     * @param targetFolder the folder to store the new resource
     * @param content the content of the resource to create
     *
     * @return the new resource
     *
     * @throws CmsException if something goes wrong
     * @throws CmsLoaderException if something goes wrong
     * @throws CmsDbSqlException if something goes wrong
     */
    private CmsResource createSingleResource(CmsObject cms, String fileName,
        String titleByEnv, int typeidByEnv, String targetFolder, InputStream content)
    throws CmsException, CmsLoaderException, CmsDbSqlException {

        String newResname = getNewResourceName(cms, fileName, targetFolder);
        CmsResource createdResource = null;

        String title = titleByEnv;
        // determine Title property value to set on new resource
        // If there is no predefined displayTitle, the file name is used
        if(null == title || title.isEmpty())
            title = fileName;
        if (title.lastIndexOf('.') != -1) {
            title = title.substring(0, title.lastIndexOf('.'));
        }

        // fileName really shouldn't contain the full path, but for some reason it does sometimes when the client is
        // running on IE7, so we eliminate anything before and including the last slash or backslash in the title
        // before setting it as a property.

        int backslashIndex = title.lastIndexOf('\\');
        if (backslashIndex != -1) {
            title = title.substring(backslashIndex + 1);
        }

        int slashIndex = title.lastIndexOf('/');
        if (slashIndex != -1) {
            title = title.substring(slashIndex + 1);
        }

        List<CmsProperty> properties = new ArrayList<CmsProperty>(1);
        CmsProperty titleProp = new CmsProperty();
        titleProp.setName(CmsPropertyDefinition.PROPERTY_TITLE);
        if (OpenCms.getWorkplaceManager().isDefaultPropertiesOnStructure()) {
            titleProp.setStructureValue(title);
        } else {
            titleProp.setResourceValue(title);
        }
        properties.add(titleProp);

        if (!cms.existsResource(newResname, CmsResourceFilter.IGNORE_EXPIRATION)) {
            // if the resource does not exist, create it

            try {
                I_CmsResourceType resType = null;
				// Prefer to use predefined TypeId
                if (typeidByEnv != -1) {
                    try {
                        resType = OpenCms.getResourceManager().getResourceType(typeidByEnv);
                    } catch (Exception ex) {
                        resType = null;
                    }
                }
				//If it is in a multilevel subdirectory, determine the data type from the entire directory.
                if (null == resType)
                    resType = OpenCms.getResourceManager().getDefaultTypeForPath(cms, newResname);
                // create the resource
                createdResource = cms.createResourceByStream(newResname, resType, content, properties);
                try {
                    cms.unlockResource(newResname);
                } catch (CmsLockException e) {
                    LOG.info("Couldn't unlock uploaded file", e);
                }
            } catch (CmsSecurityException e) {
                // in case of not enough permissions, try to create a plain text file
                I_CmsResourceType plainType = OpenCms.getResourceManager().getResourceType(
                    CmsResourceTypePlain.getStaticTypeName());
                createdResource = cms.createResourceByStream(newResname, plainType, content, properties);
                cms.unlockResource(newResname);
            } catch (CmsDbSqlException sqlExc) {
                // SQL error, probably the file is too large for the database settings, delete file
                cms.lockResource(newResname);
                cms.deleteResource(newResname, CmsResource.DELETE_PRESERVE_SIBLINGS);
                throw sqlExc;
            } catch (OutOfMemoryError e) {
                // the file is to large try to clear up
                cms.lockResource(newResname);
                cms.deleteResource(newResname, CmsResource.DELETE_PRESERVE_SIBLINGS);
                throw e;
            }

        } else {
            // if the resource already exists, replace it
            CmsResource res = cms.readResource(newResname, CmsResourceFilter.ALL);
            boolean wasLocked = false;
            try {
                if (!cms.getLock(res).isOwnedBy(cms.getRequestContext().getCurrentUser())) {
                    cms.lockResource(res);
                    wasLocked = true;
                }
                CmsFile file = cms.readFile(res);
                byte[] contents = file.getContents();
                try {
                    cms.replaceResourceByStream(newResname,
                        OpenCms.getResourceManager().getResourceType(res.getTypeId()), content, null);
                    createdResource = res;
                } catch (CmsDbSqlException sqlExc) {
                    // SQL error, probably the file is too large for the database settings, restore content
                    file.setContents(contents);
                    cms.writeFile(file);
                    throw sqlExc;
                } catch (OutOfMemoryError e) {
                    // the file is to large try to clear up
                    file.setContents(contents);
                    cms.writeFile(file);
                    throw e;
                }
            } finally {
                if (wasLocked) {
                    cms.unlockResource(res);
                }
            }
        }
        return createdResource;
    }

    /**
     * Returns the stacktrace of the given exception as String.<p>
     *
     * @param e the exception
     *
     * @return the stacktrace as String
     */
    private String formatStackTrace(Throwable e) {
        return StringUtils.join(CmsLog.render(e), '\n');
    }

    /**
     * Generates a JSON object and returns its String representation for the response.<p>
     *
     * @param success <code>true</code> if the upload was successful
     * @param message the message to display
     * @param stacktrace the stack trace in case of an error
     *
     * @return the the response String
     */
    private String generateResponse(Boolean success, String message, String stacktrace) {

        JSONObject result = new JSONObject();
        try {
            result.put(I_CmsUploadConstants.KEY_SUCCESS, success);
            result.put(I_CmsUploadConstants.KEY_MESSAGE, message);
            result.put(I_CmsUploadConstants.KEY_STACKTRACE, stacktrace);
            result.put(I_CmsUploadConstants.KEY_REQUEST_SIZE, getRequest().getContentLength());
            result.put(I_CmsUploadConstants.KEY_UPLOADED_FILES, new JSONArray(m_resourcesCreated.keySet()));
            result.put(I_CmsUploadConstants.KEY_UPLOADED_FILE_NAMES, new JSONArray(m_resourcesCreated.values()));
            if (m_uploadHook != null) {
                result.put(I_CmsUploadConstants.KEY_UPLOAD_HOOK, m_uploadHook);
            }
        } catch (JSONException e) {
            LOG.error(m_bundle.key(org.opencms.ade.upload.Messages.ERR_UPLOAD_JSON_0), e);
        }
        return result.toString();
    }

    /**
     * Returns the error message if an error occurred during the creation of resources in the VFS.<p>
     *
     * @return the error message
     */
    private String getCreationErrorMessage() {

        String message = new String();
        if (!m_resourcesCreated.isEmpty()) {
            // some resources have been created, tell the user which resources were created successfully
            StringBuffer buf = new StringBuffer(64);
            for (String name : m_resourcesCreated.values()) {
                buf.append("<br />");
                buf.append(name);
            }
            message = m_bundle.key(org.opencms.ade.upload.Messages.ERR_UPLOAD_CREATING_1, buf.toString());
        } else {
            // no resources have been created on the VFS
            message = m_bundle.key(org.opencms.ade.upload.Messages.ERR_UPLOAD_CREATING_0);
        }
        return message;
    }

    /**
     * Gets the list of file names that should be unziped.<p>
     *
     * @return the list of file names that should be unziped
     *
     * @throws UnsupportedEncodingException if something goes wrong
     */
    private List<String> getFilesToUnzip() throws UnsupportedEncodingException {

        if (m_parameterMap.get(I_CmsUploadConstants.UPLOAD_UNZIP_FILES_FIELD_NAME) != null) {
            String[] filesToUnzip = m_parameterMap.get(I_CmsUploadConstants.UPLOAD_UNZIP_FILES_FIELD_NAME);
            if (filesToUnzip != null) {
                List<String> result = new ArrayList<String>();
                for (String filename : filesToUnzip) {
                    result.add(URLDecoder.decode(filename, "UTF-8"));
                }
                return result;
            }
        }
        return Collections.emptyList();
    }

    /**
     * Returns the target folder for the new resource,
     * if the given folder does not exist root folder
     * of the current site is returned.<p>
     *
     * @param cms the CMS context to use
     *
     * @return the target folder for the new resource
     *
     * @throws CmsException if something goes wrong
     */
    private String getTargetFolder(CmsObject cms) throws CmsException {

        // get the target folder on the vfs
        CmsResource target = cms.readResource("/", CmsResourceFilter.IGNORE_EXPIRATION);
        if (m_parameterMap.get(I_CmsUploadConstants.UPLOAD_TARGET_FOLDER_FIELD_NAME) != null) {
            String targetFolder = m_parameterMap.get(I_CmsUploadConstants.UPLOAD_TARGET_FOLDER_FIELD_NAME)[0];
            if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(targetFolder)) {
                if (cms.existsResource(targetFolder)) {
                    CmsResource tmpTarget = cms.readResource(targetFolder, CmsResourceFilter.IGNORE_EXPIRATION);
                    if (tmpTarget.isFolder()) {
                        target = tmpTarget;
                    }
                }
            }
        }
        String targetFolder = cms.getRequestContext().removeSiteRoot(target.getRootPath());
        if (!targetFolder.endsWith("/")) {
            // add folder separator to currentFolder
            targetFolder += "/";
        }
        return targetFolder;
    }

    /**
     * Parses the request.<p>
     *
     * Stores the file items and the request parameters in a local variable if present.<p>
     *
     * @param listener the upload listener
     *
     * @throws Exception if anything goes wrong
     */
    private void parseRequest(CmsUploadListener listener) throws Exception {

        // check if the request is a multipart request
        if (!ServletFileUpload.isMultipartContent(getRequest())) {
            // no multipart request: Abort the upload
            throw new CmsUploadException(m_bundle.key(org.opencms.ade.upload.Messages.ERR_UPLOAD_NO_MULTIPART_0));
        }

        // this was indeed a multipart form request, read the files
        m_multiPartFileItems = readMultipartFileItems(listener);

        // check if there were any multipart file items in the request
        if ((m_multiPartFileItems == null) || m_multiPartFileItems.isEmpty()) {
            // no file items found stop process
            throw new CmsUploadException(m_bundle.key(org.opencms.ade.upload.Messages.ERR_UPLOAD_NO_FILEITEMS_0));
        }

        // there are file items in the request, get the request parameters
        m_parameterMap = CmsRequestUtil.readParameterMapFromMultiPart(
            getCmsObject().getRequestContext().getEncoding(),
            m_multiPartFileItems);

        listener.setFinished(true);
    }

    /**
     * Parses a request of the form <code>multipart/form-data</code>.<p>
     *
     * The result list will contain items of type <code>{@link FileItem}</code>.
     * If the request has no file items, then <code>null</code> is returned.<p>
     *
     * @param listener the upload listener
     *
     * @return the list of <code>{@link FileItem}</code> extracted from the multipart request,
     *      or <code>null</code> if the request has no file items
     *
     * @throws Exception if anything goes wrong
     */
    private List<FileItem> readMultipartFileItems(CmsUploadListener listener) throws Exception {

        DiskFileItemFactory factory = new DiskFileItemFactory();
        // maximum size that will be stored in memory
        factory.setSizeThreshold(4096);
        // the location for saving data that is larger than the threshold
        File temp = new File(OpenCms.getSystemInfo().getPackagesRfsPath());
        if (temp.exists() || temp.mkdirs()) {
            // make sure the folder exists
            factory.setRepository(temp);
        }

        // create a file upload servlet
        ServletFileUpload fu = new ServletFileUpload(factory);
        // set the listener
        fu.setProgressListener(listener);
        // set encoding to correctly handle special chars (e.g. in filenames)
        fu.setHeaderEncoding(getRequest().getCharacterEncoding());
        // set the maximum size for a single file (value is in bytes)
        long maxFileSizeBytes = OpenCms.getWorkplaceManager().getFileBytesMaxUploadSize(getCmsObject());
        if (maxFileSizeBytes > 0) {
            fu.setFileSizeMax(maxFileSizeBytes);
        }

        // try to parse the request
        try {
            return CmsCollectionsGenericWrapper.list(fu.parseRequest(getRequest()));
        } catch (SizeLimitExceededException e) {
            // request size is larger than maximum allowed request size, throw an error
            Integer actualSize = new Integer((int)(e.getActualSize() / 1024));
            Integer maxSize = new Integer((int)(e.getPermittedSize() / 1024));
            throw new CmsUploadException(
                m_bundle.key(org.opencms.ade.upload.Messages.ERR_UPLOAD_REQUEST_SIZE_LIMIT_2, actualSize, maxSize),
                e);
        } catch (FileSizeLimitExceededException e) {
            // file size is larger than maximum allowed file size, throw an error
            Integer actualSize = new Integer((int)(e.getActualSize() / 1024));
            Integer maxSize = new Integer((int)(e.getPermittedSize() / 1024));
            throw new CmsUploadException(
                m_bundle.key(
                    org.opencms.ade.upload.Messages.ERR_UPLOAD_FILE_SIZE_LIMIT_3,
                    actualSize,
                    e.getFileName(),
                    maxSize),
                e);
        }
    }

    /**
     * Remove the listener active in this session.
     *
     * @param listenerId the id of the listener to remove
     */
    private void removeListener(CmsUUID listenerId) {

        getRequest().getSession().removeAttribute(SESSION_ATTRIBUTE_LISTENER_ID);
        m_listeners.remove(listenerId);
    }
}
