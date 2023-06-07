package liquibase.parser.core.formattedsql;

import liquibase.change.AbstractSQLChange;
import liquibase.change.core.RawSQLChange;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.exception.ChangeLogParseException;
import liquibase.parser.FormattedChangeLogParser;
import liquibase.precondition.core.PreconditionContainer;
import liquibase.util.StringUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class FormattedSqlChangeLogParser extends FormattedChangeLogParser {

    private static final String ON_UPDATE_SQL_REGEX = ".*onUpdateSQL:(\\w+).*";
    private static final Pattern ON_UPDATE_SQL_PATTERN = Pattern.compile(ON_UPDATE_SQL_REGEX, Pattern.CASE_INSENSITIVE);

    private static final String ON_SQL_OUTPUT_REGEX = ".*onSqlOutput:(\\w+).*";
    private static final Pattern ON_SQL_OUTPUT_PATTERN = Pattern.compile(ON_SQL_OUTPUT_REGEX, Pattern.CASE_INSENSITIVE);

    @Override
    protected String getStartMultiLineCommentSequence() {
        return "\\/\\*";
    }

    @Override
    protected String getEndMultiLineCommentSequence() {
        return "\\*\\/";
    }

    @Override
    protected String getCommentSequence() {
        return "\\-\\-";
    }

    @Override
    protected boolean supportsExtension(String changelogFile) {
        return changelogFile.toLowerCase().endsWith(".sql");
    }

    @Override
    protected void handlePreconditionsCase(ChangeSet changeSet, int count, Matcher preconditionsMatcher) throws ChangeLogParseException {
        if (preconditionsMatcher.groupCount() == 0) {
            String message = String.format("Unexpected formatting at line %d. Formatted %s changelogs require known formats, such as '--preconditions <onFail>|<onError>|<onUpdate>' and others to be recognized and run. Learn all the options at %s", count, getSequenceType(), getSequenceDocumentationLink());
            throw new ChangeLogParseException("\n" + message);
        }
        if (preconditionsMatcher.groupCount() == 1) {
            String body = preconditionsMatcher.group(1);
            Matcher onFailMatcher = ON_FAIL_PATTERN.matcher(body);
            Matcher onErrorMatcher = ON_ERROR_PATTERN.matcher(body);
            Matcher onUpdateSqlMatcher = ON_UPDATE_SQL_PATTERN.matcher(body);
            Matcher onSqlOutputMatcher = ON_SQL_OUTPUT_PATTERN.matcher(body);

            PreconditionContainer pc = new PreconditionContainer();
            pc.setOnFail(StringUtil.trimToNull(parseString(onFailMatcher)));
            pc.setOnError(StringUtil.trimToNull(parseString(onErrorMatcher)));

            if (onSqlOutputMatcher.matches() && onUpdateSqlMatcher.matches()) {
                throw new IllegalArgumentException("Please modify the changelog to have preconditions set with either " +
                        "'onUpdateSql' or 'onSqlOutput', and not with both.");
            }
            if (onSqlOutputMatcher.matches()) {
                pc.setOnSqlOutput(StringUtil.trimToNull(parseString(onSqlOutputMatcher)));
            } else {
                pc.setOnSqlOutput(StringUtil.trimToNull(parseString(onUpdateSqlMatcher)));
            }
            changeSet.setPreconditions(pc);
        }
    }

    @Override
    protected AbstractSQLChange getChange() {
        return new RawSQLChange();
    }

    @Override
    protected String getSequenceDocumentationLink() {
        // TODO Do NoSQL items have different link?
        return "https://docs.liquibase.com/concepts/changelogs/sql-format.html";
    }

    @Override
    protected String getSequenceType() {
        return "SQL";
    }

    @Override
    protected void setChangeSequence(AbstractSQLChange change, String finalCurrentSequence) {
        change.setSql(finalCurrentSequence);
    }

    @Override
    protected boolean isEndDelimiter(AbstractSQLChange change) {
        // TODO Cast to RawSQLChange?
        return (change.getEndDelimiter() == null) && StringUtil.trimToEmpty(change.getSql()).endsWith("\n/");
    }

    @Override
    protected void setChangeSequence(ChangeLogParameters changeLogParameters, StringBuilder currentSequence, ChangeSet changeSet, AbstractSQLChange change) {
        change.setSql(changeLogParameters.expandExpressions(StringUtil.trimToNull(currentSequence.toString()), changeSet.getChangeLog()));
    }
}