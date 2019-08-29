<%@tag import="java.text.ParseException"%>
<%@tag import="java.time.ZoneId"%>
<%@tag import="java.time.ZonedDateTime"%>
<%@tag import="java.time.temporal.TemporalAdjusters"%>
<%@tag import="java.util.ArrayList"%>
<%@tag import="java.util.Arrays"%>
<%@tag import="java.util.Calendar"%>
<%@tag import="java.util.Collection"%>
<%@tag import="java.util.Date"%>
<%@tag import="java.util.LinkedHashMap"%>
<%@tag import="java.util.List"%>
<%@tag import="java.util.Map"%>
<%@tag import="java.util.function.BiFunction"%>
<%@tag import="java.net.InetAddress"%>
<%@tag import="java.net.UnknownHostException"%>
<%@tag import="javax.servlet.http.HttpServletRequest"%>
<%@tag import="org.apache.commons.lang.StringUtils"%>
<%@tag import="org.apache.commons.lang.time.DateFormatUtils"%>
<%@tag import="org.apache.commons.lang.time.DateUtils"%>
<%@tag import="org.apache.commons.lang3.ArrayUtils"%>
<%@tag import="org.opencms.file.CmsObject"%>
<%@tag import="org.opencms.file.CmsResource"%>
<%@tag import="org.opencms.file.CmsResourceFilter"%>
<%@tag import="org.opencms.file.types.CmsResourceTypeFolder"%>
<%@tag import="org.opencms.file.types.I_CmsResourceType"%>
<%@tag import="org.opencms.jsp.search.result.CmsSearchResultWrapper"%>
<%@tag import="org.opencms.jsp.search.result.I_CmsSearchResourceBean"%>
<%@tag import="org.opencms.jsp.util.CmsJspContentAccessBean"%>
<%@tag import="org.opencms.jsp.util.CmsJspContentAccessValueWrapper"%>
<%@tag import="org.opencms.jsp.util.CmsJspElFunctions"%>
<%@tag import="org.opencms.jsp.util.CmsJspStandardContextBean"%>
<%@tag import="org.opencms.main.CmsException"%>
<%@tag import="org.opencms.main.OpenCms"%>
<%@tag import="org.opencms.relations.CmsCategory"%>
<%@tag import="org.opencms.search.CmsSearchResource"%>
<%@tag import="org.opencms.util.CmsStringUtil"%>
<%@tag import="com.hankcs.hanlp.HanLP"%>
<%@tag import="com.hankcs.hanlp.seg.common.Term"%>
<%@tag pageEncoding="UTF-8"
    display-name="ExFunctions"
    description="Extended Functions"
%>


<%!
public static final char[] SENTENCE_ENDING_CHARS = {'.', '!', '?', '．', '。', '！', '？'};
public static List<String> wrappedRangedDaysByDate(Object refDate, String dateMode, String rangeMode) {
	final String pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'";
	List<ZonedDateTime> list = rangedTimesByDate(refDate, dateMode, rangeMode);
	List<String> result = new ArrayList<>(list.size());
	for (int i = 0; i < list.size(); i++) {
		Date date;
		if (i == list.size() - 1)
			date = Date.from(list.get(i).withHour(23).withMinute(59).withSecond(59).withNano(999).toInstant());
		else
			date = Date.from(list.get(i).withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant());
		result.add(DateFormatUtils.format(date, pattern));
	}
	return result;
}

public static String wrappedGroupedCategoriesByRoot(List<CmsCategory> items, String oper1, String oper2) {
	final String op1 = (null == oper1 || oper1.isEmpty()) ? "AND" : oper1;
	final String op2 = (null == oper2 || oper2.isEmpty()) ? "OR" : oper2;
	List<List<CmsCategory>> groupedList = groupedCategoriesByRoot(items);
	StringBuilder result = new StringBuilder();
	groupedList.forEach(list -> {
		StringBuilder buf = new StringBuilder();
		list.forEach(cat -> {
			if (buf.length() > 0)
				buf.append(" ").append(op2).append(" ");
			buf.append(cat.getPath());
		});
		//
		if (result.length() > 0)
			result.append(" ").append(op1).append(" ");

		if (list.size() > 1)
			result.append('(').append(buf).append(')');
		else
			result.append(buf);
	});
	if (groupedList.size() > 1)
		result.insert(0, '(').append(')');
	return result.toString();
}

