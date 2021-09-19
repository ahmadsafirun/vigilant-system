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
package org.jkiss.dbeaver.ext.postgresql.tools.fdw;

import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.postgresql.PostgreConstants;
import org.jkiss.dbeaver.ext.postgresql.edit.PostgreForeignTableManager;
import org.jkiss.dbeaver.ext.postgresql.edit.PostgreTableColumnManager;
import org.jkiss.dbeaver.ext.postgresql.model.*;
import org.jkiss.dbeaver.model.DBPScriptObject;
import org.jkiss.dbeaver.model.edit.DBECommand;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.impl.edit.SQLDatabasePersistAction;
import org.jkiss.dbeaver.model.impl.edit.SQLDatabasePersistActionComment;
import org.jkiss.dbeaver.model.impl.sql.edit.SQLObjectEditor;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseNode;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.sql.SQLUtils;
import org.jkiss.dbeaver.model.struct.DBSEntity;
import org.jkiss.dbeaver.model.struct.DBSEntityAttribute;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.runtime.properties.PropertySourceCustom;
import org.jkiss.dbeaver.runtime.ui.UIServiceSQL;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.dialogs.ActiveWizardPage;
import org.jkiss.dbeaver.ui.editors.SimpleCommandContext;
import org.jkiss.utils.CommonUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.*;


class PostgreFDWConfigWizardPageFinal extends ActiveWizardPage<PostgreFDWConfigWizard> {

    private static final Log log = Log.getLog(PostgreFDWConfigWizardPageFinal.class);
    private boolean activated;
    private Object sqlPanel;

    protected PostgreFDWConfigWizardPageFinal(PostgreFDWConfigWizard wizard)
    {
        super("Script");
        setTitle("Foreign wrappers mapping SQL script");
        setDescription("Preview script and perform install");
        setWizard(wizard);
    }

    @Override
    public boolean isPageComplete()
    {
        return activated && getErrorMessage() == null;
    }

    @Override
    public void createControl(Composite parent)
    {
        Composite composite = UIUtils.createComposite(parent, 1);

        {
            Group settingsGroup = UIUtils.createControlGroup(composite, "Script", 2, GridData.FILL_BOTH, 0);
            settingsGroup.setLayout(new FillLayout());
            UIServiceSQL service = DBWorkbench.getService(UIServiceSQL.class);
            if (service != null) {
                try {
                    sqlPanel = service.createSQLPanel(
                        UIUtils.getActiveWorkbenchWindow().getActivePage().getActivePart().getSite(),
                        settingsGroup,
                        getWizard(),
                        "FDW Script",
                        true,
                        "");
                } catch (DBException e) {
                    log.debug(e);
                    setErrorMessage(e.getMessage());
                }
            }
        }


        setControl(composite);
    }

    @Override
    public void activatePage() {
        if (!activated) {
            activated = true;
        }
        generateScript();
        super.activatePage();
    }

    private void generateScript() {
        StringBuilder script = new StringBuilder();
        try {
            getWizard().getRunnableContext().run(false, true, monitor -> {
                try {
                    List<DBEPersistAction> actions = generateScript(monitor);
                    script.append(
                        SQLUtils.generateScript(
                            getWizard().getDatabase().getDataSource(),
                            actions.toArray(new DBEPersistAction[0]),
                            false));
                } catch (DBException e) {
                    throw new InvocationTargetException(e);
                }
            });
        } catch (InvocationTargetException e) {
            log.debug(e.getTargetException());
            setErrorMessage(e.getTargetException().getMessage());
            return;
        } catch (InterruptedException e) {
            return;
        }
        setErrorMessage(null);

        String sql = script.toString();

        UIServiceSQL service = DBWorkbench.getService(UIServiceSQL.class);
        if (service != null) {
            service.setSQLPanelText(sqlPanel, sql);
        }
    }

    private List<DBEPersistAction> generateScript(DBRProgressMonitor monitor) throws DBException {
        PostgreDatabase database = getWizard().getDatabase();
        PostgreDataSource curDataSource = database.getDataSource();
        List<DBEPersistAction> actions = new ArrayList<>();

        PostgreFDWConfigWizard.FDWInfo selectedFDW = getWizard().getSelectedFDW();
        PropertySourceCustom propertySource = getWizard().getFdwPropertySource();
        Map<Object, Object> propValues = propertySource.getPropertiesWithDefaults();

        String serverId = getWizard().getFdwServerId();

        actions.add(new SQLDatabasePersistActionComment(curDataSource, "CREATE EXTENSION " + selectedFDW.getId()));
        {
            StringBuilder script = new StringBuilder();
            script.append("CREATE SERVER ").append(serverId)
                .append("\n\tFOREIGN DATA WRAPPER ").append(selectedFDW.getId())
                .append("\n\tOPTIONS(");
            boolean firstProp = true;
            for (Map.Entry<Object, Object> pe : propValues.entrySet()) {
                String propName = CommonUtils.toString(pe.getKey());
                String propValue = CommonUtils.toString(pe.getValue());
                if (CommonUtils.isEmpty(propName) || CommonUtils.isEmpty(propValue)) {
                    continue;
                }
                if (!firstProp) script.append(", ");
                script.append(propName).append(" '").append(propValue).append("'");
                firstProp = false;
            }
            script
                .append(")");
            actions.add(new SQLDatabasePersistAction("Create extension", script.toString()));
        }

        actions.add(new SQLDatabasePersistAction("CREATE USER MAPPING FOR CURRENT_USER SERVER " + serverId));

        // Now tables
        DBECommandContext commandContext = new SimpleCommandContext(getWizard().getExecutionContext(), false);

        try {
            PostgreFDWConfigWizard.FDWInfo fdwInfo = getWizard().getSelectedFDW();
            Map<String, Object> options = new HashMap<>();
            options.put(SQLObjectEditor.OPTION_SKIP_CONFIGURATION, true);
            PostgreForeignTableManager tableManager = new PostgreForeignTableManager();
            PostgreTableColumnManager columnManager = new PostgreTableColumnManager();

            for (DBNDatabaseNode tableNode : getWizard ().getSelectedEntities()) {
                DBSEntity entity = (DBSEntity) tableNode.getObject();

                PostgreTableForeign pgTable = (PostgreTableForeign) tableManager.createNewObject(monitor, commandContext, getWizard().getSelectedSchema(), null, options);
                if (pgTable == null) {
                    log.error("Internal error while creating new table");
                    continue;
                }
                pgTable.setName(entity.getName());
                pgTable.setForeignServerName(fdwInfo.getId());
                pgTable.setForeignOptions(new String[0]);

                for (DBSEntityAttribute attr : CommonUtils.safeCollection(entity.getAttributes(monitor))) {
                    PostgreTableColumn newColumn = columnManager.createNewObject(monitor, commandContext, pgTable, null, options);
                    assert newColumn != null;
                    newColumn.setName(attr.getName());
                    String defTypeName = database.getDefaultDataTypeName(attr.getDataKind());
                    PostgreDataType dataType = database.getDataType(monitor, defTypeName);
                    newColumn.setDataType(dataType);
                }

                DBEPersistAction[] tableDDL = tableManager.getTableDDL(monitor, pgTable, options);
                Collections.addAll(actions, tableDDL);
            }
        } finally {
            commandContext.resetChanges(true);
        }

        //CREATE SERVER clickhouse_svr FOREIGN DATA WRAPPER clickhousedb_fdw OPTIONS(dbname 'default', driver '/usr/local/lib/odbc/libclickhouseodbc.so', host '46.101.202.143');
        return actions;
    }

}
