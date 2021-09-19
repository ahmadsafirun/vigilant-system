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

package org.jkiss.dbeaver.model.app;

import org.eclipse.core.resources.IProject;
import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.*;
import org.jkiss.dbeaver.model.struct.DBSObjectFilter;

import java.util.List;

/**
 * Datasource registry.
 * Extends DBPObject to support datasources ObjectManager
 */
public interface DBPDataSourceRegistry extends DBPObject {

    String CONFIG_FILE_PREFIX = ".dbeaver-data-sources"; //$NON-NLS-1$
    String CONFIG_FILE_EXT = ".xml"; //$NON-NLS-1$
    String CONFIG_FILE_NAME = CONFIG_FILE_PREFIX + CONFIG_FILE_EXT;

    @NotNull
    DBPPlatform getPlatform();
    /**
     * Owner project.
     */
    IProject getProject();

    @Nullable
    DBPDataSourceContainer getDataSource(String id);

    @Nullable
    DBPDataSourceContainer getDataSource(DBPDataSource dataSource);

    @Nullable
    DBPDataSourceContainer findDataSourceByName(String name);

    List<? extends DBPDataSourceContainer> getDataSources();

    void addDataSourceListener(DBPEventListener listener);

    boolean removeDataSourceListener(DBPEventListener listener);

    void addDataSource(DBPDataSourceContainer dataSource);

    void removeDataSource(DBPDataSourceContainer dataSource);

    void updateDataSource(DBPDataSourceContainer dataSource);

    List<? extends DBPDataSourceFolder> getAllFolders();

    List<? extends DBPDataSourceFolder> getRootFolders();

    DBPDataSourceFolder addFolder(DBPDataSourceFolder parent, String name);

    void removeFolder(DBPDataSourceFolder folder, boolean dropContents);

    @Nullable
    DBSObjectFilter getSavedFilter(String name);

    @NotNull
    List<DBSObjectFilter> getSavedFilters();

    void updateSavedFilter(DBSObjectFilter filter);

    void removeSavedFilter(String filterName);

    void flushConfig();

    void refreshConfig();

    void notifyDataSourceListeners(final DBPEvent event);

    @NotNull
    ISecurePreferences getSecurePreferences();
}
