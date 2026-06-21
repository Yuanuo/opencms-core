<%@tag import="java.util.Objects"%>
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
       display-name="ExFunctions2"
       description="Extended Functions2"
%>

<%!/** The logger instance for this class. */
public static final org.apache.commons.logging.Log LOG = org.opencms.main.CmsLog.getLog("org.opencms.monitor");

    public static final char[] SENTENCE_ENDING_CHARS = {'.', '!', '?', '．', '。', '！', '？'};

    // ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public static List<Term> segmentAsTerms(String input) {
        final String text = null == input ? null : input.strip();
        if (null == text || text.isEmpty()) {
            return new ArrayList<>(0);
        }
        final List<Term> terms = com.hankcs.hanlp.tokenizer.IndexTokenizer.segment(text);
        final List<Term> result = new ArrayList<>(terms.size());
        for(Term term : terms) {
            if (term.word.isBlank()) continue;
            result.add(term);
        }
        return result;
    }
    public static String[] segmentAsWords(String input) {
        final List<Term> terms = segmentAsTerms(input);
        final String[] result = new String[terms.size()];
        for(int i = 0; i < terms.size(); i++) {
            result[i] = terms.get(i).word;
        }
        return result;
    }
    public static String segmentAsString(String input) {
        return joinArray(" ", segmentAsWords(input));
    }
    public static String[] segmentAsGroup(String input) {
        final String[] words = segmentAsWords(input);
        if (null == words || words.length == 0)
            return null;

        if (Objects.equals(input.strip(), words[0])) {
            final String[] temp = new String[words.length - 1];
            System.arraycopy(words, 1, temp, 0, words.length - 1);
            return new String[]{words[0], joinArray(" ", temp)};
        }

        // for the case, the first element in array is null, means no prefact match
        return new String[]{null, joinArray(" ", words)};
    }
    public static String segmentAsQuery(String input, float boostPerfectMatch) {
        final String[] group = segmentAsGroup(input);
        if (null == group || group.length == 0)
            return "%(query)";

        final StringBuilder result = new StringBuilder();
        if (null != group[0])
            result.append(group[0]).append('^').append(boostPerfectMatch).append(" ");
        result.append(group[1]);

        return result.toString();
    }
    public static String segmentAsQuery(String input, float boostPerfectMatch, float boostMostMatchs) {
        final String[] group = segmentAsGroup(input);
        if (null == group || group.length == 0)
            return "%(query)";

        final StringBuilder result = new StringBuilder();
        if (null != group[0])
            result.append(group[0]).append('^').append(boostPerfectMatch).append(" ");

        if (null != group[1] && group[1].contains(" ")) {
            result.append("(").append(group[1].replace(" ", " AND ")).append(")^").append(boostMostMatchs).append(" ");
            result.append(group[1]);
        }

        return result.toString().strip();
    }
    public static String segmentAndWrapping(String target, String example, String wrapPrefix, String wrapSuffix) {
        // for safe
        wrapPrefix = null == wrapPrefix ? "" : wrapPrefix;
        wrapSuffix = null == wrapSuffix ? "" : wrapSuffix;
        try {
            final List<Term> terms = segmentAsTerms(example);

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

    public static String buildAdvancedQueries(String input, HttpServletRequest request, PageContext pageContext, CmsObject cms) {

        return null;
    }

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%>