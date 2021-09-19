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
package org.jkiss.dbeaver.ext.hana.model;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.IAdaptable;
import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ModelPreferences;
import org.jkiss.dbeaver.ext.generic.model.GenericDataSource;
import org.jkiss.dbeaver.ext.generic.model.meta.GenericMetaModel;
import org.jkiss.dbeaver.ext.hana.model.plan.HANAPlanAnalyser;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBPDataSourceInfo;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.connection.DBPDriver;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCDatabaseMetaData;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.exec.plan.DBCPlan;
import org.jkiss.dbeaver.model.exec.plan.DBCPlanStyle;
import org.jkiss.dbeaver.model.exec.plan.DBCQueryPlanner;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSStructureAssistant;

public class HANADataSource extends GenericDataSource implements DBCQueryPlanner, IAdaptable {

    private static final Log log = Log.getLog(HANADataSource.class);
    private static final String PROP_APPLICATION_NAME = "SESSIONVARIABLE:APPLICATION";
    private static final String PROP_READONLY = "READONLY";
    

    private HashMap<String, String> sysViewColumnUnits; 
    
    public HANADataSource(DBRProgressMonitor monitor, DBPDataSourceContainer container, GenericMetaModel metaModel)
        throws DBException
    {
        super(monitor, container, metaModel, new HANASQLDialect());
    }

    @Override
    protected DBPDataSourceInfo createDataSourceInfo(DBRProgressMonitor monitor, @NotNull JDBCDatabaseMetaData metaData)
    {
        final HANADataSourceInfo info = new HANADataSourceInfo(metaData);
        return info;
    }
    
    /*
     * search
     */
    @Override
    public <T> T getAdapter(Class<T> adapter) {
        if (adapter == DBSStructureAssistant.class)
            return adapter.cast(new HANAStructureAssistant(this));
        return super.getAdapter(adapter);
    }
    
    /*
     * explain
     */
    @Override
    public DBCPlan planQueryExecution(@NotNull DBCSession session, @NotNull String query)
    throws DBCException {
        HANAPlanAnalyser plan = new HANAPlanAnalyser(this, query);
        plan.explain(session);
        return plan;
    }

    @Override
    public DBCPlanStyle getPlanStyle() {
        return DBCPlanStyle.PLAN;
    }
  
    /*
     * application
     */
    @Override
    protected boolean isPopulateClientAppName() { 
        return false; // basically true, but different property name 
    } 

    @Override
    protected Map<String, String> getInternalConnectionProperties(DBRProgressMonitor monitor, DBPDriver driver, String purpose, DBPConnectionConfiguration connectionInfo) throws DBCException {
        Map<String, String> props = new HashMap<>();
        if (!getContainer().getPreferenceStore().getBoolean(ModelPreferences.META_CLIENT_NAME_DISABLE)) {
            String appName = DBUtils.getClientApplicationName(getContainer(), purpose);
            props.put(PROP_APPLICATION_NAME, appName);
        }
        if (getContainer().isConnectionReadOnly()) {
            props.put(PROP_READONLY, "TRUE");
        }
        return props;
    }
    
    /*
     * column unit for views in SYS schema
     */
    public void initializeSysViewColumnUnits(@NotNull DBRProgressMonitor monitor) throws DBException {
        if (sysViewColumnUnits != null)
            return;
        sysViewColumnUnits = new HashMap<String, String>();
        String stmt = "SELECT VIEW_NAME||'.'||VIEW_COLUMN_NAME, UNIT FROM SYS.M_MONITOR_COLUMNS WHERE UNIT IS NOT NULL";
        try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Read generic metadata")) {
            try {
                try (JDBCPreparedStatement dbStat = session.prepareStatement(stmt)) {
                    try (JDBCResultSet resultSet = dbStat.executeQuery()) {
                        while(resultSet.next()) {
                            sysViewColumnUnits.put(resultSet.getString(1), resultSet.getString(2));
                        }
                    }
                }
            } catch (SQLException e) {
                log.debug("Error getting SYS column units: " + e.getMessage());
            }
        }
    }
    
    String getSysViewColumnUnit(String objectName, String columnName)
    {
        return sysViewColumnUnits.get(objectName+"."+columnName);
    }
}
