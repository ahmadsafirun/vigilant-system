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
package org.jkiss.dbeaver.ui.navigator.database;

import org.eclipse.swt.widgets.Composite;
import org.jkiss.dbeaver.model.app.DBPProject;
import org.jkiss.dbeaver.model.app.DBPProjectListener;
import org.jkiss.dbeaver.model.navigator.DBNEmptyNode;
import org.jkiss.dbeaver.model.navigator.DBNNode;
import org.jkiss.dbeaver.model.navigator.DBNProject;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.ui.IHelpContextIds;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.navigator.INavigatorFilter;

public class DatabaseNavigatorView extends NavigatorViewBase implements DBPProjectListener {
    public static final String VIEW_ID = "org.jkiss.dbeaver.core.databaseNavigator";

    public DatabaseNavigatorView()
    {
        super();
        DBWorkbench.getPlatform().getWorkspace().addProjectListener(this);
    }

    @Override
    protected INavigatorFilter getNavigatorFilter() {
        return new DatabaseNavigatorTreeFilter();
    }

    @Override
    public DBNNode getRootNode()
    {
        DBNProject projectNode = getModel().getRoot().getProjectNode(DBWorkbench.getPlatform().getWorkspace().getActiveProject());
        return projectNode == null ? new DBNEmptyNode() : projectNode.getDatabases();
    }

    @Override
    public void dispose()
    {
        DBWorkbench.getPlatform().getWorkspace().removeProjectListener(this);
        super.dispose();
    }

    @Override
    public void createPartControl(Composite parent)
    {
        super.createPartControl(parent);
        UIUtils.setHelp(parent, IHelpContextIds.CTX_DATABASE_NAVIGATOR);
    }

    @Override
    public void handleProjectAdd(DBPProject project) {
        // Ignore
    }

    @Override
    public void handleProjectRemove(DBPProject project) {
        // Ignore
    }

    @Override
    public void handleActiveProjectChange(DBPProject oldValue, DBPProject newValue)
    {
        UIUtils.asyncExec(() -> {
            getNavigatorTree().getViewer().setInput(new DatabaseNavigatorContent(getRootNode()));
            getSite().getSelectionProvider().setSelection(getNavigatorTree().getViewer().getSelection());
        });

    }

}
