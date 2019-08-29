<%@tag import="org.opencms.file.types.CmsResourceTypeFolder"%>
<%@tag import="org.opencms.main.CmsException"%>
<%@tag import="java.util.StringTokenizer"%>
<%@tag import="org.opencms.file.CmsFile"%>
<%@tag import="org.opencms.db.CmsDbSqlException"%>
<%@tag import="org.opencms.security.CmsSecurityException"%>
<%@tag import="org.opencms.lock.CmsLockException"%>
<%@tag import="org.opencms.file.CmsResourceFilter"%>
<%@tag import="org.opencms.file.types.CmsResourceTypePlain"%>
<%@tag import="org.opencms.file.types.I_CmsResourceType"%>
<%@tag import="org.opencms.main.OpenCms"%>
<%@tag import="org.opencms.file.CmsPropertyDefinition"%>
<%@tag import="java.util.ArrayList"%>
<%@tag import="org.opencms.file.CmsProperty"%>
<%@tag import="java.util.List"%>
<%@tag import="org.apache.commons.io.FilenameUtils"%>
<%@tag import="org.apache.commons.lang3.StringUtils"%>
<%@tag import="org.opencms.ade.upload.CmsUploadBean"%>
<%@tag import="org.opencms.file.CmsObject"%>
<%@tag import="org.opencms.file.CmsResource"%>
<%@tag language="java" pageEncoding="UTF-8"
    display-name="ExResources"
    description="Extended Resources"%>

<%!
public static void createFolder(CmsObject cms, String folder, String title) {

	if (StringUtils.isBlank(title)) {
		title = FilenameUtils.getName(folder);
	}
	
    // separate path in directories an file name ...
    StringTokenizer st = new StringTokenizer(folder, "/\\");
    StringBuilder buf = new StringBuilder();
    while (st.hasMoreTokens()) {
    	String nextPart = st.nextToken().trim();
    	if (nextPart.isEmpty() || "/".equals(nextPart) || "\\".equals(nextPart))
    		continue;
    	buf.append("/").append(nextPart);
        // now write the folders ...
        try {
        	List<CmsProperty> properties = null;
        	// only for last one
        	if (!st.hasMoreTokens()) {
        	    properties = new ArrayList<CmsProperty>(1);
        	    CmsProperty titleProp = new CmsProperty();
        	    titleProp.setName(CmsPropertyDefinition.PROPERTY_TITLE);
        	    if (OpenCms.getWorkplaceManager().isDefaultPropertiesOnStructure()) {
        	        titleProp.setStructureValue(title);
        	    } else {
        	        titleProp.setResourceValue(title);
        	    }
        	    properties.add(titleProp);
        	}
            CmsResource createdFolder = cms.createResource(buf.toString(), 
            		CmsResourceTypeFolder.RESOURCE_TYPE_ID, new byte[0], properties);
        } catch (CmsException e) {
            // of course some folders did already exist!
        }
    }
} 

public static CmsResource createResource(CmsObject cms, String folder, String fileName, 
		String title, byte[] contentBytes, Object contentType) throws Exception{

	if (StringUtils.isBlank(title)) {
		title = FilenameUtils.getBaseName(fileName);
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
    
    I_CmsResourceType resType;
    if (contentType instanceof I_CmsResourceType) {
    	resType = (I_CmsResourceType)contentType;
    }
    else if (contentType instanceof String) {
    	resType = OpenCms.getResourceManager().getResourceType((String)contentType);
    }
    else if (contentType instanceof Number) {
    	resType = OpenCms.getResourceManager().getResourceType(((Number)contentType).intValue());
    }
    else {
    	resType = OpenCms.getResourceManager().getResourceType(CmsResourceTypePlain.getStaticTypeName());
    }

	CmsResource createdResource = null;
	String newResname = CmsUploadBean.getNewResourceName(cms, fileName, folder);

    if (!cms.existsResource(newResname, CmsResourceFilter.IGNORE_EXPIRATION)) {
        // if the resource does not exist, create it

        try {
            // create the resource
            createdResource = cms.createResource(newResname, resType, contentBytes, properties);
            try {
                cms.unlockResource(newResname);
            } catch (CmsLockException e) {
                // LOG.info("Couldn't unlock uploaded file", e);
            }
        } catch (CmsSecurityException e) {
            // in case of not enough permissions, try to create a plain text file
    		I_CmsResourceType plainResType = OpenCms.getResourceManager().getResourceType(CmsResourceTypePlain.getStaticTypeName());
            createdResource = cms.createResource(newResname, plainResType, contentBytes, properties);
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
                cms.replaceResource(newResname, res.getTypeId(), contentBytes, null);
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
%>