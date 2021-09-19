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
package org.jkiss.dbeaver.ui.resources;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ui.part.FileEditorInput;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.navigator.DBNNode;
import org.jkiss.dbeaver.model.navigator.DBNResource;
import org.jkiss.dbeaver.ui.UIIcon;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.editors.EditorUtils;
import org.jkiss.dbeaver.ui.editors.sql.SQLEditor;

import java.util.Collection;
import java.util.Collections;

/**
 * Scripts handler
 */
public class ScriptsHandlerImpl extends AbstractResourceHandler {

    private static final Log log = Log.getLog(ScriptsHandlerImpl.class);

    @Override
    public int getFeatures(IResource resource)
    {
        if (resource instanceof IFile) {
            return FEATURE_OPEN | FEATURE_DELETE | FEATURE_RENAME;
        }
        return super.getFeatures(resource);
    }

    @NotNull
    @Override
    public String getTypeName(@NotNull IResource resource)
    {
        if (resource instanceof IFolder) {
            return "script folder"; //$NON-NLS-1$
        } else {
            return "script"; //$NON-NLS-1$
        }
    }

    @Override
    public String getResourceDescription(@NotNull IResource resource)
    {
        return ResourceUtils.getResourceDescription(resource);
    }

    @NotNull
    @Override
    public DBNResource makeNavigatorNode(@NotNull DBNNode parentNode, @NotNull IResource resource) throws CoreException, DBException
    {
        DBNResource node = super.makeNavigatorNode(parentNode, resource);
        updateNavigatorNode(node, resource);
        return node;
    }

    @Override
    public void updateNavigatorNode(@NotNull DBNResource node, @NotNull IResource resource) {
        super.updateNavigatorNode(node, resource);
        if (resource instanceof IFolder) {
            if (resource.getParent() instanceof IProject) {
                node.setResourceImage(UIIcon.SCRIPTS);
            }
        } else {
            node.setResourceImage(UIIcon.SQL_SCRIPT);
        }
    }

    @Override
    public void openResource(@NotNull IResource resource) throws CoreException, DBException
    {
        if (resource instanceof IFile) {
            FileEditorInput sqlInput = new FileEditorInput((IFile)resource);
            UIUtils.getActiveWorkbenchWindow().getActivePage().openEditor(
                sqlInput,
                SQLEditor.class.getName());
        } else {
            super.openResource(resource);
        }
    }

    @Nullable
    @Override
    public Collection<DBPDataSourceContainer> getAssociatedDataSources(IResource resource)
    {
        if (resource instanceof IFile) {
            DBPDataSourceContainer dataSource = EditorUtils.getFileDataSource((IFile) resource);
            return dataSource == null ? null : Collections.singleton(dataSource);
        }
        return null;
    }

    @NotNull
    @Override
    public String getResourceNodeName(@NotNull IResource resource) {
//        if (resource.getParent() instanceof IProject && resource.equals(getDefaultRoot(resource.getProject()))) {
//            return "SQL Scripts";
//        } else {
            return super.getResourceNodeName(resource);
//        }
    }


}
