/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2019 Serge Rider (serge@jkiss.org)
 * Copyright (C) 2011-2012 Eugene Fradkin (eugene.fradkin@gmail.com)
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
package org.jkiss.dbeaver.ext.oracle.edit;

import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.oracle.model.OracleSchema;
import org.jkiss.dbeaver.ext.oracle.model.OracleView;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.edit.prop.DBECommandComposite;
import org.jkiss.dbeaver.model.impl.edit.SQLDatabasePersistAction;
import org.jkiss.dbeaver.model.impl.sql.edit.SQLObjectEditor;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.cache.DBSObjectCache;
import org.jkiss.utils.CommonUtils;

import java.util.List;
import java.util.Map;

/**
 * OracleViewManager
 */
public class OracleViewManager extends SQLObjectEditor<OracleView, OracleSchema> {

    @Override
    public long getMakerOptions(DBPDataSource dataSource)
    {
        return FEATURE_EDITOR_ON_CREATE;
    }

    @Override
    protected void validateObjectProperties(ObjectChangeCommand command, Map<String, Object> options)
        throws DBException
    {
        if (CommonUtils.isEmpty(command.getObject().getName())) {
            throw new DBException("View name cannot be empty");
        }
        if (CommonUtils.isEmpty(command.getObject().getViewText())) {
            throw new DBException("View definition cannot be empty");
        }
    }

    @Nullable
    @Override
    public DBSObjectCache<? extends DBSObject, OracleView> getObjectsCache(OracleView object)
    {
        return (DBSObjectCache) object.getSchema().tableCache;
    }

    @Override
    protected OracleView createDatabaseObject(DBRProgressMonitor monitor, DBECommandContext context, Object container, Object copyFrom, Map<String, Object> options)
    {
        OracleView newView = new OracleView((OracleSchema) container, "NEW_VIEW"); //$NON-NLS-1$
        newView.setViewText("CREATE OR REPLACE VIEW " + newView.getFullyQualifiedName(DBPEvaluationContext.DDL) + " AS\nSELECT");
        return newView;
    }

    @Override
    protected void addObjectCreateActions(DBRProgressMonitor monitor, List<DBEPersistAction> actions, ObjectCreateCommand command, Map<String, Object> options)
    {
        createOrReplaceViewQuery(actions, command);
    }

    @Override
    protected void addObjectModifyActions(DBRProgressMonitor monitor, List<DBEPersistAction> actionList, ObjectChangeCommand command, Map<String, Object> options)
    {
        createOrReplaceViewQuery(actionList, command);
    }

    @Override
    protected void addObjectDeleteActions(List<DBEPersistAction> actions, ObjectDeleteCommand command, Map<String, Object> options)
    {
        actions.add(
            new SQLDatabasePersistAction("Drop view", "DROP VIEW " + command.getObject().getFullyQualifiedName(DBPEvaluationContext.DDL)) //$NON-NLS-2$
        );
    }

    private void createOrReplaceViewQuery(List<DBEPersistAction> actions, DBECommandComposite<OracleView, PropertyHandler> command)
    {
        final OracleView view = command.getObject();
        boolean hasComment = command.getProperty("comment") != null;
        if (!hasComment || command.getProperties().size() > 1) {
            String viewText = view.getViewText().trim();
            while (viewText.endsWith(";")) {
                viewText = viewText.substring(0, viewText.length() - 1);
            }
            actions.add(new SQLDatabasePersistAction("Create view", viewText));
        }
        if (hasComment) {
            actions.add(new SQLDatabasePersistAction(
                "Comment table",
                "COMMENT ON TABLE " + view.getFullyQualifiedName(DBPEvaluationContext.DDL) +
                    " IS '" + view.getComment() + "'"));
        }
    }

}

