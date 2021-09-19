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
package org.jkiss.dbeaver.ext.mssql.model;

import org.eclipse.core.runtime.IAdaptable;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ModelPreferences;
import org.jkiss.dbeaver.ext.mssql.SQLServerConstants;
import org.jkiss.dbeaver.ext.mssql.SQLServerUtils;
import org.jkiss.dbeaver.ext.mssql.model.session.SQLServerSessionManager;
import org.jkiss.dbeaver.model.*;
import org.jkiss.dbeaver.model.admin.sessions.DBAServerSessionManager;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.exec.*;
import org.jkiss.dbeaver.model.exec.jdbc.*;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCDataSource;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCExecutionContext;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCObjectCache;
import org.jkiss.dbeaver.model.meta.Association;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.*;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.utils.BeanUtils;
import org.jkiss.utils.CommonUtils;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

public class SQLServerDataSource extends JDBCDataSource implements DBSObjectSelector, DBSInstanceContainer, /*DBCQueryPlanner, */IAdaptable {

    private static final Log log = Log.getLog(SQLServerDataSource.class);

    // Delegate data type reading to the driver
    private final SystemDataTypeCache dataTypeCache = new SystemDataTypeCache();
    private final DatabaseCache databaseCache = new DatabaseCache();

    private String activeDatabaseName;
    private boolean supportsColumnProperty;
    private String serverVersion;

    public SQLServerDataSource(DBRProgressMonitor monitor, DBPDataSourceContainer container)
        throws DBException
    {
        super(monitor, container, new SQLServerDialect());
    }

    public boolean supportsColumnProperty() {
        return supportsColumnProperty;
    }

    @Override
    protected DBPDataSourceInfo createDataSourceInfo(DBRProgressMonitor monitor, @NotNull JDBCDatabaseMetaData metaData)
    {
        SQLServerDataSourceInfo info = new SQLServerDataSourceInfo(this, metaData);
        if (isDataWarehouseServer(monitor)) {
            info.setSupportsResultSetScroll(false);
        }
        return info;
    }

    boolean isDataWarehouseServer(DBRProgressMonitor monitor) {
        return getServerVersion(monitor).contains(SQLServerConstants.SQL_DW_SERVER_LABEL);
    }

    public String getServerVersion() {
        return serverVersion;
    }

