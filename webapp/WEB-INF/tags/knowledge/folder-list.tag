<%@ tag import="java.util.List" %>
<%@ tag import="java.util.ArrayList" %>
<%@ tag import="org.opencms.jsp.CmsJspTagSearch" %>
<%@ tag import="org.opencms.jsp.search.result.I_CmsSearchResourceBean" %>
<%@ tag import="org.opencms.jsp.search.result.CmsSearchResultWrapper" %>
<%@ tag import="org.opencms.file.CmsResource" %>
<%@ tag pageEncoding="UTF-8"
        display-name="folder-list"
        body-content="scriptless"
        trimDirectiveWhitespaces="true"
        description="List folders from root by given folder or file." %>

<%@ attribute name="resource" type="org.opencms.file.CmsResource" required="true"
              description="" %>

<%@ attribute name="resourceKind" type="java.lang.String" required="true"
              description="" %>

<%@ attribute name="childrenKind" type="java.lang.String" required="false"
              description="" %>

<%@ variable name-given="folderRoot" declare="true"
             description="" %>

<%@ variable name-given="folderList" declare="true"
             description="" %>

<%!
  public static CmsResource getFolderRoot(CmsResource resource, String resourceKind, PageContext pageContext) throws Exception {
    final String path = resource.getRootPath();
    CmsResource rootResource = "root".equals(resourceKind) ? resource : null;

    if (rootResource != null) {
      return rootResource;
    }

    String searchPath = path;
    for (String str : List.of(".content/kb-files/", ".content/")) {
      int idx = path.indexOf(str);
      if (idx != -1) {
        searchPath = path.substring(0, idx + str.length());
        break;
      }
    }
    String searchParams = "fq=type:\"kb-folder\"&fq=kind_en:\"root\"&fq=parent-folders:\"%s\"&page=1&sort=path desc".formatted(searchPath);
    String searchConfig = "{\"ignorequery\": true,\"extrasolrparams\": \"" + searchParams.replace("\"", "\\\"") + "\",\"pagesize\": 500}";

    CmsJspTagSearch searchTag = new CmsJspTagSearch();
    searchTag.setConfigString(searchConfig);
    searchTag.setVar("search");
    searchTag.setPageContext(pageContext);
    searchTag.doStartTag();
    searchTag.doEndTag();

    CmsSearchResultWrapper searchWrapper = (CmsSearchResultWrapper) pageContext.getAttribute("search");
    for (I_CmsSearchResourceBean bean : searchWrapper.getSearchResults()) {
      String itemPath = bean.getSearchResource().getRootPath();
      if (!path.equals(itemPath) && path.startsWith(itemPath.substring(0, itemPath.lastIndexOf('/') + 1))) {
        rootResource = bean.getSearchResource();
        break;
      }
    }
    return rootResource;
  }

  public static void getFolderList(CmsResource resource, String resourceKind, String childrenKind, PageContext pageContext) throws Exception {
    final CmsResource folderRoot = getFolderRoot(resource, resourceKind, pageContext);
    final List<Object> folderList = new ArrayList<>();

    pageContext.setAttribute("folderRoot", folderRoot);
    pageContext.setAttribute("folderList", folderList);

    if (folderRoot == null) {
      return;
    }
    childrenKind = ((childrenKind == null || childrenKind.isEmpty()) ? "catalog, book" : childrenKind).replace(" ", "").replace(",", "\" OR \"");
    String searchPath = folderRoot.getRootPath().substring(0, folderRoot.getRootPath().lastIndexOf('/') + 1);

    String searchParams = "fq=type:\"kb-folder\"&fq=kind_en:\"%s\"&fq=parent-folders:\"%s\"&page=1&sort=path asc".formatted(childrenKind, searchPath);
    String searchConfig = "{\"ignorequery\": true,\"extrasolrparams\": \"" + searchParams.replace("\"", "\\\"") + "\",\"pagesize\": 500}";

    CmsJspTagSearch searchTag = new CmsJspTagSearch();
    searchTag.setConfigString(searchConfig);
    searchTag.setVar("search");
    searchTag.setPageContext(pageContext);
    searchTag.doStartTag();
    searchTag.doEndTag();

    CmsSearchResultWrapper searchWrapper = (CmsSearchResultWrapper) pageContext.getAttribute("search");
    searchWrapper.getSearchResults().forEach(bean -> folderList.add(bean.getSearchResource()));
  }
%>

<%
  getFolderList(resource, resourceKind, childrenKind, (PageContext) jspContext);
%>
<jsp:doBody/>
