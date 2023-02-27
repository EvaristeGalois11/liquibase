package liquibase.command.core;

import liquibase.*;
import liquibase.changelog.*;
import liquibase.changelog.filter.*;
import liquibase.changelog.visitor.*;
import liquibase.command.*;
import liquibase.command.core.helpers.HubHandler;
import liquibase.database.Database;
import liquibase.database.ObjectQuotingStrategy;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import liquibase.exception.LockException;
import liquibase.executor.Executor;
import liquibase.executor.ExecutorService;
import liquibase.executor.LoggingExecutor;
import liquibase.hub.*;
import liquibase.hub.listener.HubChangeExecListener;
import liquibase.hub.model.Connection;
import liquibase.hub.model.HubChangeLog;
import liquibase.hub.model.Operation;
import liquibase.integration.commandline.ChangeExecListenerUtils;
import liquibase.lockservice.LockService;
import liquibase.lockservice.LockServiceFactory;
import liquibase.logging.core.BufferedLogService;
import liquibase.logging.core.CompositeLogService;
import liquibase.parser.ChangeLogParser;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.parser.core.xml.XMLChangeLogSAXParser;
import liquibase.resource.ResourceAccessor;
import liquibase.util.ShowSummaryUtil;
import liquibase.util.StringUtil;

import java.util.*;

import static liquibase.Liquibase.MSG_COULD_NOT_RELEASE_LOCK;
import static liquibase.command.core.helpers.UpdateHandler.*;

public class UpdateCommandStep extends AbstractCommandStep implements CleanUpCommandStep {

    public static final String[] LEGACY_COMMAND_NAME = {"migrate"};
    public static String[] COMMAND_NAME = {"update"};

    public static final CommandArgumentDefinition<String> CHANGELOG_FILE_ARG;
    public static final CommandArgumentDefinition<String> LABEL_FILTER_ARG;
    public static final CommandArgumentDefinition<String> CONTEXTS_ARG;
    public static final CommandArgumentDefinition<String> CHANGE_EXEC_LISTENER_CLASS_ARG;
    public static final CommandArgumentDefinition<String> CHANGE_EXEC_LISTENER_PROPERTIES_FILE_ARG;
    public static final CommandArgumentDefinition<ChangeExecListener> CHANGE_EXEC_LISTENER_ARG;
    public static final CommandArgumentDefinition<UpdateSummaryEnum> SHOW_SUMMARY;
    public static final CommandArgumentDefinition<ChangeLogParameters> CHANGELOG_PARAMETERS;

    static {
        CommandBuilder builder = new CommandBuilder(COMMAND_NAME, LEGACY_COMMAND_NAME);
        CHANGELOG_FILE_ARG = builder.argument(CommonArgumentNames.CHANGELOG_FILE, String.class)
                .required().description("The root changelog")
                .build();
        LABEL_FILTER_ARG = builder.argument("labelFilter", String.class)
                .addAlias("labels")
                .description("Changeset labels to match")
                .build();
        CONTEXTS_ARG = builder.argument("contexts", String.class)
                .description("Changeset contexts to match")
                .build();
        CHANGE_EXEC_LISTENER_CLASS_ARG = builder.argument("changeExecListenerClass", String.class)
                .description("Fully-qualified class which specifies a ChangeExecListener")
                .build();
        CHANGE_EXEC_LISTENER_PROPERTIES_FILE_ARG = builder.argument("changeExecListenerPropertiesFile", String.class)
                .description("Path to a properties file for the ChangeExecListenerClass")
                .build();
        CHANGE_EXEC_LISTENER_ARG = builder.argument("changeExecListener", ChangeExecListener.class)
                .hidden()
                .build();
        CHANGELOG_PARAMETERS = builder.argument("changelogParameters", ChangeLogParameters.class)
                .hidden()
                .build();
        SHOW_SUMMARY = builder.argument("showSummary", UpdateSummaryEnum.class).description("Type of update results summary to show.  Values can be 'off', 'summary', or 'verbose'.")
                .defaultValue(UpdateSummaryEnum.OFF)
                .hidden()
                .setValueHandler(value -> {
                    if (value == null) {
                        return null;
                    }
                    if (value instanceof String && !value.equals("")) {
                        final List<String> validValues = Arrays.asList("OFF", "SUMMARY", "VERBOSE");
                        if (!validValues.contains(((String) value).toUpperCase())) {
                            throw new IllegalArgumentException("Illegal value for `showUpdateSummary'.  Valid values are 'OFF', 'SUMMARY', or 'VERBOSE'");
                        }
                        return UpdateSummaryEnum.valueOf(((String) value).toUpperCase());
                    } else if (value instanceof UpdateSummaryEnum) {
                        return (UpdateSummaryEnum) value;
                    }
                    return null;
                }).build();
    }

    @Override
    public String[][] defineCommandNames() {
        return new String[][]{COMMAND_NAME, LEGACY_COMMAND_NAME};
    }

    @Override
    public void adjustCommandDefinition(CommandDefinition commandDefinition) {
        commandDefinition.setShortDescription("Deploy any changes in the changelog file that have not been deployed");

        if (commandDefinition.is(LEGACY_COMMAND_NAME)) {
            commandDefinition.setHidden(true);
        }

    }

    @Override
    public List<Class<?>> requiredDependencies() {
        return Arrays.asList(Database.class, LockService.class);
    }

