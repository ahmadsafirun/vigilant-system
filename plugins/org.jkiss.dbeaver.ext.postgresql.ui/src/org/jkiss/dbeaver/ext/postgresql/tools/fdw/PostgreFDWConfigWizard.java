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

import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ext.postgresql.model.PostgreDatabase;
import org.jkiss.dbeaver.ext.postgresql.model.PostgreForeignDataWrapper;
import org.jkiss.dbeaver.ext.postgresql.model.PostgreSchema;
import org.jkiss.dbeaver.ext.postgresql.model.fdw.FDWConfigDescriptor;
import org.jkiss.dbeaver.model.DBPContextProvider;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.navigator.DBNDataSource;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseNode;
import org.jkiss.dbeaver.model.navigator.DBNModel;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSEntity;
import org.jkiss.dbeaver.model.virtual.DBVContainer;
import org.jkiss.dbeaver.model.virtual.DBVEntity;
import org.jkiss.dbeaver.model.virtual.DBVEntityForeignKey;
import org.jkiss.dbeaver.model.virtual.DBVModel;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.runtime.properties.PropertySourceCustom;
import org.jkiss.dbeaver.ui.dialogs.BaseWizard;

import java.util.*;

class PostgreFDWConfigWizard extends BaseWizard implements DBPContextProvider {

    private static final Log log = Log.getLog(PostgreFDWConfigWizard.class);

    private PostgreFDWConfigWizardPageInput inputPage;
    private PostgreFDWConfigWizardPageConfig configPage;
    private PostgreDatabase database;

    private List<DBPDataSourceContainer> availableDataSources = null;
    private List<DBSEntity> proposedEntities = null;
    private List<DBNDatabaseNode> selectedEntities;
    private DBPDataSourceContainer selectedDataSource;
    private PostgreSchema selectedSchema;
    private FDWInfo selectedFDW;
    private String fdwServerId;
    private PropertySourceCustom fdwPropertySource;

    static class FDWInfo {
        PostgreForeignDataWrapper installedFDW;
        FDWConfigDescriptor fdwDescriptor;

        String getId() {
            return installedFDW != null ? installedFDW.getName() : fdwDescriptor.getFdwId();
        }
        String getDescription() {
            return installedFDW != null ? installedFDW.getDescription() : fdwDescriptor.getDescription();
        }
    }

    PostgreFDWConfigWizard(PostgreDatabase database) {
        setWindowTitle("Foreign Data Wrappers configurator");
        this.database = database;
        setNeedsProgressMonitor(true);

        this.fdwPropertySource = new PropertySourceCustom();
    }

    public PostgreDatabase getDatabase() {
        return database;
    }

    public PostgreSchema getSelectedSchema() {
        return selectedSchema;
    }

    public void setSelectedSchema(PostgreSchema selectedSchema) {
        this.selectedSchema = selectedSchema;
    }

    public FDWInfo getSelectedFDW() {
        return selectedFDW;
    }

    public void setSelectedFDW(FDWInfo selectedFDW) {
        this.selectedFDW = selectedFDW;
    }

    public String getFdwServerId() {
        return fdwServerId;
    }

    public void setFdwServerId(String fdwServerId) {
        this.fdwServerId = fdwServerId;
    }

    public PropertySourceCustom getFdwPropertySource() {
        return fdwPropertySource;
    }

    @Override
    public void addPages() {
        inputPage = new PostgreFDWConfigWizardPageInput(this);
        configPage = new PostgreFDWConfigWizardPageConfig(this);
        addPage(inputPage);
        addPage(configPage);
        addPage(new PostgreFDWConfigWizardPageFinal(this));
        super.addPages();
    }

    public List<DBPDataSourceContainer> getAvailableDataSources() {
        return availableDataSources == null ? Collections.emptyList() : availableDataSources;
    }

