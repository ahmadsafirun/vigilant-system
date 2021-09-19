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
package org.jkiss.dbeaver.registry;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.model.DBPDataSourceFolder;
import org.jkiss.dbeaver.model.app.DBPDataSourceRegistry;
import org.jkiss.utils.ArrayUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * DataSourceFolder
 */
public class DataSourceFolder implements DBPDataSourceFolder
{
    private final DataSourceRegistry registry;
    private DataSourceFolder parent;
    private List<DataSourceFolder> children = new ArrayList<>();
    private String name;
    private String description;

    public DataSourceFolder(DataSourceRegistry registry, DataSourceFolder parent, String name, String description) {
        this.registry = registry;
        this.name = name;
        this.description = description;
        setParent(parent);
    }

    @NotNull
    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String newName) {
        this.name = newName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public DataSourceFolder getParent() {
        return parent;
    }

    public void setParent(DataSourceFolder parent) {
        if (this.parent != null) {
            this.parent.children.remove(this);
        }
        this.parent = parent;
        if (this.parent != null) {
            this.parent.children.add(this);
        }
    }

    @Override
    public DataSourceFolder[] getChildren() {
        return ArrayUtils.toArray(DataSourceFolder.class, children);
    }

    @Override
    public DBPDataSourceRegistry getDataSourceRegistry() {
        return registry;
    }

    public DataSourceFolder getChild(String name) {
        for (DataSourceFolder child : children) {
            if (child.getName().equals(name)) {
                return child;
            }
        }
        return null;
    }

    @Override
    public String getFolderPath() {
        String path = null;
        for (DataSourceFolder folder = this; folder != null; folder = folder.getParent()) {
            path = path == null ? folder.getName() : folder.getName() + "/" + path;
        }
        return path;
    }

    @Override
    public String toString() {
        return getFolderPath();
    }
}