public static List<List<CmsCategory>> groupedCategoriesByRoot(List<CmsCategory> items) {
	Map<String, List<CmsCategory>> map = new LinkedHashMap<>();
	for (CmsCategory cat : items) {
		String pathRoot = cat.getPath().split("/")[0];
		List<CmsCategory> list = map.get(pathRoot);
		if (null == list)
			map.put(pathRoot, list = new ArrayList<>());
		list.add(cat);
	}
	return new ArrayList<>(map.values());
}

public static List<Date> rangedDaysByDate(Object refDate, String dateMode, String rangeMode) {
	List<ZonedDateTime> list = rangedTimesByDate(refDate, dateMode, rangeMode);
	List<Date> result = new ArrayList<>(list.size());
	list.forEach(zdt -> result.add(Date.from(zdt.withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant())));
	return result;
}

public static List<ZonedDateTime> rangedTimesByDate(Object refDate, String dateMode, String rangeMode) {
	List<ZonedDateTime> result = new ArrayList<>();

	Date refedDate = (null == refDate || refDate.toString().isEmpty() || refDate.toString().contains("now")) //
			? new Date()
			: CmsJspElFunctions.convertDate(refDate);
	ZonedDateTime theDay = refedDate.toInstant().atZone(ZoneId.systemDefault());
	ZonedDateTime lastDayOfYear = theDay.with(TemporalAdjusters.lastDayOfYear());
	ZonedDateTime lastDayOfMonth = theDay.with(TemporalAdjusters.lastDayOfMonth());
	ZonedDateTime startDay = null, endDay = null;

	final boolean yearMode = null != dateMode && dateMode.startsWith("y");// normal is year
	final boolean monthMode = null != dateMode && dateMode.startsWith("m");// normal is month

	final boolean lastDayMode = null != rangeMode && rangeMode.startsWith("l");// normal is lastday
	final boolean centerDayMode = null != rangeMode && rangeMode.startsWith("c");// normal is center

	if (lastDayMode || centerDayMode) { // lastday
		if (yearMode && (centerDayMode || // center current day
				(lastDayMode && theDay.getDayOfYear() >= lastDayOfYear.getDayOfYear() - 30))) { // if last month of
																								// year
			startDay = theDay.minusDays(182);
			endDay = startDay.plusYears(1);
		} else if (monthMode && (centerDayMode || // center current day
				(lastDayMode && theDay.getDayOfMonth() >= lastDayOfMonth.getDayOfMonth() - 2))) { // if last two
																									// days of month
			startDay = theDay.minusDays(15);
			endDay = startDay.plusMonths(1);
		} else if (centerDayMode || // center current day
				(lastDayMode && theDay.getDayOfWeek().getValue() > 5)) { // if last two days of week
			startDay = theDay.minusDays(3);
			endDay = startDay.plusWeeks(1);
		}
	}
	//
	if (null == startDay || null == endDay) { // normal
		if (yearMode) {
			startDay = theDay.withDayOfYear(1);
			endDay = startDay.plusYears(1);
		} else if (monthMode) {
			startDay = theDay.withDayOfMonth(1);
			endDay = startDay.plusMonths(1);
		} else {
			startDay = theDay.minusDays(theDay.getDayOfWeek().getValue() - 1);
			endDay = startDay.plusWeeks(1);
		}
	}

	ZonedDateTime nextDay = startDay;
	while (nextDay.isBefore(endDay)) {
		result.add(nextDay);
		nextDay = nextDay.plusDays(1);
	}

	return result;
}