    String getServerVersion(DBRProgressMonitor monitor) {
        if (serverVersion == null) {
            try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Read server version")) {
                serverVersion = JDBCUtils.queryString(session, "SELECT @@VERSION");
            } catch (Exception e) {
                log.debug("Error reading SQL Server version: " + e.getMessage());
                serverVersion = "";
            }
        }
        return serverVersion;
    }

    @Override
    public DBPDataSource getDataSource() {
        return this;
    }

    @Override
    protected Properties getAllConnectionProperties(@NotNull DBRProgressMonitor monitor, String purpose, DBPConnectionConfiguration connectionInfo) throws DBCException {
        Properties properties = super.getAllConnectionProperties(monitor, purpose, connectionInfo);

        if (!getContainer().getPreferenceStore().getBoolean(ModelPreferences.META_CLIENT_NAME_DISABLE)) {
            // App name
            properties.put(
                SQLServerUtils.isDriverJtds(getContainer().getDriver()) ? SQLServerConstants.APPNAME_CLIENT_PROPERTY : SQLServerConstants.APPLICATION_NAME_CLIENT_PROPERTY,
                CommonUtils.truncateString(DBUtils.getClientApplicationName(getContainer(), purpose), 64));
        }

        fillConnectionProperties(connectionInfo, properties);

        SQLServerAuthentication authSchema = SQLServerUtils.detectAuthSchema(connectionInfo);

        authSchema.getInitializer().initializeAuthentication(connectionInfo, properties);

        return properties;
    }

    @Override
    protected void initializeContextState(DBRProgressMonitor monitor, JDBCExecutionContext context, boolean setActiveObject) throws DBCException {
        super.initializeContextState(monitor, context, setActiveObject);
        if (setActiveObject ) {
            SQLServerDatabase defaultObject = getDefaultObject();
            if (defaultObject!= null && !isDataWarehouseServer(monitor)) {
                setCurrentDatabase(monitor, context, defaultObject);
            }
        }
    }

    @Override
    public Object getDataSourceFeature(String featureId) {
        switch (featureId) {
            case DBConstants.FEATURE_LIMIT_AFFECTS_DML:
                return true;
            case DBConstants.FEATURE_MAX_STRING_LENGTH:
                return 8000;
        }
        return super.getDataSourceFeature(featureId);
    }

    @Override
    public void initialize(DBRProgressMonitor monitor) throws DBException {
        super.initialize(monitor);

        this.dataTypeCache.getAllObjects(monitor, this);

        try (JDBCSession session = DBUtils.openMetaSession(monitor, this, "Load data source meta info")) {
            this.activeDatabaseName = SQLServerUtils.getCurrentDatabase(session);

            try {
                JDBCUtils.queryString(session, "SELECT COLUMNPROPERTY(0, NULL, NULL)");
                this.supportsColumnProperty = true;
            } catch (Exception e) {
                this.supportsColumnProperty = false;
            }
        } catch (Throwable e) {
            log.error("Error during connection initialization", e);
        }
    }

    //////////////////////////////////////////////////////////////////
    // Data types

    @Override
    public DBPDataKind resolveDataKind(String typeName, int valueType) {
        return getLocalDataType(valueType).getDataKind();
    }

    @Override
    public List<SQLServerDataType> getLocalDataTypes() {
        return dataTypeCache.getCachedObjects();
    }

    public SQLServerDataType getSystemDataType(int systemTypeId) {
        for (SQLServerDataType dt : dataTypeCache.getCachedObjects()) {
            if (dt.getObjectId() == systemTypeId) {
                return dt;
            }
        }
        log.debug("System data type " + systemTypeId + " not found");
        SQLServerDataType sdt = new SQLServerDataType(this, String.valueOf(systemTypeId), systemTypeId, DBPDataKind.OBJECT, java.sql.Types.OTHER);
        dataTypeCache.cacheObject(sdt);
        return sdt;
    }

    @Override
    public SQLServerDataType getLocalDataType(String typeName) {
        return dataTypeCache.getCachedObject(typeName);
    }

    @Override
    public SQLServerDataType getLocalDataType(int typeID) {
        DBSDataType dt = super.getLocalDataType(typeID);
        if (dt == null) {
            log.debug("System data type " + typeID + " not found");
        }
        return (SQLServerDataType) dt;
    }

    @Override
    public String getDefaultDataTypeName(DBPDataKind dataKind) {
        switch (dataKind) {
            case BOOLEAN: return "bit";
            case NUMERIC: return "int";
            case STRING: return "varchar";
            case DATETIME: return SQLServerConstants.TYPE_DATETIME;
            case BINARY: return "binary";
            case CONTENT: return "varbinary";
            case ROWID: return "uniqueidentifier";
            default:
                return super.getDefaultDataTypeName(dataKind);
        }
    }

    //////////////////////////////////////////////////////////
    // Databases

    protected boolean isShowAllSchemas() {
        return CommonUtils.toBoolean(getContainer().getConnectionConfiguration().getProviderProperty(SQLServerConstants.PROP_SHOW_ALL_SCHEMAS));
    }

    //////////////////////////////////////////////////////////
    // Windows authentication

    @Override
    protected String getConnectionUserName(DBPConnectionConfiguration connectionInfo) {
        if (SQLServerUtils.isWindowsAuth(connectionInfo)) {
            return "";
        } else {
            return super.getConnectionUserName(connectionInfo);
        }
    }

    @Override
    protected String getConnectionUserPassword(DBPConnectionConfiguration connectionInfo) {
        if (SQLServerUtils.isWindowsAuth(connectionInfo)) {
            return "";
        } else {
            return super.getConnectionUserPassword(connectionInfo);
        }
    }

    //////////////////////////////////////////////////////////////
    // Databases

    @Override
    public boolean supportsDefaultChange() {
        return true;
    }

    @Nullable
    @Override
    public SQLServerDatabase getDefaultObject() {
        return activeDatabaseName == null ? null : databaseCache.getCachedObject(activeDatabaseName);
    }

    @Override
    public void setDefaultObject(@NotNull DBRProgressMonitor monitor, @NotNull DBSObject object)
        throws DBException
    {
        final SQLServerDatabase oldSelectedEntity = getDefaultObject();
        if (!(object instanceof SQLServerDatabase)) {
            throw new IllegalArgumentException("Invalid object type: " + object);
        }
        for (JDBCExecutionContext context : getDefaultInstance().getAllContexts()) {
            if (!setCurrentDatabase(monitor, context, (SQLServerDatabase) object)) {
                return;
            }
        }
        activeDatabaseName = object.getName();

        // Send notifications
        if (oldSelectedEntity != null) {
            DBUtils.fireObjectSelect(oldSelectedEntity, false);
        }
        if (this.activeDatabaseName != null) {
            DBUtils.fireObjectSelect(object, true);
        }
    }

    @Override
    public boolean refreshDefaultObject(@NotNull DBCSession session) throws DBException {
        try {
            final String currentSchema = SQLServerUtils.getCurrentDatabase((JDBCSession) session);
            if (currentSchema != null && !CommonUtils.equalObjects(currentSchema, activeDatabaseName)) {
                final SQLServerDatabase newDatabase = databaseCache.getCachedObject(currentSchema);
                if (newDatabase != null) {
                    setDefaultObject(session.getProgressMonitor(), newDatabase);
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            throw new DBException(e, this);
        }
    }

    private boolean setCurrentDatabase(DBRProgressMonitor monitor, JDBCExecutionContext executionContext, SQLServerDatabase object) throws DBCException {
        if (object == null) {
            log.debug("Null current schema");
            return false;
        }
        try (JDBCSession session = executionContext.openSession(monitor, DBCExecutionPurpose.UTIL, "Set active database")) {
            SQLServerUtils.setCurrentDatabase(session, object.getName());
            return true;
        } catch (SQLException e) {
            log.error(e);
            return false;
        }
    }

    @Association
    public Collection<SQLServerDatabase> getDatabases(DBRProgressMonitor monitor) throws DBException {
        return databaseCache.getAllObjects(monitor, this);
    }

    @Override
    public Collection<? extends DBSObject> getChildren(@NotNull DBRProgressMonitor monitor) throws DBException {
        return databaseCache.getAllObjects(monitor, this);
    }

    @Override
    public DBSObject getChild(@NotNull DBRProgressMonitor monitor, @NotNull String childName) throws DBException {
        return databaseCache.getObject(monitor, this, childName);
    }

    @Override
    public Class<? extends DBSObject> getChildType(@NotNull DBRProgressMonitor monitor) throws DBException {
        return SQLServerDatabase.class;
    }

    @Override
    public void cacheStructure(@NotNull DBRProgressMonitor monitor, int scope) throws DBException {
        databaseCache.getAllObjects(monitor, this);
    }

    @Override
    public DBSObject refreshObject(@NotNull DBRProgressMonitor monitor) throws DBException {
        databaseCache.clearCache();
        return super.refreshObject(monitor);
    }

    @Override
    public DBCQueryTransformer createQueryTransformer(DBCQueryTransformType type) {
        if (type == DBCQueryTransformType.RESULT_SET_LIMIT) {
            //if (!SQLServerUtils.isDriverAzure(getContainer().getDriver())) {
                return new QueryTransformerTop();
            //}
        }
        return super.createQueryTransformer(type);
    }

    @Override
    public <T> T getAdapter(Class<T> adapter) {
        if (adapter == DBSStructureAssistant.class) {
            return adapter.cast(new SQLServerStructureAssistant(this));
        } else if (adapter == DBAServerSessionManager.class) {
            return adapter.cast(new SQLServerSessionManager(this));
        }
        return super.getAdapter(adapter);
    }

    @Override
    public ErrorPosition[] getErrorPosition(DBRProgressMonitor monitor, DBCExecutionContext context, String query, Throwable error) {
        Throwable rootCause = GeneralUtils.getRootCause(error);
        if (rootCause != null && SQLServerConstants.SQL_SERVER_EXCEPTION_CLASS_NAME.equals(rootCause.getClass().getName())) {
            // Read line number from SQLServerError class
            try {
                Object serverError = rootCause.getClass().getMethod("getSQLServerError").invoke(rootCause);
                if (serverError != null) {
                    Object serverErrorLine = BeanUtils.readObjectProperty(serverError, "lineNumber");
                    if (serverErrorLine instanceof Number) {
                        ErrorPosition pos = new ErrorPosition();
                        pos.line = ((Number) serverErrorLine).intValue() - 1;
                        return new ErrorPosition[] {pos};
                    }
                }
            } catch (Throwable e) {
                // ignore
            }
        }

        return super.getErrorPosition(monitor, context, query, error);
    }

    static class DatabaseCache extends JDBCObjectCache<SQLServerDataSource, SQLServerDatabase> {
        DatabaseCache() {
            setListOrderComparator(DBUtils.nameComparator());
        }

        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull SQLServerDataSource owner) throws SQLException {
            StringBuilder sql = new StringBuilder("SELECT db.* FROM sys.databases db");

            DBSObjectFilter databaseFilters = owner.getContainer().getObjectFilter(SQLServerDatabase.class, null, false);
            if (databaseFilters != null && databaseFilters.isEnabled()) {
                JDBCUtils.appendFilterClause(sql, databaseFilters, "name", true);
            }
            sql.append("\nORDER BY db.name");
            JDBCPreparedStatement dbStat = session.prepareStatement(sql.toString());
            if (databaseFilters != null) {
                JDBCUtils.setFilterParameters(dbStat, 1, databaseFilters);
            }
            return dbStat;
        }

        @Override
        protected SQLServerDatabase fetchObject(@NotNull JDBCSession session, @NotNull SQLServerDataSource owner, @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new SQLServerDatabase(owner, resultSet);
        }

    }

    private class SystemDataTypeCache extends JDBCObjectCache<SQLServerDataSource, SQLServerDataType> {
        @NotNull
        @Override
        protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull SQLServerDataSource sqlServerDataSource) throws SQLException {
            return session.prepareStatement("SELECT * FROM sys.types WHERE is_user_defined = 0 order by name");
        }

        @Override
        protected SQLServerDataType fetchObject(@NotNull JDBCSession session, @NotNull SQLServerDataSource dataSource, @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
            return new SQLServerDataType(dataSource, resultSet);
        }
    }
}
