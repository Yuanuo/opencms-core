
package org.opencms.file.content;

import org.opencms.configuration.CmsConfigurationManager;
import org.opencms.db.CmsDbContext;
import org.opencms.db.CmsDriverManager;
import org.opencms.file.CmsResource;
import org.opencms.main.CmsException;
import org.opencms.util.CmsFileUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dom4j.Element;

public class CmsFileContentManager {

    /**
     * The list of file content handles.
     */
    private final List<Handle> m_handles = new ArrayList<>();

    public void init(CmsDbContext dbc, CmsConfigurationManager configurationManager, CmsDriverManager driverManager)
    throws CmsException {

        for (Handle handle : m_handles) {
            handle.m_driver.init(dbc, configurationManager, null, driverManager);
        }
    }

    public void addHandle(Handle handle) {

        m_handles.add(handle);
    }

    public List<Handle> getHandles() {

        return m_handles;
    }

    public I_CmsFileContentDriver getDriver(CmsResource resource) {

        if (!resource.isFile())
            return null;
        return getDriver(resource.getResourceId().toString(), resource.getName(), resource.getLength());
    }

    public I_CmsFileContentDriver getDriver(String resourceId, String fileName, long fileSize) {

        Handle handle = getHandle(resourceId, fileName, fileSize);
        return null == handle ? null : handle.m_driver;
    }

    public Handle getHandle(String resourceId, String fileName, long fileSize) {

        for (Handle handle : m_handles) {
            if (handle.accept(resourceId, fileName, fileSize))
                return handle;
        }

        // if not accept by any filter, just return the last one;
        return m_handles.isEmpty() ? null : m_handles.get(m_handles.size() - 1);
    }

    public I_CmsFileContentDriver getRestorableDriver(byte[] content) {

        return getRestorableHandle(content).m_driver;
    }

    public Handle getRestorableHandle(byte[] content) {
        
        if (null != content && content.length > 0) {
            for (Handle handle : m_handles) {
                if (handle.m_driver.isReadable(content))
                    return handle;
            }
        }
        // if not accept by any filter, just return the last one;
        return m_handles.isEmpty() ? null : m_handles.get(m_handles.size() - 1);
    }

    public static class Handle {

        private Long minlength;
        private Long maxlength;
        private final Set<String> extensions = new LinkedHashSet<>();
        private I_CmsFileContentDriver m_driver;

        public void setDriver(I_CmsFileContentDriver driver) {

            this.m_driver = driver;
        }

        public void addExtension(String extension) {

            this.extensions.add(extension.toLowerCase());
        }

        public void setMinlength(Long minlength) {

            this.minlength = minlength;
        }

        public void setMaxlength(Long maxlength) {

            this.maxlength = maxlength;
        }
        
        public I_CmsFileContentDriver getDriver() {

            return m_driver;
        }
        
        public Set<String> getExtensions() {

            return extensions;
        }
        
        public Long getMinlength() {

            return minlength;
        }
        
        public Long getMaxlength() {

            return maxlength;
        }

        public boolean accept(String resourceId, String fileName, long fileSize) {

            // no any filter enabled
            if (extensions.isEmpty() && null == minlength && null == maxlength)
                return true;
            if (extensions.isEmpty()
                && null != minlength && minlength.longValue() < 1
                && null != maxlength && maxlength.longValue() < 1)
                return true;

            // filter by extension is enabled
            if (!extensions.isEmpty()) {
                String fileExt = CmsFileUtil.getExtension(fileName);
                // but not in the allowed set
                if (!extensions.contains(fileExt))
                    return false;
            }

            // filter by filesize
            // if less than minlength
            if (null != minlength && minlength.longValue() > 0 && minlength.longValue() > fileSize)
                return false;
            // if great than maxlength
            if (null != maxlength && maxlength.longValue() > 0 && maxlength.longValue() < fileSize)
                return false;
            
            // filter by resourceId pattern?
            
            return true;
        }
    }

    public void generateXml(Element filecontentElement) {

        for (CmsFileContentManager.Handle handle : getHandles()) {
            //handle
            Element handleElement = filecontentElement.addElement("handle");
            //driver
            Element driverElement = handleElement.addElement("driver");
            driverElement.addAttribute("class", handle.getDriver().getClass().getName());
            
            // params for driver
            Map<String, String> params = ((CmsFileContentDriverBase)handle.getDriver()).getConfiguration();
            if(null != params) {
                for(String name : params.keySet()) {
                    Element paramEle = driverElement.addElement("param");
                    paramEle.addAttribute("name", name);
                    paramEle.setText(params.get(name));
                }
            }
            
            //extension for driver
            if (null != handle.getExtensions() && !handle.getExtensions().isEmpty()) {
                for(String ext : handle.getExtensions())
                    handleElement.addElement("extension").addAttribute("value", ext);
            }
            //minlength
            if (null != handle.getMinlength())
                handleElement.addElement("minlength").addAttribute("value", handle.getMinlength().toString());
            //maxlength
            if (null != handle.getMaxlength())
                handleElement.addElement("maxlength").addAttribute("value", handle.getMaxlength().toString());
        }
    }
}