public static Date theDayWithMaxTime(Date date) {
	if (null == date)
		date = new Date();
	Calendar cal = Calendar.getInstance();
	cal.setTime(date);
	cal.set(Calendar.HOUR_OF_DAY, 23);
	cal.set(Calendar.MINUTE, 59);
	cal.set(Calendar.SECOND, 59);
	cal.set(Calendar.MILLISECOND, 999);
	return cal.getTime();
}

public static Date nextDayWithMinTime(Date date) {
	if (null == date)
		date = new Date();
	Calendar cal = Calendar.getInstance();
	cal.setTime(date);
	cal.set(Calendar.HOUR_OF_DAY, 0);
	cal.set(Calendar.MINUTE, 0);
	cal.set(Calendar.SECOND, 0);
	cal.set(Calendar.MILLISECOND, 0);
	cal.add(Calendar.DAY_OF_YEAR, 1);
	return cal.getTime();
}

@SuppressWarnings("unchecked")
public static Map<String, List<Object>> groupSearchResultsByDays(HttpServletRequest request,
		PageContext pageContext) {
	// java.util.Enumeration names = request.getAttributeNames();
	// while (names.hasMoreElements()) {
	// Object k = names.nextElement();
	// if (!k.toString().endsWith(".html"))
	// System.out.println(k + " = " + (request.getAttribute(String.valueOf(k))));
	// }
	CmsJspStandardContextBean cms = (CmsJspStandardContextBean) request.getAttribute("cms");
	Map<String, List<Object>> result = new LinkedHashMap<>();
	Map<String, String> settings = (Map<String, String>) pageContext.getAttribute("settings");

	String tmpStr = (String) pageContext.getAttribute("groupByPattern");
	if (StringUtils.isBlank(tmpStr))
		tmpStr = "yyyy-MM-dd'/'E";
	final String groupKeyPattern = tmpStr;
	String todayKey = DateFormatUtils.format(new Date(), groupKeyPattern, cms.getLocale());

	String filterdatesLstVal = settings.get("filterdatesLst");
	if (null == filterdatesLstVal || filterdatesLstVal.trim().isEmpty())
		return result;
	List<String> filterdatesLst = Arrays.asList(filterdatesLstVal.replaceAll("[\\[\\]]", "").split(","));
	String dateRangeStart = null, dateRangeEnd = null;
	for (int i = 0; i < filterdatesLst.size(); i++) {
		String itm = filterdatesLst.get(i).trim();
		try {
			Date date = DateUtils.parseDate(itm.trim(), new String[] { "yyyy-MM-dd'T'HH:mm:ss'Z'" });
			String groupKey = DateFormatUtils.format(date, groupKeyPattern, cms.getLocale());
			if (todayKey.equals(groupKey))
				groupKey = groupKey + "/today";
			result.put(groupKey, new ArrayList<>(0));
			
			if(null == dateRangeStart)
				dateRangeStart = DateFormatUtils.format(date, "MM月dd日", cms.getLocale());
			dateRangeEnd = DateFormatUtils.format(date, "MM月dd日", cms.getLocale());
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}
	pageContext.setAttribute("dateRangeStart", dateRangeStart);
	pageContext.setAttribute("dateRangeEnd", dateRangeEnd);

	CmsSearchResultWrapper search = (CmsSearchResultWrapper) request.getAttribute("search");
	if (null == search)
		return result;

	Collection<I_CmsSearchResourceBean> searchDatas = search.getSearchResults();
	for (I_CmsSearchResourceBean data : searchDatas) {
		CmsSearchResource dataRes = data.getSearchResource();

		List<String> locales = dataRes.getDocument().getMultivaluedFieldAsStringList("res_locales");
		if (null == locales)
			locales = dataRes.getDocument().getMultivaluedFieldAsStringList("con_locales");
		String locale = locales.get(0);
		// dataRes.getDocument().getFieldValueAsString("disptitle_"+locale+"_h");

		Date date = dataRes.getDocument().getFieldValueAsDate("newsdate_" + locale + "_dt");

		String groupKey = DateFormatUtils.format(date, groupKeyPattern, cms.getLocale());
		if (todayKey.equals(groupKey))
			groupKey = groupKey + "/today";
		List<Object> list = result.get(groupKey);
		if (null == list)
			result.put(groupKey, list = new ArrayList<>());
		list.add(dataRes);
	}

	return result;
}

public static Map<String, List<Object>> groupSearchResults(HttpServletRequest request,
        PageContext pageContext,
        BiFunction<CmsJspStandardContextBean, I_CmsSearchResourceBean, String> groupKeyMaker) {
    CmsJspStandardContextBean cms = (CmsJspStandardContextBean) request.getAttribute("cms");
    Map<String, List<Object>> result = new LinkedHashMap<>();
//    Map<String, String> settings = (Map<String, String>) pageContext.getAttribute("settings");

    CmsSearchResultWrapper search = (CmsSearchResultWrapper) request.getAttribute("search");
    if (null == search)
        return result;

    Collection<I_CmsSearchResourceBean> searchDatas = search.getSearchResults();
    for (I_CmsSearchResourceBean data : searchDatas) {
        
        CmsSearchResource dataRes = data.getSearchResource();
        String resGroup = dataRes.getField("group");
        
        String groupKey = groupKeyMaker.apply(cms, data);
        List<Object> list = result.get(groupKey);
        if (null == list)
            result.put(groupKey, list = new ArrayList<>());
        list.add(data);
    }

    return result;
}

public static final List<CmsJspContentAccessBean> getManagedResources(CmsObject cms, Object inputRes,
		String filterType, boolean readTree, int count) {
	List<CmsJspContentAccessBean> result = new ArrayList<>(2);
	if(null == inputRes)
	    return result;
	try {
		CmsResourceFilter filter = null;
		try {
			if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(filterType)) {
				I_CmsResourceType type = OpenCms.getResourceManager().getResourceType(filterType);
				if (null != type)
					filter = CmsResourceFilter.requireType(type);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			filter = null;
		}
		if (null == filter)
			filter = CmsResourceFilter.DEFAULT_FILES;

		CmsResource res = null;
		if (inputRes instanceof CmsResource)
			res = (CmsResource) inputRes;
		else if (inputRes instanceof PageContext) {
			CmsJspContentAccessBean conVar = (CmsJspContentAccessBean) ((PageContext) inputRes)
					.getAttribute("content");
			// CmsResource folderRes1 =
			// cms.readParentFolder(conVar.getFile().getStructureId());
			res = conVar.getResource();
		} else
			res = cms.readResource(inputRes.toString());

		I_CmsResourceType resType = OpenCms.getResourceManager().getResourceType(res);
		if (resType.getTypeName().equals(CmsResourceTypeFolder.getStaticTypeName())) {
			List<CmsResource> list = cms.readResources(res, filter, readTree);
			if (null != list && !list.isEmpty())
				list.forEach(itm -> result.add(new CmsJspContentAccessBean(cms, itm)));
		} else {
			CmsJspContentAccessBean bean = new CmsJspContentAccessBean(cms, res);
			List<CmsJspContentAccessValueWrapper> tmpList = bean.getValueList().get("Folder");
			if(null == tmpList || tmpList.isEmpty()) {
			    CmsJspContentAccessValueWrapper tmpValWrap = bean.getValue().get("Folder");
			    if(null != tmpValWrap && tmpValWrap.getIsSet()) {
			        tmpList = new ArrayList<>();
			        tmpList.add(tmpValWrap);
			    }
			}
			if (null != tmpList && !tmpList.isEmpty()) {
				for (CmsJspContentAccessValueWrapper tmpItm : tmpList) {
					String folder = tmpItm.getStringValue();
					if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(folder)) {
						try {
							List<CmsResource> list = cms.readResources(folder, filter, readTree, count + 1);
							if (null != list && !list.isEmpty()) {
								for (int i = 0; i < list.size(); i++) {
									CmsResource itm = list.get(i);
									if (itm.equals(res))
										continue;
									result.add(new CmsJspContentAccessBean(cms, itm));
									if (result.size() >= count)
										break;
								}
							}
						} catch (CmsException ex) {
							ex.printStackTrace();// DEBUG only
						}

					}
				}
			}
		}
	} catch (CmsException e) {
		e.printStackTrace();
	}
	if (result.isEmpty()) {
		for (int i = 0; i < count; i++)
			result.add(null);
	}
	return result;
}

