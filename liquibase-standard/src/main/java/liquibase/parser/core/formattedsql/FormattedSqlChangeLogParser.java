package liquibase.parser.core.formattedsql;

import liquibase.change.AbstractSQLChange;
import liquibase.change.core.RawSQLChange;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.parser.FormattedChangeLogParser;
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