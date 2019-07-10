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

package org.opencms.file.content.refs;

import org.opencms.configuration.CmsConfigurationManager;
import org.opencms.configuration.CmsParameterConfiguration;
import org.opencms.db.CmsDbContext;
import org.opencms.db.CmsDriverManager;
import org.opencms.file.CmsDataAccessException;
import org.opencms.file.CmsVfsResourceNotFoundException;
import org.opencms.file.content.CmsFileContentDriverBase;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.util.CmsFileUtil;
import org.opencms.util.CmsStringUtil;
import org.opencms.util.CmsUUID;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import org.apache.commons.logging.Log;

/**
 * store data to disk
 */
public class CmsFileContentDriver extends CmsFileContentDriverBase {

    /** The log object for this class. */
    private static final Log LOG = CmsLog.getLog(CmsFileContentDriver.class);

    /**
     * the local disk storage directory
     */
    Path storagePath;

    /**
     * the marker used to store in main db(as part of file content by driver processed).
     * it used for mark this content is wrapped by which driver,
     * so when read content, the driver's {@link #isReadable(byte[])} will determine the content using marker.
     */
    String driverMarker;

    /**
     * @see org.opencms.db.I_CmsDriver#init(org.opencms.db.CmsDbContext, org.opencms.configuration.CmsConfigurationManager, java.util.List, org.opencms.db.CmsDriverManager)
     */
    public void init(
        CmsDbContext dbc,
        CmsConfigurationManager configurationManager,
        List<String> successiveDrivers,
        CmsDriverManager driverManager) {

        final CmsParameterConfiguration configuration = configurationManager.getConfiguration();

        String configPath = getNotNullParamValue(
            configuration,
            "refs.storage",
            OpenCms.getSystemInfo().getAbsoluteRfsPathRelativeToWebApplication("../refs"));
        storagePath = Paths.get(configPath);
        storagePath.toFile().mkdirs();
        //
        this.driverMarker = getNotNullParamValue(configuration, "refs.marker", "REFS:");
    }