public static List<CmsJspContentAccessBean> getNeighborsByParentFolder(CmsObject cms, CmsResource res,
		boolean readTree) {
	List<CmsJspContentAccessBean> result = new ArrayList<>(2);
	result.add(null);
	result.add(null);
	try {
		CmsResource folderRes = cms.readParentFolder(res.getStructureId());
		I_CmsResourceType resType = OpenCms.getResourceManager().getResourceType(res);
		List<CmsResource> list = cms.readResources(folderRes, CmsResourceFilter.requireType(resType), readTree);
		for (int i = 0; i < list.size(); i++) {
			CmsResource itm = list.get(i);
			if (itm.equals(res)) {
				CmsResource prev = i - 1 < 0 ? null : list.get(i - 1);
				if (null == prev && list.size() > 0)
					prev = list.get(list.size() - 1);
				result.set(0, new CmsJspContentAccessBean(cms, prev));

				CmsResource next = i + 1 >= list.size() ? null : list.get(i + 1);
				if (null == next && list.size() > 0)
					next = list.get(0);// use the first one
				result.set(1, new CmsJspContentAccessBean(cms, next));
				break;
			}
		}
		list.forEach(itm -> result.add(new CmsJspContentAccessBean(cms, itm)));
	} catch (CmsException e) {
		e.printStackTrace();
	}
	return result;
}