    public List<DBSEntity> getProposedEntities() {
        return proposedEntities == null ? Collections.emptyList() : proposedEntities;
    }

    public DBPDataSourceContainer getSelectedDataSource() {
        return selectedDataSource;
    }

    public List<DBNDatabaseNode> getSelectedEntities() {
        return selectedEntities == null ? Collections.emptyList() : selectedEntities;
    }

    public void setSelectedEntities(List<DBNDatabaseNode> entities) {
        this.selectedEntities = entities;
        this.selectedDataSource = entities.isEmpty() ? null : entities.get(0).getDataSourceContainer();
    }

    public void addAvailableDataSource(DBPDataSourceContainer dataSource) {
        availableDataSources.add(dataSource);
    }

    public void removeAvailableDataSource(DBPDataSourceContainer dataSource) {
        availableDataSources.remove(dataSource);
    }

    void collectAvailableDataSources(DBRProgressMonitor monitor) {
        if (availableDataSources != null) {
            return;
        }
        Set<DBPDataSourceContainer> dataSources = new LinkedHashSet<>();
        Set<DBSEntity> entities = new LinkedHashSet<>();

        DBPDataSourceContainer curDataSource = database.getDataSource().getContainer();

        // Find all virtual connections
        DBVModel vModel = curDataSource.getVirtualModel();
        monitor.beginTask("Check virtual foreign keys", 1);
        collectAvailableDataSources(monitor, vModel, dataSources, entities);
        monitor.done();

        DBNModel navModel = DBWorkbench.getPlatform().getNavigatorModel();

        // Check global FK references cache
        Map<String, List<DBVEntityForeignKey>> grCache = DBVModel.getGlobalReferenceCache();
        monitor.beginTask("Check external references", grCache.size());
        for (Map.Entry<String, List<DBVEntityForeignKey>> grEntry : grCache.entrySet()) {
            DBNDataSource refDataSource = navModel.getDataSourceByPath(
                database.getDataSource().getContainer().getProject(),
                grEntry.getKey());
            if (refDataSource != null && refDataSource.getDataSourceContainer() == curDataSource) {
                try {
                    for (DBVEntityForeignKey rfk : grEntry.getValue()) {
                        monitor.subTask("Check " + rfk.getEntity().getFullyQualifiedName(DBPEvaluationContext.UI));
                        DBSEntity refEntity = rfk.getEntity().getRealEntity(monitor);
                        if (refEntity != null) {
                            dataSources.add(refEntity.getDataSource().getContainer());
                            entities.add(refEntity);
                        }
                    }
                } catch (DBException e) {
                    log.debug("Error getting referenced entity", e);
                }
            }
            monitor.worked(1);
        }
        monitor.done();

        // Check already configured FDW

        // Done
        availableDataSources = new ArrayList<>(dataSources);
        proposedEntities = new ArrayList<>(entities);
    }

    private void collectAvailableDataSources(DBRProgressMonitor monitor, DBVContainer vContainer, Set<DBPDataSourceContainer> dataSources, Set<DBSEntity> entities) {
        for (DBVContainer childContainer : vContainer.getContainers()) {
            collectAvailableDataSources(monitor, childContainer, dataSources, entities);
        }
        for (DBVEntity vEntity : vContainer.getEntities()) {
            for (DBVEntityForeignKey fk : vEntity.getForeignKeys()) {
                DBPDataSourceContainer dataSource = fk.getAssociatedDataSource();
                if (dataSource != database.getDataSource().getContainer()) {
                    dataSources.add(dataSource);
                    try {
                        entities.add(fk.getAssociatedEntity(monitor));
                    } catch (DBException e) {
                        log.debug("Error getting referenced entity", e);
                    }
                }
            }
        }
    }

    @Override
    public boolean performFinish() {
        return false;
    }

    @Nullable
    @Override
    public DBCExecutionContext getExecutionContext() {
        return DBUtils.getDefaultContext(database, true);
    }
}
