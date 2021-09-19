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
package org.jkiss.dbeaver.core;

import org.eclipse.core.resources.IWorkspace;
import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.model.app.DBPPlatform;
import org.jkiss.dbeaver.registry.BaseWorkspaceImpl;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.utils.CommonUtils;
import org.jkiss.utils.SecurityUtils;

import java.util.Properties;

/**
 * DBeaver workspace.
 *
 * Basically just a wrapper around Eclipse workspace.
 * Additionally holds information about remote workspace.
 * Identified by unique ID (random UUID).
 */
public class DBeaverWorkspace extends BaseWorkspaceImpl {

    private static final String WORKSPACE_ID = "workspace-id";

    private String workspaceId;

    DBeaverWorkspace(DBPPlatform platform, IWorkspace eclipseWorkspace) {
        super(platform, eclipseWorkspace);

        // Check workspace ID
        Properties workspaceInfo = BaseWorkspaceImpl.readWorkspaceInfo(GeneralUtils.getMetadataFolder());
        workspaceId = workspaceInfo.getProperty(WORKSPACE_ID);
        if (CommonUtils.isEmpty(workspaceId)) {
            // Generate new UUID
            workspaceId = "D" + Long.toString(
                Math.abs(SecurityUtils.generateRandomLong()),
                36).toUpperCase();
            workspaceInfo.setProperty(WORKSPACE_ID, workspaceId);
            BaseWorkspaceImpl.writeWorkspaceInfo(GeneralUtils.getMetadataFolder(), workspaceInfo);
        }
    }

    @NotNull
    @Override
    public String getWorkspaceId() {
        return workspaceId;
    }

}