public static List<CmsJspContentAccessBean> getNeighborsByParentFolder(CmsObject cms, Object inputObj) {

	CmsResource refRes = null;
	if (inputObj instanceof PageContext) {
		PageContext pageCtx = (PageContext) inputObj;
		CmsJspContentAccessBean conVar = (CmsJspContentAccessBean) pageCtx.getAttribute("content");
		refRes = conVar.getResource();
	} else if (inputObj instanceof CmsJspContentAccessBean) {
		CmsJspContentAccessBean conVar = (CmsJspContentAccessBean) inputObj;
		refRes = conVar.getResource();
	} else if (inputObj instanceof CmsResource) {
		refRes = (CmsResource) inputObj;
	}
	List<CmsJspContentAccessBean> nres = getNeighborsByParentFolder(cms, refRes, false);
	if(nres.size() > 0 && nres.get(0) != null) {
        if (inputObj instanceof PageContext) {
            CmsJspContentAccessBean prevRes = nres.get(0);
            PageContext pageCtx = (PageContext) inputObj;
            pageCtx.setAttribute("prevContent", prevRes);
            pageCtx.setAttribute("prevResource", null == prevRes ? null : prevRes.getResource());
        }
	}
    if(nres.size() > 1 && nres.get(1) != null) {
        if (inputObj instanceof PageContext) {
            CmsJspContentAccessBean nextRes = nres.get(1);
            PageContext pageCtx = (PageContext) inputObj;
            pageCtx.setAttribute("nextContent", nextRes);
            pageCtx.setAttribute("nextResource", null == nextRes ? null : nextRes.getResource());
        }
    }
    if (inputObj instanceof PageContext) {
        ((PageContext) inputObj).setAttribute("neighbors", nres);
    }
	return nres;
}