    @Override
    public List<Class<?>> providedDependencies() {
        return Arrays.asList(ChangeExecListener.class, Exception.class);
    }

    @Override
    public void run(CommandResultsBuilder resultsBuilder) throws Exception {
        CommandScope commandScope = resultsBuilder.getCommandScope();
        String changeLogFile = commandScope.getArgumentValue(CHANGELOG_FILE_ARG);
        Database database = (Database) commandScope.getDependency(Database.class);
        Contexts contexts = new Contexts(commandScope.getArgumentValue(CONTEXTS_ARG));
        LabelExpression labelExpression = new LabelExpression(commandScope.getArgumentValue(LABEL_FILTER_ARG));
        ChangeLogParameters changeLogParameters = commandScope.getArgumentValue(CHANGELOG_PARAMETERS);
        if (changeLogParameters == null) {
            changeLogParameters = new ChangeLogParameters(database);
        }
        DatabaseChangeLog databaseChangeLog = getDatabaseChangeLog(changeLogFile, changeLogParameters, true);
        if (isUpToDate(database, databaseChangeLog, contexts, labelExpression)) {
            return;
        }
        LockService lockService = LockServiceFactory.getInstance().getLockService(database);
        lockService.waitForLock();

        changeLogParameters.setContexts(contexts);
        changeLogParameters.setLabels(labelExpression);

        BufferedLogService bufferLog = new BufferedLogService();
        HubHandler hubHandler = null;
        try {
            checkLiquibaseTables(database, true, databaseChangeLog, contexts, labelExpression);
            ChangeLogHistoryService changelogService = ChangeLogHistoryServiceFactory.getInstance().getChangeLogService(database);
            changelogService.generateDeploymentId();
            databaseChangeLog.validate(database, contexts, labelExpression);

            //Set up a "chain" of ChangeExecListeners. Starting with the custom change exec listener
            //then wrapping that in the DefaultChangeExecListener.
            ChangeExecListener listener = ChangeExecListenerUtils.getChangeExecListener(database,
                    Scope.getCurrentScope().getResourceAccessor(),
                    commandScope.getArgumentValue(CHANGE_EXEC_LISTENER_CLASS_ARG),
                    commandScope.getArgumentValue(CHANGE_EXEC_LISTENER_PROPERTIES_FILE_ARG));
            DefaultChangeExecListener defaultChangeExecListener = new DefaultChangeExecListener(listener);
            hubHandler = new HubHandler(database, databaseChangeLog, changeLogFile, defaultChangeExecListener);

            ChangeLogIterator changeLogIterator = getStandardChangelogIterator(database, contexts, labelExpression, databaseChangeLog);
            StatusVisitor statusVisitor = new StatusVisitor(database);
            ChangeLogIterator shouldRunIterator = getStandardChangelogIterator(database, contexts, labelExpression, databaseChangeLog);
            shouldRunIterator.run(statusVisitor, new RuntimeEnvironment(database, contexts, labelExpression));

            //Remember we built our hubHandler with our DefaultChangeExecListener so this HubChangeExecListener is delegating to them.
            ChangeExecListener hubChangeExecListener = hubHandler.startHubForUpdate(changeLogParameters, changeLogIterator);

            commandScope.provideDependency(ChangeExecListener.class, defaultChangeExecListener);
            ChangeLogIterator runChangeLogIterator = getStandardChangelogIterator(database, contexts, labelExpression, databaseChangeLog);
            CompositeLogService compositeLogService = new CompositeLogService(true, bufferLog);
            HashMap<String, Object> scopeValues = new HashMap<>();
            scopeValues.put(Scope.Attr.logService.name(), compositeLogService);
            scopeValues.put("showSummary", commandScope.getArgumentValue(SHOW_SUMMARY));
            Scope.child(scopeValues, () -> {
                //If we are using hub, we want to use the HubChangeExecListener, which is wrapping all the others. Otherwise, use the default.
                ChangeExecListener listenerToUse = hubChangeExecListener != null ? hubChangeExecListener : defaultChangeExecListener;
                runChangeLogIterator.run(new UpdateVisitor(database, listenerToUse), new RuntimeEnvironment(database, contexts, labelExpression));
                ShowSummaryUtil.showUpdateSummary(databaseChangeLog, statusVisitor);
            });

            hubHandler.postUpdateHub(bufferLog);
            resultsBuilder.addResult("statusCode", 0);
        } catch (Exception e) {
            resultsBuilder.addResult("statusCode", 1);
            if (hubHandler != null) {
                hubHandler.postUpdateHubExceptionHandling(bufferLog, e.getMessage());
            }
            commandScope.provideDependency(Exception.class, e);
        } finally {
            try {
                lockService.releaseLock();
            } catch (LockException e) {
                Scope.getCurrentScope().getLog(ChangelogSyncCommandStep.class).severe(MSG_COULD_NOT_RELEASE_LOCK, e);
            }
        }
    }

    @Override
    public void cleanUp(CommandResultsBuilder resultsBuilder) throws Exception {
        LockServiceFactory.getInstance().resetAll();
        ChangeLogHistoryServiceFactory.getInstance().resetAll();
        Scope.getCurrentScope().getSingleton(ExecutorService.class).reset();
        if (resultsBuilder.getCommandScope().getDependency(Exception.class) != null) {
            throw (Exception) resultsBuilder.getCommandScope().getDependency(Exception.class);
        }
    }
}
