/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2017 Serge Rider (serge@jkiss.org)
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
package org.jkiss.dbeaver.model.impl.jdbc.data;

import org.jkiss.dbeaver.model.data.DBDCursor;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.impl.jdbc.exec.JDBCResultSetImpl;

import java.sql.ResultSet;

/**
 * Result set holder
 */
public class JDBCCursor extends JDBCResultSetImpl implements DBDCursor {

    public JDBCCursor(JDBCSession session, ResultSet original, String description)
    {
        super(session, null, original, description, true);
    }

    @Override
    public Object getRawValue() {
        return getOriginal();
    }

    @Override
    public boolean isNull()
    {
        return false;
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public void release()
    {
        super.close();
    }

    @Override
    public String toString()
    {
        return getStatement().getQueryString();
    }

}