public static CmsJspContentAccessBean foundPrevOrNextInNeighbors(CmsJspContentAccessBean res,
        List<CmsJspContentAccessBean> list, boolean prevOrNext) {
    for (int i = 0; i < list.size(); i++) {
        CmsJspContentAccessBean itm = list.get(i);
        if (!itm.getResource().equals(res.getResource()))
            continue;
        if (prevOrNext)
            return i - 1 < 0 ? null : list.get(i - 1);
        else
            return i + 1 >= list.size() ? null : list.get(i + 1);
    }
    return null;
}

/**
 * @param input
 * @param length
 * @return
 */
public static String trimToSize(String input, int length) {
    return trimToSize(input, length, 6, " ...");
}

public static String trimToSize(String source, int length, int area, String suffix) {

    if (null == source || source.isEmpty()) {
        return source;
    }

    // calculate real length for every char(ASCII or UNICODE)
    int requireBytesLen = 0;
    final List<Byte> requireCharsList = new ArrayList<>(source.length());
    for (char chr : source.toCharArray()) {
        if (requireBytesLen >= length)
            break;
        byte charLen = (byte) (chr > 255 ? 2 : 1);
        requireBytesLen += charLen;
        requireCharsList.add(charLen);
    }
    // no operation is required
    if (requireBytesLen < length) {
        return source;
    }

    if (null == suffix) {
        // we need an empty suffix
        suffix = "";
    }

    // all chars are ASCII
    if (requireCharsList.size() == length) {
        int strLen = requireCharsList.size() - suffix.length();
        if (strLen < 0)
            return suffix;
        return source.substring(0, strLen) + suffix;
    }

    // let suffix support dword
    int suffixBytesLen = 0;
    for (int i = 0; i < suffix.length(); i++) {
        suffixBytesLen += (byte) (suffix.charAt(i) > 255 ? 2 : 1);
    }
    int suffixBytesCount = 0;
    for (int i = requireCharsList.size() - 1; i > -1; i--) {
        suffixBytesCount += requireCharsList.get(i);
        if (suffixBytesCount < suffixBytesLen) {
            requireCharsList.remove(i);
        } else {
            break;
        }
    }

    // mod
    int modCharsLen = 0;
    final List<Integer> modCharIdxs = new ArrayList<>(area);
    for (int i = requireCharsList.size() - 1; i > -1; i--) {
        modCharIdxs.add(0, i);
        modCharsLen += requireCharsList.get(i);
        if (modCharsLen >= area)
            break;
    }
    // first search sentence ending chars
    int sentencePos = -1;
    for (int i = modCharIdxs.size() - 1; i > -1; i--) {
        char modChar = source.charAt(modCharIdxs.get(i));
        if (ArrayUtils.contains(SENTENCE_ENDING_CHARS, modChar)) {
            sentencePos = i;
            break;
        }
    }
    // or search whitespace char
    if (sentencePos == -1) {
        for (int i = modCharIdxs.size() - 1; i > -1; i--) {
            char modChar = source.charAt(modCharIdxs.get(i));
            sentencePos = (Character.isWhitespace(modChar) || modChar == ' ') ? i : -1;
            if (sentencePos >= 0)
                break;
        }
    }
    // do mod
    int maxSrcCharIdx;
    if (sentencePos >= 0) {
        maxSrcCharIdx = modCharIdxs.get(sentencePos);
    } else {
        maxSrcCharIdx = requireCharsList.size();
    }
    // build result
    StringBuilder buf = new StringBuilder(maxSrcCharIdx + 1 + suffix.length());
    buf.append(source.substring(0, maxSrcCharIdx + 1));
    buf.append(suffix);
    return buf.toString();
}