    /**
     * @see org.opencms.file.content.I_CmsFileContentDriver#createContent(org.opencms.db.CmsDbContext, org.opencms.util.CmsUUID, java.io.InputStream)
     */
    public byte[] createContent(CmsDbContext dbc, CmsUUID resourceId, InputStream content)
    throws CmsDataAccessException {

        String shortPath = internalBuildShortPath(false, resourceId.toString());
        Path path = storagePath.resolve(shortPath);
        try {
            if (null == content)
                content = CmsFileUtil.EMPTY_STREAM;
            Files.createDirectories(path.getParent());
            Files.copy(content, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new CmsDataAccessException(null, e);
        }
        return (driverMarker + shortPath).getBytes();
    }

    /**
     * @see org.opencms.file.content.I_CmsFileContentDriver#createOnlineContent(org.opencms.db.CmsDbContext, org.opencms.util.CmsUUID, byte[], int, boolean)
     */
    public byte[] createOnlineContent(
        CmsDbContext dbc,
        CmsUUID resourceId,
        byte[] content,
        int publishTag,
        boolean onlineTag)
    throws CmsDataAccessException {

        Path contentFromLocalPath = null;
        String shortPath = internalDetectShortPath(content);
        if (null != shortPath)
            contentFromLocalPath = storagePath.resolve(shortPath);

        try {
            Path onlineResPath = storagePath.resolve(internalBuildShortPath(true, resourceId.toString(), publishTag));
            Files.createDirectories(onlineResPath.getParent());

            if (null != contentFromLocalPath) {
                Files.copy(contentFromLocalPath, onlineResPath, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.write(onlineResPath, content, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            }
            shortPath = storagePath.relativize(onlineResPath).toString();
        } catch (IOException e) {
            throw new CmsDataAccessException(null, e);
        }
        return (driverMarker + shortPath).getBytes();
    }

    /**
     * @see org.opencms.file.content.I_CmsFileContentDriver#isReadable(byte[])
     */
    public boolean isReadable(byte[] content) {

        if (null != content && content.length > driverMarker.length()) {
            String temp = new String(content, 0, driverMarker.length());
            if (temp.equals(driverMarker) || temp.startsWith(driverMarker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @see org.opencms.file.content.I_CmsFileContentDriver#readContent(org.opencms.db.CmsDbContext, org.opencms.util.CmsUUID, byte[])
     */
    public InputStream readContent(CmsDbContext dbc, CmsUUID resourceId, byte[] content) throws CmsDataAccessException {

        // try to parse content as local path
        String shortPath = internalDetectShortPath(content);

        if (null != shortPath) {
            Path path = storagePath.resolve(shortPath);
            LOG.info("readContent[" + resourceId + "] AT:" + path.toString());
            try {
                return new FileInputStream(path.toFile());
            } catch (FileNotFoundException e) {
                LOG.error("readContent[" + resourceId + "] AT:" + path.toString(), e);
//                throw new CmsVfsResourceNotFoundException(null, e);
                return CmsFileUtil.EMPTY_STREAM;
            }
        }
        // unreadable content
        throw new CmsDataAccessException(null, new UnsupportedOperationException());
    }

    /**
     * @see org.opencms.file.content.I_CmsFileContentDriver#writeContent(org.opencms.db.CmsDbContext, org.opencms.util.CmsUUID, java.io.InputStream)
     */
    public byte[] writeContent(CmsDbContext dbc, CmsUUID resourceId, InputStream content)
    throws CmsDataAccessException {

        // just create and overwrite
        return createContent(dbc, resourceId, content);
    }

    /**
     * @see org.opencms.file.content.I_CmsFileContentDriver#removeContent(org.opencms.db.CmsDbContext, boolean, org.opencms.util.CmsUUID)
     */
    public void removeContent(CmsDbContext dbc, boolean onlineProject, CmsUUID resourceId) {

        String shortPath = internalBuildShortPath(onlineProject, resourceId.toString());
        Path path = storagePath.resolve(shortPath);
        // FIXME for online project, don't know which one is the online version, so cannot do remove.
        // OR if OFFLINE project,, remove all versions???
        if (!onlineProject) {
            //removeHistoryContent(dbc, resourceId, Integer.MAX_VALUE);
        }
        internalDeletePath(path);
        LOG.info("removeContent:" + path.toString());
    }

    /**
     * @see org.opencms.file.content.I_CmsFileContentDriver#removeHistoryContent(org.opencms.db.CmsDbContext, org.opencms.util.CmsUUID, int)
     */
    public void removeHistoryContent(CmsDbContext dbc, CmsUUID resourceId, int minVersionToKeep) {

        final String resId = resourceId.toString();
        String shortPath = internalBuildShortPath(true, resId);
        Path path = storagePath.resolve(shortPath);

        //scan versions and do action
        if (null != path && Files.exists(path.getParent()))
            try {
                Files.walkFileTree(path.getParent(), null, 0, new SimpleFileVisitor<Path>() {

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {

                        String name = file.getFileName().toString();
                        if (name.startsWith(resId)) {
                            String[] arr = name.split("\\.", 2);
                            int version = arr.length > 1 ? Integer.valueOf(arr[1]).intValue() : 1;
                            if (version < minVersionToKeep) {
                                Path delPath = storagePath.resolve(internalBuildShortPath(true, arr[0], version));
                                internalDeletePath(delPath);
                                LOG.info("removeHistoryContent:" + delPath.toString());
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e2) {
                //nothing will happen
            }
    }

    private void internalDeletePath(Path path) {

        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            e.printStackTrace();

            Path tempPath = path.getParent().resolve(path.getFileName() + ".del");
            try {
                //rename it,then next read will get nothing
                Files.move(path, tempPath);
            } catch (Exception e1) {
                //ignore
            } finally {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException e1) {
                    //ignore
                }
            }
        }
    }

    /**
     * build shortpath by resource id
     * @param onlineProject true if online, false offline
     * @param resourceId the id of resource
     * @return the shortpath under storagePath
     */
    String internalBuildShortPath(boolean onlineProject, String resourceId) {

        return internalBuildShortPath(onlineProject, resourceId, -1);
    }

    String internalBuildShortPath(boolean onlineProject, String resourceId, int version) {

        String name = version == -1 ? resourceId : (resourceId + "." + version);
        return CmsStringUtil.joinPaths(
            onlineProject ? "ONLINE" : "OFFLINE",
            resourceId.substring(0, 2),
            resourceId.substring(2, 3),
            name);
    }

    /**
     * determine the local path from given content.
     * @param content
     * @return
     */
    String internalDetectShortPath(byte[] content) {

        if (null != content && content.length < 256) {
            String temp = new String(content);
            if (temp.startsWith(driverMarker)) {
                return temp.substring(driverMarker.length());
            }
        }
        return null;
    }
}
