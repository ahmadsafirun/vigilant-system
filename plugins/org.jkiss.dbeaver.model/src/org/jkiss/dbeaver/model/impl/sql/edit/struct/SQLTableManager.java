/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2019 Serge Rider (serge@jkiss.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.model.impl.sql.edit.struct;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.*;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.edit.DBERegistry;
import org.jkiss.dbeaver.model.impl.DBObjectNameCaseTransformer;
import org.jkiss.dbeaver.model.impl.edit.SQLDatabasePersistAction;
import org.jkiss.dbeaver.model.impl.edit.SQLDatabasePersistActionComment;
import org.jkiss.dbeaver.model.impl.sql.edit.SQLObjectEditor;
import org.jkiss.dbeaver.model.impl.sql.edit.SQLStructEditor;
import org.jkiss.dbeaver.model.messages.ModelMessages;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.sql.SQLDataSource;
import org.jkiss.dbeaver.model.sql.SQLUtils;
import org.jkiss.dbeaver.model.struct.DBSEntityAssociation;
import org.jkiss.dbeaver.model.struct.DBSEntityAttribute;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectContainer;
import org.jkiss.dbeaver.model.struct.rdb.DBSTable;
import org.jkiss.dbeaver.model.struct.rdb.DBSTableConstraint;
import org.jkiss.dbeaver.model.struct.rdb.DBSTableForeignKey;
import org.jkiss.dbeaver.model.struct.rdb.DBSTableIndex;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.utils.CommonUtils;

import java.util.*;

/**
 * JDBC table manager
 */
public abstract class SQLTableManager<OBJECT_TYPE extends DBSTable, CONTAINER_TYPE extends DBSObjectContainer>
    extends SQLStructEditor<OBJECT_TYPE, CONTAINER_TYPE>
{

    protected static final String BASE_TABLE_NAME = "NewTable"; //$NON-NLS-1$
    protected static final String BASE_VIEW_NAME = "NewView"; //$NON-NLS-1$

    @Override
    public long getMakerOptions(DBPDataSource dataSource)
    {
        long options = FEATURE_EDITOR_ON_CREATE;
        if (dataSource instanceof SQLDataSource) {
            if (((SQLDataSource) dataSource).getSQLDialect().supportsTableDropCascade()) {
                options |= FEATURE_DELETE_CASCADE;
            }
        }
        return options;
    }

    @Override
    protected final void addObjectCreateActions(DBRProgressMonitor monitor, List<DBEPersistAction> actions, ObjectCreateCommand objectChangeCommand, Map<String, Object> options)
    {
        throw new IllegalStateException("addObjectCreateActions should never be called in struct editor");
    }
    
    protected String beginCreateTableStatement(OBJECT_TYPE table, String tableName) {
        return "CREATE " + getCreateTableType(table) + " " + tableName +
                " (" + GeneralUtils.getDefaultLineSeparator() //$NON-NLS-1$ //$NON-NLS-2$
                ;
    }
    
    protected boolean hasAttrDeclarations() {
        return true;
    }

    @Override
    protected void addStructObjectCreateActions(DBRProgressMonitor monitor, List<DBEPersistAction> actions, StructCreateCommand command, Map<String, Object> options) throws DBException {
        final OBJECT_TYPE table = command.getObject();
        final NestedObjectCommand tableProps = command.getObjectCommands().get(table);
        if (tableProps == null) {
            log.warn("Object change command not found"); //$NON-NLS-1$
            return;
        }
        final String tableName = CommonUtils.getOption(options, DBPScriptObject.OPTION_FULLY_QUALIFIED_NAMES, true) ?
            table.getFullyQualifiedName(DBPEvaluationContext.DDL) : DBUtils.getQuotedIdentifier(table);

        final String slComment = SQLUtils.getDialectFromObject(table).getSingleLineComments()[0];
        final String lineSeparator = GeneralUtils.getDefaultLineSeparator();
        StringBuilder createQuery = new StringBuilder(100);
        createQuery.append(beginCreateTableStatement(table,tableName));
        boolean hasNestedDeclarations = false;
        final Collection<NestedObjectCommand> orderedCommands = getNestedOrderedCommands(command);
        for (NestedObjectCommand nestedCommand : orderedCommands) {
            if (nestedCommand.getObject() == table) {
                continue;
            }
            if (excludeFromDDL(nestedCommand, orderedCommands)) {
                continue;
            }
            final String nestedDeclaration = nestedCommand.getNestedDeclaration(monitor, table, options);
            if (!CommonUtils.isEmpty(nestedDeclaration)) {
                // Insert nested declaration
                if (hasNestedDeclarations) {
                    // Check for embedded comment
                    int lastLFPos = createQuery.lastIndexOf(lineSeparator);
                    int lastCommentPos = createQuery.lastIndexOf(slComment);
                    if (lastCommentPos != -1) {
                        while (lastCommentPos > 0 && Character.isWhitespace(createQuery.charAt(lastCommentPos - 1))) {
                            lastCommentPos--;
                        }
                    }
                    if (lastCommentPos < 0 || lastCommentPos < lastLFPos) {
                          createQuery.append(","); //$NON-NLS-1$
                    } else {
                           createQuery.insert(lastCommentPos, ","); //$NON-NLS-1$
                    }
                    createQuery.append(lineSeparator); //$NON-NLS-1$
                }
                if (!hasNestedDeclarations && !hasAttrDeclarations()) {
                    createQuery.append("(\n\t").append(nestedDeclaration); //$NON-NLS-1$  
                } else {
                 createQuery.append("\t").append(nestedDeclaration); //$NON-NLS-1$
                }
                hasNestedDeclarations = true;
            } else {
                // This command should be executed separately
                final DBEPersistAction[] nestedActions = nestedCommand.getPersistActions(monitor, options);
                if (nestedActions != null) {
                    Collections.addAll(actions, nestedActions);
                }
            }
        }

        createQuery.append(lineSeparator); //$NON-NLS-1$
        if (hasAttrDeclarations() || hasNestedDeclarations) createQuery.append(")"); //$NON-NLS-1$
        appendTableModifiers(monitor, table, tableProps, createQuery, false);

        actions.add( 0, new SQLDatabasePersistAction(ModelMessages.model_jdbc_create_new_table, createQuery.toString()) );
    }

    protected String getCreateTableType(OBJECT_TYPE table) {
        return table.isView() ? "VIEW" : "TABLE";
    }

    protected boolean excludeFromDDL(NestedObjectCommand command, Collection<NestedObjectCommand> orderedCommands) {
        return false;
    }

    @Override
    protected void addObjectDeleteActions(List<DBEPersistAction> actions, ObjectDeleteCommand command, Map<String, Object> options)
    {
        OBJECT_TYPE object = command.getObject();
        final String tableName = CommonUtils.getOption(options, DBPScriptObject.OPTION_FULLY_QUALIFIED_NAMES, true) ?
            object.getFullyQualifiedName(DBPEvaluationContext.DDL) : DBUtils.getQuotedIdentifier(object);
        actions.add(
            new SQLDatabasePersistAction(
                ModelMessages.model_jdbc_drop_table,
                "DROP " + getCreateTableType(object) +  //$NON-NLS-2$
                " " + tableName + //$NON-NLS-2$
                (!object.isView() && CommonUtils.getOption(options, OPTION_DELETE_CASCADE) ? " CASCADE" : "") //$NON-NLS-2$
            )
        );
    }

    protected void appendTableModifiers(DBRProgressMonitor monitor, OBJECT_TYPE table, NestedObjectCommand tableProps, StringBuilder ddl, boolean alter)
    {

    }

    protected void setTableName(DBRProgressMonitor monitor, CONTAINER_TYPE container, OBJECT_TYPE table) throws DBException {
        if (table instanceof DBPNamedObject2) {
            ((DBPNamedObject2)table).setName(getNewChildName(monitor, container));
        }
    }

    protected String getNewChildName(DBRProgressMonitor monitor, CONTAINER_TYPE container) throws DBException {
        return getNewChildName(monitor, container, BASE_TABLE_NAME);
    }

    protected String getNewChildName(DBRProgressMonitor monitor, CONTAINER_TYPE container, String baseName) throws DBException {
        for (int i = 0; ; i++) {
            String tableName = DBObjectNameCaseTransformer.transformName(container.getDataSource(), i == 0 ? baseName : (baseName + "_" + i));
            DBSObject child = container.getChild(monitor, tableName);
            if (child == null) {
                return tableName;
            }
        }
    }

    public DBEPersistAction[] getTableDDL(DBRProgressMonitor monitor, OBJECT_TYPE table, Map<String, Object> options) throws DBException
    {
        List<DBEPersistAction> actions = new ArrayList<>();

        final DBERegistry editorsRegistry = table.getDataSource().getContainer().getPlatform().getEditorsRegistry();
        SQLObjectEditor<DBSEntityAttribute, OBJECT_TYPE> tcm = getObjectEditor(editorsRegistry, DBSEntityAttribute.class);
        SQLObjectEditor<DBSTableConstraint, OBJECT_TYPE> pkm = getObjectEditor(editorsRegistry, DBSTableConstraint.class);
        SQLObjectEditor<DBSTableForeignKey, OBJECT_TYPE> fkm = getObjectEditor(editorsRegistry, DBSTableForeignKey.class);
        SQLObjectEditor<DBSTableIndex, OBJECT_TYPE> im = getObjectEditor(editorsRegistry, DBSTableIndex.class);

        if (CommonUtils.getOption(options, DBPScriptObject.OPTION_DDL_ONLY_FOREIGN_KEYS)) {
            if (fkm != null) {
                // Create only foreign keys
                try {
                    for (DBSEntityAssociation foreignKey : CommonUtils.safeCollection(table.getAssociations(monitor))) {
                        if (!(foreignKey instanceof DBSTableForeignKey) ||
                            DBUtils.isHiddenObject(foreignKey) ||
                            DBUtils.isInheritedObject(foreignKey)) {
                            continue;
                        }
                        DBEPersistAction[] cmdActions = fkm.makeCreateCommand((DBSTableForeignKey) foreignKey, options).getPersistActions(monitor, options);
                        if (cmdActions != null) {
                            Collections.addAll(actions, cmdActions);
                        }
                    }
                } catch (DBException e) {
                    // Ignore primary keys
                    log.debug(e);
                }
            }
            return actions.toArray(new DBEPersistAction[0]);
        }

        if (isIncludeDropInDDL()) {
            actions.add(new SQLDatabasePersistActionComment(table.getDataSource(), "Drop table"));
            for (DBEPersistAction delAction : new ObjectDeleteCommand(table, ModelMessages.model_jdbc_delete_object).getPersistActions(monitor, options)) {
                String script = delAction.getScript();
                String delimiter = SQLUtils.getScriptLineDelimiter(SQLUtils.getDialectFromObject(table));
                if (!script.endsWith(delimiter)) {
                    script += delimiter;
                }
                actions.add(
                    new SQLDatabasePersistActionComment(
                        table.getDataSource(),
                        script));
            }
        }

        StructCreateCommand command = makeCreateCommand(table, options);
        if (tcm != null) {
            // Aggregate nested column, constraint and index commands
            for (DBSEntityAttribute column : CommonUtils.safeCollection(table.getAttributes(monitor))) {
                if (DBUtils.isHiddenObject(column) || DBUtils.isInheritedObject(column)) {
                    // Do not include hidden (pseudo?) and inherited columns in DDL
                    continue;
                }
                command.aggregateCommand(tcm.makeCreateCommand(column, options));
            }
        }
        if (pkm != null) {
            try {
                for (DBSTableConstraint constraint : CommonUtils.safeCollection(table.getConstraints(monitor))) {
                    if (DBUtils.isHiddenObject(constraint) || DBUtils.isInheritedObject(constraint)) {
                        continue;
                    }
                    command.aggregateCommand(pkm.makeCreateCommand(constraint, options));
                }
            } catch (DBException e) {
                // Ignore primary keys
                log.debug(e);
            }
        }
        if (fkm != null && !CommonUtils.getOption(options, DBPScriptObject.OPTION_DDL_SKIP_FOREIGN_KEYS)) {
            try {
                for (DBSEntityAssociation foreignKey : CommonUtils.safeCollection(table.getAssociations(monitor))) {
                    if (!(foreignKey instanceof DBSTableForeignKey) ||
                        DBUtils.isHiddenObject(foreignKey) ||
                        DBUtils.isInheritedObject(foreignKey))
                    {
                        continue;
                    }
                    command.aggregateCommand(fkm.makeCreateCommand((DBSTableForeignKey) foreignKey, options));
                }
            } catch (DBException e) {
                // Ignore primary keys
                log.debug(e);
            }
        }
        if (im != null) {
            try {
                for (DBSTableIndex index : CommonUtils.safeCollection(table.getIndexes(monitor))) {
                    if (!isIncludeIndexInDDL(index)) {
                        continue;
                    }
                    command.aggregateCommand(im.makeCreateCommand(index, options));
                }
            } catch (DBException e) {
                // Ignore indexes
                log.debug(e);
            }
        }
        addExtraDDLCommands(monitor, table, options, command);
        Collections.addAll(actions, command.getPersistActions(monitor, options));

        return actions.toArray(new DBEPersistAction[actions.size()]);
    }

    protected void addExtraDDLCommands(DBRProgressMonitor monitor, OBJECT_TYPE table, Map<String, Object> options, StructCreateCommand createCommand) {

    }

    protected boolean isIncludeIndexInDDL(DBSTableIndex index) {
        return !DBUtils.isHiddenObject(index) && !DBUtils.isInheritedObject(index);
    }

    protected boolean isIncludeDropInDDL() {
        return true;
    }

}

