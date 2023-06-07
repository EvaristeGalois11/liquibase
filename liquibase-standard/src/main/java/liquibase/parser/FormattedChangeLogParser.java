package liquibase.parser;

import liquibase.Labels;
import liquibase.Scope;
import liquibase.change.AbstractSQLChange;
import liquibase.change.Change;
import liquibase.change.core.EmptyChange;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.exception.ChangeLogParseException;
import liquibase.precondition.core.PreconditionContainer;
import liquibase.precondition.core.SqlPrecondition;
import liquibase.resource.ResourceAccessor;
import liquibase.util.StreamUtil;
import liquibase.util.StringUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("java:S2583")
public abstract class FormattedChangeLogParser implements ChangeLogParser {

    protected final String FIRST_LINE_REGEX = String.format("%s\\s*liquibase formatted.*", getSingleLineCommentSequence());
    protected final Pattern FIRST_LINE_PATTERN = Pattern.compile(FIRST_LINE_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String PROPERTY_REGEX = String.format("\\s*%s[\\s]*property\\s+(.*:.*)\\s+(.*:.*).*", getSingleLineCommentSequence());
    protected final Pattern PROPERTY_PATTERN = Pattern.compile(PROPERTY_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String ALT_PROPERTY_ONE_DASH_REGEX = "\\s*?[-]+\\s*property\\s.*";
    protected final Pattern ALT_PROPERTY_ONE_DASH_PATTERN = Pattern.compile(ALT_PROPERTY_ONE_DASH_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String CHANGE_SET_REGEX = String.format("\\s*%s[\\s]*changeset\\s+(\"[^\"]+\"|[^:]+):\\s*(\"[^\"]+\"|\\S+).*", getSingleLineCommentSequence());
    protected final Pattern CHANGE_SET_PATTERN = Pattern.compile(CHANGE_SET_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String ALT_CHANGE_SET_ONE_DASH_REGEX ="\\-[\\s]*changeset\\s.*";
    protected final Pattern ALT_CHANGE_SET_ONE_DASH_PATTERN = Pattern.compile(ALT_CHANGE_SET_ONE_DASH_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String ALT_CHANGE_SET_NO_OTHER_INFO_REGEX = String.format("\\s*%s[\\s]*changeset[\\s]*.*$", getSingleLineCommentSequence());
    protected final Pattern ALT_CHANGE_SET_NO_OTHER_INFO_PATTERN = Pattern.compile(ALT_CHANGE_SET_NO_OTHER_INFO_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String ROLLBACK_REGEX = String.format("\\s*%s[\\s]*rollback (.*)", getSingleLineCommentSequence());
    protected final Pattern ROLLBACK_PATTERN = Pattern.compile(ROLLBACK_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String ALT_ROLLBACK_ONE_DASH_REGEX = "\\s*\\-[\\s]*rollback\\s.*";
    protected final Pattern ALT_ROLLBACK_ONE_DASH_PATTERN = Pattern.compile(ALT_ROLLBACK_ONE_DASH_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String PRECONDITIONS_REGEX = String.format("\\s*%s[\\s]*preconditions(.*)", getSingleLineCommentSequence());
    protected final Pattern PRECONDITIONS_PATTERN = Pattern.compile(PRECONDITIONS_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String ALT_PRECONDITIONS_ONE_DASH_REGEX = "\\s*\\-[\\s]*preconditions\\s.*";
    protected final Pattern ALT_PRECONDITIONS_ONE_DASH_PATTERN = Pattern.compile(ALT_PRECONDITIONS_ONE_DASH_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String PRECONDITION_REGEX = String.format("\\s*%s[\\s]*precondition\\-([a-zA-Z0-9-]+) (.*)", getSingleLineCommentSequence());
    protected final Pattern PRECONDITION_PATTERN = Pattern.compile(PRECONDITION_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String ALT_PRECONDITION_ONE_DASH_REGEX = "\\s*\\-[\\s]*precondition(.*)";
    protected final Pattern ALT_PRECONDITION_ONE_DASH_PATTERN = Pattern.compile(ALT_PRECONDITION_ONE_DASH_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String STRIP_COMMENTS_REGEX = ".*stripComments:(\\w+).*";
    protected final Pattern STRIP_COMMENTS_PATTERN = Pattern.compile(STRIP_COMMENTS_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String SPLIT_STATEMENTS_REGEX = ".*splitStatements:(\\w+).*";
    protected final Pattern SPLIT_STATEMENTS_PATTERN = Pattern.compile(SPLIT_STATEMENTS_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String ROLLBACK_SPLIT_STATEMENTS_REGEX = ".*rollbackSplitStatements:(\\w+).*";
    protected final Pattern ROLLBACK_SPLIT_STATEMENTS_PATTERN = Pattern.compile(ROLLBACK_SPLIT_STATEMENTS_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String END_DELIMITER_REGEX = ".*endDelimiter:(\\S*).*";
    protected final Pattern END_DELIMITER_PATTERN = Pattern.compile(END_DELIMITER_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String ROLLBACK_END_DELIMITER_REGEX = ".*rollbackEndDelimiter:(\\S*).*";
    protected final Pattern ROLLBACK_END_DELIMITER_PATTERN = Pattern.compile(ROLLBACK_END_DELIMITER_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String COMMENT_REGEX = String.format("%s[\\s]*comment:? (.*)", getSingleLineCommentSequence());
    protected final Pattern COMMENT_PATTERN = Pattern.compile(COMMENT_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String ALT_COMMENT_PLURAL_REGEX = String.format("%s[\\s]*comments:? (.*)", getSingleLineCommentSequence());
    protected final Pattern ALT_COMMENT_PLURAL_PATTERN = Pattern.compile(ALT_COMMENT_PLURAL_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String ALT_COMMENT_ONE_DASH_REGEX = "\\-[\\s]*comment:? (.*)";
    protected final Pattern ALT_COMMENT_ONE_DASH_PATTERN = Pattern.compile(ALT_COMMENT_ONE_DASH_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String VALID_CHECK_SUM_REGEX = String.format("%s[\\s]*validCheckSum:? (.*)", getSingleLineCommentSequence());
    protected final Pattern VALID_CHECK_SUM_PATTERN = Pattern.compile(VALID_CHECK_SUM_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String ALT_VALID_CHECK_SUM_ONE_DASH_REGEX = "^\\-[\\s]*validCheckSum(.*)$";
    protected final Pattern ALT_VALID_CHECK_SUM_ONE_DASH_PATTERN = Pattern.compile(ALT_VALID_CHECK_SUM_ONE_DASH_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String IGNORE_LINES_REGEX = String.format("%s[\\s]*ignoreLines:(\\w+)", getSingleLineCommentSequence());
    protected final Pattern IGNORE_LINES_PATTERN = Pattern.compile(IGNORE_LINES_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String ALT_IGNORE_LINES_ONE_DASH_REGEX = "\\-[\\s]*?ignoreLines:(\\w+).*$";
    protected final Pattern ALT_IGNORE_LINES_ONE_DASH_PATTERN = Pattern.compile(ALT_IGNORE_LINES_ONE_DASH_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String ALT_IGNORE_REGEX = String.format("%s[\\s]*ignore:(\\w+)", getSingleLineCommentSequence());
    protected final Pattern ALT_IGNORE_PATTERN = Pattern.compile(ALT_IGNORE_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String RUN_WITH_REGEX = ".*runWith:([\\w\\$\\{\\}]+).*";
    protected final Pattern RUN_WITH_PATTERN = Pattern.compile(RUN_WITH_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String RUN_WITH_SPOOL_FILE_REGEX = ".*runWithSpoolFile:(.*).*";
    protected final Pattern RUN_WITH_SPOOL_FILE_PATTERN = Pattern.compile(RUN_WITH_SPOOL_FILE_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String RUN_ON_CHANGE_REGEX = ".*runOnChange:(\\w+).*";
    protected final Pattern RUN_ON_CHANGE_PATTERN = Pattern.compile(RUN_ON_CHANGE_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String RUN_ALWAYS_REGEX = ".*runAlways:(\\w+).*";
    protected final Pattern RUN_ALWAYS_PATTERN = Pattern.compile(RUN_ALWAYS_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String CONTEXT_REGEX = ".*context:(\".*\"|\\S*).*";
    protected final Pattern CONTEXT_PATTERN = Pattern.compile(CONTEXT_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String CONTEXT_FILTER_REGEX = ".*contextFilter:(\".*\"|\\S*).*";
    protected final Pattern CONTEXT_FILTER_PATTERN = Pattern.compile(CONTEXT_FILTER_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String LOGICAL_FILE_PATH_REGEX = ".*logicalFilePath:(\\S*).*";
    protected final Pattern LOGICAL_FILE_PATH_PATTERN = Pattern.compile(LOGICAL_FILE_PATH_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String LABELS_REGEX = ".*labels:(\\S*).*";
    protected final Pattern LABELS_PATTERN = Pattern.compile(LABELS_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String RUN_IN_TRANSACTION_REGEX = ".*runInTransaction:(\\w+).*";
    protected final Pattern RUN_IN_TRANSACTION_PATTERN = Pattern.compile(RUN_IN_TRANSACTION_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String DBMS_REGEX = ".*dbms:([^,][\\w!,]+).*";
    protected final Pattern DBMS_PATTERN = Pattern.compile(DBMS_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String IGNORE_REGEX = ".*ignore:(\\w*).*";
    protected final Pattern IGNORE_PATTERN = Pattern.compile(IGNORE_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String FAIL_ON_ERROR_REGEX = ".*failOnError:(\\w+).*";
    protected final Pattern FAIL_ON_ERROR_PATTERN = Pattern.compile(FAIL_ON_ERROR_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String ON_FAIL_REGEX = ".*onFail:(\\w+).*";
    protected final Pattern ON_FAIL_PATTERN = Pattern.compile(ON_FAIL_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String ON_ERROR_REGEX = ".*onError:(\\w+).*";
    protected final Pattern ON_ERROR_PATTERN = Pattern.compile(ON_ERROR_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String ROLLBACK_CHANGE_SET_ID_REGEX = ".*changeSetId:(\\S+).*";
    protected final Pattern ROLLBACK_CHANGE_SET_ID_PATTERN = Pattern.compile(ROLLBACK_CHANGE_SET_ID_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String ROLLBACK_CHANGE_SET_AUTHOR_REGEX = ".*changesetAuthor:(\\S+).*";
    protected final Pattern ROLLBACK_CHANGE_SET_AUTHOR_PATTERN = Pattern.compile(ROLLBACK_CHANGE_SET_AUTHOR_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String ROLLBACK_CHANGE_SET_PATH_REGEX = ".*changesetPath:(\\S+).*";
    protected final Pattern ROLLBACK_CHANGE_SET_PATH_PATTERN = Pattern.compile(ROLLBACK_CHANGE_SET_PATH_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String ROLLBACK_MULTI_LINE_START_REGEX = String.format("\\s*%s\\s*liquibase\\s*rollback\\s*$", getStartMultiLineCommentSequence());
    protected final Pattern ROLLBACK_MULTI_LINE_START_PATTERN = Pattern.compile(ROLLBACK_MULTI_LINE_START_REGEX, Pattern.CASE_INSENSITIVE);

    protected final String ROLLBACK_MULTI_LINE_END_REGEX = String.format(".*\\s*%s\\s*$", getEndMultiLineCommentSequence());
    protected final Pattern ROLLBACK_MULTI_LINE_END_PATTERN = Pattern.compile(ROLLBACK_MULTI_LINE_END_REGEX, Pattern.CASE_INSENSITIVE);

    private static final String WORD_RESULT_REGEX = "^(?:expectedResult:)?(\\w+) (.*)";
    private static final String SINGLE_QUOTE_RESULT_REGEX = "^(?:expectedResult:)?'([^']+)' (.*)";
    private static final String DOUBLE_QUOTE_RESULT_REGEX = "^(?:expectedResult:)?\"([^\"]+)\" (.*)";

    private static final Pattern[] WORD_AND_QUOTING_PATTERNS = new Pattern[]{
            Pattern.compile(WORD_RESULT_REGEX, Pattern.CASE_INSENSITIVE),
            Pattern.compile(SINGLE_QUOTE_RESULT_REGEX, Pattern.CASE_INSENSITIVE),
            Pattern.compile(DOUBLE_QUOTE_RESULT_REGEX, Pattern.CASE_INSENSITIVE)
    };

    protected abstract String getStartMultiLineCommentSequence();

    protected abstract String getEndMultiLineCommentSequence();

    protected abstract String getSingleLineCommentSequence();

    protected abstract boolean supportsExtension(String changelogFile);

    @Override
    public boolean supports(String changeLogFile, ResourceAccessor resourceAccessor) {
        BufferedReader reader = null;
        try {
            if (supportsExtension(changeLogFile)) {
                InputStream fileStream = openChangeLogFile(changeLogFile, resourceAccessor);
                if (fileStream == null) {
                    return false;
                }
                reader = new BufferedReader(StreamUtil.readStreamWithReader(fileStream, null));

                String firstLine = reader.readLine();

                while (firstLine.trim().isEmpty() && reader.ready()) {
                    firstLine = reader.readLine();
                }
                return FIRST_LINE_PATTERN.matcher(firstLine).matches();
            } else {
                return false;
            }
        } catch (IOException e) {
            Scope.getCurrentScope().getLog(getClass()).fine("Exception reading " + changeLogFile, e);
            return false;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Scope.getCurrentScope().getLog(getClass()).fine("Exception closing " + changeLogFile, e);
                }
            }
        }
    }

    @Override
    public int getPriority() {
        return PRIORITY_DEFAULT + 5;
    }

    @Override
    public DatabaseChangeLog parse(String physicalChangeLogLocation, ChangeLogParameters changeLogParameters, ResourceAccessor resourceAccessor) throws ChangeLogParseException {

        DatabaseChangeLog changeLog = new DatabaseChangeLog();
        changeLog.setChangeLogParameters(changeLogParameters);

        changeLog.setPhysicalFilePath(physicalChangeLogLocation);

        try (BufferedReader reader = new BufferedReader(StreamUtil.readStreamWithReader(openChangeLogFile(physicalChangeLogLocation, resourceAccessor), null))) {
            StringBuilder currentSequence = new StringBuilder();
            StringBuilder currentRollbackSequence = new StringBuilder();

            ChangeSet changeSet = null;
            AbstractSQLChange change = null;

            Matcher rollbackSplitStatementsPatternMatcher = null;
            boolean rollbackSplitStatements = true;
            String rollbackEndDelimiter = null;

            int count = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                count++;
                Matcher commentMatcher = COMMENT_PATTERN.matcher(line);
                Matcher propertyPatternMatcher = PROPERTY_PATTERN.matcher(line);
                Matcher altPropertyPatternMatcher = ALT_PROPERTY_ONE_DASH_PATTERN.matcher(line);
                if (propertyPatternMatcher.matches()) {
                    handleProperty(changeLogParameters, changeLog, propertyPatternMatcher);
                    continue;
                } else if (altPropertyPatternMatcher.matches()) {
                    String message = String.format("Unexpected formatting at line %d. Formatted %s changelogs require known formats, such as '--property name=<property name> value=<property value>' and others to be recognized and run. Learn all the options at %s", count, getSequenceType(), getSequenceDocumentationLink());
                    throw new ChangeLogParseException("\n" + message);
                }
                Matcher changeLogPatterMatcher = FIRST_LINE_PATTERN.matcher (line);
                if (changeLogPatterMatcher.matches ()) {
                    Matcher logicalFilePathMatcher = LOGICAL_FILE_PATH_PATTERN.matcher (line);
                    changeLog.setLogicalFilePath (parseString(logicalFilePathMatcher));

                    // TODO CHANGE_LOG_ID_PATTERN in mongosh?
//                    Matcher changeLogIdMatcher = CHANGE_LOG_ID_PATTERN.matcher (line);
//                    changeLog.setChangeLogId (parseString(changeLogIdMatcher));
                }

                Matcher ignoreLinesMatcher = IGNORE_LINES_PATTERN.matcher(line);
                Matcher altIgnoreMatcher = ALT_IGNORE_PATTERN.matcher(line);
                Matcher altIgnoreLinesOneDashMatcher = ALT_IGNORE_LINES_ONE_DASH_PATTERN.matcher(line);
                if (ignoreLinesMatcher.matches ()) {
                    if ("start".equals(ignoreLinesMatcher.group(1))){
                        while ((line = reader.readLine()) != null){
                            altIgnoreLinesOneDashMatcher = ALT_IGNORE_LINES_ONE_DASH_PATTERN.matcher(line);
                            count++;
                            ignoreLinesMatcher = IGNORE_LINES_PATTERN.matcher(line);
                            if (ignoreLinesMatcher.matches()) {
                                if ("end".equals(ignoreLinesMatcher.group(1))) {
                                    break;
                                }
                            } else if (altIgnoreLinesOneDashMatcher.matches()) {
                                String message =
                                        String.format("Unexpected formatting at line %d. Formatted %s changelogs require known formats, such as '--ignoreLines:end' and others to be recognized and run. Learn all the options at %s", count, getSequenceType(), getSequenceDocumentationLink());
                                throw new ChangeLogParseException("\n" + message);
                            }
                        }
                        continue;
                    } else {
                        try {
                            long ignoreCount = Long.parseLong(ignoreLinesMatcher.group(1));
                            while (ignoreCount > 0 && (line = reader.readLine()) != null) {
                                ignoreCount--;
                                count++;
                            }
                            continue;
                        } catch (NumberFormatException | NullPointerException nfe) {
                            throw new ChangeLogParseException("Unknown ignoreLines syntax");
                        }
                    }
                } else if (altIgnoreLinesOneDashMatcher.matches() || altIgnoreMatcher.matches()) {
                    String message =
                            String.format("Unexpected formatting at line %d. Formatted %s changelogs require known formats, such as '--ignoreLines:<count|start>' and others to be recognized and run. Learn all the options at %s", count, getSequenceType(), getSequenceDocumentationLink());
                    throw new ChangeLogParseException("\n" + message);
                }

                Matcher changeSetPatternMatcher = CHANGE_SET_PATTERN.matcher(line);
                if (changeSetPatternMatcher.matches()) {
                    String finalCurrentSequence = changeLogParameters.expandExpressions(StringUtil.trimToNull(currentSequence.toString()), changeLog);
                    if (changeSet != null) {
                        if (finalCurrentSequence == null) {
                            throw new ChangeLogParseException(String.format("No %s for changeset %s", getSequenceType(), changeSet.toString(false)));
                        }

                        setChangeSequence(change, finalCurrentSequence);

                        String currentRollbackSequenceAsString = currentRollbackSequence.toString();
                        if (StringUtil.trimToNull(currentRollbackSequenceAsString) != null) {
                            if (currentRollbackSequenceAsString.trim().toLowerCase().matches("^not required.*")) {
                                changeSet.addRollbackChange(new EmptyChange());
                            } else if (currentRollbackSequenceAsString.trim().toLowerCase().contains("changesetid")) {
                                handleChangesetIdCase(physicalChangeLogLocation, changeLog, changeSet, currentRollbackSequenceAsString);
                            } else {
                                handleElseCase(changeLogParameters, currentRollbackSequence, changeSet, rollbackSplitStatementsPatternMatcher, rollbackSplitStatements, rollbackEndDelimiter);
                            }
                        }
                    }

                    Matcher stripCommentsPatternMatcher = STRIP_COMMENTS_PATTERN.matcher(line);
                    Matcher splitStatementsPatternMatcher = SPLIT_STATEMENTS_PATTERN.matcher(line);
                    Matcher runWithMatcher = RUN_WITH_PATTERN.matcher(line);
                    Matcher runWithSpoolFileMatcher = RUN_WITH_SPOOL_FILE_PATTERN.matcher(line);
                    rollbackSplitStatementsPatternMatcher = ROLLBACK_SPLIT_STATEMENTS_PATTERN.matcher(line);
                    Matcher endDelimiterPatternMatcher = END_DELIMITER_PATTERN.matcher(line);
                    Matcher rollbackEndDelimiterPatternMatcher = ROLLBACK_END_DELIMITER_PATTERN.matcher(line);

                    Matcher logicalFilePathMatcher = LOGICAL_FILE_PATH_PATTERN.matcher(line);
                    Matcher runOnChangePatternMatcher = RUN_ON_CHANGE_PATTERN.matcher(line);
                    Matcher runAlwaysPatternMatcher = RUN_ALWAYS_PATTERN.matcher(line);
                    Matcher contextPatternMatcher = CONTEXT_PATTERN.matcher(line);
                    Matcher contextFilterPatternMatcher = CONTEXT_FILTER_PATTERN.matcher(line);
                    Matcher labelsPatternMatcher = LABELS_PATTERN.matcher(line);
                    Matcher runInTransactionPatternMatcher = RUN_IN_TRANSACTION_PATTERN.matcher(line);
                    Matcher dbmsPatternMatcher = DBMS_PATTERN.matcher(line);
                    Matcher ignorePatternMatcher = IGNORE_PATTERN.matcher(line);
                    Matcher failOnErrorPatternMatcher = FAIL_ON_ERROR_PATTERN.matcher(line);

                    boolean stripComments = parseBoolean(stripCommentsPatternMatcher, changeSet, true);
                    boolean splitStatements = parseBoolean(splitStatementsPatternMatcher, changeSet, true);
                    rollbackSplitStatements = parseBoolean(rollbackSplitStatementsPatternMatcher, changeSet, true);
                    boolean runOnChange = parseBoolean(runOnChangePatternMatcher, changeSet, false);
                    boolean runAlways = parseBoolean(runAlwaysPatternMatcher, changeSet, false);
                    boolean runInTransaction = parseBoolean(runInTransactionPatternMatcher, changeSet, true);
                    boolean failOnError = parseBoolean(failOnErrorPatternMatcher, changeSet, true);

                    String runWith = parseString(runWithMatcher);
                    if (runWith != null) {
                        runWith = changeLogParameters.expandExpressions(runWith, changeLog);
                    }
                    String runWithSpoolFile = parseString(runWithSpoolFileMatcher);
                    if (runWithSpoolFile != null) {
                        runWithSpoolFile = changeLogParameters.expandExpressions(runWithSpoolFile, changeLog);
                    }
                    String endDelimiter = parseString(endDelimiterPatternMatcher);
                    rollbackEndDelimiter = parseString(rollbackEndDelimiterPatternMatcher);
                    String context = StringUtil.trimToNull(
                            StringUtil.trimToEmpty(parseString(contextFilterPatternMatcher)).replaceFirst("^\"", "").replaceFirst("\"$", "") //remove surrounding quotes if they're in there
                    );
                    if (context == null) {
                        context = StringUtil.trimToNull(
                                StringUtil.trimToEmpty(parseString(contextPatternMatcher)).replaceFirst("^\"", "").replaceFirst("\"$", "") //remove surrounding quotes if they're in there
                        );
                    }

                    if (context != null) {
                        context = changeLogParameters.expandExpressions(context, changeLog);
                    }
                    String labels = parseString(labelsPatternMatcher);
                    if (labels != null) {
                        labels = changeLogParameters.expandExpressions(labels, changeLog);
                    }
                    String logicalFilePath = parseString(logicalFilePathMatcher);
                    if ((logicalFilePath == null) || "".equals(logicalFilePath)) {
                        logicalFilePath = changeLog.getLogicalFilePath();
                    }
                    if (logicalFilePath != null) {
                        logicalFilePath = changeLogParameters.expandExpressions(logicalFilePath, changeLog);
                    }
                    String dbms = parseString(dbmsPatternMatcher);
                    if (dbms != null) {
                        dbms = changeLogParameters.expandExpressions(dbms, changeLog);
                    }

                    String ignore = parseString(ignorePatternMatcher);
                    if (ignore != null) {
                        ignore = changeLogParameters.expandExpressions(ignore, changeLog);
                    }

                    //
                    // Make sure that this line matches the --changeset <author>:<id> with no spaces before ID
                    //
                    String idGroup = changeSetPatternMatcher.group(2);
                    String authorGroup = changeSetPatternMatcher.group(1);

                    //
                    // Use Pattern.Quote to escape the meta-characters
                    // <([{\^-=$!|]})?*+.>
                    //
                    Pattern changeSetAuthorIdPattern =
                            Pattern.compile(String.format("\\s*%s[\\s]*changeset\\s+", getSingleLineCommentSequence()) + Pattern.quote(authorGroup+ ":" + idGroup) + ".*$", Pattern.CASE_INSENSITIVE);
                    Matcher changeSetAuthorIdPatternMatcher = changeSetAuthorIdPattern.matcher(line);
                    if (! changeSetAuthorIdPatternMatcher.matches()) {
                        String message =
                                String.format("Unexpected formatting at line %d. Formatted %s changelogs require known formats, such as '--changeset <authorname>:<changesetId>' and others to be recognized and run. Learn all the options at %s", count, getSequenceType(), getSequenceDocumentationLink());
                        throw new ChangeLogParseException("\n" + message);
                    }

                    String changeSetId = changeLogParameters.expandExpressions(StringUtil.stripEnclosingQuotes(idGroup), changeLog);
                    String changeSetAuthor = changeLogParameters.expandExpressions(StringUtil.stripEnclosingQuotes(authorGroup), changeLog);

                    changeSet = new ChangeSet(changeSetId, changeSetAuthor, runAlways, runOnChange,
                                    DatabaseChangeLog.normalizePath(logicalFilePath),
                                    context, dbms, runWith, runWithSpoolFile,
                                    runInTransaction,
                                    changeLog.getObjectQuotingStrategy(), changeLog);
                    changeSet.setLabels(new Labels(labels));
                    changeSet.setIgnore(Boolean.parseBoolean(ignore));
                    changeSet.setFailOnError(failOnError);
                    changeLog.addChangeSet(changeSet);

                    change = getChange();
                    setChangeSequence(change, finalCurrentSequence);
                    if (splitStatementsPatternMatcher.matches()) {
                        change.setSplitStatements(splitStatements);
                    }
                    change.setStripComments(stripComments);
                    change.setEndDelimiter(endDelimiter);
                    changeSet.addChange(change);

                    currentSequence.setLength(0);
                    currentRollbackSequence.setLength(0);
                } else {
                    Matcher altChangeSetOneDashPatternMatcher = ALT_CHANGE_SET_ONE_DASH_PATTERN.matcher(line);
                    Matcher altChangeSetNoOtherInfoPatternMatcher = ALT_CHANGE_SET_NO_OTHER_INFO_PATTERN.matcher(line);
                    if (altChangeSetOneDashPatternMatcher.matches() || altChangeSetNoOtherInfoPatternMatcher.matches()) {
                        String message = String.format("Unexpected formatting at line %d. Formatted %s changelogs require known formats, such as '--changeset <authorname>:<changesetId>' and others to be recognized and run. Learn all the options at %s", count, getSequenceType(), getSequenceDocumentationLink());
                        throw new ChangeLogParseException("\n" + message);
                    }
                    if (changeSet != null) {
                        Matcher altCommentOneDashMatcher = ALT_COMMENT_ONE_DASH_PATTERN.matcher(line);
                        Matcher altCommentPluralMatcher = ALT_COMMENT_PLURAL_PATTERN.matcher(line);
                        Matcher rollbackMatcher = ROLLBACK_PATTERN.matcher(line);
                        Matcher altRollbackMatcher = ALT_ROLLBACK_ONE_DASH_PATTERN.matcher(line);
                        Matcher preconditionsMatcher = PRECONDITIONS_PATTERN.matcher(line);
                        Matcher altPreconditionsOneDashMatcher = ALT_PRECONDITIONS_ONE_DASH_PATTERN.matcher(line);
                        Matcher preconditionMatcher = PRECONDITION_PATTERN.matcher(line);
                        Matcher altPreconditionOneDashMatcher = ALT_PRECONDITION_ONE_DASH_PATTERN.matcher(line);
                        Matcher validCheckSumMatcher = VALID_CHECK_SUM_PATTERN.matcher(line);
                        Matcher altValidCheckSumOneDashMatcher = ALT_VALID_CHECK_SUM_ONE_DASH_PATTERN.matcher(line);
                        Matcher rollbackMultiLineStartMatcher = ROLLBACK_MULTI_LINE_START_PATTERN.matcher(line);

                        if (commentMatcher.matches()) {
                            if (commentMatcher.groupCount() == 0) {
                                String message = String.format("Unexpected formatting at line %d. Formatted %s changelogs require known formats, such as '--comment <comment>' and others to be recognized and run. Learn all the options at %s", count, getSequenceType(), getSequenceDocumentationLink());
                                throw new ChangeLogParseException("\n" + message);
                            }
                            if (commentMatcher.groupCount() == 1) {
                                changeSet.setComments(commentMatcher.group(1));
                            }
                        } else if (altCommentOneDashMatcher.matches() || altCommentPluralMatcher.matches()) {
                            String message = String.format("Unexpected formatting at line %d. Formatted %s changelogs require known formats, such as '--comment <comment>' and others to be recognized and run. Learn all the options at %s", count, getSequenceType(), getSequenceDocumentationLink());
                            throw new ChangeLogParseException("\n" + message);
                        } else if (validCheckSumMatcher.matches()) {
                            if (validCheckSumMatcher.groupCount() == 0) {
                                String message = String.format("Unexpected formatting at line %d. Formatted %s changelogs require known formats, such as '--rollback <rollback %s>' and others to be recognized and run. Learn all the options at %s", count, getSequenceType(), getSequenceType(), getSequenceDocumentationLink());
                                throw new ChangeLogParseException("\n" + message);
                            } else if (validCheckSumMatcher.groupCount() == 1) {
                                changeSet.addValidCheckSum(validCheckSumMatcher.group(1));
                            }
                        } else if (altValidCheckSumOneDashMatcher.matches()) {
                            String message = String.format("Unexpected formatting at line %d. Formatted %s changelogs require known formats, such as '--validChecksum <checksum>' and others to be recognized and run. Learn all the options at %s", count, getSequenceType(), getSequenceDocumentationLink());
                            throw new ChangeLogParseException("\n" + message);
                        } else if (rollbackMatcher.matches()) {
                            if (rollbackMatcher.groupCount() == 0) {
                                String message = String.format("Unexpected formatting at line %d. Formatted %s changelogs require known formats, such as '--rollback <rollback %s>' and others to be recognized and run. Learn all the options at %s", count, getSequenceType(), getSequenceType(), getSequenceDocumentationLink());
                                throw new ChangeLogParseException("\n" + message);
                            }
                            currentRollbackSequence.append(rollbackMatcher.group(1)).append(System.lineSeparator());
                        } else if (altRollbackMatcher.matches()) {
                            String message = String.format("Unexpected formatting at line %d. Formatted %s changelogs require known formats, such as '--rollback <rollback %s>' and others to be recognized and run. Learn all the options at %s", count, getSequenceType(), getSequenceType(), getSequenceDocumentationLink());
                            throw new ChangeLogParseException("\n" + message);
                        } else if (rollbackMultiLineStartMatcher.matches()) {
                            if (rollbackMultiLineStartMatcher.groupCount() == 0) {
                                currentRollbackSequence.append(extractMultiLineRollBack(reader));
                            }
                        } else if (preconditionsMatcher.matches()) {
                            handlePreconditionsCase(changeSet, count, preconditionsMatcher);
                        } else if (altPreconditionsOneDashMatcher.matches()) {
                            String message = String.format("Unexpected formatting at line %d. Formatted %s changelogs require known formats, such as '--preconditions <onFail>|<onError>|<onUpdate>' and others to be recognized and run. Learn all the options at %s", count, getSequenceType(), getSequenceDocumentationLink());
                            throw new ChangeLogParseException("\n" + message);
                        } else if (preconditionMatcher.matches()) {
                            if (changeSet.getPreconditions() == null) {
                                // create the defaults
                                changeSet.setPreconditions(new PreconditionContainer());
                            }
                            if (preconditionMatcher.groupCount() == 2) {
                                String name = StringUtil.trimToNull(preconditionMatcher.group(1));
                                if (name != null) {
                                    String body = preconditionMatcher.group(2).trim();
                                    if ("sql-check".equals(name)) {
                                        changeSet.getPreconditions().addNestedPrecondition(parseSqlCheckCondition(changeLogParameters.expandExpressions(StringUtil.trimToNull(body), changeSet.getChangeLog())));
                                    } else {
                                        throw new ChangeLogParseException("The '" + name + "' precondition type is not supported.");
                                    }
                                }
                            }
                        } else if (altPreconditionOneDashMatcher.matches()) {
                            String message =
                                    String.format("Unexpected formatting at line %d. Formatted %s changelogs require known formats, such as '--precondition-sql-check' and others to be recognized and run. Learn all the options at `%s`", count, getSequenceType(), getSequenceDocumentationLink());
                            throw new ChangeLogParseException("\n" + message);
                        } else {
                            currentSequence.append(line).append(System.lineSeparator());
                        }
                    } else {
                        if (commentMatcher.matches()) {
                            String message =
                                    String.format("Unexpected formatting at line %d. Formatted %s changelogs do not allow comment lines outside of changesets. Learn all the options at %s", count, getSequenceType(), getSequenceDocumentationLink());
                            throw new ChangeLogParseException("\n" + message);
                        }
                    }
                }
            }

            if (changeSet != null) {
                setChangeSequence(changeLogParameters, currentSequence, changeSet, change);

                if (isEndDelimiter(change)) {
                    change.setEndDelimiter("\n/$");
                }

                if (StringUtil.trimToNull(currentRollbackSequence.toString()) != null) {
                    if (currentRollbackSequence.toString().trim().toLowerCase().matches("^not required.*")) {
                        changeSet.addRollbackChange(new EmptyChange());
                    } else if (currentRollbackSequence.toString().trim().toLowerCase().contains("changesetid")) {
                        handleChangesetIdCase(physicalChangeLogLocation, changeLog, changeSet, currentRollbackSequence.toString());
                    } else {
                        handleElseCase(changeLogParameters, currentRollbackSequence, changeSet, rollbackSplitStatementsPatternMatcher, rollbackSplitStatements, rollbackEndDelimiter);
                    }
                }
            }

        } catch (IOException e) {
            throw new ChangeLogParseException(e);
        }

        return changeLog;
    }

    protected abstract void handlePreconditionsCase(ChangeSet changeSet, int count, Matcher preconditionsMatcher) throws ChangeLogParseException;

    protected abstract AbstractSQLChange getChange();

    protected abstract String getSequenceDocumentationLink();

    protected abstract String getSequenceType();

    protected abstract void setChangeSequence(AbstractSQLChange change, String finalCurrentSequence);

    // TODO Is it the correct name?
    protected abstract boolean isEndDelimiter(AbstractSQLChange change);

    protected abstract void setChangeSequence(ChangeLogParameters changeLogParameters, StringBuilder currentSequence, ChangeSet changeSet, AbstractSQLChange change);

    private void handleElseCase(ChangeLogParameters changeLogParameters, StringBuilder currentRollbackSequence, ChangeSet changeSet, Matcher rollbackSplitStatementsPatternMatcher, boolean rollbackSplitStatements, String rollbackEndDelimiter) {
        AbstractSQLChange rollbackChange = getChange();
        setChangeSequence(rollbackChange, changeLogParameters.expandExpressions(currentRollbackSequence.toString(), changeSet.getChangeLog()));
        if (rollbackSplitStatementsPatternMatcher.matches()) {
            rollbackChange.setSplitStatements(rollbackSplitStatements);
        }
        if (rollbackEndDelimiter != null) {
            rollbackChange.setEndDelimiter(rollbackEndDelimiter);
        }
        changeSet.addRollbackChange(rollbackChange);
    }

    private void handleChangesetIdCase(String physicalChangeLogLocation, DatabaseChangeLog changeLog, ChangeSet changeSet, String currentRollBackSequenceAsString) throws ChangeLogParseException {
        String rollbackString = currentRollBackSequenceAsString.replace("\n", "").replace("\r", "");
        Matcher authorMatcher = ROLLBACK_CHANGE_SET_AUTHOR_PATTERN.matcher(rollbackString);
        Matcher idMatcher = ROLLBACK_CHANGE_SET_ID_PATTERN.matcher(rollbackString);
        Matcher pathMatcher = ROLLBACK_CHANGE_SET_PATH_PATTERN.matcher(rollbackString);

        String changeSetAuthor = StringUtil.trimToNull(parseString(authorMatcher));
        String changeSetId = StringUtil.trimToNull(parseString(idMatcher));
        String changeSetPath = StringUtil.trimToNull(parseString(pathMatcher));

        if (changeSetId == null) {
            throw new ChangeLogParseException("'changesetId' not set in rollback block '"+rollbackString+"'");
        }

        if (changeSetAuthor == null) {
            throw new ChangeLogParseException("'changesetAuthor' not set in rollback block '"+rollbackString+"'");
        }

        if (changeSetPath == null) {
            changeSetPath = physicalChangeLogLocation;
        }

        ChangeSet rollbackChangeSet = changeLog.getChangeSet(changeSetPath, changeSetAuthor, changeSetId);
        DatabaseChangeLog parent = changeLog;
        while ((rollbackChangeSet == null) && (parent != null)) {
            parent = parent.getParentChangeLog();
            if (parent != null) {
                rollbackChangeSet = parent.getChangeSet(changeSetPath, changeSetAuthor, changeSetId);
            }
        }

        if (rollbackChangeSet == null) {
            throw new ChangeLogParseException("Change set " + new ChangeSet(changeSetId, changeSetAuthor, false, false, changeSetPath, null, null, null).toString(false) + " does not exist");
        }
        for (Change rollbackChange : rollbackChangeSet.getChanges()) {
            changeSet.addRollbackChange(rollbackChange);
        }
    }

    private void handleProperty(ChangeLogParameters changeLogParameters, DatabaseChangeLog changeLog, Matcher propertyPatternMatcher) {
        String name = null;
        String value = null;
        String context = null;
        String labels = null;
        String dbms = null;
        boolean global = true;
        for (int i = 1; i <= propertyPatternMatcher.groupCount(); i++) {
            String temp = propertyPatternMatcher.group(i);
            String[] parts = temp.split(":");
            String key = parts[0].trim().toLowerCase();
            switch (key) {
                case "name":
                    name = parts[1].trim();
                    break;
                case "value":
                    value = parts[1].trim();
                    break;
                case "context":
                    context = parts[1].trim();
                    break;
                case "labels":
                    labels = parts[1].trim();
                    break;
                case "dbms":
                    dbms = parts[1].trim();
                    break;
                case "global":
                    global = Boolean.parseBoolean(parts[1].trim());
                    break;
            }
        }
        changeLogParameters.set(name, value, context, labels, dbms, global, changeLog);
    }

    private StringBuilder extractMultiLineRollBack(BufferedReader reader) throws IOException, ChangeLogParseException {
        StringBuilder multiLineRollback = new StringBuilder();

        String line;
        if (reader != null) {
            while ((line = reader.readLine()) != null) {
                if (ROLLBACK_MULTI_LINE_END_PATTERN.matcher(line).matches()) {
                    String[] lastLineSplit = line.split(String.format("%s\\s*$", getEndMultiLineCommentSequence()));
                    if (lastLineSplit.length > 0 && !StringUtil.isWhitespace(lastLineSplit[0])) {
                        multiLineRollback.append(lastLineSplit[0]);
                    }
                    return multiLineRollback;
                }
                multiLineRollback.append(line);
            }
            throw new ChangeLogParseException("Liquibase rollback comment is not closed.");
        }
        return multiLineRollback;
    }

    private SqlPrecondition parseSqlCheckCondition(String body) throws ChangeLogParseException{
        for (Pattern pattern : WORD_AND_QUOTING_PATTERNS) {
            Matcher matcher = pattern.matcher(body);
            if (matcher.matches() && (matcher.groupCount() == 2)) {
                SqlPrecondition p = new SqlPrecondition();
                p.setExpectedResult(matcher.group(1));
                p.setSql(matcher.group(2));
                return p;
            }
        }
        throw new ChangeLogParseException("Could not parse a SqlCheck precondition from '" + body + "'.");
    }

    protected String parseString(Matcher matcher) {
        String endDelimiter = null;
        if (matcher.matches()) {
            endDelimiter = matcher.group(1);
        }
        return endDelimiter;
    }

    private boolean parseBoolean(Matcher matcher, ChangeSet changeSet, boolean defaultValue) throws ChangeLogParseException {
        boolean stripComments = defaultValue;
        if (matcher.matches()) {
            try {
                stripComments = Boolean.parseBoolean(matcher.group(1));
            } catch (Exception e) {
                throw new ChangeLogParseException("Cannot parse " + changeSet + " " + matcher.toString().replaceAll("\\.*", "") + " as a boolean");
            }
        }
        return stripComments;
    }

    protected InputStream openChangeLogFile(String physicalChangeLogLocation, ResourceAccessor resourceAccessor) throws IOException {
        return resourceAccessor.getExisting(physicalChangeLogLocation).openInputStream();
    }
}