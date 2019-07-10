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
 * For further information about Alkacon Software GmbH & Co. KG, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.db;

import org.opencms.file.CmsFile;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsProperty;
import org.opencms.file.CmsPropertyDefinition;
import org.opencms.file.CmsResource;
import org.opencms.file.CmsResourceFilter;
import org.opencms.file.CmsVfsException;
import org.opencms.file.types.CmsResourceTypeFolder;
import org.opencms.file.types.CmsResourceTypePlain;
import org.opencms.file.types.I_CmsResourceType;
import org.opencms.main.CmsException;
import org.opencms.main.OpenCms;
import org.opencms.security.CmsSecurityException;
import org.opencms.util.CmsBoundedInputStream;
import org.opencms.util.CmsFileUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

/**
 * Allows to import resources from the filesystem or a ZIP file into the OpenCms VFS.<p>
 *
 * @since 6.0.0
 */
public class CmsImportFolder {

    /** The OpenCms context object that provides the permissions. */
    private CmsObject m_cms;

    /** The names of resources that have been created or replaced during the import. */
    private List<CmsResource> m_importedResources = new ArrayList<CmsResource>();

    /** The name of the import folder to load resources from. */
    private String m_importFolderName;

    /** The import path in the OpenCms VFS. */
    private String m_importPath;

    /** The resource (folder or ZIP file) to import from in the real file system. */
    private File m_importResource;

    /** Will be true if the import resource is a valid ZIP file. */
    private boolean m_validZipFile;

    /** The import resource ZIP stream to load resources from. */
    private ZipInputStream m_zipStreamIn;

    /**
     * Default Constructor.<p>
     */
    public CmsImportFolder() {

        // noop
    }

    /**
     * Constructor for a new CmsImportFolder that will read from a ZIP file.<p>
     *
     * @param content the zip file to import
     * @param importPath the path to the OpenCms VFS to import to
     * @param cms a OpenCms context to provide the permissions
     * @param noSubFolder if <code>true</code> no sub folders will be created, if <code>false</code> the content of the
     * zip file is created 1:1 inclusive sub folders
     *
     * @throws CmsException if something goes wrong
     */
    public CmsImportFolder(InputStream content, String importPath, CmsObject cms, boolean noSubFolder)
    throws CmsException {

        importZip(content, importPath, cms, noSubFolder);
    }

    /**
     * Constructor for a new CmsImportFolder that will read from the real file system.<p>
     *
     * @param importFolderName the folder to import
     * @param importPath the path to the OpenCms VFS to import to
     * @param cms a OpenCms context to provide the permissions
     * @throws CmsException if something goes wrong
     */
    public CmsImportFolder(String importFolderName, String importPath, CmsObject cms)
    throws CmsException {

        importFolder(importFolderName, importPath, cms);
    }

    /**
     * Returns the list of imported resources.<p>
     *
     * @return the list of imported resources
     */
    public List<CmsResource> getImportedResources() {

        return m_importedResources;
    }

    /**
     * Import that will read from the real file system.<p>
     *
     * @param importFolderName the folder to import
     * @param importPath the path to the OpenCms VFS to import to
     * @param cms a OpenCms context to provide the permissions
     * @throws CmsException if something goes wrong
     */
    public void importFolder(String importFolderName, String importPath, CmsObject cms) throws CmsException {

        try {
            m_importedResources = new ArrayList<CmsResource>();
            m_importFolderName = importFolderName;
            m_importPath = importPath;
            m_cms = cms;
            // open the import resource
            getImportResource();
            // first lock the destination path
            m_cms.lockResource(m_importPath);
            // import the resources
            if (m_zipStreamIn == null) {
                importResources(m_importResource, m_importPath);
            } else {
                importZipResource(m_zipStreamIn, m_importPath, false);
            }
            // all is done, unlock the resources
            m_cms.unlockResource(m_importPath);
        } catch (Exception e) {
            throw new CmsVfsException(
                Messages.get().container(Messages.ERR_IMPORT_FOLDER_2, importFolderName, importPath),
                e);
        }

    }