///**
// * @param resourceName
// * @param transName
// * @return
// */ 
//public static final String pinyin(String resourceName, String transName) {
//    try {
//        return com.github.stuxuhai.jpinyin.PinyinHelper.convertToPinyinString(transName, "-", com.github.stuxuhai.jpinyin.PinyinFormat.WITHOUT_TONE);
//    } catch (Exception e) {}
//    return transName;
//}
//
//public static final String pinyins(String resourceName, String transName) {
//    try {
//        return com.github.stuxuhai.jpinyin.PinyinHelper.getShortPinyin(transName);
//    } catch (Exception e) {}
//    return transName;
//}


public static String segmentAndWrapping(String target, String example, String wrapPrefix, String wrapSuffix) {
    // for safe
    wrapPrefix = null == wrapPrefix ? "" : wrapPrefix;
    wrapSuffix = null == wrapSuffix ? "" : wrapSuffix;
    try {
        final List<Term> terms = HanLP.segment(example.trim());

        StringBuilder buf = new StringBuilder();
        int fromIdx = 0;
        for(Term term : terms) {
            int findIdx = target.indexOf(term.word, fromIdx);

            if (findIdx == -1) {
                continue;
            }
            // founded
            String partLeft = target.substring(fromIdx, findIdx);
            String partCurr = target.substring(findIdx, findIdx + term.word.length());

            buf.append(partLeft);
            buf.append(wrapPrefix).append(partCurr).append(wrapSuffix);

            // remark fromIdx for next loop
            fromIdx = findIdx + term.word.length();
        }
        // don't lost more chars
        if (fromIdx < target.length()) {
            buf.append(target.substring(fromIdx));
        }
        return buf.toString();
    } catch (Throwable t) {
        return target;
    }
}
public static List<String> segmentAsList(String target) {
    final String text = null == target ? null : target.trim();
    if (null == text || text.isEmpty()) {
        return new ArrayList<>(0);
    }
    final List<Term> terms = HanLP.segment(text);
    final List<String> result = new ArrayList<>(terms.size());
    for(Term term : terms) {
        result.add(term.word);
    }
    return result;
}

 public static String getRemoteAddr(HttpServletRequest request) {
     String ipAddress = null;
	// ipAddress = this.getRequest().getRemoteAddr();
     ipAddress = request.getHeader("x-forwarded-for");
     if (ipAddress == null || ipAddress.length() == 0
         || "unknown".equalsIgnoreCase(ipAddress)) {
         ipAddress = request.getHeader("Proxy-Client-IP");
     }
     if (ipAddress == null || ipAddress.length() == 0
         || "unknown".equalsIgnoreCase(ipAddress)) {
         ipAddress = request.getHeader("WL-Proxy-Client-IP");
     }
     if (ipAddress == null || ipAddress.length() == 0
         || "unknown".equalsIgnoreCase(ipAddress)) {
         ipAddress = request.getRemoteAddr();
         if (ipAddress.equals("127.0.0.1")
             || ipAddress.equals("0:0:0:0:0:0:0:1")) {
			// 根据网卡取本机配置的IP
             InetAddress inet = null;
             try {
                 inet = InetAddress.getLocalHost();
                 ipAddress = inet.getHostAddress();
             } catch (UnknownHostException e) {
                 e.printStackTrace();
             }
         }


     }


	// 对于通过多个代理的情况，第一个IP为客户端真实IP,多个IP按照','分割
     if (ipAddress != null && ipAddress.length() > 15) { // "***.***.***.***".length() = 15
         if (ipAddress.indexOf(",") > 0) {
             ipAddress = ipAddress.substring(0, ipAddress.indexOf(","));
         }
     }
     return ipAddress;
 }

public static final Object debug(HttpServletRequest request,
		PageContext pageContext) {
	return null;
}

public static void log(Object...args) {
  	final String logTime = org.apache.commons.lang3.time.DateFormatUtils.format(new java.util.Date(), "yyyy-MM-dd HH:mm:ss.SSS");
    System.out.println("LOG-" + logTime + ": " + org.apache.commons.lang3.StringUtils.join(args));
}
%>