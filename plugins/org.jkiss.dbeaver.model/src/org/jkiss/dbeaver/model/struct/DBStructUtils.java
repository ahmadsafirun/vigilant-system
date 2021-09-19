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
package org.jkiss.dbeaver.model.struct;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBPScriptObject;
import org.jkiss.dbeaver.model.DBPScriptObjectExt2;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.edit.DBERegistry;
import org.jkiss.dbeaver.model.impl.sql.edit.SQLObjectEditor;
import org.jkiss.dbeaver.model.impl.sql.edit.struct.SQLTableManager;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.sql.SQLUtils;
import org.jkiss.dbeaver.model.struct.rdb.DBSTable;
import org.jkiss.dbeaver.model.struct.rdb.DBSView;
import org.jkiss.utils.CommonUtils;

import java.util.*;

/**
 * DBUtils
 */
public final class DBStructUtils {

    private static final Log log = Log.getLog(DBStructUtils.class);

    public static String generateTableDDL(@NotNull DBRProgressMonitor monitor, @NotNull DBSTable table, Map<String, Object> options, boolean addComments) throws DBException {
        final DBERegistry editorsRegistry = table.getDataSource().getContainer().getPlatform().getEditorsRegistry();
        final SQLObjectEditor entityEditor = editorsRegistry.getObjectManager(table.getClass(), SQLObjectEditor.class);
        if (entityEditor instanceof SQLTableManager) {
            DBEPersistAction[] ddlActions = ((SQLTableManager) entityEditor).getTableDDL(monitor, table, options);
            return SQLUtils.generateScript(table.getDataSource(), ddlActions, addComments);
        }
        log.debug("Table editor not found for " + table.getClass().getName());
        return SQLUtils.generateCommentLine(table.getDataSource(), "Can't generate DDL: table editor not found for " + table.getClass().getName());
    }

    public static String generateObjectDDL(@NotNull DBRProgressMonitor monitor, @NotNull DBSObject object, Map<String, Object> options, boolean addComments) throws DBException {
        final DBERegistry editorsRegistry = object.getDataSource().getContainer().getPlatform().getEditorsRegistry();
        final SQLObjectEditor entityEditor = editorsRegistry.getObjectManager(object.getClass(), SQLObjectEditor.class);
        if (entityEditor != null) {
            SQLObjectEditor.ObjectCreateCommand createCommand = entityEditor.makeCreateCommand(object, options);
            DBEPersistAction[] ddlActions = createCommand.getPersistActions(monitor, options);

            return SQLUtils.generateScript(object.getDataSource(), ddlActions, addComments);
        }
        log.debug("Object editor not found for " + object.getClass().getName());
        return SQLUtils.generateCommentLine(object.getDataSource(), "Can't generate DDL: object editor not found for " + object.getClass().getName());
    }

    public static String getTableDDL(@NotNull DBRProgressMonitor monitor, @NotNull DBSTable table, Map<String, Object> options, boolean addComments) throws DBException {
        if (table instanceof DBPScriptObject) {
            String definitionText = ((DBPScriptObject) table).getObjectDefinitionText(monitor, options);
            if (!CommonUtils.isEmpty(definitionText)) {
                return definitionText;
            }
        }
        return generateTableDDL(monitor, table, options, addComments);
    }