    /**
     * Import that will read from a ZIP file.<p>
     *
     * @param content the zip file to import
     * @param importPath the path to the OpenCms VFS to import to
     * @param cms a OpenCms context to provide the permissions
     * @param noSubFolder if <code>true</code> no sub folders will be created, if <code>false</code> the content of the
     * zip file is created 1:1 inclusive sub folders
     *
     * @throws CmsException if something goes wrong
     */
    public void importZip(InputStream content, String importPath, CmsObject cms, boolean noSubFolder) throws CmsException {

        m_importPath = importPath;
        m_cms = cms;
        try {
            // open the import resource
            m_zipStreamIn = new ZipInputStream(content);
            m_cms.readFolder(importPath, CmsResourceFilter.IGNORE_EXPIRATION);
            // import the resources
            importZipResource(m_zipStreamIn, m_importPath, noSubFolder);
        } catch (Exception e) {
            throw new CmsVfsException(Messages.get().container(Messages.ERR_IMPORT_FOLDER_1, importPath), e);
        }

    }

    /**
     * Returns true if a valid ZIP file was imported.<p>
     *
     * @return true if a valid ZIP file was imported
     */
    public boolean isValidZipFile() {

        return m_validZipFile;
    }

    /**
     * Stores the import resource in an Object member variable.<p>
     * @throws CmsVfsException if the file to import is no valid zipfile
     */
    private void getImportResource() throws CmsVfsException {

        // get the import resource
        m_importResource = new File(m_importFolderName);
        // check if this is a folder or a ZIP file
        if (m_importResource.isFile()) {
            try {
                m_zipStreamIn = new ZipInputStream(new FileInputStream(m_importResource));
            } catch (IOException e) {
                // if file but no ZIP file throw an exception
                throw new CmsVfsException(
                    Messages.get().container(Messages.ERR_NO_ZIPFILE_1, m_importResource.getName()),
                    e);
            }
        }
    }

    /**
     * Imports the resources from the folder in the real file system to the OpenCms VFS.<p>
     *
     * @param folder the folder to import from
     * @param importPath the OpenCms VFS import path to import to
     * @throws Exception if something goes wrong during file IO
     */
    private void importResources(File folder, String importPath) throws Exception {

        String[] diskFiles = folder.list();
        File currentFile;

        I_CmsResourceType plainType = OpenCms.getResourceManager().getResourceType(
            CmsResourceTypePlain.getStaticTypeName());
        for (int i = 0; i < diskFiles.length; i++) {
            currentFile = new File(folder, diskFiles[i]);

            if (currentFile.isDirectory()) {
                // create directory in cms
                m_importedResources.add(
                    m_cms.createFolder(importPath + currentFile.getName()));
                importResources(currentFile, importPath + currentFile.getName() + "/");
            } else {
                // import file into cms
                I_CmsResourceType type = OpenCms.getResourceManager().getDefaultTypeForName(currentFile.getName());
                InputStream content = new FileInputStream(currentFile);
                // create the file
                try {
                    m_importedResources.add(
                        m_cms.createResourceByStream(importPath + currentFile.getName(), type, content, null));
                } catch (CmsSecurityException e) {
                    // in case of not enough permissions, try to create a plain text file
                    m_importedResources.add(
                        m_cms.createResourceByStream(importPath + currentFile.getName(), plainType, content, null));
                } finally {
                    content.close();
                    content = null;
                }
            }
        }
    }

