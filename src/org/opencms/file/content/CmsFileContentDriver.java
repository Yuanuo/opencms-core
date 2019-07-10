
package org.opencms.file.content;

import org.opencms.configuration.CmsConfigurationManager;
import org.opencms.db.CmsDbContext;
import org.opencms.db.CmsDriverManager;
import org.opencms.util.CmsByteArrayInputStream;
import org.opencms.util.CmsFileUtil;
import org.opencms.util.CmsUUID;

import java.io.InputStream;
import java.util.List;

/**
 * This fileContentDriver still use the main db to storage, so it's very simple
 */
public class CmsFileContentDriver extends CmsFileContentDriverBase {

    /**
     * @see org.opencms.db.I_CmsDriver#init(org.opencms.db.CmsDbContext, org.opencms.configuration.CmsConfigurationManager, java.util.List, org.opencms.db.CmsDriverManager)
     */
    @Override
    public void init(
        CmsDbContext dbc,
        CmsConfigurationManager configurationManager,
        List<String> successiveDrivers,
        CmsDriverManager driverManager) {

        //do nothing
    }

    /**
     * @see org.opencms.file.content.I_CmsFileContentDriver#createContent(org.opencms.db.CmsDbContext, CmsUUID, java.io.InputStream)
     */
    public byte[] createContent(CmsDbContext dbc, CmsUUID resourceId, InputStream content) {

        return CmsFileUtil.toBytesAgain(content);
    }

    /**
     * 
     * @see org.opencms.file.content.I_CmsFileContentDriver#isReadable(byte[])
     */
    public boolean isReadable(byte[] content) {

        return true;
    }

    /**
     * @see org.opencms.file.content.I_CmsFileContentDriver#readContent(org.opencms.db.CmsDbContext, CmsUUID, byte[])
     */
    public InputStream readContent(CmsDbContext dbc, CmsUUID resourceId, byte[] content) {

        return null == content ? CmsFileUtil.EMPTY_STREAM : new CmsByteArrayInputStream(content);
    }

    /**
     * @see org.opencms.file.content.I_CmsFileContentDriver#writeContent(org.opencms.db.CmsDbContext, CmsUUID, java.io.InputStream)
     */
    public byte[] writeContent(CmsDbContext dbc, CmsUUID resourceId, InputStream content) {

        return CmsFileUtil.toBytesAgain(content);
    }

    /**
     * @see org.opencms.file.content.I_CmsFileContentDriver#removeContent(org.opencms.db.CmsDbContext, boolean, CmsUUID)
     */
    public void removeContent(CmsDbContext dbc, boolean onlineProject, CmsUUID resourceId) {

        // do nothing, always deleted from main db
    }

    /**
     * @see org.opencms.file.content.I_CmsFileContentDriver#createOnlineContent(org.opencms.db.CmsDbContext, org.opencms.util.CmsUUID, byte[], int, boolean)
     */
    public byte[] createOnlineContent(
        CmsDbContext dbc,
        CmsUUID resourceId,
        byte[] content,
        int publishTag,
        boolean onlineTag) {

        return content;
    }

    /**
     * @see org.opencms.file.content.I_CmsFileContentDriver#removeHistoryContent(org.opencms.db.CmsDbContext, org.opencms.util.CmsUUID, int)
     */
    public void removeHistoryContent(CmsDbContext dbc, CmsUUID resourceId, int minVersionToKeep) {

        // do nothing, always deleted from main db
    }
}