    public static <T extends DBSTable> void generateTableListDDL(@NotNull DBRProgressMonitor monitor, @NotNull StringBuilder sql, @NotNull Collection<T> tablesOrViews, Map<String, Object> options, boolean addComments) throws DBException {
        List<T> goodTableList = new ArrayList<>();
        List<T> cycleTableList = new ArrayList<>();
        List<T> viewList = new ArrayList<>();

        DBStructUtils.sortTableList(monitor, tablesOrViews, goodTableList, cycleTableList, viewList);

        // Good tables: generate full DDL
        for (T table : goodTableList) {
            addDDLLine(sql, DBStructUtils.getTableDDL(monitor, table, options, addComments));
        }
        {
            // Cycle tables: generate CREATE TABLE and CREATE FOREIGN KEY separately
            // This doesn't work if table implementation doesn't support DDL restructure
            List<T> goodCycleTableList = new ArrayList<>();
            for (T table : cycleTableList) {
                if (
                    table instanceof DBPScriptObjectExt2 &&
                    ((DBPScriptObjectExt2) table).supportsObjectDefinitionOption(DBPScriptObject.OPTION_DDL_SKIP_FOREIGN_KEYS) &&
                    ((DBPScriptObjectExt2) table).supportsObjectDefinitionOption(DBPScriptObject.OPTION_DDL_ONLY_FOREIGN_KEYS))
                {
                    goodCycleTableList.add(table);
                }
            }
            cycleTableList.removeAll(goodCycleTableList);

            Map<String, Object> optionsNoFK = new HashMap<>(options);
            optionsNoFK.put(DBPScriptObject.OPTION_DDL_SKIP_FOREIGN_KEYS, true);
            for (T table : goodCycleTableList) {
                addDDLLine(sql, DBStructUtils.getTableDDL(monitor, table, optionsNoFK, addComments));
            }
            Map<String, Object> optionsOnlyFK = new HashMap<>(options);
            optionsOnlyFK.put(DBPScriptObject.OPTION_DDL_ONLY_FOREIGN_KEYS, true);
            for (T table : goodCycleTableList) {
                addDDLLine(sql, DBStructUtils.getTableDDL(monitor, table, optionsOnlyFK, addComments));
            }

            // the rest - tables which can't split their DDL
            for (T table : cycleTableList) {
                addDDLLine(sql, DBStructUtils.getTableDDL(monitor, table, options, addComments));
            }
        }
        // Views: generate them after all tables.
        // TODO: find view dependencies and generate them in right order
        for (T table : viewList) {
            addDDLLine(sql, DBStructUtils.getTableDDL(monitor, table, options, addComments));
        }
        monitor.done();
    }

    private static void addDDLLine(StringBuilder sql, String ddl) {
        if (!CommonUtils.isEmpty(ddl)) {
            sql.append("\n").append(ddl);
        }
    }

    public static <T extends DBSEntity> void sortTableList(DBRProgressMonitor monitor, Collection<T> input, List<T> simpleTables, List<T> cyclicTables, List<T> views) throws DBException {
        List<T> realTables = new ArrayList<>();
        for (T entity : input) {
            if (entity instanceof DBSView || (entity instanceof DBSTable && ((DBSTable) entity).isView())) {
                views.add(entity);
            } else {
                realTables.add(entity);
            }
        }

        // 1. Get tables without FKs
        for (Iterator<T> iterator = realTables.iterator(); iterator.hasNext(); ) {
            T table = iterator.next();
            try {
                if (CommonUtils.isEmpty(table.getAssociations(monitor))) {
                    simpleTables.add(table);
                    iterator.remove();
                }
            } catch (DBException e) {
                log.debug(e);
            }
        }

        // 2. Get tables referring tables from p.1 only
        // 3. Repeat p.2 until something is found
        boolean refsFound = true;
        while (refsFound) {
            refsFound = false;
            for (Iterator<T> iterator = realTables.iterator(); iterator.hasNext(); ) {
                T table = iterator.next();
                try {
                    boolean allGood = true;
                    for (DBSEntityAssociation ref : CommonUtils.safeCollection(table.getAssociations(monitor))) {
                        DBSEntity refEntity = ref.getAssociatedEntity();
                        if (refEntity == null || (!simpleTables.contains(refEntity) && refEntity != table)) {
                            allGood = false;
                            break;
                        }
                    }
                    if (allGood) {
                        simpleTables.add(table);
                        iterator.remove();
                        refsFound = true;
                    }
                } catch (DBException e) {
                    log.error(e);
                }
            }
        };

        // 4. The rest is cycled tables
        cyclicTables.addAll(realTables);
    }

}