    /**
     * Imports the resources from a ZIP file in the real file system to the OpenCms VFS.<p>
     *
     * @param zipStreamIn the input Stream
     * @param importPath the path in the vfs
     * @param noSubFolder if <code>true</code> no sub folders will be created, if <code>false</code> the content of the
     * zip file is created 1:1 inclusive sub folders
     *
     * @throws Exception if something goes wrong during file IO
     */
    private void importZipResource(ZipInputStream zipStreamIn, String importPath, boolean noSubFolder)
    throws Exception {

        // HACK: this method looks very crude, it should be re-written sometime...

        boolean isFolder = false;
        int j, r, stop, size;
        int entries = 0;
        boolean resourceExists;
        
        
        // determine if envMap support
        final Map<String, String> envMap = (Map<String, String>)m_cms.getRequestContext().getAttribute("envMap");
        //predefined typeid
        final int typeIdByEnv = null == envMap ? -1 : NumberUtils.toInt(envMap.get("typeid"), -1);
        //function for get displayTitle from envMap
        final Function<String, String> funcTitlesGetter = new Function<String, String>() {
            public String apply(String namePath) {
                if(null==envMap)
                    return null;
                //determine file or path by extension
                String ext = FilenameUtils.getExtension(namePath);
                if(null != ext && !ext.isEmpty()) { //file
                    String name = FilenameUtils.getName(namePath);
                    return envMap.get(name);
                } else {//path
                    //for path,this support get displayTitle from parent path
                    //example,for "a\b\c",if no title defined for "c",will try to get title from "a\b"
                    String path = "";
                    String parent = namePath;
                    while(true) {
                        parent = parent.replaceAll("/$", "");
                        path = FilenameUtils.getName(parent) + (path.isEmpty() ? "" : ("/" + path));
                        String title = envMap.get(path);
                        if(null != title)
                            return title;
                        parent = FilenameUtils.getPathNoEndSeparator(parent);
                        if(!parent.contains("/"))
                            break;
                    }
                }
                return null;
            }
        };
        //Avoid creating directories repeatedly
        final Set<String> pathsChecked = new HashSet<>();

        
        //Only need to initialize it once.
        final I_CmsResourceType folderType = OpenCms.getResourceManager()
                .getResourceType(CmsResourceTypeFolder.RESOURCE_TYPE_NAME);
        final I_CmsResourceType plainType = OpenCms.getResourceManager()
                .getResourceType(CmsResourceTypePlain.getStaticTypeName());
        I_CmsResourceType resType = null;
        // Prefer to use predefined TypeId
        if (typeIdByEnv != -1) {
            try {
                resType = OpenCms.getResourceManager().getResourceType(typeIdByEnv);
            } catch (Exception ex) {
                resType = null;
            }
        }
        
        while (true) {
            // handle the single entries ...
            j = 0;
            stop = 0;
            // open the entry ...
            ZipEntry entry = zipStreamIn.getNextEntry();
            if (entry == null) {
                break;
            }
            entries++; // count number of entries in zip
            String actImportPath = importPath;
            String filename = m_cms.getRequestContext().getFileTranslator().translateResource(entry.getName());
            // separate path in directories an file name ...
            StringTokenizer st = new StringTokenizer(filename, "/\\");
            int count = st.countTokens();
            String[] path = new String[count];

            if (filename.endsWith("\\") || filename.endsWith("/")) {
                isFolder = true; // last entry is a folder
            } else {
                isFolder = false; // last entry is a file
            }
            while (st.hasMoreTokens()) {
                // store the files and folder names in array ...
                path[j] = st.nextToken();
                j++;
            }
            stop = isFolder ? path.length : (path.length - 1);

            if (noSubFolder) {
                stop = 0;
            }
            // now write the folders ...
            for (r = 0; r < stop; r++) {
                actImportPath += path[r];
                //only create folder resource once
                if(!pathsChecked.contains(actImportPath)) {
                    try {
                        //Used if the display title is predefined for the directory
                        String title = funcTitlesGetter.apply(actImportPath);
                        List<CmsProperty> properties = new ArrayList<CmsProperty>(1);
                        //set property_title
                        if(StringUtils.isNotBlank(title)) {
                            CmsProperty titleProp = new CmsProperty();
                            titleProp.setName(CmsPropertyDefinition.PROPERTY_TITLE);
                            if (OpenCms.getWorkplaceManager().isDefaultPropertiesOnStructure()) {
                                titleProp.setStructureValue(title);
                            } else {
                                titleProp.setResourceValue(title);
                            }
                            properties.add(titleProp);
                        }
                        
                        m_importedResources.add(m_cms.createFolder(actImportPath, properties));
                    } catch (CmsException e) {
                        // of course some folders did already exist!
                    }
                    pathsChecked.add(actImportPath);
                }
                actImportPath += "/";
            }
            if (!isFolder) {
                //Used if the display title is predefined for the file
                String title = funcTitlesGetter.apply(filename);
                List<CmsProperty> properties = new ArrayList<CmsProperty>(1);
                //Used default title(filename) as property_title when no env title set
                if(StringUtils.isBlank(title))
                    title = FilenameUtils.getName(filename);//
                //set property_title
                if(StringUtils.isNotBlank(title)) {
                    CmsProperty titleProp = new CmsProperty();
                    titleProp.setName(CmsPropertyDefinition.PROPERTY_TITLE);
                    if (OpenCms.getWorkplaceManager().isDefaultPropertiesOnStructure()) {
                        titleProp.setStructureValue(title);
                    } else {
                        titleProp.setResourceValue(title);
                    }
                    properties.add(titleProp);
                }
                
                // import file into cms
                // I_CmsResourceType type = OpenCms.getResourceManager().getDefaultTypeForName(path[path.length - 1]);
                size = new Long(entry.getSize()).intValue();
                CmsBoundedInputStream content = null;
                if (size == -1) {
                    content = new CmsBoundedInputStream(zipStreamIn);
                } else {
                    content = new CmsBoundedInputStream(zipStreamIn, size);
                }
                filename = actImportPath + path[path.length - 1];

                try {
                    m_cms.lockResource(filename);
                    m_cms.readResource(filename);
                    resourceExists = true;
                } catch (CmsException e) {
                    resourceExists = false;
                }

                if (resourceExists) {
                    CmsResource res = m_cms.readResource(filename, CmsResourceFilter.ALL);
                    CmsFile file = m_cms.readFile(res);
                    //byte[] contents = file.getContents();
                    try {
                        m_cms.replaceResourceByStream(filename, 
                            OpenCms.getResourceManager().getResourceType(res.getTypeId()), 
                            content, new ArrayList<>(0));
                        m_importedResources.add(res);
                    } catch (CmsSecurityException e) {
                        // in case of not enough permissions, try to create a plain text file
                        m_cms.replaceResourceByStream(filename, plainType, content, new ArrayList<>(0));
                        m_importedResources.add(res);
                    } catch (CmsDbSqlException sqlExc) {
                        // SQL error, probably the file is too large for the database settings, restore content
                        file.setContents(file.getContents());
                        m_cms.writeFile(file);
                        throw sqlExc;
                    }
                } else {
                    String newResName = actImportPath + path[path.length - 1];
					//If it is in a multilevel subdirectory, determine the data type from the entire directory.
                    if (null == resType)
                        resType = OpenCms.getResourceManager().getDefaultTypeForPath(m_cms, newResName);
                    try {
                        m_importedResources.add(m_cms.createResourceByStream(newResName, resType, content, properties));
                    } catch (CmsSecurityException e) {
                        // in case of not enough permissions, try to create a plain text file
                        m_importedResources.add(m_cms.createResourceByStream(newResName, plainType, content, properties));
                    } catch (CmsDbSqlException sqlExc) {
                        // SQL error, probably the file is too large for the database settings, delete file
                        m_cms.lockResource(newResName);
                        m_cms.deleteResource(newResName, CmsResource.DELETE_PRESERVE_SIBLINGS);
                        throw sqlExc;
                    }
                }
            }

            // close the entry ...
            zipStreamIn.closeEntry();
        }
        zipStreamIn.close();
        if (entries > 0) {
            // at least one entry, got a valid zip file ...
            m_validZipFile = true;
        }
    }
}
