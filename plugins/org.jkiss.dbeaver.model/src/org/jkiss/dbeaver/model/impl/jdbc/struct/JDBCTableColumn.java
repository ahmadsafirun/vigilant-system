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
package org.jkiss.dbeaver.model.impl.jdbc.struct;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.*;
import org.jkiss.dbeaver.model.data.DBDLabelValuePair;
import org.jkiss.dbeaver.model.data.DBDValueHandler;
import org.jkiss.dbeaver.model.exec.DBCResultSet;
import org.jkiss.dbeaver.model.exec.DBCSession;
import org.jkiss.dbeaver.model.exec.DBCStatement;
import org.jkiss.dbeaver.model.exec.DBCStatementType;
import org.jkiss.dbeaver.model.impl.DBObjectNameCaseTransformer;
import org.jkiss.dbeaver.model.meta.IPropertyValueListProvider;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.struct.DBSAttributeEnumerable;
import org.jkiss.dbeaver.model.struct.DBSDataType;
import org.jkiss.dbeaver.model.struct.DBSEntity;
import org.jkiss.dbeaver.model.struct.DBSEntityAttribute;
import org.jkiss.dbeaver.model.struct.rdb.DBSTableColumn;
import org.jkiss.dbeaver.model.virtual.DBVUtils;
import org.jkiss.utils.CommonUtils;

import java.util.*;

/**
 * JDBC abstract table column
 */
public abstract class JDBCTableColumn<TABLE_TYPE extends DBSEntity> extends JDBCAttribute implements DBSTableColumn, DBSEntityAttribute, DBSAttributeEnumerable, DBPSaveableObject {

    private final TABLE_TYPE table;
    private boolean persisted;
    private String defaultValue;

    protected JDBCTableColumn(TABLE_TYPE table, boolean persisted)
    {
        this.table = table;
        this.persisted = persisted;
    }

    protected JDBCTableColumn(
            TABLE_TYPE table,
            boolean persisted,
            String name,
            String typeName,
            int valueType,
            int ordinalPosition,
            long maxLength,
            Integer scale,
            Integer precision,
            boolean required,
            boolean autoGenerated,
            String defaultValue)
    {
        super(name, typeName, valueType, ordinalPosition, maxLength, scale, precision, required, autoGenerated);
        this.defaultValue = defaultValue;
        this.table = table;
        this.persisted = persisted;
    }

    protected JDBCTableColumn(
        TABLE_TYPE table,
        DBSEntityAttribute source,
        boolean persisted)
    {
        super(source);
        this.table = table;
        this.persisted = persisted;
        this.defaultValue = source.getDefaultValue();
    }

    public TABLE_TYPE getTable()
    {
        return table;
    }

    @NotNull
    @Override
    public TABLE_TYPE getParentObject()
    {
        return getTable();
    }

    @NotNull
    @Property(viewable = true, editable = true, valueTransformer = DBObjectNameCaseTransformer.class, order = 10)
    @Override
    public String getName()
    {
        return super.getName();
    }

    @Property(viewable = true, editable = true, order = 20, listProvider = ColumnTypeNameListProvider.class)
    @Override
    public String getTypeName()
    {
        return super.getTypeName();
    }

    @Override
    public void setTypeName(String typeName) {
        super.setTypeName(typeName);
        final DBPDataSource dataSource = getDataSource();
        if (dataSource instanceof DBPDataTypeProvider) {
            DBSDataType dataType = ((DBPDataTypeProvider) dataSource).getLocalDataType(typeName);
            if (dataType != null) {
                updateColumnDataType(dataType);
            } else {
                updateColumnDataType(null);
            }
        }
    }

    protected void updateColumnDataType(@Nullable DBSDataType dataType) {
        if (dataType != null) {
            this.valueType = dataType.getTypeID();
        }
    }

    @Property(viewable = true, editable = true, order = 40)
    @Override
    public long getMaxLength()
    {
        return super.getMaxLength();
    }

    @Property(viewable = true, editable = true, order = 50)
    @Override
    public boolean isRequired()
    {
        return super.isRequired();
    }

    @Property(viewable = true, editable = true, order = 70)
    @Override
    public String getDefaultValue()
    {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue)
    {
        this.defaultValue = defaultValue;
    }

    @Override
    public boolean isPersisted()
    {
        return persisted;
    }

    @Override
    public void setPersisted(boolean persisted)
    {
        this.persisted = persisted;
    }

    @NotNull
    @Override
    public List<DBDLabelValuePair> getValueEnumeration(@NotNull DBCSession session, @Nullable Object valuePattern, int maxResults) throws DBException {
        DBDValueHandler valueHandler = DBUtils.findValueHandler(session, this);
        StringBuilder query = new StringBuilder();
        query.append("SELECT ").append(DBUtils.getQuotedIdentifier(this)).append(", count(*)");
        // Do not use description columns because they duplicate distinct value
//        String descColumns = DBVUtils.getDictionaryDescriptionColumns(session.getProgressMonitor(), this);
//        if (descColumns != null) {
//            query.append(", ").append(descColumns);
//        }
        query.append("\nFROM ").append(DBUtils.getObjectFullName(getTable(), DBPEvaluationContext.DML));
        if (valuePattern instanceof String) {
            query.append("\nWHERE ").append(DBUtils.getQuotedIdentifier(this));
            if (getDataKind() == DBPDataKind.STRING) {
                query.append(" LIKE ?");
            } else {
                query.append(" = ?");
            }
        }
        query.append("\nGROUP BY ").append(DBUtils.getQuotedIdentifier(this));

        try (DBCStatement dbStat = session.prepareStatement(DBCStatementType.QUERY, query.toString(), false, false, false)) {
            if (valuePattern instanceof String) {
                if (getDataKind() == DBPDataKind.STRING) {
                    valueHandler.bindValueObject(session, dbStat, this, 0, "%" + valuePattern + "%");
                } else {
                    valueHandler.bindValueObject(session, dbStat, this, 0, valuePattern);
                }
            }
            dbStat.setLimit(0, maxResults);
            if (dbStat.executeStatement()) {
                try (DBCResultSet dbResult = dbStat.openResultSet()) {
                    return DBVUtils.readDictionaryRows(session, this, valueHandler, dbResult);
                }
            } else {
                return Collections.emptyList();
            }
        }
    }

    public static class ColumnTypeNameListProvider implements IPropertyValueListProvider<JDBCTableColumn> {

        @Override
        public boolean allowCustomValue()
        {
            return true;
        }

        @Override
        public Object[] getPossibleValues(JDBCTableColumn column)
        {
            Set<String> typeNames = new TreeSet<>();
            if (column.getDataSource() instanceof DBPDataTypeProvider) {
                for (DBSDataType type : ((DBPDataTypeProvider) column.getDataSource()).getLocalDataTypes()) {
                    if (type.getDataKind() != DBPDataKind.UNKNOWN && !CommonUtils.isEmpty(type.getName()) && Character.isLetter(type.getName().charAt(0))) {
                        typeNames.add(type.getName());
                    }
                }
            }
            return typeNames.toArray(new String[typeNames.size()]);
        }
    }

}
