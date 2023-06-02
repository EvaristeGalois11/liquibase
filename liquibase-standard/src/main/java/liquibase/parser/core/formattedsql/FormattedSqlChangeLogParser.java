package liquibase.parser.core.formattedsql;

import liquibase.change.AbstractSQLChange;
import liquibase.change.core.RawSQLChange;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.exception.ChangeLogParseException;
import liquibase.parser.FormattedChangeLogParser;
import liquibase.resource.ResourceAccessor;
import liquibase.util.StringUtil;


public class FormattedSqlChangeLogParser extends FormattedChangeLogParser {

    @Override
    protected String getCommentSequence() {
        return "\\-\\-";
    }

    @Override
    protected boolean supportsExtension(String changelogFile) {
        return changelogFile.toLowerCase().endsWith(".sql");
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