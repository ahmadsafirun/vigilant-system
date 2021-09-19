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

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.impl.jdbc.struct.JDBCTableConstraint;
import org.jkiss.dbeaver.model.meta.Property;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSEntityAttributeRef;
import org.jkiss.dbeaver.model.struct.DBSEntityConstraint;
import org.jkiss.dbeaver.model.struct.DBSEntityConstraintType;
import org.jkiss.dbeaver.model.struct.DBSEntityReferrer;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLServerTableUniqueKey
 */
public class SQLServerTableUniqueKey extends JDBCTableConstraint<SQLServerTable> {
    private SQLServerTableIndex index;
    private List<SQLServerTableUniqueKeyColumn> columns;

    public SQLServerTableUniqueKey(SQLServerTable table, String name, String remarks, DBSEntityConstraintType constraintType, SQLServerTableIndex index, boolean persisted) {
        super(table, name, remarks, constraintType, persisted);
        this.index = index;
    }

    // Copy constructor
    protected SQLServerTableUniqueKey(DBRProgressMonitor monitor, SQLServerTable table, DBSEntityConstraint source) throws DBException {
        super(table, source, false);
        this.index = table.getIndex(monitor, source.getName());
    }

    @Property(viewable = true, order = 10)
    public SQLServerTableIndex getIndex() {
        return index;
    }

    @Override
    public List<? extends DBSEntityAttributeRef> getAttributeReferences(DBRProgressMonitor monitor) {
        if (columns != null) {
            return columns;
        }
        return index.getAttributeReferences(monitor);
    }

    @NotNull
    @Override
    public String getFullyQualifiedName(DBPEvaluationContext context) {
        return DBUtils.getFullQualifiedName(getDataSource(),
            getTable().getDatabase(),
            getTable().getSchema(),
            getTable(),
            this);
    }

    @NotNull
    @Override
    public SQLServerDataSource getDataSource() {
        return getTable().getDataSource();
    }

    public void addColumn(SQLServerTableUniqueKeyColumn column) {
        if (columns == null) {
            columns = new ArrayList<>();
        }
        this.columns.add(column);
    }

    void setColumns(List<SQLServerTableUniqueKeyColumn> columns) {
        this.columns = columns;
    }

}
